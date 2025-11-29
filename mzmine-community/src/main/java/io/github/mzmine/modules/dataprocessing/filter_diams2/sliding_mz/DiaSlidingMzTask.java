/*
 * Copyright (c) 2004-2025 The mzmine Development Team
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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.dataprocessing.filter_diams2.sliding_mz;

import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList.FeatureListAppliedMethod;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.modules.dataprocessing.filter_diams2.DiaCorrelationOptions;
import io.github.mzmine.modules.dataprocessing.filter_diams2.DiaMs2CorrParameters;
import io.github.mzmine.modules.dataprocessing.filter_diams2.DiaMs2CorrTask;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.submodules.ValueWithParameters;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.taskcontrol.operations.AbstractTaskSubProcessor;
import io.github.mzmine.taskcontrol.operations.TaskSubProcessor;
import io.github.mzmine.util.collections.BinarySearch;
import io.github.mzmine.util.collections.BinarySearch.DefaultTo;
import io.github.mzmine.util.collections.IndexRange;
import io.github.mzmine.util.scans.SpectraMerging;
import io.github.mzmine.util.scans.SpectraMerging.IntensityMergingType;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DiaSlidingMzTask extends AbstractTaskSubProcessor {

  private final ModularFeatureList flist;
  private final DiaMs2CorrParameters mainParam;
  private final ParameterSet parameters;
  private final ScanSelection scanSelection;
  private final ValueWithParameters<DiaCorrelationOptions> pregrouping;
  private final TaskSubProcessor pregroupingTask;
  private final int totalRows;
  private final MZTolerance mzTol = MZTolerance.FIFTEEN_PPM_OR_FIVE_MDA;
  private int processed = 0;

  protected DiaSlidingMzTask(ModularFeatureList flist, DiaMs2CorrParameters mainParam,
      ParameterSet parameters, @NotNull DiaMs2CorrTask mainTask) {
    super(mainTask);
    this.flist = flist;
    this.mainParam = mainParam;
    this.parameters = parameters;

    totalRows = flist.getNumberOfRows();
    scanSelection = mainParam.getValue(DiaMs2CorrParameters.ms2ScanSelection);
    pregrouping = parameters.getParameter(DiaSlidingMzParameters.pregrouping)
        .getValueWithParameters();

    pregroupingTask = pregrouping.value()
        .createLogicTask(flist, mainParam, pregrouping.parameters(),
            (DiaMs2CorrTask) getParentTask());
  }

  @Override
  public void process() {
    final RawDataFile file = flist.getRawDataFile(0);
    final List<Scan> ms2Scans = scanSelection.getMatchingScans(file.getScans());
    final List<? extends Scan> ms1Scans = flist.getSeletedScans(file);

    if (ms1Scans == null) {
      parentTask.error(
          "No MS1 scans set for feature list %s. Applied methods: %s".formatted(flist.getName(),
              flist.getAppliedMethods().stream().map(FeatureListAppliedMethod::getModule)
                  .map(MZmineModule::getName).collect(Collectors.joining(", "))));
    }

    pregroupingTask.process();
    if (isCanceled()) {
      return;
    }

    final List<FeatureListRow> rows = flist.getRowsCopy();

    for (final FeatureListRow row : rows) {
      final Feature feature = row.getFeature(file);
      if (feature == null || feature.getFeatureStatus() == FeatureStatus.UNKNOWN) {
        processed++;
        continue;
      }

      final List<Scan> ms2Cycle = getMs2CycleForFeature(feature, ms1Scans, ms2Scans);
      if (ms2Cycle == null) {
        continue;
      }

      final double[] relevantMzs = getRelevantMzs(feature);

      for (int i = 0; i < ms2Cycle.size(); i++) {

      }
    }
  }

  private double[] getRelevantMzs(Feature feature) {
    final List<Scan> ms2s = feature.getAllMS2FragmentScans();
    final double[] relevantMzs;
    if (ms2s.size() > 1) {
      final double[][] relevantPeaks = SpectraMerging.calculatedMergedMzsAndIntensities(ms2s,
          SpectraMerging.defaultMs2MergeTol, IntensityMergingType.SUMMED,
          SpectraMerging.DEFAULT_CENTER_FUNCTION, null, null, null);
      relevantMzs = relevantPeaks[0];
    } else {
      relevantMzs = new double[ms2s.getFirst().getNumberOfDataPoints()];
      ms2s.getFirst().getMzValues(relevantMzs);
    }
    return relevantMzs;
  }

  private @Nullable List<Scan> getMs2CycleForFeature(@NotNull final Feature feature,
      List<? extends Scan> ms1Scans, List<Scan> ms2Scans) {
    final Scan bestMs1 = feature.getRepresentativeScan();
    final int bestMs1Index = BinarySearch.binarySearch(bestMs1.getRetentionTime(),
        DefaultTo.CLOSEST_VALUE, ms1Scans.size(), i -> ms1Scans.get(i).getRetentionTime());
    final int otherMs1Index =
        bestMs1Index > 0 ? bestMs1Index - 1 : Math.min(bestMs1Index + 1, ms1Scans.size() - 1);
    final float ms2RtRangeStart = ms1Scans.get(Math.min(bestMs1Index, otherMs1Index))
        .getRetentionTime();
    final float ms2RtRangeEnd = ms1Scans.get(Math.max(bestMs1Index, otherMs1Index))
        .getRetentionTime();
    if (bestMs1Index - otherMs1Index == 0) {
      return null;
    }
    final IndexRange ms2CycleIndices = BinarySearch.indexRange(ms2RtRangeStart, ms2RtRangeEnd,
        ms2Scans, Scan::getRetentionTime);
    final List<Scan> ms2Cycle = ms2CycleIndices.sublist(ms2Scans);// todo create copy?

    if (ms2Cycle.size() < 50) {
      throw new RuntimeException(
          "Sliding mz window DIA selected, but less than 50 scans in a cycle. Are you sure this is the correct DIA mode?");
    }
    return ms2Cycle;
  }

  @Override
  public @NotNull String getTaskDescription() {
    return "";
  }

  @Override
  public double getFinishedPercentage() {
    return pregroupingTask.getFinishedPercentage() * 0.5;
  }
}
