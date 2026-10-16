package import_data.agilent_centroiding;

import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.impl.SimpleFrame;
import java.io.File;
import java.util.Collection;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Highest raw profile intensity of a raw file in the total frame spectra and in the mobility
 * scans. Native Agilent 6560 files reach a detector ceiling of ~49.5k counts per mobility scan
 * point, processed files (PNNL PreProcessor, demultiplexing) are rescaled, so saturation thresholds
 * are expressed as a fraction of these maxima.
 *
 * @param mobilityScanAbundanceLimit {@code FrameSpecAbundLimit} of the frame methods (max possible
 *                                   abundance of a mobility scan point = transients x per
 *                                   transient limit), NaN if unknown. Not defined for frames.
 */
public record FileIntensityMaxima(double frameMax, double mobilityScanMax,
                                  double mobilityScanAbundanceLimit) {

  private static final Logger logger = Logger.getLogger(FileIntensityMaxima.class.getName());

  /**
   * @param frames profile frames of the file. For files with saturated signals the most intense
   *               frames are part of the target index, so the maxima approximate the detector
   *               ceiling.
   */
  public static @NotNull FileIntensityMaxima of(@NotNull final Collection<SimpleFrame> frames) {
    return of(frames, null);
  }

  /**
   * @param rawFile the .d folder to read the abundance limit from, null for NaN
   */
  public static @NotNull FileIntensityMaxima of(@NotNull final Collection<SimpleFrame> frames,
      @Nullable final File rawFile) {
    double frameMax = 0;
    double scanMax = 0;
    for (final SimpleFrame frame : frames) {
      frameMax = Math.max(frameMax, max(frame));
      for (int i = 0; i < frame.getNumberOfMobilityScans(); i++) {
        scanMax = Math.max(scanMax, max(frame.getMobilityScan(i)));
      }
    }
    return new FileIntensityMaxima(frameMax, scanMax,
        rawFile == null ? Double.NaN : readAbundanceLimit(rawFile));
  }

  /**
   * Lowest {@code FrameSpecAbundLimit} of all frame methods in {@code AcqData/IMSFrameMeth.xml}.
   */
  public static double readAbundanceLimit(@NotNull final File rawFile) {
    final File xml = new File(rawFile, "AcqData/IMSFrameMeth.xml");
    if (!xml.isFile()) {
      return Double.NaN;
    }
    try {
      final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml);
      final NodeList limits = doc.getElementsByTagName("FrameSpecAbundLimit");
      double min = Double.NaN;
      double max = Double.NaN;
      for (int i = 0; i < limits.getLength(); i++) {
        final double value = Double.parseDouble(limits.item(i).getTextContent().trim());
        min = Double.isNaN(min) ? value : Math.min(min, value);
        max = Double.isNaN(max) ? value : Math.max(max, value);
      }
      if (min != max) {
        logger.warning("Different abundance limits %.0f - %.0f in %s, using the lowest".formatted(
            min, max, rawFile));
      }
      return min > 0 ? min : Double.NaN;
    } catch (Exception e) {
      logger.warning("Cannot read abundance limit of " + rawFile + ": " + e.getMessage());
      return Double.NaN;
    }
  }

  /**
   * Saturation threshold as fraction of the abundance limit for mobility scans. Frames have no
   * limit, so they get no threshold ({@link Double#POSITIVE_INFINITY}).
   */
  public double limitThreshold(@NotNull final SpectrumLevel level, final double fraction) {
    return switch (level) {
      case FRAME -> Double.POSITIVE_INFINITY;
      case MOBILITY_SCAN, MOBILOGRAM -> Double.isNaN(mobilityScanAbundanceLimit)
          ? Double.POSITIVE_INFINITY : fraction * mobilityScanAbundanceLimit;
    };
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
