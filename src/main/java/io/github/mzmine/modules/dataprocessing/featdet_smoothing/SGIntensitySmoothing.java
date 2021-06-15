package io.github.mzmine.modules.dataprocessing.featdet_smoothing;

import io.github.mzmine.datamodel.featuredata.IntensitySeries;
import org.jetbrains.annotations.NotNull;

public class SGIntensitySmoothing {

  private final ZeroHandlingType zht;
  private final double[] normWeights;

  /**
   * @param zeroHandlingType defines how zero values shall be handled when comparing the old and new
   *                         intensities {@link ZeroHandlingType#KEEP}, {@link
   *                         ZeroHandlingType#OVERRIDE}.
   * @param normWeights      The normalized weights for smoothing.
   */
  public SGIntensitySmoothing(@NotNull final ZeroHandlingType zeroHandlingType,
      @NotNull final double[] normWeights) {
    this.zht = zeroHandlingType;
    this.normWeights = normWeights;
  }

  /**
   * @param access The intensity series to be smoothed. Ideally an instance of {@link
   *                   io.github.mzmine.datamodel.data_access.EfficientDataAccess} for best
   *                   performance.
   * @return
   */
  public double[] smooth(@NotNull final IntensitySeries access) {
    // Initialise.
    final int numPoints = access.getNumberOfValues();
    final int fullWidth = normWeights.length;
    final int halfWidth = (fullWidth - 1) / 2;

    double[] smoothed = new double[numPoints];
    for (int i = 0; i < numPoints; i++) {
      final int k = i - halfWidth;
      for (int j = Math.max(0, -k); j < Math.min(fullWidth, numPoints - k); j++) {
        smoothed[i] += access.getIntensity(k + j) * normWeights[j];
      }

      if (smoothed[i] < 0d) {
        smoothed[i] = 0d;
      }

      // if values that were previously 0 shall remain 0, we process that here.
      if (zht == ZeroHandlingType.KEEP && Double.compare(access.getIntensity(i), 0d) == 0) {
        smoothed[i] = 0;
      }
    }

    return smoothed;
  }

}
