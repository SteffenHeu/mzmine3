package import_data.agilent_centroiding;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Combines the centroids of a target over all mobility scans of a frame into one m/z (approach 4:
 * use the unsaturated flanks of the drift peak). Only scans whose centroid height is below the
 * saturation threshold contribute, weighted by their intensity, so the saturated apex scans of the
 * drift peak are skipped.
 */
public final class MobilogramCentroider {

  /**
   * Scans below this fraction of the most intense scan of the target are ignored, their m/z is
   * dominated by counting noise.
   */
  private static final double MIN_RELATIVE_HEIGHT = 0.02;

  private MobilogramCentroider() {
  }

  /**
   * @param mz             intensity weighted m/z, NaN if no scan qualified
   * @param intensity      summed height of the used scans
   * @param scansUsed      number of mobility scans that contributed
   */
  public record Result(double mz, double intensity, int scansUsed) {

  }

  /**
   * @param scanCentroids        centroids of every mobility scan of the frame ([0] = m/z, [1] =
   *                             intensity)
   * @param exactMz              target m/z
   * @param saturationThreshold  scans with a centroid height at or above are excluded. Use
   *                             {@link Double#POSITIVE_INFINITY} to combine all scans.
   */
  public static @NotNull Result combine(@NotNull final List<double[][]> scanCentroids,
      final double exactMz, final double saturationThreshold) {
    final int n = scanCentroids.size();
    final double[] mzs = new double[n];
    final double[] heights = new double[n];
    double maxHeight = 0;
    for (int i = 0; i < n; i++) {
      final double[][] centroids = scanCentroids.get(i);
      final int best = CentroidMeasurement.mostIntenseInWindow(centroids, exactMz);
      if (best >= 0) {
        mzs[i] = centroids[0][best];
        heights[i] = centroids[1][best];
        maxHeight = Math.max(maxHeight, heights[i]);
      }
    }

    final double minHeight = MIN_RELATIVE_HEIGHT * maxHeight;
    double sumMzInt = 0;
    double sumInt = 0;
    int used = 0;
    for (int i = 0; i < n; i++) {
      if (heights[i] <= 0 || heights[i] < minHeight || heights[i] >= saturationThreshold) {
        continue;
      }
      sumMzInt += mzs[i] * heights[i];
      sumInt += heights[i];
      used++;
    }
    return used == 0 ? new Result(Double.NaN, 0, 0) : new Result(sumMzInt / sumInt, sumInt, used);
  }
}
