package import_data.agilent_centroiding;

import io.github.mzmine.gui.preferences.AgilentCentroidingOption;
import io.github.mzmine.gui.preferences.AgilentImportOptions;
import io.github.mzmine.gui.preferences.MassLynxImportOptions;
import io.github.mzmine.gui.preferences.VendorImportParameters;
import io.github.mzmine.gui.preferences.WatersLockmassParameters;
import io.github.mzmine.modules.io.import_rawdata_agilent_d.AgilentDataAccess;
import io.github.mzmine.parameters.parametertypes.combowithinput.ComboWithComboInputValue;
import java.io.File;
import org.jetbrains.annotations.NotNull;

/**
 * Shared settings for the Agilent IMS centroiding benchmark. The test data lives outside the
 * repository; the root folder can be changed with the system property
 * {@code mzmine.test.agilent.centroiding.dir} (forwarded by gradle like all
 * {@code mzmine.test.*} properties).
 */
public final class AgilentCentroidingTestConfig {

  public static final String ROOT_DIR_PROPERTY = "mzmine.test.agilent.centroiding.dir";
  public static final String REGENERATE_INDEX_PROPERTY = "mzmine.test.agilent.centroiding.regenerateIndex";
  public static final String DEFAULT_ROOT_DIR = "E:/agilent test";

  public static final String INDEX_FILE_NAME = "target_index.csv";
  public static final String RESULTS_DIR_NAME = "centroiding_results";

  /**
   * Tolerance to find a target in the centroided data while building the index.
   */
  public static final double INDEX_SEARCH_PPM = 20;
  /**
   * Window to match a detected centroid to the exact target m/z during the evaluation. Wide enough
   * to catch biased centroids of saturated peaks, narrow enough to avoid neighboring ions.
   */
  public static final double EVALUATION_SEARCH_PPM = 50;
  /**
   * A target is only indexed if its monoisotopic peak in the best frame reaches this intensity.
   */
  public static final double MIN_FRAME_INTENSITY = 1_000;
  /**
   * A target is labeled as potentially saturated if the observed M+1/M ratio exceeds the expected
   * ratio by more than this relative tolerance. A lower ratio does not indicate saturation.
   */
  public static final double RATIO_TOLERANCE = 0.15;
  /**
   * Merge width for the predicted isotope pattern so that the unresolved M+1 fine structure
   * (13C, 15N, 2H, 17O, 33S) is summed like on the TOF detector.
   */
  public static final double ISOTOPE_MERGE_WIDTH = 0.05;

  private AgilentCentroidingTestConfig() {
  }

  public static @NotNull File rootDir() {
    return new File(System.getProperty(ROOT_DIR_PROPERTY, DEFAULT_ROOT_DIR));
  }

  public static boolean regenerateIndex() {
    return Boolean.parseBoolean(System.getProperty(REGENERATE_INDEX_PROPERTY, "false"));
  }

  /**
   * Centroid import used for indexing: vendor centroided total frame spectra and mzmine
   * auto-centroided mobility scans.
   */
  public static @NotNull AgilentDataAccess openCentroided(@NotNull File file) {
    return new AgilentDataAccess(file,
        vendorParams(true, AgilentImportOptions.AGILENT_READER_AUTO_CENTROID), null, null);
  }

  /**
   * Profile import used for the mass detector benchmark: profile total frame spectra and profile
   * mobility scans.
   */
  public static @NotNull AgilentDataAccess openProfile(@NotNull File file) {
    return new AgilentDataAccess(file, vendorParams(false, AgilentImportOptions.AGILENT_READER),
        null, null);
  }

  /**
   * Agilent centroiding of the total frame spectra; mobility scans stay profile.
   */
  public static @NotNull AgilentDataAccess openVendorCentroidedFrames(@NotNull File file) {
    return new AgilentDataAccess(file, vendorParams(true, AgilentImportOptions.AGILENT_READER),
        null, null);
  }

  private static @NotNull VendorImportParameters vendorParams(final boolean vendorCentroiding,
      @NotNull final AgilentImportOptions importOption) {
    return VendorImportParameters.create(vendorCentroiding,
        MassLynxImportOptions.NATIVE_MZMINE_CENTROIDING, true,
        WatersLockmassParameters.createDefault(), true,
        new ComboWithComboInputValue<>(importOption, AgilentCentroidingOption.PREFER_STORED),
        VendorImportParameters.DEFAULT_PRESSURE_COMPENSATION);
  }

  public static double ppmToDa(final double mz, final double ppm) {
    return mz * ppm / 1e6;
  }

  public static double ppmError(final double measured, final double exact) {
    return (measured - exact) / exact * 1e6;
  }
}
