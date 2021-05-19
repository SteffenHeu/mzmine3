package util;

import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasLibraryBuilder;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasLibraryParser;
import io.github.mzmine.util.FormulaUtils;
import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openscience.cdk.interfaces.IMolecularFormula;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;

public class PfasParserTest {

  private static final Logger logger = Logger.getLogger(PfasParserTest.class.getName());

  @Test
  public void testSplit() {
    String str = ";;;";

    logger.info(() -> Arrays.toString(str.split(";", -1)) + "");
    Assertions.assertEquals(4, str.split(";", -1).length);
  }

  @Test
  public void testParser() {
    final PfasLibraryParser parser = new PfasLibraryParser();
    URL path = this.getClass().getClassLoader().getResource("files/pfas_example.csv");
    File file = new File(path.getFile());
    Assertions.assertEquals(true, parser.read(file));

    for (var entry : parser.getEntries()) {
      logger.info(PfasLibraryBuilder.isValid(entry) + " " + entry.toString());
    }
  }

  @Test
  public void testFormulaUtils() {
    String f1 = "C2H1OH";
    String f2 = "[C2H1O2]2-";

    IMolecularFormula form1 = FormulaUtils.createMajorIsotopeMolFormula("N");
    IMolecularFormula form2 = FormulaUtils.createMajorIsotopeMolFormula("[C]2-");
    IMolecularFormula form3 = FormulaUtils.createMajorIsotopeMolFormula("[O]+");

    form1 = FormulaUtils.addFormula(form1, form2);
    logger.info(form1.toString());

    form1 = FormulaUtils.addFormula(form1, form3);
    logger.info(MolecularFormulaManipulator.getString(form1));

    MolecularFormulaManipulator.adjustProtonation(form1, 2);
    logger.info(MolecularFormulaManipulator
        .getString(form1));
  }
}
