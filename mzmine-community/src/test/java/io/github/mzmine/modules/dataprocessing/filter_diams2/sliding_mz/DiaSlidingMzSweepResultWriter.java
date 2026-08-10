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

package io.github.mzmine.modules.dataprocessing.filter_diams2.sliding_mz;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DiaSlidingMzSweepResultWriter {

  private DiaSlidingMzSweepResultWriter() {
  }

  static void write(@NotNull final Path outputDirectory,
      @NotNull final List<DiaSlidingMzDiagnosticLogRecord> records,
      @NotNull final List<DiaSlidingMzSweepConfig> configs, final int targetCount,
      final int expectedPeakCount) throws IOException {
    Files.createDirectories(outputDirectory);
    final List<ParsedDiagnostic> diagnostics = records.stream()
        .map(DiaSlidingMzSweepResultWriter::parse).toList();
    writeEvents(outputDirectory.resolve("dia_sliding_filter_sweep_events.tsv"), diagnostics);
    writeReasonSummary(outputDirectory.resolve("dia_sliding_filter_rejection_summary.tsv"),
        diagnostics, expectedPeakCount);
    writeCompoundReasonSummary(
        outputDirectory.resolve("dia_sliding_filter_compound_rejection_summary.tsv"), diagnostics);
    writeRunSummary(outputDirectory.resolve("dia_sliding_filter_run_summary.tsv"), diagnostics,
        configs, targetCount, expectedPeakCount);
  }

  private static void writeEvents(@NotNull final Path output,
      @NotNull final List<ParsedDiagnostic> diagnostics) throws IOException {
    final List<String> lines = new ArrayList<>(diagnostics.size() + 1);
    lines.add("configuration\tdia_algorithm\tsliding_mz_pregrouping\tms2_noise_level"
        + "\tminimum_fragment_intensity\tevent\ttarget\tprecursor_mz\trt\texpected_mz"
        + "\treason\tcontained\ttotal\tmissing\tdetails");
    for (final ParsedDiagnostic diagnostic : diagnostics) {
      final Map<String, String> fields = diagnostic.fields();
      lines.add(String.join("\t", sanitize(diagnostic.config().name()),
          diagnostic.config().diaAlgorithm().name(),
          diagnostic.config().slidingMzPregrouping().name(),
          format(diagnostic.config().ms2NoiseLevel()),
          format(diagnostic.config().minimumFragmentIntensity()), sanitize(fields.get("event")),
          sanitize(fields.get("target")), sanitize(fields.get("precursor_mz")),
          sanitize(fields.get("rt")), sanitize(fields.get("expected_mz")),
          sanitize(fields.get("reason")), sanitize(fields.get("contained")),
          sanitize(fields.get("total")), sanitize(fields.get("missing")),
          sanitize(fields.get("details"))));
    }
    Files.write(output, lines, StandardCharsets.UTF_8);
  }

  private static void writeReasonSummary(@NotNull final Path output,
      @NotNull final List<ParsedDiagnostic> diagnostics, final int expectedPeakCount)
      throws IOException {
    final Map<SummaryKey, ReasonStatistics> statistics = new LinkedHashMap<>();
    diagnostics.stream().filter(diagnostic -> "REJECTED".equals(diagnostic.fields().get("event")))
        .forEach(diagnostic -> {
          final String reason = diagnostic.fields().getOrDefault("reason", "UNCLASSIFIED");
          final SummaryKey key = new SummaryKey(diagnostic.config(), reason);
          final ReasonStatistics value = statistics.computeIfAbsent(key,
              ignored -> new ReasonStatistics());
          value.events++;
          final String target = diagnostic.fields().getOrDefault("target", "");
          final String expectedMz = diagnostic.fields().getOrDefault("expected_mz", "");
          value.targets.add(target);
          value.expectedPeaks.add(target + '\u0000' + expectedMz);
        });

    final List<String> lines = new ArrayList<>(statistics.size() + 1);
    lines.add("configuration\tdia_algorithm\tsliding_mz_pregrouping\tms2_noise_level"
        + "\tminimum_fragment_intensity\treason\trejection_events\tunique_expected_peaks"
        + "\taffected_targets\tpercent_of_configured_expected_peaks");
    statistics.forEach((key, value) -> lines.add(
        String.join("\t", sanitize(key.config().name()), key.config().diaAlgorithm().name(),
            key.config().slidingMzPregrouping().name(), format(key.config().ms2NoiseLevel()),
            format(key.config().minimumFragmentIntensity()), sanitize(key.reason()),
            Integer.toString(value.events), Integer.toString(value.expectedPeaks.size()),
            Integer.toString(value.targets.size()),
            formatPercent(value.expectedPeaks.size(), expectedPeakCount))));
    Files.write(output, lines, StandardCharsets.UTF_8);
  }

  private static void writeCompoundReasonSummary(@NotNull final Path output,
      @NotNull final List<ParsedDiagnostic> diagnostics) throws IOException {
    final Map<CompoundReasonKey, CompoundReasonStatistics> statistics = new LinkedHashMap<>();
    diagnostics.stream().filter(diagnostic -> "REJECTED".equals(diagnostic.fields().get("event")))
        .forEach(diagnostic -> {
          final String target = diagnostic.fields().getOrDefault("target", "");
          final String reason = diagnostic.fields().getOrDefault("reason", "UNCLASSIFIED");
          final String expectedMz = diagnostic.fields().getOrDefault("expected_mz", "");
          final CompoundReasonKey key = new CompoundReasonKey(diagnostic.config(), target, reason);
          final CompoundReasonStatistics value = statistics.computeIfAbsent(key,
              ignored -> new CompoundReasonStatistics());
          value.events++;
          value.expectedMzs.add(expectedMz);
        });

    final List<String> lines = new ArrayList<>(statistics.size() + 1);
    lines.add("configuration\tdia_algorithm\tsliding_mz_pregrouping\tcompound_target\treason"
        + "\trejection_events\tunique_expected_peaks\texpected_mzs");
    statistics.forEach((key, value) -> lines.add(
        String.join("\t", sanitize(key.config().name()), key.config().diaAlgorithm().name(),
            key.config().slidingMzPregrouping().name(), sanitize(key.target()),
            sanitize(key.reason()), Integer.toString(value.events),
            Integer.toString(value.expectedMzs.size()),
            sanitize(value.expectedMzs.stream().sorted().collect(Collectors.joining(", "))))));
    Files.write(output, lines, StandardCharsets.UTF_8);
  }

  private static void writeRunSummary(@NotNull final Path output,
      @NotNull final List<ParsedDiagnostic> diagnostics,
      @NotNull final List<DiaSlidingMzSweepConfig> configs, final int targetCount,
      final int expectedPeakCount) throws IOException {
    final Map<DiaSlidingMzSweepConfig, List<ParsedDiagnostic>> byConfig = new HashMap<>();
    diagnostics.forEach(
        diagnostic -> byConfig.computeIfAbsent(diagnostic.config(), ignored -> new ArrayList<>())
            .add(diagnostic));

    final List<String> lines = new ArrayList<>(configs.size() + 1);
    lines.add("configuration\tdia_algorithm\tsliding_mz_pregrouping\tms2_noise_level"
        + "\tminimum_fragment_intensity\tdiagnostics_applicable\tconfigured_targets"
        + "\tconfigured_expected_peaks"
        + "\tcoverage_messages\trejection_events\tunique_rejected_expected_peaks"
        + "\texpected_peaks_without_rejection_event\tpercent_rejected");
    for (final DiaSlidingMzSweepConfig config : configs) {
      if (!config.usesSlidingMz()) {
        lines.add(String.join("\t", sanitize(config.name()), config.diaAlgorithm().name(),
            config.slidingMzPregrouping().name(), format(config.ms2NoiseLevel()),
            format(config.minimumFragmentIntensity()), Boolean.FALSE.toString(),
            Integer.toString(targetCount), Integer.toString(expectedPeakCount), "", "", "", "",
            ""));
        continue;
      }
      final List<ParsedDiagnostic> configDiagnostics = byConfig.getOrDefault(config, List.of());
      final long coverageMessages = configDiagnostics.stream()
          .filter(diagnostic -> "COVERAGE".equals(diagnostic.fields().get("event"))).count();
      final List<ParsedDiagnostic> rejections = configDiagnostics.stream()
          .filter(diagnostic -> "REJECTED".equals(diagnostic.fields().get("event"))).toList();
      final Set<String> uniqueRejected = new HashSet<>();
      rejections.forEach(diagnostic -> uniqueRejected.add(
          diagnostic.fields().getOrDefault("target", "") + '\u0000' + diagnostic.fields()
              .getOrDefault("expected_mz", "")));
      final int withoutRejectionEvent = Math.max(0, expectedPeakCount - uniqueRejected.size());
      lines.add(String.join("\t", sanitize(config.name()), config.diaAlgorithm().name(),
          config.slidingMzPregrouping().name(), format(config.ms2NoiseLevel()),
          format(config.minimumFragmentIntensity()), Boolean.TRUE.toString(),
          Integer.toString(targetCount), Integer.toString(expectedPeakCount),
          Long.toString(coverageMessages), Integer.toString(rejections.size()),
          Integer.toString(uniqueRejected.size()), Integer.toString(withoutRejectionEvent),
          formatPercent(uniqueRejected.size(), expectedPeakCount)));
    }
    Files.write(output, lines, StandardCharsets.UTF_8);
  }

  private static @NotNull ParsedDiagnostic parse(
      @NotNull final DiaSlidingMzDiagnosticLogRecord record) {
    final String[] tokens = record.message().split("\\s*\\|\\s*");
    final Map<String, String> fields = new LinkedHashMap<>();
    for (int i = 1; i < tokens.length; i++) {
      final int separator = tokens[i].indexOf('=');
      if (separator > 0) {
        fields.put(tokens[i].substring(0, separator).strip(),
            tokens[i].substring(separator + 1).strip());
      }
    }
    return new ParsedDiagnostic(record.config(), fields);
  }

  private static @NotNull String sanitize(@Nullable final String value) {
    return value == null ? "" : value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
  }

  private static @NotNull String format(final double value) {
    return String.format(Locale.ROOT, "%.6f", value);
  }

  private static @NotNull String formatPercent(final int numerator, final int denominator) {
    return denominator == 0 ? "0.000"
        : String.format(Locale.ROOT, "%.3f", numerator * 100d / denominator);
  }

  private record ParsedDiagnostic(@NotNull DiaSlidingMzSweepConfig config,
                                  @NotNull Map<String, String> fields) {

  }

  private record SummaryKey(@NotNull DiaSlidingMzSweepConfig config, @NotNull String reason) {

  }

  private record CompoundReasonKey(@NotNull DiaSlidingMzSweepConfig config, @NotNull String target,
                                   @NotNull String reason) {

  }

  private static final class ReasonStatistics {

    private int events;
    private final Set<String> expectedPeaks = new HashSet<>();
    private final Set<String> targets = new HashSet<>();
  }

  private static final class CompoundReasonStatistics {

    private int events;
    private final Set<String> expectedMzs = new HashSet<>();
  }
}
