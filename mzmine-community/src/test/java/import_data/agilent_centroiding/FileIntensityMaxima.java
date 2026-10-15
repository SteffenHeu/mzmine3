package import_data.agilent_centroiding;

import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.impl.SimpleFrame;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;

/**
 * Highest raw profile intensity of a raw file in the total frame spectra and in the mobility
 * scans. Native Agilent 6560 files reach a detector ceiling of ~49.5k counts per mobility scan
 * point, processed files (PNNL PreProcessor, demultiplexing) are rescaled, so saturation thresholds
 * are expressed as a fraction of these maxima.
 */
public record FileIntensityMaxima(double frameMax, double mobilityScanMax) {

  /**
   * @param frames profile frames of the file. For files with saturated signals the most intense
   *               frames are part of the target index, so the maxima approximate the detector
   *               ceiling.
   */
  public static @NotNull FileIntensityMaxima of(@NotNull final Collection<SimpleFrame> frames) {
    double frameMax = 0;
    double scanMax = 0;
    for (final SimpleFrame frame : frames) {
      frameMax = Math.max(frameMax, max(frame));
      for (int i = 0; i < frame.getNumberOfMobilityScans(); i++) {
        scanMax = Math.max(scanMax, max(frame.getMobilityScan(i)));
      }
    }
    return new FileIntensityMaxima(frameMax, scanMax);
  }

  public double max(@NotNull final SpectrumLevel level) {
    return switch (level) {
      case FRAME -> frameMax;
      case MOBILITY_SCAN, MOBILOGRAM -> mobilityScanMax;
    };
  }

  private static double max(@NotNull final MassSpectrum spectrum) {
    double max = 0;
    for (int i = 0; i < spectrum.getNumberOfDataPoints(); i++) {
      max = Math.max(max, spectrum.getIntensityValue(i));
    }
    return max;
  }
}
