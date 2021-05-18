package util;

import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasLibraryBuilder;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasLibraryParser;
import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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


}
