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

package io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.methods.segment;

import io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.api.MzCalibrationFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;

/**
 * Calibration-segment function: a single, retention-time-independent polynomial of the absolute m/z
 * error (Da, {@code Δ = measured − true}) vs. m/z, fitted to calibrant matches found within the
 * segment RT window and applied to the whole file as {@code calibrated = measured − Δ(measured)}.
 */
public class SegmentCalibrationFunction implements MzCalibrationFunction {

  private final PolynomialFunction deltaMz;
  private final double minMz;
  private final double maxMz;
  private final String description;

  /**
   * @param deltaMz polynomial modeling the absolute m/z error (Da) as a function of measured m/z
   * @param minMz   lower bound of the calibrant m/z range; the polynomial is not extrapolated below
   * @param maxMz   upper bound of the calibrant m/z range; the polynomial is not extrapolated above
   */
  public SegmentCalibrationFunction(PolynomialFunction deltaMz, double minMz, double maxMz,
      String description) {
    this.deltaMz = deltaMz;
    this.minMz = minMz;
    this.maxMz = maxMz;
    this.description = description;
  }

  @Override
  public double getCalibratedMz(double measuredMz, float rt) {
    // clamp to the calibrant m/z range: peaks outside keep the smallest/largest calibrant shift
    final double clampedMz = Math.max(minMz, Math.min(maxMz, measuredMz));
    return measuredMz - deltaMz.value(clampedMz);
  }

  @Override
  public String description() {
    return description;
  }
}
