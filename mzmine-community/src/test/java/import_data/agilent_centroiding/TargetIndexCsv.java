package import_data.agilent_centroiding;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads and writes the target index of a dataset.
 */
public final class TargetIndexCsv {

  private static final String[] HEADER = {"dataset", "raw_file", "target", "neutral_formula",
      "adduct", "ion_formula", "exact_mz", "expected_m1_ratio", "vendor_saturation_flag",
      "frame_id", "frame_rt", "frame_m_intensity", "frame_m1_intensity", "frame_ratio",
      "mobility_scan_index", "mobility", "mobility_m_intensity", "mobility_m1_intensity",
      "mobility_ratio", "frame_ratio_deviation", "mobility_ratio_deviation",
      "potentially_saturated"};

  private TargetIndexCsv() {
  }

  public static void write(@NotNull final File file, @NotNull final List<TargetIndexEntry> entries)
      throws IOException {
    try (final CSVWriter writer = new CSVWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
      writer.writeNext(HEADER, false);
      for (final TargetIndexEntry e : entries) {
        writer.writeNext(new String[]{e.dataset(), e.rawFile(), e.targetName(), e.neutralFormula(),
            e.adduct(), e.ionFormula(), num(e.exactMz()), num(e.expectedRatio()),
            e.vendorSaturationFlag() == null ? "" : e.vendorSaturationFlag().toString(),
            String.valueOf(e.frameId()), num(e.frameRt()), num(e.frameMIntensity()),
            num(e.frameM1Intensity()), num(e.frameRatio()), String.valueOf(e.mobilityScanIndex()),
            num(e.mobility()), num(e.mobilityMIntensity()), num(e.mobilityM1Intensity()),
            num(e.mobilityRatio()), num(e.frameRatioDeviation()), num(e.mobilityRatioDeviation()),
            String.valueOf(e.potentiallySaturated())}, false);
      }
    }
  }

  public static @NotNull List<TargetIndexEntry> read(@NotNull final File file) throws IOException {
    try (final CSVReader reader = new CSVReader(new FileReader(file, StandardCharsets.UTF_8))) {
      final List<String[]> rows = reader.readAll();
      final List<TargetIndexEntry> entries = new ArrayList<>(rows.size());
      for (final String[] r : rows.subList(1, rows.size())) {
        entries.add(new TargetIndexEntry(r[0], r[1], r[2], r[3], r[4], r[5], dbl(r[6]), dbl(r[7]),
            bool(r[8]), Integer.parseInt(r[9]), dbl(r[10]), dbl(r[11]), dbl(r[12]), dbl(r[13]),
            Integer.parseInt(r[14]), dbl(r[15]), dbl(r[16]), dbl(r[17]), dbl(r[18]), dbl(r[19]),
            dbl(r[20]), Boolean.parseBoolean(r[21])));
      }
      return entries;
    } catch (CsvException e) {
      throw new IOException("Cannot parse " + file, e);
    }
  }

  private static @NotNull String num(final double value) {
    return Double.isNaN(value) ? "" : String.valueOf(value);
  }

  private static double dbl(@NotNull final String value) {
    return value.isBlank() ? Double.NaN : Double.parseDouble(value);
  }

  private static @Nullable Boolean bool(@NotNull final String value) {
    return value.isBlank() ? null : Boolean.parseBoolean(value);
  }
}
