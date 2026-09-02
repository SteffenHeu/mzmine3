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

package io.github.mzmine.modules.tools.tools_autoparam.optimizer;

import io.github.mzmine.modules.tools.batchwizard.WizardPart;
import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.modules.tools.batchwizard.subparameters.CustomizationWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.IonMobilityWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassDetectorWizardOptions;
import io.github.mzmine.modules.tools.batchwizard.subparameters.ParameterOverride;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.IonMobilityWizardParameterFactory;
import io.github.mzmine.modules.tools.tools_autoparam.DataFileStatistics;
import io.github.mzmine.modules.tools.tools_autoparam.RawDataParameterEstimation;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.ParameterSolutionPrototype.BatchParameterSolutionPrototype;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.ParameterSolutionPrototype.WizardParameterSolutionPrototype;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.metrics.IsotopeRatioConsistencyScore;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.metrics.SweepMetric;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.moeaframework.core.PRNG;
import org.moeaframework.core.Solution;
import org.moeaframework.core.population.NondominatedPopulation;
import org.moeaframework.core.variable.RealVariable;
import org.moeaframework.core.variable.Variable;
import org.moeaframework.problem.AbstractProblem;

/**
 * Derives single "best guess" parameter values from {@link DataFileStatistics} without iterative
 * optimization. Used for logging baseline metrics and warm-starting the MOEA optimizer.
 */
public final class SinglePassParameterEstimation {

  private static final Logger logger = Logger.getLogger(
      SinglePassParameterEstimation.class.getName());

  private SinglePassParameterEstimation() {
  }

  /**
   * Estimates a single value for each optimization parameter based on raw data statistics.
   *
   * @param stats    per-file statistics from
   *                 {@link io.github.mzmine.modules.tools.tools_autoparam.AutoParamTask}
   * @param builder  the builder that defines parameter ranges and instrument type
   * @param sequence selected wizard presets, providing defaults for parameters that cannot yet be
   *                 estimated from the raw data
   * @return map of variable name to estimated value
   */
  public static @NotNull Map<String, Double> estimate(
      @NotNull List<@NotNull DataFileStatistics> stats,
      @NotNull WizardParameterSolutionBuilder builder, @NotNull WizardSequence sequence) {

    final Map<String, Double> estimates = new LinkedHashMap<>();

    // FWHM: median (50th percentile) of isotope peak FWHMs
    final double[] fwhms = stats.stream().map(DataFileStatistics::getIsotopePeakFwhms)
        .flatMapToDouble(Arrays::stream).toArray();
    final Double fwhmEstimate = RawDataParameterEstimation.estimateFwhm(fwhms);
    if (fwhmEstimate != null) {
      estimates.put("FWHM", fwhmEstimate);
    }

    final double[] dataPts = stats.stream()
        .map(DataFileStatistics::getNumberOfLowestIsotopeDataPoints).flatMapToInt(Arrays::stream)
        .mapToDouble(i -> i).toArray();
    final Double minConsecutiveEstimate = RawDataParameterEstimation.estimateMinConsecutiveScans(
        dataPts);
    if (minConsecutiveEstimate != null) {
      estimates.put("Min consecutive", minConsecutiveEstimate);
    }

    // MS1 noise level: factor 5 for injection-time instruments, 15th percentile of edge intensities otherwise
    if (builder.getMassDetectorType() == MassDetectorWizardOptions.FACTOR_OF_LOWEST_SIGNAL) {
      estimates.put("MS1 noise level", 5.0);
    } else {
      final double[] edgeIntensities = stats.stream().map(DataFileStatistics::getEdgeIntensities)
          .flatMapToDouble(Arrays::stream).toArray();
      final Double noiseEstimate = RawDataParameterEstimation.estimateAbsoluteNoiseLevel(
          edgeIntensities);
      if (noiseEstimate != null) {
        estimates.put("MS1 noise level", noiseEstimate);
      }
    }

    // Min height: median of lowest isotope heights
    final double[] heights = stats.stream().map(DataFileStatistics::getLowestIsotopeHeights)
        .flatMapToDouble(Arrays::stream).toArray();
    final Double minHeightEstimate = RawDataParameterEstimation.estimateMinHeight(heights);
    if (minHeightEstimate != null) {
      estimates.put("Min height", minHeightEstimate);
    }

    final int mzToleranceIndex = List.of(WizardParameterSolutionBuilder.ALL_TOLERANCE_OPTIONS)
        .indexOf(RawDataParameterEstimation.estimateMzTolerance(stats));
    estimates.put("MZ tolerance option", (double) mzToleranceIndex);

    // Inter sample RT tolerance: a high quantile of the deviations seen between the aligned
    // benchmark features, floored at the smallest deviation so it is never below the search range
    estimates.put("Inter sample RT tolerance",
        Math.max(builder.getMinRtSampleToSampleTol(), builder.getEstimatedRtSampleToSampleTol()));

    // Batch parameters: midpoints of their defined ranges
    estimates.put("Top-to-edge ratio", 1.7);
    estimates.put("Chrom. Threshold", 0.85);
    estimates.put("Wavelet SNR threshold", 4d);

    // No raw-data estimator exists for mobility peak width yet. Start at the selected preset's
    // factory default so the initial solution is complete and deterministic rather than NaN.
    sequence.get(WizardPart.IMS).filter(
            step -> step.getFactory() instanceof IonMobilityWizardParameterFactory factory
                && factory != IonMobilityWizardParameterFactory.NO_IMS).map(
            step -> step.createDefaultParameterPreset()
                .getValue(IonMobilityWizardParameters.approximateImsFWHM))
        .filter(value -> value != null && Double.isFinite(value))
        .ifPresent(value -> estimates.put(OptimizationParameterRegistry.MOBILITY_FWHM_NAME, value));

    return estimates;
  }

