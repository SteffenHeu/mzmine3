package io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max;

import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.MobilityScan;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import org.jetbrains.annotations.NotNull;

/**
 * Derives the detector saturation level of the mobility scans of a raw file from the data: a
 * saturated detector clips many data points at (almost) the same highest intensity, while the
 * highest point of an unsaturated file is a single peak. The base peak of every mobility scan of
 * one MS level is compared with the highest base peak of the file.
 */
public final class SaturationPlateauEstimator {

  /**
   * Base peaks within this relative distance of the highest base peak belong to the plateau
   */
  public static final double PLATEAU_TOLERANCE = 0.01;
  /**
   * Minimum number of mobility scans with a base peak on the plateau to call the file saturated
   */
  public static final int MIN_PLATEAU_SCANS = 10;

  private SaturationPlateauEstimator() {
  }

  /**
   * @param file    the raw file
   * @param msLevel ms level of the frames to check
   * @return the plateau intensity (highest mobility scan intensity of the file) or NaN if the
   * highest intensity does not occur repeatedly
   */
  public static double estimatePlateau(@NotNull final IMSRawDataFile file, final int msLevel) {
    final DoubleArrayList basePeaks = new DoubleArrayList();
    for (final Frame frame : file.getFrames()) {
      if (frame.getMSLevel() != msLevel) {
        continue;
      }
      for (final MobilityScan scan : frame.getMobilityScans()) {
        final Double basePeak = scan.getBasePeakIntensity();
        if (basePeak != null) {
          basePeaks.add(basePeak.doubleValue());
        }
      }
    }
    return estimatePlateau(basePeaks.toDoubleArray());
  }

  /**
   * @param basePeaks base peak intensity of each mobility scan
   * @return the highest base peak if at least {@link #MIN_PLATEAU_SCANS} base peaks are within
   * {@link #PLATEAU_TOLERANCE} of it, otherwise NaN
   */
  public static double estimatePlateau(final double @NotNull [] basePeaks) {
    double max = 0;
    for (final double basePeak : basePeaks) {
      max = Math.max(max, basePeak);
    }
    if (max <= 0) {
      return Double.NaN;
    }

    final double plateauStart = max * (1 - PLATEAU_TOLERANCE);
    int plateauScans = 0;
    for (final double basePeak : basePeaks) {
      if (basePeak >= plateauStart && ++plateauScans >= MIN_PLATEAU_SCANS) {
        return max;
      }
    }
    return Double.NaN;
  }
}
