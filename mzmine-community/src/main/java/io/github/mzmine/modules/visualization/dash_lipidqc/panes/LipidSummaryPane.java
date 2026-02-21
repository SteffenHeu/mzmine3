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

package io.github.mzmine.modules.visualization.dash_lipidqc.panes;

import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.types.annotations.LipidMatchListType;
import io.github.mzmine.javafx.mvci.FxUpdateTask;
import io.github.mzmine.javafx.mvci.LatestTaskScheduler;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.MSMSLipidTools;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.molecular_species.MolecularSpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.species_level.SpeciesLevelAnnotation;
import io.github.mzmine.modules.visualization.dash_lipidqc.LipidAnnotationQCDashboardModel;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.category.DefaultCategoryDataset;
import io.github.mzmine.gui.chartbasics.gui.javafx.EChartViewer;

public class LipidSummaryPane extends BorderPane {

  private static final Pattern SUBCLASS_TOKEN_PATTERN = Pattern.compile(
      "^([A-Za-z][A-Za-z0-9-]*(?:\\s+[OP]-)?)");

  private final @NotNull LipidAnnotationQCDashboardModel model;
  private final @NotNull LatestTaskScheduler scheduler = new LatestTaskScheduler();
  private final @NotNull Label placeholder = new Label("Select a feature list with lipid annotations.");
  private final @NotNull DashboardFilterState filterState;
  private final @NotNull ComboBox<SummaryGroup> groupSelector = new ComboBox<>(
      FXCollections.observableArrayList(SummaryGroup.values()));
  private final @NotNull ComboBox<SummaryCountMode> countModeSelector = new ComboBox<>(
      FXCollections.observableArrayList(SummaryCountMode.values()));
  private final @NotNull Button clearFilterButton = new Button("Clear filter");

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

    clearFilterButton.setOnAction(_ -> {
      selectedGroup = null;
      filterState.setBarSelectedRowIds(Set.of());
      final Runnable onChange = filterState.getOnChange();
      if (onChange != null) {
        onChange.run();
      }
      requestChartUpdate();
    });

    final HBox preferredLevelRow = new HBox(6, new Label("Preferred level:"),
        preferredLevelCombo);
    preferredLevelRow.setAlignment(Pos.CENTER_LEFT);
    final HBox groupByRow = new HBox(6, new Label("Group by:"), groupSelector);
    groupByRow.setAlignment(Pos.CENTER_LEFT);
    final HBox countModeRow = new HBox(6, new Label("Count mode:"), countModeSelector);
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

