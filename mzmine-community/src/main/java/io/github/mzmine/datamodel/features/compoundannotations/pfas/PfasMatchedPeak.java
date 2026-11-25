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

package io.github.mzmine.datamodel.features.compoundannotations.pfas;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Persisted view of one observed peak assigned to a predicted PFAS ion. Stores rule ids instead of
 * rules so saved projects stay readable when the catalogue changes.
 *
 * @param observedMz observed m/z
 * @param intensity  observed intensity
 * @param expectedMz predicted m/z
 * @param formula    predicted ion formula without charge
 * @param charge     ion charge
 * @param evidence   strongest evidence level of the explaining rules
 * @param ruleIds     ids of all rules that predict this ion
 * @param lossFormula neutral loss from the precursor if a loss rule predicts this ion, else null
 */
public record PfasMatchedPeak(double observedMz, double intensity, double expectedMz,
                              @NotNull String formula, int charge,
                              @NotNull PfasEvidenceLevel evidence, @NotNull List<String> ruleIds,
                              @Nullable String lossFormula) {

  public PfasMatchedPeak {
    ruleIds = List.copyOf(ruleIds);
  }

  /**
   * @return the predicted ion formula with its charge
   */
  public @NotNull String ionLabel() {
    return PfasIonLabels.ion(formula, charge);
  }

  /**
   * @param ionType precursor ion type, or null if unknown
   * @return the ion formula and, for loss rules, the loss notation, e.g. C7F15⁻ = [M-CO2-H]⁻
   */
  public @NotNull String ionLabel(@Nullable String ionType) {
    return lossFormula == null || ionType == null ? ionLabel()
        : ionLabel() + " = " + PfasIonLabels.loss(ionType, lossFormula);
  }

}
