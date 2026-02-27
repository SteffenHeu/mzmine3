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

import io.github.mzmine.javafx.components.factories.FxButtons;
import io.github.mzmine.javafx.components.factories.FxLabels;
import io.github.mzmine.javafx.components.factories.FxSplitPanes;
import io.github.mzmine.javafx.components.factories.FxTextFields;
import io.github.mzmine.javafx.components.util.FxLayout;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.modules.visualization.molstructure.Structure2DComponent;
import io.github.mzmine.util.spectraldb.entry.DBEntryField;
import io.github.mzmine.util.spectraldb.entry.SpectralLibraryEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Comparator;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the JavaFX layout for the spectral library editor.
 */
public final class SpectralLibraryEditorViewBuilder extends FxViewBuilder<SpectralLibraryEditorModel> {

  private final @NotNull SpectralLibraryEditorController controller;
  private final @NotNull SpectralLibrarySpectrumPane spectrumPane = new SpectralLibrarySpectrumPane();
  private final @NotNull Structure2DComponent structurePane = new Structure2DComponent();
  private final @NotNull ListView<SpectralLibraryEntry> entryList = new ListView<>();

  /**
   * Creates a view builder bound to the editor model and controller.
   *
   * @param model editor state model.
   * @param controller editor controller handling user actions.
   */
  protected SpectralLibraryEditorViewBuilder(@NotNull final SpectralLibraryEditorModel model,
      @NotNull final SpectralLibraryEditorController controller) {
    super(model);
    this.controller = controller;
  }

  /**
   * Builds the root editor view.
   *
   * @return top-level region containing toolbar, content and status bar.
   */
  @Override
  public @NotNull Region build() {
    final BorderPane root = new BorderPane();
    root.setTop(createToolbar());
    root.setCenter(createMainPane());
    root.setBottom(createStatusPane());
    initializeBindings();
    return root;
  }

  /**
   * Connects model properties to UI controls and selection events.
   */
  private void initializeBindings() {
    final FilteredList<SpectralLibraryEntry> filteredEntries = new FilteredList<>(model.getEntries(),
        _ -> true);
    entryList.setItems(filteredEntries);
    entryList.setCellFactory(_ -> new SpectralLibraryEntryListCell());
    entryList.setPlaceholder(FxLabels.newLabel("No entries available"));

    entryList.getSelectionModel().selectedItemProperty().addListener((_, _, selectedEntry) -> {
      if (!Objects.equals(model.getSelectedEntry(), selectedEntry)) {
        model.setSelectedEntry(selectedEntry);
      }
    });

    model.selectedEntryProperty().addListener((_, _, selectedEntry) -> {
      if (!Objects.equals(entryList.getSelectionModel().getSelectedItem(), selectedEntry)) {
        if (selectedEntry == null) {
          entryList.getSelectionModel().clearSelection();
        } else {
          entryList.getSelectionModel().select(selectedEntry);
        }
      }
      spectrumPane.setEntry(selectedEntry);
    });

    filteredEntries.addListener((ListChangeListener<? super SpectralLibraryEntry>) _ ->
        ensureSelectionInFilteredEntries(filteredEntries));
    model.filterFieldProperty().addListener((_, _, _) -> applyEntryFilter(filteredEntries));
    model.filterTextProperty().addListener((_, _, _) -> applyEntryFilter(filteredEntries));
    model.filterFieldTextProperty().addListener((_, _, newText) -> applyFilterFieldText(newText));
    applyEntryFilter(filteredEntries);

    model.structureMoleculeProperty().addListener((_, _, structure) -> structurePane.setMolecule(
        structure == null ? null : structure.structure()));
    model.listRefreshVersionProperty().addListener((_, _, _) -> entryList.refresh());
    spectrumPane.setEntry(model.getSelectedEntry());
    final var structure = model.getStructureMolecule();
    structurePane.setMolecule(structure == null ? null : structure.structure());
  }

