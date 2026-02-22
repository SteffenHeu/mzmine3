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

package io.github.mzmine.modules.visualization.dash_lipidqc.summary;

import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.SelectableCategoryBarRenderer;
import io.github.mzmine.javafx.components.factories.FxButtons;
import io.github.mzmine.javafx.components.factories.FxLabels;
import io.github.mzmine.javafx.mvci.LatestTaskScheduler;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.modules.visualization.dash_lipidqc.LipidAnnotationQCDashboardModel;
import io.github.mzmine.modules.visualization.dash_lipidqc.state.DashboardFilterState;
import java.awt.Color;
import java.awt.Font;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.CategoryTextAnnotation;
import org.jfree.chart.axis.CategoryAnchor;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.entity.CategoryItemEntity;
import org.jfree.chart.fx.interaction.ChartMouseEventFX;
import org.jfree.chart.fx.interaction.ChartMouseListenerFX;
import org.jfree.chart.labels.CategoryToolTipGenerator;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.category.DefaultCategoryDataset;
import io.github.mzmine.gui.chartbasics.gui.javafx.EChartViewer;
import javafx.scene.input.MouseButton;

public class LipidSummaryPane extends BorderPane {

  private final @NotNull LipidAnnotationQCDashboardModel model;
  private final @NotNull LatestTaskScheduler scheduler = new LatestTaskScheduler();
  private final @NotNull Label placeholder = new Label("Select a feature list with lipid annotations.");
  private final @NotNull DashboardFilterState filterState;
  private final @NotNull ComboBox<SummaryGroup> groupSelector = new ComboBox<>(
      FXCollections.observableArrayList(SummaryGroup.values()));
  private final @NotNull ComboBox<SummaryCountMode> countModeSelector = new ComboBox<>(
      FXCollections.observableArrayList(SummaryCountMode.values()));
  private final @NotNull Button clearFilterButton;

  private @Nullable ModularFeatureList featureList;
  private @Nullable String selectedGroup;
  private final @NotNull Map<String, Set<Integer>> groupToRowIds = new TreeMap<>();

  public LipidSummaryPane(final @NotNull LipidAnnotationQCDashboardModel model,
      final @NotNull DashboardFilterState filterState,
      final @NotNull ComboBox<?> preferredLevelCombo) {
    this.model = model;
    this.filterState = filterState;

    groupSelector.getSelectionModel().select(SummaryGroup.LIPID_SUBCLASS);
    countModeSelector.getSelectionModel().select(SummaryCountMode.ROW_COUNT);
    groupSelector.valueProperty().addListener((_, _, _) -> requestChartUpdate());
    countModeSelector.valueProperty().addListener((_, _, _) -> requestChartUpdate());

    clearFilterButton = FxButtons.createButton("Clear filter", this::clearSummaryFilter);

    final HBox preferredLevelRow = new HBox(6, FxLabels.newLabel("Preferred level:"),
        preferredLevelCombo);
    preferredLevelRow.setAlignment(Pos.CENTER_LEFT);
    final HBox groupByRow = new HBox(6, FxLabels.newLabel("Group by:"), groupSelector);
    groupByRow.setAlignment(Pos.CENTER_LEFT);
    final HBox countModeRow = new HBox(6, FxLabels.newLabel("Count mode:"), countModeSelector);
    countModeRow.setAlignment(Pos.CENTER_LEFT);
    final HBox actionRow = new HBox(6, clearFilterButton);
    actionRow.setAlignment(Pos.CENTER_LEFT);

    final VBox filterControls = new VBox(6, preferredLevelRow, groupByRow, countModeRow,
        actionRow);
    filterControls.setAlignment(Pos.TOP_LEFT);
    final TitledPane filterPane = new TitledPane("Summary filters", filterControls);
    filterPane.setCollapsible(true);
    final Accordion filterAccordion = new Accordion(filterPane);
    filterAccordion.setExpandedPane(null);
    setBottom(filterAccordion);

    model.getFeatureTableFx().sceneProperty().addListener((_, _, scene) -> {
      if (scene != null) {
        scene.getRoot().styleProperty().addListener((_, _, _) -> requestChartUpdate());
      }
    });
    showPlaceholder("Select a feature list with lipid annotations.");
  }

  private void clearSummaryFilter() {
    selectedGroup = null;
    filterState.setBarSelectedRowIds(Set.of());
    final Runnable onChange = filterState.getOnChange();
    if (onChange != null) {
      onChange.run();
    }
    requestChartUpdate();
  }

  public void setFeatureList(final @NotNull ModularFeatureList featureList) {
    this.featureList = featureList;
    requestChartUpdate();
  }

