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

package io.github.mzmine.modules.dataprocessing.masscalibration.methods.lockmass;

import io.github.mzmine.modules.dataprocessing.masscalibration.api.PolynomialMzErrorFit;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.ComboParameter;
import io.github.mzmine.parameters.parametertypes.DoubleParameter;
import io.github.mzmine.parameters.parametertypes.StringParameter;
import io.github.mzmine.parameters.parametertypes.combowithinput.ComboWithStringInputParameter;
import io.github.mzmine.parameters.parametertypes.combowithinput.ComboWithStringInputValue;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.MZToleranceParameter;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class LockmassCalibrationParameters extends SimpleParameterSet {

  public static final ComboWithStringInputParameter<LockmassPreset> lockmass =
      new ComboWithStringInputParameter<>(
          new StringParameter("Custom lockmass m/z",
              "One or more lockmass m/z values, comma-separated (e.g. \"556.2766, 1013.99\").", ""),
          LockmassPreset.values(), LockmassPreset.CUSTOM,
          new ComboWithStringInputValue<>(LockmassPreset.LEUCINE_ENKEPHALIN_POS, null));

  public static final MZToleranceParameter mzTolerance = new MZToleranceParameter(
      "m/z tolerance", "Search window around each expected lockmass m/z.", 0.01, 20);

  public static final DoubleParameter minIntensity = new DoubleParameter("Minimum intensity",
      "Lockmass candidate peaks below this intensity are ignored.",
      NumberFormat.getNumberInstance(), 0.0, 0.0, null);

  public static final DoubleParameter loessBandwidth = new DoubleParameter("RT smoothing bandwidth",
      "LOESS bandwidth (fraction of spectra, 0-1) used to smooth the per-spectrum correction over "
          + "retention time. Larger values are more robust to single-spectrum outliers.",
      NumberFormat.getNumberInstance(), 0.3, 0.01, 1.0);

  public static final ComboParameter<String> polynomialDegree = new ComboParameter<>(
      "Polynomial degree",
      "Degree of the per-spectrum m/z polynomial fit to the lockmass errors. Must be at most "
          + "(number of lockmasses - 1). \"Auto\" picks the file-wide degree with the lowest "
          + "residual.", PolynomialMzErrorFit.degreeChoices(PolynomialMzErrorFit.MAX_UI_DEGREE),
      PolynomialMzErrorFit.AUTO_LABEL);

  public LockmassCalibrationParameters() {
    super(lockmass, mzTolerance, minIntensity, loessBandwidth, polynomialDegree);
  }

  /**
   * Resolve the configured lockmass m/z values: either the preset's fixed masses or the parsed
   * comma-separated custom input.
   *
   * @return the lockmass m/z values (may be empty if the custom input could not be parsed)
   */
  public static double[] resolveLockmasses(ComboWithStringInputValue<LockmassPreset> value) {
    final LockmassPreset preset = value.getSelectedOption();
    if (preset != LockmassPreset.CUSTOM) {
      return preset.mzValues();
    }
    final String text = value.getEmbeddedValue();
    if (text == null || text.isBlank()) {
      return new double[0];
    }
    final List<Double> parsed = new ArrayList<>();
    for (String token : text.split(",")) {
      final String trimmed = token.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      try {
        parsed.add(Double.parseDouble(trimmed));
      } catch (NumberFormatException ignored) {
        // reported in checkParameterValues
      }
    }
    return parsed.stream().mapToDouble(Double::doubleValue).toArray();
  }

  @Override
  public boolean checkParameterValues(Collection<String> errorMessages,
      boolean skipRawDataAndFeatureListParameters) {
    boolean ok = super.checkParameterValues(errorMessages, skipRawDataAndFeatureListParameters);

    final double[] lockmasses = resolveLockmasses(getValue(lockmass));
    if (lockmasses.length == 0) {
      errorMessages.add("No valid lockmass m/z values were specified.");
      ok = false;
    }

    final int degree = PolynomialMzErrorFit.parseDegree(getValue(polynomialDegree));
    if (degree != PolynomialMzErrorFit.AUTO_DEGREE && lockmasses.length > 0
        && degree > lockmasses.length - 1) {
      errorMessages.add(("Polynomial degree (%d) must be at most the number of lockmasses minus one "
          + "(%d).").formatted(degree, lockmasses.length - 1));
      ok = false;
    }
    return ok;
  }

  public MZTolerance getMzTolerance() {
    return getValue(mzTolerance);
  }

  @NotNull
  public double[] getLockmasses() {
    return resolveLockmasses(getValue(lockmass));
  }
}
