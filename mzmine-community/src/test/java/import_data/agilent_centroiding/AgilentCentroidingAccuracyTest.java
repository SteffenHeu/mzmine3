package import_data.agilent_centroiding;

import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.MobilityScan;
import io.github.mzmine.datamodel.impl.SimpleFrame;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetector;
import io.github.mzmine.modules.io.import_rawdata_agilent_d.AgilentDataAccess;
import io.github.mzmine.project.impl.IMSRawDataFileImpl;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
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
 * Benchmark of centroiding methods on Agilent 6560 IMS data. For every target in the target
 * indices (see {@link AgilentTargetIndexGeneratorTest}) only the indexed frame is imported in
 * profile mode, then every {@link MassDetectorConfig mass detector} centroids the total frame
 * spectrum and the most intense mobility scan of the target. The Agilent centroiding of the total
 * frame spectrum is added as reference. Errors are calculated against the exact m/z of the target
 * and summarized split by the saturation label.
 * <p>
 * Results are written to {@code <root>/centroiding_results/} and the overall table is logged.
 */
@Disabled("Agilent centroiding benchmark, run manually")
@TestInstance(Lifecycle.PER_CLASS)
@DisabledOnOs({OS.MAC, OS.LINUX})
public class AgilentCentroidingAccuracyTest {

  private static final Logger logger = Logger.getLogger(
      AgilentCentroidingAccuracyTest.class.getName());
  public static final String AGILENT_CENTROIDING = "agilent_frame_centroid";

  @BeforeAll
  void init() {
    MZmineTestUtil.startMzmineCore();
  }

  @Test
  void evaluateCentroiding() throws IOException {
    final List<TargetIndexEntry> entries = loadIndices();
    Assumptions.assumeFalse(entries.isEmpty(), "no target index found, run the generator first");

    final List<MassDetectorConfig> configs = MassDetectorConfig.defaults();
    final List<CentroidMeasurement> measurements = new ArrayList<>();

    final Map<String, List<TargetIndexEntry>> byFile = entries.stream().collect(
        Collectors.groupingBy(e -> e.dataset() + "/" + e.rawFile(), LinkedHashMap::new,
            Collectors.toList()));
    for (final List<TargetIndexEntry> fileEntries : byFile.values()) {
      measurements.addAll(evaluateFile(fileEntries, configs));
    }

    final List<CentroidingEvaluation.SummaryRow> summary = CentroidingEvaluation.summarize(
        measurements);
    final File resultsDir = new File(AgilentCentroidingTestConfig.rootDir(),
        AgilentCentroidingTestConfig.RESULTS_DIR_NAME);
    resultsDir.mkdirs();
    final String timestamp = LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    CentroidingEvaluation.writeMeasurements(
        new File(resultsDir, timestamp + "_measurements.csv"), measurements);
    CentroidingEvaluation.writeSummary(new File(resultsDir, timestamp + "_summary.csv"), summary);
    logger.info("Centroiding accuracy (all datasets):\n" + CentroidingEvaluation.formatTable(
        summary));

    Assertions.assertFalse(measurements.isEmpty());
  }

  private static @NotNull List<TargetIndexEntry> loadIndices() throws IOException {
    final List<TargetIndexEntry> entries = new ArrayList<>();
    for (final CentroidingDataset dataset : CentroidingDataset.values()) {
      if (dataset.indexFile().exists()) {
        entries.addAll(TargetIndexCsv.read(dataset.indexFile()));
      }
    }
    return entries;
  }

  /**
   * Imports only the indexed frames of one raw file, in profile mode and with Agilent frame
   * centroiding, and measures all targets of this file.
   */
  private static @NotNull List<CentroidMeasurement> evaluateFile(
      @NotNull final List<TargetIndexEntry> entries,
      @NotNull final List<MassDetectorConfig> configs) {
    final TargetIndexEntry first = entries.getFirst();
    final CentroidingDataset dataset = CentroidingDataset.valueOf(first.dataset());
    final File rawFile = dataset.resolve(first.rawFile());
    // frames in ascending order, the reader is sequential
    final Map<Integer, List<TargetIndexEntry>> byFrame = entries.stream().collect(
        Collectors.groupingBy(TargetIndexEntry::frameId, TreeMap::new, Collectors.toList()));

    final List<CentroidMeasurement> measurements = new ArrayList<>();
    final long start = System.currentTimeMillis();
    // decision: load all indexed frames first, the saturation thresholds are derived from the
    // highest intensity of the file
    final Map<Integer, SimpleFrame> profileFrames = new TreeMap<>();
    final Map<Integer, SimpleFrame> vendorFrames = new TreeMap<>();
    try (final AgilentDataAccess profileAccess = AgilentCentroidingTestConfig.openProfile(rawFile);
        final AgilentDataAccess vendorAccess = AgilentCentroidingTestConfig.openVendorCentroidedFrames(
            rawFile)) {
      final IMSRawDataFileImpl profileFile = (IMSRawDataFileImpl) profileAccess.createDataFile();
      final IMSRawDataFileImpl vendorFile = (IMSRawDataFileImpl) vendorAccess.createDataFile();
      for (final int frameId : byFrame.keySet()) {
        final SimpleFrame profileFrame = profileAccess.readFrame(profileFile, frameId);
        final SimpleFrame vendorFrame = vendorAccess.readFrame(vendorFile, frameId);
        if (profileFrame == null || vendorFrame == null) {
          logger.warning("Frame %d missing in %s".formatted(frameId, rawFile));
          continue;
        }
        profileFrames.put(frameId, profileFrame);
        vendorFrames.put(frameId, vendorFrame);
      }
    }

    final FileIntensityMaxima maxima = FileIntensityMaxima.of(profileFrames.values());
    for (final Map.Entry<Integer, SimpleFrame> e : profileFrames.entrySet()) {
      final List<TargetIndexEntry> frameEntries = byFrame.get(e.getKey());
      measurements.addAll(
          evaluateFrame(frameEntries, e.getValue(), vendorFrames.get(e.getKey()), configs,
              maxima));
      measurements.addAll(evaluateMobilogram(frameEntries, e.getValue(), maxima));
    }
    logger.info(("Evaluated %d targets in %d frames of %s (%d s), max intensity frame %.0f, "
        + "mobility scan %.0f").formatted(entries.size(), byFrame.size(), rawFile.getName(),
        (System.currentTimeMillis() - start) / 1000, maxima.frameMax(),
        maxima.mobilityScanMax()));
    return measurements;
  }

