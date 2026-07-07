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

package io.github.mzmine.modules.dataprocessing.masscalibration.methods.standards.errormodel;

import io.github.mzmine.modules.dataprocessing.masscalibration.api.PolynomialMzErrorFit;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.ComboParameter;
import io.github.mzmine.parameters.parametertypes.DoubleParameter;
import java.text.NumberFormat;

/**
 * "Auto" fits all three models and keeps the lowest-residual one; it therefore needs both the KNN
 * neighbor percentage and the OLS degree (which may itself be "Auto").
 */
public class AutoErrorModelParameters extends SimpleParameterSet {

  public static final DoubleParameter nearestNeighborsPercentage = new DoubleParameter(
      "KNN neighbors (%)", "Percentage of nearest neighbors used by the KNN regression candidate.",
      NumberFormat.getNumberInstance(), 10.0, 0.0, 100.0);

  public static final ComboParameter<String> polynomialDegree = new ComboParameter<>(
      "Polynomial degree",
      "Degree of the OLS candidate. \"Auto\" picks the degree with the lowest residual.",
      PolynomialMzErrorFit.degreeChoices(PolynomialMzErrorFit.MAX_UI_DEGREE),
      PolynomialMzErrorFit.AUTO_LABEL);

  public AutoErrorModelParameters() {
    super(nearestNeighborsPercentage, polynomialDegree);
  }
}
