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
 * Descriptors for the bridge's two auxiliary read paths. Both are enumerate-then-read: the bridge
 * lists descriptors cheaply, and the importer reads only the ones it wants by {@code traceId}.
 * <p>
 * MRM transitions and analog channels deliberately do not share a descriptor. They share only the
 * time/value numerical shape — a transition's Q1/Q3 is not a detector unit, and a detector
 * wavelength is not a transition property.
 */
public final class ShimadzuTraceDescriptors {

  private ShimadzuTraceDescriptors() {
  }

  /**
   * One targeted chromatogram.
   *
   * @param traceId stable id used to read the trace back. Composed by the bridge from the vendor's
   *                own (segment, event, channel) coordinates — deliberately not from Q1/Q3, since
   *                the same transition recurs across segments and collision energies.
   * @param kind    {@code "MRM"} or {@code "SIM"}. SIM has no Q3.
   */
  public record Mrm(@NotNull String traceId, @NotNull String kind, @Nullable Double q1, @Nullable Double q3,
             @Nullable Double collisionEnergy, int segment, int event, int channel) {

    static @NotNull Mrm parse(@NotNull JsonNode n) {
      return new Mrm(n.path("traceId").asText(), n.path("traceKind").asText("MRM"),
          optDouble(n, "q1"), optDouble(n, "q3"), optDouble(n, "collisionEnergy"),
          n.path("segment").asInt(-1), n.path("event").asInt(-1), n.path("channel").asInt(-1));
    }
  }

  /**
   * One non-MS detector channel.
   *
   * @param rawUnit             unit exactly as the SDK reports it
   * @param canonicalUnit       normalized spelling; equals {@code rawUnit} when unrecognized
   * @param signalType          normalized class (PRESSURE, TEMPERATURE, ABSORBANCE, …) or UNKNOWN
   * @param intensityMultiplier vendor scale factor the bridge did NOT apply. When this is present
   *                            and not 1, the stored values may not be in {@code rawUnit} — see the
   *                            unit warning in the wrapper README.
   */
  public record Analog(@NotNull String traceId, @Nullable String name, @Nullable String detectorType,
                @Nullable String signalDescription, @Nullable String rawUnit,
                @Nullable String canonicalUnit, @Nullable Double wavelengthNm,
                @NotNull String signalType, @Nullable Double intensityMultiplier) {

    static @NotNull Analog parse(@NotNull JsonNode n) {
      return new Analog(n.path("traceId").asText(), optText(n, "name"), optText(n, "detectorType"),
          optText(n, "signalDescription"), optText(n, "rawUnit"), optText(n, "canonicalUnit"),
          optDouble(n, "wavelengthNm"), n.path("signalType").asText("UNKNOWN"),
          optDouble(n, "intensityMultiplier"));
    }

    /**
     * Label for the trace inside mzmine. Prefers the vendor channel name and keeps the device with
     * it, because several devices can expose channels with the same name.
     */
    public @NotNull String displayName() {
      final String n = name != null ? name : traceId;
      return detectorType != null ? detectorType + " " + n : n;
    }
  }

  static @Nullable Double optDouble(@NotNull JsonNode parent, @NotNull String field) {
    final JsonNode n = parent.path(field);
    // The bridge sends explicit nulls for unavailable values rather than omitting
    // the key, so both cases must be handled.
    return n.isMissingNode() || n.isNull() ? null : n.asDouble();
  }

  static @Nullable String optText(@NotNull JsonNode parent, @NotNull String field) {
    final JsonNode n = parent.path(field);
    if (n.isMissingNode() || n.isNull()) {
      return null;
    }
    final String s = n.asText();
    return s == null || s.isBlank() ? null : s;
  }
}
