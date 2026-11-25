package io.github.mzmine.datamodel.features.compoundannotations.pfas;

import com.google.common.collect.Range;
import io.github.mzmine.modules.tools.isotopeprediction.IsotopePatternCalculator;
import io.github.mzmine.util.FormulaUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openscience.cdk.formula.MolecularFormulaGenerator;
import org.openscience.cdk.formula.MolecularFormulaRange;
import org.openscience.cdk.interfaces.IIsotope;
import org.openscience.cdk.interfaces.IMolecularFormula;
import org.openscience.cdk.silent.SilentChemObjectBuilder;

/**
 * Intensity of an MS2 spectrum explained by rule-matched peaks and by sub-formulas of the precursor
 * ion. Sub-formulas are only searched for the peaks that rules left unexplained, as a neutral loss
 * from the precursor ion; a sub-formula that is also a substructure of the structure is preferred.
 * Peaks in the precursor window, peaks less than 5 Da below the precursor and 13C isotope peaks
 * count neither as explained nor in the total, unless a rule matched them.
 *
 * @param subFormulaPeaks       peaks explained by a sub-formula of the precursor ion
 * @param ruleIntensity         intensity of rule-matched peaks
 * @param substructureIntensity intensity of sub-formula peaks that are a substructure
 * @param formulaIntensity      intensity of sub-formula peaks without a fitting substructure
 * @param totalIntensity        intensity of all counted peaks
 * @param fragments             substructure index, null if no peak needed it
 */
