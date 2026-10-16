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
import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.datamodel.MassSpectrumType;
import io.github.mzmine.datamodel.MobilityType;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.featuredata.OtherFeatureUtils;
import io.github.mzmine.datamodel.features.ModularDataModel;
import io.github.mzmine.datamodel.features.rawfiletypes.AcquisitionSoftwareType;
import io.github.mzmine.datamodel.features.rawfiletypes.AcquisitionSoftwareVersionType;
import io.github.mzmine.datamodel.features.rawfiletypes.InstrumentModelType;
import io.github.mzmine.datamodel.features.rawfiletypes.InstrumentSerialNumberType;
import io.github.mzmine.datamodel.features.rawfiletypes.InstrumentVendorType;
import io.github.mzmine.datamodel.features.rawfiletypes.IonSourcesType;
import io.github.mzmine.datamodel.features.rawfiletypes.MassAnalyzersType;
import io.github.mzmine.datamodel.features.rawfiletypes.OperatorNameType;
import io.github.mzmine.datamodel.features.rawfiletypes.SampleDescriptionType;
import io.github.mzmine.datamodel.features.rawfiletypes.SampleNameType;
import io.github.mzmine.datamodel.features.types.abstr.StringListType;
import io.github.mzmine.datamodel.features.types.abstr.StringType;
import io.github.mzmine.datamodel.features.types.annotations.AcquisitionMethodType;
import io.github.mzmine.datamodel.features.types.otherdectectors.ChromatogramTypeType;
import io.github.mzmine.datamodel.features.types.otherdectectors.WavelengthType;
import io.github.mzmine.datamodel.impl.BuildingMobilityScan;
import io.github.mzmine.datamodel.impl.DDAMsMsInfoImpl;
import io.github.mzmine.datamodel.impl.DIAImsMsMsInfoImpl;
import io.github.mzmine.datamodel.impl.SimpleFrame;
import io.github.mzmine.datamodel.impl.SimpleMassSpectrum;
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
import io.github.mzmine.gui.preferences.AgilentCentroidingOption;
import io.github.mzmine.gui.preferences.AgilentImportOptions;
import io.github.mzmine.gui.preferences.VendorImportParameters;
import io.github.mzmine.parameters.parametertypes.combowithinput.ComboWithComboInputValue;
import io.github.mzmine.datamodel.features.rawfiletypes.MobilityScanAbundanceLimitType;
import io.github.mzmine.datamodel.features.rawfiletypes.MobilityScanAbundanceLimits;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max.AdaptiveLocalMaxMassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max.LocalMaxGaussianModule;
import io.github.mzmine.modules.dataprocessing.id_ccscalibration.CCSCalibration;
import io.github.mzmine.modules.dataprocessing.id_ccscalibration.DriftTubeCCSCalibration;
import io.github.mzmine.modules.io.import_rawdata_all.spectral_processor.ScanImportProcessorConfig;
import io.github.mzmine.modules.io.import_rawdata_all.spectral_processor.SimpleSpectralArrays;
import io.github.mzmine.modules.io.import_rawdata_mzml.msdk.data.ChromatogramType;
import io.github.mzmine.modules.io.import_rawdata_mzml.msdk.data.MzMLUnits;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.project.impl.IMSRawDataFileImpl;
import io.github.mzmine.project.impl.RawDataFileImpl;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.date.DateTimeUtils;
import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.concurrent.NotThreadSafe;
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
@NotThreadSafe
public class AgilentDataAccess implements AutoCloseable {

  private static final Logger logger = Logger.getLogger(AgilentDataAccess.class.getName());
  private static final Pattern wavelengthPattern = Pattern.compile("Sig=([0-9]+[,\\.]?[0-9]+)");

  private final AgilentReaderClient client;
  private final File rawFile;
  @Nullable
  private final MemoryMapStorage storage;
  @Nullable
  private final ScanImportProcessorConfig processor;

