/*
 * Copyright (c) 2004-2026 The mzmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.io.import_rawdata_shimadzu;

import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.RawDataImportTask;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.datamodel.otherdetectors.OtherDataFile;
import io.github.mzmine.datamodel.otherdetectors.OtherDataFileImpl;
import io.github.mzmine.datamodel.otherdetectors.OtherFeature;
import io.github.mzmine.datamodel.otherdetectors.OtherTimeSeriesDataImpl;
import io.github.mzmine.gui.preferences.VendorImportParameters;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.modules.io.import_rawdata_all.AllSpectralDataImportParameters;
import io.github.mzmine.modules.io.import_rawdata_all.spectral_processor.ScanImportProcessorConfig;
import io.github.mzmine.modules.io.import_rawdata_mzml.msdk.data.ChromatogramType;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.project.impl.RawDataFileImpl;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Imports a Shimadzu LabSolutions {@code .lcd} (LC-MS) or {@code .qgd} (GC-MS) file via the external
 * {@code ShimadzuBridge.exe} child process.
 * <p>
 * A file can independently hold mass spectra, MRM/SIM transitions, and non-MS detector channels, so
 * the three are imported through three separate passes driven by the capabilities the bridge reports
 * from {@code open}. An MRM-only file yields no spectra at all — its vendor "scans" are acquisition
 * cycles, and importing them as spectra would produce thousands of meaningless one-peak MS² scans.
 */
public class ShimadzuImportTask extends AbstractTask implements RawDataImportTask {

  private static final Logger logger = Logger.getLogger(ShimadzuImportTask.class.getName());

  private final File file;
  @NotNull
  private final Class<? extends MZmineModule> module;
  @NotNull
  private final ParameterSet parameters;
  @NotNull
  private final MZmineProject project;
  @NotNull
  private final ScanImportProcessorConfig processor;
  private final boolean centroid;

  private int totalItems = 0;
  private int loadedItems = 0;
  private RawDataFileImpl dataFile;

  public ShimadzuImportTask(@Nullable MemoryMapStorage storage, @NotNull Instant moduleCallDate,
      File file, @NotNull Class<? extends MZmineModule> module, @NotNull ParameterSet parameters,
      @NotNull MZmineProject project, @NotNull ScanImportProcessorConfig processor) {
    super(storage, moduleCallDate);
    this.file = file;
    this.module = module;
    this.parameters = parameters;
    this.project = project;
    this.processor = processor;
    VendorImportParameters vendorParam = parameters.getParameter(
        AllSpectralDataImportParameters.vendorOptions).getValue();
    centroid = vendorParam.getValue(VendorImportParameters.applyVendorCentroiding);
  }

  @Override
  public String getTaskDescription() {
    return "Importing Shimadzu raw data file %s. Item %d/%d".formatted(file.getName(), loadedItems,
        totalItems);
  }

  @Override
  public double getFinishedPercentage() {
    return totalItems == 0 ? 0d : loadedItems / (double) totalItems;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);

    try (ShimadzuDataAccess access = new ShimadzuDataAccess(file, storage)) {
      final ShimadzuCapabilities caps = access.capabilities();
      totalItems = caps.expectedSpectra() + caps.mrmTraceCount() + caps.analogTraceCount();

      if (!caps.hasMassSpectra() && !caps.hasMrmTraces() && !caps.hasAnalogTraces()) {
        // Nothing readable at all. Finish with an empty file rather than failing,
        // matching the other vendor importers, but make the reason visible.
        logger.warning(
            "Shimadzu file %s reports no mass spectra, no transitions and no analog channels (%d vendor scans)".formatted(
                file.getName(), caps.vendorScanCount()));
      }

      // The raw data file has to exist before any other-detector file can attach
      // to it, so create it even when the file holds only traces.
      dataFile = access.createDataFile();

      if (caps.hasMassSpectra()) {
        readScans(access, caps);
      } else if (caps.vendorScanCount() > 0) {
        logger.info(
            "Skipping the spectrum pass for %s: its %d vendor scans are %s acquisition cycles, imported as transitions instead".formatted(
                file.getName(), caps.vendorScanCount(), caps.mrmState()));
      }

      if (isCanceled()) {
        return;
      }

      final List<OtherDataFile> otherFiles = new ArrayList<>();
      if (caps.hasMrmTraces()) {
        addIfPresent(otherFiles, readMrmTraces(access));
      }
      if (isCanceled()) {
        return;
      }
      if (caps.hasAnalogTraces()) {
        otherFiles.addAll(readAnalogTraces(access));
      }
      if (!otherFiles.isEmpty()) {
        dataFile.addOtherDataFiles(otherFiles);
      }

      if (isCanceled()) {
        return;
      }

      final var appliedMethod = new SimpleFeatureListAppliedMethod(module, parameters,
          getModuleCallDate());
      dataFile.getAppliedMethods().add(appliedMethod);

      project.addFile(dataFile);
      setStatus(TaskStatus.FINISHED);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error reading Shimadzu file " + file.getAbsolutePath(), e);
      error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
  }

