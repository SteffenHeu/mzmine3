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

import io.github.mzmine.javafx.mvci.FxController;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.util.spectraldb.entry.DBEntryField;
import io.github.mzmine.util.spectraldb.entry.SpectralLibraryEntry;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Controls filtering, selection and check state for the entry list panel.
 */
public final class SpectralLibraryEntryListController extends
    FxController<SpectralLibraryEntryListModel> {

  private final @NotNull SpectralLibraryEntryListViewBuilder viewBuilder;
  private @NotNull Predicate<SpectralLibraryEntry> editedEntryPredicate = _ -> false;

  /**
   * Creates a list controller backed by the shared entries list.
   *
   * @param entries shared spectral library entries list.
   */
  public SpectralLibraryEntryListController(@NotNull final ObservableList<SpectralLibraryEntry> entries) {
    super(new SpectralLibraryEntryListModel(entries));
    viewBuilder = new SpectralLibraryEntryListViewBuilder(model, this);

    model.filterFieldTextProperty().addListener((_, _, text) -> applyFilterFieldText(text));
    model.getFilteredEntries().predicateProperty()
        .bind(Bindings.createObjectBinding(this::createEntryFilterPredicate,
            model.filterFieldProperty(), model.filterTextProperty()));
    model.getFilteredEntries().addListener(
        (ListChangeListener<? super SpectralLibraryEntry>) _ -> ensurePrimaryEntry());

    ensurePrimaryEntry();
  }

  /**
   * Returns the list view builder.
   *
   * @return list view builder.
   */
  @Override
  protected @NotNull FxViewBuilder<SpectralLibraryEntryListModel> getViewBuilder() {
    return viewBuilder;
  }

  /**
   * Returns the primary entry used for spectrum and metadata views.
   *
   * @return primary entry or {@code null}.
   */
  public @Nullable SpectralLibraryEntry getPrimaryEntry() {
    return model.getPrimaryEntry();
  }

  /**
   * Returns the primary entry property.
   *
   * @return primary entry property.
   */
  public @NotNull ObjectProperty<@Nullable SpectralLibraryEntry> primaryEntryProperty() {
    return model.primaryEntryProperty();
  }

  /**
   * Sets the predicate used to decide whether a list entry should be marked as edited.
   *
   * @param editedEntryPredicate predicate returning {@code true} for edited entries.
   */
  public void setEditedEntryPredicate(@NotNull final Predicate<SpectralLibraryEntry> editedEntryPredicate) {
    this.editedEntryPredicate = editedEntryPredicate;
  }

  /**
   * Checks whether a specific entry should be rendered as edited.
   *
   * @param entry entry to inspect.
   * @return {@code true} if the entry is edited.
   */
  public boolean isEntryEdited(@Nullable final SpectralLibraryEntry entry) {
    return entry != null && editedEntryPredicate.test(entry);
  }

  /**
   * Updates the tracked selected entries from checklist selection.
   *
   * @param selectedItems current selected checklist items.
   */
  public void onSelectedItemsChanged(@NotNull final List<SpectralLibraryEntry> selectedItems) {
    final List<SpectralLibraryEntry> selectedItemsSnapshot = List.copyOf(selectedItems);
    if (!Platform.isFxApplicationThread()) {
      onGuiThread(() -> onSelectedItemsChanged(selectedItemsSnapshot));
      return;
    }

    model.setSelectedEntries(selectedItemsSnapshot);
    ensurePrimaryEntry();
  }

  /**
   * Updates the tracked checked entries from checklist checks.
   *
   * @param checkedItems current checked checklist items.
   */
  public void onCheckedItemsChanged(@NotNull final List<SpectralLibraryEntry> checkedItems) {
    final List<SpectralLibraryEntry> checkedItemsSnapshot = List.copyOf(checkedItems);
    if (!Platform.isFxApplicationThread()) {
      onGuiThread(() -> onCheckedItemsChanged(checkedItemsSnapshot));
      return;
    }

    model.setCheckedEntries(checkedItemsSnapshot);
    ensurePrimaryEntry();
  }

  /**
   * Returns a combined set of selected and checked entries.
   *
   * @return unique selected or checked entries in stable order.
   */
  public @NotNull List<SpectralLibraryEntry> getSelectedOrCheckedEntries() {
    final LinkedHashSet<SpectralLibraryEntry> entries = new LinkedHashSet<>();
    entries.addAll(model.getSelectedEntries());
    entries.addAll(model.getCheckedEntries());
    return List.copyOf(entries);
  }

  /**
   * Clears tracked selection and check state.
   */
  public void clearSelectionState() {
    if (!Platform.isFxApplicationThread()) {
      onGuiThread(this::clearSelectionState);
      return;
    }

    model.clearSelectionState();
    ensurePrimaryEntry();
  }

  /**
   * Updates the primary entry to the first selected, checked or visible entry.
   */
  public void ensurePrimaryEntry() {
    if (!Platform.isFxApplicationThread()) {
      onGuiThread(this::ensurePrimaryEntry);
      return;
    }

    final SpectralLibraryEntry firstSelected = firstContainedEntry(model.getSelectedEntries());
    if (firstSelected != null) {
      model.setPrimaryEntry(firstSelected);
      return;
    }

    final SpectralLibraryEntry firstChecked = firstContainedEntry(model.getCheckedEntries());
    if (firstChecked != null) {
      model.setPrimaryEntry(firstChecked);
      return;
    }

    if (model.getFilteredEntries().isEmpty()) {
      model.setPrimaryEntry(null);
      return;
    }
    model.setPrimaryEntry(model.getFilteredEntries().getFirst());
  }

  /**
   * Requests a visual refresh of the checklist rows.
   */
  public void requestListRefresh() {
    if (!Platform.isFxApplicationThread()) {
      onGuiThread(this::requestListRefresh);
      return;
    }

    model.requestListRefresh();
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
   * Creates the current metadata filter predicate from model properties.
   *
   * @return predicate used by the filtered entry list.
   */
  private @NotNull Predicate<SpectralLibraryEntry> createEntryFilterPredicate() {
    final String query = model.getFilterText().strip();
    final DBEntryField filterField = model.getFilterField();
    if (query.isEmpty()) {
      return _ -> true;
    }

    final String lowerQuery = query.toLowerCase(Locale.ROOT);
    return entry -> {
      final String value = entry.getField(filterField).map(String::valueOf).orElse("");
      return value.toLowerCase(Locale.ROOT).contains(lowerQuery);
    };
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
   * Returns the first entry still visible in the filtered list.
   *
   * @param candidates ordered candidate entries.
   * @return first visible candidate or {@code null}.
   */
  private @Nullable SpectralLibraryEntry firstContainedEntry(
      @NotNull final List<SpectralLibraryEntry> candidates) {
    for (final SpectralLibraryEntry candidate : candidates) {
      if (model.getFilteredEntries().contains(candidate)) {
        return candidate;
      }
    }
    return null;
  }
}
