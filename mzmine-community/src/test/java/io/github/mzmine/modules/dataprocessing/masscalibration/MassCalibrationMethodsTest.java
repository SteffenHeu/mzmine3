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

package io.github.mzmine.modules.dataprocessing.masscalibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mzmine.modules.dataprocessing.masscalibration.api.PolynomialMzErrorFit;
import io.github.mzmine.modules.dataprocessing.masscalibration.methods.lockmass.LockmassCalibrationFunction;
import io.github.mzmine.modules.dataprocessing.masscalibration.methods.segment.SegmentCalibrationFunction;
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.junit.jupiter.api.Test;

/**
 * Tests for the calibration functions and the shared polynomial fit. All error models are in
 * absolute m/z (Da): the correction is {@code calibrated = measured − Δ(measured)}.
 */
class MassCalibrationMethodsTest {

  private static final double EPS = 1e-9;

  // ---------------------------------------------------------------------------------------------
  // LockmassCalibrationFunction (absolute Da)
  // ---------------------------------------------------------------------------------------------

  @Test
  void constantOffsetRemovesUniformError() {
    // degree 0, constant +0.002 Da error model
    final LockmassCalibrationFunction fn = new LockmassCalibrationFunction(0, new double[]{0.002}, 0,
        10_000, "const");
    assertEquals(100.0, fn.getCalibratedMz(100.002, 1.0f), 1e-9);
    assertEquals(0.002, fn.modeledDeltaMz(555.0, 1.0f), EPS);
  }

  @Test
  void multiLockmassMzPolynomialModelsDeltaVsMz() {
    // degree 1: Δ(mz) = 0.001 + 1e-6 * mz (Da); m/z range wide enough to avoid clamping
    final LockmassCalibrationFunction fn = new LockmassCalibrationFunction(1,
        new double[]{0.001, 1e-6}, 0, 10_000, "linear in mz");
    assertEquals(0.001 + 1e-6 * 100.0, fn.modeledDeltaMz(100.0, 1.0f), EPS);
    assertEquals(0.001 + 1e-6 * 500.0, fn.modeledDeltaMz(500.0, 1.0f), EPS);
    // calibrated m/z subtracts the modeled Δ
    assertEquals(500.0 - (0.001 + 1e-6 * 500.0), fn.getCalibratedMz(500.0, 1.0f), EPS);
  }

  @Test
  void mzPolynomialClampsOutsideLockmassRange() {
    // Δ(mz) = 0.001 + 1e-6 * mz ; lockmass m/z range [100, 300]
    final LockmassCalibrationFunction fn = new LockmassCalibrationFunction(1,
        new double[]{0.001, 1e-6}, 100, 300, "clamped");
    // within range: evaluated at the actual m/z
    assertEquals(0.001 + 1e-6 * 200.0, fn.modeledDeltaMz(200.0, 1.0f), EPS);
    // below range -> Δ of the smallest lockmass (m/z 100)
    assertEquals(0.001 + 1e-6 * 100.0, fn.modeledDeltaMz(50.0, 1.0f), EPS);
    // above range -> Δ of the largest lockmass (m/z 300)
    assertEquals(0.001 + 1e-6 * 300.0, fn.modeledDeltaMz(1000.0, 1.0f), EPS);
  }

  @Test
  void rtDependentCorrectionClampsToClosestOutsideRange() {
    // degree-0 coefficient (Δ in Da) varying over RT: 0.002 @ rt1, 0.004 @ rt2, 0.006 @ rt3
    final double[] rts = {1.0, 2.0, 3.0};
    final double[] coeff0 = {0.002, 0.004, 0.006};
    final PolynomialSplineFunction spline = new LinearInterpolator().interpolate(rts, coeff0);
    final LockmassCalibrationFunction fn = new LockmassCalibrationFunction(0,
        new PolynomialSplineFunction[]{spline}, 1.0, 3.0, 0, 10_000, "rt-dependent");

    // within range: linear interpolation
    assertEquals(0.004, fn.modeledDeltaMz(100.0, 2.0f), EPS);
    assertEquals(0.003, fn.modeledDeltaMz(100.0, 1.5f), EPS);
    // below range -> closest (rt1)
    assertEquals(0.002, fn.modeledDeltaMz(100.0, 0.0f), EPS);
    // above range -> closest (rt3)
    assertEquals(0.006, fn.modeledDeltaMz(100.0, 5.0f), EPS);
  }