  /**
   * Creates the top toolbar with open and save-as actions.
   *
   * @return toolbar node.
   */
  private @NotNull Node createToolbar() {
    final Button openButton = FxButtons.createLoadButton("Open", controller::onOpenRequested);

    final Button saveAsButton = FxButtons.createButton("Save As", "Save to a new spectral library file",
        controller::onSaveAsRequested);
    saveAsButton.disableProperty().bind(model.saveEnabledProperty().not());

    final String filterFieldTooltip = "Select metadata field used for filtering entries";
    final List<String> uniqueColumnValues = model.getEditableFields().stream().map(DBEntryField::toString)
        .distinct().sorted(Comparator.naturalOrder()).toList();
    final TextField filterFieldTextField = FxTextFields.newAutoGrowTextField(
        model.filterFieldTextProperty(), "", filterFieldTooltip, 4, 20);
    FxTextFields.bindAutoCompletion(filterFieldTextField, uniqueColumnValues);

    final TextField filterTextField = FxTextFields.newTextField(22, model.filterTextProperty(),
        "Filter entries", "Filter entry list by the selected metadata field");
    final Label metadataFieldLabel = FxLabels.newLabelNoWrap("Medatata field: ");
    final Label filterLabel = FxLabels.newLabelNoWrap("Filter: ");

    final Label currentFileLabel = FxLabels.newLabel(model.currentFileTextProperty());

    final Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    final HBox toolbar = FxLayout.newHBox(Pos.CENTER_LEFT, FxLayout.DEFAULT_PADDING_INSETS, openButton,
        saveAsButton, metadataFieldLabel, filterFieldTextField, filterLabel, filterTextField, spacer,
        currentFileLabel);
    toolbar.setPadding(new Insets(8));
    return toolbar;
  }

  /**
   * Resolves entered filter field text to a known metadata field.
   *
   * @param fieldText text entered in the filter field selector.
   */
  private void applyFilterFieldText(@NotNull final String fieldText) {
    final DBEntryField resolvedField = resolveFilterField(fieldText);
    if (resolvedField == null) {
      return;
    }
    if (!Objects.equals(model.getFilterField(), resolvedField)) {
      model.setFilterField(resolvedField);
    }
    if (!Objects.equals(model.getFilterFieldText(), resolvedField.toString())) {
      model.setFilterFieldText(resolvedField.toString());
    }
  }

  /**
   * Finds a metadata field from user-entered filter field text.
   *
   * @param fieldText text entered in the field selector.
   * @return matching metadata field or {@code null} if no match exists.
   */
  private @Nullable DBEntryField resolveFilterField(@NotNull final String fieldText) {
    final String normalizedText = fieldText.strip();
    if (normalizedText.isEmpty()) {
      return null;
    }

    for (final DBEntryField field : model.getEditableFields()) {
      final String enumName = field.name().replace('_', ' ');
      if (field.toString().equalsIgnoreCase(normalizedText) || field.name()
          .equalsIgnoreCase(normalizedText) || enumName.equalsIgnoreCase(normalizedText)) {
        return field;
      }
    }
    return null;
  }

  /**
   * Applies the current metadata filter settings to the entry list.
   *
   * @param filteredEntries filtered view backing the list control.
   */
  private void applyEntryFilter(@NotNull final FilteredList<SpectralLibraryEntry> filteredEntries) {
    final String query = model.getFilterText().strip();
    final DBEntryField filterField = model.getFilterField();
    if (query.isEmpty()) {
      filteredEntries.setPredicate(_ -> true);
      ensureSelectionInFilteredEntries(filteredEntries);
      return;
    }

    final String lowerQuery = query.toLowerCase(Locale.ROOT);
    filteredEntries.setPredicate(entry -> {
      final String value = entry.getField(filterField).map(String::valueOf).orElse("");
      return value.toLowerCase(Locale.ROOT).contains(lowerQuery);
    });
    ensureSelectionInFilteredEntries(filteredEntries);
  }

  /**
   * Ensures the current selection is part of the filtered entry list.
   *
   * @param filteredEntries filtered entry view currently shown in the list.
   */
  private void ensureSelectionInFilteredEntries(
      @NotNull final FilteredList<SpectralLibraryEntry> filteredEntries) {
    final SpectralLibraryEntry selectedEntry = model.getSelectedEntry();
    if (selectedEntry != null && filteredEntries.contains(selectedEntry)) {
      return;
    }

    if (filteredEntries.isEmpty()) {
      if (selectedEntry != null) {
        model.setSelectedEntry(null);
      }
      return;
    }

    model.setSelectedEntry(filteredEntries.getFirst());
  }

  /**
   * Creates the three-column main split pane.
   *
   * @return main content node.
   */
  private @NotNull Node createMainPane() {
    final SplitPane splitPane = FxSplitPanes.newSplitPane(0.2, Orientation.HORIZONTAL, createEntryPane(),
        createSpectrumPane(), createMetadataPane());
    splitPane.setDividerPositions(0.2, 0.67);
    return splitPane;
  }

