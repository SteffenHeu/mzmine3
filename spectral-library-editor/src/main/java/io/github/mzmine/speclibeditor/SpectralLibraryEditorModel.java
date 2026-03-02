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
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
  private final @NotNull StringProperty windowTitle = new SimpleStringProperty("mzmine Spectral Library Editor");
  private final @NotNull BooleanProperty saveEnabled = new SimpleBooleanProperty(false);
  private final @NotNull BooleanProperty metadataEnabled = new SimpleBooleanProperty(false);
  private final @NotNull ObjectProperty<@Nullable MolecularStructure> structureMolecule =
      new SimpleObjectProperty<>();

  private final @NotNull Map<DBEntryField, StringProperty> metadataText = new EnumMap<>(DBEntryField.class);
  private final @NotNull Map<DBEntryField, BooleanProperty> metadataError = new EnumMap<>(DBEntryField.class);
  private final @NotNull Map<DBEntryField, BooleanProperty> metadataEdited = new EnumMap<>(DBEntryField.class);
  private final @NotNull IdentityHashMap<SpectralLibraryEntry, EnumMap<DBEntryField, String>>
      originalValuesByEntry = new IdentityHashMap<>();
  private final @NotNull IdentityHashMap<SpectralLibraryEntry, EnumSet<DBEntryField>>
      changedFieldsByEntry = new IdentityHashMap<>();

  /**
   * Initializes per-field metadata properties used by the editor form.
   */
  public SpectralLibraryEditorModel() {
    for (final DBEntryField field : EDITABLE_FIELDS) {
      metadataText.put(field, new SimpleStringProperty(""));
      metadataError.put(field, new SimpleBooleanProperty(false));
      metadataEdited.put(field, new SimpleBooleanProperty(false));
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

  /**
   * Returns the edited-state property for a metadata field.
   *
   * @param field metadata field key.
   * @return edited state property for the field.
   */
  public @NotNull BooleanProperty metadataEditedProperty(@NotNull final DBEntryField field) {
    return metadataEdited.get(field);
  }

  /**
   * Sets the edited state for a metadata field.
   *
   * @param field metadata field key.
   * @param edited {@code true} if the field differs from its loaded/saved baseline value.
   */
  public void setMetadataEdited(@NotNull final DBEntryField field, final boolean edited) {
    metadataEditedProperty(field).set(edited);
  }

  /**
   * Clears all baseline snapshots and tracked field changes.
   */
  public void clearEditedEntryTracking() {
    originalValuesByEntry.clear();
    changedFieldsByEntry.clear();
  }

  /**
   * Sets the metadata baseline snapshot for one entry.
   *
   * @param entry entry key.
   * @param snapshot baseline field values.
   */
  public void setEntryBaselineSnapshot(@NotNull final SpectralLibraryEntry entry,
      @NotNull final Map<DBEntryField, String> snapshot) {
    final EnumMap<DBEntryField, String> baseline = new EnumMap<>(DBEntryField.class);
    baseline.putAll(snapshot);
    originalValuesByEntry.put(entry, baseline);
    changedFieldsByEntry.remove(entry);
  }

  /**
   * Removes all tracking state for one entry.
   *
   * @param entry entry to remove.
   */
  public void removeEditedEntryTracking(@NotNull final SpectralLibraryEntry entry) {
    originalValuesByEntry.remove(entry);
    changedFieldsByEntry.remove(entry);
  }

  /**
   * Checks whether an entry has at least one changed metadata field.
   *
   * @param entry entry to inspect.
   * @return {@code true} if the entry is edited.
   */
  public boolean isEntryEdited(@Nullable final SpectralLibraryEntry entry) {
    if (entry == null) {
      return false;
    }
    final EnumSet<DBEntryField> changedFields = changedFieldsByEntry.get(entry);
    return changedFields != null && !changedFields.isEmpty();
  }

  /**
   * Checks whether one metadata field of an entry is edited.
   *
   * @param entry entry to inspect.
   * @param field field to inspect.
   * @return {@code true} if the field differs from baseline.
   */
  public boolean isFieldEdited(@Nullable final SpectralLibraryEntry entry,
      @NotNull final DBEntryField field) {
    if (entry == null) {
      return false;
    }
    final EnumSet<DBEntryField> changedFields = changedFieldsByEntry.get(entry);
    return changedFields != null && changedFields.contains(field);
  }

  /**
   * Updates field-level tracking by comparing current value with baseline value.
   *
   * @param entry entry being edited.
   * @param field field being edited.
   * @param currentValue serialized current field value.
   */
  public void updateFieldChangeTracking(@NotNull final SpectralLibraryEntry entry,
      @NotNull final DBEntryField field, @NotNull final String currentValue) {
    final EnumMap<DBEntryField, String> baseline = originalValuesByEntry.get(entry);
    final String baselineValue = baseline == null ? "" : baseline.getOrDefault(field, "");
    final EnumSet<DBEntryField> changedFields = changedFieldsByEntry.computeIfAbsent(entry,
        _ -> EnumSet.noneOf(DBEntryField.class));

    if (Objects.equals(currentValue, baselineValue)) {
      changedFields.remove(field);
    } else {
      changedFields.add(field);
    }

    if (changedFields.isEmpty()) {
      changedFieldsByEntry.remove(entry);
    }
  }

  /**
   * Checks whether at least one entry has edited metadata fields.
   *
   * @return {@code true} if any entry is edited.
   */
  public boolean hasAnyEntryEdits() {
    return !changedFieldsByEntry.isEmpty();
  }
}
