package import_data.agilent_centroiding;

/**
 * Spectrum on which a centroid was determined.
 */
public enum SpectrumLevel {
  /**
   * Total frame spectrum (sum of all mobility scans).
   */
  FRAME,
  /**
   * Single mobility scan (drift time bin), the most intense one of the target.
   */
  MOBILITY_SCAN,
  /**
   * Combination of the centroids of all mobility scans of the target in the frame.
   */
  MOBILOGRAM
}
