package import_data.agilent_centroiding;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import testutils.MZmineTestUtil;

/**
 * Generates the target index ({@code target_index.csv}) of each benchmark dataset from a centroided
 * import of all frames. Existing indices are kept unless
 * {@code -Dmzmine.test.agilent.centroiding.regenerateIndex=true} is set. Datasets that are not
 * present locally are skipped.
 */
@Disabled("Agilent centroiding benchmark, run manually")
@TestInstance(Lifecycle.PER_CLASS)
@DisabledOnOs({OS.MAC, OS.LINUX})
public class AgilentTargetIndexGeneratorTest {

  private static final Logger logger = Logger.getLogger(
      AgilentTargetIndexGeneratorTest.class.getName());

  @BeforeAll
  void init() {
    MZmineTestUtil.startMzmineCore();
  }

  @ParameterizedTest
  @EnumSource(CentroidingDataset.class)
  void generateIndex(final CentroidingDataset dataset) throws IOException {
    Assumptions.assumeTrue(dataset.exists(), "dataset missing: " + dataset.folder());
    Assumptions.assumeTrue(
        AgilentCentroidingTestConfig.regenerateIndex() || !dataset.indexFile().exists(),
        "index exists, set -D" + AgilentCentroidingTestConfig.REGENERATE_INDEX_PROPERTY
            + "=true to regenerate");

    final List<TargetIndexEntry> entries = TargetIndexBuilder.build(dataset);
    TargetIndexCsv.write(dataset.indexFile(), entries);

    final long saturated = entries.stream().filter(TargetIndexEntry::potentiallySaturated).count();
    logger.info("%s: %d indexed targets, %d potentially saturated -> %s".formatted(dataset,
        entries.size(), saturated, dataset.indexFile()));
    Assertions.assertFalse(entries.isEmpty(), "no target found in " + dataset);
  }
}
