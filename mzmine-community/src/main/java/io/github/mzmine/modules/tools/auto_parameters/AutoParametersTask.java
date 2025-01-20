/*
 * Copyright (c) 2004-2023 The MZmine Development Team
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

package io.github.mzmine.modules.tools.auto_parameters;

import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.ScanDataType;
import io.github.mzmine.datamodel.data_access.ScanDataAccess;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.impl.masslist.SimpleMassList;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.MZmineRunnableModule;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.auto.AutoMassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.auto.AutoMassDetectorParameters;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.scans.SpectraMerging;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AutoParametersTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(AutoParametersTask.class.getName());

  final RawDataFile file;
  private double progress;
  private AutoParametersResultCollection result;

  private double chromatogramProgress = 0d;

  /**
   * @param storage        The {@link MemoryMapStorage} used to store results of this task (e.g.
   *                       RawDataFiles, MassLists, FeatureLists). May be null if results shall be
   *                       stored in ram. For now, one storage should be created per module call in
   *                       {@link MZmineRunnableModule#runModule(MZmineProject, ParameterSet,
   *                       Collection, Instant)}.
   * @param moduleCallDate
   */
  public AutoParametersTask(@Nullable MemoryMapStorage storage, @NotNull Instant moduleCallDate,
      final RawDataFile file) {
    super(storage, moduleCallDate);
    this.file = file;
  }

  public AutoParametersResultCollection getResult() {
    return result;
  }

  @Override
  public String getTaskDescription() {
    return "Determining auto parameters.";
  }

  @Override
  public double getFinishedPercentage() {
    return progress;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    findChromatograms(file);
    MZmineCore.getDesktop().addTab(new AutoParametersTab("Results", result));
    setStatus(TaskStatus.FINISHED);
  }

  public void findChromatograms(final RawDataFile file) {

    final RangeMap<Double, Scan> basePeakMap = TreeRangeMap.create();

    final Range<Float> dataRTRange = file.getDataRTRange();
    // try to avoid void volume and reequilibration.
    final Range<Double> assessedRtRange = Range.closed(dataRTRange.upperEndpoint() * .1,
        dataRTRange.upperEndpoint() * 0.7);
    final ScanSelection scanSelection = new ScanSelection(assessedRtRange, 1);
    logger.finest("Parameter optimisation for file %s".formatted(file.getName()));

    // high mz-tolerance to not map too much signals.
    final MZTolerance basePeakRecognitionTolerance = new MZTolerance(3d, 0);
    final List<Scan> assessedScans = scanSelection.getMatchingScans(file.getScans());
    final List<Scan> sortedScans = assessedScans.stream()
        .sorted(Comparator.comparingDouble(Scan::getBasePeakIntensity).reversed()).toList();
    logger.finest(
        "Parameter optimisation for file %s, processing from rt %.2f-%.2f".formatted(file.getName(),
            assessedRtRange.lowerEndpoint(), assessedRtRange.upperEndpoint()));

    // map all base-peaks with the scan they are the most intense in.
    for (Scan scan : sortedScans) {
      if (basePeakMap.get(scan.getBasePeakMz()) == null) {
        final Range<Double> range = SpectraMerging.createNewNonOverlappingRange(basePeakMap,
            basePeakRecognitionTolerance.getToleranceRange(scan.getBasePeakMz()));
        basePeakMap.put(range, scan);
      }
    }
    logger.finest(
        "Parameter optimisation for file %s, using %d individual m/zs".formatted(file.getName(),
            basePeakMap.asMapOfRanges().size()));

    applyZeroIntensityMassDetection(assessedScans);
    // use raw data as imported. Let's hope that for profile data the highest point does not jitter too much.
    final ScanDataAccess access = EfficientDataAccess.of(file, ScanDataType.MASS_LIST,
        assessedScans);
    final List<MZTolerance> tolerances = List.of(new MZTolerance(0.5, 0), new MZTolerance(0.1, 0),
        new MZTolerance(0.05, 0), new MZTolerance(0.02, 0), new MZTolerance(0.01, 0),
        new MZTolerance(0.008, 0), new MZTolerance(0.005, 0), new MZTolerance(0.003, 0),
        new MZTolerance(0.001, 0), new MZTolerance(0.0008, 0), new MZTolerance(0.0003, 0),
        new MZTolerance(0.0001, 0));
    logger.finest(
        "Parameter optimisation for file %s, building chromatograms for %d ranges".formatted(
            file.getName(), tolerances.size()));

    final ModularFeatureList flist = new ModularFeatureList("flist", getMemoryMapStorage(), file);
    List<AutoFeatureResult> autoFeatureResults = new ArrayList<>();
    final double chromatogramStepProgress = 1d / basePeakMap.asMapOfRanges().size();
    for (Scan scan : basePeakMap.asMapOfRanges().values()) {
      final AutoFeatureResult autoFeatureResult = new AutoFeatureResult(flist, scan,
          scan.getBasePeakMz(), getMemoryMapStorage());
      autoFeatureResult.createChromatograms(tolerances, access);
      autoFeatureResults.add(autoFeatureResult);
      chromatogramProgress += chromatogramStepProgress;
    }
    logger.finest(
        "Parameter optimisation for file %s, created %d chromatograms".formatted(file.getName(),
            autoFeatureResults.size()));

    autoFeatureResults.forEach(c -> c.processChromatograms(access));
    logger.finest("Creating statistics for %d auto features.".formatted(autoFeatureResults.size()));
    logger.finest(
        "Parameter optimisation for file %s, processed %d chromatograms".formatted(file.getName(),
            autoFeatureResults.size()));
//    autoFeatureResults.forEach(AutoFeatureResult::printStatistics);
//    assessedScans.forEach(s -> s.addMassList(null));
    result = new AutoParametersResultCollection(autoFeatureResults);
  }

  private void applyZeroIntensityMassDetection(List<Scan> assessedScans) {
    final AutoMassDetector autoMassDetector = MZmineCore.getModuleInstance(AutoMassDetector.class);
    ParameterSet autoMassDetectorParameters = new AutoMassDetectorParameters().cloneParameterSet();
    autoMassDetectorParameters.setParameter(AutoMassDetectorParameters.noiseLevel, 0d);

    for (Scan assessedScan : assessedScans) {
      assessedScan.addMassList(new SimpleMassList(getMemoryMapStorage(),
          autoMassDetector.getMassValues(assessedScan)));
    }
  }
}
