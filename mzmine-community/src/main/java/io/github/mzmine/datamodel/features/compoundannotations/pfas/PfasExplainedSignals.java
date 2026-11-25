package io.github.mzmine.datamodel.features.compoundannotations.pfas;

import com.google.common.collect.Range;
import io.github.mzmine.modules.tools.isotopeprediction.IsotopePatternCalculator;
import io.github.mzmine.util.FormulaUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.openscience.cdk.formula.MolecularFormulaGenerator;
import org.openscience.cdk.formula.MolecularFormulaRange;
import org.openscience.cdk.interfaces.IIsotope;
import org.openscience.cdk.interfaces.IMolecularFormula;
import org.openscience.cdk.silent.SilentChemObjectBuilder;

/**
 * Intensity of an MS2 spectrum explained by rule-matched peaks and by sub-formulas of the precursor
 * ion. Sub-formulas are only searched for the peaks that rules left unexplained, as a neutral loss
 * from the precursor ion. Peaks in the precursor window, peaks less than 5 Da below the precursor
 * and 13C isotope peaks count neither as explained nor in the total, unless a rule matched them.
 *
 * @param subFormulaPeaks     peaks explained by a sub-formula of the precursor ion
 * @param ruleIntensity       intensity of rule-matched peaks
 * @param subFormulaIntensity intensity of sub-formula matched peaks
 * @param totalIntensity      intensity of all peaks outside the precursor window
 */
public record PfasExplainedSignals(@NotNull List<SubFormulaPeak> subFormulaPeaks,
                                   double ruleIntensity, double subFormulaIntensity,
                                   double totalIntensity) {

  private static final double MIN_LOSS = 5;

  public PfasExplainedSignals {
    subFormulaPeaks = List.copyOf(subFormulaPeaks);
  }

  /**
   * A peak explained by a neutral loss from the precursor ion.
   *
   * @param peakIndex   index of the peak in the spectrum
   * @param mz          observed m/z
   * @param intensity   observed intensity
   * @param lossFormula neutral loss from the precursor ion
   */
  public record SubFormulaPeak(int peakIndex, double mz, double intensity,
                               @NotNull String lossFormula) {

  }

  /**
   * @return explained fraction of the total intensity, rules and sub-formulas
   */
  public double explained() {
    return totalIntensity > 0 ? (ruleIntensity + subFormulaIntensity) / totalIntensity : 0d;
  }

  /**
   * @param mzs         observed m/z values
   * @param intensities observed intensities
   * @param precursorMz calculated precursor m/z
   * @param ionFormula  precursor ion formula with charge
   * @param rulePeaks   indices of peaks matched by rules
   * @param settings    matching settings of the annotation run
   * @return explained intensity of rules and sub-formulas
   */
  public static @NotNull PfasExplainedSignals compute(final double @NotNull [] mzs,
      final double @NotNull [] intensities, final double precursorMz,
      final @NotNull IMolecularFormula ionFormula, final @NotNull Set<Integer> rulePeaks,
      final @NotNull PfasMatchSettings settings) {
    double total = 0;
    double basePeak = 0;
    final boolean[] valid = new boolean[mzs.length];
    for (int i = 0; i < mzs.length; i++) {
      // decision: like the MS/MS score of formula prediction, losses below 5 Da (precursor
      // isotopes, FT artefacts) and 13C isotope peaks are neither explained nor unexplained
      valid[i] = !settings.precursorTolerance().checkWithinTolerance(mzs[i], precursorMz) && (
          rulePeaks.contains(i) || (precursorMz - mzs[i] >= MIN_LOSS && !isIsotope(mzs,
              intensities, i, settings)));
      if (valid[i]) {
        total += intensities[i];
        basePeak = Math.max(basePeak, intensities[i]);
      }
    }

    // a sub-formula loses at most every atom of the precursor ion
    final MolecularFormulaRange elements = new MolecularFormulaRange();
    for (final IIsotope isotope : ionFormula.isotopes()) {
      elements.addIsotope(isotope, 0, ionFormula.getIsotopeCount(isotope));
    }

    final double minIntensity = basePeak * settings.minRelativeIntensity();
    double ruleIntensity = 0;
    double subFormulaIntensity = 0;
    final List<SubFormulaPeak> subFormulaPeaks = new ArrayList<>();
    for (int i = 0; i < mzs.length; i++) {
      if (!valid[i]) {
        continue;
      }
      if (rulePeaks.contains(i)) {
        ruleIntensity += intensities[i];
        continue;
      }
      if (intensities[i] < minIntensity) {
        continue;
      }
      // assumption: singly charged precursor and fragment, so the m/z difference is the loss mass
      final Range<Double> peak = settings.fragmentTolerance().getToleranceRange(mzs[i]);
      final double minLoss = precursorMz - peak.upperEndpoint();
      final double maxLoss = precursorMz - peak.lowerEndpoint();
      if (minLoss < 1) {
        continue;
      }
      // decision: the first formula in the window, like the MS/MS score of formula prediction
      final IMolecularFormula loss = new MolecularFormulaGenerator(
          SilentChemObjectBuilder.getInstance(), minLoss, maxLoss, elements).getNextFormula();
      if (loss != null) {
        subFormulaIntensity += intensities[i];
        subFormulaPeaks.add(new SubFormulaPeak(i, mzs[i], intensities[i],
            FormulaUtils.getFormulaString(loss, false)));
      }
    }
    return new PfasExplainedSignals(subFormulaPeaks, ruleIntensity, subFormulaIntensity, total);
  }

  /**
   * @return true if a more intense peak one 13C distance below explains this peak as an isotope
   */
  private static boolean isIsotope(final double @NotNull [] mzs,
      final double @NotNull [] intensities, final int index,
      final @NotNull PfasMatchSettings settings) {
    final double monoisotopic = mzs[index] - IsotopePatternCalculator.THIRTHEEN_C_DISTANCE;
    for (int j = 0; j < mzs.length; j++) {
      if (intensities[j] > intensities[index] && settings.fragmentTolerance()
          .checkWithinTolerance(mzs[j], monoisotopic)) {
        return true;
      }
    }
    return false;
  }
}
