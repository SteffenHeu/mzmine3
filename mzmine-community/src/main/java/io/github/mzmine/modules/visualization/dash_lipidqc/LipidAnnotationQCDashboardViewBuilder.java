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

package io.github.mzmine.modules.visualization.dash_lipidqc;

import io.github.mzmine.datamodel.features.DataTypeValueChangeListener;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.types.DataTypes;
import io.github.mzmine.datamodel.features.types.annotations.LipidMatchListType;
import io.github.mzmine.datamodel.features.types.annotations.PreferredAnnotationType;
import io.github.mzmine.datamodel.features.types.fx.ColumnID;
import io.github.mzmine.datamodel.features.types.fx.ColumnType;
import io.github.mzmine.javafx.components.factories.FxLabels;
import io.github.mzmine.javafx.components.factories.FxSplitPanes;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.modules.visualization.dash_lipidqc.isotope.IsotopePane;
import io.github.mzmine.modules.visualization.dash_lipidqc.kendrick.KendrickPane;
import io.github.mzmine.modules.visualization.dash_lipidqc.layout.DashboardLayoutFactory;
import io.github.mzmine.modules.visualization.dash_lipidqc.matched.MatchedSignalsPane;
import io.github.mzmine.modules.visualization.dash_lipidqc.quality.AnnotationQualityPane;
import io.github.mzmine.modules.visualization.dash_lipidqc.retention.EquivalentCarbonNumberPane;
import io.github.mzmine.modules.visualization.dash_lipidqc.state.DashboardFilterState;
import io.github.mzmine.modules.visualization.dash_lipidqc.summary.LipidSummaryPane;
import io.github.mzmine.modules.visualization.featurelisttable_modular.FeatureTableFX;
import io.github.mzmine.util.FeatureTableFXUtil;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * View builder for the lipid annotation QC dashboard. Assembles the multi-pane layout (summary,
 * Kendrick, quality, retention, matched signals, isotope) and wires selection listeners between the
 * feature table and the dashboard panes.
 */
