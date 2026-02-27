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

import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.datamodel.features.types.DataTypes;
import io.github.mzmine.datamodel.structures.MolecularStructure;
import io.github.mzmine.datamodel.structures.StructureParser;
import io.github.mzmine.gui.preferences.Themes;
import io.github.mzmine.javafx.mvci.FxController;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.util.spectraldb.entry.DBEntryField;
import io.github.mzmine.util.spectraldb.entry.SpectralLibrary;
import io.github.mzmine.util.spectraldb.entry.SpectralLibraryEntry;
import io.github.mzmine.util.spectraldb.parser.UnsupportedFormatException;
import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Coordinates user actions, file IO and model updates for the editor.
 */
public final class SpectralLibraryEditorController extends FxController<SpectralLibraryEditorModel> {

  private static final Logger logger = Logger.getLogger(SpectralLibraryEditorController.class.getName());
  private static final @NotNull List<DBEntryField> LIST_LABEL_FIELDS = List.of(DBEntryField.NAME,
      DBEntryField.ENTRY_ID, DBEntryField.PRECURSOR_MZ);

  private final @NotNull Stage owner;
  private final @NotNull SpectralLibraryFileService fileService = new SpectralLibraryFileService();
  private final @NotNull SpectralLibraryEditorViewBuilder viewBuilder;

  private @Nullable SpectralLibrary currentLibrary;
  private @Nullable File currentFile;
  /**
   * Tracks whether the currently loaded library has unsaved edits.
   */
  private boolean dirty;
  /**
   * Guards metadata commits while the controller updates form fields programmatically.
   */
  private boolean updatingMetadataFields;

  /**
   * Creates a controller instance bound to the given owner stage.
   *
   * @param owner stage used as owner for dialogs and title binding.
   */
  public SpectralLibraryEditorController(@NotNull final Stage owner) {
    super(new SpectralLibraryEditorModel());
    this.owner = owner;
    viewBuilder = new SpectralLibraryEditorViewBuilder(model, this);
    owner.titleProperty().bind(model.windowTitleProperty());

    model.selectedEntryProperty().addListener((_, _, selectedEntry) -> {
      updateMetadataForSelection(selectedEntry);
      updateStructureForSelection(selectedEntry);
    });
    owner.sceneProperty().addListener((_, _, _) -> applyTheme());
    updateMetadataForSelection(null);
    updateStructureForSelection(null);
    applyTheme();
    updateWindowState();
  }

  /**
   * Returns the view builder used to construct the editor UI.
   *
   * @return editor view builder.
   */
  @Override
  protected @NotNull FxViewBuilder<SpectralLibraryEditorModel> getViewBuilder() {
    return viewBuilder;
  }

  /**
   * Handles the Open action and loads a selected spectral library file.
   */
  public void onOpenRequested() {
    if (dirty && !confirmDiscardChanges()) {
      return;
    }

    final FileChooser chooser = createOpenFileChooser();
    final File selectedFile = chooser.showOpenDialog(owner);
    if (selectedFile == null) {
      return;
    }

    loadLibrary(selectedFile);
  }

  /**
   * Handles save requests by delegating to Save As to avoid overwriting existing files.
   */
  public void onSaveRequested() {
    onSaveAsRequested();
  }

  /**
   * Handles the Save As action and writes the current library to a new file.
   */
  public void onSaveAsRequested() {
    if (currentLibrary == null) {
      setStatus("No library loaded.");
      return;
    }

    final FileChooser chooser = createSaveFileChooser();
    if (currentFile != null) {
      chooser.setInitialFileName(currentFile.getName());
    }
    final File selectedFile = chooser.showSaveDialog(owner);
    if (selectedFile == null) {
      return;
    }

    final SpectralLibraryFileFormat selectedFormat = Optional.ofNullable(
            SpectralLibraryFileFormat.fromFile(selectedFile))
        .orElseGet(() -> SpectralLibraryFileFormat.fromExtensionFilter(chooser.getSelectedExtensionFilter()));
    final File targetFile = fileService.ensureExtension(selectedFile, selectedFormat);
    saveLibrary(targetFile, selectedFormat);
  }

