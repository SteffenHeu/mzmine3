package import_data.agilent_centroiding;

import com.opencsv.CSVWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * Summary statistics of the centroiding benchmark: accuracy per detector and spectrum level, split
 * by the saturation label of the targets.
 */
public final class CentroidingEvaluation {

  private static final String[] SUMMARY_HEADER = {"group", "detector", "level", "peak_class", "n",
      "found", "mean_ppm", "mean_abs_ppm", "median_abs_ppm", "rms_ppm", "p95_abs_ppm",
      "max_abs_ppm", "mean_abs_da", "split_fraction"};

  private CentroidingEvaluation() {
  }

  /**
   * @param group     name of the subset, e.g. "all" or the dataset
   * @param peakClass a {@link PeakClass} name or "all"
   */
  public record SummaryRow(@NotNull String group, @NotNull String detector,
                           @NotNull SpectrumLevel level, @NotNull String peakClass, int n,
                           int found, double meanPpm, double meanAbsPpm, double medianAbsPpm,
                           double rmsPpm, double p95AbsPpm, double maxAbsPpm, double meanAbsDa,
                           double splitFraction) {

  }

  public static @NotNull List<SummaryRow> summarize(
      @NotNull final List<CentroidMeasurement> measurements) {
    final List<SummaryRow> rows = new ArrayList<>();
    rows.addAll(summarizeGroup("all", measurements));
    final Map<String, List<CentroidMeasurement>> byDataset = measurements.stream()
        .collect(Collectors.groupingBy(CentroidMeasurement::dataset, LinkedHashMap::new,
            Collectors.toList()));
    byDataset.forEach((dataset, list) -> rows.addAll(summarizeGroup(dataset, list)));
    return rows;
  }

  private static @NotNull List<SummaryRow> summarizeGroup(@NotNull final String group,
      @NotNull final List<CentroidMeasurement> measurements) {
    final List<SummaryRow> rows = new ArrayList<>();
    final Map<String, Map<SpectrumLevel, List<CentroidMeasurement>>> byDetector = measurements.stream()
        .collect(Collectors.groupingBy(CentroidMeasurement::detector, LinkedHashMap::new,
            Collectors.groupingBy(CentroidMeasurement::level, LinkedHashMap::new,
                Collectors.toList())));
    byDetector.forEach((detector, byLevel) -> byLevel.forEach((level, list) -> {
      rows.add(stats(group, detector, level, "all", list));
      for (final PeakClass peakClass : PeakClass.values()) {
        rows.add(stats(group, detector, level, peakClass.name(),
            list.stream().filter(m -> m.peakClass() == peakClass).toList()));
      }
    }));
    return rows;
  }

  private static @NotNull SummaryRow stats(@NotNull final String group,
      @NotNull final String detector, @NotNull final SpectrumLevel level,
      @NotNull final String peakClass, @NotNull final List<CentroidMeasurement> list) {
    final List<CentroidMeasurement> found = list.stream().filter(CentroidMeasurement::found)
        .toList();
    final double[] ppm = found.stream().mapToDouble(CentroidMeasurement::errorPpm).toArray();
    final double[] absPpm = Arrays.stream(ppm).map(Math::abs).sorted().toArray();
    final double meanAbsDa = found.stream().mapToDouble(m -> Math.abs(m.errorDa())).average()
        .orElse(Double.NaN);
    // for the mobilogram, centroidsInWindow holds the number of combined scans, not split peaks
    final double split = level == SpectrumLevel.MOBILOGRAM ? Double.NaN
        : found.stream().filter(m -> m.centroidsInWindow() > 1).count() / (double) Math.max(1,
            found.size());
    return new SummaryRow(group, detector, level, peakClass, list.size(), found.size(),
        Arrays.stream(ppm).average().orElse(Double.NaN),
        Arrays.stream(absPpm).average().orElse(Double.NaN), percentile(absPpm, 0.5),
        Math.sqrt(Arrays.stream(ppm).map(v -> v * v).average().orElse(Double.NaN)),
        percentile(absPpm, 0.95), absPpm.length == 0 ? Double.NaN : absPpm[absPpm.length - 1],
        meanAbsDa, split);
  }

  private static double percentile(final double @NotNull [] sorted, final double p) {
    if (sorted.length == 0) {
      return Double.NaN;
    }
    final int index = (int) Math.min(sorted.length - 1, Math.round(p * (sorted.length - 1)));
    return sorted[index];
  }

  public static void writeSummary(@NotNull final File file, @NotNull final List<SummaryRow> rows)
      throws IOException {
    try (final CSVWriter writer = new CSVWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
      writer.writeNext(SUMMARY_HEADER, false);
      for (final SummaryRow r : rows) {
        writer.writeNext(new String[]{r.group(), r.detector(), r.level().name(), r.peakClass(),
            String.valueOf(r.n()), String.valueOf(r.found()), num(r.meanPpm()),
            num(r.meanAbsPpm()), num(r.medianAbsPpm()), num(r.rmsPpm()), num(r.p95AbsPpm()),
            num(r.maxAbsPpm()), num(r.meanAbsDa()), num(r.splitFraction())}, false);
      }
    }
  }

  public static void writeMeasurements(@NotNull final File file,
      @NotNull final List<CentroidMeasurement> measurements) throws IOException {
    try (final CSVWriter writer = new CSVWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
      writer.writeNext(new String[]{"dataset", "raw_file", "target", "exact_mz", "level",
          "detector", "found", "measured_mz", "intensity", "error_da", "error_ppm",
          "centroids_in_window", "potentially_saturated", "vendor_saturation_flag", "asymmetry",
          "peak_class"}, false);
      for (final CentroidMeasurement m : measurements) {
        writer.writeNext(new String[]{m.dataset(), m.rawFile(), m.target(), num(m.exactMz()),
            m.level().name(), m.detector(), String.valueOf(m.found()), num(m.measuredMz()),
            num(m.intensity()), num(m.errorDa()), num(m.errorPpm()),
            String.valueOf(m.centroidsInWindow()), String.valueOf(m.potentiallySaturated()),
            m.vendorSaturationFlag() == null ? "" : m.vendorSaturationFlag().toString(),
            num(m.asymmetry()), m.peakClass().name()}, false);
      }
    }
  }

  /**
   * Text table of the overall results (group "all"), sorted by level, saturation and median error.
   */
  public static @NotNull String formatTable(@NotNull final List<SummaryRow> rows) {
    final StringBuilder b = new StringBuilder();
    b.append("%-28s %-14s %-10s %6s %6s %9s %9s %9s %9s %9s %7s%n".formatted("detector", "level",
        "class", "n", "found", "mean_ppm", "med|ppm|", "mean|ppm|", "p95|ppm|", "rms_ppm",
        "split"));
    final Function<SummaryRow, String> key = r -> r.level() + r.peakClass();
    rows.stream().filter(r -> r.group().equals("all")).sorted(
        Comparator.comparing(key).thenComparingDouble(SummaryRow::medianAbsPpm)).forEach(
        r -> b.append(String.format(Locale.ROOT,
            "%-28s %-14s %-10s %6d %6d %9.2f %9.2f %9.2f %9.2f %9.2f %7.3f%n", r.detector(),
            r.level(), r.peakClass(), r.n(), r.found(), r.meanPpm(), r.medianAbsPpm(),
            r.meanAbsPpm(), r.p95AbsPpm(), r.rmsPpm(), r.splitFraction())));
    return b.toString();
  }

  private static @NotNull String num(final double value) {
    return Double.isNaN(value) ? "" : String.format(Locale.ROOT, "%.6f", value);
  }
}
