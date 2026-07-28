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

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Range;
import io.github.mzmine.datamodel.MassSpectrumType;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.featuredata.OtherFeatureUtils;
import io.github.mzmine.datamodel.impl.DDAMsMsInfoImpl;
import io.github.mzmine.datamodel.impl.SimpleScan;
import io.github.mzmine.datamodel.impl.builders.SimpleBuildingScan;
import io.github.mzmine.datamodel.msms.ActivationMethod;
import io.github.mzmine.datamodel.msms.DDAMsMsInfo;
import io.github.mzmine.datamodel.otherdetectors.OtherFeature;
import io.github.mzmine.datamodel.otherdetectors.OtherFeatureImpl;
import io.github.mzmine.datamodel.otherdetectors.OtherTimeSeriesData;
import io.github.mzmine.datamodel.otherdetectors.SimpleOtherTimeSeries;
import io.github.mzmine.modules.io.import_rawdata_all.spectral_processor.ScanImportProcessorConfig;
import io.github.mzmine.modules.io.import_rawdata_all.spectral_processor.SimpleSpectralArrays;
import io.github.mzmine.project.impl.RawDataFileImpl;
import io.github.mzmine.util.MemoryMapStorage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Typed access to one open Shimadzu file. This is the only class that knows the bridge's protocol
 * operation names, and the only one that reads wire headers and binary blobs; it converts the
 * bridge's normalized results into mzmine model objects. The import task above it deals in
 * {@link SimpleScan} and {@link OtherFeature} only.
 * <p>
 * Owns the child process and closes it. Not thread-safe: the wire is a strictly alternating
 * request/response stream, so one command must be fully consumed before the next is sent.
 */
public final class ShimadzuDataAccess implements AutoCloseable {

  private static final Logger logger = Logger.getLogger(ShimadzuDataAccess.class.getName());

  private final File file;
  private final MemoryMapStorage storage;
  private final ShimadzuBridgeProcess bridge;
  private final ShimadzuProtocol protocol;
  private final ShimadzuCapabilities capabilities;

  private boolean fileOpen;

  /**
   * Launches the bridge, preflights it, and opens {@code file}. Any warning the bridge reports for
   * the open is logged here, so a partially readable file is visible in the log rather than silently
   * short.
   */
  public ShimadzuDataAccess(@NotNull File file, @Nullable MemoryMapStorage storage) throws IOException {
    this.file = file;
    this.storage = storage;
    this.bridge = new ShimadzuBridgeProcess();
    this.protocol = bridge.protocol();

    try {
      checkPreflight();

      protocol.send(ShimadzuProtocol.openRequest(file.getAbsolutePath()));
      final JsonNode openResponse = protocol.readHeader();
      if (!openResponse.path("ok").asBoolean(false)) {
        throw new IOException("ShimadzuBridge could not open %s: %s (%s)".formatted(file.getName(),
            openResponse.path("error").asText("unknown"), openResponse.path("code").asText("?")));
      }
      fileOpen = true;
      capabilities = ShimadzuCapabilities.fromOpenResponse(openResponse);
      logOpenWarnings(openResponse);
      logCapabilities();
    } catch (IOException | RuntimeException e) {
      // A failed open still owns the child process.
      bridge.close();
      throw e;
    }
  }

  /**
   * Ask the bridge to verify its own deployment before any data is read. A broken payload otherwise
   * surfaces as an opaque failure on the first read.
   */
  private void checkPreflight() throws IOException {
    protocol.send(ShimadzuProtocol.preflightRequest());
    final JsonNode report = protocol.readHeader();
    if (report.path("ok").asBoolean(false)) {
      return;
    }
    final List<String> problems = new ArrayList<>();
    report.path("problems").forEach(n -> problems.add(n.asText()));
    throw new IOException(
        "ShimadzuBridge deployment is not usable: " + String.join("; ", problems));
  }

  private void logOpenWarnings(@NotNull JsonNode openResponse) {
    for (JsonNode w : openResponse.path("warnings")) {
      logger.warning("ShimadzuBridge (%s): [%s/%s] %s".formatted(file.getName(),
          w.path("facet").asText("?"), w.path("code").asText("?"), w.path("message").asText("")));
    }
  }