  /**
   * Applies metadata edits for a field to the currently selected entry.
   *
   * @param field metadata field that was committed by the user.
   */
  public void onMetadataCommit(@NotNull final DBEntryField field) {
    if (updatingMetadataFields) {
      return;
    }

    final SpectralLibraryEntry entry = model.getSelectedEntry();
    if (entry == null) {
      return;
    }

    final String input = model.getMetadataText(field).trim();
    if (input.isEmpty()) {
      final Object oldValue = entry.getFields().remove(field);
      model.setMetadataError(field, false);
      model.setMetadataText(field, "");
      if (oldValue != null) {
        markDirty();
      }
      refreshEntryListIfNeeded(field);
      if (isStructureField(field)) {
        updateStructureForSelection(entry);
      }
      return;
    }

    final Object converted = convertInput(field, input);
    if (converted == null) {
      model.setMetadataError(field, true);
      setStatus("Invalid value for " + field + ": " + input);
      return;
    }
    if (!passesDataTypeMapperValidation(field, input)) {
      model.setMetadataError(field, true);
      setStatus("Invalid value for " + field + ": " + input);
      return;
    }

    model.setMetadataError(field, false);
    model.setMetadataText(field, serializeValue(converted));
    final Object previousValue = entry.getFields().get(field);
    if (Objects.equals(previousValue, converted)) {
      return;
    }

    entry.putIfNotNull(field, converted);
    markDirty();
    refreshEntryListIfNeeded(field);
    if (isStructureField(field)) {
      updateStructureForSelection(entry);
    }
  }

  /**
   * Loads a spectral library file into the model.
   *
   * @param file input library file.
   */
  private void loadLibrary(@NotNull final File file) {
    try {
      final SpectralLibrary loadedLibrary = fileService.loadLibrary(file);
      currentLibrary = loadedLibrary;
      currentFile = file;
      dirty = false;

      model.getEntries().setAll(loadedLibrary.getEntries());
      if (loadedLibrary.getEntries().isEmpty()) {
        model.setSelectedEntry(null);
      } else {
        model.setSelectedEntry(loadedLibrary.getEntries().getFirst());
      }

      setStatus("Loaded " + file.getName() + " with " + loadedLibrary.getEntries().size() + " entries.");
      updateWindowState();
    } catch (final UnsupportedFormatException e) {
      logger.log(Level.WARNING, "Unsupported library format " + file.getAbsolutePath(), e);
      setStatus("Unsupported spectral library format: " + file.getName());
    } catch (final Exception e) {
      logger.log(Level.WARNING, "Cannot load spectral library " + file.getAbsolutePath(), e);
      setStatus("Failed to load file: " + e.getMessage());
    }
  }

  /**
   * Saves the current library to the specified output file and format.
   *
   * @param file target output file.
   * @param format output serialization format.
   */
  private void saveLibrary(@NotNull final File file, @NotNull final SpectralLibraryFileFormat format) {
    if (currentLibrary == null) {
      setStatus("No library loaded.");
      return;
    }

    try {
      fileService.saveLibrary(currentLibrary, file, format);
      currentFile = file;
      dirty = false;
      setStatus("Saved " + file.getName() + " in " + format.name() + " format.");
      updateWindowState();
    } catch (final Exception e) {
      logger.log(Level.WARNING, "Cannot save library to " + file.getAbsolutePath(), e);
      setStatus("Failed to save file: " + e.getMessage());
    }
  }

