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

package io.github.mzmine.modules.tools.tools_autoparam.estimation;

import io.github.mzmine.modules.tools.batchwizard.WizardPart;
import io.github.mzmine.modules.tools.batchwizard.subparameters.IonInterfaceHplcWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.IonMobilityWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassDetectorWizardOptions;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassSpectrometerWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.custom_parameters.WizardMassDetectorNoiseLevels;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.IonMobilityWizardParameterFactory;
import io.github.mzmine.modules.tools.tools_autoparam.InterSampleRtStatistics;
import io.github.mzmine.modules.tools.tools_autoparam.RawDataParameterEstimation;
import io.github.mzmine.modules.tools.tools_autoparam.estimation.domain.ChoiceSearchDomain;
import io.github.mzmine.modules.tools.tools_autoparam.estimation.domain.DoubleSearchDomain;
import io.github.mzmine.modules.tools.tools_autoparam.estimation.domain.IntegerSearchDomain;
import io.github.mzmine.modules.tools.tools_autoparam.estimation.domain.MappedSearchDomain;
import io.github.mzmine.modules.tools.tools_autoparam.estimation.domain.RtSearchDomain;
import io.github.mzmine.modules.tools.tools_autoparam.estimation.domain.SearchScale;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance.Unit;
import io.github.mzmine.util.MathUtils;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Per-parameter rules return a typed initial value and its search domain together.
 */
public final class ParameterEstimators {

  private ParameterEstimators() {
  }

  public static @NotNull ParameterEstimate<Double> minimumFeatureHeight(
      @NotNull ParameterEstimationContext context) {
    final double[] heights = context.analysis().heights();
    if (heights.length == 0) {
      return new ParameterEstimate<>(
          context.preset(WizardPart.MS, MassSpectrometerWizardParameters.minimumFeatureHeight),
          ValueOrigin.PRESET_DEFAULT, new DoubleSearchDomain(100d, 1E8d, SearchScale.LOGARITHMIC));
    }
    return new ParameterEstimate<>(RawDataParameterEstimation.estimateMinHeight(heights),
        ValueOrigin.RAW_DATA,
        new DoubleSearchDomain(quantile(heights, 0.05), quantile(heights, 0.95),
            SearchScale.LOGARITHMIC));
  }

  public static @NotNull ParameterEstimate<RTTolerance> chromatographicFwhm(
      @NotNull ParameterEstimationContext context) {
    final double[] widths = context.analysis().fwhms();
    if (widths.length == 0) {
      return new ParameterEstimate<>(context.preset(WizardPart.ION_INTERFACE,
          IonInterfaceHplcWizardParameters.approximateChromatographicFWHM),
          ValueOrigin.PRESET_DEFAULT, rtDomain(0.005, 0.1));
    }
    final double median = RawDataParameterEstimation.estimateFwhm(widths);
    return new ParameterEstimate<>(minutes(median), ValueOrigin.RAW_DATA,
        rtDomain(quantile(widths, 0.05), Math.max(quantile(widths, 0.90), median * 2d)));
  }

  public static @NotNull ParameterEstimate<Integer> minimumConsecutiveScans(
      @NotNull ParameterEstimationContext context) {
    final double[] scans = context.analysis().consecutiveScans();
    if (scans.length == 0) {
      return new ParameterEstimate<>(context.preset(WizardPart.ION_INTERFACE,
          IonInterfaceHplcWizardParameters.minNumberOfDataPoints), ValueOrigin.PRESET_DEFAULT,
          new IntegerSearchDomain(4, 10));
    }
    // assumption: three scans are the physical minimum for a peak to have a shape.
    return new ParameterEstimate<>(
        RawDataParameterEstimation.estimateMinConsecutiveScans(scans).intValue(),
        ValueOrigin.RAW_DATA, new IntegerSearchDomain(3, (int) Math.max(9, quantile(scans, 0.8))));
  }

  public static @NotNull ParameterEstimate<WizardMassDetectorNoiseLevels> ms1Noise(
      @NotNull ParameterEstimationContext context) {
    final MassDetectorWizardOptions type = context.massDetectorType();
    final double[] edges = context.analysis().edgeIntensities();
    final boolean factor = type == MassDetectorWizardOptions.FACTOR_OF_LOWEST_SIGNAL;
    final DoubleSearchDomain bounds = factor ? new DoubleSearchDomain(3d, 15d, SearchScale.LINEAR)
        : new DoubleSearchDomain(edges.length == 0 ? 10d : quantile(edges, 0.05),
            edges.length == 0 ? 1E6d : quantile(edges, 0.95), SearchScale.LOGARITHMIC);
    final MappedSearchDomain<WizardMassDetectorNoiseLevels> domain = new MappedSearchDomain<>(
        bounds, WizardMassDetectorNoiseLevels::getMs1NoiseLevel,
        value -> new WizardMassDetectorNoiseLevels(type, value, value / 2.5));
    if (!factor && edges.length == 0) {
      return new ParameterEstimate<>(
          context.preset(WizardPart.MS, MassSpectrometerWizardParameters.massDetectorOption),
          ValueOrigin.PRESET_DEFAULT, domain);
    }
    final double initial =
        factor ? 5d : RawDataParameterEstimation.estimateAbsoluteNoiseLevel(edges);
    return new ParameterEstimate<>(domain.decode(initial),
        factor ? ValueOrigin.HEURISTIC : ValueOrigin.RAW_DATA, domain);
  }

