package io.github.mzmine.modules.dataprocessing.id_pfas_annotation;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IntensityCoverageUtils {

  private static final Logger logger = Logger.getLogger(IntensityCoverageUtils.class.getName());

  public static double getIntensityCoverage(@NotNull final MassSpectrum spectrum,
      @NotNull final double[] mzs, @NotNull final MZTolerance tolerance) {
    return getIntensityCoverage(spectrum, mzs, tolerance, null, null);
  }

  /**
   * @param spectrum  the spectrum to match
   * @param mzs       sorted ascending
   * @param tolerance mz tolerance
   * @return
   */
  public static <T> double getIntensityCoverage(@NotNull final MassSpectrum spectrum,
      @NotNull final double[] mzs, @NotNull final MZTolerance tolerance,
      @Nullable final List<T> inPossibleFragments, @Nullable List<T> outMatchedFragments) {

    if ((inPossibleFragments != null && outMatchedFragments == null) || (outMatchedFragments != null
        && inPossibleFragments == null)) {
      throw new IllegalArgumentException("Only one list specified.");
    } else if(inPossibleFragments != null && (mzs.length != inPossibleFragments.size())) {
      throw  new IllegalArgumentException("Number of mzs differs from possible fragments.");
    }

    if (spectrum.getNumberOfDataPoints() == 0) {
      return 0d;
    }

    final Double tic = Objects.requireNonNullElse(spectrum.getTIC(), 1d);

    double coveredIntensity = 0d;

    final int numDp = spectrum.getNumberOfDataPoints();

    int spectrumIndex = 0;
    for (int i = 0; i < mzs.length && spectrumIndex < numDp; i++) {
      final Range<Double> toleranceRange = tolerance.getToleranceRange(mzs[i]);
      final double lower = toleranceRange.lowerEndpoint();
      final double upper = toleranceRange.upperEndpoint();

      double spectrumMz = spectrum.getMzValue(spectrumIndex);
      if (spectrumMz > upper) {
        continue;
      }

      // progress as long as spectrumMz is lower than the current lower limit
      while (spectrumMz < lower && spectrumIndex < numDp - 1) {
        spectrumIndex++;
        spectrumMz = spectrum.getMzValue(spectrumIndex);
      }

      // check if we are within tolerance now
      if (tolerance.checkWithinTolerance(mzs[i], spectrumMz)) {
        double bestIntensity = spectrum.getIntensityValue(spectrumIndex);
        int bestIndex = spectrumIndex;

        // check the next signals in the spectrum, if we can match a higher intensity signal
        for (int j = spectrumIndex; j < numDp; j++) {
          // as soon as we are not within the tolerance range anymore, we can stop
          if (!tolerance.checkWithinTolerance(mzs[i], spectrum.getMzValue(j))) {
            break;
          }
          // check if we can match a higher intensity signal
          if (bestIntensity > spectrum.getIntensityValue(j)) {
            bestIndex = j;
            bestIntensity = spectrum.getIntensityValue(j);
          }
        }

        if (inPossibleFragments != null) {
          outMatchedFragments.add(inPossibleFragments.get(i));

          /*if(inPossibleFragments.get(i) instanceof PfasFragment f) {
            if(f.block().getName().equals("Betaine")) {
              logger.info("Matched " + spectrumMz + " to " + mzs[i] + " - " + f.formula());
            }
          }*/
        }
        spectrumIndex = bestIndex + 1;  // don't match a signal twice
        coveredIntensity += bestIntensity;
      }

    }
    return coveredIntensity / tic;
  }

}
