package io.github.mzmine.modules.visualization.pfas;

import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasAnnotation;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasExplainedSignals;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasMatchSettings;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasMatchedPeak;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasSubstructureFragments;
import io.github.mzmine.datamodel.features.types.annotations.SmilesStructureType;
import io.github.mzmine.datamodel.identities.iontype.IonType;
import io.github.mzmine.datamodel.structures.MolecularStructure;
import io.github.mzmine.util.FormulaUtils;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IMolecularFormula;

/**
 * Signals of one PFAS annotation that are recomputed for the viewer, they are not saved.
 *
 * @param annotation  the annotation these signals belong to
 * @param subFormulas sub-formula explained signals, null if the matching settings are unknown or
 *                    the annotation has no scan, formula or ion type
 * @param fragments   substructures of the structure for highlights; empty without structure
 * @param molecule    structure the substructure atom indices refer to, null without structure
 */
record PfasMatchSignals(@NotNull PfasAnnotation annotation,
                        @Nullable PfasExplainedSignals subFormulas,
                        @NotNull PfasSubstructureFragments fragments,
                        @Nullable IAtomContainer molecule) {

  /**
   * Rebuilds the signals of an annotation. May take a while for large structures, call it off the
   * FX thread.
   *
   * @param annotation annotation with scan, formula, ion type and structure
   * @param settings   matching settings of the annotation run, null if unknown
   * @return the recomputed signals
   */
  static @NotNull PfasMatchSignals compute(final @NotNull PfasAnnotation annotation,
      final @Nullable PfasMatchSettings settings) {
    final PfasExplainedSignals subFormulas = subFormulas(annotation, settings);
    // decision: the structure of the substructure index, so highlighted atom indices match. Built
    // from the stored SMILES if no sub-formula needed it, rule peaks are highlighted as well
    final PfasSubstructureFragments fragments =
        subFormulas != null && subFormulas.fragments() != null ? subFormulas.fragments()
            : PfasSubstructureFragments.of(annotation.get(SmilesStructureType.class));
    final MolecularStructure structure = annotation.getStructure();
    final IAtomContainer molecule = fragments.molecule() != null ? fragments.molecule()
        : structure != null ? structure.structure() : null;
    return new PfasMatchSignals(annotation, subFormulas, fragments, molecule);
  }

  /**
   * @param annotation annotation with an optional scan
   * @return the centroided mass list of the scan if available, else the scan, or null
   */
  static @Nullable MassSpectrum spectrum(final @NotNull PfasAnnotation annotation) {
    final Scan scan = annotation.getScan();
    // decision: the annotation matches the centroided mass list when one is available.
    return scan == null ? null : scan.getMassList() == null ? scan : scan.getMassList();
  }

  /**
   * Rebuilds the sub-formula explained peaks with the settings of the annotation run.
   *
   * @param annotation annotation with scan, formula and ion type
   * @param settings   matching settings of the annotation run, or null if unknown
   * @return explained signals, or null if they cannot be rebuilt
   */
  private static @Nullable PfasExplainedSignals subFormulas(
      final @NotNull PfasAnnotation annotation, final @Nullable PfasMatchSettings settings) {
    final MassSpectrum spectrum = spectrum(annotation);
    final IonType ionType = annotation.getAdductType();
    final Double precursorMz = annotation.getPrecursorMZ();
    final IMolecularFormula neutral = FormulaUtils.parse(annotation.getFormula());
    if (settings == null || spectrum == null || ionType == null || precursorMz == null
        || neutral == null) {
      return null;
    }
    final IMolecularFormula ion = ionType.addToFormula(neutral, true).orElse(null);
    if (ion == null) {
      return null;
    }
    final double[] mzs = spectrum.getMzValues(new double[spectrum.getNumberOfDataPoints()]);
    final double[] intensities = spectrum.getIntensityValues(
        new double[spectrum.getNumberOfDataPoints()]);
    // rule-matched peaks were taken from the same spectrum, so their m/z values are identical
    final Set<Double> matched = annotation.getMatchedPeaks().stream()
        .map(PfasMatchedPeak::observedMz).collect(Collectors.toSet());
    final Set<Integer> rulePeaks = new HashSet<>();
    for (int i = 0; i < mzs.length; i++) {
      if (matched.contains(mzs[i])) {
        rulePeaks.add(i);
      }
    }
    // only full structures are stored as SMILES, building block annotations get no substructures
    final String smiles = annotation.get(SmilesStructureType.class);
    return PfasExplainedSignals.compute(mzs, intensities, precursorMz, ion, rulePeaks, settings,
        () -> PfasSubstructureFragments.of(smiles));
  }
}