  /**
   * Applies every available estimate that maps to the active wizard presets. Wizard parameters are
   * written directly to their steps and batch-only parameters are added to the customization
   * overrides. Mobility FWHM is intentionally excluded because it currently comes from the preset
   * default rather than raw-data estimation.
   */
  public static void applyToWizardSequence(@NotNull WizardSequence sequence,
      @NotNull Map<String, Double> estimates, @NotNull WizardParameterSolutionBuilder builder) {
    applyToWizardSequence(sequence, estimates, builder, Set.of());
  }

  /**
   * @param excludedBatchParameterNames batch parameters supplied by an optimizer solution instead
   */
  static void applyToWizardSequence(@NotNull WizardSequence sequence,
      @NotNull Map<String, Double> estimates, @NotNull WizardParameterSolutionBuilder builder,
      @NotNull Set<String> excludedBatchParameterNames) {
    final List<WizardParameterSolution> parameters = new ArrayList<>();
    for (final ParameterSolutionPrototype prototype : OptimizationParameterRegistry.forSequence(
        sequence)) {
      if (prototype instanceof WizardParameterSolutionPrototype wizardPrototype
          && estimates.containsKey(wizardPrototype.name())
          && !OptimizationParameterRegistry.MOBILITY_FWHM_NAME.equals(wizardPrototype.name())) {
        parameters.add(wizardPrototype.toRealSolution(builder, parameters.size()));
      }
    }

    final Solution solution = new Solution(parameters.size(), 0);
    parameters.forEach(parameter -> parameter.applyToSolution(solution));
    applyToSolution(solution, estimates);
    for (final WizardParameterSolution parameter : parameters) {
      sequence.get(parameter.part())
          .ifPresent(step -> parameter.setToParameters().accept(step, solution, parameter.index()));
    }

    applyBatchOverridesToWizardSequence(sequence,
        createEstimatedBatchOverrides(sequence, estimates, excludedBatchParameterNames));
  }