  /**
   * Synchronizes metadata text fields with the currently selected entry.
   *
   * @param entry selected entry or {@code null} if nothing is selected.
   */
  private void updateMetadataForSelection(@Nullable final SpectralLibraryEntry entry) {
    updatingMetadataFields = true;
    try {
      model.setMetadataEnabled(entry != null);
      for (final DBEntryField field : model.getEditableFields()) {
        model.setMetadataError(field, false);
        final String value = entry == null ? "" : serializeValue(entry.getField(field).orElse(null));
        model.setMetadataText(field, value);
      }
    } finally {
      updatingMetadataFields = false;
    }
  }

  /**
   * Converts editor input into the value type expected by the metadata field.
   *
   * @param field target metadata field.
   * @param input raw user input.
   * @return converted value or {@code null} if conversion fails.
   */
  private @Nullable Object convertInput(@NotNull final DBEntryField field, @NotNull final String input) {
    if (field.getObjectClass().equals(List.class)) {
      if (input.startsWith("[") && input.endsWith("]")) {
        final Object converted = field.tryConvertValue(input);
        if (converted != null) {
          return converted;
        }
      }
      // decision: keep simple comma-separated editing for list-like metadata fields.
      final List<String> values = input.lines().flatMap(line -> List.of(line.split(",")).stream())
          .map(String::trim).filter(value -> !value.isBlank()).toList();
      return values;
    }

    return field.tryConvertValue(input);
  }

  /**
   * Validates input with the DataType mapper when one is available.
   *
   * @param field target metadata field.
   * @param input raw user input.
   * @return {@code true} if mapper validation passes or no mapper exists.
   */
  private boolean passesDataTypeMapperValidation(@NotNull final DBEntryField field,
      @NotNull final String input) {
    final Class<? extends DataType> dataTypeClass = field.getDataType();
    final DataType<?> dataType = DataTypes.get(dataTypeClass);
    if (dataType == null) {
      return true;
    }

    final Function<@Nullable String, ?> mapper = dataType.getMapper();
    if (mapper == null) {
      return true;
    }

    try {
      // decision: treat null from mapper as validation failure for non-empty input.
      return mapper.apply(input) != null;
    } catch (Exception e) {
      logger.log(Level.FINEST, "Mapper validation failed for field " + field + " value " + input,
          e);
      return false;
    }
  }

