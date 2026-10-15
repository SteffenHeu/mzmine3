package import_data.agilent_centroiding;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class of a target peak in one spectrum for the split of the accuracy benchmark. Saturation takes
 * precedence over the shape.
 */
public enum PeakClass {
  /**
   * Isotope ratio indicates detector saturation (target index label).
   */
  SATURATED,
  /**
   * Unsaturated, tailing or fronting beyond {@link #ASYMMETRY_LIMIT}.
   */
  ASYMMETRIC,
  /**
   * Unsaturated and approximately symmetric.
   */
  GAUSSIAN,
  /**
   * No peak or no measurable half-height width.
   */
  UNKNOWN;

  /**
   * Peaks with a half-height asymmetry factor outside [1 / limit, limit] are asymmetric.
   */
  public static final double ASYMMETRY_LIMIT = 1.25;

  public static @NotNull PeakClass classify(final boolean saturated,
      @Nullable final PeakAsymmetry shape) {
    if (saturated) {
      return SATURATED;
    }
    if (shape == null || Double.isNaN(shape.asymmetry())) {
      return UNKNOWN;
    }
    final double as = shape.asymmetry();
    return as > ASYMMETRY_LIMIT || as < 1 / ASYMMETRY_LIMIT ? ASYMMETRIC : GAUSSIAN;
  }
}
