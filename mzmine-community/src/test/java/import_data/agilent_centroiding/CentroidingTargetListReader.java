package import_data.agilent_centroiding;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import import_data.agilent_centroiding.LipidNameFormulaParser.ParsedLipid;
import io.github.mzmine.datamodel.IonizationType;
import io.github.mzmine.datamodel.PolarityType;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Readers for the different target list formats of the benchmark datasets. All readers return
 * targets with calculated exact m/z; measured m/z values (if present in a list) are only used to
 * choose the adduct.
 */
public final class CentroidingTargetListReader {

  private static final Logger logger = Logger.getLogger(
      CentroidingTargetListReader.class.getName());

  /**
   * Max deviation between a measured feature m/z and the calculated m/z of an adduct candidate to
   * accept that adduct.
   */
  private static final double ADDUCT_GUARD_PPM = 15;

  private CentroidingTargetListReader() {
  }

  /**
   * Baker lab Skyline transition template (PFAS library incl. Agilent tune mix). Columns: Molecule
   * List Name, Molecule Name, Molecular Formula, Precursor Adduct, Precursor Charge, ... Label
   * Type.
   */
  public static @NotNull List<CentroidingTarget> readSkylinePfasLibrary(@NotNull final File csv,
      @NotNull final PolarityType polarity) {
    final List<String[]> rows = readCsv(csv);
    final String[] header = rows.getFirst();
    final int nameCol = column(header, "Molecule Name");
    final int formulaCol = column(header, "Molecular Formula");
    final int adductCol = column(header, "Precursor Adduct");
    final int labelCol = column(header, "Label Type");

    final List<CentroidingTarget> targets = new ArrayList<>();
    for (final String[] row : rows.subList(1, rows.size())) {
      if (labelCol < row.length && row[labelCol].toLowerCase().contains("heavy")) {
        continue;
      }
      final IonizationType ion = skylineAdduct(row[adductCol]);
      if (ion == null || ion.getPolarity() != polarity || row[formulaCol].isBlank()) {
        continue;
      }
      addTarget(targets, row[nameCol], row[formulaCol], ion, null);
    }
    return deduplicate(targets);
  }

  /**
   * Targets extracted from a Skyline small molecule document (light precursors only).
   */
  public static @NotNull List<CentroidingTarget> readSkylineDocumentExport(
      @NotNull final File csv) {
    final List<String[]> rows = readCsv(csv);
    final String[] header = rows.getFirst();
    final int nameCol = column(header, "name");
    final int formulaCol = column(header, "neutral_formula");
    final int labelCol = column(header, "label");
    final int chargeCol = column(header, "charge");

    final List<CentroidingTarget> targets = new ArrayList<>();
    for (final String[] row : rows.subList(1, rows.size())) {
      if (!"light".equals(row[labelCol])) {
        continue;
      }
      // assumption: the EPA 533 document only contains deprotonated / protonated precursors
      final IonizationType ion = Integer.parseInt(row[chargeCol]) < 0
          ? IonizationType.NEGATIVE_HYDROGEN : IonizationType.POSITIVE_HYDROGEN;
      addTarget(targets, row[nameCol], row[formulaCol], ion, null);
    }
    return deduplicate(targets);
  }

  /**
   * Simple target list with columns name/abbreviation, neutral_formula and adduct.
   */
  public static @NotNull List<CentroidingTarget> readFormulaList(@NotNull final File csv) {
    final List<String[]> rows = readCsv(csv);
    final String[] header = rows.getFirst();
    final int nameCol = column(header, "abbreviation");
    final int formulaCol = column(header, "neutral_formula");
    final int adductCol = column(header, "adduct");

    final List<CentroidingTarget> targets = new ArrayList<>();
    for (final String[] row : rows.subList(1, rows.size())) {
      final IonizationType ion = Arrays.stream(IonizationType.values())
          .filter(t -> t.getAdductName().equals(row[adductCol])).findFirst().orElse(null);
      if (ion == null) {
        logger.warning("Unknown adduct " + row[adductCol] + " in " + csv);
        continue;
      }
      addTarget(targets, row[nameCol], row[formulaCol], ion, null);
    }
    return targets;
  }