  private void applySummaryResult(final @NotNull SummaryComputationResult result) {
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
    final SelectableBarRenderer selectable = new SelectableBarRenderer(selectedGroup);
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

  private record SummaryComputationResult(@Nullable String placeholderText,
                                          @NotNull SummaryGroup grouping,
                                          @NotNull SummaryCountMode countMode,
                                          @NotNull Map<String, Integer> groupToCount,
                                          @NotNull Map<String, Set<Integer>> groupToRowIds,
                                          @NotNull Map<String, String> groupTooltip,
                                          int totalCount,
                                          @Nullable String selectedGroup) {

  }

  private static final class SummaryComputationTask extends FxUpdateTask<LipidSummaryPane> {

    private final @Nullable ModularFeatureList featureList;
    private final @Nullable SummaryGroup grouping;
    private final @Nullable SummaryCountMode countMode;
    private final @Nullable String selectedGroup;
    private @NotNull SummaryComputationResult result;

    private SummaryComputationTask(final @NotNull LipidSummaryPane pane,
        final @Nullable ModularFeatureList featureList, final @Nullable SummaryGroup grouping,
        final @Nullable SummaryCountMode countMode, final @Nullable String selectedGroup) {
      super("lipidqc-summary-update", pane);
      this.featureList = featureList;
      this.grouping = grouping;
      this.countMode = countMode;
      this.selectedGroup = selectedGroup;
      final SummaryGroup fallbackGrouping =
          grouping != null ? grouping : SummaryGroup.LIPID_SUBCLASS;
      final SummaryCountMode fallbackCountMode =
          countMode != null ? countMode : SummaryCountMode.ROW_COUNT;
      result = new SummaryComputationResult("Select a feature list with lipid annotations.",
          fallbackGrouping, fallbackCountMode, Map.of(), Map.of(), Map.of(), 0, null);
    }

    @Override
    protected void process() {
      if (featureList == null) {
        result = new SummaryComputationResult("Select a feature list with lipid annotations.",
            fallbackGrouping(), fallbackCountMode(), Map.of(), Map.of(), Map.of(), 0, null);
        return;
      }
      final SummaryGroup localGrouping = fallbackGrouping();
      final SummaryCountMode localCountMode = fallbackCountMode();
      final List<MatchedLipid> bestMatches = featureList.getRows().stream()
          .map(FeatureListRow::getLipidMatches).filter(matches -> !matches.isEmpty())
          .map(List::getFirst).toList();
      if (bestMatches.isEmpty()) {
        result = new SummaryComputationResult("No lipid annotations available in this feature list.",
            localGrouping, localCountMode, Map.of(), Map.of(), Map.of(), 0, null);
        return;
      }

      final Map<String, Integer> groupToCount = new TreeMap<>();
      final Map<String, Set<String>> groupToUniqueAnnotations = new TreeMap<>();
      final Map<String, String> groupTooltip = new TreeMap<>();
      final Map<String, Set<Integer>> groupToRowIds = new TreeMap<>();
      int totalLipidRows = 0;
      for (final FeatureListRow row : featureList.getRows()) {
        final List<MatchedLipid> matches = row.get(LipidMatchListType.class);
        if (matches == null || matches.isEmpty()) {
          continue;
        }
        totalLipidRows++;
        if (localCountMode == SummaryCountMode.ROW_COUNT) {
          final MatchedLipid lipid = matches.getFirst();
          final String groupName = localGrouping.extractGroupLabel(lipid, extractSubclassToken(lipid));
          groupTooltip.putIfAbsent(groupName,
              localGrouping.extractTooltip(lipid, extractSubclassToken(lipid)));
          groupToCount.merge(groupName, 1, Integer::sum);
          groupToRowIds.computeIfAbsent(groupName, _ -> new HashSet<>()).add(row.getID());
          continue;
        }

        final Set<String> rowMolecularAggregateKeys = new HashSet<>();
        for (final MatchedLipid lipid : matches) {
          if (lipid.getLipidAnnotation() instanceof MolecularSpeciesLevelAnnotation) {
            final String speciesKey = speciesAggregateKey(lipid);
            if (speciesKey != null) {
              rowMolecularAggregateKeys.add(speciesKey);
            }
          }
        }
        final Set<String> rowUniqueAnnotationKeys = new HashSet<>();
        for (final MatchedLipid lipid : matches) {
          final String groupName = localGrouping.extractGroupLabel(lipid, extractSubclassToken(lipid));
          groupTooltip.putIfAbsent(groupName,
              localGrouping.extractTooltip(lipid, extractSubclassToken(lipid)));
          groupToRowIds.computeIfAbsent(groupName, _ -> new HashSet<>()).add(row.getID());

          final @Nullable String uniqueAnnotationKey = uniqueAnnotationKey(lipid,
              rowMolecularAggregateKeys);
          if (uniqueAnnotationKey == null || !rowUniqueAnnotationKeys.add(uniqueAnnotationKey)) {
            continue;
          }
          groupToUniqueAnnotations.computeIfAbsent(groupName, _ -> new HashSet<>())
              .add(uniqueAnnotationKey);
        }
      }
      if (localCountMode == SummaryCountMode.UNIQUE_ANNOTATIONS) {
        groupToUniqueAnnotations.forEach(
            (group, annotations) -> groupToCount.put(group, annotations.size()));
      }
      final int totalCount = localCountMode == SummaryCountMode.UNIQUE_ANNOTATIONS
          ? groupToUniqueAnnotations.values().stream().flatMap(Set::stream)
          .collect(java.util.stream.Collectors.toSet()).size() : totalLipidRows;

      final @Nullable String effectiveSelectedGroup =
          selectedGroup != null && groupToCount.containsKey(selectedGroup) ? selectedGroup : null;
      result = new SummaryComputationResult(null, localGrouping, localCountMode, groupToCount,
          groupToRowIds, groupTooltip, totalCount, effectiveSelectedGroup);
    }

    private @NotNull SummaryGroup fallbackGrouping() {
      return grouping != null ? grouping : SummaryGroup.LIPID_SUBCLASS;
    }

    private @NotNull SummaryCountMode fallbackCountMode() {
      return countMode != null ? countMode : SummaryCountMode.ROW_COUNT;
    }

    @Override
    protected void updateGuiModel() {
      model.applySummaryResult(result);
    }

    @Override
    public @NotNull String getTaskDescription() {
      return "Calculating lipid summary plot";
    }

    @Override
    public double getFinishedPercentage() {
      return 0d;
    }
  }

  private static @NotNull Color summaryLabelColor() {
    return io.github.mzmine.main.MZmineCore.getConfiguration().isDarkMode()
        ? new Color(230, 230, 230) : new Color(35, 35, 35);
  }

  private static @NotNull String extractSubclassToken(final @NotNull MatchedLipid lipid) {
    final String annotation = lipid.getLipidAnnotation().getAnnotation();
    if (annotation == null || annotation.isBlank()) {
      return lipid.getLipidAnnotation().getLipidClass().getAbbr();
    }
    final Matcher matcher = SUBCLASS_TOKEN_PATTERN.matcher(annotation);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }
    final String[] parts = annotation.trim().split("\\s+", 2);
    if (parts.length > 0 && !parts[0].isBlank()) {
      return parts[0];
    }
    return lipid.getLipidAnnotation().getLipidClass().getAbbr();
  }

