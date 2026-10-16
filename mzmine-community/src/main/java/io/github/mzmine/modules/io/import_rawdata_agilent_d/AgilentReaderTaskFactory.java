package io.github.mzmine.modules.io.import_rawdata_agilent_d;

import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataImportTask;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.modules.io.import_rawdata_all.spectral_processor.ScanImportProcessorConfig;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.util.MemoryMapStorage;
import java.io.File;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Creates the import task of one Agilent {@code .d} file for
 * {@link io.github.mzmine.gui.preferences.AgilentImportOptions#CUSTOM}. The implementation is
 * provided by mzmine pro and registered at startup with {@link #register(AgilentReaderTaskFactory)};
 * without it, {@link AgilentImportTaskDelegator} falls back to the native AgilentReader.
 */
@FunctionalInterface
public interface AgilentReaderTaskFactory {

  @NotNull RawDataImportTask create(@Nullable MemoryMapStorage storage,
      @NotNull Instant moduleCallDate, @NotNull File file,
      @NotNull Class<? extends MZmineModule> module, @NotNull ParameterSet parameters,
      @NotNull MZmineProject project, @Nullable ScanImportProcessorConfig processor);

  /**
   * Registers the factory of the mzio reader import, replacing a previous one.
   */
  static void register(@Nullable AgilentReaderTaskFactory factory) {
    Registry.FACTORY.set(factory);
  }

  /**
   * @return the registered factory or null if the mzio reader is not available
   */
  static @Nullable AgilentReaderTaskFactory registered() {
    return Registry.FACTORY.get();
  }

  final class Registry {

    private static final AtomicReference<AgilentReaderTaskFactory> FACTORY = new AtomicReference<>();

    private Registry() {
    }
  }
}
