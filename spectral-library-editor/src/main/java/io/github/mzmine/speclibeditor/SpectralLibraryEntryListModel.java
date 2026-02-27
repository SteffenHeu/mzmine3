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
import java.util.Arrays;
import java.util.List;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stores observable state for the entry list panel.
 */
public final class SpectralLibraryEntryListModel {

  private static final @NotNull List<DBEntryField> EDITABLE_FIELDS = Arrays.stream(DBEntryField.values())
      .filter(field -> field != DBEntryField.UNSPECIFIED).toList();

  private final @NotNull ObservableList<SpectralLibraryEntry> entries;
  private final @NotNull FilteredList<SpectralLibraryEntry> filteredEntries;
  private final @NotNull ObjectProperty<@NotNull DBEntryField> filterField =
      new SimpleObjectProperty<>(DBEntryField.NAME);
  private final @NotNull StringProperty filterFieldText =
      new SimpleStringProperty(DBEntryField.NAME.toString());
  private final @NotNull StringProperty filterText = new SimpleStringProperty("");
  private final @NotNull ObjectProperty<@Nullable SpectralLibraryEntry> primaryEntry =
      new SimpleObjectProperty<>();
  private final @NotNull ObservableList<SpectralLibraryEntry> selectedEntries =
      FXCollections.observableArrayList();
  private final @NotNull ObservableList<SpectralLibraryEntry> checkedEntries =
      FXCollections.observableArrayList();
  private final @NotNull LongProperty listRefreshVersion = new SimpleLongProperty(0);

  /**
   * Creates a list model backed by the shared library entries list.
   *
   * @param entries shared list of all library entries.
   */
  public SpectralLibraryEntryListModel(@NotNull final ObservableList<SpectralLibraryEntry> entries) {
    this.entries = entries;
    filteredEntries = new FilteredList<>(entries, _ -> true);
  }

  /**
   * Returns metadata fields that can be used for filtering.
   *
   * @return list of filterable metadata fields.
   */
  public @NotNull List<DBEntryField> getEditableFields() {
    return EDITABLE_FIELDS;
  }

  /**
   * Returns the unfiltered entries list.
   *
   * @return backing entries list.
   */
  public @NotNull ObservableList<SpectralLibraryEntry> getEntries() {
    return entries;
  }

  /**
   * Returns the filtered entries list shown in the checklist.
   *
   * @return filtered entries view.
   */
  public @NotNull FilteredList<SpectralLibraryEntry> getFilteredEntries() {
    return filteredEntries;
  }

  /**
   * Returns the selected metadata field used for filtering.
   *
   * @return active filter field.
   */
  public @NotNull DBEntryField getFilterField() {
    return filterField.get();
  }

  /**
   * Returns the selected metadata field property used for filtering.
   *
   * @return filter field property.
   */
  public @NotNull ObjectProperty<@NotNull DBEntryField> filterFieldProperty() {
    return filterField;
  }

  /**
   * Updates the selected metadata field used for filtering.
   *
   * @param filterField new filter field.
   */
  public void setFilterField(@NotNull final DBEntryField filterField) {
    this.filterField.set(filterField);
  }

  /**
   * Returns the filter field input text.
   *
   * @return filter field text.
   */
  public @NotNull String getFilterFieldText() {
    return filterFieldText.get();
  }

  /**
   * Returns the filter field input property.
   *
   * @return filter field text property.
   */
  public @NotNull StringProperty filterFieldTextProperty() {
    return filterFieldText;
  }

  /**
   * Updates the filter field input text.
   *
   * @param filterFieldText new filter field text.
   */
  public void setFilterFieldText(@NotNull final String filterFieldText) {
    this.filterFieldText.set(filterFieldText);
  }

  /**
   * Returns the filter query text.
   *
   * @return filter query.
   */
  public @NotNull String getFilterText() {
    return filterText.get();
  }

  /**
   * Returns the filter query property.
   *
   * @return filter query property.
   */
  public @NotNull StringProperty filterTextProperty() {
    return filterText;
  }

  /**
   * Updates the filter query text.
   *
   * @param filterText new filter query.
   */
  public void setFilterText(@NotNull final String filterText) {
    this.filterText.set(filterText);
  }

  /**
   * Returns the primary entry shown in spectrum and metadata panes.
   *
   * @return selected primary entry or {@code null}.
   */
  public @Nullable SpectralLibraryEntry getPrimaryEntry() {
    return primaryEntry.get();
  }

  /**
   * Returns the primary entry property.
   *
   * @return primary entry property.
   */
  public @NotNull ObjectProperty<@Nullable SpectralLibraryEntry> primaryEntryProperty() {
    return primaryEntry;
  }

  /**
   * Updates the primary entry shown in detail panes.
   *
   * @param primaryEntry new primary entry or {@code null}.
   */
  public void setPrimaryEntry(@Nullable final SpectralLibraryEntry primaryEntry) {
    this.primaryEntry.set(primaryEntry);
  }

  /**
   * Returns the currently selected entries in the checklist.
   *
   * @return selected entries list.
   */
  public @NotNull ObservableList<SpectralLibraryEntry> getSelectedEntries() {
    return selectedEntries;
  }

  /**
   * Replaces the selected entries list.
   *
   * @param selectedEntries new selected entries.
   */
  public void setSelectedEntries(@NotNull final List<SpectralLibraryEntry> selectedEntries) {
    this.selectedEntries.setAll(selectedEntries);
  }

  /**
   * Returns the currently checked entries in the checklist.
   *
   * @return checked entries list.
   */
  public @NotNull ObservableList<SpectralLibraryEntry> getCheckedEntries() {
    return checkedEntries;
  }

  /**
   * Replaces the checked entries list.
   *
   * @param checkedEntries new checked entries.
   */
  public void setCheckedEntries(@NotNull final List<SpectralLibraryEntry> checkedEntries) {
    this.checkedEntries.setAll(checkedEntries);
  }

  /**
   * Clears current selected and checked entry tracking.
   */
  public void clearSelectionState() {
    selectedEntries.clear();
    checkedEntries.clear();
  }

  /**
   * Returns a version counter used to request list refreshes.
   *
   * @return list refresh version property.
   */
  public @NotNull LongProperty listRefreshVersionProperty() {
    return listRefreshVersion;
  }

  /**
   * Increments the refresh counter to trigger a checklist refresh.
   */
  public void requestListRefresh() {
    listRefreshVersion.set(listRefreshVersion.get() + 1);
  }
}