  private void logCapabilities() {
    logger.finest(
        "Shimadzu %s: spectra=%s (%d of %d vendor scans), mrm=%s (%d), analogData=%s, analogStatus=%s (%d total)".formatted(
            file.getName(), capabilities.massSpectraState(), capabilities.expectedSpectra(),
            capabilities.vendorScanCount(), capabilities.mrmState(), capabilities.mrmTraceCount(),
            capabilities.analogDataChannelState(), capabilities.analogStatusChannelState(),
            capabilities.analogTraceCount()));
  }

  public @NotNull ShimadzuCapabilities capabilities() {
    return capabilities;
  }

  public @NotNull RawDataFileImpl createDataFile() throws IOException {
    // No ion mobility in this SDK, so there is only ever one file type to create.
    return new RawDataFileImpl(file.getName(), file.getAbsolutePath(), storage);
  }

  // ---------------------------------------------------------------------------
  // Mass spectra
  // ---------------------------------------------------------------------------

  /**
   * Outcome of a full scan pass, for logging.
   *
   * @param imported        scans handed to {@code onScan}
   * @param skippedTargeted MRM/SIM acquisition cycles skipped — these belong to the transition path,
   *                        not the spectrum path
   * @param skippedFiltered scans rejected by the user's scan filter
   * @param skippedFailed   scans the SDK could not read
   */
  public record ScanReadResult(int imported, int skippedTargeted, int skippedFiltered, int skippedFailed) {

  }

  /**
   * Stream every vendor scan through the bridge, converting the ones that are mass spectra and are
   * accepted by the scan filter. MRM/SIM acquisition cycles are skipped here, so an MRM-only file
   * contributes no spectra at all.
   *
   * @param profile   request profile data; the bridge reports what it actually returned per scan
   * @param onScan    receives each imported scan, in vendor order
   * @param onFrame   invoked once per consumed frame, for progress
   * @param cancelled polled once per frame; a true return abandons the pass
   */
  public @NotNull ScanReadResult readAllScans(@NotNull RawDataFileImpl out,
      @NotNull ScanImportProcessorConfig processor, boolean profile,
      @NotNull Consumer<SimpleScan> onScan, @NotNull Runnable onFrame,
      @NotNull BooleanSupplier cancelled) throws IOException {

    final int frames = capabilities.scanRangeLength();
    if (frames <= 0) {
      return new ScanReadResult(0, 0, 0, 0);
    }

    protocol.send(ShimadzuProtocol.scanRangeRequest(1, frames, profile));
    final JsonNode outer = protocol.readHeader();
    if (!outer.path("ok").asBoolean(false)) {
      throw new IOException("ShimadzuBridge scanRange failed: " + outer.path("error")
          .asText("unknown"));
    }
    final int count = outer.path("count").asInt(0);
    if (count != frames) {
      // The blob stream is framed by this count, so a mismatch would desync the
      // reader. Trust the header and say so.
      logger.warning(
          "ShimadzuBridge scanRange returned %d frames, expected %d; reading the announced number".formatted(
              count, frames));
    }

    int imported = 0, skippedTargeted = 0, skippedFiltered = 0, skippedFailed = 0;

    for (int i = 0; i < count; i++) {
      if (cancelled.getAsBoolean()) {
        break;
      }

      final JsonNode hdr = protocol.readHeader();
      onFrame.run();

      if (!hdr.path("ok").asBoolean(false)) {
        // A per-scan failure replaces that frame's header and emits no blobs, so
        // the stream stays parseable. One bad scan must not kill the import.
        skippedFailed++;
        if (skippedFailed <= 5) {
          logger.log(Level.WARNING, "Skipping Shimadzu scan #{0}: {1} ({2})",
              new Object[]{i + 1, hdr.path("error").asText("unknown"),
                  hdr.path("code").asText("?")});
        }
        continue;
      }

      final int nPeaks = hdr.path("nPeaks").asInt(0);
      // The blobs must be consumed even for scans we discard, or the stream
      // desyncs — so read them before any skip decision.
      final double[] mz = protocol.readDoubles(nPeaks);
      final double[] intensity = protocol.readDoubles(nPeaks);

      if (hdr.path("isTargetedTrace").asBoolean(false)) {
        // An MRM/SIM acquisition cycle. Its data is imported as a transition
        // chromatogram instead; importing it here too would duplicate it.
        skippedTargeted++;
        continue;
      }

      final SimpleScan scan = buildScan(out, processor, hdr, mz, intensity);
      if (scan == null) {
        skippedFiltered++;
        continue;
      }
      onScan.accept(scan);
      imported++;
    }

    return new ScanReadResult(imported, skippedTargeted, skippedFiltered, skippedFailed);
  }

