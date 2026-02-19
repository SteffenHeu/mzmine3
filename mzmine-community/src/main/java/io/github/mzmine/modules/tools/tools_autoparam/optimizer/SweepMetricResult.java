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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.moeaframework.core.variable.Variable;

/**
 * Wraps a {@link ParameterSweepResult} together with pre-computed scores for a fixed set of
 * {@link SweepMetric}s. All metrics are evaluated exactly once at construction via
 * {@link #of(ParameterSweepResult, List)}, so the view can read values without re-evaluating.
 */
public record SweepMetricResult(@NotNull ParameterSweepResult sweepResult,
                                @NotNull List<SweepMetric> metrics, double[] scores) {

  /**
   * Evaluates every metric in {@code metrics} against {@code result} and returns the combined
   * record. The score at index {@code i} corresponds to {@code metrics.get(i)}.
   */
  public static SweepMetricResult of(@NotNull ParameterSweepResult result,
      @NotNull List<SweepMetric> metrics) {
    final double[] scores = new double[metrics.size()];
    for (int i = 0; i < metrics.size(); i++) {
      scores[i] = metrics.get(i).evaluate(result);
    }
    return new SweepMetricResult(result, metrics, scores);
  }

  /**
   * Returns a new list of {@link SweepMetricResult}s in which {@code metric} and its pre-computed
   * {@code scores} are appended to every element. The {@code scores} array must have the same
   * length as {@code results}.
   *
   * @param results existing result list (not modified)
   * @param metric  the additional metric to append
   * @param scores  pre-computed scores, one entry per element in {@code results}
   * @return new list with the metric appended at the last index
   */
  public static @NotNull List<SweepMetricResult> withAdditionalMetric(
      @NotNull List<SweepMetricResult> results, @NotNull SweepMetric metric,
      double @NotNull [] scores) {
    final List<SweepMetricResult> extended = new ArrayList<>(results.size());
    for (int i = 0; i < results.size(); i++) {
      final SweepMetricResult r = results.get(i);
      final List<SweepMetric> newMetrics = new ArrayList<>(r.metrics());
      newMetrics.add(metric);
      final double[] newScores = Arrays.copyOf(r.scores(), r.scores().length + 1);
      newScores[newScores.length - 1] = scores[i];
      extended.add(new SweepMetricResult(r.sweepResult(), List.copyOf(newMetrics), newScores));
    }
    return List.copyOf(extended);
  }

  /**
   * Pre-computed score for the metric at position {@code index}.
   */
  public double getScore(int index) {
    return scores[index];
  }

  // --- Convenience delegates ---

  public String parameterName() {
    return sweepResult.parameterName();
  }

  public Variable variable() {
    return sweepResult.variable();
  }

  public WizardParameterPrototype prototype() {
    return sweepResult.prototype();
  }
}