  /**
   * Agilent ESI tune mix. The reference list only contains m/z and CCS, so the formulas of the
   * homologous series are generated and matched to the listed m/z.
   */
  public static @NotNull List<CentroidingTarget> readTuneMix(@NotNull final File tsv,
      @NotNull final PolarityType polarity) {
    final List<CentroidingTarget> candidates = new ArrayList<>();
    if (polarity == PolarityType.POSITIVE) {
      addTarget(candidates, "Betaine", "C5H11NO2", IonizationType.POSITIVE_HYDROGEN, null);
      // HP-0321 ... HP-2721: each step adds C6F12
      for (int k = 1; k <= 9; k++) {
        addTarget(candidates, "ATM " + (322 + 300 * (k - 1)),
            "C%dH18F%dN3O6P3".formatted(6 * k, 12 * (k - 1)), IonizationType.POSITIVE_HYDROGEN,
            null);
      }
    } else {
      addTarget(candidates, "ATM 301", "C6H2ON3F9", IonizationType.NEGATIVE_HYDROGEN, null);
      addTarget(candidates, "ATM 601", "C12H2ON3F21", IonizationType.NEGATIVE_HYDROGEN, null);
      for (int j = 0; j <= 5; j++) {
        addTarget(candidates, "ATM " + (1033 + 300 * j),
            "C%dH19O8N3P3F%d".formatted(20 + 6 * j, 27 + 12 * j), IonizationType.NEGATIVE_HYDROGEN,
            null);
      }
    }

    final String sign = polarity == PolarityType.POSITIVE ? "pos" : "neg";
    final List<CentroidingTarget> targets = new ArrayList<>();
    final List<String[]> rows = readTsv(tsv);
    for (final String[] row : rows.subList(1, rows.size())) {
      if (!sign.equals(row[3])) {
        continue;
      }
      final double listedMz = Double.parseDouble(row[1]);
      candidates.stream()
          .filter(t -> Math.abs(AgilentCentroidingTestConfig.ppmError(listedMz, t.exactMz()))
              <= ADDUCT_GUARD_PPM).findFirst().ifPresentOrElse(targets::add,
              () -> logger.warning("No tune mix formula for listed m/z " + listedMz));
    }
    return targets;
  }

  /**
   * Agilent Mass Profiler feature table (ID Result, m/z, Sat.). Only identified lipids are used.
   * The measured m/z only selects the adduct, the target m/z is calculated from the formula.
   */
  public static @NotNull List<CentroidingTarget> readMassProfilerLipids(@NotNull final File tsv,
      @NotNull final PolarityType polarity) {
    final List<String[]> rows = readTsv(tsv);
    final String[] header = rows.getFirst();
    final int idCol = column(header, "ID Result");
    final int mzCol = column(header, "m/z");
    final int satCol = column(header, "Sat.");

    final List<CentroidingTarget> targets = new ArrayList<>();
    int skipped = 0;
    for (final String[] row : rows.subList(1, rows.size())) {
      final String name = idCol < row.length ? row[idCol].trim() : "";
      if (name.isEmpty()) {
        continue;
      }
      final ParsedLipid lipid = LipidNameFormulaParser.parse(name);
      if (lipid == null) {
        skipped++;
        continue;
      }
      final double measuredMz = Double.parseDouble(row[mzCol]);
      final boolean vendorSaturated = satCol < row.length && "x".equalsIgnoreCase(
          row[satCol].trim());

      CentroidingTarget match = null;
      for (final IonizationType ion : LipidNameFormulaParser.adductCandidates(lipid.lipidClass(),
          polarity)) {
        final CentroidingTarget candidate = CentroidingTarget.create(name, lipid.formula(), ion,
            vendorSaturated);
        if (candidate != null && Math.abs(
            AgilentCentroidingTestConfig.ppmError(measuredMz, candidate.exactMz()))
            <= ADDUCT_GUARD_PPM) {
          match = candidate;
          break;
        }
      }
      if (match == null) {
        skipped++;
        logger.info("No adduct matches measured m/z %.4f for %s (%s)".formatted(measuredMz, name,
            lipid.formula()));
        continue;
      }
      targets.add(match);
    }
    logger.info("%s: %d lipid targets, %d skipped".formatted(tsv.getName(), targets.size(),
        skipped));
    return deduplicate(targets);
  }

