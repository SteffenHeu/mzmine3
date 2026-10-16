package io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max;

import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.MassSpectrumType;
import io.github.mzmine.datamodel.MobilityScan;
import io.github.mzmine.datamodel.features.SimpleModularDataModel;
import io.github.mzmine.datamodel.features.rawfiletypes.MobilityScanAbundanceLimitType;
import io.github.mzmine.datamodel.features.rawfiletypes.MobilityScanAbundanceLimits;
import io.github.mzmine.datamodel.impl.SimpleMassSpectrum;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Saturation threshold of the {@link AdaptiveLocalMaxMassDetector}: only applied to mobility scans,
 * resolved from manual input or the raw file metadata, and the parameter with restricted options.
 */
class AdaptiveLocalMaxSaturationTest {

  private static final double CEILING = 50_000;
  private static final double LIMIT = 52_224;

  /**
   * Tailing profile peak (fronting Gaussian half width 0.008, tailing 0.016) of height 100k at m/z
   * 500, clipped at the detector ceiling with a small ripple (real clipped tops are not perfectly
   * flat) and zero end points like profile data.
   */
  private static @NotNull SimpleMassSpectrum clippedPeak() {
    final int n = 101;
    final double[] mzs = new double[n];
    final double[] intensities = new double[n];
    // TOF grid: equidistant in sqrt(m/z), ~0.002 spacing at m/z 500
    final double sqrtStart = Math.sqrt(499.9);
    final double sqrtStep = 0.002 / (2 * Math.sqrt(500));
    for (int i = 0; i < n; i++) {
      final double mz = Math.pow(sqrtStart + i * sqrtStep, 2);
      final double delta = mz - 500.0;
      final double width = delta < 0 ? 0.008 : 0.016;
      mzs[i] = mz;
      final double ceiling = CEILING - 25 * (i % 3);
      intensities[i] = Math.min(ceiling,
          100_000 * Math.exp(-0.5 * (delta / width) * (delta / width)));
    }
    intensities[0] = 0;
    intensities[n - 1] = 0;
    return new SimpleMassSpectrum(mzs, intensities, MassSpectrumType.PROFILE);
  }

  private static @NotNull AdaptiveLocalMaxMassDetector detector(
      @NotNull final SaturationThresholdValue saturation) {
    return new AdaptiveLocalMaxMassDetector(0, AbundanceMeasure.Height, 3,
        new LocalMaxNoSmoothingModule(), Double.POSITIVE_INFINITY, saturation);
  }

  private static double centroid(final double[][] values) {
    Assertions.assertEquals(1, values[0].length, "expected one centroid");
    return values[0][0];
  }

  @Test
  void manualThresholdOnlyAppliesToMobilityScans() {
    final SimpleMassSpectrum spectrum = clippedPeak();
    final AdaptiveLocalMaxMassDetector off = detector(SaturationThresholdValue.OFF);
    final AdaptiveLocalMaxMassDetector manual = detector(
        new SaturationThresholdValue(SaturationThresholdOption.MANUAL, 25_000));
    // same threshold as fixed constructor value, applies to all spectra
    final AdaptiveLocalMaxMassDetector fixed = new AdaptiveLocalMaxMassDetector(0,
        AbundanceMeasure.Height, 3, new LocalMaxNoSmoothingModule(), 25_000);

    final double offMz = centroid(off.getMobilityScanMassValues(List.of(spectrum)).getFirst());
    final double manualMz = centroid(
        manual.getMobilityScanMassValues(List.of(spectrum)).getFirst());

    Assertions.assertNotEquals(offMz, manualMz, 1e-9, "saturation branch was not applied");
    Assertions.assertEquals(centroid(fixed.getMassValues(spectrum)), manualMz, 1e-12);
    // frames and other spectra ignore the mobility scan threshold
    Assertions.assertEquals(centroid(off.getMassValues(spectrum)),
        centroid(manual.getMassValues(spectrum)), 1e-12);
    // the threshold is reset after the mobility scans
    manual.getMobilityScanMassValues(List.of(spectrum));
    Assertions.assertEquals(offMz, centroid(manual.getMassValues(spectrum)), 1e-12);
  }

  @Test
  void metadataThresholdIsHalfTheAbundanceLimitOfTheMsLevel() {
    final SimpleMassSpectrum spectrum = clippedPeak();
    final IMSRawDataFile file = Mockito.mock(IMSRawDataFile.class);
    final SimpleModularDataModel metadata = new SimpleModularDataModel();
    // MS1 limit unknown, MS2 limit known
    metadata.set(MobilityScanAbundanceLimitType.class,
        new MobilityScanAbundanceLimits(Double.NaN, LIMIT));
    Mockito.when(file.getFileMetadata()).thenReturn(metadata);

    final AdaptiveLocalMaxMassDetector fromMetadata = detector(
        new SaturationThresholdValue(SaturationThresholdOption.RAW_FILE_METADATA, 0));
    final AdaptiveLocalMaxMassDetector fixed = new AdaptiveLocalMaxMassDetector(0,
        AbundanceMeasure.Height, 3, new LocalMaxNoSmoothingModule(),
        AdaptiveLocalMaxMassDetector.SATURATION_LIMIT_FRACTION * LIMIT);
    final double unsaturatedMz = centroid(detector(SaturationThresholdValue.OFF).getMassValues(
        spectrum));

    Assertions.assertEquals(centroid(fixed.getMassValues(spectrum)), centroid(
            fromMetadata.getMobilityScanMassValues(mobilityScans(file, 2, spectrum)).getFirst()),
        1e-12);
    // no limit for MS1 -> no saturation handling
    Assertions.assertEquals(unsaturatedMz, centroid(
        fromMetadata.getMobilityScanMassValues(mobilityScans(file, 1, spectrum)).getFirst()),
        1e-12);
    // plain spectra without raw file (import) -> no saturation handling
    Assertions.assertEquals(unsaturatedMz,
        centroid(fromMetadata.getMobilityScanMassValues(List.of(spectrum)).getFirst()), 1e-12);
  }

