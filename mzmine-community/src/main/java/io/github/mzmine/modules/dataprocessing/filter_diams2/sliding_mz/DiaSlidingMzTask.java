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

package io.github.mzmine.modules.dataprocessing.filter_diams2.sliding_mz;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.MergedMassSpectrum;
import io.github.mzmine.datamodel.MergedMassSpectrum.MergingType;
import io.github.mzmine.datamodel.PseudoSpectrumType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.SimpleRange.SimpleDoubleRange;
import io.github.mzmine.datamodel.featuredata.FeatureDataUtils;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList.FeatureListAppliedMethod;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.impl.DDAMsMsInfoImpl;
import io.github.mzmine.datamodel.impl.SimplePseudoSpectrum;
import io.github.mzmine.datamodel.msms.MsMsInfo;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.modules.dataprocessing.filter_diams2.DiaCorrelationOptions;
import io.github.mzmine.modules.dataprocessing.filter_diams2.DiaMs2CorrParameters;
import io.github.mzmine.modules.dataprocessing.filter_diams2.DiaMs2CorrTask;
import io.github.mzmine.modules.dataprocessing.filter_diams2.no_corr.DiaMs2NoCorrParameters;
import io.github.mzmine.modules.dataprocessing.filter_diams2.rt_corr.DiaMs2RtCorrParameters;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.submodules.ValueWithParameters;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.taskcontrol.operations.AbstractTaskSubProcessor;
import io.github.mzmine.taskcontrol.operations.TaskSubProcessor;
import io.github.mzmine.util.ArrayUtils;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.RangeUtils;
import io.github.mzmine.util.collections.BinarySearch;
import io.github.mzmine.util.collections.BinarySearch.DefaultTo;
import io.github.mzmine.util.collections.IndexRange;
import io.github.mzmine.util.scans.SpectraMerging;
import io.github.mzmine.util.scans.SpectraMerging.IntensityMergingType;
import it.unimi.dsi.fastutil.doubles.Double2ObjectMap;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntToDoubleFunction;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DiaSlidingMzTask extends AbstractTaskSubProcessor {

  private static final Logger logger = Logger.getLogger(DiaSlidingMzTask.class.getName());

  // decision: use the validated 10 Da edge span for a 5 Da isolation window.
  static final SlidingMzShapeAcceptanceMode SHAPE_ACCEPTANCE_MODE = SlidingMzShapeAcceptanceMode.TOP_TO_MAX_EDGE;
  static final double SHAPE_EDGE_WINDOW_MULTIPLIER = 2d;
  static final double MINIMUM_SHAPE_TOP_EDGE_RATIO = 2d;
  static final int MINIMUM_SHAPE_CONSECUTIVE_POINTS = 5;
  static final int SHAPE_ZERO_MARGIN_INDICES = 2;

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
  private final double minFragmentIntensity;
  private final @NotNull String diagnosticConfiguration;
  private final boolean logShapeMetrics;
  private int processed = 0;

  protected DiaSlidingMzTask(@NotNull final ModularFeatureList flist,
      @NotNull final DiaMs2CorrParameters mainParam, @NotNull final ParameterSet parameters,
      @NotNull final DiaMs2CorrTask mainTask) {
    super(mainTask);
    this.flist = flist;
    this.mainParam = mainParam;
    this.parameters = parameters;

    totalRows = flist.getNumberOfRows();
    scanSelection = mainParam.getValue(DiaMs2CorrParameters.ms2ScanSelection);
    pregrouping = parameters.getParameter(DiaSlidingMzParameters.pregrouping)
        .getValueWithParameters();
    diagnosticConfiguration = DiaSlidingMzDiagnostics.CONFIGURATION_LABELS.getOrDefault(
        pregrouping.value(), "unassigned");
    logShapeMetrics = DiaSlidingMzDiagnostics.LOG_SHAPE_METRICS;

    minFragmentIntensity = switch (pregrouping.value()) {
      case RT_CORRELATION ->
          pregrouping.parameters().getValue(DiaMs2RtCorrParameters.minMs2Intensity);
      case NO_CORRELATION -> pregrouping.parameters().getValue(DiaMs2NoCorrParameters.minIntensity);
      case SLIDING_MZ -> throw new RuntimeException(
          "Select a valid pregrouping option, RT correlation or no correlation");
    };

    pregroupingTask = pregrouping.value()
        .createLogicTask(flist, mainParam, pregrouping.parameters(),
            (DiaMs2CorrTask) getParentTask());

    dummy = new ModularFeatureList("dummy", temp, flist.getNumberOfRows(), flist.getNumberOfRows(),
        flist.getRawDataFiles().get(0));
  }

  public static @Nullable List<Scan> getMs2CycleForRt(final float rt, List<? extends Scan> ms1Scans,
      List<Scan> ms2Scans, @Nullable MemoryMapStorage temp) {
    final int bestMs1Index = BinarySearch.binarySearch(rt, DefaultTo.CLOSEST_VALUE, ms1Scans.size(),
        i -> ms1Scans.get(i).getRetentionTime());

    final int startIndex = Math.max(bestMs1Index - 1, 0);
    final int endIndex = Math.min(bestMs1Index + 2, ms1Scans.size() - 1);

    final double ms2RtRangeStart = ms1Scans.get(startIndex).getRetentionTime();
    final double ms2RtRangeEnd = ms1Scans.get(endIndex).getRetentionTime();

    final IndexRange ms2CycleIndices = BinarySearch.indexRange(ms2RtRangeStart, ms2RtRangeEnd,
        ms2Scans, Scan::getRetentionTime);
    final var threeMs2Cycles = ms2CycleIndices.sublist(ms2Scans);

    if (threeMs2Cycles.size() < 50 * 3) {
      throw new RuntimeException(
          "Sliding mz window DIA selected, but less than 50 scans in a cycle. Are you sure this is the correct DIA mode?");
    }

    Map<@Nullable Range<Double>, List<Scan>> groupedByWindow = threeMs2Cycles.stream()
        .filter(s -> s.getMsMsInfo() != null && s.getMsMsInfo().getIsolationWindow() != null)
        .collect(Collectors.groupingBy(s -> s.getMsMsInfo().getIsolationWindow()));

    var ms2Cycle = groupedByWindow.entrySet().stream().map(e -> {
      MergedMassSpectrum merged = SpectraMerging.mergeSpectra(e.getValue(),
          SpectraMerging.defaultMs2MergeTol, IntensityMergingType.SUMMED, MergingType.ALL_ENERGIES,
          null, null, 2, SpectraMerging.DEFAULT_CENTER_FUNCTION, null);
      return (Scan) merged;
    }).sorted(Comparator.comparing(Scan::getRetentionTime)).toList();
//    logger.finest("Merged for scan index " + bestMs1Index);
    return ms2Cycle;
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

//    final TreeRangeMap<Float, CycleMassograms> massogramBuffer = TreeRangeMap.create();
    final HashMap<Float, CycleMassograms> massogramBuffer = new HashMap<>();

    for (final FeatureListRow row : rows) {
      final Feature feature = row.getFeature(file);
      if (feature == null || feature.getFeatureStatus() == FeatureStatus.UNKNOWN) {
        processed++;
        continue;
      }

      CycleMassograms cycleMassograms = massogramBuffer.get(feature.getRT());
      if (cycleMassograms == null) {
        final List<Scan> ms2Cycle = getMs2CycleForRt(feature.getRT(), ms1Scans, ms2Scans, temp);
        if (ms2Cycle == null) {
          continue;
        }
        CycleMassograms buffered = new CycleMassograms(ms2Cycle, dummy);
        massogramBuffer.put(feature.getRT(), buffered);
        cycleMassograms = buffered;
      }

      final ExpectedSpectrum expectedSpectrum = findExpectedSpectrum(feature);
      final double[] relevantMzs = getRelevantMzs(feature, expectedSpectrum);
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
      final IndexRange isolationIndexRange = BinarySearch.indexRange(
          cycleMassograms.isolationCenters(), featureMz - isolationWidth / 2,
          featureMz + isolationWidth / 2);
      final double quadStep =
          cycleMassograms.isolationCenter(1) - cycleMassograms.isolationCenter(0);
      final int maxToleranceWindow = (int) Math.ceil((isolationWidth / 2) / quadStep);

      /*logger.finest("Searching in scans %d (%.2f) - %d (%.2f) with tolerance window %d".formatted(
          isolationIndexRange.min(), cycleMassograms.isolationRange(isolationIndexRange.min()).upper(),
          isolationIndexRange.maxInclusive(),
          cycleMassograms.isolationRange(isolationIndexRange.maxInclusive()).lower(), maxToleranceWindow));*/

      final Object2IntArrayMap<ModularFeature> massogramMaxIndices = getTraceMaxIndices(feature,
          closestIsolationIndex, isolationIndexRange, maxToleranceWindow, cycleMassograms,
          relevantMzs, expectedSpectrum);

      final DoubleArrayList mzs = new DoubleArrayList();
      final DoubleArrayList intensities = new DoubleArrayList();
      for (Entry<ModularFeature> massogramEntry : massogramMaxIndices.object2IntEntrySet()) {
        final ModularFeature mzFeature = new ModularFeature(dummy, file,
            massogramEntry.getKey().getFeatureData()
                .subSeries(temp, isolationIndexRange.min(), isolationIndexRange.maxExclusive()),
            FeatureStatus.MANUAL);
        mzs.add(mzFeature.getMZ()); // mz average across a few points
        // intensity from where the main feature is in the center of the isolation
        intensities.add(
            massogramEntry.getKey().getFeatureData().getIntensity(closestIsolationIndex));
      }

      if (mzs.isEmpty()) {
        feature.setAllMS2FragmentScans(List.of());
        continue;
      }

//      logger.finest(
//          "Removed %d uncorrelated signals (%d -> %d)".formatted(relevantMzs.length - mzs.size(),
//              relevantMzs.length, mzs.size()));

      final MsMsInfo closestMsMsInfo = cycleMassograms.ms2Scans().getFirst().getMsMsInfo();
      final DDAMsMsInfoImpl msmsInfo = new DDAMsMsInfoImpl(
          cycleMassograms.isolationCenter(closestIsolationIndex), feature.getCharge(),
          closestMsMsInfo.getActivationEnergy(), null, null,
          cycleMassograms.ms2Scans().getFirst().getMSLevel(), closestMsMsInfo.getActivationMethod(),
          cycleMassograms.isolationRange(closestIsolationIndex).guava());
      final SimplePseudoSpectrum mzCorrelatedSpectrum = new SimplePseudoSpectrum(file, 2,
          feature.getRT(), msmsInfo, mzs.toDoubleArray(), intensities.toDoubleArray(),
          feature.getRepresentativePolarity(), null,
          pregrouping.value() == DiaCorrelationOptions.RT_CORRELATION
              ? PseudoSpectrumType.SLIDING_MZ_RT_CORR : PseudoSpectrumType.SLIDING_MZ_NO_RT);
      feature.setAllMS2FragmentScans(List.of(mzCorrelatedSpectrum));
      processed++;

      if (isCanceled()) {
        return;
      }
    }

    long cached = CycleMassograms.cachedRequests.get();
    long total = CycleMassograms.allRequest.get();
    logger.finest("Cached: %d, Total: %d".formatted(cached, total));

  }

  private static @Nullable ExpectedSpectrum findExpectedSpectrum(@NotNull final Feature feature) {
    if (DiaSlidingMzDiagnostics.EXPECTED_SPECTRA.isEmpty()) {
      return null;
    }

    final double precursorMz = feature.getMZ();
    final double rt = feature.getRT();
    return DiaSlidingMzDiagnostics.EXPECTED_SPECTRA.stream().filter(
            expected -> expected.precursorMzRange().contains(precursorMz) && expected.rtRange()
                .contains(rt))
        // decision: precursor m/z identifies a target first; RT resolves overlapping m/z targets.
        .min(Comparator.comparingDouble((ExpectedSpectrum expected) -> Math.abs(
                RangeUtils.rangeCenter(expected.precursorMzRange()).doubleValue() - precursorMz))
            .thenComparingDouble(expected -> Math.abs(
                RangeUtils.rangeCenter(expected.rtRange()).doubleValue() - rt))).orElse(null);
  }

  private boolean checkMassogramShape(@NotNull final ModularFeature massogram, final int maxIndex,
      @NotNull final Feature feature, final double requestedMz,
      @Nullable final ExpectedSpectrum expectedSpectrum,
      @Nullable final ExpectedFragment expectedFragment,
      @NotNull final IntToDoubleFunction decisionIntensityAt,
      @NotNull final CycleMassograms massograms, final int precursorCenterIndex) {

    final IonTimeSeries<? extends Scan> series = massogram.getFeatureData();
    final double maxIntensity = series.getIntensity(maxIndex);
    if (maxIntensity < minFragmentIntensity) {
      logExpectedMzRejection(feature, expectedSpectrum, expectedFragment, requestedMz, massogram,
          ExpectedFragmentRejectionReason.BELOW_MINIMUM_INTENSITY, String.format(Locale.ROOT,
              "apex intensity %.3f at index %d is below the minimum fragment intensity %.3f",
              maxIntensity, maxIndex, minFragmentIntensity));
      return false;
    }

    double prevIntensity = decisionIntensityAt.applyAsDouble(maxIndex);
    final int numDecreasing = 2;
    int numNonZero = 1;
    ExpectedFragmentRejectionReason slopeRejectionReason = null;
    String slopeRejectionDetails = null;

    for (int i = maxIndex - 1; i >= maxIndex - numDecreasing && i > 0; i--) {
      final double currentIntensity = decisionIntensityAt.applyAsDouble(i);
      if (currentIntensity > prevIntensity && slopeRejectionReason == null) {
        slopeRejectionReason = ExpectedFragmentRejectionReason.RISING_LEFT_OF_APEX;
        slopeRejectionDetails = String.format(Locale.ROOT,
            "massogram rises left of the proposed apex: intensity %.3f at index %d exceeds %.3f at index %d",
            currentIntensity, i, prevIntensity, i + 1);
      }
      if (currentIntensity > 0) {
        numNonZero++;
      }
      prevIntensity = currentIntensity;
    }

    prevIntensity = decisionIntensityAt.applyAsDouble(maxIndex);
    for (int i = maxIndex + 1; i <= maxIndex + numDecreasing && i < series.getNumberOfValues();
        i++) {
      final double currentIntensity = decisionIntensityAt.applyAsDouble(i);
      if (currentIntensity > prevIntensity && slopeRejectionReason == null) {
        slopeRejectionReason = ExpectedFragmentRejectionReason.RISING_RIGHT_OF_APEX;
        slopeRejectionDetails = String.format(Locale.ROOT,
            "massogram rises right of the proposed apex: intensity %.3f at index %d exceeds %.3f at index %d",
            currentIntensity, i, prevIntensity, i - 1);
      }
      if (currentIntensity > 0) {
        numNonZero++;
      }
      prevIntensity = currentIntensity;
    }

    if (numNonZero < 3) { // todo for final: move from hardcoded to minimumShapeConsecutivePoints?
      logExpectedMzRejection(feature, expectedSpectrum, expectedFragment, requestedMz, massogram,
          ExpectedFragmentRejectionReason.TOO_FEW_NON_ZERO_POINTS, String.format(Locale.ROOT,
              "massogram contains only %d non-zero point(s) around the proposed apex; at least 3 are required",
              numNonZero));
      return false;
    }

    final double shapeIsolationWidth = massograms.isolationRange(precursorCenterIndex).length();
    final SlidingMzShapeAcceptanceResult alternative =
        slopeRejectionReason != null || logShapeMetrics ? SlidingMzShapeAcceptance.evaluate(
            SHAPE_ACCEPTANCE_MODE, series, massograms.isolationCenters(), precursorCenterIndex,
            maxIndex, shapeIsolationWidth, SHAPE_EDGE_WINDOW_MULTIPLIER,
            MINIMUM_SHAPE_TOP_EDGE_RATIO, MINIMUM_SHAPE_CONSECUTIVE_POINTS,
            SHAPE_ZERO_MARGIN_INDICES) : null;
    if (logShapeMetrics) {
      logExpectedMzShapeMetric(feature, expectedSpectrum, expectedFragment, requestedMz, massogram,
          Objects.requireNonNull(alternative).details());
    }
    if (slopeRejectionReason == null) {
      return true;
    }

    Objects.requireNonNull(alternative);
    final String combinedDetails = slopeRejectionDetails + "; " + alternative.details();
    if (alternative.accepted()) {
      logExpectedMzAlternativeAcceptance(feature, expectedSpectrum, expectedFragment, requestedMz,
          massogram, slopeRejectionReason, combinedDetails);
      return true;
    }
    logExpectedMzRejection(feature, expectedSpectrum, expectedFragment, requestedMz, massogram,
        slopeRejectionReason, combinedDetails);
    return false;
//    logger.finest(
//        "Removing %d/%d peaks due to not matching mass isolation shapes.".formatted(toRemove.size(),
//            traceMaxIndices.size()));

  }


  private boolean shapeCheck2(ModularFeature massogram, final int maxIndex,
      final int precursorMaxIndex, CycleMassograms massograms, final int maxToleranceWindow) {
    final IonTimeSeries<? extends Scan> series = massogram.getFeatureData();

    final double windowCenter = massograms.isolationCenter(precursorMaxIndex);
    final SimpleDoubleRange range_wide = massograms.isolationRange(precursorMaxIndex);

    final double auc_tot_raw = massogram.getArea();
    final double originalRangeLength = range_wide.length() / CycleMassograms.isolationWidthFactor;
    final SimpleDoubleRange range_core = new SimpleDoubleRange(
        windowCenter - originalRangeLength / 2, windowCenter + originalRangeLength / 2);
    final int local_apex = maxIndex;
    final double local_height = series.getIntensity(maxIndex);

    // area in the main precursor isolatio window
    final double auc_local = FeatureDataUtils.calculateArea(series, precursorMaxIndex - 1,
        precursorMaxIndex + 2);

    final double auc_local_large = FeatureDataUtils.calculateArea(series,
        Math.max(0, precursorMaxIndex - maxToleranceWindow),
        Math.min(precursorMaxIndex + maxToleranceWindow, series.getNumberOfValues()));

    final double auc_ratio_large =
        auc_local <= 0 ? Double.POSITIVE_INFINITY : auc_local_large / auc_local;
    final double auc_ratio_tot =
        auc_tot_raw <= 0 ? 0 : auc_local / auc_tot_raw; // why 0 here but infinity before?

    final double max_area =
        massogram.getHeight() * RangeUtils.rangeLength(massogram.getRawDataPointsRTRange());
    final double auc_tot = max_area <= 0 ? 0 : auc_tot_raw / max_area;
    final double auc_score = auc_ratio_large <= 0 ? 0 : auc_ratio_tot * auc_tot / auc_ratio_large;

    return auc_ratio_tot >= 0.025 && auc_ratio_large <= 5.5 && auc_tot >= 0.2 && auc_score >= 0.001;
  }


  private @NotNull Object2IntArrayMap<ModularFeature> getTraceMaxIndices(
      @NotNull final Feature feature, final int closestIsolationIndex,
      @NotNull final IndexRange isolationIndexRange,
      final int maxToleranceWindow, @NotNull final CycleMassograms massograms,
      final double @NotNull [] relevantMzs, @Nullable final ExpectedSpectrum expectedSpectrum) {

    final Object2IntArrayMap<ModularFeature> ms2FeaturesMaxIndices = new Object2IntArrayMap<>();

    final Double2ObjectMap<ModularFeature> massogramFeatures = massograms.getTraces(relevantMzs,
        mzTol, temp);
    for (final var massogramEntry : massogramFeatures.double2ObjectEntrySet()) {
      final double requestedMz = massogramEntry.getDoubleKey();
      final ModularFeature massogramFeature = massogramEntry.getValue();
      final ExpectedFragment expectedFragment =
          expectedSpectrum == null ? null : expectedSpectrum.fragments().get(requestedMz);
      final IonTimeSeries<?> trace = massogramFeature.getFeatureData();
      final SlidingMzTraceApexEvaluation apexEvaluation = HighIntensityDespikedSlidingMzTraceApexFinder.evaluate(
          trace::getIntensity, trace.getNumberOfValues(), closestIsolationIndex,
          isolationIndexRange, maxToleranceWindow, minFragmentIntensity);
      final SlidingMzTraceApexResult apexResult = apexEvaluation.result();
      final int apexIndex;
      if (apexResult.isAccepted()) {
        apexIndex = apexResult.apexIndex();
      } else if (apexResult.rejectionReason()
          == ExpectedFragmentRejectionReason.INVALID_LOCAL_SLOPE) {
        final SlidingMzShapeAcceptanceResult alternative = SlidingMzShapeAcceptance.evaluate(
            SHAPE_ACCEPTANCE_MODE, trace, massograms.isolationCenters(), closestIsolationIndex,
            closestIsolationIndex, massograms.isolationRange(closestIsolationIndex).length(),
            SHAPE_EDGE_WINDOW_MULTIPLIER, MINIMUM_SHAPE_TOP_EDGE_RATIO,
            MINIMUM_SHAPE_CONSECUTIVE_POINTS, SHAPE_ZERO_MARGIN_INDICES);
        final String combinedDetails = apexResult.details() + "; " + alternative.details();
        if (!alternative.accepted()) {
          logExpectedMzRejection(feature, expectedSpectrum, expectedFragment, requestedMz,
              massogramFeature, Objects.requireNonNull(apexResult.rejectionReason()),
              combinedDetails);
          continue;
        }
        apexIndex = alternative.preferredApexIndex();
        logExpectedMzAlternativeAcceptance(feature, expectedSpectrum, expectedFragment, requestedMz,
            massogramFeature, Objects.requireNonNull(apexResult.rejectionReason()),
            combinedDetails);
      } else {
        logExpectedMzRejection(feature, expectedSpectrum, expectedFragment, requestedMz,
            massogramFeature, Objects.requireNonNull(apexResult.rejectionReason()),
            apexResult.details());
        continue;
      }
      if (checkMassogramShape(massogramFeature, apexIndex, feature, requestedMz, expectedSpectrum,
          expectedFragment, apexEvaluation.decisionIntensityAt(), massograms,
          closestIsolationIndex)) {
        ms2FeaturesMaxIndices.put(massogramFeature, apexIndex);
//        if(shapeCheck2(massogramFeature, maxIndex, closestIsolationIndex, massograms, maxToleranceWindow)) {
//          ms2FeaturesMaxIndices.put(massogramFeature, maxIndex);
//        }
      }
    }

    final double[] intensities = ms2FeaturesMaxIndices.object2IntEntrySet().stream()
        .mapToDouble(e -> e.getKey().getFeatureData().getIntensity(e.getIntValue())).toArray();
    ArrayUtils.sum(intensities);

    return ms2FeaturesMaxIndices;
  }

  private double @NotNull [] getRelevantMzs(@NotNull final Feature feature,
      @Nullable final ExpectedSpectrum expectedSpectrum) {
    final List<Scan> ms2s = feature.getAllMS2FragmentScans();
    final double[] relevantMzs;
    if (ms2s.size() > 1) {
      final double[][] relevantPeaks = SpectraMerging.calculatedMergedMzsAndIntensities(ms2s,
          SpectraMerging.defaultMs2MergeTol, IntensityMergingType.SUMMED,
          SpectraMerging.DEFAULT_CENTER_FUNCTION, null, null, null);
      relevantMzs = relevantPeaks[0];
    } else if (ms2s.size() == 1) {
      relevantMzs = new double[ms2s.getFirst().getNumberOfDataPoints()];
      ms2s.getFirst().getMzValues(relevantMzs);
    } else {
      relevantMzs = new double[0];
    }
    logExpectedMzCoverage(feature, relevantMzs, expectedSpectrum);
    return relevantMzs;
  }

  private void logExpectedMzCoverage(@NotNull final Feature feature,
      final double @NotNull [] relevantMzs, @Nullable final ExpectedSpectrum expectedSpectrum) {
    if (expectedSpectrum == null) {
      return;
    }

    final List<ExpectedFragment> expectedFragments = expectedSpectrum.fragments().asMapOfRanges()
        .values().stream().distinct().sorted(Comparator.comparingDouble(ExpectedFragment::mz))
        .toList();
    final Set<ExpectedFragment> containedFragments = Arrays.stream(relevantMzs)
        .mapToObj(expectedSpectrum.fragments()::get).filter(Objects::nonNull)
        .collect(Collectors.toSet());
    final List<ExpectedFragment> missingFragments = expectedFragments.stream()
        .filter(fragment -> !containedFragments.contains(fragment)).toList();
    final int contained = expectedFragments.size() - missingFragments.size();
    final String coverage = expectedFragments.isEmpty() ? "n/a"
        : String.format(Locale.ROOT, "%.1f%%", contained * 100d / expectedFragments.size());
    final String missingMzs = missingFragments.isEmpty() ? "none" : missingFragments.stream().map(
        fragment -> String.format(Locale.ROOT, "%.6f", fragment.mz())).collect(
        Collectors.joining(", "));

    logger.warning(String.format(Locale.ROOT,
        "Sliding-m/z diagnostics | configuration=%s | event=COVERAGE | target=%s"
            + " | precursor_mz=%.6f | rt=%.4f"
            + " | contained=%d | total=%d | missing=%s | details=relevant-mz coverage %s",
        sanitizeLogValue(diagnosticConfiguration), sanitizeLogValue(expectedSpectrum.label()),
        feature.getMZ(), feature.getRT(), contained, expectedFragments.size(), missingMzs,
        coverage));
    for (final ExpectedFragment missingFragment : missingFragments) {
      logExpectedMzRejection(feature, expectedSpectrum, missingFragment, missingFragment.mz(), null,
          ExpectedFragmentRejectionReason.NOT_IN_RELEVANT_MZS,
          "expected fragment was absent from the returned relevant m/z values");
    }
  }

  private void logExpectedMzRejection(@NotNull final Feature feature,
      @Nullable final ExpectedSpectrum expectedSpectrum,
      @Nullable final ExpectedFragment expectedFragment, final double requestedMz,
      @Nullable final ModularFeature massogram,
      @NotNull final ExpectedFragmentRejectionReason reason, @NotNull final String details) {
    if (expectedSpectrum == null || expectedFragment == null) {
      return;
    }

    final String expectedIntensity =
        Double.isFinite(expectedFragment.intensity()) ? String.format(Locale.ROOT,
            "; expected intensity %.3f", expectedFragment.intensity()) : "";
    final String traceMz =
        massogram == null ? "none" : String.format(Locale.ROOT, "%.6f", massogram.getMZ());
    logger.warning(String.format(Locale.ROOT,
        "Sliding-m/z diagnostics | configuration=%s | event=REJECTED | target=%s"
            + " | precursor_mz=%.6f | rt=%.4f | expected_mz=%.6f | reason=%s"
            + " | requested_mz=%.6f | trace_mz=%s | details=%s%s",
        sanitizeLogValue(diagnosticConfiguration), sanitizeLogValue(expectedSpectrum.label()),
        feature.getMZ(), feature.getRT(), expectedFragment.mz(), reason, requestedMz, traceMz,
        sanitizeLogValue(details), expectedIntensity));
  }

  private void logExpectedMzAlternativeAcceptance(@NotNull final Feature feature,
      @Nullable final ExpectedSpectrum expectedSpectrum,
      @Nullable final ExpectedFragment expectedFragment, final double requestedMz,
      @NotNull final ModularFeature massogram,
      @NotNull final ExpectedFragmentRejectionReason originalReason,
      @NotNull final String details) {
    if (expectedSpectrum == null || expectedFragment == null) {
      return;
    }

    final String expectedIntensity =
        Double.isFinite(expectedFragment.intensity()) ? String.format(Locale.ROOT,
            "; expected intensity %.3f", expectedFragment.intensity()) : "";
    logger.warning(String.format(Locale.ROOT,
        "Sliding-m/z diagnostics | configuration=%s | event=ACCEPTED_ALTERNATIVE | target=%s"
            + " | precursor_mz=%.6f | rt=%.4f | expected_mz=%.6f | original_reason=%s"
            + " | criterion=%s | requested_mz=%.6f | trace_mz=%.6f | details=%s%s",
        sanitizeLogValue(diagnosticConfiguration), sanitizeLogValue(expectedSpectrum.label()),
        feature.getMZ(), feature.getRT(), expectedFragment.mz(), originalReason,
        SHAPE_ACCEPTANCE_MODE.name(), requestedMz, massogram.getMZ(), sanitizeLogValue(details),
        expectedIntensity));
  }

  private void logExpectedMzShapeMetric(@NotNull final Feature feature,
      @Nullable final ExpectedSpectrum expectedSpectrum,
      @Nullable final ExpectedFragment expectedFragment, final double requestedMz,
      @NotNull final ModularFeature massogram, @NotNull final String details) {
    if (expectedSpectrum == null || expectedFragment == null) {
      return;
    }

    logger.warning(String.format(Locale.ROOT,
        "Sliding-m/z diagnostics | configuration=%s | event=SHAPE_METRIC | target=%s"
            + " | precursor_mz=%.6f | rt=%.4f | expected_mz=%.6f"
            + " | requested_mz=%.6f | trace_mz=%.6f | details=%s",
        sanitizeLogValue(diagnosticConfiguration), sanitizeLogValue(expectedSpectrum.label()),
        feature.getMZ(), feature.getRT(), expectedFragment.mz(), requestedMz, massogram.getMZ(),
        sanitizeLogValue(details)));
  }

  private static @NotNull String sanitizeLogValue(@NotNull final String value) {
    return value.replace('|', '/').replace('\r', ' ').replace('\n', ' ').trim();
  }

  @Override
  public @NotNull String getTaskDescription() {
    return processed == 0 ? "Applying MS2 pre grouping by %s.".formatted(
        pregrouping.value().toString())
        : "Filtering MS2s by sliding quad isolation for row %d/%d".formatted(processed, totalRows);
  }

  @Override
  public double getFinishedPercentage() {
    return pregroupingTask.getFinishedPercentage() * 0.5 + ((double) processed / totalRows) * 0.5;
  }
}