  private void requestChartUpdate() {
    final SummaryGroup grouping = groupSelector.getValue();
    final SummaryCountMode countMode = countModeSelector.getValue();
    scheduler.onTaskThreadDelayed(
        new SummaryComputationTask(this, featureList, grouping, countMode, selectedGroup),
        Duration.millis(120));
  }

  void applySummaryResult(final @NotNull SummaryComputationResult result) {
    if (result.placeholderText() != null) {
      showPlaceholder(result.placeholderText());
      groupToRowIds.clear();
      return;
    }

    selectedGroup = result.selectedGroup();
    groupToRowIds.clear();
    groupToRowIds.putAll(result.groupToRowIds());

    final DefaultCategoryDataset dataset = new DefaultCategoryDataset();
    result.groupToCount().forEach(
        (group, count) -> dataset.addValue(count, result.countMode().getSeriesLabel(), group));

    final JFreeChart chart = ChartFactory.createBarChart(null, result.grouping().getAxisLabel(),
        result.countMode().getRangeAxisLabel(), dataset, PlotOrientation.VERTICAL,
        false, true, false);
    chart.getCategoryPlot().getDomainAxis().setCategoryLabelPositions(CategoryLabelPositions.UP_45);
    final EChartViewer viewer = new EChartViewer(chart);
    final SelectableCategoryBarRenderer selectable = new SelectableCategoryBarRenderer();
    selectable.setSelectedCategoryKey(selectedGroup);
    selectable.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator());
    selectable.setDefaultItemLabelsVisible(true);
    final Color textColor = summaryLabelColor();
    selectable.setDefaultItemLabelPaint(textColor);
    final double maxCount = Math.max(1d,
        result.groupToCount().values().stream().mapToInt(Integer::intValue).max().orElse(1));
    chart.getCategoryPlot().getRangeAxis().setUpperMargin(0.24d);
    if (dataset.getColumnCount() > 0) {
      final Comparable<?> rightCategory = dataset.getColumnKey(dataset.getColumnCount() - 1);
      final CategoryTextAnnotation totalAnnotation = new CategoryTextAnnotation(
          result.countMode().getTotalLabelPrefix() + result.totalCount(), rightCategory,
          maxCount * 1.12d);
      totalAnnotation.setCategoryAnchor(CategoryAnchor.END);
      totalAnnotation.setTextAnchor(TextAnchor.CENTER_RIGHT);
      totalAnnotation.setFont(new Font("SansSerif", Font.BOLD, 12));
      totalAnnotation.setPaint(textColor);
      chart.getCategoryPlot().addAnnotation(totalAnnotation);
    }
    selectable.setDefaultToolTipGenerator(
        (CategoryToolTipGenerator) (tipDataset, row, column) -> result.groupTooltip()
            .getOrDefault(Objects.toString(tipDataset.getColumnKey(column), ""),
                Objects.toString(tipDataset.getColumnKey(column), "")));
    viewer.addChartMouseListener(new ChartMouseListenerFX() {
      @Override
      public void chartMouseClicked(final ChartMouseEventFX event) {
        if (event.getTrigger() == null || !event.getTrigger().isStillSincePress()
            || event.getTrigger().getButton() != MouseButton.PRIMARY) {
          return;
        }
        if (event.getEntity() instanceof CategoryItemEntity categoryEntity) {
          final String key = Objects.toString(categoryEntity.getColumnKey(), null);
          if (key != null && groupToRowIds.containsKey(key)) {
            selectedGroup = key.equals(selectedGroup) ? null : key;
            filterState.setBarSelectedRowIds(
                selectedGroup == null ? Set.of() : Set.copyOf(groupToRowIds.get(selectedGroup)));
            final Runnable onChange = filterState.getOnChange();
            if (onChange != null) {
              onChange.run();
            }
            requestChartUpdate();
          }
        }
      }

      @Override
      public void chartMouseMoved(final ChartMouseEventFX event) {
      }
    });
    ConfigService.getConfiguration().getDefaultChartTheme().apply(viewer);
    if (chart.getTitle() != null) {
      chart.getTitle().setPaint(textColor);
    }
    chart.getCategoryPlot().getDomainAxis().setLabelPaint(textColor);
    chart.getCategoryPlot().getDomainAxis().setTickLabelPaint(textColor);
    chart.getCategoryPlot().getRangeAxis().setLabelPaint(textColor);
    chart.getCategoryPlot().getRangeAxis().setTickLabelPaint(textColor);
    chart.getCategoryPlot().setRenderer(selectable);
    setCenter(viewer);
  }

  private static @NotNull Color summaryLabelColor() {
    return io.github.mzmine.main.ConfigService.getConfiguration().isDarkMode()
        ? new Color(230, 230, 230) : new Color(35, 35, 35);
  }

  private void showPlaceholder(final @NotNull String text) {
    placeholder.setText(text);
    setCenter(placeholder);
  }
}
