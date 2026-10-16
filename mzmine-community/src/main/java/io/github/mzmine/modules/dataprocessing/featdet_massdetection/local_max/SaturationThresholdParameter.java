package io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max;

import io.github.mzmine.parameters.parametertypes.DoubleParameter;
import io.github.mzmine.parameters.parametertypes.combowithinput.ComboWithInputParameter;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Source of the detector saturation threshold for mobility scans: off, manual input, raw file
 * metadata or derived from the data. The available options can be restricted, e.g., the import
 * cannot access the raw file metadata or all mobility scans of the file.
 */
public class SaturationThresholdParameter extends
    ComboWithInputParameter<SaturationThresholdOption, SaturationThresholdValue, DoubleParameter> {

  public static final List<SaturationThresholdOption> ALL_OPTIONS = List.of(
      SaturationThresholdOption.values());
  /**
   * Options that work without the raw data file, e.g., during the advanced import
   */
  public static final List<SaturationThresholdOption> IMPORT_OPTIONS = List.of(
      SaturationThresholdOption.OFF, SaturationThresholdOption.MANUAL);

  public SaturationThresholdParameter() {
    this(createEmbeddedParameter(), ALL_OPTIONS, SaturationThresholdValue.OFF);
  }

  public SaturationThresholdParameter(@NotNull final DoubleParameter embeddedParameter,
      @NotNull final List<SaturationThresholdOption> options,
      @NotNull final SaturationThresholdValue defaultValue) {
    super(embeddedParameter, options.toArray(SaturationThresholdOption[]::new),
        SaturationThresholdOption.MANUAL, defaultValue);
  }

  private static @NotNull DoubleParameter createEmbeddedParameter() {
    return new DoubleParameter("Saturation threshold (mobility scans)", """
        Raw intensity of a mobility scan data point from which on profile peaks are treated as \
        saturated by the detector. Saturated peaks are centroided by the mode of an exponentially \
        modified Gaussian fit to the saturated core and the flanks. Only applies to mobility scans, \
        not to frames.
        Off: no saturation handling.
        Manual: the entered raw intensity.
        Raw file metadata: 0.5 x mobility scan abundance limit of the raw file (e.g., Agilent), \
        not available during import.
        Auto: 0.5 x the highest mobility scan intensity of the file if it occurs repeatedly \
        (saturation plateau), not available during import.""", new DecimalFormat("0"), 0d, 0d,
        null);
  }

  /**
   * @return copy of this parameter that only offers the given options. The value is reset to
   * {@link SaturationThresholdOption#OFF} if it is not available.
   */
  public @NotNull SaturationThresholdParameter withOptions(
      @NotNull final List<SaturationThresholdOption> options) {
    final DoubleParameter embeddedClone = embeddedParameter.cloneParameter();
    final SaturationThresholdValue current = Objects.requireNonNullElse(value,
        SaturationThresholdValue.OFF);
    final SaturationThresholdValue newValue =
        options.contains(current.option()) ? current : SaturationThresholdValue.OFF;
    embeddedClone.setValue(newValue.manual());
    return new SaturationThresholdParameter(embeddedClone, options, newValue);
  }

  /**
   * @return the options offered by this parameter
   */
  public @NotNull List<SaturationThresholdOption> getOptions() {
    return List.copyOf(choices);
  }

  @Override
  public SaturationThresholdValue createValue(final SaturationThresholdOption option,
      final DoubleParameter embeddedParameter) {
    return new SaturationThresholdValue(option,
        Objects.requireNonNullElse(embeddedParameter.getValue(), 0d));
  }

  /**
   * Values with an option that is not offered (e.g., copied from the full parameter set into the
   * import parameters) are reset to {@link SaturationThresholdOption#OFF}.
   */
  @Override
  public void setValue(final SaturationThresholdValue newValue) {
    if (newValue != null && choices != null && !choices.contains(newValue.option())) {
      super.setValue(new SaturationThresholdValue(SaturationThresholdOption.OFF, newValue.manual()));
      return;
    }
    super.setValue(newValue);
  }

  @Override
  public SaturationThresholdParameter cloneParameter() {
    // keep restricted options of copies, e.g., for the import
    return withOptions(choices);
  }
}
