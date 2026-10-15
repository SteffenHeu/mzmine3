package import_data.agilent_centroiding;

import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectorPreprocessor;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.exactmass.ExactMassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max.AdaptiveLocalMaxMassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max.LocalMaxGaussianModule;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max.LocalMaxMassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max.LocalMaxNoSmoothingModule;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max.LocalMaxSavitzkyGolayModule;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max.LocalMaxSavitzkyGolayParameters;
import io.github.mzmine.parameters.ParameterSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

/**
 * A mass detector configuration of the centroiding benchmark. Detectors keep state of the last
 * spectrum (preprocessed intensities), so a new instance is created per frame and level.
 *
 * @param name    unique name used in the result tables
 * @param factory creates a detector for a spectrum level and the intensity maxima of the file
 *                (saturation-aware detectors derive their threshold from it)
 */
public record MassDetectorConfig(@NotNull String name,
                                 @NotNull BiFunction<SpectrumLevel, FileIntensityMaxima, MassDetector> factory) {

  /**
   * Minimum number of non-zero points per peak, same as the import auto-centroiding.
   */
  private static final int MIN_POINTS = 3;
  private static final int[] SMOOTHING_WIDTHS = {5, 7};
  /**
   * Saturation thresholds as fraction of the highest raw intensity of the file in the respective
   * dimension. Native 6560 files: ratio distortion starts at ~0.3-0.5 of the ~49.5k ceiling.
   */
  public static final double[] SATURATION_FRACTIONS = {0.3, 0.4, 0.5};
  /**
   * Saturation threshold of the EMG branch of the adaptive local max detector as fraction of the
   * highest raw intensity of the file in the respective dimension.
   */
  public static final double EMG_SATURATION_FRACTION = 0.5;

  public @NotNull MassDetector create(@NotNull final SpectrumLevel level,
      @NotNull final FileIntensityMaxima maxima) {
    return factory.apply(level, maxima);
  }

  /**
   * Exact mass, local maximum and adaptive local maximum detectors, each without, with Gaussian
   * and with Savitzky-Golay smoothing, and the adaptive local max with EMG saturation branch.
   */
  public static @NotNull List<MassDetectorConfig> defaults() {
    final List<MassDetectorConfig> configs = new ArrayList<>();
    configs.add(fixed("exact_mass", () -> new ExactMassDetector(0)));

    configs.add(localMax("local_max_none", new LocalMaxNoSmoothingModule()));
    configs.add(adaptive("adaptive_local_max_none", new LocalMaxNoSmoothingModule()));
    for (final int width : SMOOTHING_WIDTHS) {
      configs.add(localMax("local_max_gauss" + width, new LocalMaxGaussianModule(width)));
      configs.add(localMax("local_max_sg" + width, savitzkyGolay(width)));
      configs.add(adaptive("adaptive_local_max_gauss" + width, new LocalMaxGaussianModule(width)));
      configs.add(adaptive("adaptive_local_max_sg" + width, savitzkyGolay(width)));
    }

    // decision: the EMG saturation branch is compared at the best threshold of the first run only
    configs.add(new MassDetectorConfig(
        String.format(Locale.ROOT, "adaptive_local_max_emg_f%.1f", EMG_SATURATION_FRACTION),
        (level, maxima) -> new AdaptiveLocalMaxMassDetector(0, AbundanceMeasure.Height,
            MIN_POINTS, new LocalMaxNoSmoothingModule(),
            EMG_SATURATION_FRACTION * maxima.max(level))));
    return configs;
  }

  /**
   * Detectors used to centroid every mobility scan before combining them over the mobilogram.
   */
  public static @NotNull List<MassDetectorConfig> mobilogramBaseDetectors() {
    return List.of(localMax("local_max_none", new LocalMaxNoSmoothingModule()),
        adaptive("adaptive_local_max_none", new LocalMaxNoSmoothingModule()));
  }

  private static @NotNull MassDetectorConfig fixed(@NotNull final String name,
      @NotNull final Supplier<MassDetector> detector) {
    return new MassDetectorConfig(name, (level, maxima) -> detector.get());
  }

  private static @NotNull MassDetectorConfig localMax(@NotNull final String name,
      @NotNull final MassDetectorPreprocessor preprocessor) {
    return fixed(name,
        () -> new LocalMaxMassDetector(0, AbundanceMeasure.Height, MIN_POINTS, preprocessor));
  }

  private static @NotNull MassDetectorConfig adaptive(@NotNull final String name,
      @NotNull final MassDetectorPreprocessor preprocessor) {
    return fixed(name, () -> new AdaptiveLocalMaxMassDetector(0, AbundanceMeasure.Height,
        MIN_POINTS, preprocessor));
  }

  private static @NotNull MassDetectorPreprocessor savitzkyGolay(final int width) {
    final ParameterSet params = new LocalMaxSavitzkyGolayParameters().cloneParameterSet();
    params.setParameter(LocalMaxSavitzkyGolayParameters.width, width);
    return new LocalMaxSavitzkyGolayModule().createPreprocessor(params);
  }
}
