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

import io.github.mzmine.javafx.components.factories.FxLabels;
import io.github.mzmine.javafx.components.factories.FxTextFields;
import io.github.mzmine.javafx.components.util.FxLayout;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.util.spectraldb.entry.DBEntryField;
import io.github.mzmine.util.spectraldb.entry.SpectralLibraryEntry;
import java.util.Comparator;
import java.util.List;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.controlsfx.control.CheckListView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the entry list pane with filters and a checklist view.
 */
public final class SpectralLibraryEntryListViewBuilder extends FxViewBuilder<SpectralLibraryEntryListModel> {

  private final @NotNull SpectralLibraryEntryListController controller;
  private final @NotNull CheckListView<SpectralLibraryEntry> entryList = new CheckListView<>();

  /**
   * Creates a view builder for the entry list pane.
   *
   * @param model list pane model.
   * @param controller list pane controller.
   */
  protected SpectralLibraryEntryListViewBuilder(@NotNull final SpectralLibraryEntryListModel model,
      @NotNull final SpectralLibraryEntryListController controller) {
    super(model);
    this.controller = controller;
  }

  /**
   * Builds the entry list pane.
   *
   * @return list pane region.
   */
  @Override
  public @NotNull Region build() {
    final Label title = FxLabels.newBoldTitle("Library Entries");
    final Node filterControls = createFilterControls();

    entryList.setItems(model.getFilteredEntries());
    entryList.setPlaceholder(FxLabels.newLabel("No entries available"));
    entryList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    entryList.setCellFactory(CheckBoxListCell.forListView(entryList::getItemBooleanProperty,
        createEntryConverter()));

    entryList.getSelectionModel().getSelectedItems().addListener(
        (ListChangeListener<? super SpectralLibraryEntry>) _ ->
            controller.onSelectedItemsChanged(List.copyOf(entryList.getSelectionModel().getSelectedItems())));
    entryList.getCheckModel().getCheckedItems().addListener(
        (ListChangeListener<? super SpectralLibraryEntry>) _ ->
            controller.onCheckedItemsChanged(List.copyOf(entryList.getCheckModel().getCheckedItems())));

    model.listRefreshVersionProperty().addListener((_, _, _) -> entryList.refresh());

    final VBox pane = FxLayout.newVBox(Pos.TOP_LEFT, FxLayout.DEFAULT_PADDING_INSETS, true, title,
        filterControls, entryList);
    VBox.setVgrow(entryList, Priority.ALWAYS);
    pane.setMinWidth(280);
    return pane;
  }

  /**
   * Creates the filter controls shown above the checklist.
   *
   * @return grouped filter section.
   */
  private @NotNull Node createFilterControls() {
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

    final HBox fieldRow = FxLayout.newHBox(Pos.CENTER_LEFT, Insets.EMPTY, metadataFieldLabel,
        filterFieldTextField);
    final HBox filterRow = FxLayout.newHBox(Pos.CENTER_LEFT, Insets.EMPTY, filterLabel, filterTextField);
    final Label groupLabel = FxLabels.newLabelNoWrap("Metadata search");
    final VBox filterBox = FxLayout.newVBox(Pos.TOP_LEFT, FxLayout.DEFAULT_PADDING_INSETS, true, groupLabel,
        fieldRow, filterRow);
    return FxLayout.wrapInBorder(filterBox);
  }

  /**
   * Creates a string converter for checklist entry text.
   *
   * @return entry string converter.
   */
  private @NotNull StringConverter<SpectralLibraryEntry> createEntryConverter() {
    return new StringConverter<>() {
      @Override
      public @NotNull String toString(@Nullable final SpectralLibraryEntry object) {
        if (object == null) {
          return "";
        }
        return SpectralLibraryEntryListCell.toDisplayText(object, controller.isEntryEdited(object));
      }

      @Override
      public @Nullable SpectralLibraryEntry fromString(final String string) {
        return null;
      }
    };
  }

}
