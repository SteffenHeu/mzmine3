package import_data.agilent_centroiding;

import com.opencsv.CSVWriter;
import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.MobilityScan;
import io.github.mzmine.datamodel.impl.SimpleFrame;
import io.github.mzmine.modules.io.import_rawdata_agilent_d.AgilentDataAccess;
import io.github.mzmine.project.impl.IMSRawDataFileImpl;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import testutils.MZmineTestUtil;

/**
 * Diagnostic data collection (not an accuracy test): imports the indexed frames in profile mode and
 * records, for every target, the raw apex heights of M and M+1 in the total frame spectrum and in
 * every mobility scan of the frame, plus the flatness of the M apex. Used to find the intensity at
 * which the detector saturates, per dataset and per dimension.
 * <p>
 * Writes {@code saturation_profile_scans.csv} and {@code saturation_file_maxima.csv} to the results
 * folder.
 */
@Disabled("Agilent centroiding benchmark, run manually")
@TestInstance(Lifecycle.PER_CLASS)
@DisabledOnOs({OS.MAC, OS.LINUX})
public class SaturationProfileScanTest {

  private static final Logger logger = Logger.getLogger(SaturationProfileScanTest.class.getName());

  /**
   * Window around the exact m/z for the raw apex of a profile peak. Profile peaks are wider than
   * centroid errors, the apex itself is within a few ppm.
   */
  private static final double APEX_WINDOW_PPM = 30;
  /**
   * Points at or above this fraction of the apex count as part of a flat (clipped) top.
   */
  private static final double FLAT_FRACTION = 0.98;

  @BeforeAll
  void init() {
    MZmineTestUtil.startMzmineCore();
  }

  @Test
  void collectProfileApexIntensities() throws IOException {
    final List<TargetIndexEntry> entries = new ArrayList<>();
    for (final CentroidingDataset dataset : CentroidingDataset.values()) {
      if (dataset.indexFile().exists()) {
        entries.addAll(TargetIndexCsv.read(dataset.indexFile()));
      }
    }
    Assumptions.assumeFalse(entries.isEmpty(), "no target index found");

    final File resultsDir = new File(AgilentCentroidingTestConfig.rootDir(),
        AgilentCentroidingTestConfig.RESULTS_DIR_NAME);
    resultsDir.mkdirs();

    final Map<String, List<TargetIndexEntry>> byFile = entries.stream().collect(
        Collectors.groupingBy(e -> e.dataset() + "/" + e.rawFile(), LinkedHashMap::new,
            Collectors.toList()));

    int rows = 0;
    try (final CSVWriter scans = new CSVWriter(
        new FileWriter(new File(resultsDir, "saturation_profile_scans.csv"),
            StandardCharsets.UTF_8)); final CSVWriter maxima = new CSVWriter(
        new FileWriter(new File(resultsDir, "saturation_file_maxima.csv"),
            StandardCharsets.UTF_8))) {
      scans.writeNext(new String[]{"dataset", "raw_file", "target", "exact_mz", "expected_ratio",
          "index_saturated", "level", "frame_id", "scan_index", "is_index_scan", "m_apex",
          "m1_apex", "ratio_deviation", "flat_points", "peak_points"}, false);
      maxima.writeNext(new String[]{"dataset", "raw_file", "level", "max_intensity",
          "points_at_max", "points_above_99pct", "frames"}, false);

      for (final List<TargetIndexEntry> fileEntries : byFile.values()) {
        rows += collectFile(fileEntries, scans, maxima);
      }
    }
    logger.info("Wrote %d profile apex rows".formatted(rows));
    Assertions.assertTrue(rows > 0);
  }

  private static int collectFile(@NotNull final List<TargetIndexEntry> entries,
      @NotNull final CSVWriter scans, @NotNull final CSVWriter maxima) {
    final TargetIndexEntry first = entries.getFirst();
    final CentroidingDataset dataset = CentroidingDataset.valueOf(first.dataset());
    final File rawFile = dataset.resolve(first.rawFile());
    final Map<Integer, List<TargetIndexEntry>> byFrame = entries.stream().collect(
        Collectors.groupingBy(TargetIndexEntry::frameId, TreeMap::new, Collectors.toList()));

    final MaxTracker frameMax = new MaxTracker();
    final MaxTracker scanMax = new MaxTracker();
    int rows = 0;
    try (final AgilentDataAccess access = AgilentCentroidingTestConfig.openProfile(rawFile)) {
      final IMSRawDataFileImpl file = (IMSRawDataFileImpl) access.createDataFile();
      for (final Map.Entry<Integer, List<TargetIndexEntry>> e : byFrame.entrySet()) {
        final SimpleFrame frame = access.readFrame(file, e.getKey());
        if (frame == null) {
          continue;
        }
        frameMax.add(frame);
        for (int i = 0; i < frame.getNumberOfMobilityScans(); i++) {
          scanMax.add(frame.getMobilityScan(i));
        }

        for (final TargetIndexEntry entry : e.getValue()) {
          scans.writeNext(row(entry, SpectrumLevel.FRAME, -1, false, frame), false);
          rows++;
          for (int i = 0; i < frame.getNumberOfMobilityScans(); i++) {
            final MobilityScan scan = frame.getMobilityScan(i);
            if (apex(scan, entry.exactMz()).intensity() <= 0) {
              continue;
            }
            scans.writeNext(
                row(entry, SpectrumLevel.MOBILITY_SCAN, i, i == entry.mobilityScanIndex(), scan),
                false);
            rows++;
          }
        }
      }
    }
    maxima.writeNext(frameMax.row(dataset, first.rawFile(), SpectrumLevel.FRAME, byFrame.size()),
        false);
    maxima.writeNext(
        scanMax.row(dataset, first.rawFile(), SpectrumLevel.MOBILITY_SCAN, byFrame.size()), false);
    return rows;
  }

