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

import io.github.mzmine.util.spectraldb.entry.DBEntryField;
import io.github.mzmine.util.spectraldb.entry.SpectralLibraryEntry;
import java.util.Locale;
import javafx.scene.control.ListCell;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Renders a spectral library entry row in the entry list view.
 */
public final class SpectralLibraryEntryListCell extends ListCell<SpectralLibraryEntry> {

  /**
   * Creates a compact display label for an entry without an index.
   *
   * @param item entry to format.
   * @return formatted entry label.
   */
  public static @NotNull String toDisplayText(@NotNull final SpectralLibraryEntry item) {
    return toDisplayText(item, -1, false);
  }

  /**
   * Creates a compact display label for an entry without an index and optional edited marker.
   *
   * @param item entry to format.
   * @param edited whether the entry has unsaved metadata edits.
   * @return formatted entry label.
   */
  public static @NotNull String toDisplayText(@NotNull final SpectralLibraryEntry item,
      final boolean edited) {
    return toDisplayText(item, -1, edited);
  }

  /**
   * Creates a compact display label for an entry with an optional index prefix.
   *
   * @param item entry to format.
   * @param index zero-based index or a negative value to omit the index.
   * @return formatted entry label.
   */
  public static @NotNull String toDisplayText(@NotNull final SpectralLibraryEntry item,
      final int index) {
    return toDisplayText(item, index, false);
  }

  /**
   * Creates a compact display label for an entry with optional index and edited marker.
   *
   * @param item entry to format.
   * @param index zero-based index or a negative value to omit the index.
   * @param edited whether the entry has unsaved metadata edits.
   * @return formatted entry label.
   */
  public static @NotNull String toDisplayText(@NotNull final SpectralLibraryEntry item,
      final int index, final boolean edited) {
    final String name = item.getAsString(DBEntryField.NAME)
        .or(() -> item.getAsString(DBEntryField.ENTRY_ID))
        .orElse(index >= 0 ? "Entry " + (index + 1) : "Entry");
    final String precursor = item.getAsDouble(DBEntryField.PRECURSOR_MZ)
        .map(value -> String.format(Locale.US, "%.5f", value)).orElse("n/a");
    final String prefix = index >= 0 ? (index + 1) + " | " : "";
    final String editedPrefix = edited ? "* " : "";
    return editedPrefix + prefix + name + " | m/z " + precursor;
  }

  /**
   * Updates the cell text with a compact label for the given entry.
   *
   * @param item entry to render.
   * @param empty whether the cell is currently empty.
   */
  @Override
  protected void updateItem(@Nullable final SpectralLibraryEntry item, final boolean empty) {
    super.updateItem(item, empty);
    if (empty || item == null) {
      setText(null);
      return;
    }
    setText(toDisplayText(item, getIndex()));
  }
}
