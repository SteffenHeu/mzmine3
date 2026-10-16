package io.github.mzmine.modules.io.import_rawdata_agilent_d;

import io.github.mzmine.datamodel.features.ModularDataModel;
import io.github.mzmine.datamodel.impl.SimpleFrame;
import io.github.mzmine.datamodel.impl.SimpleScan;
import io.github.mzmine.modules.dataprocessing.id_ccscalibration.CCSCalibration;
import io.github.mzmine.project.impl.IMSRawDataFileImpl;
import io.github.mzmine.project.impl.RawDataFileImpl;
import java.time.LocalDateTime;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Read access to one Agilent {@code .d} file for {@link AgilentImportTask}, implemented by the
 * native AgilentReader ({@link AgilentDataAccess}) and the mzio reader (mzmine pro). Frames are
 * 1-based (1..{@link #getFrameCount()}), scans 0-based (0..{@link #getScanCount()}-1).
 */
public interface AgilentRawDataAccess extends AutoCloseable {

  boolean isIms();

  int getFrameCount();

  long getScanCount();

  /**
   * Whether the file has any non-MRM MS scans to import as spectra.
   */
  boolean hasMsScans();

  /**
   * Whether the file has MRM/SRM transitions (imported as other time series, not spectra).
   */
  boolean hasMrm();

  @NotNull RawDataFileImpl createDataFile();

  @Nullable CCSCalibration getCcsCalibration();

  @Nullable LocalDateTime getAcqDate();

  void applyToFileMetadata(@NotNull ModularDataModel metadata);

  /**
   * @return the frame or null if filtered out by the scan selection
   */
  @Nullable SimpleFrame readFrame(@NotNull IMSRawDataFileImpl file, int frameId);

  /**
   * @return the scan or null if filtered out by the scan selection or an MRM reading
   */
  @Nullable SimpleScan readScan(@NotNull RawDataFileImpl file, int id);

  void readMrms(@NotNull RawDataFileImpl dataFile);

  void readAnalogChannels(@NotNull RawDataFileImpl dataFile);

  @Override
  void close();
}
