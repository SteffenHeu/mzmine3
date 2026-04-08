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

import io.github.mzmine.datamodel.features.FeatureList;
import org.jetbrains.annotations.NotNull;
import org.moeaframework.core.Solution;

/**
 * Maximise: harmonic mean of the {@link SlawIntegrationScore} and {@link IpoIsotopeScore} scores.
 * The harmonic mean rewards solutions that perform well on <em>both</em> components simultaneously
 * and penalises extreme imbalances between the two.
 * <p>
 * {@link #evaluate} computes the raw harmonic mean (used by the MOEA optimizer). For the parameter
 * sweep, use {@link #computeNormalizedScores} instead, which min-max normalises each component to
 * [0, 1] across all results before combining them.
 * <p>
 * Returns 0 when both components are 0.
 */
public record HarmonicSlawIsotopes() implements SweepMetric {

  /**
   * Attribute key under which the raw slaw component is stored on the solution for display
   * normalisation.
   */
  public static final String ATTR_HARMONIC_SLAW = "_harmonic_slaw";

  /**
   * Attribute key under which the raw iso component is stored on the solution for display
   * normalisation.
   */
  public static final String ATTR_HARMONIC_ISO = "_harmonic_iso";

  @Override
  public @NotNull String name() {
    return "Harmonic slaw-isotopes";
  }

  @Override
  public boolean higherIsBetter() {
    return true;
  }

  /**
   * Raw harmonic mean of the two component scores (used by the MOEA optimizer). For the parameter
   * sweep, prefer {@link #computeNormalizedScores} instead.
   */
  @Override
  public double evaluate(@NotNull FeatureList featureList) {
    final double slawScore = SLAW_INTEGRATION_SCORE.evaluate(featureList);
    final double isoScore = IPO_ISOTOPE_SCORE.evaluate(featureList);
    final double sum = slawScore + isoScore;
    return sum == 0.0 ? 0.0 : 2.0 * slawScore * isoScore / sum;
  }

  /**
   * Stores the raw slaw and iso component scores as solution attributes so the results table can
   * normalise them across the full population before displaying the harmonic score.
   */
  @Override
  public void applyAttributes(@NotNull FeatureList featureList, @NotNull Solution solution) {
    solution.setAttribute(ATTR_HARMONIC_SLAW, SLAW_INTEGRATION_SCORE.evaluate(featureList));
    solution.setAttribute(ATTR_HARMONIC_ISO, IPO_ISOTOPE_SCORE.evaluate(featureList));
  }

  /**
   * Computes normalised harmonic mean scores across all results. Each component ({@code slaw} and
   * {@code iso}) is normalised to [0, 1] by dividing by the maximum value across all results before
   * the harmonic mean is applied. This avoids the combined score being dominated by whichever
   * component has larger raw values, while keeping scores proportional (the score is 0 only when
   * the raw component value is 0, not merely when it is the minimum in the population).
   * <p>
   * NaN entries in either input propagate to NaN in the output. When the maximum of a component is
   * 0 (all values are 0) the normalised value is treated as 1 for every result.
   *
   * @param slawScores pre-computed {@link SlawIntegrationScore} values, one per result
   * @param isoScores  pre-computed {@link IpoIsotopeScore} values, one per result
   * @return array of length {@code slawScores.length} with the normalised harmonic scores
   */
  public static double @NotNull [] computeNormalizedScores(double @NotNull [] slawScores,
      double @NotNull [] isoScores) {
    double slawMax = 0;
    double isoMax = 0;
    for (int i = 0; i < slawScores.length; i++) {
      if (!Double.isNaN(slawScores[i])) {
        slawMax = Math.max(slawMax, slawScores[i]);
      }
      if (!Double.isNaN(isoScores[i])) {
        isoMax = Math.max(isoMax, isoScores[i]);
      }
    }

    final double[] result = new double[slawScores.length];
    for (int i = 0; i < slawScores.length; i++) {
      if (Double.isNaN(slawScores[i]) || Double.isNaN(isoScores[i])) {
        result[i] = Double.NaN;
        continue;
      }
      final double normSlaw = slawMax > 0 ? slawScores[i] / slawMax : 1.0;
      final double normIso = isoMax > 0 ? isoScores[i] / isoMax : 1.0;
      final double sum = normSlaw + normIso;
      result[i] = sum == 0.0 ? 0.0 : 2.0 * normSlaw * normIso / sum;
    }
    return result;
  }
}