public record PfasExplainedSignals(@NotNull List<SubFormulaPeak> subFormulaPeaks,
                                   double ruleIntensity, double substructureIntensity,
                                   double formulaIntensity, double totalIntensity,
                                   @Nullable PfasSubstructureFragments fragments) {

  private static final double MIN_LOSS = 5;
  // shortcut: formulas per peak are capped, raise if wide tolerances miss substructures
  private static final int MAX_FORMULAS_PER_PEAK = 100;

  public PfasExplainedSignals {
    subFormulaPeaks = List.copyOf(subFormulaPeaks);
  }

  /**
   * A peak explained by a neutral loss from the precursor ion.
   *
   * @param peakIndex    index of the peak in the spectrum
   * @param mz           observed m/z
   * @param intensity    observed intensity
   * @param fragmentFormula fragment ion formula without charge, precursor ion minus the loss
   * @param charge          fragment ion charge, the precursor ion charge
   * @param lossFormula     neutral loss from the precursor ion
   * @param substructure    substructure of the annotated structure that explains the fragment, or
   *                        null
   */
  public record SubFormulaPeak(int peakIndex, double mz, double intensity,
                               @NotNull String fragmentFormula, int charge,
                               @NotNull String lossFormula,
                               PfasSubstructureFragments.@Nullable Match substructure) {

    /**
     * @return true if a substructure explains the fragment
     */
    public boolean isSubstructure() {
      return substructure != null;
    }
  }

  /**
   * @param mode signals that count as explained
   * @return explained fraction of the total intensity
   */
  public double explained(@NotNull PfasExplainedSignalsMode mode) {
    if (totalIntensity <= 0) {
      return 0d;
    }
    final double explained = switch (mode) {
      case RULES -> ruleIntensity;
      case RULES_AND_SUBSTRUCTURES -> ruleIntensity + substructureIntensity;
      case ALL -> ruleIntensity + substructureIntensity + formulaIntensity;
    };
    return explained / totalIntensity;
  }

  /**
   * @param mzs         observed m/z values
   * @param intensities observed intensities
   * @param precursorMz calculated precursor m/z
   * @param ionFormula  precursor ion formula with charge
   * @param rulePeaks   indices of peaks matched by rules
   * @param settings    matching settings of the annotation run
   * @param fragments   substructures of the structure, only requested if a peak has a sub-formula
   * @return explained intensity of rules, substructures and sub-formulas
   */
  public static @NotNull PfasExplainedSignals compute(final double @NotNull [] mzs,
      final double @NotNull [] intensities, final double precursorMz,
      final @NotNull IMolecularFormula ionFormula, final @NotNull Set<Integer> rulePeaks,
      final @NotNull PfasMatchSettings settings,
      final @NotNull Supplier<PfasSubstructureFragments> fragments) {
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
    final Map<String, Integer> ionCounts = elementCounts(ionFormula);
    final int charge = ionFormula.getCharge() == null ? 0 : ionFormula.getCharge();

    final double minIntensity = basePeak * settings.minRelativeIntensity();
    double ruleIntensity = 0;
    double substructureIntensity = 0;
    double formulaIntensity = 0;
    final List<SubFormulaPeak> subFormulaPeaks = new ArrayList<>();
    // requested on the first sub-formula only, building the index is the expensive part
    PfasSubstructureFragments index = null;
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
      final MolecularFormulaGenerator generator = new MolecularFormulaGenerator(
          SilentChemObjectBuilder.getInstance(), minLoss, maxLoss, elements);
      // decision: a substructure fragment wins, otherwise the first formula in the window
      IMolecularFormula first = null;
      IMolecularFormula substructure = null;
      PfasSubstructureFragments.Match match = null;
      IMolecularFormula loss;
      for (int n = 0; n < MAX_FORMULAS_PER_PEAK && (loss = generator.getNextFormula()) != null;
          n++) {
        if (first == null) {
          first = loss;
        }
        if (index == null) {
          index = fragments.get();
        }
        match = index.find(minus(ionCounts, elementCounts(loss)));
        if (match != null) {
          substructure = loss;
          break;
        }
      }
      if (first == null) {
        continue;
      }
      final IMolecularFormula chosen = substructure != null ? substructure : first;
      if (substructure != null) {
        substructureIntensity += intensities[i];
      } else {
        formulaIntensity += intensities[i];
      }
      subFormulaPeaks.add(new SubFormulaPeak(i, mzs[i], intensities[i],
          hill(minus(ionCounts, elementCounts(chosen))), charge,
          FormulaUtils.getFormulaString(chosen, false), match));
    }
    return new PfasExplainedSignals(subFormulaPeaks, ruleIntensity, substructureIntensity,
        formulaIntensity, total, index);
  }

  /**
   * @return element symbol to count, H included
   */
  private static @NotNull Map<String, Integer> elementCounts(
      final @NotNull IMolecularFormula formula) {
    final Map<String, Integer> counts = new HashMap<>();
    for (final IIsotope isotope : formula.isotopes()) {
      counts.merge(isotope.getSymbol(), formula.getIsotopeCount(isotope), Integer::sum);
    }
    return counts;
  }

  /**
   * @return formula in Hill order, C and H first, e.g. C7F7
   */
  private static @NotNull String hill(final @NotNull Map<String, Integer> counts) {
    final StringBuilder formula = new StringBuilder();
    final boolean carbon = counts.getOrDefault("C", 0) > 0;
    counts.entrySet().stream().filter(e -> e.getValue() > 0).sorted(
            Comparator.comparing((Map.Entry<String, Integer> e) -> carbon ? hillRank(e.getKey()) : 2)
                .thenComparing(Map.Entry::getKey))
        .forEach(e -> formula.append(e.getKey()).append(e.getValue() > 1 ? e.getValue() : ""));
    return formula.toString();
  }

  private static int hillRank(final @NotNull String element) {
    return switch (element) {
      case "C" -> 0;
      case "H" -> 1;
      default -> 2;
    };
  }

  /**
   * @return element counts of a minus b
   */
  private static @NotNull Map<String, Integer> minus(final @NotNull Map<String, Integer> a,
      final @NotNull Map<String, Integer> b) {
    final Map<String, Integer> result = new HashMap<>(a);
    b.forEach((element, count) -> result.merge(element, -count, Integer::sum));
    return result;
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
