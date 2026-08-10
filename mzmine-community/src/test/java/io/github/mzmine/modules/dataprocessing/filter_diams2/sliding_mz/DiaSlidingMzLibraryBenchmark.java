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

import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.util.spectraldb.entry.DBEntryField;
import io.github.mzmine.util.spectraldb.entry.SpectralLibrary;
import io.github.mzmine.util.spectraldb.entry.SpectralLibraryEntry;
import io.github.mzmine.util.spectraldb.parser.AutoLibraryParser;
import io.github.mzmine.util.spectraldb.parser.UnsupportedFormatException;
import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

final class DiaSlidingMzLibraryBenchmark {

  private DiaSlidingMzLibraryBenchmark() {
  }

  static @NotNull SpectralLibrary parseLibrary(@NotNull final File libraryFile)
      throws IOException, UnsupportedFormatException {
    final SpectralLibrary library = new SpectralLibrary(null, libraryFile);
    final AutoLibraryParser parser = new AutoLibraryParser(1_000,
        (entries, alreadyProcessed) -> library.addEntries(entries), false);
    if (!parser.parse(null, libraryFile, library)) {
      throw new IOException("AutoLibraryParser could not parse " + libraryFile);
    }
    return library;
  }

  static @NotNull Optional<SpectralLibraryEntry> findExpectedSpectrum(
      @NotNull final SpectralLibrary library, @NotNull final DiaSlidingMzExpectedTarget target,
      @NotNull final MZTolerance mzTolerance, @NotNull final RTTolerance rtTolerance) {
    final String normalizedTargetName = normalizeCompoundName(target.compoundName());
    return library.stream()
        .filter(entry -> normalizedTargetName.equals(normalizeCompoundName(entryName(entry))))
        .filter(
            entry -> entry.getPrecursorMZ() != null && mzTolerance.checkWithinTolerance(target.mz(),
                entry.getPrecursorMZ())).filter(entry -> entry.getAsFloat(DBEntryField.RT)
            .map(rt -> rtTolerance.checkWithinTolerance(target.rt(), rt)).orElse(false))
        // decision: RT distinguishes the duplicated chromatographic variants in the enriched MSP.
        .min(Comparator.<SpectralLibraryEntry>comparingDouble(entry -> Math.abs(
                entry.getAsFloat(DBEntryField.RT).orElse(Float.POSITIVE_INFINITY) - target.rt()))
            .thenComparingDouble(entry -> Math.abs(entry.getPrecursorMZ() - target.mz())));
  }

  static @NotNull SpectralLibraryEntry requireExpectedSpectrum(
      @NotNull final SpectralLibrary library, @NotNull final File libraryFile,
      @NotNull final DiaSlidingMzExpectedTarget target, @NotNull final MZTolerance mzTolerance,
      @NotNull final RTTolerance rtTolerance) {
    return findExpectedSpectrum(library, target, mzTolerance, rtTolerance).orElseThrow(
        () -> new IllegalArgumentException(
            describeMissingSpectrum(library, libraryFile, target, mzTolerance, rtTolerance)));
  }

  static @NotNull String describeMissingSpectrum(@NotNull final SpectralLibrary library,
      @NotNull final File libraryFile, @NotNull final DiaSlidingMzExpectedTarget target,
      @NotNull final MZTolerance mzTolerance, @NotNull final RTTolerance rtTolerance) {
    final String normalizedTargetName = normalizeCompoundName(target.compoundName());
    final List<SpectralLibraryEntry> nameMatches = library.stream()
        .filter(entry -> normalizedTargetName.equals(normalizeCompoundName(entryName(entry))))
        .toList();
    if (nameMatches.isEmpty()) {
      return "No library spectrum named '%s' in %s".formatted(target.compoundName(),
          libraryFile.getAbsolutePath());
    }

    final List<SpectralLibraryEntry> mzMatches = nameMatches.stream().filter(
        entry -> entry.getPrecursorMZ() != null && mzTolerance.checkWithinTolerance(target.mz(),
            entry.getPrecursorMZ())).toList();
    if (mzMatches.isEmpty()) {
      final SpectralLibraryEntry closest = nameMatches.stream()
          .filter(entry -> entry.getPrecursorMZ() != null)
          .min(Comparator.comparingDouble(entry -> Math.abs(entry.getPrecursorMZ() - target.mz())))
          .orElse(nameMatches.get(0));
      return "No library spectrum for '%s' matches target m/z %.6f; closest precursor is %s in %s".formatted(
          target.compoundName(), target.mz(), Objects.toString(closest.getPrecursorMZ(), "missing"),
          libraryFile.getAbsolutePath());
    }

    final Optional<SpectralLibraryEntry> closestRt = mzMatches.stream()
        .filter(entry -> entry.getAsFloat(DBEntryField.RT).isPresent()).min(
            Comparator.comparingDouble(
                entry -> Math.abs(entry.getAsFloat(DBEntryField.RT).orElseThrow() - target.rt())));
    if (closestRt.isEmpty()) {
      return "Library spectrum for '%s' at m/z %.6f has no RT in %s".formatted(
          target.compoundName(), target.mz(), libraryFile.getAbsolutePath());
    }

    final SpectralLibraryEntry closest = closestRt.orElseThrow();
    final float libraryRt = closest.getAsFloat(DBEntryField.RT).orElseThrow();
    return ("Library spectrum for '%s' at m/z %.6f has RT %.4f, which differs from target RT "
        + "%.4f by %.4f min and is outside %s in %s").formatted(target.compoundName(), target.mz(),
        libraryRt, target.rt(), Math.abs(libraryRt - target.rt()), rtTolerance,
        libraryFile.getAbsolutePath());
  }

  static @NotNull String entryName(@NotNull final SpectralLibraryEntry entry) {
    return Objects.toString(entry.getField(DBEntryField.NAME).orElse(""), "").strip();
  }

  private static @NotNull String normalizeCompoundName(@NotNull final String name) {
    // assumption: a trailing "-2" denotes the second chromatographic RT of the same spectrum.
    return name.strip().replaceFirst("(?i)-2$", "").toLowerCase(Locale.ROOT);
  }
}
