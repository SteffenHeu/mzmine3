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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DiaSlidingMzDiagnosticLogHandler extends Handler {

  private final List<DiaSlidingMzDiagnosticLogRecord> records = new CopyOnWriteArrayList<>();
  private volatile @Nullable DiaSlidingMzSweepConfig currentConfig;
  private volatile Map<String, DiaSlidingMzSweepConfig> configurationsByName = Map.of();

  DiaSlidingMzDiagnosticLogHandler() {
    setLevel(Level.WARNING);
  }

  void setCurrentConfig(@NotNull final DiaSlidingMzSweepConfig config) {
    currentConfig = config;
  }

  void setConfigurations(@NotNull final List<DiaSlidingMzSweepConfig> configs) {
    configurationsByName = configs.stream()
        .collect(Collectors.toUnmodifiableMap(DiaSlidingMzSweepConfig::name, config -> config));
  }

  @NotNull List<DiaSlidingMzDiagnosticLogRecord> getRecords() {
    return List.copyOf(records);
  }

  @Override
  public void publish(@NotNull final LogRecord record) {
    if (!isLoggable(record) || !record.getMessage().startsWith("Sliding-m/z diagnostics")) {
      return;
    }
    final String configurationName = field(record.getMessage(), "configuration");
    final DiaSlidingMzSweepConfig config = configurationsByName.getOrDefault(configurationName,
        currentConfig);
    if (config == null) {
      return;
    }
    records.add(new DiaSlidingMzDiagnosticLogRecord(config, record.getMessage()));
  }

  private static @NotNull String field(@NotNull final String message,
      @NotNull final String fieldName) {
    final String marker = fieldName + '=';
    for (final String token : message.split("\\s*\\|\\s*")) {
      if (token.startsWith(marker)) {
        return token.substring(marker.length()).strip();
      }
    }
    return "";
  }

  @Override
  public void flush() {
  }

  @Override
  public void close() {
  }
}
