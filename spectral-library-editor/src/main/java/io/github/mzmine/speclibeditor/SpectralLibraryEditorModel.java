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

import io.github.mzmine.datamodel.structures.MolecularStructure;
import io.github.mzmine.util.spectraldb.entry.DBEntryField;
import io.github.mzmine.util.spectraldb.entry.SpectralLibraryEntry;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stores observable editor state shared between controller and view.
 */
public final class SpectralLibraryEditorModel {

  private static final @NotNull List<DBEntryField> EDITABLE_FIELDS = Arrays.stream(DBEntryField.values())
      .filter(field -> field != DBEntryField.UNSPECIFIED).toList();

  private final @NotNull ObservableList<SpectralLibraryEntry> entries = FXCollections.observableArrayList();
  private final @NotNull ObjectProperty<@Nullable SpectralLibraryEntry> selectedEntry =
      new SimpleObjectProperty<>();

  private final @NotNull StringProperty statusText = new SimpleStringProperty(
      "Open a spectral library to start editing.");
  private final @NotNull StringProperty currentFileText = new SimpleStringProperty("No file loaded");
  private final @NotNull StringProperty windowTitle = new SimpleStringProperty("MZmine Spectral Library Editor");
  private final @NotNull BooleanProperty saveEnabled = new SimpleBooleanProperty(false);
  private final @NotNull BooleanProperty metadataEnabled = new SimpleBooleanProperty(false);
  private final @NotNull ObjectProperty<@NotNull DBEntryField> filterField =
      new SimpleObjectProperty<>(DBEntryField.NAME);
  private final @NotNull StringProperty filterFieldText = new SimpleStringProperty(
      DBEntryField.NAME.toString());
  private final @NotNull StringProperty filterText = new SimpleStringProperty("");
  private final @NotNull BooleanProperty structureVisible = new SimpleBooleanProperty(false);
  private final @NotNull ObjectProperty<@Nullable MolecularStructure> structureMolecule =
      new SimpleObjectProperty<>();
  private final @NotNull LongProperty listRefreshVersion = new SimpleLongProperty(0);

  private final @NotNull Map<DBEntryField, StringProperty> metadataText = new EnumMap<>(DBEntryField.class);
  private final @NotNull Map<DBEntryField, BooleanProperty> metadataError = new EnumMap<>(DBEntryField.class);

  /**
   * Initializes per-field metadata properties used by the editor form.
   */
  public SpectralLibraryEditorModel() {
    for (final DBEntryField field : EDITABLE_FIELDS) {
      metadataText.put(field, new SimpleStringProperty(""));
      metadataError.put(field, new SimpleBooleanProperty(false));
    }
  }

  /**
   * Returns metadata fields that can be edited in the UI.
   *
   * @return immutable list of editable fields.
   */
  public @NotNull List<DBEntryField> getEditableFields() {
    return EDITABLE_FIELDS;
  }

  /**
   * Returns the observable list of loaded library entries.
   *
   * @return entry list bound to the entry view.
   */
  public @NotNull ObservableList<SpectralLibraryEntry> getEntries() {
    return entries;
  }

  /**
   * Returns the currently selected entry.
   *
   * @return selected entry or {@code null} if nothing is selected.
   */
  public @Nullable SpectralLibraryEntry getSelectedEntry() {
    return selectedEntry.get();
  }

  /**
   * Returns the selected entry property.
   *
   * @return property used to observe and update selection.
   */
  public @NotNull ObjectProperty<@Nullable SpectralLibraryEntry> selectedEntryProperty() {
    return selectedEntry;
  }

  /**
   * Updates the selected entry.
   *
   * @param selectedEntry the new selected entry or {@code null}.
   */
  public void setSelectedEntry(@Nullable final SpectralLibraryEntry selectedEntry) {
    this.selectedEntry.set(selectedEntry);
  }

  /**
   * Returns the status text property shown in the status bar.
   *
   * @return status text property.
   */
  public @NotNull StringProperty statusTextProperty() {
    return statusText;
  }

  /**
   * Updates the status text shown in the status bar.
   *
   * @param statusText the new status message.
   */
  public void setStatusText(@NotNull final String statusText) {
    this.statusText.set(statusText);
  }

  /**
   * Returns the property containing the current file label text.
   *
   * @return current file label property.
   */
  public @NotNull StringProperty currentFileTextProperty() {
    return currentFileText;
  }

  /**
   * Updates the current file label text.
   *
   * @param currentFileText text shown for the current file.
   */
  public void setCurrentFileText(@NotNull final String currentFileText) {
    this.currentFileText.set(currentFileText);
  }

  /**
   * Returns the window title property.
   *
   * @return title property bound to the stage.
   */
  public @NotNull StringProperty windowTitleProperty() {
    return windowTitle;
  }

  /**
   * Updates the window title.
   *
   * @param windowTitle the new title text.
   */
  public void setWindowTitle(@NotNull final String windowTitle) {
    this.windowTitle.set(windowTitle);
  }

  /**
   * Returns the property controlling whether saving is enabled.
   *
   * @return save-enabled property.
   */
  public @NotNull BooleanProperty saveEnabledProperty() {
    return saveEnabled;
  }

  /**
   * Sets whether save actions are enabled.
   *
   * @param saveEnabled {@code true} if saving should be available.
   */
  public void setSaveEnabled(final boolean saveEnabled) {
    this.saveEnabled.set(saveEnabled);
  }