  /**
   * Convert one scan header plus its arrays into a {@link SimpleScan}, or null when the user's scan
   * filter rejects it.
   */
  private @Nullable SimpleScan buildScan(@NotNull RawDataFileImpl out,
      @NotNull ScanImportProcessorConfig processor, @NotNull JsonNode hdr, double[] mz,
      double[] intensity) {

    // scanNo is the SDK's 1-based scan number and needs no translation.
    final int scanNumber = hdr.path("scanNo").asInt(0);
    final int msLevel = hdr.path("msLevel").asInt(1);
    final float rt = (float) hdr.path("rt").asDouble(0d);
    final PolarityType polarity = parsePolarity(hdr.path("polarity").asText(""));
    final MassSpectrumType type = hdr.path("profile").asBoolean(false) ? MassSpectrumType.PROFILE
        : MassSpectrumType.CENTROIDED;

    // hasSelectedPrecursor is false for fragmentation without a selected
    // precursor (all-ions / DIA-style). No precursor may be invented for those.
    final boolean hasPrecursor = hdr.path("hasSelectedPrecursor").asBoolean(false);
    final double precursorMz = hasPrecursor ? firstPrecursorMz(hdr) : 0d;
    final int charge = hdr.path("charge").asInt(0);

    final SimpleBuildingScan metadataScan = new SimpleBuildingScan(scanNumber, msLevel, polarity,
        type, rt, precursorMz, charge);
    if (!processor.scanFilter().matches(metadataScan)) {
      return null;
    }

    final SimpleSpectralArrays processed = processor.processor()
        .processScan(metadataScan, new SimpleSpectralArrays(mz, intensity));

    final MassSpectrumType finalType =
        type == MassSpectrumType.CENTROIDED || processor.isMassDetectActive(msLevel)
            ? MassSpectrumType.CENTROIDED : MassSpectrumType.PROFILE;

    final DDAMsMsInfo msMs;
    if (hasPrecursor && precursorMz > 0) {
      final float ce = (float) hdr.path("collisionEnergy").asDouble(0d);
      msMs = new DDAMsMsInfoImpl(precursorMz, charge > 0 ? charge : null, ce, null, null, msLevel,
          activationMethod(hdr), isolationWindow(hdr, precursorMz));
    } else {
      msMs = null;
    }

    return new SimpleScan(out, scanNumber, msLevel, rt, msMs, processed.mzs(),
        processed.intensities(), finalType, polarity, scanDefinition(hdr), null);
  }

  private static double firstPrecursorMz(@NotNull JsonNode hdr) {
    // The bridge filters the SDK's zero-padded precursor buffer, so element 0 is
    // a real precursor whenever the array is present.
    final JsonNode precursors = hdr.path("precursorMz");
    return precursors.isArray() && !precursors.isEmpty() ? precursors.get(0).asDouble(0d) : 0d;
  }

  /**
   * The bridge reports {@code CID} for every MS² scan because the SDK exposes no dissociation
   * discriminator on its supported surface. Anything else, including a null, maps to
   * {@link ActivationMethod#UNKNOWN} rather than being guessed at.
   */
  private static @NotNull ActivationMethod activationMethod(@NotNull JsonNode hdr) {
    final String dissociation = ShimadzuTraceDescriptors.optText(hdr, "dissociationType");
    if (dissociation == null) {
      return ActivationMethod.UNKNOWN;
    }
    return switch (dissociation.toUpperCase()) {
      case "CID" -> ActivationMethod.CID;
      case "HCD" -> ActivationMethod.HCD;
      case "EAD", "OAD" -> ActivationMethod.EAD;
      default -> ActivationMethod.UNKNOWN;
    };
  }

  /**
   * Isolation window from the bridge's quad transmission width, centred on the precursor, or null
   * when no usable width was reported for this scan.
   */
  private static @Nullable Range<Double> isolationWindow(@NotNull JsonNode hdr, double precursorMz) {
    final Double width = ShimadzuTraceDescriptors.optDouble(hdr, "isolationWidth");
    if (width == null || width <= 0) {
      return null;
    }
    final double half = width / 2d;
    return Range.closed(precursorMz - half, precursorMz + half);
  }

