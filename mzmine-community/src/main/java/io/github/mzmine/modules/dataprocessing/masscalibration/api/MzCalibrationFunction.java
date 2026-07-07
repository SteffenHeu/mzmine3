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

/**
 * The fitted, file-specific result of a {@link MzCalibrationMethod}. It maps a measured m/z observed
 * at a given retention time to a calibrated m/z.
 * <p>
 * The retention time argument lets retention-time-dependent methods (e.g. lockmass) pick the
 * closest or interpolated correction for that point in the run. Retention-time-independent methods
 * (calibration segment, internal standards) ignore it.
 */
public interface MzCalibrationFunction {

  /**
   * Calibrated m/z for a measured m/z observed at retention time {@code rt} (minutes).
   *
   * @param measuredMz the measured (uncalibrated) m/z
   * @param rt         the retention time (minutes) of the spectrum the peak was observed in
   * @return the calibrated m/z
   */
  double getCalibratedMz(double measuredMz, float rt);

  /**
   * Vectorized convenience method. The default implementation loops over
   * {@link #getCalibratedMz(double, float)}; implementations may override for speed. A new array is
   * returned, the input is not modified.
   *
   * @param measuredMz the measured (uncalibrated) m/z values
   * @param rt         the retention time (minutes) of the spectrum
   * @return a new array of calibrated m/z values
   */
  default double[] getCalibratedMz(double[] measuredMz, float rt) {
    final double[] calibrated = new double[measuredMz.length];
    for (int i = 0; i < measuredMz.length; i++) {
      calibrated[i] = getCalibratedMz(measuredMz[i], rt);
    }
    return calibrated;
  }

  /**
   * @return a short human-readable description of the fitted model (including fit quality where
   * available), shown in the preview and in the log.
   */
  String description();
}
