package io.github.mzmine.modules.io.import_rawdata_agilent_d;

import io.github.mzmine.gui.preferences.VendorImportParameters;
import io.github.mzmine.modules.io.import_rawdata_all.spectral_processor.ScanImportProcessorConfig;
import io.github.mzmine.util.MemoryMapStorage;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Opens an {@link AgilentRawDataAccess} for one file.
 */
@FunctionalInterface
public interface AgilentRawDataAccessFactory {

  @NotNull AgilentRawDataAccess open(@NotNull File rawFile,
      @NotNull VendorImportParameters vendorParameters, @Nullable MemoryMapStorage storage,
      @Nullable ScanImportProcessorConfig processor);
}