  /**
   * Descriptive scan definition. Shimadzu multi-segment acquisitions put many scans at the same
   * retention time in different functions, so segment/event/mode is what distinguishes them.
   */
  private static @NotNull String scanDefinition(@NotNull JsonNode hdr) {
    final StringBuilder sb = new StringBuilder(32);
    final int seg = hdr.path("segmentNo").asInt(-1);
    final int evt = hdr.path("eventNo").asInt(-1);
    final String mode = hdr.path("analysisMode").asText("");
    if (seg >= 0) {
      sb.append("seg=").append(seg);
    }
    if (evt >= 0) {
      appendSpaced(sb, "evt=" + evt);
    }
    if (!mode.isEmpty()) {
      appendSpaced(sb, "mode=" + mode);
    }
    return sb.toString();
  }

  private static void appendSpaced(@NotNull StringBuilder sb, @NotNull String s) {
    if (!sb.isEmpty()) {
      sb.append(' ');
    }
    sb.append(s);
  }

  private static @NotNull PolarityType parsePolarity(@Nullable String s) {
    if (s == null || s.isEmpty()) {
      return PolarityType.UNKNOWN;
    }
    final String u = s.toUpperCase();
    if (u.contains("POSITIVE") || u.equals("POS") || u.equals("+")) {
      return PolarityType.POSITIVE;
    }
    if (u.contains("NEGATIVE") || u.equals("NEG") || u.equals("-")) {
      return PolarityType.NEGATIVE;
    }
    return PolarityType.UNKNOWN;
  }

  // ---------------------------------------------------------------------------
  // MRM / SIM transitions
  // ---------------------------------------------------------------------------

  public @NotNull List<ShimadzuTraceDescriptors.Mrm> listMrmTraces() throws IOException {
    protocol.send(ShimadzuProtocol.listMrmTracesRequest());
    final JsonNode response = protocol.readHeader();
    if (!response.path("ok").asBoolean(false)) {
      throw new IOException("ShimadzuBridge listMrmTraces failed: " + response.path("error")
          .asText("unknown"));
    }
    final List<ShimadzuTraceDescriptors.Mrm> traces = new ArrayList<>();
    for (JsonNode n : response.path("traces")) {
      traces.add(ShimadzuTraceDescriptors.Mrm.parse(n));
    }
    return traces;
  }

  /**
   * Read one transition chromatogram. Returns null when the trace could not be read or produced no
   * usable series — the affected transition is named in the log and the import continues.
   */
  public @Nullable OtherFeature readMrmTrace(@NotNull ShimadzuTraceDescriptors.Mrm d,
      @NotNull OtherTimeSeriesData timeSeriesData) throws IOException {

    protocol.send(ShimadzuProtocol.mrmTraceRequest(d.traceId()));
    final Series series = readSeries("mrmTrace " + d.traceId());
    if (series == null) {
      return null;
    }

    final SimpleOtherTimeSeries ts = buildSeries(series, mrmTraceName(d), timeSeriesData);
    if (ts == null) {
      return null;
    }

    final OtherFeature feature = new OtherFeatureImpl(ts);
    // Q1 becomes the precursor of the feature's MS/MS info, Q3 its m/z. SIM has
    // no Q3, so fall back to Q1 to keep the feature addressable by mass.
    final double q1 = d.q1() != null ? d.q1() : 0d;
    final double q3 = d.q3() != null ? d.q3() : q1;
    final Float ce = d.collisionEnergy() != null ? d.collisionEnergy().floatValue() : null;
    OtherFeatureUtils.applyMrmInfo(q1, q3, ActivationMethod.CID, ce, feature);
    return feature;
  }

  private static @NotNull String mrmTraceName(@NotNull ShimadzuTraceDescriptors.Mrm d) {
    if (d.q1() == null) {
      return "%s %s".formatted(d.kind(), d.traceId());
    }
    if (d.q3() == null) {
      // SIM: one monitored mass, no product stage.
      return "SIM %.4f (seg=%d evt=%d ch=%d)".formatted(d.q1(), d.segment(), d.event(),
          d.channel());
    }
    return "%.4f -> %.4f (seg=%d evt=%d ch=%d)".formatted(d.q1(), d.q3(), d.segment(), d.event(),
        d.channel());
  }

  // ---------------------------------------------------------------------------
  // Analog / non-MS channels
  // ---------------------------------------------------------------------------

