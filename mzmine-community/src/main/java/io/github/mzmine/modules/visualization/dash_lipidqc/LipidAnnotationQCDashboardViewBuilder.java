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

import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.molecular_species.MolecularSpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.species_level.SpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.LipidAnnotationLevel;
import io.github.mzmine.modules.visualization.dash_lipidqc.panes.DashboardFilterState;
import io.github.mzmine.modules.visualization.dash_lipidqc.panes.DashboardLayoutFactory;
import io.github.mzmine.modules.visualization.dash_lipidqc.panes.AnnotationQualityPane;
import io.github.mzmine.modules.visualization.dash_lipidqc.panes.EquivalentCarbonNumberPane;
import io.github.mzmine.modules.visualization.dash_lipidqc.panes.IsotopePane;
import io.github.mzmine.modules.visualization.dash_lipidqc.panes.KendrickPane;
import io.github.mzmine.modules.visualization.dash_lipidqc.panes.LipidSummaryPane;
import io.github.mzmine.modules.visualization.dash_lipidqc.panes.MatchedSignalsPane;
import io.github.mzmine.util.FeatureTableFXUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
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
    final MatchedSignalsPane matchedSignalsPane = new MatchedSignalsPane();
    final ComboBox<PreferredLipidLevelOption> preferredLevelCombo = new ComboBox<>(
        FXCollections.observableArrayList(PreferredLipidLevelOption.values()));
    preferredLevelCombo.getSelectionModel().select(PreferredLipidLevelOption.MOLECULAR_SPECIES);
    final LipidSummaryPane summaryPane = new LipidSummaryPane(model, filterState,
        preferredLevelCombo);
    final Runnable refreshAllDashboardPlots = () -> {
      final @Nullable ModularFeatureList currentFeatureList = model.getFeatureList();
      final @Nullable FeatureListRow selectedRow = model.getRow();
      if (currentFeatureList != null) {
        summaryPane.setFeatureList(currentFeatureList);
        qualityPane.setFeatureList(currentFeatureList);
        kendrickPane.setFeatureList(currentFeatureList);
        ecnPane.setFeatureList(currentFeatureList);
        isotopePane.setFeatureList(currentFeatureList);
        matchedSignalsPane.setFeatureList(currentFeatureList);
      }
      qualityPane.setRow(selectedRow);
      kendrickPane.setRow(selectedRow);
      ecnPane.setRow(selectedRow);
      isotopePane.setRow(selectedRow);
      matchedSignalsPane.setRow(selectedRow);
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
      if (row != null && model.getFeatureTableFx().getSelectedRow() != model.getRow()) {
        FeatureTableFXUtil.selectAndScrollTo(row, model.getFeatureTableFx());
      }
      isotopePane.setRow(row);
      ecnPane.setRow(row);
      matchedSignalsPane.setRow(row);
      kendrickPane.setRow(row);
      qualityPane.setRow(row);
    });

    model.featureListProperty().subscribe(flist -> {
      ecnPane.setFeatureList(flist);
      isotopePane.setFeatureList(flist);
      matchedSignalsPane.setFeatureList(flist);
      kendrickPane.setFeatureList(flist);
      summaryPane.setFeatureList(flist);
      qualityPane.setFeatureList(flist);
      Platform.runLater(() -> selectFirstVisibleRow(model));
    });

    filterState.setOnChange(() -> {
      final Set<Integer> rowIds = filterState.getBarSelectedRowIds();
      if (rowIds == null || rowIds.isEmpty()) {
        model.getFeatureTableController().getFilterModel().setIdFilter("");
      } else {
        model.getFeatureTableController().getFilterModel().setIdFilter(
            rowIds.stream().sorted().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(""));
      }
      kendrickPane.applyFilters();
      Platform.runLater(() -> selectFirstVisibleRow(model));
    });

    final BorderPane retentionSection = DashboardLayoutFactory.wrapInSubsection(
        "Retention time analysis", ecnPane);
    if (retentionSection.getTop() instanceof Label titleLabel) {
      titleLabel.textProperty().bind(ecnPane.paneTitleProperty());
    }

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
    final SplitPane mainSplit = new SplitPane(dashboardContent,
        model.getFeatureTableController().buildView());
    mainSplit.setOrientation(Orientation.VERTICAL);
    mainSplit.setDividerPositions(0.68);

    return mainSplit;
  }

  private static void selectFirstVisibleRow(@NotNull LipidAnnotationQCDashboardModel model) {
    final var table = model.getFeatureTableFx();
    final var filteredItems = table.getFilteredRowItems();
    if (filteredItems == null || filteredItems.isEmpty()) {
      model.setRow(null);
      return;
    }
    final FeatureListRow firstRow = filteredItems.get(0).getValue();
    if (firstRow != null && !Objects.equals(model.getRow(), firstRow)) {
      FeatureTableFXUtil.selectAndScrollTo(firstRow, table);
      model.setRow(firstRow);
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

  private static boolean isMatchingLevel(final @NotNull MatchedLipid match,
      final @NotNull LipidAnnotationLevel level) {
    return switch (level) {
      case SPECIES_LEVEL -> match.getLipidAnnotation() instanceof SpeciesLevelAnnotation;
      case MOLECULAR_SPECIES_LEVEL ->
          match.getLipidAnnotation() instanceof MolecularSpeciesLevelAnnotation;
    };
  }

  private enum PreferredLipidLevelOption {
    SPECIES("Species level", LipidAnnotationLevel.SPECIES_LEVEL), MOLECULAR_SPECIES(
        "Molecular species level", LipidAnnotationLevel.MOLECULAR_SPECIES_LEVEL);

    private final @NotNull String label;
    private final @NotNull LipidAnnotationLevel level;

    PreferredLipidLevelOption(final @NotNull String label,
        final @NotNull LipidAnnotationLevel level) {
      this.label = label;
      this.level = level;
    }

    @Override
    public @NotNull String toString() {
      return label;
    }
  }

}
