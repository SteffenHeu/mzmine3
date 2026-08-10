/*
 * Copyright (c) 2004-2026 The mzmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.dataprocessing.featdet_masscalibration2;

import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.ScanDataType;
import io.github.mzmine.datamodel.data_access.ScanDataAccess;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.datamodel.impl.MobilityScanStorage;
import io.github.mzmine.datamodel.impl.SimpleFrame;
import io.github.mzmine.datamodel.impl.masslist.SimpleMassList;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.api.MzCalibrationFunction;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.api.MzCalibrationMethod;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.submodules.ValueWithParameters;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.exceptions.MissingMassListException;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MassCalibrationTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(MassCalibrationTask.class.getName());

  private final RawDataFile dataFile;
  private final ParameterSet parameters;
  private final ScanSelection scanSelection;
  private int processedScans = 0;
  private int totalScans = 0;

  public MassCalibrationTask(RawDataFile dataFile, ParameterSet parameters,
      MemoryMapStorage storageMemoryMap, @NotNull Instant moduleCallDate) {
    super(storageMemoryMap, moduleCallDate);
    this.dataFile = dataFile;
    this.parameters = parameters;
    this.scanSelection = parameters.getValue(MassCalibrationParameters.scanSelection);
  }

  @Override
  public String getTaskDescription() {
    return "Mass calibration of " + dataFile;
  }

  @Override
  public double getFinishedPercentage() {
    return totalScans == 0 ? 0 : (double) processedScans / totalScans;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    try {
      // resolve the selected calibration method and configure it for this run
      final ValueWithParameters<MzCalibrationMethods> selected = parameters.getParameter(
          MassCalibrationParameters.calibrationMethod).getValueWithParameters();
      final MzCalibrationMethod method = selected.value().getModuleInstance()
          .newInstance(selected.parameters(), getMemoryMapStorage());

      // 1) build the file-specific calibration
      final MzCalibrationFunction calibration = method.buildCalibration(dataFile);
      if (calibration == null) {
        setErrorMessage("Could not establish a mass calibration for " + dataFile.getName()
            + " (no lockmass / calibrant matches found). See log for details.");
        setStatus(TaskStatus.ERROR);
        return;
      }
      logger.info("Mass calibration for " + dataFile.getName() + ": " + calibration.description());

      // 2) apply the calibration to all selected mass lists, substituting them in place
      final ScanDataAccess data = EfficientDataAccess.of(dataFile, ScanDataType.MASS_LIST,
          scanSelection);
      totalScans = data.getNumberOfScans();

      final double @Nullable [] mobScanMzValueBuffer =
          dataFile instanceof IMSRawDataFile ims ? new double[ims.getFrames().stream()
              .mapToInt(f -> f.getMobilityScanStorage().getMassListTotalNumPoints()).max()
              .getAsInt()] : null;

      while (data.hasNextScan()) {
        if (isCanceled()) {
          return;
        }
        final Scan scan = data.nextScan();
        if (scan == null) {
          continue;
        }

        final int n = data.getNumberOfDataPoints();
        final double[] mz = new double[n];
        final double[] intensities = new double[n];
        final float rt = scan.getRetentionTime();
        for (int i = 0; i < n; i++) {
          mz[i] = calibration.getCalibratedMz(data.getMzValue(i), rt);
          intensities[i] = data.getIntensityValue(i);
        }
        scan.addMassList(new SimpleMassList(getMemoryMapStorage(), mz, intensities));

        if (scan instanceof SimpleFrame frame && mobScanMzValueBuffer != null) {
          final MobilityScanStorage mobStorage = frame.getMobilityScanStorage();
          mobStorage.getAllMassListMzValues(mobScanMzValueBuffer);
          for (int i = 0; i < mobStorage.getMassListTotalNumPoints(); i++) {
            mobScanMzValueBuffer[i] = calibration.getCalibratedMz(mobScanMzValueBuffer[i], rt);
          }
          mobStorage.setMassListMzValues(mobScanMzValueBuffer,
              mobStorage.getMassListTotalNumPoints(), getMemoryMapStorage());
        }
        processedScans++;
      }

      dataFile.getAppliedMethods().add(
          new SimpleFeatureListAppliedMethod(MassCalibrationModule.class, parameters,
              getModuleCallDate()));
    } catch (MissingMassListException e) {
      setErrorMessage(
          "A selected scan has no mass list. Run mass detection before mass calibration.");
      setStatus(TaskStatus.ERROR);
      return;
    } catch (Exception e) {
      logger.log(Level.WARNING, "Error during mass calibration: " + e.getMessage(), e);
      setErrorMessage(e.getMessage());
      setStatus(TaskStatus.ERROR);
      return;
    }

    setStatus(TaskStatus.FINISHED);
    logger.info("Finished mass calibration on " + dataFile);
  }
}
