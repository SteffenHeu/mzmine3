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

package io.github.mzmine.modules.dataprocessing.masscalibration.api;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoint;

/**
 * Shared helper to fit a polynomial of measured m/z vs. the <b>absolute</b> mass error (Da,
 * {@code Δ = measured − true}) and to choose a polynomial degree automatically by lowest residual
 * (sum of squared residuals, SSR).
 * <p>
 * Uses commons-math3 {@link PolynomialCurveFitter} / {@link PolynomialFunction} so the unified mass
 * calibration methods (lockmass, calibration segment, internal standards) share one fitting code
 * path with no exponential/logarithmic domain terms. The "Auto" option exposed in the UI maps onto
 * {@link #chooseDegreeByLowestResidual(double[], double[], int)}.
 */
public final class PolynomialMzErrorFit {

  /**
   * Sentinel degree value meaning "choose automatically by lowest residual".
   */
  public static final int AUTO_DEGREE = -1;

  /**
   * Maximum polynomial degree offered in the UI.
   */
  public static final int MAX_UI_DEGREE = 9;

  /**
   * UI label for the automatic-degree option.
   */
  public static final String AUTO_LABEL = "Auto";

  private PolynomialMzErrorFit() {
  }

  /**
   * @return the choices for a degree {@code ComboParameter}: "Auto" followed by 0..{@code maxDegree}.
   */
  public static String[] degreeChoices(int maxDegree) {
    final String[] choices = new String[maxDegree + 2];
    choices[0] = AUTO_LABEL;
    for (int d = 0; d <= maxDegree; d++) {
      choices[d + 1] = Integer.toString(d);
    }
    return choices;
  }

  /**
   * Parse a degree label produced by {@link #degreeChoices(int)}.
   *
   * @param label "Auto" or a non-negative integer string
   * @return {@link #AUTO_DEGREE} for "Auto", otherwise the parsed degree
   */
  public static int parseDegree(String label) {
    return AUTO_LABEL.equalsIgnoreCase(label) ? AUTO_DEGREE : Integer.parseInt(label.trim());
  }

  /**
   * Fit a polynomial of the given degree to (m/z, absolute error) points.
   *
   * @param mz      measured m/z values
   * @param deltaMz corresponding absolute m/z errors (Da), {@code measured − true}
   * @param degree  polynomial degree (&ge; 0)
   * @return the fitted polynomial (use {@link PolynomialFunction#value(double)} to look up the
   * modeled absolute error for an m/z)
   */
  public static PolynomialFunction fit(double[] mz, double[] deltaMz, int degree) {
    final PolynomialCurveFitter fitter = PolynomialCurveFitter.create(degree);
    final List<WeightedObservedPoint> points = new ArrayList<>(mz.length);
    for (int i = 0; i < mz.length; i++) {
      points.add(new WeightedObservedPoint(1.0, mz[i], deltaMz[i]));
    }
    return new PolynomialFunction(fitter.fit(points));
  }

  /**
   * Choose the polynomial degree with the lowest residual (SSR) for the given points. To avoid
   * trivial exact interpolation (which would always pick the highest degree), candidate degrees are
   * capped so that there are always more points than coefficients ({@code degree + 1 < nPoints}).
   *
   * @param mz        measured m/z values
   * @param deltaMz   corresponding absolute m/z errors (Da)
   * @param maxDegree the upper bound on degree to consider (e.g. {@code #lockmasses - 1} or
   *                  {@link #MAX_UI_DEGREE})
   * @return the degree (0..maxDegree) minimizing SSR; 0 if there are too few points
   */
  public static int chooseDegreeByLowestResidual(double[] mz, double[] deltaMz, int maxDegree) {
    final int n = mz.length;
    if (n < 2) {
      return 0;
    }
    // ensure the system stays over-determined: need degree + 1 < n
    final int cappedMax = Math.min(maxDegree, n - 2);
    if (cappedMax <= 0) {
      return 0;
    }

    int bestDegree = 0;
    double bestSsr = Double.POSITIVE_INFINITY;
    for (int degree = 0; degree <= cappedMax; degree++) {
      final PolynomialFunction poly = fit(mz, deltaMz, degree);
      final double ssr = sumSquaredResiduals(poly, mz, deltaMz);
      if (ssr < bestSsr) {
        bestSsr = ssr;
        bestDegree = degree;
      }
    }
    return bestDegree;
  }

  /**
   * Sum of squared residuals between observed absolute errors and the polynomial's modeled errors.
   */
  public static double sumSquaredResiduals(PolynomialFunction poly, double[] mz, double[] deltaMz) {
    double ssr = 0;
    for (int i = 0; i < mz.length; i++) {
      final double residual = deltaMz[i] - poly.value(mz[i]);
      ssr += residual * residual;
    }
    return ssr;
  }
}
