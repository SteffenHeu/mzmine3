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

package io.github.mzmine.speclibeditor;

import io.github.mzmine.modules.io.spectraldbsubmit.formats.MGFEntryGenerator;
import io.github.mzmine.modules.io.spectraldbsubmit.formats.MSPEntryGenerator;
import io.github.mzmine.modules.io.spectraldbsubmit.formats.MZmineJsonGenerator;
import io.github.mzmine.modules.io.spectraldbsubmit.formats.SpectrumString;
import io.github.mzmine.parameters.parametertypes.IntensityNormalizer;
import io.github.mzmine.util.spectraldb.entry.SpectralLibrary;
import io.github.mzmine.util.spectraldb.entry.SpectralLibraryEntry;
import io.github.mzmine.util.spectraldb.parser.AutoLibraryParser;
import io.github.mzmine.util.spectraldb.parser.UnsupportedFormatException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Provides load and save operations for spectral library files.
 */
public final class SpectralLibraryFileService {

  private static final Logger logger = Logger.getLogger(SpectralLibraryFileService.class.getName());
  private static final int PARSER_BUFFER_SIZE = 1000;
  private static final @NotNull IntensityNormalizer EXPORT_NORMALIZER =
      IntensityNormalizer.createDefault();

  /**
   * Parses a spectral library file into a {@link SpectralLibrary}.
   *
   * @param sourceFile input file to parse.
   * @return parsed library containing all entries.
   * @throws IOException if file reading fails.
   * @throws UnsupportedFormatException if the format is not supported.
   */
  public @NotNull SpectralLibrary loadLibrary(@NotNull final File sourceFile)
      throws IOException, UnsupportedFormatException {
    final SpectralLibrary library = new SpectralLibrary(null, sourceFile);
    final AutoLibraryParser parser = new AutoLibraryParser(PARSER_BUFFER_SIZE,
        (entries, alreadyProcessed) -> library.addEntries(entries));

    final boolean success = parser.parse(null, sourceFile, library);
    if (!success) {
      throw new UnsupportedFormatException(
          "Could not parse spectral library file " + sourceFile.getAbsolutePath());
    }

    library.trim();
    logger.info(() -> "Loaded spectral library " + sourceFile.getAbsolutePath() + " entries="
        + library.getEntries().size());
    return library;
  }

  /**
   * Writes all library entries to a target file in the selected format.
   *
   * @param library source library to export.
   * @param targetFile output file destination.
   * @param format output format.
   * @throws IOException if writing fails.
   */
  public void saveLibrary(@NotNull final SpectralLibrary library, @NotNull final File targetFile,
      @NotNull final SpectralLibraryFileFormat format) throws IOException {
    final List<SpectralLibraryEntry> entries = library.getEntries();
    try (BufferedWriter writer = Files.newBufferedWriter(targetFile.toPath(), StandardCharsets.UTF_8)) {
      int entryNumber = 0;
      for (final SpectralLibraryEntry entry : entries) {
        entryNumber++;
        writeEntry(writer, entry, format, entryNumber);
      }
    }
    logger.info(
        () -> "Saved spectral library " + targetFile.getAbsolutePath() + " entries=" + entries.size());
  }

  /**
   * Ensures a file name has a known extension by appending the format suffix if needed.
   *
   * @param file selected target file.
   * @param format chosen output format.
   * @return file with a valid output extension.
   */
  public @NotNull File ensureExtension(@NotNull final File file,
      @NotNull final SpectralLibraryFileFormat format) {
    if (SpectralLibraryFileFormat.fromFile(file) != null) {
      return file;
    }

    final File parent = file.getParentFile();
    final String newName = file.getName() + format.getFileSuffix();
    return parent == null ? new File(newName) : new File(parent, newName);
  }

  /**
   * Writes one library entry to the output writer.
   *
   * @param writer output writer.
   * @param entry entry to serialize.
   * @param format output format.
   * @param entryNumber one-based index used in error reporting.
   * @throws IOException if serialization or writing fails.
   */
  private void writeEntry(@NotNull final BufferedWriter writer, @NotNull final SpectralLibraryEntry entry,
      @NotNull final SpectralLibraryFileFormat format, final int entryNumber) throws IOException {
    final String text;
    try {
      text = switch (format) {
        case MGF -> {
          final SpectrumString spectrumString = MGFEntryGenerator.createMGFEntry(entry, EXPORT_NORMALIZER);
          yield spectrumString.spectrum();
        }
        case MSP -> MSPEntryGenerator.createMSPEntry(entry, EXPORT_NORMALIZER);
        case MZMINE_JSON -> MZmineJsonGenerator.generateJSON(entry, EXPORT_NORMALIZER) + "\n";
      };
    } catch (final Exception e) {
      throw new IOException("Failed to convert entry " + entryNumber + " to format " + format.name(), e);
    }
    writer.write(text);
    if (!text.endsWith("\n")) {
      writer.newLine();
    }
  }
}