  /**
   * Converts available single-pass estimates for batch-only parameters into wizard customization
   * overrides.
   *
   * @param excludedParameterNames parameters supplied by the optimizer solution instead; excluding
   *                               them prevents duplicate overrides for the same batch parameter
   */
  static @NotNull List<ParameterOverride> createEstimatedBatchOverrides(
      @NotNull WizardSequence sequence, @NotNull Map<String, Double> estimates,
      @NotNull Set<String> excludedParameterNames) {
    final List<ParameterOverride> overrides = new ArrayList<>();
    for (final ParameterSolutionPrototype prototype : OptimizationParameterRegistry.forSequence(
        sequence)) {
      if (!(prototype instanceof BatchParameterSolutionPrototype batchPrototype)
          || !estimates.containsKey(batchPrototype.name())
          || excludedParameterNames.contains(batchPrototype.name())) {
        continue;
      }

      final BatchParameterSolution parameter = batchPrototype.toBatchParameterSolution(0);
      final Solution estimatedSolution = new Solution(1, 0);
      parameter.applyToSolution(estimatedSolution);
      applyToSolution(estimatedSolution, estimates);
      overrides.add(parameter.toParameterOverride(estimatedSolution));
    }
    return List.copyOf(overrides);
  }

  /**
   * Adds or replaces batch parameter overrides without discarding unrelated wizard customization.
   */
  static void applyBatchOverridesToWizardSequence(@NotNull WizardSequence sequence,
      @NotNull List<ParameterOverride> replacements) {
    if (replacements.isEmpty()) {
      return;
    }
    sequence.get(WizardPart.CUSTOMIZATION).ifPresent(customization -> {
      final List<ParameterOverride> existing = customization.getValue(
          CustomizationWizardParameters.overrides);
      final List<ParameterOverride> merged = new ArrayList<>(
          existing != null ? existing : List.of());
      for (final ParameterOverride replacement : replacements) {
        merged.removeIf(current -> targetsSameParameter(current, replacement));
        merged.add(replacement);
      }
      customization.setParameter(CustomizationWizardParameters.enabled, true);
      customization.setParameter(CustomizationWizardParameters.overrides, List.copyOf(merged));
    });
  }

  private static boolean targetsSameParameter(@NotNull ParameterOverride first,
      @NotNull ParameterOverride second) {
    return first.moduleClassName().equals(second.moduleClassName())
        && first.parameterWithValue().getName().equals(second.parameterWithValue().getName())
        && first.scope() == second.scope();
  }

  /**
   * Standard deviation for warm-start perturbation as a fraction of each variable's range.
   */
  private static final double WARM_START_PERTURBATION = 0.20;

  /**
   * Standard deviation of the multiplicative perturbation, in natural log units. At 0.5 two
   * standard deviations span a factor of 0.37 to 2.7 around the estimate, which covers the range of
   * offsets measured between the estimate and the optimum on the reference datasets without
   * degenerating into a uniform draw.
   */
  private static final double LOG_WARM_START_PERTURBATION = 0.5;

  /**
   * Creates a list of pre-built solutions for warm-starting the MOEA via
   * {@link org.moeaframework.core.initialization.InjectedInitialization}. The first solution uses
   * exact center values, subsequent ones add Gaussian perturbation. The total count is
   * {@value #WARM_START_FRACTION} of the population size.
   * <p>
   * These solutions bypass {@code RandomInitialization.randomize()} because
   * {@code InjectedInitialization} injects them directly into the initial population.
   *
   * @param problem   the optimization problem (used to create solutions with correct variables)
   * @param estimates variable name to estimated value
   * @param warmCount Number of solutions to base on the derived parameters
   * @return list of pre-built solutions to inject
   */
  public static @NotNull List<Solution> createWarmStartSolutions(@NotNull AbstractProblem problem,
      @NotNull Map<String, Double> estimates, int warmCount) {
    return createWarmStartSolutions(problem, estimates, warmCount, WarmStartSampling.GAUSSIAN);
  }