  /**
   * Returns the property controlling metadata editor enablement.
   *
   * @return metadata-enabled property.
   */
  public @NotNull BooleanProperty metadataEnabledProperty() {
    return metadataEnabled;
  }

  /**
   * Sets whether metadata fields are editable.
   *
   * @param metadataEnabled {@code true} to enable metadata editing.
   */
  public void setMetadataEnabled(final boolean metadataEnabled) {
    this.metadataEnabled.set(metadataEnabled);
  }

  /**
   * Returns the selected metadata field used for list filtering.
   *
   * @return filter field selection.
   */
  public @NotNull DBEntryField getFilterField() {
    return filterField.get();
  }

  /**
   * Returns the selected metadata field property used for list filtering.
   *
   * @return filter field property.
   */
  public @NotNull ObjectProperty<@NotNull DBEntryField> filterFieldProperty() {
    return filterField;
  }

  /**
   * Updates the selected metadata field used for list filtering.
   *
   * @param filterField filter field to apply.
   */
  public void setFilterField(@NotNull final DBEntryField filterField) {
    this.filterField.set(filterField);
  }

  /**
   * Returns the current filter field label text.
   *
   * @return filter field label text.
   */
  public @NotNull String getFilterFieldText() {
    return filterFieldText.get();
  }

  /**
   * Returns the filter field label text property.
   *
   * @return filter field label property.
   */
  public @NotNull StringProperty filterFieldTextProperty() {
    return filterFieldText;
  }

  /**
   * Updates the filter field label text.
   *
   * @param filterFieldText filter field label text entered by the user.
   */
  public void setFilterFieldText(@NotNull final String filterFieldText) {
    this.filterFieldText.set(filterFieldText);
  }

  /**
   * Returns the current text query used for list filtering.
   *
   * @return filter text query.
   */
  public @NotNull String getFilterText() {
    return filterText.get();
  }

  /**
   * Returns the text query property used for list filtering.
   *
   * @return filter text property.
   */
  public @NotNull StringProperty filterTextProperty() {
    return filterText;
  }

  /**
   * Updates the text query used for list filtering.
   *
   * @param filterText new filter query text.
   */
  public void setFilterText(@NotNull final String filterText) {
    this.filterText.set(filterText);
  }

  /**
   * Returns whether the structure preview should be shown.
   *
   * @return {@code true} if the structure preview should be visible.
   */
  public boolean isStructureVisible() {
    return structureVisible.get();
  }

  /**
   * Returns the structure preview visibility property.
   *
   * @return visibility property for the structure preview pane.
   */
  public @NotNull BooleanProperty structureVisibleProperty() {
    return structureVisible;
  }

  /**
   * Sets whether the structure preview should be shown.
   *
   * @param structureVisible {@code true} to show the structure preview.
   */
  public void setStructureVisible(final boolean structureVisible) {
    this.structureVisible.set(structureVisible);
  }

  /**
   * Returns the currently displayed molecular structure.
   *
   * @return structure molecule or {@code null} if unavailable.
   */
  public @Nullable MolecularStructure getStructureMolecule() {
    return structureMolecule.get();
  }

  /**
   * Returns the molecular structure property for the preview component.
   *
   * @return structure molecule property.
   */
  public @NotNull ObjectProperty<@Nullable MolecularStructure> structureMoleculeProperty() {
    return structureMolecule;
  }

  /**
   * Updates the molecular structure displayed in the preview.
   *
   * @param structureMolecule molecule to display or {@code null}.
   */
  public void setStructureMolecule(@Nullable final MolecularStructure structureMolecule) {
    this.structureMolecule.set(structureMolecule);
  }

  /**
   * Returns a version counter used to trigger list refreshes.
   *
   * @return list refresh version property.
   */
  public @NotNull LongProperty listRefreshVersionProperty() {
    return listRefreshVersion;
  }

  /**
   * Increments the list refresh version to request a UI refresh.
   */
  public void requestListRefresh() {
    listRefreshVersion.set(listRefreshVersion.get() + 1);
  }

  /**
   * Returns the editable text property for a metadata field.
   *
   * @param field metadata field key.
   * @return text property for the field editor.
   */
  public @NotNull StringProperty metadataTextProperty(@NotNull final DBEntryField field) {
    return metadataText.get(field);
  }

  /**
   * Returns the current editor text for a metadata field.
   *
   * @param field metadata field key.
   * @return current text value.
   */
  public @NotNull String getMetadataText(@NotNull final DBEntryField field) {
    return metadataTextProperty(field).get();
  }

  /**
   * Updates editor text for a metadata field.
   *
   * @param field metadata field key.
   * @param text new editor text.
   */
  public void setMetadataText(@NotNull final DBEntryField field, @NotNull final String text) {
    metadataTextProperty(field).set(text);
  }

  /**
   * Returns the validation error property for a metadata field.
   *
   * @param field metadata field key.
   * @return error state property for the field.
   */
  public @NotNull BooleanProperty metadataErrorProperty(@NotNull final DBEntryField field) {
    return metadataError.get(field);
  }

  /**
   * Sets the validation error state for a metadata field.
   *
   * @param field metadata field key.
   * @param hasError {@code true} if the field currently has a validation error.
   */
  public void setMetadataError(@NotNull final DBEntryField field, final boolean hasError) {
    metadataErrorProperty(field).set(hasError);
  }
}
