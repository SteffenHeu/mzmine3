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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What the opened Shimadzu file actually contains, as reported by the bridge's {@code open}
 * response. Mass spectra, MRM traces and analog channels are described independently, and each facet
 * carries a state rather than a boolean so that "this file stores none" stays distinguishable from
 * "this format does not support it" and from "enumeration failed".
 *
 * @param massSpectraState          state of the mass-spectrum facet
 * @param mrmState                  state of the targeted (MRM/SIM) facet
 * @param analogDataChannelState    state of the non-MS detector data channels (UV, PDA, RID, …)
 * @param analogStatusChannelState  state of the instrument/status curves (pressure, temperature, …)
 * @param vendorScanCount           raw vendor cycle count. On an MRM-only file this counts
 *                                  acquisition cycles, NOT importable spectra, which is why
 *                                  {@link #hasMassSpectra()} must never be derived from it.
 * @param spectrumCount             number of importable spectra, or null when the file mixes
 *                                  spectral and targeted events and the split is only knowable per
 *                                  scan
 * @param spectraRequireFiltering   true when the scan loop must skip scans flagged
 *                                  {@code isTargetedTrace}
 * @param mrmTraceCount             number of transition descriptors
 * @param analogTraceCount          number of analog channel descriptors
 * @param hasProfileSpectra         observed on a bounded probe, not a file-wide guarantee
 * @param hasStoredCentroidSpectra  observed on a bounded probe, not a file-wide guarantee
 */
public record ShimadzuCapabilities(@NotNull String massSpectraState, @NotNull String mrmState,
                            @NotNull String analogDataChannelState,
                            @NotNull String analogStatusChannelState, int vendorScanCount,
                            @Nullable Integer spectrumCount, boolean spectraRequireFiltering,
                            int mrmTraceCount, int analogTraceCount, boolean hasProfileSpectra,
                            boolean hasStoredCentroidSpectra) {

  public static final String PRESENT = "present";
  public static final String EMPTY = "empty";
  public static final String UNSUPPORTED = "unsupported";
  public static final String FAILED = "failed";

  /**
   * Parse the {@code capabilities} object of an {@code open} response. Missing keys fall back to
   * {@link #UNSUPPORTED} / zero so an older bridge cannot make the importer throw.
   */
  static @NotNull ShimadzuCapabilities fromOpenResponse(@NotNull JsonNode openResponse) {
    final JsonNode c = openResponse.path("capabilities");
    final JsonNode spectrumCount = c.path("spectrumCount");
    return new ShimadzuCapabilities(state(c, "massSpectraState"), state(c, "mrmState"),
        state(c, "analogDataChannelState"), state(c, "analogStatusChannelState"),
        // vendorScanCount is duplicated as the top-level scanCount; prefer the
        // capability field and fall back for robustness.
        c.path("vendorScanCount").asInt(openResponse.path("scanCount").asInt(0)),
        spectrumCount.isMissingNode() || spectrumCount.isNull() ? null : spectrumCount.asInt(),
        c.path("spectraRequireFiltering").asBoolean(false), c.path("mrmTraceCount").asInt(0),
        c.path("analogTraceCount").asInt(0), c.path("hasProfileSpectra").asBoolean(false),
        c.path("hasStoredCentroidSpectra").asBoolean(false));
  }

  private static @NotNull String state(@NotNull JsonNode caps, @NotNull String field) {
    final String s = caps.path(field).asText(UNSUPPORTED);
    return s == null || s.isEmpty() ? UNSUPPORTED : s;
  }

  public boolean hasMassSpectra() {
    return PRESENT.equals(massSpectraState);
  }

  public boolean hasMrmTraces() {
    return PRESENT.equals(mrmState) && mrmTraceCount > 0;
  }

  public boolean hasAnalogTraces() {
    return (PRESENT.equals(analogDataChannelState) || PRESENT.equals(analogStatusChannelState))
        && analogTraceCount > 0;
  }

  /**
   * Upper bound on the scan numbers the bridge will accept, i.e. how many frames a full
   * {@code scanRange} emits. This is the vendor cycle count, not the spectrum count.
   */
  public int scanRangeLength() {
    return vendorScanCount;
  }

  /**
   * Number of spectra expected to survive the targeted-trace filter, for progress reporting only.
   */
  public int expectedSpectra() {
    return spectrumCount != null ? spectrumCount : vendorScanCount;
  }
}