  public @NotNull List<ShimadzuTraceDescriptors.Analog> listAnalogTraces() throws IOException {
    protocol.send(ShimadzuProtocol.listAnalogTracesRequest());
    final JsonNode response = protocol.readHeader();
    if (!response.path("ok").asBoolean(false)) {
      throw new IOException("ShimadzuBridge listAnalogTraces failed: " + response.path("error")
          .asText("unknown"));
    }
    final List<ShimadzuTraceDescriptors.Analog> traces = new ArrayList<>();
    for (JsonNode n : response.path("traces")) {
      traces.add(ShimadzuTraceDescriptors.Analog.parse(n));
    }
    return traces;
  }

  /**
   * Read one analog channel. Values are exactly what the SDK reported: the bridge applies no unit
   * conversion, and neither do we. When the channel carries a vendor scale factor other than 1 that
   * is logged, because the stored values may then not be in the unit the SDK names.
   */
  public @Nullable OtherFeature readAnalogTrace(@NotNull ShimadzuTraceDescriptors.Analog d,
      @NotNull OtherTimeSeriesData timeSeriesData) throws IOException {

    protocol.send(ShimadzuProtocol.analogTraceRequest(d.traceId()));
    final Series series = readSeries("analogTrace " + d.traceId());
    if (series == null) {
      return null;
    }

    final SimpleOtherTimeSeries ts = buildSeries(series, d.displayName(), timeSeriesData);
    if (ts == null) {
      return null;
    }

    if (d.intensityMultiplier() != null && d.intensityMultiplier() != 1d) {
      logger.info(
          ("Shimadzu analog channel '%s' reports unit '%s' with an unapplied vendor scale factor of %s. "
              + "Values are imported unconverted; verify against LabSolutions before interpreting them.").formatted(
              d.displayName(), d.rawUnit(), d.intensityMultiplier()));
    }

    return new OtherFeatureImpl(ts);
  }

  // ---------------------------------------------------------------------------
  // Shared series plumbing
  // ---------------------------------------------------------------------------

  /**
   * A header plus its two blobs, already consumed off the wire.
   */
  private record Series(@NotNull JsonNode header, double[] domain, double[] range) {

  }

  /**
   * Read a trace response: header, then the x and y blobs. Returns null on a reported error, having
   * consumed nothing further — an error response carries no blobs, so the stream stays aligned.
   */
  private @Nullable Series readSeries(@NotNull String what) throws IOException {
    final JsonNode hdr = protocol.readHeader();
    if (!hdr.path("ok").asBoolean(false)) {
      logger.warning("ShimadzuBridge %s failed: %s (%s)".formatted(what,
          hdr.path("error").asText("unknown"), hdr.path("code").asText("?")));
      return null;
    }
    final int n = hdr.path("nPoints").asInt(0);
    final double[] domain = protocol.readDoubles(n);
    final double[] range = protocol.readDoubles(n);
    return new Series(hdr, domain, range);
  }

  /**
   * Store a series, or null when it is empty or not usable. mzmine requires a retention-time axis
   * sorted ascending; a vendor trace that violates that is skipped with a named warning rather than
   * aborting the whole import.
   */
  private @Nullable SimpleOtherTimeSeries buildSeries(@NotNull Series series, @NotNull String name,
      @NotNull OtherTimeSeriesData timeSeriesData) {
    if (series.domain().length == 0) {
      logger.finest("Shimadzu trace '%s' is empty; skipping".formatted(name));
      return null;
    }
    final float[] rts = new float[series.domain().length];
    for (int i = 0; i < rts.length; i++) {
      rts[i] = (float) series.domain()[i];
    }
    try {
      return new SimpleOtherTimeSeries(storage, rts, series.range(), name, timeSeriesData);
    } catch (IllegalArgumentException e) {
      logger.warning(
          "Shimadzu trace '%s' could not be stored: %s".formatted(name, e.getMessage()));
      return null;
    }
  }

  @Override
  public void close() {
    try {
      if (fileOpen) {
        protocol.send(ShimadzuProtocol.closeRequest());
        protocol.readHeader();
        fileOpen = false;
      }
    } catch (IOException e) {
      logger.log(Level.FINE, "ShimadzuBridge did not acknowledge close; terminating anyway", e);
    } finally {
      bridge.close();
    }
  }
}
