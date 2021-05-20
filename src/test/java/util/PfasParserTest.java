package util;

import com.google.common.collect.Range;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasCompound;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasLibraryBuilder;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasLibraryParser;
import io.github.mzmine.util.FormulaUtils;
import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.interfaces.IMolecularFormula;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
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
  public void testParser() throws CloneNotSupportedException {
    final PfasLibraryParser parser = new PfasLibraryParser();
    URL path = this.getClass().getClassLoader().getResource("files/pfos-pfoa-database.CSV");
    File file = new File(path.getFile());
    Assertions.assertEquals(true, parser.read(file));

    for (var entry : parser.getEntries()) {
      logger.info(PfasLibraryBuilder.isValid(entry) + " " + entry.toString());
    }

    PfasLibraryBuilder pfasLibraryBuilder = new PfasLibraryBuilder(parser.getEntries(),
        Range.closed(7, 8), Range.singleton(1), Range.singleton(1));
    pfasLibraryBuilder.buildBlockMap();
    pfasLibraryBuilder.buildLibrary();

    final List<PfasCompound> library = pfasLibraryBuilder.getLibrary();
    Assertions.assertNotEquals(null, library);
    Assertions.assertEquals(4, library.size());

    final PfasCompound pfoa = library.get(0);
    IMolecularFormula ionisedPFOA = FormulaUtils.cloneFormula(pfoa.getFormula());
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> FormulaUtils.calculateMzRatio(ionisedPFOA));

    MolecularFormulaManipulator.adjustProtonation(ionisedPFOA, -1);
    final double mz = FormulaUtils.calculateMzRatio(ionisedPFOA);
    Assertions.assertEquals("[C8F15O2]-", MolecularFormulaManipulator.getString(ionisedPFOA));
    Assert.assertEquals(412.9664261, mz, 0.0000001);

    final PfasCompound pfos = library.get(3);
    Assertions.assertEquals("C8HF17O3S", MolecularFormulaManipulator.getString(pfos.getFormula()));
//    Assertions.assertEquals();
  }

  @Test
  public void testFormulaUtils() {
    IChemObjectBuilder builder = SilentChemObjectBuilder.getInstance();
    IMolecularFormula pfoa = MolecularFormulaManipulator
        .getMajorIsotopeMolecularFormula("C8HF15O2", builder);
    StringBuilder sb = new StringBuilder().append("Neutral: ")
        .append(MolecularFormulaManipulator.getString(pfoa)).append(" ").append(
            MolecularFormulaManipulator.getMass(pfoa, MolecularFormulaManipulator.MostAbundant));

    MolecularFormulaManipulator.adjustProtonation(pfoa, -1);
    final double mz = FormulaUtils.calculateMzRatio(pfoa);

  }
}
