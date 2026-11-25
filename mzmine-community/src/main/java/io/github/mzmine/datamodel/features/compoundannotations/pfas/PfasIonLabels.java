package io.github.mzmine.datamodel.features.compoundannotations.pfas;

import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/**
 * Labels of predicted and matched PFAS ions in spectra and tables.
 */
public final class PfasIonLabels {

  private PfasIonLabels() {
  }

  /**
   * @param formula ion formula without charge
   * @param charge  ion charge
   * @return formula with charge, e.g. C7F15⁻
   */
  public static @NotNull String ion(@NotNull String formula, int charge) {
    final String magnitude = Math.abs(charge) > 1 ? Integer.toString(Math.abs(charge)) : "";
    return formula + magnitude + (charge > 0 ? "⁺" : charge < 0 ? "⁻" : "");
  }

  /**
   * @param ionType     precursor ion type, e.g. [M-H]-
   * @param lossFormula neutral loss from the precursor
   * @return the loss inserted after M, e.g. [M-CO2-H]⁻
   */
  public static @NotNull String loss(@NotNull String ionType, @NotNull String lossFormula) {
    final int m = ionType.indexOf('M');
    // assumption: ion types are written like [M-H]-; others get the loss appended
    final String label = m < 0 ? ionType + "-" + lossFormula
        : ionType.substring(0, m + 1) + "-" + lossFormula + ionType.substring(m + 1);
    if (label.endsWith("-")) {
      return label.substring(0, label.length() - 1) + "⁻";
    }
    return label.endsWith("+") ? label.substring(0, label.length() - 1) + "⁺" : label;
  }

  /**
   * @param evidence evidence level
   * @return lower case name, e.g. subclass aligned
   */
  public static @NotNull String evidence(@NotNull PfasEvidenceLevel evidence) {
    return evidence.name().toLowerCase(Locale.ROOT).replace('_', ' ');
  }
}
