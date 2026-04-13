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

import io.github.mzmine.modules.tools.batchwizard.subparameters.MassDetectorWizardOptions;
import io.github.mzmine.modules.tools.tools_autoparam.DataFileStatistics;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.metrics.HarmonicSlawIsotopes;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.metrics.SweepMetric;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.util.MathUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.moeaframework.core.PRNG;
import org.moeaframework.core.Solution;
import org.moeaframework.core.population.NondominatedPopulation;
import org.moeaframework.core.variable.BinaryIntegerVariable;
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
   * @param stats   per-file statistics from
   *                {@link io.github.mzmine.modules.tools.tools_autoparam.AutoParamTask}
   * @param builder the builder that defines parameter ranges and instrument type
   * @return map of variable name to estimated value
   */
  public static @NotNull Map<String, Double> estimate(
      @NotNull List<@NotNull DataFileStatistics> stats,
      @NotNull WizardParameterSolutionBuilder builder) {

    final Map<String, Double> estimates = new LinkedHashMap<>();

    // FWHM: median (50th percentile) of isotope peak FWHMs
    final double[] fwhms = stats.stream().map(DataFileStatistics::getIsotopePeakFwhms)
        .flatMapToDouble(Arrays::stream).sorted().toArray();
    if (fwhms.length > 0) {
      estimates.put("FWHM", MathUtils.calcQuantileSorted(fwhms, 0.5));
    }

    // Min consecutive data points: 25th percentile
    final double[] dataPts = stats.stream()
        .map(DataFileStatistics::getNumberOfLowestIsotopeDataPoints).flatMapToInt(Arrays::stream)
        .mapToDouble(i -> i).sorted().toArray();
    if (dataPts.length > 0) {
      estimates.put("Min consecutive", MathUtils.calcQuantileSorted(dataPts, 0.25));
    }

    // MS1 noise level: factor 5 for injection-time instruments, 15th percentile of edge intensities otherwise
    if (builder.getMassDetectorType() == MassDetectorWizardOptions.FACTOR_OF_LOWEST_SIGNAL) {
      estimates.put("MS1 noise level", 5.0);
    } else {
      final double[] edgeIntensities = stats.stream().map(DataFileStatistics::getEdgeIntensities)
          .flatMapToDouble(Arrays::stream).sorted().toArray();
      if (edgeIntensities.length > 0) {
        estimates.put("MS1 noise level", MathUtils.calcQuantileSorted(edgeIntensities, 0.15));
      }
    }

    // Min height: 15th percentile of lowest isotope heights
    final double[] heights = stats.stream().map(DataFileStatistics::getLowestIsotopeHeights)
        .flatMapToDouble(Arrays::stream).sorted().toArray();
    if (heights.length > 0) {
      estimates.put("Min height", MathUtils.calcQuantileSorted(heights, 0.15));
    }

    // MZ tolerance option: index into availableTolerances covering the harmonized isotope tolerance
    final int bestTolIndex = estimateBestToleranceIndex(stats, builder);
    estimates.put("MZ tolerance option", (double) bestTolIndex);

    // Inter sample RT tolerance: midpoint of the range derived from aligned benchmarks
    final double rtMid =
        (builder.getMinRtSampleToSampleTol() + builder.getMaxRtSampleToSampleTol()) / 2.0;
    estimates.put("Inter sample RT tolerance", rtMid);

    // Batch parameters: midpoints of their defined ranges
    estimates.put("Top-to-edge ratio", 2.0);
    estimates.put("Chrom. Threshold", 0.85);
    estimates.put("Wavelet SNR threshold", 5d);

    return estimates;
  }

  /**
   * Finds the index in the builder's available tolerance array that best covers the harmonized
   * isotope tolerance across all files.
   */
  private static int estimateBestToleranceIndex(@NotNull List<@NotNull DataFileStatistics> stats,
      @NotNull WizardParameterSolutionBuilder builder) {

    final MZTolerance[] available = builder.getAvailableTolerances();

    // decision: harmonize the per-file isotope tolerances into one covering tolerance
    MZTolerance harmonized = null;
    for (DataFileStatistics stat : stats) {
      final MZTolerance fileTol = stat.getMzToleranceForIsotopes();
      if (fileTol != null && harmonized == null) {
        harmonized = fileTol;
      } else if (fileTol != null) {
        harmonized = MZTolerance.max(harmonized, fileTol);
      }
    }

    if (harmonized == null) {
      return available.length / 2;
    }

    // find smallest available tolerance that covers the harmonized value
    for (int i = 0; i < available.length; i++) {
      if (available[i].getMzTolerance() >= harmonized.getMzTolerance()
          && available[i].getPpmTolerance() >= harmonized.getPpmTolerance()) {
        return i;
      }
    }
    return available.length - 1;
  }

  /**
   * Fraction of the population that will be warm-started near the single-pass estimate.
   */
  private static final double WARM_START_FRACTION = 0.3;

  /**
   * Standard deviation for warm-start perturbation as a fraction of each variable's range.
   */
  private static final double WARM_START_PERTURBATION = 0.15;

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
    final List<Solution> solutions = new ArrayList<>(warmCount);

    for (int i = 0; i < warmCount; i++) {
      final Solution solution = problem.newSolution();
      if (i == 0) {
        // first solution: exact center values
        applyToSolution(solution, estimates);
      } else {
        // subsequent: center with perturbation
        applyWithPerturbation(solution, estimates, WARM_START_PERTURBATION);
      }
      solution.setAttribute("Guesstimated", "true");
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
   * Applies center values with Gaussian perturbation for warm-starting. Each real variable is
   * perturbed by Gaussian noise with sigma = perturbationFraction * range. Integer variables get a
   * uniform offset in [-1, +1].
   *
   * @param perturbationFraction fraction of the variable range used as standard deviation
   */
  public static void applyWithPerturbation(@NotNull Solution solution,
      @NotNull Map<String, Double> estimates, double perturbationFraction) {
    for (int i = 0; i < solution.getNumberOfVariables(); i++) {
      final Variable var = solution.getVariable(i);
      Double center = estimates.get(var.getName());
      if (var instanceof RealVariable rv) {
        final double range = rv.getUpperBound() - rv.getLowerBound();
        final double noise = PRNG.nextGaussian() * perturbationFraction * range;
        if (center == null) {
          center = (rv.getLowerBound() + rv.getUpperBound()) / 2;
        }
        rv.setValue(Math.clamp(center + noise, rv.getLowerBound(), rv.getUpperBound()));
      } else if (var instanceof BinaryIntegerVariable biv) {
        if (center == null) {
          center = (double) ((biv.getLowerBound() + biv.getUpperBound()) / 2);
        }
        final int intCenter = (int) Math.round(center);
        final int offset = PRNG.nextInt(3) - 1;
        biv.setValue(Math.clamp(intCenter + offset, biv.getLowerBound(), biv.getUpperBound()));
      }
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
   * {@link HarmonicSlawIsotopes} score, or first metric if harmonic is not enabled).
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
    // prefer HarmonicSlawIsotopes
    for (int i = 0; i < enabledMetrics.size(); i++) {
      if (enabledMetrics.get(i) instanceof HarmonicSlawIsotopes) {
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
    } else if (var instanceof BinaryIntegerVariable biv) {
      final int clamped = Math.clamp((int) Math.round(value), biv.getLowerBound(),
          biv.getUpperBound());
      biv.setValue(clamped);
    }
  }
}
