package io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max;

import io.github.mzmine.parameters.parametertypes.combowithinput.ComboWithInputValue;
import org.jetbrains.annotations.NotNull;

/**
 * @param option source of the saturation threshold
 * @param manual threshold in raw intensity, only used for {@link SaturationThresholdOption#MANUAL}
 */
public record SaturationThresholdValue(@NotNull SaturationThresholdOption option,
                                       double manual) implements
    ComboWithInputValue<SaturationThresholdOption, Double> {

  public static final SaturationThresholdValue OFF = new SaturationThresholdValue(
      SaturationThresholdOption.OFF, 0d);

  @Override
  public @NotNull SaturationThresholdOption getSelectedOption() {
    return option;
  }

  @Override
  public Double getEmbeddedValue() {
    return manual;
  }
}
