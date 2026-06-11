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

package io.github.mzmine.modules.io.import_rawdata_agilent_d;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Range;
import io.github.mzmine.datamodel.MassSpectrumType;
import io.github.mzmine.datamodel.MobilityType;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.featuredata.OtherFeatureUtils;
import io.github.mzmine.datamodel.impl.BuildingMobilityScan;
import io.github.mzmine.datamodel.impl.DDAMsMsInfoImpl;
import io.github.mzmine.datamodel.impl.DIAImsMsMsInfoImpl;
import io.github.mzmine.datamodel.impl.SimpleFrame;
import io.github.mzmine.datamodel.impl.SimpleScan;
import io.github.mzmine.datamodel.impl.builders.SimpleBuildingScan;
import io.github.mzmine.datamodel.impl.masslist.ScanPointerMassList;
import io.github.mzmine.datamodel.msms.ActivationMethod;
import io.github.mzmine.datamodel.msms.DIAMsMsInfoImpl;
import io.github.mzmine.datamodel.msms.IonMobilityMsMsInfo;
import io.github.mzmine.datamodel.msms.MsMsInfo;
import io.github.mzmine.datamodel.otherdetectors.OtherDataFileImpl;
import io.github.mzmine.datamodel.otherdetectors.OtherFeature;
import io.github.mzmine.datamodel.otherdetectors.OtherFeatureImpl;
import io.github.mzmine.datamodel.otherdetectors.OtherTimeSeriesDataImpl;
import io.github.mzmine.datamodel.otherdetectors.SimpleOtherTimeSeries;
import io.github.mzmine.gui.preferences.VendorImportParameters;
import io.github.mzmine.modules.dataprocessing.id_ccscalibration.CCSCalibration;
import io.github.mzmine.modules.dataprocessing.id_ccscalibration.DriftTubeCCSCalibration;
import io.github.mzmine.modules.io.import_rawdata_all.spectral_processor.ScanImportProcessorConfig;
import io.github.mzmine.modules.io.import_rawdata_all.spectral_processor.SimpleSpectralArrays;
import io.github.mzmine.modules.io.import_rawdata_mzml.msdk.data.ChromatogramType;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.project.impl.IMSRawDataFileImpl;
import io.github.mzmine.project.impl.RawDataFileImpl;
import io.github.mzmine.util.MemoryMapStorage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Single touch point between mzmine and the AgilentBridge subprocess. Mirrors
 * {@code MassLynxDataAccess}: it owns the open file (here: the bridge process), exposes typed read
 * methods, and is {@link AutoCloseable}. The import task only calls these methods and never sees
 * the wire protocol.
 * <p>
 * Frame numbers are 1-based (1..{@link #getFrameCount()}); scans are 0-based
 * (0..{@link #getScanCount()}-1), matching the bridge.
 */
public class AgilentDataAccess implements AutoCloseable {

  private static final Logger logger = Logger.getLogger(AgilentDataAccess.class.getName());

  private final AgilentBridgeClient client;
  private final File rawFile;
  @Nullable
  private final MemoryMapStorage storage;
  @Nullable
  private final ScanImportProcessorConfig processor;

  // cached "open" response
  private final boolean isIms;
  private final boolean requestCentroid;
  private final long numScans;
  private final int numFrames;
  private final double[] driftTimes;
  private final boolean hasSingleFieldCcs;
  private final double tFix;
  private final double beta;
  private final boolean hasMrm;
  private final boolean hasMsScans;

  public AgilentDataAccess(@NotNull File rawFile, @NotNull VendorImportParameters vendorParam,
      @Nullable MemoryMapStorage storage, @Nullable ScanImportProcessorConfig processor) {
    this.rawFile = rawFile;
    this.storage = storage;
    this.processor = processor;
    this.requestCentroid = vendorParam.getValue(VendorImportParameters.applyVendorCentroiding);

    this.client = new AgilentBridgeClient();
    final JsonNode open = client.send(
        Map.of("op", "open", "path", rawFile.getAbsolutePath(), "requestCentroid",
            requestCentroid));

    this.isIms = open.path("isIms").asBoolean(false);
    this.numScans = open.path("numScans").asLong(0);
    this.numFrames = open.path("numFrames").asInt(0);
    this.driftTimes = toDoubleArray(open.get("driftTimes"));
    this.hasSingleFieldCcs = open.path("hasSingleFieldCcs").asBoolean(false);
    this.tFix = open.path("tFix").asDouble(Double.NaN);
    this.beta = open.path("beta").asDouble(Double.NaN);
    this.hasMrm = open.path("hasMrm").asBoolean(false);
    this.hasMsScans = open.path("hasMsScans").asBoolean(true);

    logger.finest(
        "AgilentBridge opened %s: ims=%b, frames=%d, scans=%d, requestCentroid=%b".formatted(
            rawFile.getName(), isIms, numFrames, numScans, requestCentroid));
  }

  public boolean isIms() {
    return isIms;
  }

  public int getFrameCount() {
    return numFrames;
  }

  public long getScanCount() {
    return numScans;
  }

  /**
   * Whether the file has any non-MRM MS scans to import as spectra.
   */
  public boolean hasMsScans() {
    return hasMsScans;
  }

  /**
   * Whether the file has MRM/SRM transitions (imported as OtherTimeSeries, not spectra).
   */
  public boolean hasMrm() {
    return hasMrm;
  }

  public @NotNull RawDataFileImpl createDataFile() {
    if (isIms) {
      return new IMSRawDataFileImpl(rawFile.getName(), rawFile.getAbsolutePath(), storage);
    }
    return new RawDataFileImpl(rawFile.getName(), rawFile.getAbsolutePath(), storage);
  }

  /**
   * Single-field drift-tube CCS calibration from the bridge, or null if unavailable.
   */
  public @Nullable CCSCalibration getCcsCalibration() {
    if (hasSingleFieldCcs && !Double.isNaN(beta) && !Double.isNaN(tFix)) {
      return new DriftTubeCCSCalibration(beta, tFix, -1, -1);
    }
    return null;
  }

  /**
   * Reads one IMS frame: the Total Frame Spectrum becomes the {@link SimpleFrame}, the per-bin
   * mobility scans its {@link BuildingMobilityScan}s. Returns null if filtered out by the scan
   * selection.
   */
  public @Nullable SimpleFrame readFrame(@NotNull IMSRawDataFileImpl file, int frameId) {
    // --- metadata (mtd) ---
    final JsonNode mtd = client.send(Map.of("op", "mtd", "id", frameId));
    final float rt = (float) mtd.path("rtMin").asDouble(0);
    final PolarityType polarity = parsePolarity(mtd.path("polarity").asText(null));
    // MS level comes from the frame's fragmentation class (All-Ions high-energy -> MS2)
    final int msLevel = mtd.path("msLevel").asInt(1);

    if (filteredOut(
        new SimpleBuildingScan(0, msLevel, polarity, MassSpectrumType.PROFILE, rt, 0, 0))) {
      // peak ops (tfs / mobilityScans) are never requested for a filtered frame
      return null;
    }

    // --- Total Frame Spectrum (peak data) -> the Frame ---
    final JsonNode tfs = client.send(Map.of("op", "tfs", "frameId", frameId));
    final int numPoints = tfs.path("numPoints").asInt(0);
    final boolean centroided = tfs.path("centroided").asBoolean(false);
    final double[] mzs = client.readBlob(numPoints);
    final double[] intensities = client.readBlob(numPoints);

    final MassSpectrumType type = spectrumType(centroided, msLevel);
    final var meta = new SimpleBuildingScan(0, msLevel, polarity, type, rt, 0, 0);
    final SimpleSpectralArrays frameData = applyProcessor(meta, mzs, intensities);
    final boolean massDetect = isMassDetectActive(msLevel);
    final Set<IonMobilityMsMsInfo> precursorInfos =
        msLevel > 1 ? Set.of(buildImsMsMsInfo(mtd)) : null;

    final SimpleFrame frame = new SimpleFrame(file, frameId, msLevel, rt, frameData.mzs(),
        frameData.intensities(), type, polarity, "frame=" + frameId, mzRange(frameData.mzs()),
        MobilityType.DRIFT_TUBE, precursorInfos, null);
    if (massDetect) {
      frame.addMassList(new ScanPointerMassList(frame));
    }

    // --- mobility scans for this frame ---
    final MassSpectrumType mobType = spectrumType(false, msLevel);
    final JsonNode mob = client.send(Map.of("op", "mobilityScans", "frameId", frameId));
    final int numMobScans = mob.path("numScans").asInt(0);
    final JsonNode pointsPerScan = mob.get("pointsPerScan");
    final List<BuildingMobilityScan> mobScans = new ArrayList<>(numMobScans);
    for (int i = 0; i < numMobScans; i++) {
      final int pts = pointsPerScan.get(i).asInt(0);
      final double[] mobMz = client.readBlob(pts);
      final double[] mobIntensity = client.readBlob(pts);
      final var mobMeta = new SimpleBuildingScan(0, msLevel, polarity, mobType, rt, 0, 0);
      final SimpleSpectralArrays data = applyProcessor(mobMeta, mobMz, mobIntensity);
      mobScans.add(new BuildingMobilityScan(i, data.mzs(), data.intensities(), mobType));
    }

    frame.setMobilityScans(mobScans, massDetect);
    frame.setMobilities(driftTimes);
    return frame;
  }

  /**
   * Reads one non-IMS scan, or null if filtered out by the scan selection.
   */
  public @Nullable SimpleScan readScan(@NotNull RawDataFileImpl file, int id) {
    // --- metadata (mtd) ---
    final JsonNode mtd = client.send(Map.of("op", "mtd", "id", id));

    // MRM/SRM scans are imported as OtherTimeSeries (see readMrms), never as spectra
    if ("MultipleReaction".equals(mtd.path("msScanType").asText(""))) {
      return null;
    }

    final int msLevel = mtd.path("msLevel").asInt(1);
    final float rt = (float) mtd.path("rtMin").asDouble(0);
    final PolarityType polarity = parsePolarity(mtd.path("polarity").asText(null));
    final double precursorMz = mtd.path("precursorMz").asDouble(0);

    if (filteredOut(new SimpleBuildingScan(0, msLevel, polarity, MassSpectrumType.PROFILE, rt,
        (float) precursorMz, 0))) {
      // the peak "scan" op is never requested for a filtered scan
      return null;
    }

    // --- peak data ---
    final JsonNode peak = client.send(Map.of("op", "scan", "id", id));
    final int numPoints = peak.path("numPoints").asInt(0);
    final boolean centroided = peak.path("centroided").asBoolean(false);
    final double[] mzs = client.readBlob(numPoints);
    final double[] intensities = client.readBlob(numPoints);

    final MassSpectrumType type = spectrumType(centroided, msLevel);
    final var meta = new SimpleBuildingScan(0, msLevel, polarity, type, rt, (float) precursorMz, 0);
    final SimpleSpectralArrays data = applyProcessor(meta, mzs, intensities);
    final MsMsInfo msMsInfo = buildScanMsMsInfo(mtd, msLevel);

    final SimpleScan scan = new SimpleScan(file, id, msLevel, rt, msMsInfo, data.mzs(),
        data.intensities(), type, polarity, "scan=" + id, mzRange(data.mzs()));
    if (isMassDetectActive(msLevel)) {
      scan.addMassList(new ScanPointerMassList(scan));
    }
    return scan;
  }

  /**
   * Reads the file's MRM/SRM transitions and attaches them to {@code dataFile} as one
   * {@link io.github.mzmine.modules.io.import_rawdata_mzml.msdk.data.ChromatogramType#MRM_SRM}
   * other-data file. No-op for non-MRM files. Mirrors the Waters MRM path.
   */
  public void readMrms(@NotNull RawDataFileImpl dataFile) {
    final JsonNode h = client.send(Map.of("op", "mrm"));
    final int count = h.path("count").asInt(0);
    final JsonNode transitions = h.get("transitions");

    final double[] q1 = new double[count];
    final double[] q3 = new double[count];
    final float[][] rts = new float[count][];
    final double[][] intensities = new double[count][];
    for (int i = 0; i < count; i++) {
      final JsonNode t = transitions.get(i);
      q1[i] = t.path("q1").asDouble(0);
      q3[i] = t.path("q3").asDouble(0);
      final int n = t.path("numPoints").asInt(0);
      rts[i] = toFloats(client.readBlob(n));
      intensities[i] = client.readBlob(n);
    }
    if (count == 0) {
      return;
    }

    final OtherDataFileImpl mrmFile = new OtherDataFileImpl(dataFile);
    mrmFile.setDescription("MRM/SRM");
    final OtherTimeSeriesDataImpl mrmData = new OtherTimeSeriesDataImpl(mrmFile);
    mrmData.setChromatogramType(ChromatogramType.MRM_SRM);
    mrmData.setTimeSeriesRangeLabel("Intensity");
    mrmData.setTimeSeriesRangeUnit("cts");
    mrmData.setTimeSeriesDomainLabel("RT");
    mrmData.setTimeSeriesDomainUnit("min");
    mrmFile.setOtherTimeSeriesData(mrmData);

    for (int i = 0; i < count; i++) {
      final SimpleOtherTimeSeries series = new SimpleOtherTimeSeries(storage, rts[i],
          intensities[i], "%.2f -> %.2f".formatted(q1[i], q3[i]), mrmData);
      final OtherFeature feature = new OtherFeatureImpl(series);
      OtherFeatureUtils.applyMrmInfo(q1[i], q3[i], ActivationMethod.CID, null, feature);
      mrmData.addRawTrace(feature);
    }
    if (mrmFile.hasTimeSeries()) {
      dataFile.addOtherDataFiles(List.of(mrmFile));
    }
  }

  /**
   * Reads non-MS detector signals (DAD/UV, instrument curves) and attaches them to
   * {@code dataFile}, grouped by unit into one other-data file each. Mirrors the Waters analog
   * channel path.
   */
  public void readAnalogChannels(@NotNull RawDataFileImpl dataFile) {
    final JsonNode h = client.send(Map.of("op", "analog"));
    final int count = h.path("count").asInt(0);
    final JsonNode channels = h.get("channels");

    final String[] names = new String[count];
    final String[] units = new String[count];
    final float[][] rts = new float[count][];
    final double[][] intensities = new double[count][];
    for (int i = 0; i < count; i++) {
      final JsonNode c = channels.get(i);
      names[i] = c.path("name").asText("");
      units[i] = c.path("unit").asText("");
      final int n = c.path("numPoints").asInt(0);
      rts[i] = toFloats(client.readBlob(n));
      intensities[i] = client.readBlob(n);
    }
    if (count == 0) {
      return;
    }

    final Map<String, OtherTimeSeriesDataImpl> unitToData = new HashMap<>();
    for (int i = 0; i < count; i++) {
      final OtherTimeSeriesDataImpl tsd = unitToData.computeIfAbsent(units[i], u -> {
        final OtherDataFileImpl otherFile = new OtherDataFileImpl(dataFile);
        final OtherTimeSeriesDataImpl data = new OtherTimeSeriesDataImpl(otherFile);
        otherFile.setOtherTimeSeriesData(data);
        otherFile.setDescription(u + "_Agilent_Analog");
        data.setTimeSeriesRangeUnit(u);
        return data;
      });
      final SimpleOtherTimeSeries trace = new SimpleOtherTimeSeries(storage, rts[i], intensities[i],
          names[i], tsd);
      tsd.addRawTrace(new OtherFeatureImpl(trace));
    }
    dataFile.addOtherDataFiles(
        unitToData.values().stream().map(OtherTimeSeriesDataImpl::getOtherDataFile).toList());
  }

  private static float[] toFloats(double[] values) {
    final float[] out = new float[values.length];
    for (int i = 0; i < values.length; i++) {
      out[i] = (float) values[i];
    }
    return out;
  }

  /**
   * Builds the ion-mobility MS/MS info for an MS2 frame. All-Ions (DIA) frames have no precursor
   * selection (full-range isolation window = null); DDA frames carry a precursor m/z + isolation
   * window. Mirrors the Waters IMS path, which uses {@link DIAImsMsMsInfoImpl} for all IMS MS/MS.
   */
  private IonMobilityMsMsInfo buildImsMsMsInfo(JsonNode mtd) {
    final float collisionEnergy = (float) mtd.path("collisionEnergy").asDouble(0);
    final Float ce = collisionEnergy > 0 ? collisionEnergy : null;

    Range<Double> isolationWindow = null;
    if (mtd.path("hasPrecursor").asBoolean(false)) {
      final double mz = mtd.path("precursorMz").asDouble(0);
      final double width = mtd.path("isolationWidth").asDouble(0);
      if (width > 0) {
        isolationWindow = Range.closed(mz - width / 2, mz + width / 2);
      }
    }
    final int numDriftBins = driftTimes.length;
    return new DIAImsMsMsInfoImpl(Range.closed(0, Math.max(0, numDriftBins - 1)), ce, null,
        isolationWindow);
  }

  /**
   * MS/MS info for a non-IMS scan. A scan with a precursor is targeted MS/MS (DDA); a scan without
   * one but at a non-zero collision energy is All-Ions / MSE (DIA), where the full m/z range is
   * fragmented.
   */
  private MsMsInfo buildScanMsMsInfo(JsonNode header, int msLevel) {
    if (msLevel <= 1) {
      return null;
    }
    if (header.path("hasPrecursor").asBoolean(false)) {
      return buildMsMsInfo(header, msLevel);
    }
    final float ce = (float) header.path("collisionEnergy").asDouble(0);
    return new DIAMsMsInfoImpl(ce > 0 ? ce : null, null, msLevel, ActivationMethod.CID, null);
  }

  private MsMsInfo buildMsMsInfo(JsonNode header, int msLevel) {
    final int charge = header.path("precursorCharge").asInt(0);
    final float ce = (float) header.path("collisionEnergy").asDouble(0);
    return new DDAMsMsInfoImpl(header.path("precursorMz").asDouble(0), charge > 0 ? charge : null,
        ce > 0 ? ce : null, null, null, msLevel, ActivationMethod.CID, isolationWindow(header));
  }

  /**
   * Quad isolation window from the bridge, or null when none / degenerate (zero width).
   */
  private static Range<Double> isolationWindow(JsonNode header) {
    final double low = header.path("isolationLowMz").asDouble(0);
    final double high = header.path("isolationHighMz").asDouble(0);
    return high > low ? Range.closed(low, high) : null;
  }

  private SimpleSpectralArrays applyProcessor(@NotNull SimpleBuildingScan meta, double[] mzs,
      double[] intensities) {
    final SimpleSpectralArrays arrays = new SimpleSpectralArrays(mzs, intensities);
    if (processor == null || !processor.hasProcessors()) {
      return arrays;
    }
    return processor.processor().processScan(meta, arrays);
  }

  private boolean filteredOut(@NotNull SimpleBuildingScan meta) {
    if (processor == null || !processor.hasProcessors() || processor.scanFilter() == null
        || !processor.scanFilter().isActiveFilter()) {
      return false;
    }
    final ScanSelection selection = processor.scanFilter();
    return !selection.matches(meta);
  }

  private boolean isMassDetectActive(int msLevel) {
    return processor != null && processor.isMassDetectActive(msLevel);
  }

  /**
   * Bridge data is profile for IMS and (when requested) centroided for non-IMS. The data becomes
   * centroided when mzmine mass detection runs during import.
   */
  private MassSpectrumType spectrumType(boolean centroided, int msLevel) {
    return centroided || isMassDetectActive(msLevel) ? MassSpectrumType.CENTROIDED
        : MassSpectrumType.PROFILE;
  }

  private static Range<Double> mzRange(double[] mzs) {
    return mzs.length > 0 ? Range.closed(mzs[0], mzs[mzs.length - 1]) : Range.closed(0d, 0d);
  }

  private static PolarityType parsePolarity(@Nullable String polarity) {
    if (polarity == null) {
      return PolarityType.UNKNOWN;
    }
    return switch (polarity) {
      case "Positive" -> PolarityType.POSITIVE;
      case "Negative" -> PolarityType.NEGATIVE;
      default -> PolarityType.UNKNOWN;
    };
  }

  private static double[] toDoubleArray(@Nullable JsonNode array) {
    if (array == null || !array.isArray()) {
      return new double[0];
    }
    final double[] out = new double[array.size()];
    for (int i = 0; i < out.length; i++) {
      out[i] = array.get(i).asDouble();
    }
    return out;
  }

  @Override
  public void close() {
    try {
      client.send(Map.of("op", "close"));
    } catch (RuntimeException e) {
      logger.finest("AgilentBridge close op failed (ignored): " + e.getMessage());
    }
    client.close();
  }
}
