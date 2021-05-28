package util;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.impl.masslist.SimpleMassList;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.IntensityCoverageUtils;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasCompound;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasFragment;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasLibraryBuilder;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasLibraryParser;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.util.FormulaUtils;
import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import org.junit.Assert;
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
  public void testParser() throws CloneNotSupportedException {
    final PfasLibraryParser parser = new PfasLibraryParser();
    URL path = this.getClass().getClassLoader().getResource("files/pfos-pfoa-database.CSV");
    File file = new File(path.getFile());
    Assertions.assertEquals(true, parser.read(file));

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

  }

  @Test
  public void testFragmentSpectrum() {
    final PfasLibraryParser parser = new PfasLibraryParser();
    URL path = this.getClass().getClassLoader().getResource("files/pfas-betaineamide-test.CSV");
    File file = new File(path.getFile());
    Assertions.assertEquals(true, parser.read(file));

    PfasLibraryBuilder pfasLibraryBuilder = new PfasLibraryBuilder(parser.getEntries(),
        Range.closed(8, 8), Range.singleton(1), Range.singleton(1));
    pfasLibraryBuilder.buildBlockMap();
    pfasLibraryBuilder.buildLibrary();

    final List<PfasCompound> library = pfasLibraryBuilder.getLibrary();
    Assertions.assertNotEquals(null, library);
    Assertions.assertEquals(1, library.size());

    final PfasCompound pfoba = library.get(0);
    IMolecularFormula ionisedPFOBA = FormulaUtils.cloneFormula(pfoba.getFormula());
    MolecularFormulaManipulator.adjustProtonation(ionisedPFOBA, -1);

    List<PfasFragment> fragments = pfoba.getObservedIons(PolarityType.NEGATIVE);

    Assertions.assertEquals(11, fragments.size());
    Assertions.assertEquals(368.97659687990944, fragments.get(7).mz());
    Assertions.assertEquals("F(CF2)7-", fragments.get(7).formula());
    Assertions.assertEquals(
        "PfasFragment[mz=545.0527162599095, formula=[M-H-CH2CO2H]-, block=SUBSTITUENT, "
            + "Propylbetaine, (CH2)3N(CH3)2CH2COO, NL(-) 0: [Me2NCH2CO2H, 73.016378336, null], "
            + "NL(-) 1: [CH2CO2H, 59.013304336, null], F(-) 0: [Me2NC2H2O2-, 102.0560521, null], "
            + "NL(+) 0: [CH2CO2, 58.005479304, null], NL(+) 1: [(CH3)2NCH2CO2H, 88.039853432, null], "
            + "NL(+) 2: [C2H4(CH3)2NCH2CO2H, 116.07115356, null], NL(+) 3: "
            + "[COC2H4(CH3)2NCH2CO2H, 144.06606818, Amide], F(-) 0: [Me2NC2H2O2H2+, 104.070605, null]]",
        fragments.get(10).toString());
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

    logger.info("" + coverage);
  }
}
