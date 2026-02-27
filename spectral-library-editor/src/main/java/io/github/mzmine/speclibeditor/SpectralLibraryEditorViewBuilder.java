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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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

/**
 * Builds the JavaFX layout for the spectral library editor.
 */
public final class SpectralLibraryEditorViewBuilder extends FxViewBuilder<SpectralLibraryEditorModel> {

  private final @NotNull SpectralLibraryEditorController controller;
  private final @NotNull SpectralLibraryEntryListController entryListController;
  private final @NotNull SpectralLibrarySpectrumPane spectrumPane = new SpectralLibrarySpectrumPane();
  private final @NotNull Structure2DComponent structurePane = new Structure2DComponent();

  /**
   * Creates a view builder bound to the editor model and controller.
   *
   * @param model editor state model.
   * @param controller editor controller handling user actions.
   * @param entryListController entry list controller for the left panel.
   */
  protected SpectralLibraryEditorViewBuilder(@NotNull final SpectralLibraryEditorModel model,
      @NotNull final SpectralLibraryEditorController controller,
      @NotNull final SpectralLibraryEntryListController entryListController) {
    super(model);
    this.controller = controller;
    this.entryListController = entryListController;
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
    structurePane.moleculeProperty().bind(Bindings.createObjectBinding(
        () -> {
          final var structure = model.getStructureMolecule();
          return structure == null ? null : structure.structure();
        }, model.structureMoleculeProperty()));
    model.selectedEntryProperty().addListener((_, _, selectedEntry) -> spectrumPane.setEntry(selectedEntry));
    spectrumPane.setEntry(model.getSelectedEntry());
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

    final Button removeEntriesButton = FxButtons.createButton("Remove selected",
        "Remove selected or checked entries from the current library",
        controller::onRemoveSelectedOrCheckedRequested);
    removeEntriesButton.disableProperty().bind(model.saveEnabledProperty().not());

    final Label currentFileLabel = FxLabels.newLabel(model.currentFileTextProperty());

    final Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    final HBox toolbar = FxLayout.newHBox(Pos.CENTER_LEFT, FxLayout.DEFAULT_PADDING_INSETS, openButton,
        saveAsButton, removeEntriesButton, spacer,
        currentFileLabel);
    toolbar.setPadding(new Insets(8));
    return toolbar;
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
    return entryListController.buildView();
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
    wrapper.visibleProperty().bind(model.structureMoleculeProperty().map(Objects::nonNull));
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
