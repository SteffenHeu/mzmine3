package import_data.agilent_centroiding;

import io.github.mzmine.datamodel.PolarityType;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

/**
 * Agilent 6560 benchmark datasets downloaded to {@link AgilentCentroidingTestConfig#rootDir()}.
 * Each dataset folder contains one or more IMS {@code .d} folders and a target list. See the
 * README.md in the root folder for the origin of each dataset.
 * <p>
 * decision: MSV000088215 (NIST serum PFAS) was removed, all centroiding methods showed the same
 * +12 to +15 ppm offset, so the file is not calibrated properly and only adds calibration error.
 */
public enum CentroidingDataset {

  /**
   * Flow injection of PFAS standards at three amounts (50 pg, 1 ng, 5 ng), ESI-.
   */
  MSV000096020(PolarityType.NEGATIVE),
  /**
   * Agilent tune mix infusion, raw and PNNL PreProcessor output, ESI+.
   */
  MSV000085979(PolarityType.POSITIVE),
  /**
   * NIST SRM 1950 plasma lipids, LC all-ions, raw and PNNL PreProcessor output, ESI+.
   */
  MSV000087295(PolarityType.POSITIVE),
  /**
   * Rat brain micropunch lipidomics, 2.0 mm and 0.5 mm punch, ESI+.
   */
  MSV000088206(PolarityType.POSITIVE),
  /**
   * EPA 533 PFAS calibration levels + tune mix (demultiplexed), ESI-.
   */
  MSV000096126(PolarityType.NEGATIVE);

  private final PolarityType polarity;

  CentroidingDataset(@NotNull final PolarityType polarity) {
    this.polarity = polarity;
  }

  public @NotNull PolarityType polarity() {
    return polarity;
  }

  public @NotNull File folder() {
    return new File(AgilentCentroidingTestConfig.rootDir(), name());
  }

  public @NotNull File indexFile() {
    return new File(folder(), AgilentCentroidingTestConfig.INDEX_FILE_NAME);
  }

  public boolean exists() {
    return folder().isDirectory();
  }

  /**
   * @return all IMS .d folders (with IMSFrame.bin) below the dataset folder, sorted by path
   */
  public @NotNull List<File> rawFiles() {
    final Path root = folder().toPath();
    try (Stream<Path> paths = Files.walk(root, 4)) {
      return paths.filter(Files::isDirectory)
          .filter(p -> p.getFileName().toString().endsWith(".d"))
          .filter(p -> Files.exists(p.resolve("AcqData").resolve("IMSFrame.bin")))
          // skip .d folders nested in other .d folders
          .filter(p -> !p.getParent().getFileName().toString().endsWith(".d")).sorted()
          .map(Path::toFile).toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * @return the path of the raw file relative to the dataset folder, used as key in the index
   */
  public @NotNull String relativePath(@NotNull final File rawFile) {
    return folder().toPath().relativize(rawFile.toPath()).toString().replace('\\', '/');
  }

  public @NotNull File resolve(@NotNull final String relativePath) {
    return new File(folder(), relativePath);
  }

  /**
   * Targets for each raw file. Most datasets use one list for all files.
   */
  public @NotNull Map<File, List<CentroidingTarget>> loadTargets() {
    final Map<File, List<CentroidingTarget>> targets = new LinkedHashMap<>();
    for (final File rawFile : rawFiles()) {
      targets.put(rawFile, loadTargets(rawFile));
    }
    return targets;
  }

  private @NotNull List<CentroidingTarget> loadTargets(@NotNull final File rawFile) {
    final File folder = folder();
    final File lists = new File(folder, "target_list");
    return switch (this) {
      case MSV000096020 ->
          CentroidingTargetListReader.readFormulaList(new File(lists, "targets_FIA_analytes.csv"));
      case MSV000085979 ->
          CentroidingTargetListReader.readTuneMix(new File(folder, "targets_tunemix_ccs.tsv"),
              polarity);
      case MSV000087295 -> {
        // the PreProcessor output has its own feature table
        final String table = rawFile.getName().contains("_MA-") ? "Features_preprocessed.tsv"
            : "Features_original.tsv";
        yield CentroidingTargetListReader.readMassProfilerLipids(new File(lists, table), polarity);
      }
      case MSV000088206 -> CentroidingTargetListReader.readLipidNames(
          new File(lists, "targets_lipids_downloaded_samples.csv"), polarity);
      case MSV000096126 -> {
        final List<CentroidingTarget> all = new ArrayList<>(
            CentroidingTargetListReader.readSkylineDocumentExport(
                new File(lists, "targets_EPA533_from_skyline.csv")));
        all.addAll(CentroidingTargetListReader.readSkylinePfasLibrary(
            new File(lists, "targets_PFAS_CCS_library.csv"), polarity));
        yield all.stream().filter(distinctIonFormula()).toList();
      }
    };
  }

  private static @NotNull Predicate<CentroidingTarget> distinctIonFormula() {
    final Set<String> seen = new HashSet<>();
    return t -> seen.add(t.ionFormula());
  }
}
