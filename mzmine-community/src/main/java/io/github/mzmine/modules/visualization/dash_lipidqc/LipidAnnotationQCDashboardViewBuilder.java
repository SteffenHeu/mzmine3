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

import io.github.mzmine.datamodel.features.FeatureList.FeatureListAppliedMethod;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.DataTypeValueChangeListener;
import io.github.mzmine.datamodel.features.types.DataTypes;
import io.github.mzmine.datamodel.features.types.annotations.LipidMatchListType;
import io.github.mzmine.datamodel.features.types.annotations.PreferredAnnotationType;
import io.github.mzmine.datamodel.features.types.fx.ColumnID;
import io.github.mzmine.datamodel.features.types.fx.ColumnType;
import io.github.mzmine.javafx.components.factories.FxLabels;
import io.github.mzmine.javafx.components.factories.FxSplitPanes;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnalysisType;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnnotationModule;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnnotationParameters;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.molecular_species.MolecularSpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.species_level.SpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.LipidAnnotationLevel;
import io.github.mzmine.modules.visualization.dash_lipidqc.state.DashboardFilterState;
import io.github.mzmine.modules.visualization.dash_lipidqc.layout.DashboardLayoutFactory;
import io.github.mzmine.modules.visualization.dash_lipidqc.quality.AnnotationQualityPane;
import io.github.mzmine.modules.visualization.dash_lipidqc.retention.EquivalentCarbonNumberPane;
import io.github.mzmine.modules.visualization.dash_lipidqc.isotope.IsotopePane;
import io.github.mzmine.modules.visualization.dash_lipidqc.kendrick.KendrickPane;
import io.github.mzmine.modules.visualization.dash_lipidqc.summary.LipidSummaryPane;
import io.github.mzmine.modules.visualization.dash_lipidqc.matched.MatchedSignalsPane;
import io.github.mzmine.modules.visualization.featurelisttable_modular.FeatureTableFX;
import io.github.mzmine.util.FeatureTableFXUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    preferredLevelCombo.getSelectionModel().select(PreferredLipidLevelOption.MOLECULAR_SPECIES);
    final LipidSummaryPane summaryPane = new LipidSummaryPane(model, filterState,
        preferredLevelCombo);
    summaryPane.setOnGroupSelectedRowIds(
        rowIds -> Platform.runLater(() -> selectAndScrollToGroupRow(model, rowIds)));

    final BorderPane retentionSection = DashboardLayoutFactory.wrapInSubsection(
        "Retention time analysis", ecnPane);
    final @Nullable Label retentionTitleLabel =
        retentionSection.getTop() instanceof Label label ? label : null;
    final Label retentionDisabledLabel = FxLabels.newLabel(
        "Retention time analysis is disabled for this lipid analysis mode.");
    retentionDisabledLabel.setStyle("-fx-padding: 8;");
    final BooleanProperty retentionAnalysisEnabled = new SimpleBooleanProperty(true);

    final Runnable applyDashboardModeFromFeatureList = () -> {
      final @Nullable ModularFeatureList currentFeatureList = model.getFeatureList();
      final boolean includeRetention = shouldIncludeRetentionTimeAnalysis(currentFeatureList);
      retentionAnalysisEnabled.set(includeRetention);
      qualityPane.setIncludeRetentionTimeAnalysis(includeRetention);
      kendrickPane.setIncludeRetentionTimeAnalysis(includeRetention);
      if (retentionTitleLabel != null) {
        retentionTitleLabel.textProperty().unbind();
      }
      if (includeRetention) {
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
    };

    final Runnable refreshAllDashboardPlots = () -> {
      applyDashboardModeFromFeatureList.run();
      final @Nullable ModularFeatureList currentFeatureList = model.getFeatureList();
      final @Nullable FeatureListRow selectedRow = model.getRow();
      if (currentFeatureList != null) {
        summaryPane.setFeatureList(currentFeatureList);
        qualityPane.setFeatureList(currentFeatureList);
        kendrickPane.setFeatureList(currentFeatureList);
        if (retentionAnalysisEnabled.get()) {
          ecnPane.setFeatureList(currentFeatureList);
        }
        isotopePane.setFeatureList(currentFeatureList);
        matchedSignalsPane.setFeatureList(currentFeatureList);
      }
      qualityPane.setRow(selectedRow);
      kendrickPane.setRow(selectedRow);
      if (retentionAnalysisEnabled.get()) {
        ecnPane.setRow(selectedRow);
      }
      isotopePane.setRow(selectedRow);
      matchedSignalsPane.setRow(selectedRow);
    };

    final AtomicBoolean lipidAnnotationRefreshScheduled = new AtomicBoolean(false);
    final DataTypeValueChangeListener<Object> lipidAnnotationListener =
        (_, _, _, _) -> {
          if (lipidAnnotationRefreshScheduled.compareAndSet(false, true)) {
            Platform.runLater(() -> {
              lipidAnnotationRefreshScheduled.set(false);
              refreshAllDashboardPlots.run();
            });
          }
        };
    final var lipidMatchesType = DataTypes.get(LipidMatchListType.class);
    final var preferredAnnotationType = DataTypes.get(PreferredAnnotationType.class);
    final AtomicReference<@Nullable ModularFeatureList> annotationListenerFeatureList =
        new AtomicReference<>(null);
    final Runnable updateLipidAnnotationListener = () -> {
      final @Nullable ModularFeatureList currentFeatureList = model.getFeatureList();
      final @Nullable ModularFeatureList previousFeatureList =
          annotationListenerFeatureList.getAndSet(currentFeatureList);
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

    qualityPane.setOnAnnotationsChanged(refreshAllDashboardPlots);

    model.featureTableFxProperty().get().getSelectionModel().selectedItemProperty()
        .addListener((_, _, row) -> model.setRow(row == null ? null : row.getValue()));
    model.featureTableFxProperty().get().getFilteredRowItems().addListener(
        (javafx.collections.ListChangeListener<javafx.scene.control.TreeItem<io.github.mzmine.datamodel.features.ModularFeatureListRow>>) _ -> Platform.runLater(
            () -> {
              refreshAllDashboardPlots.run();
              selectFirstVisibleRow(model);
            }));
    model.rowProperty().addListener((_, _, row) -> {
      if (row != null) {
        final @Nullable FeatureListRow tableSelectedRow = model.getFeatureTableFx().getSelectedRow();
        if (tableSelectedRow == null || tableSelectedRow.getID() != row.getID()) {
          FeatureTableFXUtil.selectAndScrollTo(row, model.getFeatureTableFx());
        }
      }
      isotopePane.setRow(row);
      if (retentionAnalysisEnabled.get()) {
        ecnPane.setRow(row);
      }
      matchedSignalsPane.setRow(row);
      kendrickPane.setRow(row);
      qualityPane.setRow(row);
    });

    model.featureListProperty().subscribe(flist -> {
      updateLipidAnnotationListener.run();
      applyDashboardModeFromFeatureList.run();
      if (retentionAnalysisEnabled.get()) {
        ecnPane.setFeatureList(flist);
      }
      isotopePane.setFeatureList(flist);
      matchedSignalsPane.setFeatureList(flist);
      kendrickPane.setFeatureList(flist);
      summaryPane.setFeatureList(flist);
      qualityPane.setFeatureList(flist);
      Platform.runLater(() -> selectFirstVisibleRow(model));
    });

    filterState.setOnChange(() -> {
      applyPreferredAnnotationSortForClassFilter(filterState, model.getFeatureTableFx());
      kendrickPane.applyFilters();
    });

    updateLipidAnnotationListener.run();
    applyDashboardModeFromFeatureList.run();

    final Region dashboardContent = DashboardLayoutFactory.createSixPaneLayout(
        DashboardLayoutFactory.wrapInSubsection("Lipid annotation summary", summaryPane),
        DashboardLayoutFactory.wrapInSubsection("Kendrick mass plot", kendrickPane),
        DashboardLayoutFactory.wrapInSubsection("Lipid annotation quality", qualityPane),
        retentionSection,
        DashboardLayoutFactory.wrapInSubsection("Matched lipid signals", matchedSignalsPane),
        DashboardLayoutFactory.wrapInSubsection("Isotope pattern", isotopePane));

    preferredLevelCombo.setOnAction(_ -> {
      final PreferredLipidLevelOption option = preferredLevelCombo.getValue();
      if (option == null) {
        return;
      }
      applyPreferredLipidLevel(model.getFeatureList(), option.level);
      model.getFeatureTableFx().refresh();
      refreshAllDashboardPlots.run();
    });
    final var mainSplit = FxSplitPanes.newSplitPane(0.68, Orientation.VERTICAL, dashboardContent,
        model.getFeatureTableController().buildView());

    return mainSplit;
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
    if (firstRow != null && (model.getRow() == null || model.getRow().getID() != firstRow.getID())) {
      FeatureTableFXUtil.selectAndScrollTo(firstRow, table);
      model.setRow(firstRow);
    }
  }

  private static void selectAndScrollToGroupRow(
      final @NotNull LipidAnnotationQCDashboardModel model,
      final @NotNull Set<Integer> rowIds) {
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

  private static void applyPreferredLipidLevel(final @NotNull ModularFeatureList featureList,
      final @NotNull LipidAnnotationLevel level) {
    for (final FeatureListRow row : featureList.getRows()) {
      final List<MatchedLipid> matches = row.getLipidMatches();
      if (matches.isEmpty()) {
        continue;
      }
      final List<MatchedLipid> preferredLevel = matches.stream()
          .filter(match -> isMatchingLevel(match, level)).toList();
      if (preferredLevel.isEmpty()) {
        continue;
      }
      final List<MatchedLipid> reordered = new ArrayList<>(preferredLevel);
      reordered.addAll(matches.stream().filter(match -> !isMatchingLevel(match, level)).toList());
      row.setLipidAnnotations(reordered);
    }
  }

  private static boolean shouldIncludeRetentionTimeAnalysis(
      final @Nullable ModularFeatureList featureList) {
    if (featureList == null) {
      return true;
    }
    final List<FeatureListAppliedMethod> appliedMethods = featureList.getAppliedMethods();
    for (int i = appliedMethods.size() - 1; i >= 0; i--) {
      final FeatureListAppliedMethod appliedMethod = appliedMethods.get(i);
      if (!(appliedMethod.getModule() instanceof LipidAnnotationModule)) {
        continue;
      }
      try {
        final LipidAnalysisType analysisType = appliedMethod.getParameters()
            .getParameter(LipidAnnotationParameters.lipidAnalysisType).getValue();
        return analysisType == null || analysisType.hasRetentionTimePattern();
      } catch (RuntimeException ignored) {
        return true;
      }
    }
    return true;
  }

  private static boolean isMatchingLevel(final @NotNull MatchedLipid match,
      final @NotNull LipidAnnotationLevel level) {
    return switch (level) {
      case SPECIES_LEVEL -> match.getLipidAnnotation() instanceof SpeciesLevelAnnotation;
      case MOLECULAR_SPECIES_LEVEL ->
          match.getLipidAnnotation() instanceof MolecularSpeciesLevelAnnotation;
    };
  }

  private static void applyPreferredAnnotationSortForClassFilter(
      final @NotNull DashboardFilterState filterState, final @NotNull FeatureTableFX table) {
    if (filterState.getBarSelectedRowIds().isEmpty()) {
      return;
    }

    final @Nullable TreeTableColumn<ModularFeatureListRow, ?> preferredAnnotationColumn =
        findPreferredAnnotationColumn(table);
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