  private static @Nullable String uniqueAnnotationKey(final @NotNull MatchedLipid lipid,
      final @NotNull Set<String> rowMolecularAggregateKeys) {
    final String annotation = lipid.getLipidAnnotation().getAnnotation();
    final String speciesAggregateKey = speciesAggregateKey(lipid);
    if (lipid.getLipidAnnotation() instanceof SpeciesLevelAnnotation) {
      if (speciesAggregateKey != null && rowMolecularAggregateKeys.contains(
          speciesAggregateKey)) {
        return null;
      }
      return speciesAggregateKey == null ? "S|" + annotation : "S|" + speciesAggregateKey;
    }
    if (lipid.getLipidAnnotation() instanceof MolecularSpeciesLevelAnnotation) {
      return "M|" + annotation;
    }
    return "A|" + annotation;
  }

  private static @Nullable String speciesAggregateKey(final @NotNull MatchedLipid lipid) {
    final var carbonsDbe = MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
        lipid.getLipidAnnotation().getAnnotation());
    final int carbons = carbonsDbe.getKey();
    final int dbe = carbonsDbe.getValue();
    if (carbons < 0 || dbe < 0) {
      return null;
    }
    return lipid.getLipidAnnotation().getLipidClass().getName() + "|" + carbons + ":" + dbe;
  }

  private void showPlaceholder(final @NotNull String text) {
    placeholder.setText(text);
    setCenter(placeholder);
  }

  private static final class SelectableBarRenderer extends BarRenderer {

    private final @Nullable String selectedGroup;
    private final @NotNull Color selected = ConfigService.getDefaultColorPalette().getPositiveColorAWT();
    private final @NotNull Color normal = ConfigService.getDefaultColorPalette().getMainColorAWT();

    private SelectableBarRenderer(final @Nullable String selectedGroup) {
      this.selectedGroup = selectedGroup;
      setBarPainter(new StandardBarPainter());
      setShadowVisible(false);
    }

    @Override
    public @NotNull java.awt.Paint getItemPaint(final int row, final int column) {
      final var plot = getPlot();
      if (plot != null && plot.getDataset() != null) {
        final String key = Objects.toString(plot.getDataset().getColumnKey(column), null);
        if (selectedGroup != null && selectedGroup.equals(key)) {
          return selected;
        }
      }
      return normal;
    }
  }

  private enum SummaryCountMode {
    ROW_COUNT("Rows", "Number of lipid annotations", "Lipid annotations",
        "Total lipids: "), UNIQUE_ANNOTATIONS("Unique annotations",
        "Number of unique lipid annotations", "Unique lipid annotations",
        "Total unique annotations: ");

    private final @NotNull String label;
    private final @NotNull String rangeAxisLabel;
    private final @NotNull String seriesLabel;
    private final @NotNull String totalLabelPrefix;

    SummaryCountMode(final @NotNull String label, final @NotNull String rangeAxisLabel,
        final @NotNull String seriesLabel, final @NotNull String totalLabelPrefix) {
      this.label = label;
      this.rangeAxisLabel = rangeAxisLabel;
      this.seriesLabel = seriesLabel;
      this.totalLabelPrefix = totalLabelPrefix;
    }

    private @NotNull String getRangeAxisLabel() {
      return rangeAxisLabel;
    }

    private @NotNull String getSeriesLabel() {
      return seriesLabel;
    }

    private @NotNull String getTotalLabelPrefix() {
      return totalLabelPrefix;
    }

    @Override
    public @NotNull String toString() {
      return label;
    }
  }

  private enum SummaryGroup {
    LIPID_SUBCLASS("Lipid subclass") {
      @Override
      @NotNull
      String extractGroupLabel(final @NotNull MatchedLipid lipid, final @NotNull String subclassToken) {
        return subclassToken;
      }

      @Override
      @NotNull
      String extractTooltip(final @NotNull MatchedLipid lipid, final @NotNull String subclassToken) {
        return lipid.getLipidAnnotation().getLipidClass().getName();
      }
    }, LIPID_MAIN_CLASS("Lipid main class") {
      @Override
      @NotNull
      String extractGroupLabel(final @NotNull MatchedLipid lipid, final @NotNull String subclassToken) {
        return lipid.getLipidAnnotation().getLipidClass().getMainClass().getName();
      }
    }, LIPID_CATEGORY("Lipid category") {
      @Override
      @NotNull
      String extractGroupLabel(final @NotNull MatchedLipid lipid, final @NotNull String subclassToken) {
        return lipid.getLipidAnnotation().getLipidClass().getMainClass().getLipidCategory()
            .getName();
      }
    };

    private final @NotNull String axisLabel;

    SummaryGroup(final @NotNull String axisLabel) {
      this.axisLabel = axisLabel;
    }

    private @NotNull String getAxisLabel() {
      return axisLabel;
    }

    abstract @NotNull String extractGroupLabel(@NotNull MatchedLipid lipid,
        @NotNull String subclassToken);

    @NotNull String extractTooltip(final @NotNull MatchedLipid lipid,
        final @NotNull String subclassToken) {
      return extractGroupLabel(lipid, subclassToken);
    }

    @Override
    public @NotNull String toString() {
      return axisLabel;
    }
  }
}
