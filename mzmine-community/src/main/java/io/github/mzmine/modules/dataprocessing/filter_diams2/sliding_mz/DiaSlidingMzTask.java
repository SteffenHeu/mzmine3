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

import com.google.common.collect.TreeRangeMap;
import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.PseudoSpectrumType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.SimpleRange.SimpleDoubleRange;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList.FeatureListAppliedMethod;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.impl.SimplePseudoSpectrum;
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
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.collections.BinarySearch;
import io.github.mzmine.util.collections.BinarySearch.DefaultTo;
import io.github.mzmine.util.collections.IndexRange;
import io.github.mzmine.util.scans.SpectraMerging;
import io.github.mzmine.util.scans.SpectraMerging.IntensityMergingType;
import it.unimi.dsi.fastutil.doubles.Double2ObjectMap;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DiaSlidingMzTask extends AbstractTaskSubProcessor {

  private static final Logger logger = Logger.getLogger(DiaSlidingMzTask.class.getName());

  private final ModularFeatureList flist;
  private final DiaMs2CorrParameters mainParam;
  private final ParameterSet parameters;
  private final ScanSelection scanSelection;
  private final ValueWithParameters<DiaCorrelationOptions> pregrouping;
  private final TaskSubProcessor pregroupingTask;
  private final int totalRows;
  private final MZTolerance mzTol = MZTolerance.FIFTEEN_PPM_OR_FIVE_MDA;
  private final MemoryMapStorage temp = MemoryMapStorage.forFeatureList();
  private final ModularFeatureList dummy;
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

    dummy = new ModularFeatureList("dummy", temp, flist.getNumberOfRows(), flist.getNumberOfRows(),
        flist.getRawDataFiles().get(0));
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

    final TreeRangeMap<Float, CycleMassograms> massogramBuffer = TreeRangeMap.create();

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

      CycleMassograms cycleMassograms = massogramBuffer.get(
          ms2Cycle.get(ms2Cycle.size() / 2).getRetentionTime());
      if (cycleMassograms == null) {
        CycleMassograms buffered = new CycleMassograms(ms2Cycle);
        massogramBuffer.put(buffered.rtRange(), buffered);
        cycleMassograms = buffered;
      }

      final double[] relevantMzs = getRelevantMzs(feature);
      if (relevantMzs.length < 1) {
        continue;
      }

      final double featureMz = feature.getMZ();
      final int closestIsolationIndex = BinarySearch.binarySearch(
          cycleMassograms.isolationCenters(), featureMz, DefaultTo.CLOSEST_VALUE, 0,
          cycleMassograms.ms2Scans().size());
      final SimpleDoubleRange isolationWindow = cycleMassograms.isolationRanges()
          .get(closestIsolationIndex);
      final double isolationWidth = isolationWindow.length();
      final IndexRange indexRange = BinarySearch.indexRange(cycleMassograms.isolationCenters(),
          featureMz - isolationWidth / 2, featureMz + isolationWidth / 2);
      final double quadStep =
          cycleMassograms.isolationCenter(1) - cycleMassograms.isolationCenter(0);
      final int maxToleranceWindow = (int) Math.ceil((isolationWidth / 2) / quadStep);

      logger.finest("Searching in scans %d (%.2f) - %d (%.2f) with tolerance window %d".formatted(
          indexRange.min(), cycleMassograms.isolationRange(indexRange.min()).upper(),
          indexRange.maxInclusive(),
          cycleMassograms.isolationRange(indexRange.maxInclusive()).lower(), maxToleranceWindow));

      final Object2IntArrayMap<IonTimeSeries<?>> traceMaxIndices = getTraceMaxIndices(
          closestIsolationIndex, indexRange, maxToleranceWindow, cycleMassograms, relevantMzs);

      DoubleArrayList mzs = new DoubleArrayList();
      DoubleArrayList intensities = new DoubleArrayList();
      for (Entry<IonTimeSeries<?>> seriesEntry : traceMaxIndices.object2IntEntrySet()) {
        final ModularFeature mzFeature = new ModularFeature(dummy, file,
            seriesEntry.getKey().subSeries(temp, indexRange.min(), indexRange.maxExclusive()),
            FeatureStatus.MANUAL);
        mzs.add(mzFeature.getMZ());
        mzs.add(mzFeature.getHeight());
      }

      SimplePseudoSpectrum mzCorrelatedSpectrum = new SimplePseudoSpectrum(file, 2, feature.getRT(),
          null, mzs.toDoubleArray(), intensities.toDoubleArray(),
          feature.getRepresentativePolarity(), null, PseudoSpectrumType.LC_DIA);
      feature.setAllMS2FragmentScans(List.of(mzCorrelatedSpectrum));
      processed++;
    }
  }

  private @NotNull Object2IntArrayMap<IonTimeSeries<?>> getTraceMaxIndices(
      final int closestIsolationIndex, final IndexRange indexRange, final int maxToleranceWindow,
      @NotNull final CycleMassograms massograms, final double @NotNull [] relevantMzs) {

    final Object2IntArrayMap<IonTimeSeries<?>> traceMaxIndices = new Object2IntArrayMap<>();

    final Double2ObjectMap<IonTimeSeries<?>> traces = massograms.getTraces(relevantMzs, mzTol,
        temp);
    for (final IonTimeSeries<?> trace : traces.values()) {

      // slope at current point
      final double intensityAtClosestIsolation = trace.getIntensity(closestIsolationIndex);
      final double leftSlope =
          trace.getIntensity(closestIsolationIndex - 1) - intensityAtClosestIsolation;
      final double rightSlope =
          intensityAtClosestIsolation - trace.getIntensity(closestIsolationIndex + 1);

      int searchDirection;
      if (leftSlope < 0 && rightSlope < 0) {
        // increasing, search right
        searchDirection = 1;
      } else if (leftSlope > 0 && rightSlope > 0) {
        // decreasing, search left
        searchDirection = -1;
      } else if (leftSlope < 0 && rightSlope > 0) {
        // at maximum
        searchDirection = 0;
      } else {
        // in local minimum, not valid
        continue;
      }

      // get maximum
      double maxIntensity = intensityAtClosestIsolation;
      int maxIndex = closestIsolationIndex;
      for (int i = closestIsolationIndex; i < indexRange.maxExclusive() && i >= indexRange.min();
          i += searchDirection) {
        final double intensity = trace.getIntensity(i);
        if (intensity > maxIntensity) {
          maxIntensity = intensity;
          maxIndex = i;
        } else {
          break;
        }
      }

      if (Math.abs(maxIndex - closestIsolationIndex) <= maxToleranceWindow) {
        traceMaxIndices.put(trace, maxIndex);
      }
    }
    return traceMaxIndices;
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
