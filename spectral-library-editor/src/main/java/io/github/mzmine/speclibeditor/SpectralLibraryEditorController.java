/*
 * Copyright (c) 2004-2026 The mzmine Development Team
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
import io.github.mzmine.util.spectraldb.entry.SpectralDBEntry;
import io.github.mzmine.util.spectraldb.entry.SpectralLibrary;
import io.github.mzmine.util.spectraldb.entry.SpectralLibraryEntry;
import io.github.mzmine.util.spectraldb.parser.UnsupportedFormatException;
import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.application.Platform;
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
  private final @NotNull ExecutorService ioExecutor = Executors.newSingleThreadExecutor(
      runnable -> {
        final Thread thread = new Thread(runnable, "spectral-library-editor-io");
        thread.setDaemon(true);
        return thread;
      });
  private final @NotNull SpectralLibraryEntryListController entryListController;
  private final @NotNull SpectralLibraryEditorViewBuilder viewBuilder;

  private @Nullable File currentFile;
  private long loadRequestCounter;
  /**
   * Tracks non-metadata edits that cannot be inferred from field-level diffs (for example removals).
   */
  private boolean structuralDirty;
  /**
   * Guards metadata commits while the controller updates form fields programmatically.
   */
  private boolean updatingMetadataFields;
  /**
   * Tracks whether a save task is currently running in the background.
   */
  private boolean saveInProgress;

  /**
   * Creates a controller instance bound to the given owner stage.
   *
   * @param owner stage used as owner for dialogs and title binding.
   */
  public SpectralLibraryEditorController(@NotNull final Stage owner) {
    super(new SpectralLibraryEditorModel());
    this.owner = owner;
    entryListController = new SpectralLibraryEntryListController(model.getEntries());
    entryListController.setEditedEntryPredicate(this::isEntryEdited);
    final SpectralLibraryMetadataPaneController metadataPaneController =
        new SpectralLibraryMetadataPaneController(model, this);
    viewBuilder = new SpectralLibraryEditorViewBuilder(model, this, entryListController,
        metadataPaneController);
    owner.titleProperty().bind(model.windowTitleProperty());

    model.selectedEntryProperty().bind(entryListController.primaryEntryProperty());

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
    if (!Platform.isFxApplicationThread()) {
      onGuiThread(this::onOpenRequested);
      return;
    }

    if (saveInProgress) {
      setStatus("Save in progress. Please wait.");
      return;
    }

    if (isDirty() && !confirmDiscardChanges()) {
      return;
    }

    final FileChooser chooser = createOpenFileChooser();
    final File selectedFile = chooser.showOpenDialog(owner);
    if (selectedFile == null) {
      return;
    }

    loadLibraryAsync(selectedFile);
  }

  /**
   * Handles save requests by delegating to Save As to avoid overwriting existing files.
   */
  public void onSaveRequested() {
    if (!Platform.isFxApplicationThread()) {
      onGuiThread(this::onSaveRequested);
      return;
    }

    onSaveAsRequested();
  }

  /**
   * Checks whether an entry currently differs from the loaded/saved baseline.
   *
   * @param entry entry to inspect.
   * @return {@code true} if at least one metadata field was changed.
   */
  public boolean isEntryEdited(@Nullable final SpectralLibraryEntry entry) {
    return model.isEntryEdited(entry);
  }

  /**
   * Removes all currently selected or checked entries from the loaded library.
   */
  public void onRemoveSelectedOrCheckedRequested() {
    if (!Platform.isFxApplicationThread()) {
      onGuiThread(this::onRemoveSelectedOrCheckedRequested);
      return;
    }

    if (saveInProgress) {
      setStatus("Save in progress. Please wait.");
      return;
    }

    if (!isLibraryLoaded()) {
      setStatus("No library loaded.");
      return;
    }

    final List<SpectralLibraryEntry> entriesToRemove = entryListController.getSelectedOrCheckedEntries();
    if (entriesToRemove.isEmpty()) {
      setStatus("No selected or checked entries to remove.");
      return;
    }

    final int removedCount = entriesToRemove.size();
    for (final SpectralLibraryEntry entry : entriesToRemove) {
      model.removeEditedEntryTracking(entry);
    }
    model.getEntries().removeAll(entriesToRemove);
    entryListController.clearSelectionState();
    entryListController.ensurePrimaryEntry();
    markStructuralDirty();
    setStatus("Removed " + removedCount + " entries.");
  }

  /**
   * Handles the Save As action and writes the current library to a new file.
   */
  public void onSaveAsRequested() {
    if (!Platform.isFxApplicationThread()) {
      onGuiThread(this::onSaveAsRequested);
      return;
    }

    if (saveInProgress) {
      setStatus("Save in progress. Please wait.");
      return;
    }

    if (!isLibraryLoaded()) {
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
    if (!Platform.isFxApplicationThread()) {
      onGuiThread(() -> onMetadataCommit(field));
      return;
    }

    if (saveInProgress) {
      return;
    }

    if (updatingMetadataFields) {
      return;
    }

    final SpectralLibraryEntry entry = model.getSelectedEntry();
    if (entry == null) {
      return;
    }
    final boolean wasEntryEdited = isEntryEdited(entry);

    final String input = model.getMetadataText(field).trim();
    if (input.isEmpty()) {
      entry.getFields().remove(field);
      model.setMetadataError(field, false);
      model.setMetadataText(field, "");
      updateFieldChangeTracking(entry, field);
      updateMetadataEditedForSelection(entry);
      updateDirtyState();
      refreshEntryListIfNeeded(field, wasEntryEdited != isEntryEdited(entry));
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
      updateFieldChangeTracking(entry, field);
      updateMetadataEditedForSelection(entry);
      updateDirtyState();
      refreshEntryListIfNeeded(field, wasEntryEdited != isEntryEdited(entry));
      return;
    }

    entry.putIfNotNull(field, converted);
    updateFieldChangeTracking(entry, field);
    updateMetadataEditedForSelection(entry);
    updateDirtyState();
    refreshEntryListIfNeeded(field, wasEntryEdited != isEntryEdited(entry));
    if (isStructureField(field)) {
      updateStructureForSelection(entry);
    }
  }

  /**
   * Loads a spectral library file on a background thread.
   *
   * @param file input library file.
   */
  private void loadLibraryAsync(@NotNull final File file) {
    final long requestId = ++loadRequestCounter;
    setStatus("Loading " + file.getName() + "...");
    try {
      ioExecutor.submit(() -> {
        try {
          final SpectralLibrary loadedLibrary = fileService.loadLibrary(file);
          onGuiThread(() -> {
            if (requestId != loadRequestCounter) {
              return;
            }
            applyLoadedLibrary(file, loadedLibrary);
          });
        } catch (final UnsupportedFormatException e) {
          logger.log(Level.WARNING, "Unsupported library format " + file.getAbsolutePath(), e);
          onGuiThread(() -> {
            if (requestId != loadRequestCounter) {
              return;
            }
            setStatus("Unsupported spectral library format: " + file.getName());
          });
        } catch (final Exception e) {
          logger.log(Level.WARNING, "Cannot load spectral library " + file.getAbsolutePath(), e);
          onGuiThread(() -> {
            if (requestId != loadRequestCounter) {
              return;
            }
            setStatus("Failed to load file: " + e.getMessage());
          });
        }
      });
    } catch (final RejectedExecutionException e) {
      logger.log(Level.WARNING, "Cannot schedule library loading for " + file.getAbsolutePath(), e);
      setStatus("Failed to load file: background loader is not available.");
    }
  }

  /**
   * Stops controller resources and background tasks.
   */
  @Override
  public void close() {
    super.close();
    ioExecutor.shutdownNow();
  }

  /**
   * Applies a loaded library to the model on the JavaFX thread.
   *
   * @param file source file used for loading.
   * @param loadedLibrary loaded spectral library.
   */
  private void applyLoadedLibrary(@NotNull final File file,
      @NotNull final SpectralLibrary loadedLibrary) {
    currentFile = file;
    structuralDirty = false;
    saveInProgress = false;

    model.getEntries().setAll(copyEntries(loadedLibrary.getEntries()));
    rebuildBaselineSnapshots();
    entryListController.clearSelectionState();
    entryListController.ensurePrimaryEntry();

    setStatus("Loaded " + file.getName() + " with " + loadedLibrary.getEntries().size() + " entries.");
    updateDirtyState();
  }

  /**
   * Saves the current library to the specified output file and format.
   *
   * @param file target output file.
   * @param format output serialization format.
   */
  private void saveLibrary(@NotNull final File file, @NotNull final SpectralLibraryFileFormat format) {
    if (!isLibraryLoaded()) {
      setStatus("No library loaded.");
      return;
    }

    // decision: snapshot the JavaFX list on GUI thread, then perform deep-copy/export on background thread.
    final List<SpectralLibraryEntry> entriesSnapshot = List.copyOf(model.getEntries());
    saveInProgress = true;
    setStatus("Saving " + file.getName() + "...");
    updateWindowState();

    try {
      ioExecutor.submit(() -> {
        try {
          final SpectralLibrary exportLibrary = createLibraryFromEntries(file, entriesSnapshot);
          fileService.saveLibrary(exportLibrary, file, format);
          onGuiThread(() -> applySaveSuccess(file, format));
        } catch (final Exception e) {
          logger.log(Level.WARNING, "Cannot save library to " + file.getAbsolutePath(), e);
          onGuiThread(() -> {
            saveInProgress = false;
            updateWindowState();
            setStatus("Failed to save file: " + e.getMessage());
          });
        }
      });
    } catch (final RejectedExecutionException e) {
      logger.log(Level.WARNING, "Cannot schedule library saving for " + file.getAbsolutePath(), e);
      saveInProgress = false;
      updateWindowState();
      setStatus("Failed to save file: background saver is not available.");
    }
  }

  /**
   * Applies state updates after a successful save operation.
   *
   * @param file   saved output file.
   * @param format saved output format.
   */
  private void applySaveSuccess(@NotNull final File file,
      @NotNull final SpectralLibraryFileFormat format) {
    currentFile = file;
    structuralDirty = false;
    saveInProgress = false;
    rebuildBaselineSnapshots();
    updateMetadataEditedForSelection(model.getSelectedEntry());
    entryListController.requestListRefresh();
    setStatus("Saved " + file.getName() + " in " + format.name() + " format.");
    updateDirtyState();
  }

  /**
   * Creates a detached export library from a source entry snapshot.
   *
   * @param targetFile file the export library is associated with.
   * @param sourceEntries source entries that should be exported.
   * @return new library containing detached copies of all source entries.
   */
  private @NotNull SpectralLibrary createLibraryFromEntries(@NotNull final File targetFile,
      @NotNull final List<SpectralLibraryEntry> sourceEntries) {
    final SpectralLibrary exportLibrary = new SpectralLibrary(null, targetFile);
    exportLibrary.addEntries(copyEntries(sourceEntries));
    exportLibrary.trim();
    return exportLibrary;
  }

  /**
   * Creates detached copies for all entries in a list.
   *
   * @param entries source entries.
   * @return copied entries detached from their source library.
   */
  private @NotNull List<SpectralLibraryEntry> copyEntries(
      @NotNull final List<SpectralLibraryEntry> entries) {
    return entries.stream().map(this::copyEntry).toList();
  }

  /**
   * Creates a detached copy of a single entry.
   *
   * @param entry source entry.
   * @return copied entry with copied metadata and data points.
   */
  private @NotNull SpectralLibraryEntry copyEntry(@NotNull final SpectralLibraryEntry entry) {
    if (entry instanceof SpectralDBEntry dbEntry) {
      return new SpectralDBEntry(dbEntry);
    }

    final int size = entry.getNumberOfDataPoints();
    final double[] mzValues = entry.getMzValues(new double[size]);
    final double[] intensityValues = entry.getIntensityValues(new double[size]);
    final Map<DBEntryField, Object> fields = new EnumMap<>(DBEntryField.class);
    fields.putAll(entry.getFields());
    return new SpectralDBEntry(null, mzValues, intensityValues, fields, null);
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
        model.setMetadataEdited(field, isFieldEdited(entry, field));
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
  private void refreshEntryListIfNeeded(@NotNull final DBEntryField field,
      final boolean entryEditedStateChanged) {
    if (entryEditedStateChanged || LIST_LABEL_FIELDS.contains(field)) {
      entryListController.requestListRefresh();
    }
  }

  /**
   * Updates the structure preview based on SMILES and InChI values of the selected entry.
   *
   * @param entry selected entry or {@code null}.
   */
  private void updateStructureForSelection(@Nullable final SpectralLibraryEntry entry) {
    if (entry == null) {
      model.setStructureMolecule(null);
      return;
    }

    final String smiles = entry.getAsString(DBEntryField.SMILES).orElse(null);
    final String inchi = entry.getAsString(DBEntryField.INCHI).orElse(null);
    final boolean hasStructureInput = isNonBlank(smiles) || isNonBlank(inchi);
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
   * Rebuilds baseline snapshots and clears metadata change markers.
   */
  private void rebuildBaselineSnapshots() {
    model.clearEditedEntryTracking();
    for (final SpectralLibraryEntry entry : model.getEntries()) {
      model.setEntryBaselineSnapshot(entry, snapshotEntryValues(entry));
    }
  }

  /**
   * Creates a metadata baseline snapshot for all editable fields of one entry.
   *
   * @param entry source entry.
   * @return field-to-serialized-value baseline map.
   */
  private @NotNull EnumMap<DBEntryField, String> snapshotEntryValues(
      @NotNull final SpectralLibraryEntry entry) {
    final EnumMap<DBEntryField, String> snapshot = new EnumMap<>(DBEntryField.class);
    for (final DBEntryField field : model.getEditableFields()) {
      snapshot.put(field, serializeValue(entry.getField(field).orElse(null)));
    }
    return snapshot;
  }

  /**
   * Updates field-level change tracking for one entry field against its baseline value.
   *
   * @param entry changed entry.
   * @param field changed field.
   */
  private void updateFieldChangeTracking(@NotNull final SpectralLibraryEntry entry,
      @NotNull final DBEntryField field) {
    final String currentValue = serializeValue(entry.getField(field).orElse(null));
    model.updateFieldChangeTracking(entry, field, currentValue);
  }

  /**
   * Checks whether a specific field of an entry is currently edited.
   *
   * @param entry target entry.
   * @param field target field.
   * @return {@code true} if the field differs from baseline.
   */
  private boolean isFieldEdited(@Nullable final SpectralLibraryEntry entry,
      @NotNull final DBEntryField field) {
    return model.isFieldEdited(entry, field);
  }

  /**
   * Updates field-level edited markers for the selected metadata form.
   *
   * @param entry currently selected entry.
   */
  private void updateMetadataEditedForSelection(@Nullable final SpectralLibraryEntry entry) {
    for (final DBEntryField editableField : model.getEditableFields()) {
      model.setMetadataEdited(editableField, isFieldEdited(entry, editableField));
    }
  }

  /**
   * Checks whether at least one entry contains metadata edits.
   *
   * @return {@code true} if any entry has changed fields.
   */
  private boolean hasAnyEntryEdits() {
    return model.hasAnyEntryEdits();
  }

  /**
   * Marks non-metadata changes (for example entry removal) as dirty.
   */
  private void markStructuralDirty() {
    structuralDirty = true;
    updateDirtyState();
  }

  /**
   * Computes whether the current library has unsaved edits.
   *
   * @return {@code true} if structural or metadata edits are present.
   */
  private boolean isDirty() {
    return structuralDirty || hasAnyEntryEdits();
  }

  /**
   * Recomputes the overall dirty flag and updates dependent UI state.
   */
  private void updateDirtyState() {
    updateWindowState();
  }

  /**
   * Recomputes title, current file label and save enablement.
   */
  private void updateWindowState() {
    final StringBuilder title = new StringBuilder("mzmine Spectral Library Editor");
    if (currentFile != null) {
      title.append(" - ").append(currentFile.getName());
    }
    if (isDirty()) {
      title.append(" *");
    }
    model.setWindowTitle(title.toString());

    model.setCurrentFileText(currentFile == null ? "No file loaded"
        : currentFile.getName() + " (" + model.getEntries().size() + " entries)");
    model.setSaveEnabled(isLibraryLoaded() && !saveInProgress);
  }

  /**
   * Returns whether a spectral library is currently loaded.
   *
   * @return {@code true} if a source file is loaded.
   */
  private boolean isLibraryLoaded() {
    // decision: loaded state stays true even if the loaded library has zero entries.
    return currentFile != null;
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

  @Override
  public void onGuiThread(Runnable task) {
    Platform.runLater(task);
  }

}

