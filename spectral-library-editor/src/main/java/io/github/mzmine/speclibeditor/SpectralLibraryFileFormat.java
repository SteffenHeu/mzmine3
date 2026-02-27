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

import java.io.File;
import java.util.Locale;
import javafx.stage.FileChooser.ExtensionFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Supported spectral library file formats and chooser integration helpers.
 */
public enum SpectralLibraryFileFormat {
  MGF("MGF (*.mgf)", "mgf"), MSP("MSP (*.msp)", "msp"), MZMINE_JSON("MZmine JSON (*.json)",
      "json");

  private final @NotNull String description;
  private final @NotNull String extension;

  /**
   * Creates a format definition.
   *
   * @param description chooser display text.
   * @param extension canonical file extension without dot.
   */
  SpectralLibraryFileFormat(@NotNull final String description, @NotNull final String extension) {
    this.description = description;
    this.extension = extension;
  }

  /**
   * Returns the canonical extension without leading dot.
   *
   * @return extension identifier.
   */
  public @NotNull String getExtension() {
    return extension;
  }

  /**
   * Returns the file suffix including a leading dot.
   *
   * @return extension suffix for file names.
   */
  public @NotNull String getFileSuffix() {
    return "." + extension;
  }

  /**
   * Creates a JavaFX extension filter for this format.
   *
   * @return extension filter matching the format suffix.
   */
  public @NotNull ExtensionFilter toExtensionFilter() {
    return new ExtensionFilter(description, "*" + getFileSuffix());
  }

  /**
   * Detects a format from a file extension.
   *
   * @param file file to inspect.
   * @return detected format or {@code null} if unsupported.
   */
  public static @Nullable SpectralLibraryFileFormat fromFile(@NotNull final File file) {
    final String name = file.getName();
    final int dotIndex = name.lastIndexOf('.');
    if (dotIndex < 0 || dotIndex == name.length() - 1) {
      return null;
    }
    final String extension = name.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    return switch (extension) {
      case "mgf" -> MGF;
      case "msp", "msp_riken", "msp_nist" -> MSP;
      case "json" -> MZMINE_JSON;
      default -> null;
    };
  }

  /**
   * Resolves a format from a selected chooser extension filter.
   *
   * @param filter selected file chooser filter.
   * @return matching format or JSON as fallback.
   */
  public static @NotNull SpectralLibraryFileFormat fromExtensionFilter(
      @Nullable final ExtensionFilter filter) {
    if (filter == null || filter.getExtensions().isEmpty()) {
      return MZMINE_JSON;
    }
    final String extensionPattern = filter.getExtensions().get(0).toLowerCase(Locale.ROOT);
    return switch (extensionPattern) {
      case "*.mgf" -> MGF;
      case "*.msp" -> MSP;
      case "*.json" -> MZMINE_JSON;
      default -> MZMINE_JSON;
    };
  }
}