  /**
   * Creates the entry list panel.
   *
   * @return left panel node.
   */
  private @NotNull Node createEntryPane() {
    final Label title = FxLabels.newBoldTitle("Library Entries");
    final VBox pane = FxLayout.newVBox(Pos.TOP_LEFT, FxLayout.DEFAULT_PADDING_INSETS, true, title, entryList);
    VBox.setVgrow(entryList, Priority.ALWAYS);
    pane.setMinWidth(280);
    return pane;
  }

  /**
   * Creates the spectrum chart panel.
   *
   * @return center panel node.
   */
  private @NotNull Node createSpectrumPane() {
    final Label title = FxLabels.newBoldTitle("Spectrum");
    final Node chartNode = spectrumPane.getNode();
    final VBox pane = FxLayout.newVBox(Pos.TOP_LEFT, FxLayout.DEFAULT_PADDING_INSETS, true, title, chartNode);
    VBox.setVgrow(chartNode, Priority.ALWAYS);
    return pane;
  }

  /**
   * Creates the metadata editor panel.
   *
   * @return right panel node.
   */
  private @NotNull Node createMetadataPane() {
    final Label title = FxLabels.newBoldTitle("Metadata");
    final Node structureNode = createStructurePreviewPane();
    final GridPane metadataGrid = createMetadataGrid();
    final VBox metadataContent = FxLayout.newVBox(Pos.TOP_LEFT, FxLayout.DEFAULT_PADDING_INSETS, true,
        structureNode, metadataGrid);
    final ScrollPane metadataScrollPane = FxLayout.newScrollPane(metadataContent, ScrollBarPolicy.NEVER,
        ScrollBarPolicy.AS_NEEDED);
    metadataScrollPane.setFitToWidth(true);

    final VBox pane = FxLayout.newVBox(Pos.TOP_LEFT, FxLayout.DEFAULT_PADDING_INSETS, true, title,
        metadataScrollPane);
    VBox.setVgrow(metadataScrollPane, Priority.ALWAYS);
    pane.setMinWidth(470);
    return pane;
  }

  /**
   * Creates the structure preview shown above the metadata form.
   *
   * @return structure preview node.
   */
  private @NotNull Node createStructurePreviewPane() {
    final Label structureLabel = FxLabels.newLabelNoWrap("Structure");
    structurePane.setContextMenuEnabled(false);
    final StackPane structureContainer = new StackPane(structurePane);
    structureContainer.setMinHeight(190);
    structureContainer.setPrefHeight(220);
    structureContainer.setMaxHeight(260);
    final VBox wrapper = FxLayout.newVBox(Pos.TOP_LEFT, Insets.EMPTY, true, structureLabel,
        structureContainer);
    wrapper.visibleProperty().bind(model.structureVisibleProperty());
    wrapper.managedProperty().bind(wrapper.visibleProperty());
    VBox.setVgrow(structureContainer, Priority.NEVER);
    return wrapper;
  }

  /**
   * Creates the two-column metadata form with labels and editors.
   *
   * @return populated metadata grid.
   */
  private @NotNull GridPane createMetadataGrid() {
    final List<Node> gridNodes = new ArrayList<>();
    for (final DBEntryField field : model.getEditableFields()) {
      final Label label = FxLabels.newLabelNoWrap(field.toString());
      final String tooltip = field.name() + " (" + field.getObjectClass().getSimpleName() + ")";
      final TextField editor = FxTextFields.newTextField(28, model.metadataTextProperty(field), field.name(),
          tooltip);
      editor.setOnAction(_ -> controller.onMetadataCommit(field));
      editor.focusedProperty().addListener((_, _, focused) -> {
        if (!focused) {
          controller.onMetadataCommit(field);
        }
      });
      editor.disableProperty().bind(model.metadataEnabledProperty().not());
      editor.styleProperty().bind(model.metadataErrorProperty(field).map(
          hasError -> hasError ? "-fx-border-color: #b00020;" : "").orElse(""));

      gridNodes.add(label);
      gridNodes.add(editor);
    }

    return FxLayout.newGrid2Col(FxLayout.GridColumnGrow.RIGHT, Insets.EMPTY,
        gridNodes.toArray(Node[]::new));
  }

  /**
   * Creates the bottom status bar.
   *
   * @return status pane node.
   */
  private @NotNull Node createStatusPane() {
    final Label statusLabel = FxLabels.newLabel(model.statusTextProperty());
    final HBox pane = FxLayout.newHBox(Pos.CENTER_LEFT, FxLayout.DEFAULT_PADDING_INSETS, statusLabel);
    pane.setPadding(new Insets(6, 8, 8, 8));
    return pane;
  }
}
