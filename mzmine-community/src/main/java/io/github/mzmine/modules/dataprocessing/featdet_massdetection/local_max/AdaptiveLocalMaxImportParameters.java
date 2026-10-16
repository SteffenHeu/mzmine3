package io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max;

/**
 * {@link AdaptiveLocalMaxMassDetectorParameters} for the advanced import. The import processors only
 * see the spectral arrays without the raw data file, so the saturation threshold options that need
 * the raw file metadata or all mobility scans of the file are not offered.
 */
public class AdaptiveLocalMaxImportParameters extends AdaptiveLocalMaxMassDetectorParameters {

  public static final SaturationThresholdParameter importSaturationThreshold = saturationThreshold.withOptions(
      SaturationThresholdParameter.IMPORT_OPTIONS);

  public AdaptiveLocalMaxImportParameters() {
    super(importSaturationThreshold);
  }
}
