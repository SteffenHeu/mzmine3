package import_data.agilent_centroiding;

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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.logging.Logger;
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
 * Diagnostic timing (not an accuracy test) of local max, adaptive local max and adaptive local max
 * with EMG saturation branch on real profile spectra: all indexed frames of a few raw files,
 * separately for the total frame spectra and the mobility scans. Single threaded, warm-up rounds first, the median of the
 * measured rounds is reported. The frames are loaded before timing, so file access is excluded.
 */
@Disabled("Agilent centroiding benchmark, run manually")
@TestInstance(Lifecycle.PER_CLASS)
@DisabledOnOs({OS.MAC, OS.LINUX})
public class MassDetectorSpeedTest {

  private static final Logger logger = Logger.getLogger(MassDetectorSpeedTest.class.getName());
  private static final int WARMUP_ROUNDS = 2;
  private static final int MEASURED_ROUNDS = 5;
  private static final double SATURATION_FRACTION = 0.5;

  /**
   * dataset, raw file relative to the dataset folder
   */
  private static final String[][] FILES = {
      {"MSV000085979", "SingleField_tunemix/I_Raw/Agilent_TuneMix_Pos_14Sep18.d"},
      {"MSV000087295", "042319_RP_1D_Al_All_0002.d"},
      {"MSV000088206", "M1_T46_357_2000_positive.d"}};

  /**
   * @param threshold saturation threshold of the EMG branch for this dimension
   */
  private record Workload(@NotNull String name, @NotNull List<? extends MassSpectrum> spectra,
                          double threshold) {

  }

  @BeforeAll
  void init() {
    MZmineTestUtil.startMzmineCore();
  }

  @Test
  void compareSpeed() throws IOException {
    final StringBuilder report = new StringBuilder();
    report.append(String.format(Locale.ROOT, "%-58s %-26s %9s %9s %10s %10s%n", "workload",
        "detector", "spectra", "total_ms", "us/spec", "x local"));
    int workloads = 0;
    for (final String[] file : FILES) {
      final CentroidingDataset dataset = CentroidingDataset.valueOf(file[0]);
      if (!dataset.indexFile().exists()) {
        continue;
      }
      final List<SimpleFrame> frames = loadIndexedFrames(dataset, file[1]);
      if (frames.isEmpty()) {
        continue;
      }
      final FileIntensityMaxima maxima = FileIntensityMaxima.of(frames);
      final List<MassSpectrum> scans = new ArrayList<>();
      for (final SimpleFrame frame : frames) {
        for (int i = 0; i < frame.getNumberOfMobilityScans(); i++) {
          scans.add(frame.getMobilityScan(i));
        }
      }
      final String fileName = new File(file[1]).getName();
      for (final Workload workload : List.of(
          new Workload(fileName + " frames", frames, SATURATION_FRACTION * maxima.frameMax()),
          new Workload(fileName + " mobility scans", scans,
              SATURATION_FRACTION * maxima.mobilityScanMax()))) {
        report.append(timeWorkload(workload));
        workloads++;
      }
    }
    Assumptions.assumeTrue(workloads > 0, "no benchmark data");
    logger.info("Mass detector speed:\n" + report);
    Assertions.assertTrue(workloads > 0);
  }

  private static @NotNull String timeWorkload(@NotNull final Workload workload) {
    final List<String[]> rows = new ArrayList<>();
    final List<DetectorFactory> factories = List.of(
        new DetectorFactory("local_max_none",
            () -> new LocalMaxMassDetector(0, AbundanceMeasure.Height, 3,
                new LocalMaxNoSmoothingModule())),
        new DetectorFactory("adaptive_local_max_none",
            () -> new AdaptiveLocalMaxMassDetector(0, AbundanceMeasure.Height, 3,
                new LocalMaxNoSmoothingModule())),
        new DetectorFactory("adaptive_local_max_emg",
            () -> new AdaptiveLocalMaxMassDetector(0, AbundanceMeasure.Height, 3,
                new LocalMaxNoSmoothingModule(), workload.threshold())));

    double localMaxMedian = Double.NaN;
    final StringBuilder out = new StringBuilder();
    for (final DetectorFactory factory : factories) {
      final double[] roundsMs = new double[MEASURED_ROUNDS];
      long sink = 0;
      for (int round = 0; round < WARMUP_ROUNDS + MEASURED_ROUNDS; round++) {
        // new instance per round, detectors keep state of the last spectrum
        final MassDetector detector = factory.supplier().get();
        final long start = System.nanoTime();
        for (final MassSpectrum spectrum : workload.spectra()) {
          sink += detector.getMassValues(spectrum)[0].length;
        }
        final long elapsed = System.nanoTime() - start;
        if (round >= WARMUP_ROUNDS) {
          roundsMs[round - WARMUP_ROUNDS] = elapsed / 1e6;
        }
      }
      Arrays.sort(roundsMs);
      final double medianMs = roundsMs[MEASURED_ROUNDS / 2];
      if (Double.isNaN(localMaxMedian)) {
        localMaxMedian = medianMs;
      }
      out.append(String.format(Locale.ROOT, "%-58s %-26s %9d %9.1f %10.2f %10.2f%n",
          workload.name(), factory.name(), workload.spectra().size(), medianMs,
          medianMs * 1000 / workload.spectra().size(), medianMs / localMaxMedian));
      // keep the JIT from dropping the work
      Assertions.assertTrue(sink >= 0);
    }
    return out.toString();
  }

  private record DetectorFactory(@NotNull String name, @NotNull Supplier<MassDetector> supplier) {

  }

  private static @NotNull List<SimpleFrame> loadIndexedFrames(
      @NotNull final CentroidingDataset dataset, @NotNull final String rawFile)
      throws IOException {
    final TreeSet<Integer> frameIds = new TreeSet<>(
        TargetIndexCsv.read(dataset.indexFile()).stream().filter(e -> e.rawFile().equals(rawFile))
            .map(TargetIndexEntry::frameId).toList());
    final List<SimpleFrame> frames = new ArrayList<>();
    try (final AgilentDataAccess access = AgilentCentroidingTestConfig.openProfile(
        dataset.resolve(rawFile))) {
      final IMSRawDataFileImpl file = (IMSRawDataFileImpl) access.createDataFile();
      for (final int frameId : frameIds) {
        final SimpleFrame frame = access.readFrame(file, frameId);
        if (frame != null) {
          frames.add(frame);
        }
      }
    }
    return frames;
  }
}
