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

package io.github.mzmine.modules.dataprocessing.masscalibration.methods.segment;

import io.github.mzmine.modules.dataprocessing.masscalibration.api.CalibrantListSource;
import io.github.mzmine.modules.dataprocessing.masscalibration.api.PolynomialMzErrorFit;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.ComboParameter;
import io.github.mzmine.parameters.parametertypes.DoubleParameter;
import io.github.mzmine.parameters.parametertypes.combowithinput.ComboWithFileInputParameter;
import io.github.mzmine.parameters.parametertypes.combowithinput.ComboWithFileInputValue;
import io.github.mzmine.parameters.parametertypes.filenames.FileNameParameter;
import io.github.mzmine.parameters.parametertypes.filenames.FileSelectionType;
import io.github.mzmine.parameters.parametertypes.ranges.RTRangeParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.MZToleranceParameter;
import com.google.common.collect.Range;
import java.text.NumberFormat;

public class CalibrationSegmentParameters extends SimpleParameterSet {

  public static final ComboWithFileInputParameter<CalibrantListSource> calibrantList =
      new ComboWithFileInputParameter<>(
          new FileNameParameter("Custom calibrant list",
              "CSV/TSV file of calibrant ions with an \"mz\" column (optional \"rt\").",
              FileSelectionType.OPEN), CalibrantListSource.segmentOptions(),
          CalibrantListSource.CUSTOM_FILE,
          new ComboWithFileInputValue<>(CalibrantListSource.NaFormPosNeg, null));

  public static final RTRangeParameter rtRange = new RTRangeParameter("Calibration segment (RT)",
      "Retention time window (minutes) during which the calibrant mix was injected.", true,
      Range.closed(0.0, 1.0));

  public static final MZToleranceParameter mzTolerance = new MZToleranceParameter("m/z tolerance",
      "Matching tolerance between calibrant m/z and measured peaks.", 0.005, 10);

  public static final DoubleParameter minIntensity = new DoubleParameter("Minimum intensity",
      "Calibrant candidate peaks below this intensity are ignored.",
      NumberFormat.getNumberInstance(), 0.0, 0.0, null);

  public static final ComboParameter<String> polynomialDegree = new ComboParameter<>(
      "Polynomial degree",
      "Degree of the m/z-error polynomial. \"Auto\" picks the degree with the lowest residual.",
      PolynomialMzErrorFit.degreeChoices(PolynomialMzErrorFit.MAX_UI_DEGREE),
      PolynomialMzErrorFit.AUTO_LABEL);

  public CalibrationSegmentParameters() {
    super(calibrantList, rtRange, mzTolerance, minIntensity, polynomialDegree);
  }
}
