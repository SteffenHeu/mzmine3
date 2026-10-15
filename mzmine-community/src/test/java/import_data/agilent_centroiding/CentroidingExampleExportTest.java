package import_data.agilent_centroiding;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.impl.SimpleFrame;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max.AdaptiveLocalMaxMassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max.LocalMaxMassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max.LocalMaxNoSmoothingModule;
import io.github.mzmine.modules.io.import_rawdata_agilent_d.AgilentDataAccess;
import io.github.mzmine.project.impl.IMSRawDataFileImpl;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
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
 * Diagnostic export (not an accuracy test) of example peaks for the documentation: the raw profile
 * around the target and the centroids of the compared methods (Agilent frame centroiding, local
 * max, adaptive local max with EMG saturation branch) plus the m/z combined from the unsaturated
 * mobility scans of the frame. Examples are read from {@code plot_examples.csv} (dataset, raw_file,
 * target, level) in the results folder, the output is written to {@code plot_example_*.csv}.
 */
@Disabled("Agilent centroiding benchmark, run manually")
@TestInstance(Lifecycle.PER_CLASS)
@DisabledOnOs({OS.MAC, OS.LINUX})
public class CentroidingExampleExportTest {

  /**
   * Exported range around the exact m/z.
   */
  private static final double RANGE_PPM = 150;

  private record Example(int id, @NotNull String dataset, @NotNull String rawFile,
                         @NotNull String target, @NotNull SpectrumLevel level) {

  }

  @BeforeAll
  void init() {
    MZmineTestUtil.startMzmineCore();
  }

  @Test
  void exportExamples() throws IOException, CsvException {
    final File resultsDir = new File(AgilentCentroidingTestConfig.rootDir(),
        AgilentCentroidingTestConfig.RESULTS_DIR_NAME);
    final File examplesFile = new File(resultsDir, "plot_examples.csv");
    Assumptions.assumeTrue(examplesFile.exists(), "no examples defined: " + examplesFile);

    final List<String[]> rows;
    try (final CSVReader reader = new CSVReader(
        new FileReader(examplesFile, StandardCharsets.UTF_8))) {
      rows = reader.readAll();
    }
    // group by raw file, each file is opened once
    final Map<String, List<Example>> byFile = new LinkedHashMap<>();
    for (int i = 1; i < rows.size(); i++) {
      final String[] r = rows.get(i);
      final Example example = new Example(i, r[0], r[1], r[2], SpectrumLevel.valueOf(r[3]));
      byFile.computeIfAbsent(r[0] + "/" + r[1], k -> new ArrayList<>()).add(example);
    }

    int exported = 0;
    try (final CSVWriter summary = writer(resultsDir, "plot_example_summary.csv");
        final CSVWriter points = writer(resultsDir, "plot_example_points.csv");
        final CSVWriter centroids = writer(resultsDir, "plot_example_centroids.csv")) {
      summary.writeNext(new String[]{"example", "dataset", "raw_file", "target", "level",
          "frame_id", "scan_index", "exact_mz", "saturation_threshold", "potentially_saturated",
          "asymmetry", "mobility_edge_mz", "mobility_edge_scans"}, false);
      points.writeNext(new String[]{"example", "mz", "intensity"}, false);
      centroids.writeNext(new String[]{"example", "method", "mz", "intensity"}, false);
      for (final List<Example> examples : byFile.values()) {
        exported += exportFile(examples, summary, points, centroids);
      }
    }
    Assertions.assertTrue(exported > 0);
  }

