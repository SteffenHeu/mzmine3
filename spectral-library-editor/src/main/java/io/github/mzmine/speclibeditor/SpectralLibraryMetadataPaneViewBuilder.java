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

import io.github.mzmine.javafx.components.factories.FxLabels;
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
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;

/**
 * Builds the metadata and structure pane.
 */
public final class SpectralLibraryMetadataPaneViewBuilder extends
    FxViewBuilder<SpectralLibraryEditorModel> {

  private final @NotNull SpectralLibraryMetadataPaneController controller;
  private final @NotNull Structure2DComponent structurePane = new Structure2DComponent();

  /**
   * Creates the metadata pane view builder.
   *
   * @param model shared editor model.
   * @param controller metadata pane controller handling metadata commits.
   */
  protected SpectralLibraryMetadataPaneViewBuilder(@NotNull final SpectralLibraryEditorModel model,
      @NotNull final SpectralLibraryMetadataPaneController controller) {
    super(model);
    this.controller = controller;
  }

  /**
   * Builds the metadata and structure editor pane.
   *
   * @return metadata pane region.
   */
  @Override
  public @NotNull Region build() {
    initializeBindings();

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
   * Connects structure state from the model to the structure component.
   */
  private void initializeBindings() {
    if (structurePane.moleculeProperty().isBound()) {
      structurePane.moleculeProperty().unbind();
    }
    structurePane.moleculeProperty().bind(Bindings.createObjectBinding(
        () -> {
          final var structure = model.getStructureMolecule();
          return structure == null ? null : structure.structure();
        }, model.structureMoleculeProperty()));
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
   * Creates the metadata editor grid.
   *
   * @return metadata grid.
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
      label.styleProperty().bind(model.metadataEditedProperty(field).map(
          edited -> edited ? "-fx-font-weight: bold; -fx-text-fill: -jr-accent;" : "").orElse(""));
      editor.styleProperty().bind(Bindings.createStringBinding(() -> {
            if (model.metadataErrorProperty(field).get()) {
              return "-fx-border-color: #b00020;";
            }
            if (model.metadataEditedProperty(field).get()) {
              return "-fx-border-color: -jr-accent;";
            }
            return "";
          }, model.metadataErrorProperty(field), model.metadataEditedProperty(field)));

      gridNodes.add(label);
      gridNodes.add(editor);
    }

    return FxLayout.newGrid2Col(FxLayout.GridColumnGrow.RIGHT, Insets.EMPTY,
        gridNodes.toArray(Node[]::new));
  }
}