  /**
   * Serializes an entry field value into a UI text representation.
   *
   * @param value value to serialize.
   * @return serialized text shown in metadata editors.
   */
  private @NotNull String serializeValue(@Nullable final Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof Collection<?> collection) {
      return collection.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }
    if (value.getClass().isArray()) {
      final int length = Array.getLength(value);
      final List<String> values = new ArrayList<>(length);
      for (int i = 0; i < length; i++) {
        values.add(String.valueOf(Array.get(value, i)));
      }
      return String.join(", ", values);
    }
    return String.valueOf(value);
  }

  /**
   * Refreshes the entry list when a label-relevant metadata field changes.
   *
   * @param field metadata field that changed.
   */
  private void refreshEntryListIfNeeded(@NotNull final DBEntryField field) {
    if (LIST_LABEL_FIELDS.contains(field)) {
      model.requestListRefresh();
    }
  }

  /**
   * Updates the structure preview based on SMILES and InChI values of the selected entry.
   *
   * @param entry selected entry or {@code null}.
   */
  private void updateStructureForSelection(@Nullable final SpectralLibraryEntry entry) {
    if (entry == null) {
      model.setStructureVisible(false);
      model.setStructureMolecule(null);
      return;
    }

    final String smiles = entry.getAsString(DBEntryField.SMILES).orElse(null);
    final String inchi = entry.getAsString(DBEntryField.INCHI).orElse(null);
    final boolean hasStructureInput = isNonBlank(smiles) || isNonBlank(inchi);
    model.setStructureVisible(hasStructureInput);
    if (!hasStructureInput) {
      model.setStructureMolecule(null);
      return;
    }

    final MolecularStructure structure = StructureParser.silent().parseStructure(smiles, inchi);
    model.setStructureMolecule(structure);
  }

  /**
   * Checks whether a value is not {@code null} and not blank.
   *
   * @param value value to test.
   * @return {@code true} if the value contains non-whitespace characters.
   */
  private boolean isNonBlank(@Nullable final String value) {
    return value != null && !value.isBlank();
  }

  /**
   * Checks whether a metadata field contributes to structure preview generation.
   *
   * @param field metadata field to test.
   * @return {@code true} if the field is SMILES or InChI.
   */
  private boolean isStructureField(@NotNull final DBEntryField field) {
    return switch (field) {
      case SMILES, INCHI -> true;
      default -> false;
    };
  }

  /**
   * Applies the fixed editor theme to the current scene.
   */
  private void applyTheme() {
    if (owner.getScene() == null) {
      return;
    }
    Themes.JABREF_LIGHT.apply(owner.getScene().getStylesheets());
  }

  /**
   * Creates the file chooser used for opening spectral libraries.
   *
   * @return configured open file chooser.
   */
  private @NotNull FileChooser createOpenFileChooser() {
    final FileChooser chooser = new FileChooser();
    chooser.setTitle("Open Spectral Library");
    chooser.getExtensionFilters().setAll(
        new ExtensionFilter("Supported spectral libraries", "*.mgf", "*.msp", "*.msp_RIKEN", "*.msp_NIST",
            "*.jdx", "*.json"),
        new ExtensionFilter("MGF (*.mgf)", "*.mgf"),
        new ExtensionFilter("MSP (*.msp)", "*.msp"),
        new ExtensionFilter("MZmine JSON (*.json)", "*.json"),
        new ExtensionFilter("All files", "*.*"));
    return chooser;
  }

  /**
   * Creates the file chooser used for saving spectral libraries.
   *
   * @return configured save file chooser.
   */
  private @NotNull FileChooser createSaveFileChooser() {
    final FileChooser chooser = new FileChooser();
    chooser.setTitle("Save Spectral Library");
    final ExtensionFilter mgfFilter = SpectralLibraryFileFormat.MGF.toExtensionFilter();
    final ExtensionFilter mspFilter = SpectralLibraryFileFormat.MSP.toExtensionFilter();
    final ExtensionFilter jsonFilter = SpectralLibraryFileFormat.MZMINE_JSON.toExtensionFilter();
    chooser.getExtensionFilters().setAll(mgfFilter, mspFilter, jsonFilter);
    chooser.setSelectedExtensionFilter(mgfFilter);
    return chooser;
  }

  /**
   * Asks the user whether unsaved changes should be discarded.
   *
   * @return {@code true} if the user confirms discarding changes.
   */
  private boolean confirmDiscardChanges() {
    final Alert alert = new Alert(AlertType.CONFIRMATION);
    alert.initOwner(owner);
    alert.setTitle("Discard unsaved changes?");
    alert.setHeaderText("The current library contains unsaved edits.");
    alert.setContentText("Open a different file and discard current edits?");
    final Optional<ButtonType> result = alert.showAndWait();
    return result.isPresent() && result.get() == ButtonType.OK;
  }

  /**
   * Marks the current library as modified and updates UI state.
   */
  private void markDirty() {
    dirty = true;
    updateWindowState();
  }

  /**
   * Recomputes title, current file label and save enablement.
   */
  private void updateWindowState() {
    final StringBuilder title = new StringBuilder("MZmine Spectral Library Editor");
    if (currentFile != null) {
      title.append(" - ").append(currentFile.getName());
    }
    if (dirty) {
      title.append(" *");
    }
    model.setWindowTitle(title.toString());

    model.setCurrentFileText(currentFile == null ? "No file loaded"
        : currentFile.getName() + " (" + model.getEntries().size() + " entries)");
    model.setSaveEnabled(currentLibrary != null);
  }

  /**
   * Updates status text and logs the same message.
   *
   * @param message status message to display.
   */
  private void setStatus(@NotNull final String message) {
    model.setStatusText(message);
    logger.info(message);
  }
}
