package io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max;

import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.AbundanceMeasureParameter;
import io.github.mzmine.parameters.parametertypes.DoubleParameter;
import io.github.mzmine.parameters.parametertypes.IntegerParameter;
import io.github.mzmine.parameters.parametertypes.submodules.ModuleOptionsEnumComboParameter;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Parameters of the {@link AdaptiveLocalMaxMassDetector}: the local maximum parameters and the
 * saturation threshold of mobility scans.
 */
public class AdaptiveLocalMaxMassDetectorParameters extends SimpleParameterSet {

  public static final DoubleParameter noiseLevel = LocalMaxMassDetectorParameters.noiseLevel.cloneParameter();
  public static final IntegerParameter minNumberOfDp = LocalMaxMassDetectorParameters.minNumberOfDp.cloneParameter();
  public static final AbundanceMeasureParameter intensityCalculation = LocalMaxMassDetectorParameters.intensityCalculation.cloneParameter();
  public static final ModuleOptionsEnumComboParameter<LocalMaxSmoothingOptions> smoothing = LocalMaxMassDetectorParameters.smoothing.cloneParameter();
  public static final SaturationThresholdParameter saturationThreshold = new SaturationThresholdParameter();

  public AdaptiveLocalMaxMassDetectorParameters() {
    this(saturationThreshold);
  }

  /**
   * @param saturation the saturation threshold parameter, may offer fewer options, see
   *                   {@link AdaptiveLocalMaxImportParameters}
   */
  protected AdaptiveLocalMaxMassDetectorParameters(
      @NotNull final SaturationThresholdParameter saturation) {
    super(
        "https://mzmine.github.io/mzmine_documentation/module_docs/featdet_mass_detection/mass-detection-algorithms.html#local-maxima",
        noiseLevel, minNumberOfDp, intensityCalculation, smoothing, saturation);
  }

  @Override
  public int getVersion() {
    // same version as the local maximum parameters this set was split from
    return 2;
  }

  @Override
  public void handleLoadedParameters(final Map<String, Parameter<?>> loadedParams,
      final int loadedVersion) {
    super.handleLoadedParameters(loadedParams, loadedVersion);
    // smoothing was added without a version bump - disable it for configs that did not store it
    if (!loadedParams.containsKey(smoothing.getName())) {
      setParameter(smoothing, LocalMaxSmoothingOptions.NONE);
    }
  }
}
