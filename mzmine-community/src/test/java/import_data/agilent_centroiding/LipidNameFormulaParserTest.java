package import_data.agilent_centroiding;

import import_data.agilent_centroiding.LipidNameFormulaParser.ParsedLipid;
import io.github.mzmine.datamodel.IonizationType;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.util.FormulaUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Checks the lipid name parsing against literature formulas so that the expected M+1/M ratios and
 * the exact m/z of the centroiding benchmark are based on correct formulas.
 */
@Disabled("Agilent centroiding benchmark, run manually")
class LipidNameFormulaParserTest {

  @ParameterizedTest
  @CsvSource({ //
      "PC 34:1, C42H82NO8P", //
      "PC(16:0_18:1), C42H82NO8P", //
      "PC(14:0_18:1);PC(16:0_16:1), C40H78NO8P", //
      "LPC 16:0, C24H50NO7P", //
      "LPC(0:0/16:0), C24H50NO7P", //
      "LPE(18:1/0:0), C23H46NO7P", //
      "PE 32:0, C37H74NO8P", //
      "SM d34:1, C39H79N2O6P", //
      "Cer(d18:1/24:0), C42H83NO3", //
      "DG(16:0_18:1), C37H70O5", //
      "TG 52:2, C55H102O6", //
      "CE(18:2), C45H76O2", //
      "AC(16:0), C23H46NO4", // cation, observed as [M]+
      "ANA(16:0), C18H37NO2" //
  })
  void parsesFormula(final String name, final String expectedFormula) {
    final ParsedLipid lipid = LipidNameFormulaParser.parse(name);
    Assertions.assertNotNull(lipid, name);
    Assertions.assertEquals(FormulaUtils.calculateExactMass(expectedFormula),
        FormulaUtils.calculateExactMass(lipid.formula()), 1e-4,
        name + " parsed as " + lipid.formula());
  }

  @Test
  void acylcarnitineIsObservedAsCation() {
    final ParsedLipid lipid = LipidNameFormulaParser.parse("AC(16:0)");
    Assertions.assertNotNull(lipid);
    final CentroidingTarget target = CentroidingTarget.create("AC(16:0)", lipid.formula(),
        LipidNameFormulaParser.adductCandidates(lipid.lipidClass(), PolarityType.POSITIVE)
            .getFirst(), null);
    Assertions.assertNotNull(target);
    Assertions.assertEquals(400.3421, target.exactMz(), 1e-3);
  }

  @Test
  void skipsDeuteratedStandards() {
    Assertions.assertNull(LipidNameFormulaParser.parse("PC 33:1(d7)"));
  }

  @Test
  void exactMzOfProtonatedPc() {
    final ParsedLipid lipid = LipidNameFormulaParser.parse("PC 34:1");
    Assertions.assertNotNull(lipid);
    final CentroidingTarget target = CentroidingTarget.create("PC 34:1", lipid.formula(),
        IonizationType.POSITIVE_HYDROGEN, null);
    Assertions.assertNotNull(target);
    Assertions.assertEquals(760.5851, target.exactMz(), 1e-3);
    // C42 -> ~47 % M+1 relative to M
    Assertions.assertEquals(0.48, target.expectedM1Ratio(), 0.03);
  }
}
