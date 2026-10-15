package import_data.agilent_centroiding;

import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.MobilityScan;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.impl.SimpleFrame;
import io.github.mzmine.modules.io.import_rawdata_agilent_d.AgilentDataAccess;
import io.github.mzmine.project.impl.IMSRawDataFileImpl;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the {@link TargetIndexEntry target index} of a dataset from a centroided import: for each
 * target the MS1 frame with the most intense monoisotopic peak and, within that frame, the most
 * intense mobility scan. The observed M+1/M ratio is compared to the predicted ratio to label
 * potentially saturated targets.
 */
public final class TargetIndexBuilder {

  private static final Logger logger = Logger.getLogger(TargetIndexBuilder.class.getName());

  /**
   * Mobility scan ratios are only used for the saturation label if the monoisotopic peak in the
   * mobility scan has at least this intensity, otherwise counting noise dominates the ratio.
   */
  private static final double MIN_MOBILITY_INTENSITY_FOR_RATIO = 500;

  private TargetIndexBuilder() {
  }

  public static @NotNull List<TargetIndexEntry> build(@NotNull final CentroidingDataset dataset) {
    final List<TargetIndexEntry> entries = new ArrayList<>();
    for (final Map.Entry<File, List<CentroidingTarget>> e : dataset.loadTargets().entrySet()) {
      entries.addAll(buildForFile(dataset, e.getKey(), e.getValue()));
    }
    return entries;
  }

  public static @NotNull List<TargetIndexEntry> buildForFile(
      @NotNull final CentroidingDataset dataset, @NotNull final File rawFile,
      @NotNull final List<CentroidingTarget> targets) {
    final long start = System.currentTimeMillis();
    final int numTargets = targets.size();
    final double[] bestIntensity = new double[numTargets];
    final int[] bestFrameId = new int[numTargets];
    Arrays.fill(bestFrameId, -1);

    final List<TargetIndexEntry> entries = new ArrayList<>();
    try (final AgilentDataAccess access = AgilentCentroidingTestConfig.openCentroided(rawFile)) {
      final IMSRawDataFileImpl file = (IMSRawDataFileImpl) access.createDataFile();

      // pass 1: most intense MS1 frame per target
      for (int frameId = 1; frameId <= access.getFrameCount(); frameId++) {
        final SimpleFrame frame = access.readFrame(file, frameId);
        if (!isMatchingMs1(frame, dataset.polarity())) {
          continue;
        }
        for (int t = 0; t < numTargets; t++) {
          final double intensity = maxIntensity(frame, targets.get(t).exactMz());
          if (intensity > bestIntensity[t]) {
            bestIntensity[t] = intensity;
            bestFrameId[t] = frameId;
          }
        }
      }

      // pass 2: most intense mobility scan and isotope ratios in the best frame
      final Map<Integer, SimpleFrame> frameCache = new HashMap<>();
      for (int t = 0; t < numTargets; t++) {
        if (bestFrameId[t] < 0
            || bestIntensity[t] < AgilentCentroidingTestConfig.MIN_FRAME_INTENSITY) {
          continue;
        }
        final SimpleFrame frame = frameCache.computeIfAbsent(bestFrameId[t],
            id -> access.readFrame(file, id));
        entries.add(createEntry(dataset, rawFile, targets.get(t), frame));
      }
    }
    logger.info("Indexed %d of %d targets in %s (%d s)".formatted(entries.size(), numTargets,
        rawFile.getName(), (System.currentTimeMillis() - start) / 1000));
    return entries;
  }

  private static @NotNull TargetIndexEntry createEntry(@NotNull final CentroidingDataset dataset,
      @NotNull final File rawFile, @NotNull final CentroidingTarget target,
      @NotNull final SimpleFrame frame) {
    final double mz = target.exactMz();
    final double m1Mz = target.m1Mz();

    int bestScan = -1;
    double bestScanIntensity = 0;
    for (int i = 0; i < frame.getNumberOfMobilityScans(); i++) {
      final double intensity = maxIntensity(frame.getMobilityScan(i), mz);
      if (intensity > bestScanIntensity) {
        bestScanIntensity = intensity;
        bestScan = i;
      }
    }

    final double frameM = maxIntensity(frame, mz);
    final double frameM1 = maxIntensity(frame, m1Mz);
    final double frameRatio = frameM1 > 0 ? frameM1 / frameM : Double.NaN;

    double mobilityM = 0;
    double mobilityM1 = 0;
    double mobility = Double.NaN;
    if (bestScan >= 0) {
      final MobilityScan scan = frame.getMobilityScan(bestScan);
      mobilityM = maxIntensity(scan, mz);
      mobilityM1 = maxIntensity(scan, m1Mz);
      mobility = scan.getMobility();
    }
    final double mobilityRatio = mobilityM1 > 0 ? mobilityM1 / mobilityM : Double.NaN;

    final double expected = target.expectedM1Ratio();
    final double frameDeviation = frameRatio / expected - 1;
    final double mobilityDeviation = mobilityRatio / expected - 1;
    // decision: only a too high M+1/M indicates saturation (the monoisotopic peak is clipped), a
    // too low ratio or a missing M+1 (NaN) is not counted
    final boolean frameOff = frameDeviation > AgilentCentroidingTestConfig.RATIO_TOLERANCE;
    final boolean mobilityOff = mobilityM >= MIN_MOBILITY_INTENSITY_FOR_RATIO
        && mobilityDeviation > AgilentCentroidingTestConfig.RATIO_TOLERANCE;

    return new TargetIndexEntry(dataset.name(), dataset.relativePath(rawFile), target.name(),
        target.neutralFormula(), target.ionType().name(), target.ionFormula(), mz, expected,
        target.vendorSaturationFlag(), frame.getScanNumber(), frame.getRetentionTime(), frameM,
        frameM1, frameRatio, bestScan, mobility, mobilityM, mobilityM1, mobilityRatio,
        frameDeviation, mobilityDeviation, frameOff || mobilityOff);
  }

  private static boolean isMatchingMs1(@Nullable final SimpleFrame frame,
      @NotNull final PolarityType polarity) {
    if (frame == null || frame.getMSLevel() != 1) {
      return false;
    }
    final PolarityType framePolarity = frame.getPolarity();
    return framePolarity == PolarityType.UNKNOWN || framePolarity == polarity;
  }

  /**
   * @return the max intensity of all centroids within
   * {@link AgilentCentroidingTestConfig#INDEX_SEARCH_PPM} of mz, 0 if there is none
   */
  static double maxIntensity(@NotNull final MassSpectrum spectrum, final double mz) {
    final double tol = AgilentCentroidingTestConfig.ppmToDa(mz,
        AgilentCentroidingTestConfig.INDEX_SEARCH_PPM);
    final int n = spectrum.getNumberOfDataPoints();
    // binary search for the first m/z >= lower bound
    int lo = 0;
    int hi = n;
    while (lo < hi) {
      final int mid = (lo + hi) >>> 1;
      if (spectrum.getMzValue(mid) < mz - tol) {
        lo = mid + 1;
      } else {
        hi = mid;
      }
    }
    double max = 0;
    for (int i = lo; i < n && spectrum.getMzValue(i) <= mz + tol; i++) {
      max = Math.max(max, spectrum.getIntensityValue(i));
    }
    return max;
  }
}