  /**
   * @param sampling how the perturbations are spread around the estimate. The first solution is the
   *                 unperturbed estimate either way.
   */
  public static @NotNull List<Solution> createWarmStartSolutions(@NotNull AbstractProblem problem,
      @NotNull Map<String, Double> estimates, int warmCount, @NotNull WarmStartSampling sampling) {
    final List<Solution> solutions = new ArrayList<>(warmCount);
    // the deviates for every perturbed solution at once, because a space-filling sequence is only
    // space filling as a set - it cannot be drawn one solution at a time
    final double[][] deviates = sampling.normalDeviates(Math.max(0, warmCount - 1),
        problem.getNumberOfVariables());
    final SearchScaleProvider scaleProvider =
        problem instanceof SearchScaleProvider provider ? provider : _ -> SearchScale.LINEAR;

    for (int i = 0; i < warmCount; i++) {
      final Solution solution = problem.newSolution();
      if (i == 0) {
        // first solution: exact center values
        applyToSolution(solution, estimates);
        SolutionOrigin.ESTIMATE.applyTo(solution);
      } else {
        // subsequent: center with perturbation
        applyWithPerturbation(solution, estimates, WARM_START_PERTURBATION, deviates[i - 1],
            scaleProvider);
        SolutionOrigin.PERTURBED.applyTo(solution);
      }
      solutions.add(solution);
    }

    logger.finest("Created %d warm-start solutions for injection".formatted(warmCount));
    return solutions;
  }

  /**
   * Applies the estimated center values to a solution's variables by matching variable names.
   */
  public static void applyToSolution(@NotNull Solution solution,
      @NotNull Map<String, Double> estimates) {
    for (int i = 0; i < solution.getNumberOfVariables(); i++) {
      final Variable var = solution.getVariable(i);
      final Double center = estimates.get(var.getName());
      if (center == null) {
        continue;
      }
      applyValueToVariable(var, center);
    }
  }

  /**
   * Applies center values with Gaussian perturbation for warm-starting, on whichever scale suits the
   * variable. {@link OrdinalIntegerVariable} is a {@link RealVariable}, so integer parameters are
   * perturbed too and only rounded when they are read.
   *
   * @param perturbationFraction standard deviation, as a fraction of the variable's range for an
   *                             additive variable and in log units for a logarithmic one
   * @param scaleProvider       explicit scale declaration for each named parameter
   */
  public static void applyWithPerturbation(@NotNull Solution solution,
      @NotNull Map<String, Double> estimates, double perturbationFraction,
      @NotNull SearchScaleProvider scaleProvider) {
    applyWithPerturbation(solution, estimates, perturbationFraction, null, scaleProvider);
  }

  /**
   * @param normalDeviates one standard normal deviate per variable, or null to draw them. Supplying
   *                       them lets a space-filling design decide the spread instead of chance.
   */
  private static void applyWithPerturbation(@NotNull Solution solution,
      @NotNull Map<String, Double> estimates, double perturbationFraction,
      @Nullable double[] normalDeviates, @NotNull SearchScaleProvider scaleProvider) {
    for (int i = 0; i < solution.getNumberOfVariables(); i++) {
      final Variable var = solution.getVariable(i);
      if (!(var instanceof RealVariable rv)) {
        continue;
      }
      final Double center = estimates.get(var.getName());
      final double noise = normalDeviates != null && i < normalDeviates.length ? normalDeviates[i]
          : PRNG.nextGaussian();
      final double perturbed;

      final boolean logarithmic = !(rv instanceof OrdinalIntegerVariable)
          && scaleProvider.searchScale(rv.getName()) == SearchScale.LOGARITHMIC;
      if (logarithmic) {
        if (rv.getLowerBound() <= 0d) {
          throw new IllegalArgumentException(
              "Logarithmic parameter %s requires a positive lower bound, found %s".formatted(
                  rv.getName(), rv.getLowerBound()));
        }
        // a fixed relative jitter around the estimate, independent of how wide the box is. The
        // geometric mean is the log scale midpoint, used when there is no estimate to jitter.
        final double base =
            center != null ? center : Math.sqrt(rv.getLowerBound() * rv.getUpperBound());
        perturbed = base * Math.exp(noise * LOG_WARM_START_PERTURBATION);
      } else {
        final double base = center != null ? center : (rv.getLowerBound() + rv.getUpperBound()) / 2;
        perturbed = base + noise * perturbationFraction * (rv.getUpperBound() - rv.getLowerBound());
      }

      rv.setValue(Math.clamp(perturbed, rv.getLowerBound(), rv.getUpperBound()));
    }
  }

