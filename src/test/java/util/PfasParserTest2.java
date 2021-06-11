package util;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.impl.masslist.SimpleMassList;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.IntensityCoverageUtils;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasCompound;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasFragment;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasLibraryBuilder;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasLibraryParser2;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.util.FormulaUtils;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.logging.Logger;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openscience.cdk.interfaces.IMolecularFormula;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;

public class PfasParserTest2 {

  private static final Logger logger = Logger.getLogger(PfasParserTest2.class.getName());

  @Test
  public void testSplit() {
    String str = ";;;";

    Assertions.assertEquals(4, str.split(";", -1).length);
  }

  @Test
  public void parseTest() {
    final PfasLibraryParser2 parser = new PfasLibraryParser2();

    URL path = this.getClass().getClassLoader().getResource("files/Pfas-database-new-format.csv");
    File file = new File(path.getFile());
    Assertions.assertEquals(true, parser.read(file));

    PfasLibraryBuilder pfasLibraryBuilder = new PfasLibraryBuilder(parser.getEntries(),
        Range.closed(7, 8), Range.singleton(1), Range.singleton(1));
    pfasLibraryBuilder.buildLibrary();

    final List<PfasCompound> library = pfasLibraryBuilder.getLibrary();
    Assertions.assertNotEquals(null, library);
  }

  @Test
  public void testParser() {
    final PfasLibraryParser2 parser = new PfasLibraryParser2();
    URL path = this.getClass().getClassLoader().getResource("files/pfos-pfoa-database-new-format.csv");
    File file = new File(path.getFile());
    Assertions.assertEquals(true, parser.read(file));

    PfasLibraryBuilder pfasLibraryBuilder = new PfasLibraryBuilder(parser.getEntries(),
        Range.closed(7, 8), Range.singleton(1), Range.singleton(1));
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
  public void testFragmentSpectrum() {
    final PfasLibraryParser2 parser = new PfasLibraryParser2();
    URL path = this.getClass().getClassLoader()
        .getResource("files/pfas-betaineamide-test-new-format.CSV");
    File file = new File(path.getFile());
    Assertions.assertEquals(true, parser.read(file));

    Assertions.assertEquals(3, parser.getEntries().size());

    Assertions.assertEquals(
        "BACKBONE, Perfluoroalkyl, F(CF2)nR, F(-) 0: [F(CF2)n-, NaN, null], F(-) 0: [F(CF2)n+, NaN, null]",
        parser.getEntries().get(0).toString());
    Assertions
        .assertEquals("FUNCTIONAL_GROUP, Amide, XCONHZ", parser.getEntries().get(1).toString());
    Assertions.assertEquals("SUBSTITUENT, Propylbetaine, (CH2)3N(CH3)2CH2COO, "
        + "NL(-) 0: [(CH3)2NCH2CO2H, 103.063329, null], " + "NL(-) 1: [CH2CO2H, 59.013304, null], "
        + "F(-) 0: [(CH3)2NC2H2O2-, 102.056052, null], " + "NL(+) 0: [CH2CO2, 58.005479, null], "
        + "NL(+) 1: [(CH3)2NCH2CO2H, 103.063329, null], "
        + "NL(+) 2: [C2H4(CH3)2NCH2CO2H, 131.094629, null], "
        + "NL(+) 3: [COC2H4(CH3)2NCH2CO2H, 159.089543, Amide], "
        + "F(-) 0: [(CH3)2NC2H2O2H2+, 104.070605, null]", parser.getEntries().get(2).toString());

    PfasLibraryBuilder pfasLibraryBuilder = new PfasLibraryBuilder(parser.getEntries(),
        Range.closed(8, 8), Range.singleton(1), Range.singleton(1));
    pfasLibraryBuilder.buildLibrary();

    final List<PfasCompound> library = pfasLibraryBuilder.getLibrary();
    Assertions.assertNotEquals(null, library);
    Assertions.assertEquals(1, library.size());

    final PfasCompound pfoba = library.get(0);
    IMolecularFormula ionisedPFOBA = FormulaUtils.cloneFormula(pfoba.getFormula());
    MolecularFormulaManipulator.adjustProtonation(ionisedPFOBA, -1);
//    logger.info(MolecularFormulaManipulator.getString(ionisedPFOBA));
//    double mz = MolecularFormulaManipulator.getMass(ionisedPFOBA);

    List<PfasFragment> fragments = pfoba.getObservedIons(PolarityType.NEGATIVE);

    Assertions.assertEquals(11, fragments.size());
    Assertions.assertEquals(368.97659687990944, fragments.get(8).mz());
    Assertions.assertEquals("F(CF2)7-", fragments.get(8).formula());
    Assertions.assertEquals(
        "PfasFragment[mz=546.060541, formula=[M-H-CH2CO2H]-, block=SUBSTITUENT, "
            + "Propylbetaine, (CH2)3N(CH3)2CH2COO, NL(-) 0: [(CH3)2NCH2CO2H, 103.063329, null], "
            + "NL(-) 1: [CH2CO2H, 59.013304, null], F(-) 0: [(CH3)2NC2H2O2-, 102.056052, null], "
            + "NL(+) 0: [CH2CO2, 58.005479, null], NL(+) 1: [(CH3)2NCH2CO2H, 103.063329, null], "
            + "NL(+) 2: [C2H4(CH3)2NCH2CO2H, 131.094629, null], "
            + "NL(+) 3: [COC2H4(CH3)2NCH2CO2H, 159.089543, Amide], "
            + "F(-) 0: [(CH3)2NC2H2O2H2+, 104.070605, null]]", fragments.get(10).toString());

    fragments.forEach(f -> logger.info(f.toString()));
  }

  @Test
  public void intensityCoverageTest() {
    final double[] spectrumMzs = new double[]{100, 200, 300, 400, 423, 450, 475, 500, 600, 705};
    final double[] spectrumIntensities = new double[]{1000, 1000, 300, 500, 1000, 300, 300, 1000,
        500, 2000};
    final double[] matchingMzs = new double[]{100.001, 199.999, 200.001, 350, 357, 400.001, 475,
        599.998, 705, 800, 900};

    final MassSpectrum ml = new SimpleMassList(null, spectrumMzs, spectrumIntensities);

    Assertions.assertEquals(7900, ml.getTIC());

    final double coverage = IntensityCoverageUtils
        .getIntensityCoverage(ml, matchingMzs, new MZTolerance(0.01, 0d));

    Assertions.assertEquals(0.6708860759493671, coverage);
  }
}
