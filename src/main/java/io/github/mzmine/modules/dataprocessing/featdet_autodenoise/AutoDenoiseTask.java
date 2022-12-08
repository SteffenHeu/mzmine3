/*
 * Copyright (c) 2004-2022 The MZmine Development Team
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

package io.github.mzmine.modules.dataprocessing.featdet_autodenoise;

import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.MassList;
import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.MobilityScan;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.impl.masslist.SimpleMassList;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.scans.DenoisingUtils;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AutoDenoiseTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(
      AutoDenoiseTask.class.getName());

  private final RawDataFile file;
  private final int numDetections;
  //  private final ScanDimension dimension;
  private final ScanSelection selection;
  private final Map<Integer, List<Scan>> msLevelScanMap = new HashMap<>();
  private final int totalScans;
  private int processedScans;
  private final int width = 5;
  private final MZTolerance mzTol = new MZTolerance(0.003, 15);

  private final Map<Scan, MassSpectrum> centroidedSpectra = new HashMap<>();
  private final Map<MassSpectrum, Scan> rawSpectra = new HashMap<>();


  public AutoDenoiseTask(@Nullable MemoryMapStorage storage,
      @NotNull Instant moduleCallDate, RawDataFile file,
      ParameterSet parameters) {
    super(storage, moduleCallDate);

    this.file = file;
    selection = parameters.getValue(AutoDenoiseParameters.scanSelection);
//    width = parameters.getValue(AutoDenoiseParameters.width);
    numDetections = parameters.getValue(AutoDenoiseParameters.numDetections);

    if (selection.getMsLevel() == null) {
      for (int msLevel : file.getMSLevels()) {
        msLevelScanMap.put(msLevel, selection.getMatchingScans(
            file.getScans().stream()
                .filter(scan -> scan.getMSLevel() == msLevel).toList()));
      }
    } else {
      msLevelScanMap.put(selection.getMsLevel(),
          selection.getMatchingScans(file.getScans()));
    }
    totalScans = msLevelScanMap.values().stream().mapToInt(List::size).sum();
  }


  @Override
  public String getTaskDescription() {
    return "Denoising data file " + file.getName();
  }

  @Override
  public double getFinishedPercentage() {
    return processedScans / (double) totalScans;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);

    msLevelScanMap.forEach((msLevel, scans) -> {

      for (int i = 0; i < scans.size(); i++) {
        final Scan scan = scans.get(i);
        final int startIndex = Math.max(0, i - width / 2);
        final int endIndex = Math.min(scans.size() - 1, i + width / 2);

        if (scan instanceof Frame frame) {
//          final List<Frame> sublist = (List<Frame>) (List<? extends Scan>) scans.subList(
//              startIndex, endIndex + 1);

//          final List<MobilityScan> mobilityScans = sublist.stream()
//              .flatMap(f -> f.getMobilityScans().stream()).toList();
          final List<MobilityScan> mobilityScans = frame.getMobilityScans();
          final Map<MobilityScan, double[][]> denoised = DenoisingUtils.denoiseToDataPoints(
              mobilityScans, mzTol, numDetections);

          final List<double[][]> massListData = mobilityScans.stream()
              .map(denoised::get).toList();

          frame.getMobilityScanStorage()
              .setMassLists(getMemoryMapStorage(), massListData);

          logger.finest("Removed " + (
              frame.getMobilityScanStorage().getRawTotalNumPoints()
                  - frame.getMobilityScanStorage().getMassListTotalNumPoints()
                  + " data points from mobility scans in frame "
                  + frame.getFrameId()));
        }

        if (scan.getNumberOfDataPoints() > 0) {
          final Map<Scan, double[][]> denoised = DenoisingUtils.denoiseToDataPoints(
              scans.subList(startIndex, endIndex + 1), mzTol, numDetections);
          final MassList ml = new SimpleMassList(getMemoryMapStorage(),
              denoised.get(scan));
          scan.addMassList(ml);
        }

        processedScans++;
        if (isCanceled()) {
          return;
        }
      }
    });

    setStatus(TaskStatus.FINISHED);
  }

  /*private MassSpectrum getCentroidedSpectrum(Scan scan) {
    if (scan.getSpectrumType().isCentroided()) {
      return scan;
    }
    return centroidedSpectra.computeIfAbsent(scan, s -> {
      final double[][] massValues = exactMass.getMassValues(s,
          exactMassDetectorParameters);
      final SimpleMassList masslist = new SimpleMassList(null, massValues);
      rawSpectra.put(masslist, s);
      return masslist;
    });
  }*/

}
