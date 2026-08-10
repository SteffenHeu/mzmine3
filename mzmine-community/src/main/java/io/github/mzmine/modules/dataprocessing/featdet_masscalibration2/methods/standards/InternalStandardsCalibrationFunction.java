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

package io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.methods.standards;

import io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.api.MzCalibrationFunction;
import java.util.function.DoubleUnaryOperator;

/**
 * Internal-standards / contaminant recalibration function. The correction is an absolute m/z error
 * model (Da, {@code Δ(measuredMz) = measured − true}) — a constant offset, a KNN trend, or a
 * polynomial — applied to every spectrum (retention-time-independent) as
 * {@code calibrated = measured − Δ(measured)}.
 */
public class InternalStandardsCalibrationFunction implements MzCalibrationFunction {

  private final DoubleUnaryOperator deltaMzModel;
  private final double minMz;
  private final double maxMz;
  private final String description;

  /**
   * @param deltaMzModel maps a (clamped) measured m/z to the modeled absolute m/z error (Da)
   * @param minMz        lower bound of the calibrant m/z range; the model is not extrapolated below
   * @param maxMz        upper bound of the calibrant m/z range; the model is not extrapolated above
   */
  public InternalStandardsCalibrationFunction(DoubleUnaryOperator deltaMzModel, double minMz,
      double maxMz, String description) {
    this.deltaMzModel = deltaMzModel;
    this.minMz = minMz;
    this.maxMz = maxMz;
    this.description = description;
  }

  @Override
  public double getCalibratedMz(double measuredMz, float rt) {
    final double clampedMz = Math.max(minMz, Math.min(maxMz, measuredMz));
    return measuredMz - deltaMzModel.applyAsDouble(clampedMz);
  }

  @Override
  public String description() {
    return description;
  }
}