  private static int exportFile(@NotNull final List<Example> examples,
      @NotNull final CSVWriter summary, @NotNull final CSVWriter points,
      @NotNull final CSVWriter centroids) throws IOException {
    final Example first = examples.getFirst();
    final CentroidingDataset dataset = CentroidingDataset.valueOf(first.dataset());
    final List<TargetIndexEntry> fileEntries = TargetIndexCsv.read(dataset.indexFile()).stream()
        .filter(e -> e.rawFile().equals(first.rawFile())).toList();
    final File rawFile = dataset.resolve(first.rawFile());

    // all indexed frames for the file maximum (saturation threshold), like the benchmark
    final Map<Integer, SimpleFrame> profileFrames = new TreeMap<>();
    final Map<Integer, SimpleFrame> vendorFrames = new TreeMap<>();
    final TreeSet<Integer> exampleFrames = new TreeSet<>();
    for (final Example example : examples) {
      fileEntries.stream().filter(e -> e.targetName().equals(example.target())).findFirst()
          .ifPresent(e -> exampleFrames.add(e.frameId()));
    }
    try (final AgilentDataAccess profile = AgilentCentroidingTestConfig.openProfile(rawFile);
        final AgilentDataAccess vendor = AgilentCentroidingTestConfig.openVendorCentroidedFrames(
            rawFile)) {
      final IMSRawDataFileImpl profileFile = (IMSRawDataFileImpl) profile.createDataFile();
      final IMSRawDataFileImpl vendorFile = (IMSRawDataFileImpl) vendor.createDataFile();
      for (final int frameId : new TreeSet<>(
          fileEntries.stream().map(TargetIndexEntry::frameId).toList())) {
        final SimpleFrame frame = profile.readFrame(profileFile, frameId);
        if (frame != null) {
          profileFrames.put(frameId, frame);
        }
        if (exampleFrames.contains(frameId)) {
          vendorFrames.put(frameId, vendor.readFrame(vendorFile, frameId));
        }
      }
    }
    final FileIntensityMaxima maxima = FileIntensityMaxima.of(profileFrames.values());

    int exported = 0;
    for (final Example example : examples) {
      final TargetIndexEntry entry = fileEntries.stream()
          .filter(e -> e.targetName().equals(example.target())).findFirst().orElse(null);
      if (entry == null || !profileFrames.containsKey(entry.frameId())) {
        continue;
      }
      final SimpleFrame frame = profileFrames.get(entry.frameId());
      final MassSpectrum spectrum = example.level() == SpectrumLevel.FRAME ? frame
          : frame.getMobilityScan(entry.mobilityScanIndex());
      final double threshold =
          MassDetectorConfig.EMG_SATURATION_FRACTION * maxima.max(example.level());
      final double low = entry.exactMz() - AgilentCentroidingTestConfig.ppmToDa(entry.exactMz(),
          RANGE_PPM);
      final double high = entry.exactMz() + AgilentCentroidingTestConfig.ppmToDa(entry.exactMz(),
          RANGE_PPM);
      final String id = String.valueOf(example.id());

      for (int i = 0; i < spectrum.getNumberOfDataPoints(); i++) {
        final double mz = spectrum.getMzValue(i);
        if (mz >= low && mz <= high) {
          points.writeNext(new String[]{id, num(mz), num(spectrum.getIntensityValue(i))}, false);
        }
      }

      final Map<String, double[][]> methods = new LinkedHashMap<>();
      methods.put("local_max", new LocalMaxMassDetector(0, AbundanceMeasure.Height, 3,
          new LocalMaxNoSmoothingModule()).getMassValues(spectrum));
      methods.put("adaptive_local_max_emg",
          new AdaptiveLocalMaxMassDetector(0, AbundanceMeasure.Height, 3,
              new LocalMaxNoSmoothingModule(), threshold).getMassValues(spectrum));
      if (example.level() == SpectrumLevel.FRAME) {
        methods.put("agilent", toArrays(vendorFrames.get(entry.frameId())));
      }
      methods.forEach((method, values) -> {
        for (int i = 0; i < values[0].length; i++) {
          if (values[0][i] >= low && values[0][i] <= high) {
            centroids.writeNext(
                new String[]{id, method, num(values[0][i]), num(values[1][i])}, false);
          }
        }
      });

      // m/z combined from the unsaturated mobility scans of this frame
      final MassDetector scanDetector = new AdaptiveLocalMaxMassDetector(0,
          AbundanceMeasure.Height, 3, new LocalMaxNoSmoothingModule());
      final List<double[][]> scanCentroids = new ArrayList<>();
      for (int i = 0; i < frame.getNumberOfMobilityScans(); i++) {
        scanCentroids.add(scanDetector.getMassValues(frame.getMobilityScan(i)));
      }
      final MobilogramCentroider.Result edge = MobilogramCentroider.combine(scanCentroids,
          entry.exactMz(),
          MassDetectorConfig.EMG_SATURATION_FRACTION * maxima.mobilityScanMax());

      final PeakAsymmetry shape = PeakAsymmetry.measure(spectrum, entry.exactMz());
      summary.writeNext(new String[]{id, example.dataset(), example.rawFile(), example.target(),
          example.level().name(), String.valueOf(entry.frameId()),
          String.valueOf(example.level() == SpectrumLevel.FRAME ? -1 : entry.mobilityScanIndex()),
          num(entry.exactMz()), num(threshold), String.valueOf(entry.potentiallySaturated()),
          shape == null ? "" : num(shape.asymmetry()), num(edge.mz()),
          String.valueOf(edge.scansUsed())}, false);
      exported++;
    }
    return exported;
  }

  private static double @NotNull [] @NotNull [] toArrays(@NotNull final MassSpectrum spectrum) {
    final int n = spectrum.getNumberOfDataPoints();
    final double[][] arrays = new double[2][n];
    for (int i = 0; i < n; i++) {
      arrays[0][i] = spectrum.getMzValue(i);
      arrays[1][i] = spectrum.getIntensityValue(i);
    }
    return arrays;
  }

  private static @NotNull CSVWriter writer(@NotNull final File dir, @NotNull final String name)
      throws IOException {
    return new CSVWriter(new FileWriter(new File(dir, name), StandardCharsets.UTF_8));
  }

  private static @NotNull String num(final double value) {
    return Double.isNaN(value) ? "" : String.format(Locale.ROOT, "%.6f", value);
  }
}
