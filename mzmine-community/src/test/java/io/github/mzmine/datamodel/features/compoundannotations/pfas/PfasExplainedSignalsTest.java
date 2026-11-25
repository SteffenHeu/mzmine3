package io.github.mzmine.datamodel.features.compoundannotations.pfas;

import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.util.FormulaUtils;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PfasExplainedSignalsTest {

  private static final PfasMatchSettings SETTINGS = new PfasMatchSettings(
      new MZTolerance(0.003, 10), new MZTolerance(0.005, 5), 0.01);

  /**
   * PFOA [M-H]-: the chain ion is rule-matched, C3F7- is a sub-formula, the 13C isotope of the
   * chain ion is ignored and a noise peak stays unexplained.
   */
  @Test
  void subFormulasExplainRemainingSignals() {
    final double[] mzs = {91.7220, 168.9894, 368.9766, 369.9800, 412.9664};
    final double[] intensities = {10, 20, 100, 8, 500};
    final PfasExplainedSignals signals = PfasExplainedSignals.compute(mzs, intensities, 412.9664,
        FormulaUtils.parse("[C8F15O2]-"), Set.of(2), SETTINGS);
    Assertions.assertEquals(List.of(
            new PfasExplainedSignals.SubFormulaPeak(1, 168.9894, 20, "C5F8O2")),
        signals.subFormulaPeaks());
    Assertions.assertEquals(100, signals.ruleIntensity());
    Assertions.assertEquals(130, signals.totalIntensity());
    Assertions.assertEquals(120d / 130, signals.explained(), 1e-9);
  }

  @Test
  void lossLabelsInsertTheLossAfterM() {
    Assertions.assertEquals("[M-CO2-H]⁻", PfasIonLabels.loss("[M-H]-", "CO2"));
    Assertions.assertEquals("[M-C2H7N]⁺", PfasIonLabels.loss("[M]+", "C2H7N"));
  }
}