  // ---------------------------------------------------------------------------------------------
  // PolynomialMzErrorFit (math3, absolute Da)
  // ---------------------------------------------------------------------------------------------

  @Test
  void polynomialFitRecoversLinearErrorTrend() {
    final double[] mz = {100, 200, 300, 400};
    final double[] delta = {1e-4, 2e-4, 3e-4, 4e-4}; // Δ = 1e-6 * mz
    final PolynomialFunction poly = PolynomialMzErrorFit.fit(mz, delta, 1);
    assertEquals(5e-4, poly.value(500.0), 1e-9);
  }

  @Test
  void autoDegreePicksLowResidualDegree() {
    // quadratic error: Δ = 1e-4 + 2e-7 * mz^2
    final double[] mz = {-3, -2, -1, 0, 1, 2, 3};
    final double[] delta = new double[mz.length];
    for (int i = 0; i < mz.length; i++) {
      delta[i] = 1e-4 + 2e-7 * mz[i] * mz[i];
    }
    final int degree = PolynomialMzErrorFit.chooseDegreeByLowestResidual(mz, delta,
        PolynomialMzErrorFit.MAX_UI_DEGREE);
    // a degree-2 (or higher) fit reproduces the data; the residual at the chosen degree is ~0
    assertTrue(degree >= 2, "expected at least degree 2, got " + degree);
    final PolynomialFunction poly = PolynomialMzErrorFit.fit(mz, delta, degree);
    assertEquals(0.0, PolynomialMzErrorFit.sumSquaredResiduals(poly, mz, delta), 1e-12);
  }

  @Test
  void autoDegreeIsCappedToKeepFitOverdetermined() {
    // only 3 points -> degree capped at n-2 = 1
    final double[] mz = {100, 200, 300};
    final double[] delta = {1e-4, 5e-4, 2e-4};
    final int degree = PolynomialMzErrorFit.chooseDegreeByLowestResidual(mz, delta, 9);
    assertTrue(degree <= 1, "degree should be capped to 1 for 3 points, got " + degree);
  }

  // ---------------------------------------------------------------------------------------------
  // SegmentCalibrationFunction (absolute Da)
  // ---------------------------------------------------------------------------------------------

  @Test
  void segmentFunctionRemovesModeledError() {
    // constant +0.01 Da error fitted as a degree-0 polynomial
    final double[] mz = {100, 200, 300};
    final double[] delta = {0.01, 0.01, 0.01};
    final PolynomialFunction poly = PolynomialMzErrorFit.fit(mz, delta, 0);
    final SegmentCalibrationFunction fn = new SegmentCalibrationFunction(poly, 100, 300, "segment");
    assertEquals(250.0, fn.getCalibratedMz(250.01, 0.0f), 1e-9);
  }

  @Test
  void segmentFunctionClampsOutsideCalibrantRange() {
    // Δ(mz) = 2e-5 * mz, fitted degree 1, calibrant range [100, 300]
    final double[] mz = {100, 200, 300};
    final double[] delta = {2e-3, 4e-3, 6e-3};
    final PolynomialFunction poly = PolynomialMzErrorFit.fit(mz, delta, 1);
    final SegmentCalibrationFunction fn = new SegmentCalibrationFunction(poly, 100, 300, "segment");

    // a peak at m/z 1000 (far above the calibrant range) uses the Δ at m/z 300
    final double boundaryDelta = poly.value(300.0);
    assertEquals(1000.0 - boundaryDelta, fn.getCalibratedMz(1000.0, 0.0f), 1e-9);
  }
}