public class LipidAnnotationQCDashboardViewBuilder extends
    FxViewBuilder<LipidAnnotationQCDashboardModel> {

  protected LipidAnnotationQCDashboardViewBuilder(LipidAnnotationQCDashboardModel model) {
    super(model);
  }

  @Override
  public Region build() {
    final DashboardFilterState filterState = new DashboardFilterState();
    final IsotopePane isotopePane = new IsotopePane();
    final EquivalentCarbonNumberPane ecnPane = new EquivalentCarbonNumberPane(model);
    final KendrickPane kendrickPane = new KendrickPane(model, filterState);
    final AnnotationQualityPane qualityPane = new AnnotationQualityPane(model);
    kendrickPane.setOnReviewModeChanged(qualityPane::setKendrickReviewMode);
    final MatchedSignalsPane matchedSignalsPane = new MatchedSignalsPane();
    final ComboBox<PreferredLipidLevelOption> preferredLevelCombo = new ComboBox<>(
        FXCollections.observableArrayList(PreferredLipidLevelOption.values()));
    preferredLevelCombo.getSelectionModel().select(model.getPreferredLipidLevel());
    final LipidSummaryPane summaryPane = new LipidSummaryPane(model.featureListProperty(),
        filterState, preferredLevelCombo, model.getFeatureTableFx());
    summaryPane.setOnGroupSelectedRowIds(
        rowIds -> Platform.runLater(() -> selectAndScrollToGroupRow(model, rowIds)));

    final BorderPane retentionSection = DashboardLayoutFactory.wrapInSubsection(
        "Retention time analysis", ecnPane);
    final @Nullable Label retentionTitleLabel =
        retentionSection.getTop() instanceof Label label ? label : null;
    final Label retentionDisabledLabel = FxLabels.newLabel(
        "Retention time analysis is disabled for this lipid analysis mode.");
    retentionDisabledLabel.setStyle("-fx-padding: 8;");

    model.retentionTimeAnalysisEnabledProperty().subscribe(enabled -> {
      if (retentionTitleLabel != null) {
        retentionTitleLabel.textProperty().unbind();
      }
      if (enabled) {
        retentionSection.setCenter(ecnPane);
        if (retentionTitleLabel != null) {
          retentionTitleLabel.textProperty().bind(ecnPane.paneTitleProperty());
        }
      } else {
        if (retentionTitleLabel != null) {
          retentionTitleLabel.setText("Retention time analysis");
        }
        retentionSection.setCenter(retentionDisabledLabel);
      }
    });

    final AtomicBoolean lipidAnnotationRefreshScheduled = new AtomicBoolean(false);
    final DataTypeValueChangeListener<Object> lipidAnnotationListener = (_, _, _, _) -> {
      if (lipidAnnotationRefreshScheduled.compareAndSet(false, true)) {
        Platform.runLater(() -> {
          lipidAnnotationRefreshScheduled.set(false);
          refreshAllDashboardPlots(summaryPane, qualityPane, kendrickPane, ecnPane, isotopePane,
              matchedSignalsPane);
        });
      }
    };
    final var lipidMatchesType = DataTypes.get(LipidMatchListType.class);
    final var preferredAnnotationType = DataTypes.get(PreferredAnnotationType.class);
    final AtomicReference<@Nullable ModularFeatureList> annotationListenerFeatureList = new AtomicReference<>(
        null);
    final Runnable updateLipidAnnotationListener = () -> {
      final @Nullable ModularFeatureList currentFeatureList = model.getFeatureList();
      final @Nullable ModularFeatureList previousFeatureList = annotationListenerFeatureList.getAndSet(
          currentFeatureList);
      if (previousFeatureList != null && previousFeatureList != currentFeatureList) {
        previousFeatureList.removeRowTypeValueListener(lipidMatchesType, lipidAnnotationListener);
        previousFeatureList.removeRowTypeValueListener(preferredAnnotationType,
            lipidAnnotationListener);
      }
      if (currentFeatureList != null && currentFeatureList != previousFeatureList) {
        currentFeatureList.addRowTypeValueListener(lipidMatchesType, lipidAnnotationListener);
        currentFeatureList.addRowTypeValueListener(preferredAnnotationType,
            lipidAnnotationListener);
      }
    };

    qualityPane.setOnAnnotationsChanged(
        () -> refreshAllDashboardPlots(summaryPane, qualityPane, kendrickPane, ecnPane, isotopePane,
            matchedSignalsPane));

    model.featureTableFxProperty().get().getSelectionModel().selectedItemProperty()
        .addListener((_, _, row) -> model.setRow(row == null ? null : row.getValue()));
    model.featureTableFxProperty().get().getFilteredRowItems().addListener(
        (javafx.collections.ListChangeListener<javafx.scene.control.TreeItem<ModularFeatureListRow>>) _ -> Platform.runLater(
            () -> {
              refreshAllDashboardPlots(summaryPane, qualityPane, kendrickPane, ecnPane, isotopePane,
                  matchedSignalsPane);
              selectFirstVisibleRow(model);
            }));
    model.rowProperty().addListener((_, _, row) -> {
      if (row != null) {
        final @Nullable FeatureListRow tableSelectedRow = model.getFeatureTableFx()
            .getSelectedRow();
        if (tableSelectedRow == null || tableSelectedRow.getID() != row.getID()) {
          FeatureTableFXUtil.selectAndScrollTo(row, model.getFeatureTableFx());
        }
      }
      isotopePane.setRow(row);
      matchedSignalsPane.setRow(row);
    });

    model.featureListProperty().subscribe(flist -> {
      updateLipidAnnotationListener.run();
      isotopePane.setFeatureList(flist);
      matchedSignalsPane.setFeatureList(flist);
      Platform.runLater(() -> selectFirstVisibleRow(model));
    });

    filterState.setOnChange(() -> {
      applyPreferredAnnotationSortForClassFilter(filterState, model.getFeatureTableFx());
      kendrickPane.requestUpdate();
    });

    updateLipidAnnotationListener.run();

    final Region dashboardContent = DashboardLayoutFactory.createSixPaneLayout(
        DashboardLayoutFactory.wrapInSubsection("Lipid annotation summary", summaryPane),
        DashboardLayoutFactory.wrapInSubsection("Kendrick mass plot", kendrickPane),
        DashboardLayoutFactory.wrapInSubsection("Lipid annotation quality", qualityPane),
        retentionSection,
        DashboardLayoutFactory.wrapInSubsection("Matched lipid signals", matchedSignalsPane),
        DashboardLayoutFactory.wrapInSubsection("Isotope pattern", isotopePane));

    preferredLevelCombo.setOnAction(_ -> {
      final PreferredLipidLevelOption option = preferredLevelCombo.getValue();
      if (option != null) {
        model.setPreferredLipidLevel(option);
      }
    });
    model.preferredLipidLevelProperty().subscribe(
        _ -> refreshAllDashboardPlots(summaryPane, qualityPane, kendrickPane, ecnPane, isotopePane,
            matchedSignalsPane));
    final var mainSplit = FxSplitPanes.newSplitPane(0.68, Orientation.VERTICAL, dashboardContent,
        model.getFeatureTableController().buildView());

    return mainSplit;
  }

  private void refreshAllDashboardPlots(LipidSummaryPane summaryPane,
      AnnotationQualityPane qualityPane, KendrickPane kendrickPane,
      EquivalentCarbonNumberPane ecnPane, IsotopePane isotopePane,
      MatchedSignalsPane matchedSignalsPane) {
    final @Nullable ModularFeatureList currentFeatureList = model.getFeatureList();
    final @Nullable FeatureListRow selectedRow = model.getRow();
    summaryPane.requestChartUpdate();
    qualityPane.requestUpdate();
    kendrickPane.requestUpdate();
    if (model.isRetentionTimeAnalysisEnabled()) {
      ecnPane.requestUpdate();
    }
    isotopePane.setFeatureList(currentFeatureList);
    matchedSignalsPane.setFeatureList(currentFeatureList);
    isotopePane.setRow(selectedRow);
    matchedSignalsPane.setRow(selectedRow);
  }

  private static void selectFirstVisibleRow(@NotNull LipidAnnotationQCDashboardModel model) {
    final var table = model.getFeatureTableFx();
    final var filteredItems = table.getFilteredRowItems();
    if (filteredItems == null || filteredItems.isEmpty()) {
      model.setRow(null);
      return;
    }
    final @Nullable FeatureListRow currentRow = model.getRow();
    if (currentRow != null && filteredItems.stream().map(javafx.scene.control.TreeItem::getValue)
        .filter(Objects::nonNull).anyMatch(row -> row.getID() == currentRow.getID())) {
      if (!Objects.equals(table.getSelectedRow(), currentRow)) {
        FeatureTableFXUtil.selectAndScrollTo(currentRow, table);
      }
      return;
    }
    final FeatureListRow firstRow = filteredItems.get(0).getValue();
    if (firstRow != null && (model.getRow() == null
        || model.getRow().getID() != firstRow.getID())) {
      FeatureTableFXUtil.selectAndScrollTo(firstRow, table);
      model.setRow(firstRow);
    }
  }

  private static void selectAndScrollToGroupRow(
      final @NotNull LipidAnnotationQCDashboardModel model, final @NotNull Set<Integer> rowIds) {
    if (rowIds.isEmpty()) {
      return;
    }

    final @NotNull FeatureTableFX table = model.getFeatureTableFx();
    final var visibleItems = table.getFilteredRowItems();
    if (visibleItems != null) {
      for (final var item : visibleItems) {
        final @Nullable FeatureListRow row = item.getValue();
        if (row != null && rowIds.contains(row.getID())) {
          FeatureTableFXUtil.selectAndScrollTo(row, table);
          model.setRow(row);
          return;
        }
      }
    }

    final @Nullable ModularFeatureList featureList = model.getFeatureList();
    if (featureList == null) {
      return;
    }
    for (final FeatureListRow row : featureList.getRows()) {
      if (rowIds.contains(row.getID())) {
        FeatureTableFXUtil.selectAndScrollTo(row, table);
        model.setRow(row);
        return;
      }
    }
  }

  private static void applyPreferredAnnotationSortForClassFilter(
      final @NotNull DashboardFilterState filterState, final @NotNull FeatureTableFX table) {
    if (filterState.getBarSelectedRowIds().isEmpty()) {
      return;
    }

    final @Nullable TreeTableColumn<ModularFeatureListRow, ?> preferredAnnotationColumn = findPreferredAnnotationColumn(
        table);
    if (preferredAnnotationColumn == null || !preferredAnnotationColumn.isSortable()) {
      return;
    }

    final @Nullable FeatureListRow selectedRow = table.getSelectedRow();
    preferredAnnotationColumn.setSortType(TreeTableColumn.SortType.ASCENDING);
    table.getTable().getSortOrder().setAll(preferredAnnotationColumn);
    table.getTable().sort();

    if (selectedRow != null) {
      FeatureTableFXUtil.selectAndScrollTo(selectedRow, table);
    }
  }

  private static @Nullable TreeTableColumn<ModularFeatureListRow, ?> findPreferredAnnotationColumn(
      final @NotNull FeatureTableFX table) {
    final var preferredAnnotationType = DataTypes.get(PreferredAnnotationType.class);
    for (final Map.Entry<TreeTableColumn<ModularFeatureListRow, ?>, ColumnID> entry : table.getNewColumnMap()
        .entrySet()) {
      final @NotNull ColumnID columnId = entry.getValue();
      if (columnId.getType() != ColumnType.ROW_TYPE || columnId.getRaw() != null) {
        continue;
      }
      if (!preferredAnnotationType.equals(columnId.getDataType())) {
        continue;
      }
      if (columnId.getSubColIndex() == 0) {
        return entry.getKey();
      }
    }
    return null;
  }

}