  // ---------------------------------------------------------------------------
  // Mass spectra
  // ---------------------------------------------------------------------------

  private void readScans(@NotNull ShimadzuDataAccess access, @NotNull ShimadzuCapabilities caps)
      throws IOException {
    final var result = access.readAllScans(dataFile, processor, !centroid, dataFile::addScan,
        () -> loadedItems++, this::isCanceled);

    if (result.skippedTargeted() > 0) {
      logger.info("Shimadzu %s: skipped %d MRM/SIM acquisition cycle(s) in the spectrum pass".formatted(
          file.getName(), result.skippedTargeted()));
    }
    if (result.skippedFailed() > 0) {
      logger.warning("Shimadzu %s: skipped %d scan(s) the SDK could not read".formatted(
          file.getName(), result.skippedFailed()));
    }
    if (caps.spectraRequireFiltering()) {
      logger.info(
          "Shimadzu %s mixes spectral and targeted events; imported %d spectra of %d vendor scans".formatted(
              file.getName(), result.imported(), caps.vendorScanCount()));
    }
  }

  // ---------------------------------------------------------------------------
  // MRM / SIM transitions
  // ---------------------------------------------------------------------------

  /**
   * Import every transition as one trace in a single MRM/SRM other-data file. Returns null when
   * nothing could be read.
   */
  private @Nullable OtherDataFile readMrmTraces(@NotNull ShimadzuDataAccess access)
      throws IOException {
    final var descriptors = access.listMrmTraces();
    if (descriptors.isEmpty()) {
      return null;
    }

    final OtherDataFileImpl otherFile = new OtherDataFileImpl(dataFile);
    otherFile.setDescription("MRM/SRM");
    final OtherTimeSeriesDataImpl data = new OtherTimeSeriesDataImpl(otherFile);
    data.setChromatogramType(ChromatogramType.MRM_SRM);
    data.setTimeSeriesDomainLabel("RT");
    data.setTimeSeriesDomainUnit("min");
    data.setTimeSeriesRangeLabel("Intensity");
    data.setTimeSeriesRangeUnit("a.u.");
    otherFile.setOtherTimeSeriesData(data);

    int imported = 0;
    for (var descriptor : descriptors) {
      if (isCanceled()) {
        return null;
      }
      final OtherFeature trace = access.readMrmTrace(descriptor, data);
      loadedItems++;
      if (trace != null) {
        data.addRawTrace(trace);
        imported++;
      }
    }

    if (imported == 0) {
      logger.warning("Shimadzu %s declared %d transitions but none could be read".formatted(
          file.getName(), descriptors.size()));
      return null;
    }
    logger.finest("Shimadzu %s: imported %d of %d transitions".formatted(file.getName(), imported,
        descriptors.size()));
    return otherFile;
  }

  // ---------------------------------------------------------------------------
  // Analog / non-MS channels
  // ---------------------------------------------------------------------------