  /**
   * Mobility scan mocks of one frame that delegate the data to the spectrum
   */
  private static @NotNull List<MobilityScan> mobilityScans(@NotNull final IMSRawDataFile file,
      final int msLevel, @NotNull final MassSpectrum spectrum) {
    final Frame frame = Mockito.mock(Frame.class);
    Mockito.when(frame.getMSLevel()).thenReturn(msLevel);
    final MobilityScan scan = Mockito.mock(MobilityScan.class);
    Mockito.when(scan.getDataFile()).thenReturn(file);
    Mockito.when(scan.getFrame()).thenReturn(frame);
    Mockito.when(scan.getNumberOfDataPoints()).thenReturn(spectrum.getNumberOfDataPoints());
    Mockito.when(scan.getMzValue(Mockito.anyInt()))
        .thenAnswer(inv -> spectrum.getMzValue(inv.getArgument(0)));
    Mockito.when(scan.getIntensityValue(Mockito.anyInt()))
        .thenAnswer(inv -> spectrum.getIntensityValue(inv.getArgument(0)));
    return List.of(scan);
  }

  @Test
  void plateauNeedsRepeatedMaximum() {
    final List<Double> values = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
      values.add(1000d + i);
    }
    // single highest point -> no plateau
    values.add(40_000d);
    Assertions.assertTrue(Double.isNaN(SaturationPlateauEstimator.estimatePlateau(
        values.stream().mapToDouble(Double::doubleValue).toArray())));

    // clipped points close to the ceiling
    for (int i = 0; i < SaturationPlateauEstimator.MIN_PLATEAU_SCANS; i++) {
      values.add(49_500d - i * 10);
    }
    Assertions.assertEquals(49_500d, SaturationPlateauEstimator.estimatePlateau(
        values.stream().mapToDouble(Double::doubleValue).toArray()));
  }

  @Test
  void restrictedOptionsSurviveCloneAndRejectHiddenValues() {
    final SaturationThresholdParameter full = new SaturationThresholdParameter();
    Assertions.assertEquals(SaturationThresholdParameter.ALL_OPTIONS, full.getOptions());

    final SaturationThresholdParameter restricted = full.withOptions(
        SaturationThresholdParameter.IMPORT_OPTIONS);
    Assertions.assertEquals(SaturationThresholdParameter.IMPORT_OPTIONS,
        restricted.cloneParameter().getOptions());

    restricted.setValue(
        new SaturationThresholdValue(SaturationThresholdOption.RAW_FILE_METADATA, 0));
    Assertions.assertEquals(SaturationThresholdOption.OFF, restricted.getValue().option());
    restricted.setValue(new SaturationThresholdValue(SaturationThresholdOption.MANUAL, 1234));
    Assertions.assertEquals(SaturationThresholdOption.MANUAL, restricted.getValue().option());
    Assertions.assertEquals(1234, restricted.cloneParameter().getValue().manual());
  }

  @Test
  void importParametersOnlyOfferImportOptions() {
    final AdaptiveLocalMaxImportParameters params = (AdaptiveLocalMaxImportParameters) new AdaptiveLocalMaxImportParameters().cloneParameterSet();
    Assertions.assertEquals(SaturationThresholdParameter.IMPORT_OPTIONS,
        params.getParameter(AdaptiveLocalMaxMassDetectorParameters.saturationThreshold)
            .getOptions());
    final AdaptiveLocalMaxMassDetectorParameters full = (AdaptiveLocalMaxMassDetectorParameters) new AdaptiveLocalMaxMassDetectorParameters().cloneParameterSet();
    Assertions.assertEquals(SaturationThresholdParameter.ALL_OPTIONS,
        full.getParameter(AdaptiveLocalMaxMassDetectorParameters.saturationThreshold)
            .getOptions());
  }

  @Test
  void abundanceLimitsSaveString() {
    final MobilityScanAbundanceLimits limits = new MobilityScanAbundanceLimits(LIMIT, Double.NaN);
    Assertions.assertEquals(limits, MobilityScanAbundanceLimits.parse(limits.toSaveString()));
    Assertions.assertEquals(LIMIT, limits.forMsLevel(1));
    Assertions.assertTrue(Double.isNaN(limits.forMsLevel(2)));
    Assertions.assertEquals(new MobilityScanAbundanceLimits(LIMIT, 1d), limits.withMsLevel(2, 1));
  }
}
