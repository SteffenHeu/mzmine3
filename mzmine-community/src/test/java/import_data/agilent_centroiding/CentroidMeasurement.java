package import_data.agilent_centroiding;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Result of one centroiding method for one target on one spectrum.
 *
 * @param detector             mass detector configuration name or the Agilent centroiding
 * @param found                a centroid was detected within the evaluation window
 * @param measuredMz           m/z of the most intense centroid within the window, NaN if not
 *                             found
 * @param errorDa              measuredMz - exactMz
 * @param errorPpm             error in ppm
 * @param centroidsInWindow    number of centroids within the window, values above 1 indicate split
 *                             peaks
 * @param potentiallySaturated saturation label from the target index
 * @param vendorSaturationFlag saturation flag of the vendor software, null if unknown
 * @param asymmetry            half-height asymmetry factor of the raw profile peak of the target
 *                             in the evaluated spectrum, NaN if unknown
 * @param peakClass            saturated, asymmetric or Gaussian
 */
public record CentroidMeasurement(@NotNull String dataset, @NotNull String rawFile,
                                  @NotNull String target, double exactMz,
                                  @NotNull SpectrumLevel level, @NotNull String detector,
                                  boolean found, double measuredMz, double intensity,
                                  double errorDa, double errorPpm, int centroidsInWindow,
                                  boolean potentiallySaturated,
                                  @Nullable Boolean vendorSaturationFlag, double asymmetry,
                                  @NotNull PeakClass peakClass) {

  /**
   * Matches the most intense centroid within
   * {@link AgilentCentroidingTestConfig#EVALUATION_SEARCH_PPM} of the exact m/z.
   *
   * @param centroids [0] = m/z, [1] = intensity, sorted by m/z
   * @param shape     raw peak shape of the target in the evaluated spectrum
   */
  public static @NotNull CentroidMeasurement match(@NotNull final TargetIndexEntry entry,
      @NotNull final SpectrumLevel level, @NotNull final String detector,
      final double @NotNull [] @NotNull [] centroids, @Nullable final PeakAsymmetry shape) {
    final int best = mostIntenseInWindow(centroids, entry.exactMz());
    final int count = countInWindow(centroids, entry.exactMz());
    return of(entry, level, detector, best >= 0 ? centroids[0][best] : Double.NaN,
        best >= 0 ? centroids[1][best] : Double.NaN, count, shape);
  }

  /**
   * Measurement of a derived m/z, e.g. combined over several mobility scans. NaN m/z = not found.
   */
  public static @NotNull CentroidMeasurement of(@NotNull final TargetIndexEntry entry,
      @NotNull final SpectrumLevel level, @NotNull final String detector, final double mz,
      final double intensity, final int centroidsInWindow, @Nullable final PeakAsymmetry shape) {
    final double exactMz = entry.exactMz();
    final boolean found = !Double.isNaN(mz);
    return new CentroidMeasurement(entry.dataset(), entry.rawFile(), entry.targetName(), exactMz,
        level, detector, found, mz, intensity, mz - exactMz,
        AgilentCentroidingTestConfig.ppmError(mz, exactMz), centroidsInWindow,
        entry.potentiallySaturated(), entry.vendorSaturationFlag(),
        shape == null ? Double.NaN : shape.asymmetry(),
        PeakClass.classify(entry.potentiallySaturated(), shape));
  }

  /**
   * @return index of the most intense centroid within the evaluation window or -1
   */
  public static int mostIntenseInWindow(final double @NotNull [] @NotNull [] centroids,
      final double exactMz) {
    final double tol = AgilentCentroidingTestConfig.ppmToDa(exactMz,
        AgilentCentroidingTestConfig.EVALUATION_SEARCH_PPM);
    int best = -1;
    for (int i = 0; i < centroids[0].length; i++) {
      if (Math.abs(centroids[0][i] - exactMz) <= tol && (best < 0
          || centroids[1][i] > centroids[1][best])) {
        best = i;
      }
    }
    return best;
  }

  private static int countInWindow(final double @NotNull [] @NotNull [] centroids,
      final double exactMz) {
    final double tol = AgilentCentroidingTestConfig.ppmToDa(exactMz,
        AgilentCentroidingTestConfig.EVALUATION_SEARCH_PPM);
    int count = 0;
    for (final double mz : centroids[0]) {
      if (Math.abs(mz - exactMz) <= tol) {
        count++;
      }
    }
    return count;
  }
}