  /**
   * Import analog channels grouped by unit, one other-data file per unit, mirroring how the Waters
   * importer groups them. Values are stored exactly as the SDK reported them; no unit conversion is
   * applied anywhere in this path.
   */
  private @NotNull List<OtherDataFile> readAnalogTraces(@NotNull ShimadzuDataAccess access)
      throws IOException {
    final var descriptors = access.listAnalogTraces();
    if (descriptors.isEmpty()) {
      return List.of();
    }

    // LinkedHashMap so the resulting other-files keep vendor enumeration order.
    final Map<String, OtherTimeSeriesDataImpl> byUnit = new LinkedHashMap<>();
    int imported = 0;

    for (var descriptor : descriptors) {
      if (isCanceled()) {
        return List.of();
      }

      final String unit = descriptor.rawUnit() != null ? descriptor.rawUnit() : "";
      final OtherTimeSeriesDataImpl data = byUnit.computeIfAbsent(unit,
          u -> createAnalogData(descriptor, u));

      final OtherFeature trace = access.readAnalogTrace(descriptor, data);
      loadedItems++;
      if (trace != null) {
        data.addRawTrace(trace);
        imported++;
      }
    }

    logger.finest("Shimadzu %s: imported %d of %d analog channel(s) in %d unit group(s)".formatted(
        file.getName(), imported, descriptors.size(), byUnit.size()));

    return byUnit.values().stream().map(OtherTimeSeriesDataImpl::getOtherDataFile)
        .filter(OtherDataFile::hasTimeSeries).map(OtherDataFile.class::cast).toList();
  }

  private @NotNull OtherTimeSeriesDataImpl createAnalogData(
      @NotNull ShimadzuTraceDescriptors.Analog descriptor, @NotNull String unit) {
    final OtherDataFileImpl otherFile = new OtherDataFileImpl(dataFile);
    final OtherTimeSeriesDataImpl data = new OtherTimeSeriesDataImpl(otherFile);
    otherFile.setOtherTimeSeriesData(data);
    otherFile.setDescription(
        unit.isEmpty() ? "Shimadzu analog" : "Shimadzu analog (%s)".formatted(unit));
    data.setChromatogramType(chromatogramType(descriptor.signalType()));
    data.setTimeSeriesDomainLabel("RT");
    data.setTimeSeriesDomainUnit("min");
    // The vendor's own unit string. The bridge applies no conversion, so this is
    // the unit the stored values are labelled with — see the wrapper README on
    // channels whose label and values disagree.
    data.setTimeSeriesRangeUnit(unit);
    data.setTimeSeriesRangeLabel(rangeLabel(descriptor.signalType()));
    return data;
  }

  /**
   * Map the bridge's normalized signal class onto mzmine's chromatogram types. Classes mzmine has no
   * equivalent for — temperature, pump composition — stay {@link ChromatogramType#UNKNOWN} rather
   * than being forced into a wrong one; the description and unit still carry the vendor's meaning.
   */
  private static @NotNull ChromatogramType chromatogramType(@NotNull String signalType) {
    return switch (signalType) {
      case "PRESSURE" -> ChromatogramType.PRESSURE;
      case "FLOW" -> ChromatogramType.FLOW_RATE;
      case "ABSORBANCE" -> ChromatogramType.ABSORPTION;
      case "FLUORESCENCE" -> ChromatogramType.EMISSION;
      default -> ChromatogramType.UNKNOWN;
    };
  }

  private static @NotNull String rangeLabel(@NotNull String signalType) {
    return switch (signalType) {
      case "PRESSURE" -> "Pressure";
      case "FLOW" -> "Flow rate";
      case "ABSORBANCE" -> "Absorbance";
      case "FLUORESCENCE" -> "Intensity";
      case "TEMPERATURE" -> "Temperature";
      case "COMPOSITION" -> "Composition";
      case "VOLTAGE" -> "Signal";
      default -> "Value";
    };
  }

  private static void addIfPresent(@NotNull List<OtherDataFile> sink, @Nullable OtherDataFile f) {
    if (f != null) {
      sink.add(f);
    }
  }

  @Override
  public @NotNull List<RawDataFile> getImportedRawDataFiles() {
    return isFinished() && dataFile != null ? List.of(dataFile) : List.of();
  }
}