  private static @NotNull List<CentroidMeasurement> evaluateFrame(
      @NotNull final List<TargetIndexEntry> entries, @NotNull final SimpleFrame profileFrame,
      @NotNull final SimpleFrame vendorFrame, @NotNull final List<MassDetectorConfig> configs,
      @NotNull final FileIntensityMaxima maxima) {
    final List<CentroidMeasurement> measurements = new ArrayList<>();

    // raw peak shape of each target in the frame and in its mobility scan, for the peak classes
    final Map<TargetIndexEntry, PeakAsymmetry> frameShapes = new HashMap<>();
    final Map<TargetIndexEntry, PeakAsymmetry> scanShapes = new HashMap<>();
    for (final TargetIndexEntry entry : entries) {
      frameShapes.put(entry, PeakAsymmetry.measure(profileFrame, entry.exactMz()));
      final MobilityScan scan = entry.mobilityScanIndex() >= 0 ? profileFrame.getMobilityScan(
          entry.mobilityScanIndex()) : null;
      scanShapes.put(entry, scan == null ? null : PeakAsymmetry.measure(scan, entry.exactMz()));
    }

    final double[][] vendorCentroids = toArrays(vendorFrame);
    for (final TargetIndexEntry entry : entries) {
      measurements.add(CentroidMeasurement.match(entry, SpectrumLevel.FRAME, AGILENT_CENTROIDING,
          vendorCentroids, frameShapes.get(entry)));
    }

    for (final MassDetectorConfig config : configs) {
      // several targets share the frame or even the mobility scan
      final double[][] frameCentroids = config.create(SpectrumLevel.FRAME, maxima)
          .getMassValues(profileFrame);
      final MassDetector scanDetector = config.create(SpectrumLevel.MOBILITY_SCAN, maxima);
      final Map<Integer, double[][]> scanCentroids = new HashMap<>();
      for (final TargetIndexEntry entry : entries) {
        measurements.add(
            CentroidMeasurement.match(entry, SpectrumLevel.FRAME, config.name(), frameCentroids,
                frameShapes.get(entry)));

        final int scanIndex = entry.mobilityScanIndex();
        final MobilityScan scan = scanIndex >= 0 ? profileFrame.getMobilityScan(scanIndex) : null;
        if (scan == null) {
          continue;
        }
        final double[][] centroids = scanCentroids.computeIfAbsent(scanIndex,
            i -> scanDetector.getMassValues(scan));
        measurements.add(
            CentroidMeasurement.match(entry, SpectrumLevel.MOBILITY_SCAN, config.name(),
                centroids, scanShapes.get(entry)));
      }
    }
    return measurements;
  }

  /**
   * Approach 4: combine the target centroids of all mobility scans, once with all scans and once
   * only with the scans below the saturation threshold.
   */
  private static @NotNull List<CentroidMeasurement> evaluateMobilogram(
      @NotNull final List<TargetIndexEntry> entries, @NotNull final SimpleFrame profileFrame,
      @NotNull final FileIntensityMaxima maxima) {
    final List<CentroidMeasurement> measurements = new ArrayList<>();
    for (final MassDetectorConfig base : MassDetectorConfig.mobilogramBaseDetectors()) {
      final MassDetector detector = base.create(SpectrumLevel.MOBILITY_SCAN, maxima);
      final List<double[][]> scanCentroids = new ArrayList<>(
          profileFrame.getNumberOfMobilityScans());
      for (int i = 0; i < profileFrame.getNumberOfMobilityScans(); i++) {
        scanCentroids.add(detector.getMassValues(profileFrame.getMobilityScan(i)));
      }

      for (final TargetIndexEntry entry : entries) {
        // the mobilogram is classified by the most intense mobility scan of the target
        final MobilityScan indexScan = entry.mobilityScanIndex() >= 0
            ? profileFrame.getMobilityScan(entry.mobilityScanIndex()) : null;
        final PeakAsymmetry shape =
            indexScan == null ? null : PeakAsymmetry.measure(indexScan, entry.exactMz());
        final MobilogramCentroider.Result all = MobilogramCentroider.combine(scanCentroids,
            entry.exactMz(), Double.POSITIVE_INFINITY);
        measurements.add(CentroidMeasurement.of(entry, SpectrumLevel.MOBILOGRAM,
            "mobilogram_all_" + base.name(), all.mz(), all.intensity(), all.scansUsed(),
            shape));
        for (final double fraction : MassDetectorConfig.SATURATION_FRACTIONS) {
          final MobilogramCentroider.Result flank = MobilogramCentroider.combine(scanCentroids,
              entry.exactMz(), fraction * maxima.mobilityScanMax());
          measurements.add(CentroidMeasurement.of(entry, SpectrumLevel.MOBILOGRAM,
              String.format(Locale.ROOT, "mobilogram_flank_%s_f%.1f", base.name(), fraction),
              flank.mz(), flank.intensity(), flank.scansUsed(), shape));
        }
      }
    }
    return measurements;
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
}
