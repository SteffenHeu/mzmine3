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

package io.github.mzmine.modules.dataprocessing.filter_imstoraw;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.MassSpectrumType;
import io.github.mzmine.datamodel.MobilityScan;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.FeatureList.FeatureListAppliedMethod;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.datamodel.impl.DDAMsMsInfoImpl;
import io.github.mzmine.datamodel.impl.SimpleScan;
import io.github.mzmine.datamodel.msms.DDAMsMsInfo;
import io.github.mzmine.datamodel.msms.DIAMsMsInfoImpl;
import io.github.mzmine.datamodel.msms.IonMobilityMsMsInfo;
import io.github.mzmine.datamodel.msms.MsMsInfo;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MathUtils;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.scans.SpectraMerging;
import io.github.mzmine.util.scans.SpectraMerging.IntensityMergingType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ImsToRawDataFileTask extends AbstractTask {

  private static final String OUTPUT_SUFFIX = " converted";

  private final MZmineProject project;
  private final IMSRawDataFile sourceFile;
  private final ParameterSet parameters;
  private final List<? extends Frame> selectedFrames;
  private final int mobilityScansToMerge;
  private final double maxTicRsdDeviation;
  private final int totalMobilityScans;
  private int processedMobilityScans;

  ImsToRawDataFileTask(@NotNull MZmineProject project, @NotNull IMSRawDataFile sourceFile,
      @NotNull ParameterSet parameters, @Nullable MemoryMapStorage storage,
      @NotNull Instant moduleCallDate) {
    super(storage, moduleCallDate);
    this.project = project;
    this.sourceFile = sourceFile;
    this.parameters = parameters;
    final ScanSelection scanSelection = parameters.getValue(
        ImsToRawDataFileParameters.scanSelection);
    selectedFrames = scanSelection.getMatchingScans(sourceFile.getFrames());
    mobilityScansToMerge = parameters.getValue(ImsToRawDataFileParameters.mobilityScansToMerge);
    maxTicRsdDeviation = parameters.getValue(ImsToRawDataFileParameters.maxTicRsdDeviation);
    totalMobilityScans = selectedFrames.stream().mapToInt(Frame::getNumberOfMobilityScans).sum();
  }

  @Override
  public String getTaskDescription() {
    return "Converting mobility scans in " + sourceFile.getName();
  }

  @Override
  public double getFinishedPercentage() {
    return totalMobilityScans == 0 ? 0d : (double) processedMobilityScans / totalMobilityScans;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    if (totalMobilityScans == 0) {
      setErrorMessage("No mobility scans found in " + sourceFile.getName());
      setStatus(TaskStatus.ERROR);
      return;
    }

    try {
      final RawDataFile newFile = MZmineCore.createNewFile(sourceFile.getName() + OUTPUT_SUFFIX,
          null, getMemoryMapStorage());
      newFile.setColor(sourceFile.getColor());
      newFile.setStartTimeStamp(sourceFile.getStartTimeStamp());

      final List<TimedMobilityScan> mergeGroup = new ArrayList<>(mobilityScansToMerge);
      final List<SimpleScan> mergedScans = new ArrayList<>();
      int newScanNumber = 1;
      for (int frameIndex = 0; frameIndex < selectedFrames.size(); frameIndex++) {
        final Frame frame = selectedFrames.get(frameIndex);
        final float nextFrameRt = getNextFrameRt(selectedFrames, frameIndex);
        final int numMobilityScans = frame.getNumberOfMobilityScans();

        for (int mobilityScanIndex = 0; mobilityScanIndex < numMobilityScans;
            mobilityScanIndex++) {
          if (isCanceled()) {
            return;
          }

          final MobilityScan mobilityScan = frame.getMobilityScan(mobilityScanIndex);
          if (mobilityScan == null) {
            throw new IllegalStateException(
                "Missing mobility scan " + mobilityScanIndex + " in frame " + frame.getFrameId());
          }
          final float rt = interpolateRt(frame.getRetentionTime(), nextFrameRt, mobilityScanIndex,
              numMobilityScans);
          mergeGroup.add(new TimedMobilityScan(mobilityScan, rt));
          processedMobilityScans++;

          if (mergeGroup.size() == mobilityScansToMerge) {
            mergedScans.add(createScan(newFile, newScanNumber++, mergeGroup));
            mergeGroup.clear();
          }
        }
      }

      if (!mergeGroup.isEmpty()) {
        mergedScans.add(createScan(newFile, newScanNumber, mergeGroup));
      }

      int retainedScanNumber = 1;
      for (final SimpleScan scan : filterLowTicScans(mergedScans, maxTicRsdDeviation)) {
        scan.setScanNumber(retainedScanNumber++);
        newFile.addScan(scan);
      }

      for (final FeatureListAppliedMethod appliedMethod : sourceFile.getAppliedMethods()) {
        newFile.getAppliedMethods().add(appliedMethod);
      }
      newFile.getAppliedMethods().add(
          new SimpleFeatureListAppliedMethod(ImsToRawDataFileModule.class, parameters,
              getModuleCallDate()));
      project.addFile(newFile);
      setStatus(TaskStatus.FINISHED);
    } catch (Exception e) {
      setErrorMessage(e.getMessage() != null ? e.getMessage() : e.toString());
      setStatus(TaskStatus.ERROR);
    }
  }

  private SimpleScan createScan(@NotNull RawDataFile newFile, int scanNumber,
      @NotNull List<TimedMobilityScan> group) {
    final MobilityScan representative = group.getFirst().scan();
    final double[][] data = mergeData(group);
    final float rt = (float) group.stream().mapToDouble(TimedMobilityScan::rt).average()
        .orElseThrow();
    final Range<Double> scanningMzRange = group.stream().map(TimedMobilityScan::scan)
        .map(MobilityScan::getScanningMZRange).filter(range -> range != null).reduce(Range::span)
        .orElse(null);
    final MassSpectrumType spectrumType = group.size() == 1 ? representative.getSpectrumType()
        : MassSpectrumType.CENTROIDED;

    return new SimpleScan(newFile, scanNumber, representative.getMSLevel(), rt,
        toRegularMsMsInfo(representative.getMsMsInfo()), data[0], data[1], spectrumType,
        representative.getPolarity(), representative.getScanDefinition(), scanningMzRange,
        representative.getInjectionTime());
  }

  private static double[][] mergeData(@NotNull List<TimedMobilityScan> group) {
    if (group.size() == 1) {
      final MobilityScan scan = group.getFirst().scan();
      return new double[][]{scan.getMzValues(new double[0]),
          scan.getIntensityValues(new double[0])};
    }

    final List<MobilityScan> scans = group.stream().map(TimedMobilityScan::scan).toList();
    final var tolerance = scans.getFirst().getMSLevel() == 1 ? SpectraMerging.defaultMs1MergeTol
        : SpectraMerging.defaultMs2MergeTol;
    return SpectraMerging.calculatedMergedMzsAndIntensities(scans, tolerance,
        IntensityMergingType.SUMMED, SpectraMerging.DEFAULT_CENTER_FUNCTION, null, null, null);
  }

  static List<SimpleScan> filterLowTicScans(@NotNull List<SimpleScan> scans,
      double maxRsdDeviation) {
    if (scans.size() < 2) {
      return scans;
    }

    final double[] tics = scans.stream().mapToDouble(scan -> scan.getTIC()).toArray();
    final double meanTic = MathUtils.calcAvg(tics);
    final double relativeStandardDeviation = MathUtils.calcRelativeStd(tics);
    if (!(meanTic > 0d) || !Double.isFinite(relativeStandardDeviation)
        || relativeStandardDeviation == 0d) {
      return scans;
    }

    final double minimumTic = meanTic * (1d - maxRsdDeviation * relativeStandardDeviation);
    return scans.stream().filter(scan -> scan.getTIC() >= minimumTic).toList();
  }

  private static @Nullable MsMsInfo toRegularMsMsInfo(@Nullable MsMsInfo info) {
    if (!(info instanceof IonMobilityMsMsInfo)) {
      return info;
    }
    if (info instanceof DDAMsMsInfo ddaInfo) {
      return new DDAMsMsInfoImpl(ddaInfo.getIsolationMz(), ddaInfo.getPrecursorCharge(),
          ddaInfo.getActivationEnergy(), null, null, ddaInfo.getMsLevel(),
          ddaInfo.getActivationMethod(), ddaInfo.getIsolationWindow());
    }
    return new DIAMsMsInfoImpl(info.getActivationEnergy(), null, info.getMsLevel(),
        info.getActivationMethod(), info.getIsolationWindow());
  }

  static float interpolateRt(float frameRt, float nextFrameRt, int mobilityScanIndex,
      int totalMobilityScans) {
    return frameRt + (float) mobilityScanIndex / totalMobilityScans * (nextFrameRt - frameRt);
  }

  private static float getNextFrameRt(@NotNull List<? extends Frame> frames, int frameIndex) {
    if (frameIndex + 1 < frames.size()) {
      return frames.get(frameIndex + 1).getRetentionTime();
    }
    if (frameIndex > 0) {
      final float currentRt = frames.get(frameIndex).getRetentionTime();
      return currentRt + currentRt - frames.get(frameIndex - 1).getRetentionTime();
    }
    return frames.get(frameIndex).getRetentionTime();
  }

  private record TimedMobilityScan(@NotNull MobilityScan scan, float rt) {

  }
}
