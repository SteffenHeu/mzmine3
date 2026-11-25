package io.github.mzmine.datamodel.features.compoundannotations.pfas;

import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.util.FormulaUtils;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PfasExplainedSignalsTest {

  private static final PfasMatchSettings SETTINGS = new PfasMatchSettings(
      new MZTolerance(0.003, 10), new MZTolerance(0.005, 5), 0.01);

  private static final String PFOA = "OC(=O)C(F)(F)C(F)(F)C(F)(F)C(F)(F)C(F)(F)C(F)(F)C(F)(F)F";

  /**
   * PFOA [M-H]-: the chain ion is rule-matched, C3F7- is a substructure, C2F5O- only a sub-formula
   * (no oxygen next to the CF2CF3 end), the 13C isotope of the chain ion is ignored and a noise
   * peak stays unexplained.
   */
  @Test
  void substructuresArePreferredAndCountedByMode() {
    final double[] mzs = {91.7220, 134.9875, 168.9894, 368.9766, 369.9800, 412.9664};
    final double[] intensities = {10, 5, 20, 100, 8, 500};
    final PfasExplainedSignals signals = PfasExplainedSignals.compute(mzs, intensities, 412.9664,
        FormulaUtils.parse("[C8F15O2]-"), Set.of(3), SETTINGS,
        () -> PfasSubstructureFragments.of(PFOA));
    final List<PfasExplainedSignals.SubFormulaPeak> peaks = signals.subFormulaPeaks();
    Assertions.assertEquals(List.of(1, 2),
        peaks.stream().map(PfasExplainedSignals.SubFormulaPeak::peakIndex).toList());
    Assertions.assertEquals(List.of("C6F10O", "C5F8O2"),
        peaks.stream().map(PfasExplainedSignals.SubFormulaPeak::lossFormula).toList());
    Assertions.assertFalse(peaks.getFirst().isSubstructure());
    // C3F7: the CF3-CF2-CF2 end, cut off by one bond
    final PfasSubstructureFragments.Match c3f7 = peaks.get(1).substructure();
    Assertions.assertNotNull(c3f7);
    Assertions.assertEquals(10, c3f7.atoms().size());
    Assertions.assertEquals(1, c3f7.cutBonds().size());
    Assertions.assertEquals(135, signals.totalIntensity());
    Assertions.assertEquals(100d / 135, signals.explained(PfasExplainedSignalsMode.RULES), 1e-9);
    Assertions.assertEquals(120d / 135,
        signals.explained(PfasExplainedSignalsMode.RULES_AND_SUBSTRUCTURES), 1e-9);
    Assertions.assertEquals(125d / 135, signals.explained(PfasExplainedSignalsMode.ALL), 1e-9);
  }

  @Test
  void hfEliminationOnlyAtFluorotelomerJunctions() {
    final PfasSubstructureFragments pfoa = PfasSubstructureFragments.of(PFOA);
    Assertions.assertTrue(pfoa.contains(Map.of("C", 3, "F", 7)));
    // no CH next to CF, so no HF elimination
    Assertions.assertFalse(pfoa.contains(Map.of("C", 3, "F", 5)));
    final PfasSubstructureFragments fts = PfasSubstructureFragments.of(
        "OS(=O)(=O)CCC(F)(F)C(F)(F)C(F)(F)C(F)(F)C(F)(F)C(F)(F)F");
    // C6F13CH2CH2 minus 4 HF
    Assertions.assertEquals(4, fts.find(Map.of("C", 8, "F", 9)).hfLoss());
  }

  @Test
  void structureIsOnlyParsedForSubFormulas() {
    final double[] mzs = {368.9766, 412.9664};
    final double[] intensities = {100, 500};
    PfasExplainedSignals.compute(mzs, intensities, 412.9664, FormulaUtils.parse("[C8F15O2]-"),
        Set.of(0), SETTINGS, () -> {
          throw new AssertionError("no sub-formula, so no substructures needed");
        });
  }

  @Test
  void lossLabelsInsertTheLossAfterM() {
    Assertions.assertEquals("[M-CO2-H]⁻", PfasIonLabels.loss("[M-H]-", "CO2"));
    Assertions.assertEquals("[M-C2H7N]⁺", PfasIonLabels.loss("[M]+", "C2H7N"));
  }
}
