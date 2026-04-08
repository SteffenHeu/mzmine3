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

package io.github.mzmine.modules.tools.tools_autoparam.optimizer.metrics;

import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.statistics.FeaturesDataTable;
import io.github.mzmine.modules.dataanalysis.utils.StatisticUtils;
import io.github.mzmine.modules.dataanalysis.utils.imputation.ImputationFunctions;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.LcMsOptimizationProblem;
import io.github.mzmine.parameters.parametertypes.statistics.AbundanceDataTablePreparationConfig;
import org.jetbrains.annotations.NotNull;
import org.moeaframework.core.Solution;
import org.moeaframework.core.objective.Maximize;
import org.moeaframework.core.objective.Minimize;
import org.moeaframework.core.objective.Objective;

/**
 * A metric that can be evaluated on a {@link FeatureList}. Each implementation corresponds to one
 * of the objectives used in {@link LcMsOptimizationProblem}.
 * <p>
 * Convenience singleton constants are provided for all parameterless metrics. The
 * {@link BenchmarkTargetCount} record carries its own target list.
 *
 * <pre>{@code
 * double score = SweepMetric.ROWS_BELOW_CV20.evaluate(featureList);
 * double score = new SweepMetric.BenchmarkTargetCount(targets).evaluate(featureList);
 * }</pre>
 */
public sealed interface SweepMetric permits BenchmarkTargetCount, DoublePeakRatio, FillRatio,
    HarmonicSlawIsotopes, IpoIsotopeScore, SlawIntegrationScore, YasinIsotopeScore {

  // --- Singleton constants for parameterless metrics ---
  DoublePeakRatio DOUBLE_PEAK_RATIO = new DoublePeakRatio();
  FillRatio FILL_RATIO = new FillRatio();

  IpoIsotopeScore IPO_ISOTOPE_SCORE = new IpoIsotopeScore();
  SlawIntegrationScore SLAW_INTEGRATION_SCORE = new SlawIntegrationScore();
  HarmonicSlawIsotopes HARMONIC_SLAW_ISOTOPES = new HarmonicSlawIsotopes();
  YasinIsotopeScore YASIN_ISOTOPE_SCORE = new YasinIsotopeScore();

  // --- Interface contract ---

  /**
   * Human-readable metric name for display and logging.
   */
  @NotNull String name();

  /**
   * Whether a higher value means a better result.
   */
  boolean higherIsBetter();

  /**
   * Evaluates the metric on the given feature list.
   */
  double evaluate(@NotNull FeatureList featureList);

  /**
   * Returns the MOEA {@link Objective} for this metric, based on {@link #name()} and
   * {@link #higherIsBetter()}.
   */
  default @NotNull Objective objective() {
    return higherIsBetter() ? new Maximize(name()) : new Minimize(name());
  }

  /**
   * Stores additional attributes on the solution after evaluation. The default is a no-op; metrics
   * that need to persist component scores (e.g. for display normalisation) override this.
   */
  default void applyAttributes(@NotNull FeatureList featureList, @NotNull Solution solution) {
    // decision: no-op by default; only HarmonicSlawIsotopes overrides this
  }

  // --- Helper ---

  /**
   * Builds a {@link FeaturesDataTable} from the feature list for abundance-based metrics.
   */
  static @NotNull FeaturesDataTable buildDataTable(@NotNull FeatureList featureList) {
    final var config = new AbundanceDataTablePreparationConfig(AbundanceMeasure.Area,
        ImputationFunctions.Zero);
    return StatisticUtils.extractAbundancesPrepareData(featureList.getRows(),
        featureList.getRawDataFiles(), config);
  }
}
