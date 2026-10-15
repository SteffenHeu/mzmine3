package import_data.agilent_centroiding;

import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max.AdaptiveLocalMaxMassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max.LocalMaxNoSmoothingModule;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max.LocalMaxPeak;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shape of the raw profile peak of a target in one spectrum. The asymmetry factor is the right
 * half-width divided by the left half-width at 50 % of the apex height (> 1 tailing, < 1
 * fronting). The apex position is the vertex of a parabola through the highest point and its
 * neighbors, the half-height crossings are linearly interpolated between profile points.
 *
 * @param asymmetry   right / left half-width at half height, NaN if not measurable
 * @param leftPoints  profile points left of the apex within the detected peak
 * @param rightPoints profile points right of the apex within the detected peak
 * @param height      raw apex height
 */
public record PeakAsymmetry(double asymmetry, int leftPoints, int rightPoints, double height) {

  private static final double HEIGHT_FRACTION = 0.5;

  /**
   * @return the shape of the detected peak closest to the exact m/z (within the evaluation
   * window), null if there is no peak
   */
  public static @Nullable PeakAsymmetry measure(@NotNull final MassSpectrum spectrum,
      final double exactMz) {
    final AdaptiveLocalMaxMassDetector detector = new AdaptiveLocalMaxMassDetector(0,
        AbundanceMeasure.Height, 3, new LocalMaxNoSmoothingModule());
    final double tol = AgilentCentroidingTestConfig.ppmToDa(exactMz,
        AgilentCentroidingTestConfig.EVALUATION_SEARCH_PPM);
    LocalMaxPeak best = null;
    for (final LocalMaxPeak peak : detector.detectPeaks(spectrum)) {
      if (Math.abs(peak.centroidMz() - exactMz) <= tol && (best == null
          || peak.height() > best.height())) {
        best = peak;
      }
    }
    if (best == null) {
      return null;
    }
    return new PeakAsymmetry(asymmetry(spectrum, best), best.leftPoints(), best.rightPoints(),
        best.height());
  }

  private static double asymmetry(@NotNull final MassSpectrum spectrum,
      @NotNull final LocalMaxPeak peak) {
    final int apex = peak.apexIndex();
    final double apexMz = apexVertex(spectrum, peak);
    final double level = HEIGHT_FRACTION * spectrum.getIntensityValue(apex);

    double leftMz = Double.NaN;
    for (int i = apex; i > peak.leftIndex(); i--) {
      final double inner = spectrum.getIntensityValue(i);
      final double outer = spectrum.getIntensityValue(i - 1);
      if (outer < level && inner >= level) {
        leftMz = interpolate(spectrum.getMzValue(i - 1), outer, spectrum.getMzValue(i), inner,
            level);
        break;
      }
    }
    double rightMz = Double.NaN;
    for (int i = apex; i < peak.rightIndexExclusive() - 1; i++) {
      final double inner = spectrum.getIntensityValue(i);
      final double outer = spectrum.getIntensityValue(i + 1);
      if (outer < level && inner >= level) {
        rightMz = interpolate(spectrum.getMzValue(i), inner, spectrum.getMzValue(i + 1), outer,
            level);
        break;
      }
    }
    final double left = apexMz - leftMz;
    final double right = rightMz - apexMz;
    return left > 0 && right > 0 ? right / left : Double.NaN;
  }

  /**
   * m/z where the line between two points reaches the intensity level.
   */
  private static double interpolate(final double mz1, final double int1, final double mz2,
      final double int2, final double level) {
    return int1 == int2 ? (mz1 + mz2) / 2 : mz1 + (level - int1) / (int2 - int1) * (mz2 - mz1);
  }

  /**
   * Vertex of the parabola through the apex and its two neighbors, the apex m/z if not possible.
   */
  private static double apexVertex(@NotNull final MassSpectrum spectrum,
      @NotNull final LocalMaxPeak peak) {
    final int i = peak.apexIndex();
    if (i <= peak.leftIndex() || i >= peak.rightIndexExclusive() - 1) {
      return spectrum.getMzValue(i);
    }
    final double x0 = spectrum.getMzValue(i - 1);
    final double x1 = spectrum.getMzValue(i);
    final double x2 = spectrum.getMzValue(i + 1);
    final double y0 = spectrum.getIntensityValue(i - 1);
    final double y1 = spectrum.getIntensityValue(i);
    final double y2 = spectrum.getIntensityValue(i + 1);
    final double denominator = (x0 - x1) * (x0 - x2) * (x1 - x2);
    if (denominator == 0) {
      return x1;
    }
    final double a = (x2 * (y1 - y0) + x1 * (y0 - y2) + x0 * (y2 - y1)) / denominator;
    final double b = (x2 * x2 * (y0 - y1) + x1 * x1 * (y2 - y0) + x0 * x0 * (y1 - y2))
        / denominator;
    if (a >= 0) {
      return x1;
    }
    final double vertex = -b / (2 * a);
    return vertex >= x0 && vertex <= x2 ? vertex : x1;
  }
}