  private static String @NotNull [] row(@NotNull final TargetIndexEntry entry,
      @NotNull final SpectrumLevel level, final int scanIndex, final boolean isIndexScan,
      @NotNull final MassSpectrum spectrum) {
    final double m1Mz = entry.exactMz() + CentroidingTarget.M1_DISTANCE;
    final Apex m = apex(spectrum, entry.exactMz());
    final Apex m1 = apex(spectrum, m1Mz);
    final double deviation =
        m.intensity() > 0 && m1.intensity() > 0 ? m1.intensity() / m.intensity()
            / entry.expectedRatio() - 1 : Double.NaN;
    return new String[]{entry.dataset(), entry.rawFile(), entry.targetName(),
        num(entry.exactMz()), num(entry.expectedRatio()),
        String.valueOf(entry.potentiallySaturated()), level.name(),
        String.valueOf(entry.frameId()), String.valueOf(scanIndex), String.valueOf(isIndexScan),
        num(m.intensity()), num(m1.intensity()), num(deviation), String.valueOf(m.flatPoints()),
        String.valueOf(m.peakPoints())};
  }

  /**
   * @param intensity   highest raw intensity within the apex window
   * @param flatPoints  consecutive points around the apex with at least
   *                    {@link #FLAT_FRACTION} of the apex intensity
   * @param peakPoints  non-zero points of the contiguous peak around the apex
   */
  private record Apex(double intensity, int flatPoints, int peakPoints) {

  }

  private static @NotNull Apex apex(@NotNull final MassSpectrum spectrum, final double mz) {
    final double tol = AgilentCentroidingTestConfig.ppmToDa(mz, APEX_WINDOW_PPM);
    final int n = spectrum.getNumberOfDataPoints();
    int apex = -1;
    for (int i = 0; i < n; i++) {
      final double v = spectrum.getMzValue(i);
      if (v < mz - tol) {
        continue;
      }
      if (v > mz + tol) {
        break;
      }
      if (apex < 0 || spectrum.getIntensityValue(i) > spectrum.getIntensityValue(apex)) {
        apex = i;
      }
    }
    if (apex < 0) {
      return new Apex(0, 0, 0);
    }
    final double max = spectrum.getIntensityValue(apex);
    int flat = 1;
    for (int i = apex - 1; i >= 0 && spectrum.getIntensityValue(i) >= FLAT_FRACTION * max; i--) {
      flat++;
    }
    for (int i = apex + 1; i < n && spectrum.getIntensityValue(i) >= FLAT_FRACTION * max; i++) {
      flat++;
    }
    int points = 1;
    for (int i = apex - 1; i >= 0 && spectrum.getIntensityValue(i) > 0; i--) {
      points++;
    }
    for (int i = apex + 1; i < n && spectrum.getIntensityValue(i) > 0; i++) {
      points++;
    }
    return new Apex(max, flat, points);
  }

  /**
   * Tracks the highest raw intensity of all spectra and how often it occurs (a hard cap such as the
   * ADC limit shows up as many points with exactly the same maximum).
   */
  private static final class MaxTracker {

    private final DoubleArrayList values = new DoubleArrayList();
    private double max = 0;

    void add(@NotNull final MassSpectrum spectrum) {
      for (int i = 0; i < spectrum.getNumberOfDataPoints(); i++) {
        final double v = spectrum.getIntensityValue(i);
        if (v > 0) {
          values.add(v);
          max = Math.max(max, v);
        }
      }
    }

    String @NotNull [] row(@NotNull final CentroidingDataset dataset, @NotNull final String rawFile,
        @NotNull final SpectrumLevel level, final int frames) {
      final long atMax = values.doubleStream().filter(v -> v == max).count();
      final long above99 = values.doubleStream().filter(v -> v >= 0.99 * max).count();
      return new String[]{dataset.name(), rawFile, level.name(), num(max), String.valueOf(atMax),
          String.valueOf(above99), String.valueOf(frames)};
    }
  }

  private static @NotNull String num(final double value) {
    return Double.isNaN(value) ? "" : String.format(Locale.ROOT, "%.6f", value);
  }
}