  public static @NotNull ParameterEstimate<MZTolerance> mzTolerance(
      @NotNull ParameterEstimationContext context) {
    final int lower = context.lowResolution() ? 5
        : context.massDetectorType() == MassDetectorWizardOptions.FACTOR_OF_LOWEST_SIGNAL ? 1 : 2;
    final int upper = context.lowResolution() ? 11
        : context.massDetectorType() == MassDetectorWizardOptions.FACTOR_OF_LOWEST_SIGNAL ? 4 : 6;
    final List<MZTolerance> all = List.of(MzToleranceSearchOptions.ALL_TOLERANCE_OPTIONS);
    final int estimate = all.indexOf(
        RawDataParameterEstimation.estimateMzTolerance(context.analysis().files()));
    return new ParameterEstimate<>(all.get(Math.clamp(estimate, lower, upper)),
        context.analysis().files().isEmpty() ? ValueOrigin.HEURISTIC : ValueOrigin.RAW_DATA,
        new ChoiceSearchDomain<>(all.subList(lower, upper + 1), lower));
  }

  public static @NotNull ParameterEstimate<RTTolerance> interSampleRt(
      @NotNull ParameterEstimationContext context) {
    final InterSampleRtStatistics stats = interSampleRtStatistics(context.analysis());
    if (stats.isEmpty()) {
      return new ParameterEstimate<>(context.preset(WizardPart.ION_INTERFACE,
          IonInterfaceHplcWizardParameters.interSampleRTTolerance), ValueOrigin.PRESET_DEFAULT,
          rtDomain(0.01, 0.2));
    }
    return new ParameterEstimate<>(minutes(stats.estimatedTolerance()), ValueOrigin.RAW_DATA,
        rtDomain(stats.lowerSearchBound(), stats.upperSearchBound()));
  }

  public static @NotNull InterSampleRtStatistics interSampleRtStatistics(
      @NotNull RawDataAnalysis analysis) {
    final double[] deviations = analysis.rtDeviations();
    if (deviations.length == 0) {
      return new InterSampleRtStatistics(deviations, Double.NaN, Double.NaN, Double.NaN);
    }
    // decision: retain the 98th-percentile estimate and twice-maximum search headroom.
    return new InterSampleRtStatistics(deviations, (float) quantile(deviations, 0.98),
        (float) quantile(deviations, 0d), (float) quantile(deviations, 1d) * 2d);
  }

  public static @NotNull ParameterEstimate<Double> mobilityFwhm(
      @NotNull ParameterEstimationContext context) {
    final IonMobilityWizardParameterFactory factory = (IonMobilityWizardParameterFactory) context.sequence()
        .get(WizardPart.IMS).orElseThrow().getFactory();
    final DoubleSearchDomain domain = switch (factory) {
      case TIMS -> new DoubleSearchDomain(0.003, 0.02, SearchScale.LINEAR);
      case IMS -> new DoubleSearchDomain(0.01, 5, SearchScale.LINEAR);
      case DTIMS, TWIMS -> new DoubleSearchDomain(0.1, 5, SearchScale.LINEAR);
      case SLIM -> new DoubleSearchDomain(1, 20, SearchScale.LINEAR);
      case NO_IMS -> throw new IllegalArgumentException("Mobility requires an IMS preset");
    };
    return new ParameterEstimate<>(
        context.preset(WizardPart.IMS, IonMobilityWizardParameters.approximateImsFWHM),
        ValueOrigin.PRESET_DEFAULT, domain);
  }

  private static double quantile(double @NotNull [] sorted, double quantile) {
    return MathUtils.calcQuantileSorted(sorted, quantile);
  }

  private static @NotNull RTTolerance minutes(double value) {
    return new RTTolerance((float) value, Unit.MINUTES);
  }

  private static @NotNull RtSearchDomain rtDomain(double lower, double upper) {
    return new RtSearchDomain(new DoubleSearchDomain(lower, upper, SearchScale.LINEAR));
  }

  public static @Nullable MZTolerance estimateSampleToSampleMzTolerance(
      @NotNull Map<MZTolerance, Integer> toleranceCounter, float coverageQuantile) {
    if (toleranceCounter.isEmpty()) {
      return null;
    }
    final int requiredSampleSize = (int) (
        toleranceCounter.values().stream().mapToInt(Integer::intValue).sum() * coverageQuantile);
    final List<Entry<MZTolerance, Integer>> sortedToleranceCounts = toleranceCounter.entrySet()
        .stream().sorted(Comparator.comparingDouble(e -> e.getKey().getMzTolerance())).toList();

    int coveredRows = 0;
    for (final Entry<MZTolerance, Integer> toleranceCount : sortedToleranceCounts) {
      coveredRows += toleranceCount.getValue();
      if (coveredRows >= requiredSampleSize) {
        return toleranceCount.getKey();
      }
    }

    throw new IllegalStateException(
        "Unable to find sample tolerance threshold. " + sortedToleranceCounts.stream()
            .map(e -> e.getKey().toString() + ": " + e.getValue())
            .collect(Collectors.joining(", ")));
  }


}