  /**
   * if import option is {@link AgilentImportOptions#AGILENT_READER_AUTO_CENTROID} and
   * {@link AgilentDataAccess#requestCentroid}, centroiding will be applied using the
   * {@code frameCentroider} and the {@link #mobilityScanCentroiders}
   */
  private final AgilentImportOptions importOption;
  // decision: adaptive local maximum (benchmarked on Agilent 6560 IMS data), same smoothing as the
  // previous local maximum centroider
  /**
   * Centroids frames (total frame spectra) and non-IMS scans. No saturation handling, the abundance
   * limit only applies to mobility scans.
   */
  private final AdaptiveLocalMaxMassDetector frameCentroider = new AdaptiveLocalMaxMassDetector(0,
      AbundanceMeasure.Height, 3, new LocalMaxGaussianModule(7));
  /**
   * Centroids mobility scans per ms level, with the saturation threshold derived from the mobility
   * scan abundance limit of the first frame of this ms level.
   */
  private final Map<Integer, AdaptiveLocalMaxMassDetector> mobilityScanCentroiders = new HashMap<>();

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
  // cached "fileMetadata" response
  private @Nullable JsonNode fileMetadata;

  public AgilentDataAccess(@NotNull File rawFile, @NotNull VendorImportParameters vendorParam,
      @Nullable MemoryMapStorage storage, @Nullable ScanImportProcessorConfig processor) {
    this.rawFile = rawFile;
    this.storage = storage;
    this.processor = processor;
    this.requestCentroid = vendorParam.getValue(VendorImportParameters.applyVendorCentroiding);
    final ComboWithComboInputValue<AgilentImportOptions, AgilentCentroidingOption> agilentSettings = vendorParam.getValue(
        VendorImportParameters.agilentImportChoice);
    this.importOption = agilentSettings.getSelectedOption();

    // The general profile-vs-centroid decision stays with "Try vendor centroiding": when off we
    // request "none" (profile); when on, the centroid source selects stored vs. recentroided.
    final AgilentCentroidingOption centroiding = agilentSettings.getEmbeddedValue() != null
        ? agilentSettings.getEmbeddedValue() : AgilentCentroidingOption.PREFER_STORED;
    final String centroidMode = requestCentroid ? centroiding.getWireCentroidMode() : "none";

    this.client = new AgilentReaderClient();
    final JsonNode open = client.send(
        Map.of("op", "open", "path", rawFile.getAbsolutePath(), "centroidMode", centroidMode));

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
        "AgilentReader opened %s: ims=%b, frames=%d, scans=%d, requestCentroid=%b".formatted(
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
   * @return the file level metadata from the bridge, requested once and cached
   */
  private @NotNull JsonNode fileMetadata() {
    if (fileMetadata == null) {
      fileMetadata = client.send(Map.of("op", "fileMetadata"));
    }
    return fileMetadata;
  }

  /**
   * @return the acquisition date and time, null if not available. Recent files store UTC, which is
   * converted by {@link DateTimeUtils#parse(String)}, old files an unzoned local time.
   */
  public @Nullable LocalDateTime getAcqDate() {
    final String date = text(fileMetadata(), "acquisitionDateTime");
    if (date == null) {
      return null;
    }
    try {
      return DateTimeUtils.parse(date);
    } catch (RuntimeException e) {
      logger.fine("Cannot parse acquisition date '%s' of file %s".formatted(date,
          rawFile.getName()));
      return null;
    }
  }

  /**
   * Sets the file level metadata of the bridge (sample, operator, instrument, acquisition) to the
   * raw file metadata. Mirrors {@code MassLynxDataAccess#applyToFileMetadata}.
   */
  public void applyToFileMetadata(@NotNull final ModularDataModel metadata) {
    final JsonNode node = fileMetadata();
    // decision: the sample ID is the sample name if no name was entered
    final String sampleName = text(node, "sampleName");
    setText(metadata, SampleNameType.class,
        sampleName != null ? sampleName : text(node, "sampleId"));
    setText(metadata, SampleDescriptionType.class, text(node, "sampleDescription"));
    setText(metadata, OperatorNameType.class, text(node, "operatorName"));
    setText(metadata, InstrumentModelType.class, text(node, "instrumentModel"));
    setText(metadata, InstrumentSerialNumberType.class, text(node, "instrumentSerialNumber"));
    setText(metadata, InstrumentVendorType.class, text(node, "instrumentVendor"));
    setText(metadata, AcquisitionSoftwareType.class, text(node, "acquisitionSoftware"));
    setText(metadata, AcquisitionSoftwareVersionType.class,
        text(node, "acquisitionSoftwareVersion"));
    setText(metadata, AcquisitionMethodType.class, text(node, "acquisitionMethod"));
    setList(metadata, IonSourcesType.class, node.get("ionSources"));
    setList(metadata, MassAnalyzersType.class, node.get("massAnalyzers"));
  }

  private static @Nullable String text(@NotNull JsonNode node, @NotNull String field) {
    final JsonNode value = node.get(field);
    if (value == null || !value.isTextual() || value.asText().isBlank()) {
      return null;
    }
    return value.asText().strip();
  }

  private static void setText(@NotNull ModularDataModel metadata,
      @NotNull Class<? extends StringType> type, @Nullable String value) {
    if (value != null) {
      metadata.set(type, value);
    }
  }

  private static void setList(@NotNull ModularDataModel metadata,
      @NotNull Class<? extends StringListType> type, @Nullable JsonNode array) {
    if (array == null || !array.isArray() || array.isEmpty()) {
      return;
    }
    final List<String> values = new ArrayList<>(array.size());
    for (final JsonNode value : array) {
      if (value.isTextual() && !value.asText().isBlank()) {
        values.add(value.asText().strip());
      }
    }
    if (!values.isEmpty()) {
      metadata.set(type, List.copyOf(values));
    }
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
    // TOF transients summed per mobility scan and the max possible abundance of a mobility scan
    // point (detector saturation level, not for the TFS); 0 / NaN when not stored
    final int mobilityScanTransients = mtd.path("mobilityScanTransients").asInt(0);
    final double mobilityScanAbundLimit = abundanceLimit(mtd, "mobilityScanAbundLimit");

    if (filteredOut(
        new SimpleBuildingScan(0, msLevel, polarity, MassSpectrumType.PROFILE, rt, 0, 0))) {
      // peak ops (tfs / mobilityScans) are never requested for a filtered frame
      return null;
    }

    // --- Total Frame Spectrum (peak data) -> the Frame ---
    final JsonNode tfs = client.send(Map.of("op", "tfs", "frameId", frameId));
    final int numPoints = tfs.path("numPoints").asInt(0);
    boolean centroided = tfs.path("centroided").asBoolean(false);
    double[] mzs = client.readBlob(numPoints);
    double[] intensities = client.readBlob(numPoints);

    final boolean mzmineCentroiding =
        requestCentroid && importOption == AgilentImportOptions.AGILENT_READER_AUTO_CENTROID;
    if (!centroided && mzmineCentroiding) {
      final SimpleMassSpectrum preCentroid = new SimpleMassSpectrum(mzs, intensities,
          MassSpectrumType.PROFILE);
      final double[][] centroidedSpectrum = frameCentroider.getMassValues(preCentroid);
      mzs = centroidedSpectrum[0];
      intensities = centroidedSpectrum[1];
      centroided = true;
    }

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

    // the limit of the first frame per ms level is stored in the raw file metadata
    final double fileAbundLimit = updateMobilityScanAbundanceLimit(file, msLevel,
        mobilityScanAbundLimit);

    // --- mobility scans for this frame --- mobility scans are always profile if they come from the API.
    final MassSpectrumType mobType = spectrumType(mzmineCentroiding, msLevel);
    final JsonNode mob = client.send(Map.of("op", "mobilityScans", "frameId", frameId));
    final int numMobScans = mob.path("numScans").asInt(0);
    final JsonNode pointsPerScan = mob.get("pointsPerScan");
    final List<SimpleSpectralArrays> mobilityScanData = new ArrayList<>(numMobScans);
    double maxMobilityScanIntensity = 0;
    for (int i = 0; i < numMobScans; i++) {
      final int pts = pointsPerScan.get(i).asInt(0);
      final double[] mobMz = client.readBlob(pts);
      final double[] mobIntensity = client.readBlob(pts);
      for (final double intensity : mobIntensity) {
        maxMobilityScanIntensity = Math.max(maxMobilityScanIntensity, intensity);
      }
      mobilityScanData.add(new SimpleSpectralArrays(mobMz, mobIntensity));
    }

    if (!Double.isNaN(fileAbundLimit) && maxMobilityScanIntensity > fileAbundLimit) {
      // TODO separate PR: warn and disable the saturation handling for this file. The metadata does
      //  not match the data, e.g., PNNL PreProcessor output keeps the transients and abundance
      //  limit of the raw file although the intensities were summed.
    }

    if (mzmineCentroiding) {
      final List<SimpleMassSpectrum> profiles = new ArrayList<>(numMobScans);
      for (final SimpleSpectralArrays data : mobilityScanData) {
        profiles.add(
            new SimpleMassSpectrum(data.mzs(), data.intensities(), MassSpectrumType.PROFILE));
      }
      // all mobility scans of the frame at once
      final List<double[][]> centroidedScans = mobilityScanCentroider(msLevel,
          fileAbundLimit).getMobilityScanMassValues(profiles);
      mobilityScanData.clear();
      for (final double[][] values : centroidedScans) {
        mobilityScanData.add(new SimpleSpectralArrays(values[0], values[1]));
      }
    }

    final var mobMeta = new SimpleBuildingScan(0, msLevel, polarity, mobType, rt, 0, 0);
    final List<SimpleSpectralArrays> processed = applyMobilityScanProcessor(mobMeta,
        mobilityScanData);
    final List<BuildingMobilityScan> mobScans = new ArrayList<>(processed.size());
    for (int i = 0; i < processed.size(); i++) {
      final SimpleSpectralArrays data = processed.get(i);
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
    // max possible abundance of a scan point (detector saturation level); NaN when not stored.
    // MHDAC has no transient count for non-IMS files.
    final double abundLimit = abundanceLimit(mtd, "abundLimit");

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
   * {@link ChromatogramType#MRM_SRM} other-data file. No-op for non-MRM files.
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
   * {@code dataFile}, grouped by unit into one other-data file each.
   */
  public void readAnalogChannels(@NotNull RawDataFileImpl dataFile) {

    final JsonNode h = client.send(Map.of("op", "analog"));
    final int count = h.path("count").asInt(0);
    final JsonNode channels = h.get("channels");

    final String[] names = new String[count];
    final String[] units = new String[count];
    final String[] deviceTypes = new String[count];
    final String[] channelDescriptions = new String[count];
    final float[][] rts = new float[count][];
    final double[][] intensities = new double[count][];
    for (int i = 0; i < count; i++) {
      final JsonNode c = channels.get(i);
      names[i] = c.path("name").asText("");
      units[i] = c.path("unit").asText("");
      deviceTypes[i] = c.path("deviceType").asText("");
      channelDescriptions[i] = c.path("signalDesc").asText("");
      final int n = c.path("numPoints").asInt(0);
      rts[i] = toFloats(client.readBlob(n));
      intensities[i] = client.readBlob(n);
    }
    if (count == 0) {
      return;
    }

    final Map<DeviceWithUnit, OtherTimeSeriesDataImpl> unitToData = new HashMap<>();
    for (int i = 0; i < count; i++) {
      final String channelDescription = channelDescriptions[i];

      final AnalogDevice device = AnalogDevice.fromTitle(deviceTypes[i]);
      AnalogUnit unit = AnalogUnit.fromDescription(channelDescription);
      if (device == null) {
        continue;
      }
      if (unit == AnalogUnit.UNKNOWN) {
        unit = AnalogUnit.fromDevice(device);
      }

      final OtherTimeSeriesDataImpl tsd = unitToData.computeIfAbsent(
          new DeviceWithUnit(device, unit), dwu -> {
            final OtherDataFileImpl otherFile = new OtherDataFileImpl(dataFile);
            final OtherTimeSeriesDataImpl data = new OtherTimeSeriesDataImpl(otherFile);
            otherFile.setOtherTimeSeriesData(data);
            otherFile.setDescription(device.title + " " + dwu.unit.description);
            data.setTimeSeriesRangeUnit(dwu.unit.unit.getSign());
            data.setTimeSeriesRangeLabel(dwu.unit.unit.getLabel());
            data.setChromatogramType(dwu.unit.type);
            return data;
          });

      if (unit.getMapper() != null) {
        final DoubleUnaryOperator mapper = unit.getMapper();
        intensities[i] = Arrays.stream(intensities[i]).map(mapper).toArray();
      }
      final SimpleOtherTimeSeries trace = new SimpleOtherTimeSeries(storage, rts[i], intensities[i],
          channelDescriptions[i], tsd);

      OtherFeatureImpl feature = new OtherFeatureImpl(trace);
      feature.set(ChromatogramTypeType.class, unit.type);
      setWavelength(unit, wavelengthPattern, channelDescription, feature);

      tsd.addRawTrace(feature);
    }
    dataFile.addOtherDataFiles(
        unitToData.values().stream().map(OtherTimeSeriesDataImpl::getOtherDataFile).toList());
  }

  private static void setWavelength(@NotNull AnalogUnit unit, @NotNull Pattern wavelengthPattern,
      @NotNull String channelDescription, @NotNull OtherFeatureImpl feature) {
    if (unit.type != ChromatogramType.ABSORPTION) {
      return;
    }
    Matcher matcher = wavelengthPattern.matcher(channelDescription);
    if (!matcher.find()) {
      return;
    }

    final String strWavelength = matcher.group(1).replace(',', '.');
    try {
      final double v = Double.parseDouble(strWavelength);
      feature.set(WavelengthType.class, v);
    } catch (NumberFormatException e) {
      // silent
    }
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
    return new DIAMsMsInfoImpl(ce > 0 ? ce : null, null, msLevel, activationMethod(header), null);
  }

  private MsMsInfo buildMsMsInfo(JsonNode header, int msLevel) {
    final int charge = precursorCharge(header);
    final float ce = (float) header.path("collisionEnergy").asDouble(0);
    return new DDAMsMsInfoImpl(header.path("precursorMz").asDouble(0), charge > 0 ? charge : null,
        ce > 0 ? ce : null, null, null, msLevel, activationMethod(header), isolationWindow(header));
  }

  /**
   * Precursor charge: the spectrum-derived charge if present, else the low-level scan record's
   * charge state. 0 when neither is set (typical for non-DDA acquisitions).
   */
  private static int precursorCharge(JsonNode header) {
    final int charge = header.path("precursorCharge").asInt(0);
    return charge > 0 ? charge : header.path("chargeState").asInt(0);
  }

  /**
   * Dissociation/activation method from the bridge's low-level scan record. Agilent reports
   * {@code Unspecified} for the default workflow, which we treat as CID (its usual mode); only an
   * explicit {@code ETD} flag changes the mapping.
   */
  private static ActivationMethod activationMethod(JsonNode header) {
    final String mode = header.path("fragmentationMode").asText(null);
    if (mode == null) {
      return ActivationMethod.CID;
    }
    return switch (mode) {
      case "ETD" -> ActivationMethod.ETD;
      default -> ActivationMethod.CID; // CID / Unspecified
    };
  }

  /**
   * Quad isolation window from the bridge: the explicit MZOfInterest range when present, else the
   * precursor m/z widened by the configured quad isolation width. Null when neither is available.
   */
  private static Range<Double> isolationWindow(JsonNode header) {
    final double low = header.path("isolationLowMz").asDouble(0);
    final double high = header.path("isolationHighMz").asDouble(0);
    if (high > low) {
      return Range.closed(low, high);
    }
    final double mz = header.path("precursorMz").asDouble(0);
    final double width = header.path("isolationWidth").asDouble(0);
    return mz > 0 && width > 0 ? Range.closed(mz - width / 2, mz + width / 2) : null;
  }

  /**
   * Stores the mobility scan abundance limit of this ms level in the raw file metadata if it is not
   * set yet.
   *
   * @return the limit of the file for this ms level (from the first frame of the ms level), NaN if
   * unknown
   */
  private static double updateMobilityScanAbundanceLimit(@NotNull final IMSRawDataFileImpl file,
      final int msLevel, final double frameLimit) {
    final MobilityScanAbundanceLimits limits = Objects.requireNonNullElse(
        file.getFileMetadata().get(MobilityScanAbundanceLimitType.class),
        MobilityScanAbundanceLimits.UNKNOWN);
    final double fileLimit = limits.forMsLevel(msLevel);
    if (Double.isNaN(fileLimit)) {
      if (!Double.isNaN(frameLimit)) {
        file.getFileMetadata().set(MobilityScanAbundanceLimitType.class,
            limits.withMsLevel(msLevel, frameLimit));
      }
      return frameLimit;
    }
    if (!Double.isNaN(frameLimit) && Double.compare(fileLimit, frameLimit) != 0) {
      // TODO separate PR: warn that the abundance limit changes within the frames of this ms level,
      //  the limit (and saturation threshold) of the first frame is used.
    }
    return fileLimit;
  }

  /**
   * @param abundanceLimit mobility scan abundance limit of the file for this ms level, NaN if
   *                       unknown
   * @return the centroider of this ms level, with the saturation threshold derived from the
   * abundance limit of the first frame of this ms level
   */
  private @NotNull AdaptiveLocalMaxMassDetector mobilityScanCentroider(final int msLevel,
      final double abundanceLimit) {
    return mobilityScanCentroiders.computeIfAbsent(msLevel, level -> {
      final double threshold = Double.isNaN(abundanceLimit) ? Double.POSITIVE_INFINITY
          : AdaptiveLocalMaxMassDetector.SATURATION_LIMIT_FRACTION * abundanceLimit;
      logger.finest("Mobility scan saturation threshold of %s MS%d: %.0f".formatted(
          rawFile.getName(), level, threshold));
      return new AdaptiveLocalMaxMassDetector(0, AbundanceMeasure.Height, 3,
          new LocalMaxGaussianModule(7), threshold);
    });
  }

  /**
   * Processes all mobility scans of a frame together, see
   * {@link io.github.mzmine.modules.io.import_rawdata_all.spectral_processor.MsProcessor#processMobilityScans}.
   */
  private @NotNull List<SimpleSpectralArrays> applyMobilityScanProcessor(
      @NotNull final SimpleBuildingScan meta,
      @NotNull final List<SimpleSpectralArrays> mobilityScans) {
    if (processor == null || !processor.hasProcessors()) {
      return mobilityScans;
    }
    return processor.processor().processMobilityScans(meta, mobilityScans);
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

  /**
   * Abundance limit from the bridge mtd. The SDK reports 0 or NaN when the file does not store it,
   * both mapped to NaN.
   */
  private static double abundanceLimit(@NotNull JsonNode mtd, @NotNull String field) {
    final double limit = mtd.path(field).asDouble(Double.NaN);
    return limit > 0 ? limit : Double.NaN;
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

  /**
   * Taken from DeviceType from the MHDAC sdk.
   */
  private enum AnalogDevice {
    TCC("ThermostattedColumnCompartment"), //
    DAD("DiodeArrayDetector"),  //
    FID("FlameIonizationDetector"), //
    TCD("ThermalConductivityDetector"), //
    RID("RefractiveIndexDetector"), //
    MWD("MultiWavelengthDetector"), //
    VWD("VariableWavelengthDetector"), //
    ECD("ElectronCaptureDetector"), // some kind of current, but which order of magnitude?
    FD("FluorescenceDetector"), // No unit for emission yet?
    ELSD("EvaporativeLightScatteringDetector"), //
    IP("IsocraticPump"), //
    BP("BinaryPump"), //
    QP("QuarternaryPump"), //
    CP("CapillaryPump"), //
    NP("NanoPump"), //
    LFP("LowFlowPump"), //
    UIB2("UIB2"), // "Universal interface box"
    ;

    /**
     * enum name in agilent software
     */
    final String title;

    AnalogDevice(String title) {
      this.title = title;
    }

    @Nullable
    public static AnalogDevice fromTitle(@Nullable String title) {
      if (title == null) {
        return null;
      }
      for (var device : values()) {
        if (device.title.equalsIgnoreCase(title)) {
          return device;
        }
      }
      return null;
    }
  }

  private enum AnalogUnit {
    UNKNOWN("unknown", MzMLUnits.UNKNOWN, ChromatogramType.UNKNOWN), //
    PRESSURE("pressure", MzMLUnits.BAR, ChromatogramType.PRESSURE), //
    ABSORBANCE("absorbance", MzMLUnits.ABSORBANCE, ChromatogramType.ABSORPTION), // to be changed
    FLOW("flow", MzMLUnits.MICRO_LITER_PER_MINUTE, ChromatogramType.FLOW_RATE), //
    PICO_AMPERE("pico ampere", MzMLUnits.PICO_AMPERE, ChromatogramType.UNKNOWN), // to be changed
    TEMPERATURE("temperature", MzMLUnits.DEGREE_CELSIUS, ChromatogramType.UNKNOWN), EMISSION(
        "emission", MzMLUnits.DIMENSIONLESS,
        ChromatogramType.EMISSION), // emission unknown - to be changed
    SOLVENT_RATIO("solvent ratio", MzMLUnits.UNKNOWN, ChromatogramType.UNKNOWN),
    ;

    private final String description;
    private final MzMLUnits unit;
    private final ChromatogramType type;


    AnalogUnit(String description, MzMLUnits unit, ChromatogramType type) {
      this.description = description;
      this.unit = unit;
      this.type = type;
    }

    @Nullable
    public DoubleUnaryOperator getMapper() {
      return switch (this) {
        case FLOW -> value -> value * 1000; // agilent flow is mL/min, Unit ontology is uL/min
        default -> null;
      };
    }

    @NotNull
    static AnalogUnit fromDescription(@Nullable String description) {
      if (description == null) {
        return UNKNOWN;
      }

      for (var unit : values()) {
        if (unit.description.equalsIgnoreCase(description) || description.toLowerCase()
            .contains(unit.description)) {
          return unit;
        }
      }
      return UNKNOWN;
    }

    @NotNull
    static AnalogUnit fromDevice(AnalogDevice device) {
      return switch (device) {
        case TCC, ECD, NP, BP, QP, CP, LFP, IP, ELSD, FD, UIB2 -> UNKNOWN;
        case DAD, VWD, MWD -> ABSORBANCE;
        case FID -> PICO_AMPERE;
        case TCD -> TEMPERATURE;
        case RID -> SOLVENT_RATIO;
      };
    }
  }

  private record DeviceWithUnit(AnalogDevice device, AnalogUnit unit) {

  }
}
