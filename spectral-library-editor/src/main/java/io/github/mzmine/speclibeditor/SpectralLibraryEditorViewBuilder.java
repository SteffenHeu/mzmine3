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
import io.github.mzmine.javafx.components.util.FxLayout;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;

/**
 * Builds the JavaFX layout for the spectral library editor.
 */
public final class SpectralLibraryEditorViewBuilder extends FxViewBuilder<SpectralLibraryEditorModel> {

  private final @NotNull SpectralLibraryEditorController controller;
  private final @NotNull SpectralLibraryEntryListController entryListController;
  private final @NotNull SpectralLibraryMetadataPaneController metadataPaneController;
  private final @NotNull SpectralLibrarySpectrumPane spectrumPane = new SpectralLibrarySpectrumPane();

  /**
   * Creates a view builder bound to the editor model and controller.
   *
   * @param model editor state model.
   * @param controller editor controller handling user actions.
   * @param entryListController entry list controller for the left panel.
   * @param metadataPaneController metadata pane controller for the right panel.
   */
  protected SpectralLibraryEditorViewBuilder(@NotNull final SpectralLibraryEditorModel model,
      @NotNull final SpectralLibraryEditorController controller,
      @NotNull final SpectralLibraryEntryListController entryListController,
      @NotNull final SpectralLibraryMetadataPaneController metadataPaneController) {
    super(model);
    this.controller = controller;
    this.entryListController = entryListController;
    this.metadataPaneController = metadataPaneController;
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
    return metadataPaneController.buildView();
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