  /**
   * Logs the single-pass estimated parameter values and the resulting metric scores after
   * evaluation.
   */
  public static void logResults(@NotNull Solution solution, @NotNull Map<String, Double> estimates,
      @NotNull List<SweepMetric> enabledMetrics) {
    final StringBuilder sb = new StringBuilder("Single-pass parameter estimation results:\n");
    sb.append("  Estimated parameters:\n");
    for (Map.Entry<String, Double> entry : estimates.entrySet()) {
      sb.append("    %s = %.6f%n".formatted(entry.getKey(), entry.getValue()));
    }
    sb.append("  Metric scores:\n");
    for (int i = 0; i < enabledMetrics.size(); i++) {
      sb.append(
          "    %s = %.6f%n".formatted(enabledMetrics.get(i).name(), solution.getObjectiveValue(i)));
    }
    logger.info(sb.toString());
  }

  /**
   * Logs comparison between single-pass results and the best MOEA solution (by
   * {@link IsotopeRatioConsistencyScore} score, or first metric if harmonic is not enabled).
   */
  public static void logComparison(@NotNull Solution singlePass,
      @NotNull NondominatedPopulation moeaResult, @NotNull List<SweepMetric> enabledMetrics) {

    // find the harmonic slaw-isotopes metric index, fallback to first maximize metric
    int compareIndex = findComparisonMetricIndex(enabledMetrics);
    if (compareIndex < 0) {
      logger.info("No maximize metric found for single-pass vs MOEA comparison.");
      return;
    }

    final String compareName = enabledMetrics.get(compareIndex).name();

    // find best MOEA solution by the comparison metric
    Solution bestMoea = null;
    double bestScore = Double.NEGATIVE_INFINITY;
    for (Solution sol : moeaResult) {
      final double score = sol.getObjectiveValue(compareIndex);
      if (score > bestScore) {
        bestScore = score;
        bestMoea = sol;
      }
    }

    final StringBuilder sb = new StringBuilder("\n=== Single-pass vs MOEA comparison ===\n");
    sb.append("  %-30s  %15s  %15s%n".formatted("Metric", "Single-pass", "MOEA best"));
    sb.append("  %-30s  %15s  %15s%n".formatted("-".repeat(30), "-".repeat(15), "-".repeat(15)));
    for (int i = 0; i < enabledMetrics.size(); i++) {
      final double spValue = singlePass.getObjectiveValue(i);
      final double moeaValue = bestMoea != null ? bestMoea.getObjectiveValue(i) : Double.NaN;
      sb.append(
          "  %-30s  %15.4f  %15.4f%n".formatted(enabledMetrics.get(i).name(), spValue, moeaValue));
    }
    sb.append("  Comparison metric: %s%n".formatted(compareName));
    sb.append("  MOEA Pareto frontier size: %d%n".formatted(moeaResult.size()));
    logger.info(sb.toString());
  }

  private static int findComparisonMetricIndex(@NotNull List<SweepMetric> enabledMetrics) {
    // prefer YasinIsotopeScore
    for (int i = 0; i < enabledMetrics.size(); i++) {
      if (enabledMetrics.get(i) instanceof IsotopeRatioConsistencyScore) {
        return i;
      }
    }
    // fallback: first maximize metric
    for (int i = 0; i < enabledMetrics.size(); i++) {
      if (enabledMetrics.get(i).higherIsBetter()) {
        return i;
      }
    }
    return -1;
  }

  private static void applyValueToVariable(@NotNull Variable var, double value) {
    if (var instanceof RealVariable rv) {
      rv.setValue(Math.clamp(value, rv.getLowerBound(), rv.getUpperBound()));
    }
  }
}
