package io.github.mzmine.modules.dataprocessing.filter_lipidannotationcleanup;

import io.github.mzmine.datamodel.IonizationType;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.types.annotations.LipidMatchListType;
import io.github.mzmine.datamodel.features.types.numbers.MZType;
import io.github.mzmine.datamodel.features.types.numbers.RTType;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.ILipidAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.LipidClasses;
import io.github.mzmine.modules.dataprocessing.id_lipidid.utils.LipidFactory;
import io.github.mzmine.util.FormulaUtils;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds lipid matches and rows with controlled QC score components for cleanup tests. Rows have no
 * ion identity and no isotope pattern, so with default weights and without RT scoring the overall
 * score of a single annotation on a row is {@code (40 * ms1 + 100 * ms2 + 5) / 175}.
 */
final class CleanupTestLipids {

  private static final LipidFactory LIPID_FACTORY = new LipidFactory();

  private CleanupTestLipids() {
  }

  /**
   * @param lipidClass   lipid class
   * @param carbons      chain carbons
   * @param dbes         chain double bonds
   * @param ionization   ionization type of the match
   * @param msMsScore    MS2 explained intensity in {@code [0, 1]}
   * @param ppmDeviation deviation of the measured m/z from the exact m/z
   * @return the match
   */
  static @NotNull MatchedLipid match(final @NotNull LipidClasses lipidClass, final int carbons,
      final int dbes, final @NotNull IonizationType ionization, final double msMsScore,
      final double ppmDeviation) {
    final ILipidAnnotation annotation = LIPID_FACTORY.buildSpeciesLevelLipid(lipidClass, carbons,
        dbes, 0);
    final double exactMz = FormulaUtils.getMonoisotopicMass(annotation.getMolecularFormula())
        + ionization.getAddedMass();
    final double measuredMz = exactMz * (1d + ppmDeviation * 1e-6);
    return new MatchedLipid(annotation, measuredMz, ionization, Set.of(), msMsScore);
  }

  /**
   * @param featureList feature list the row is added to
   * @param id          row ID
   * @param rt          average RT or {@code null}
   * @param matches     lipid matches in this order
   * @return the row
   */
  static @NotNull ModularFeatureListRow row(final @NotNull ModularFeatureList featureList,
      final int id, final @Nullable Float rt, final @NotNull MatchedLipid... matches) {
    final var row = new ModularFeatureListRow(featureList, id);
    // setAverageMZ/RT are bindings without features, so set the types directly
    row.set(MZType.class, matches[0].getAccurateMz());
    row.set(RTType.class, rt);
    row.set(LipidMatchListType.class, List.of(matches));
    featureList.addRow(row);
    return row;
  }
}
