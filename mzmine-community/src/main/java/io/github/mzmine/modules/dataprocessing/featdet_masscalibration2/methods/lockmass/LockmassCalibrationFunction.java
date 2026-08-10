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

package io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.methods.lockmass;

import io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.api.MzCalibrationFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.jetbrains.annotations.Nullable;

/**
 * Lockmass calibration function. The per-spectrum correction is a polynomial of the absolute m/z
 * error (Da, {@code measured − true}) vs. measured m/z; each polynomial coefficient varies smoothly
 * over retention time. The calibrated m/z is {@code measured − Δ(measured, rt)}.
 * <p>
 * For a query at retention time {@code rt}, the coefficients are looked up from the per-coefficient,
 * retention-time-smoothed spline functions (clamped to the observed RT range — i.e. the
 * "closest" correction for points outside the lockmass RT range, such as MSn scans). When there is
 * not enough RT support to smooth, constant coefficients are used instead.
 */
public class LockmassCalibrationFunction implements MzCalibrationFunction {

  private final int degree;
  // exactly one of the following two is non-null
  private final PolynomialSplineFunction @Nullable [] coefficientSplines; // length degree+1
  private final double @Nullable [] constantCoefficients; // length degree+1
  private final double minRt;
  private final double maxRt;
  private final double minMz;
  private final double maxMz;
  private final String description;

  /**
   * RT-dependent model: one smoothed spline per polynomial coefficient.
   *
   * @param minMz lower bound of the lockmass m/z range; the polynomial is not extrapolated below it
   * @param maxMz upper bound of the lockmass m/z range; the polynomial is not extrapolated above it
   */
  public LockmassCalibrationFunction(int degree, PolynomialSplineFunction[] coefficientSplines,
      double minRt, double maxRt, double minMz, double maxMz, String description) {
    this.degree = degree;
    this.coefficientSplines = coefficientSplines;
    this.constantCoefficients = null;
    this.minRt = minRt;
    this.maxRt = maxRt;
    this.minMz = minMz;
    this.maxMz = maxMz;
    this.description = description;
  }

  /**
   * RT-independent fallback: constant coefficients used for all retention times.
   *
   * @param minMz lower bound of the lockmass m/z range; the polynomial is not extrapolated below it
   * @param maxMz upper bound of the lockmass m/z range; the polynomial is not extrapolated above it
   */
  public LockmassCalibrationFunction(int degree, double[] constantCoefficients, double minMz,
      double maxMz, String description) {
    this.degree = degree;
    this.coefficientSplines = null;
    this.constantCoefficients = constantCoefficients;
    this.minRt = Double.NEGATIVE_INFINITY;
    this.maxRt = Double.POSITIVE_INFINITY;
    this.minMz = minMz;
    this.maxMz = maxMz;
    this.description = description;
  }

  @Override
  public double getCalibratedMz(double measuredMz, float rt) {
    return measuredMz - modeledDeltaMz(measuredMz, rt);
  }

  /**
   * @return the modeled absolute m/z error (Da, {@code measured − true}) at the given m/z and
   * retention time. The polynomial is evaluated with the m/z clamped to the lockmass m/z range, so
   * peaks below/above the calibrant range keep the shift of the smallest/largest lockmass rather
   * than being extrapolated.
   */
  public double modeledDeltaMz(double measuredMz, float rt) {
    final double clampedMz = Math.max(minMz, Math.min(maxMz, measuredMz));
    double delta = 0;
    double power = 1;
    for (int k = 0; k <= degree; k++) {
      delta += coefficient(k, rt) * power;
      power *= clampedMz;
    }
    return delta;
  }

  private double coefficient(int k, float rt) {
    if (constantCoefficients != null) {
      return constantCoefficients[k];
    }
    // clamp to the observed RT range so out-of-range queries use the closest correction
    final double clamped = Math.max(minRt, Math.min(maxRt, rt));
    return coefficientSplines[k].value(clamped);
  }

  @Override
  public String description() {
    return description;
  }
}
