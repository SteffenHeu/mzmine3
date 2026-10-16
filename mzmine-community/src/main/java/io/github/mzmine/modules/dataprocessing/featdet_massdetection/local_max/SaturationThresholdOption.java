package io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max;

import io.github.mzmine.datamodel.utils.UniqueIdSupplier;
import org.jetbrains.annotations.NotNull;

/**
 * Source of the detector saturation threshold of mobility scans in the
 * {@link AdaptiveLocalMaxMassDetector}.
 */
public enum SaturationThresholdOption implements UniqueIdSupplier {
  /**
   * No saturation handling
   */
  OFF,
  /**
   * User defined threshold in raw intensity of a mobility scan data point
   */
  MANUAL,
  /**
   * Fraction of the mobility scan abundance limit in the raw file metadata
   */
  RAW_FILE_METADATA,
  /**
   * Derived from the data: fraction of the highest mobility scan intensity of the file if it occurs
   * repeatedly (saturation plateau)
   */
  AUTO;

  @Override
  public @NotNull String getUniqueID() {
    return switch (this) {
      case OFF -> "off";
      case MANUAL -> "manual";
      case RAW_FILE_METADATA -> "raw_file_metadata";
      case AUTO -> "auto";
    };
  }

  @Override
  public String toString() {
    return switch (this) {
      case OFF -> "Off";
      case MANUAL -> "Manual";
      case RAW_FILE_METADATA -> "Raw file metadata";
      case AUTO -> "Auto (saturation plateau)";
    };
  }
}