  /**
   * Lipid names from the Odenkirk 2022 SI export ({@code lipid, ionization_mode, ...}). Without a
   * measured m/z the most likely adduct per class is used.
   */
  public static @NotNull List<CentroidingTarget> readLipidNames(@NotNull final File csv,
      @NotNull final PolarityType polarity) {
    final String mode = polarity == PolarityType.POSITIVE ? "Positive" : "Negative";
    final List<CentroidingTarget> targets = new ArrayList<>();
    int skipped = 0;
    final List<String[]> rows = readCsv(csv);
    for (final String[] row : rows.subList(1, rows.size())) {
      if (!mode.equals(row[1])) {
        continue;
      }
      final ParsedLipid lipid = LipidNameFormulaParser.parse(row[0]);
      if (lipid == null) {
        skipped++;
        continue;
      }
      final IonizationType ion = LipidNameFormulaParser.adductCandidates(lipid.lipidClass(),
          polarity).getFirst();
      addTarget(targets, row[0], lipid.formula(), ion, null);
    }
    logger.info("%s: %d lipid targets, %d skipped".formatted(csv.getName(), targets.size(),
        skipped));
    return deduplicate(targets);
  }

  private static void addTarget(@NotNull final List<CentroidingTarget> targets,
      @NotNull final String name, @NotNull final String formula,
      @NotNull final IonizationType ion, @Nullable final Boolean vendorSaturated) {
    final CentroidingTarget target = CentroidingTarget.create(name, formula, ion, vendorSaturated);
    if (target == null) {
      logger.warning("Cannot ionize %s (%s) as %s".formatted(name, formula, ion));
      return;
    }
    targets.add(target);
  }

  /**
   * Same ion formula = same target. Keeps the first name.
   */
  private static @NotNull List<CentroidingTarget> deduplicate(
      @NotNull final List<CentroidingTarget> targets) {
    final Map<String, CentroidingTarget> unique = new LinkedHashMap<>();
    for (final CentroidingTarget target : targets) {
      unique.putIfAbsent(target.ionFormula(), target);
    }
    return new ArrayList<>(unique.values());
  }

  private static @Nullable IonizationType skylineAdduct(@NotNull final String adduct) {
    return switch (adduct.trim()) {
      case "M+H", "[M+H]+" -> IonizationType.POSITIVE_HYDROGEN;
      case "M-H", "[M-H]-" -> IonizationType.NEGATIVE_HYDROGEN;
      case "M+NH4", "[M+NH4]+" -> IonizationType.AMMONIUM;
      case "M+Na", "[M+Na]+" -> IonizationType.SODIUM;
      case "M+Cl", "[M+Cl]-" -> IonizationType.CHLORIDE;
      default -> null;
    };
  }

  private static int column(@NotNull final String[] header, @NotNull final String name) {
    for (int i = 0; i < header.length; i++) {
      if (header[i].trim().equalsIgnoreCase(name)) {
        return i;
      }
    }
    throw new IllegalArgumentException("Column %s not found in %s".formatted(name,
        Arrays.toString(header)));
  }

  private static @NotNull List<String[]> readCsv(@NotNull final File csv) {
    try (final CSVReader reader = new CSVReader(new FileReader(csv, StandardCharsets.UTF_8))) {
      return reader.readAll();
    } catch (IOException | CsvException e) {
      throw new IllegalStateException("Cannot read " + csv, e);
    }
  }

  private static @NotNull List<String[]> readTsv(@NotNull final File tsv) {
    try {
      // files from Windows tools have CRLF line endings
      return Files.readAllLines(tsv.toPath(), StandardCharsets.UTF_8).stream()
          .map(String::stripTrailing).filter(line -> !line.isEmpty())
          .map(line -> line.split("\t", -1)).toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
