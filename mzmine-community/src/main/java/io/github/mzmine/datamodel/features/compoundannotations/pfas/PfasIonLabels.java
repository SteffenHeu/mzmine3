package io.github.mzmine.datamodel.features.compoundannotations.pfas;

import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  /**
   * Two-line peak label of PFAS spectra, e.g. "218.9862, C4F9⁻" and "Perfluoroalkyl segment,
   * generic". Full formulas, all building blocks and rule ids belong in the tooltip.
   *
   * @param mz          formatted m/z
   * @param formula     fragment ion formula without charge
   * @param charge      fragment ion charge
   * @param ionType     precursor ion type for the loss notation, or null
   * @param lossFormula neutral loss of loss rules, or null to show the formula
   * @param description short description, e.g. the building block
   * @param evidence    evidence level, or null if the signal has none
   * @return "m/z, ion or [M-X]" and "description, evidence"
   */
  public static @NotNull String peakLabel(@NotNull String mz, @NotNull String formula, int charge,
      @Nullable String ionType, @Nullable String lossFormula, @NotNull String description,
      @Nullable PfasEvidenceLevel evidence) {
    // decision: loss rules show only the loss notation, formula and loss together get too long
    final String ion = lossFormula != null && ionType != null ? loss(ionType, lossFormula)
        : ion(formula, charge);
    final String level = evidence == null ? "" : ", " + switch (evidence) {
      case DIAGNOSTIC -> "diagnostic";
      case SUBCLASS_ALIGNED -> "subclass";
      case GENERIC -> "generic";
    };
    return "%s, %s%n%s%s".formatted(mz, ion, description, level);
  }
}
