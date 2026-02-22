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

package io.github.mzmine.modules.visualization.dash_lipidqc.retention;

import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.types.annotations.LipidMatchListType;
import io.github.mzmine.gui.chartbasics.gui.javafx.EChartViewer;
import io.github.mzmine.javafx.components.factories.FxLabels;
import io.github.mzmine.javafx.mvci.LatestTaskScheduler;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.MSMSLipidTools;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.molecular_species.MolecularSpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.species_level.SpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.ILipidAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.ILipidClass;
import io.github.mzmine.modules.visualization.dash_lipidqc.LipidAnnotationQCDashboardModel;
import io.github.mzmine.modules.visualization.equivalentcarbonnumberplot.EquivalentCarbonNumberChart;
import io.github.mzmine.modules.visualization.equivalentcarbonnumberplot.EquivalentCarbonNumberDataset;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.FeatureTableFXUtil;
import java.awt.Color;
import java.awt.Paint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Accordion;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.fx.interaction.ChartMouseEventFX;
import org.jfree.chart.fx.interaction.ChartMouseListenerFX;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

public class EquivalentCarbonNumberPane extends BorderPane {

  private static final @NotNull Color SELECTED_POINT_COLOR = ConfigService.getDefaultColorPalette()
      .getPositiveColorAWT();
  private final LipidAnnotationQCDashboardModel model;
  private final @NotNull LatestTaskScheduler scheduler = new LatestTaskScheduler();
  private final @NotNull Label placeholder = FxLabels.newLabel(
      "Select a row with lipid annotations.");
  private final StringProperty paneTitle = new SimpleStringProperty("Retention time analysis");
  private final ComboBox<RetentionTrendMode> trendModeCombo = new ComboBox<>(
      FXCollections.observableArrayList(RetentionTrendMode.values()));
  private List<FeatureListRow> rowsWithLipidIds = List.of();
  private @Nullable FeatureListRow selectedRow;

  public EquivalentCarbonNumberPane(final @NotNull LipidAnnotationQCDashboardModel model) {
    this.model = model;
    trendModeCombo.getSelectionModel().select(RetentionTrendMode.COMBINED_CARBON_DBE_TRENDS);
    trendModeCombo.valueProperty().addListener((_, _, _) -> requestUpdate());
    final HBox trendRow = new HBox(6, FxLabels.newLabel("Trend:"), trendModeCombo);
    trendRow.setAlignment(Pos.CENTER_LEFT);
    final VBox controlBox = new VBox(6, trendRow);
    controlBox.setAlignment(Pos.TOP_LEFT);
    final TitledPane controls = new TitledPane("Retention time analysis options", controlBox);
    controls.setCollapsible(true);
    final Accordion accordion = new Accordion(controls);
    accordion.setExpandedPane(null);
    setBottom(accordion);
    setCenter(placeholder);
    BorderPane.setAlignment(placeholder, Pos.CENTER);
  }

  public @NotNull StringProperty paneTitleProperty() {
    return paneTitle;
  }

  public void setFeatureList(final @NotNull ModularFeatureList featureList) {
    rowsWithLipidIds = featureList.getRows().stream()
        .filter(EquivalentCarbonNumberPane::rowHasMatchedLipidSignals)
        .map(r -> (FeatureListRow) r).toList();
    requestUpdate();
  }

  public void setRow(final @Nullable FeatureListRow row) {
    selectedRow = row;
    requestUpdate();
  }

  private static boolean rowHasMatchedLipidSignals(final @NotNull FeatureListRow row) {
    final List<MatchedLipid> matches = row.get(LipidMatchListType.class);
    return matches != null && !matches.isEmpty();
  }

  private void requestUpdate() {
    final List<FeatureListRow> currentRowsWithLipidIds = getCurrentRowsWithLipidIds();
    final RetentionTrendMode mode = trendModeCombo.getValue();
    scheduler.onTaskThreadDelayed(new RetentionComputationTask(this, selectedRow,
        currentRowsWithLipidIds, mode), Duration.millis(120));
  }

  void applyComputationResult(final @NotNull RetentionComputationResult result) {
    updatePaneTitle("Retention time analysis");
    if (result.placeholderText() != null) {
      showPlaceholder(result.placeholderText());
      return;
    }

    final RetentionComputationPayload payload = result.payload();
    if (payload == null) {
      showPlaceholder("No retention trend data available.");
      return;
    }
    updatePaneTitle("Retention time analysis: " + payload.mode());
    switch (payload) {
      case EcnRetentionPayload ecnPayload -> showEcnTrend(ecnPayload.selectedMatch(),
          ecnPayload.rowsWithLipidIds(), ecnPayload.selectedClass(), ecnPayload.dbe(),
          ecnPayload.matchCount());
      case DbeRetentionPayload dbePayload -> showDbeTrend(dbePayload.selectedMatch(),
          dbePayload.selectedClass(), dbePayload.carbons(), dbePayload.selectedDbe(),
          dbePayload.dataset());
      case CombinedRetentionPayload combinedPayload -> showCombinedTrend(
          combinedPayload.selectedMatch(), combinedPayload.selectedClass(),
          combinedPayload.selectedCarbons(), combinedPayload.selectedDbe(),
          combinedPayload.carbonsDataset(), combinedPayload.dbeDataset());
    }
  }

  private void showEcnTrend(final @NotNull MatchedLipid selectedMatch,
      final @NotNull List<FeatureListRow> currentRowsWithLipidIds,
      final @NotNull ILipidClass selectedClass, final int dbe, final int matchCount) {
    showPlaceholder("Loading ECN model...");
    final EquivalentCarbonNumberDataset dataset = new EquivalentCarbonNumberDataset(
        currentRowsWithLipidIds, currentRowsWithLipidIds.toArray(new FeatureListRow[0]),
        selectedClass, dbe);
    final int selectedRowId = selectedRow == null ? -1 : selectedRow.getID();
    final Runnable renderChart = () -> {
      if (trendModeCombo.getValue() != RetentionTrendMode.ECN_CARBON_TREND) {
        return;
      }
      if (selectedRowId >= 0 && (selectedRow == null || selectedRow.getID() != selectedRowId)) {
        return;
      }
      final EquivalentCarbonNumberChart chart = new EquivalentCarbonNumberChart("",
          "Retention time", "Number of carbons", dataset);
      configureNoCrosshair(chart.getChart().getXYPlot());
      if (chart.getChart().getXYPlot().getRenderer(0) instanceof XYLineAndShapeRenderer renderer) {
        renderer.setSeriesPaint(0, ecnTrendDatasetColor());
        renderer.setDefaultItemLabelPaint(retentionLabelPaint());
      }
      if (chart.getChart().getXYPlot().getRenderer(1) instanceof XYLineAndShapeRenderer renderer) {
        renderer.setSeriesPaint(0, ecnTrendDatasetColor());
      }
      chart.addChartMouseListener(new ChartMouseListenerFX() {
        @Override
        public void chartMouseClicked(final ChartMouseEventFX event) {
          if (event.getTrigger() == null || !event.getTrigger().isStillSincePress()
              || event.getTrigger().getButton() != MouseButton.PRIMARY) {
            return;
          }
          if (!(event.getEntity() instanceof XYItemEntity entity)
              || entity.getDataset() != dataset) {
            return;
          }
          final int item = entity.getItem();
          if (item < 0 || item >= dataset.getItemCount(0)) {
            return;
          }
          final MatchedLipid clickedLipid = dataset.getMatchedLipid(item);
          final FeatureListRow clickedRow = findRowForLipid(clickedLipid);
          if (clickedRow != null) {
            model.setRow(clickedRow);
            FeatureTableFXUtil.selectAndScrollTo(clickedRow, model.getFeatureTableFx());
          }
        }

        @Override
        public void chartMouseMoved(final ChartMouseEventFX event) {
        }
      });
      if (selectedRow != null) {
        highlightSelectedLipid(chart, selectedRow, selectedMatch);
      }
      chart.setMinSize(250, 120);
      updatePaneTitle(
          "Retention time analysis: " + selectedClass.getAbbr() + " ECN DBE=" + dbe + " (R2 "
              + ConfigService.getConfiguration().getScoreFormat().format(chart.getR2()) + ", n="
              + matchCount + ")");
      setCenter(chart);
    };
    dataset.addTaskStatusListener((_, newStatus, _) -> {
      if (newStatus == TaskStatus.FINISHED) {
        Platform.runLater(renderChart);
      } else if (newStatus == TaskStatus.ERROR || newStatus == TaskStatus.CANCELED) {
        Platform.runLater(
            () -> showPlaceholder("Not enough lipids for ECN model or computation failed."));
      }
    });
    if (dataset.getStatus() == TaskStatus.FINISHED) {
      renderChart.run();
    } else if (dataset.getStatus() == TaskStatus.ERROR
        || dataset.getStatus() == TaskStatus.CANCELED) {
      showPlaceholder("Not enough lipids for ECN model or computation failed.");
    }
  }

  private void showDbeTrend(final @NotNull MatchedLipid selectedMatch,
      final @NotNull ILipidClass selectedClass, final int carbons, final int selectedDbe,
      final @NotNull RetentionTrendDataset trendDataset) {
    final TrendChartResult chartResult = createTrendChart(trendDataset, "Number of DBEs",
        dbeTrendDatasetColor());
    final EChartViewer chart = chartResult.chart();
    configureNoCrosshair(chart.getChart().getXYPlot());
    chart.addChartMouseListener(new ChartMouseListenerFX() {
      @Override
      public void chartMouseClicked(final ChartMouseEventFX event) {
        if (event.getTrigger() == null || !event.getTrigger().isStillSincePress()
            || event.getTrigger().getButton() != MouseButton.PRIMARY) {
          return;
        }
        if (!(event.getEntity() instanceof XYItemEntity entity)
            || !(entity.getDataset() instanceof RetentionTrendDataset clickedDataset)) {
          return;
        }
        final int item = entity.getItem();
        if (item < 0 || item >= clickedDataset.getItemCount(0)) {
          return;
        }
        final MatchedLipid clickedLipid = clickedDataset.getMatchedLipid(item);
        if (clickedLipid == null) {
          return;
        }
        final FeatureListRow clickedRow = findRowForLipid(clickedLipid);
        if (clickedRow != null) {
          model.setRow(clickedRow);
          FeatureTableFXUtil.selectAndScrollTo(clickedRow, model.getFeatureTableFx());
        }
      }

      @Override
      public void chartMouseMoved(final ChartMouseEventFX event) {
      }
    });

    if (selectedRow != null) {
      highlightSelectedTrendPoint(chart, selectedRow, selectedDbe);
    }
    updatePaneTitle(
        "Retention time analysis: " + selectedClass.getAbbr() + " DBE C=" + carbons + " (R2 "
            + ConfigService.getConfiguration().getScoreFormat().format(chartResult.r2()) + ", n="
            + trendDataset.getItemCount(0) + ")");
    setCenter(chart);
  }

  private void showCombinedTrend(final @NotNull MatchedLipid selectedMatch,
      final @NotNull ILipidClass selectedClass, final int selectedCarbons,
      final int selectedDbe, final @Nullable RetentionTrendDataset carbonTrendDataset,
      final @Nullable RetentionTrendDataset dbeTrendDataset) {
    final CombinedTrendChartResult chartResult = createCombinedTrendChart(
        carbonTrendDataset, dbeTrendDataset);
    final EChartViewer chart = chartResult.chart();
    configureNoCrosshair(chart.getChart().getXYPlot());
    chart.addChartMouseListener(new ChartMouseListenerFX() {
      @Override
      public void chartMouseClicked(final ChartMouseEventFX event) {
        if (event.getTrigger() == null || !event.getTrigger().isStillSincePress()
            || event.getTrigger().getButton() != MouseButton.PRIMARY) {
          return;
        }
        if (!(event.getEntity() instanceof XYItemEntity entity)
            || !(entity.getDataset() instanceof RetentionTrendDataset clickedDataset)) {
          return;
        }
        final int item = entity.getItem();
        if (item < 0 || item >= clickedDataset.getItemCount(0)) {
          return;
        }
        final MatchedLipid clickedLipid = clickedDataset.getMatchedLipid(item);
        if (clickedLipid == null) {
          return;
        }
        final FeatureListRow clickedRow = findRowForLipid(clickedLipid);
        if (clickedRow != null) {
          model.setRow(clickedRow);
          FeatureTableFXUtil.selectAndScrollTo(clickedRow, model.getFeatureTableFx());
        }
      }

      @Override
      public void chartMouseMoved(final ChartMouseEventFX event) {
      }
    });

    if (selectedRow != null) {
      highlightSelectedCombinedTrendPoints(chart, selectedRow, selectedCarbons, selectedDbe,
          chartResult);
    }
    updatePaneTitle(
        "Retention time analysis: " + selectedClass.getAbbr() + " " + selectedCarbons + ":"
            + selectedDbe);
    setCenter(chart);
  }


  private static TrendChartResult createTrendChart(final @NotNull RetentionTrendDataset dataset,
      final @NotNull String yAxisLabel, final @NotNull Paint trendPaint) {
    final JFreeChart chart = ChartFactory.createScatterPlot("", "Retention time", yAxisLabel,
        dataset, PlotOrientation.VERTICAL, false, true, true);
    final EChartViewer viewer = new EChartViewer(chart);
    ConfigService.getConfiguration().getDefaultChartTheme().apply(viewer);

    final XYPlot plot = chart.getXYPlot();
    configureNoCrosshair(plot);
    final XYLineAndShapeRenderer pointRenderer = new XYLineAndShapeRenderer(false, true);
    pointRenderer.setSeriesShape(0, new Ellipse2D.Double(-3, -3, 6, 6));
    pointRenderer.setSeriesPaint(0, trendPaint);
    pointRenderer.setDefaultItemLabelGenerator(
        (xyDataset, series, item) -> dataset.getLabel(item));
    pointRenderer.setDefaultItemLabelPaint(retentionLabelPaint());
    pointRenderer.setDefaultItemLabelsVisible(true);
    pointRenderer.setDefaultToolTipGenerator(
        (xyDataset, series, item) -> dataset.getTooltip(item));
    plot.setRenderer(0, pointRenderer);

    final double[] regression = calculateLinearRegression(dataset);
    if (dataset.getItemCount(0) > 1 && Double.isFinite(regression[0]) && Double.isFinite(
        regression[1])) {
      plot.setDataset(1, createRegressionDataset(regression[0], regression[1], dataset));
      final XYLineAndShapeRenderer regressionRenderer = new XYLineAndShapeRenderer(true, false);
      regressionRenderer.setSeriesPaint(0, trendPaint);
      plot.setRenderer(1, regressionRenderer);
    }
    return new TrendChartResult(viewer, regression[2]);
  }

  private static double[] calculateLinearRegression(
      final @NotNull RetentionTrendDataset dataset) {
    final int itemCount = dataset.getItemCount(0);
    if (itemCount < 2) {
      return new double[]{Double.NaN, Double.NaN, Double.NaN};
    }
    double sumX = 0d;
    double sumY = 0d;
    double sumXX = 0d;
    double sumXY = 0d;
    for (int i = 0; i < itemCount; i++) {
      final double x = dataset.getXValue(0, i);
      final double y = dataset.getYValue(0, i);
      sumX += x;
      sumY += y;
      sumXX += x * x;
      sumXY += x * y;
    }
    final double denominator = itemCount * sumXX - sumX * sumX;
    if (Math.abs(denominator) < 1e-10d) {
      return new double[]{Double.NaN, Double.NaN, Double.NaN};
    }
    final double slope = (itemCount * sumXY - sumX * sumY) / denominator;
    final double intercept = (sumY - slope * sumX) / itemCount;

    final double meanY = sumY / itemCount;
    double ssr = 0d;
    double sse = 0d;
    for (int i = 0; i < itemCount; i++) {
      final double x = dataset.getXValue(0, i);
      final double y = dataset.getYValue(0, i);
      final double predicted = slope * x + intercept;
      ssr += (predicted - meanY) * (predicted - meanY);
      sse += (y - predicted) * (y - predicted);
    }
    final double r2 = (ssr + sse) > 0d ? ssr / (ssr + sse) : Double.NaN;
    return new double[]{slope, intercept, r2};
  }

  private static @NotNull XYSeriesCollection createRegressionDataset(final double slope,
      final double intercept, final @NotNull RetentionTrendDataset dataset) {
    final double minX = java.util.Arrays.stream(dataset.getXValues()).min().orElse(0d);
    final double maxX = java.util.Arrays.stream(dataset.getXValues()).max().orElse(minX);
    final XYSeries series = new XYSeries("Regression");
    series.add(minX, slope * minX + intercept);
    series.add(maxX, slope * maxX + intercept);
    final XYSeriesCollection regressionDataset = new XYSeriesCollection();
    regressionDataset.addSeries(series);
    return regressionDataset;
  }

  private static @NotNull CombinedTrendChartResult createCombinedTrendChart(
      final @Nullable RetentionTrendDataset carbonDataset,
      final @Nullable RetentionTrendDataset dbeDataset) {
    final XYPlot plot = new XYPlot();
    configureNoCrosshair(plot);
    final NumberAxis domainAxis = new NumberAxis("Retention time");
    plot.setDomainAxis(domainAxis);

    int carbonAxisIndex = -1;
    int dbeAxisIndex = -1;
    double carbonR2 = Double.NaN;
    double dbeR2 = Double.NaN;
    final boolean hasCarbon = carbonDataset != null;
    final boolean hasDbe = dbeDataset != null;
    final int carbonDatasetIndex = hasCarbon ? 0 : -1;
    final int dbeDatasetIndex = hasDbe ? (hasCarbon ? 1 : 0) : -1;
    final int carbonRegressionIndex = hasCarbon ? (hasDbe ? 2 : 1) : -1;
    final int dbeRegressionIndex = hasDbe ? (hasCarbon ? 3 : 1) : -1;
    if (carbonDataset != null) {
      carbonAxisIndex = 0;
      final NumberAxis carbonAxis = new NumberAxis("Number of carbons");
      plot.setRangeAxis(carbonAxisIndex, carbonAxis);
      plot.setDataset(carbonDatasetIndex, carbonDataset);
      plot.mapDatasetToRangeAxis(carbonDatasetIndex, carbonAxisIndex);
      plot.setRenderer(carbonDatasetIndex, createTrendPointRenderer(carbonDataset,
          ecnTrendDatasetColor(),
          new Ellipse2D.Double(-3.2d, -3.2d, 6.4d, 6.4d)));
      final double[] regression = calculateLinearRegression(carbonDataset);
      carbonR2 = regression[2];
      appendRegressionDatasetIfValid(plot, carbonRegressionIndex, regression, carbonDataset,
          carbonAxisIndex, ecnTrendDatasetColor());
    }
    if (dbeDataset != null) {
      dbeAxisIndex = carbonDataset != null ? 1 : 0;
      final NumberAxis dbeAxis = new NumberAxis("Number of DBEs");
      plot.setRangeAxis(dbeAxisIndex, dbeAxis);
      plot.setDataset(dbeDatasetIndex, dbeDataset);
      plot.mapDatasetToRangeAxis(dbeDatasetIndex, dbeAxisIndex);
      plot.setRenderer(dbeDatasetIndex, createTrendPointRenderer(dbeDataset,
          dbeTrendDatasetColor(),
          new Rectangle2D.Double(-3d, -3d, 6d, 6d)));
      final double[] regression = calculateLinearRegression(dbeDataset);
      dbeR2 = regression[2];
      appendRegressionDatasetIfValid(plot, dbeRegressionIndex, regression, dbeDataset,
          dbeAxisIndex, dbeTrendDatasetColor());
    }

    final JFreeChart chart = new JFreeChart("", JFreeChart.DEFAULT_TITLE_FONT, plot, false);
    chart.setBackgroundPaint(new Color(0, 0, 0, 0));
    final EChartViewer viewer = new EChartViewer(chart);
    ConfigService.getConfiguration().getDefaultChartTheme().apply(viewer);
    enforceCombinedDatasetColors(plot, carbonDatasetIndex, dbeDatasetIndex, carbonRegressionIndex,
        dbeRegressionIndex);
    configureNoCrosshair(plot);
    configureCombinedAxisRanges(plot, carbonDataset, dbeDataset, carbonAxisIndex, dbeAxisIndex);
    return new CombinedTrendChartResult(viewer, carbonR2, dbeR2, carbonAxisIndex, dbeAxisIndex);
  }

  private static void enforceCombinedDatasetColors(final @NotNull XYPlot plot,
      final int carbonDatasetIndex, final int dbeDatasetIndex, final int carbonRegressionIndex,
      final int dbeRegressionIndex) {
    if (carbonDatasetIndex >= 0
        && plot.getRenderer(carbonDatasetIndex) instanceof XYLineAndShapeRenderer renderer) {
      renderer.setSeriesPaint(0, ecnTrendDatasetColor());
      renderer.setDefaultItemLabelPaint(retentionLabelPaint());
    }
    if (dbeDatasetIndex >= 0
        && plot.getRenderer(dbeDatasetIndex) instanceof XYLineAndShapeRenderer renderer) {
      renderer.setSeriesPaint(0, dbeTrendDatasetColor());
      renderer.setDefaultItemLabelPaint(retentionLabelPaint());
    }
    if (carbonRegressionIndex >= 0
        && plot.getRenderer(carbonRegressionIndex) instanceof XYLineAndShapeRenderer renderer) {
      renderer.setSeriesPaint(0, ecnTrendDatasetColor());
    }
    if (dbeRegressionIndex >= 0
        && plot.getRenderer(dbeRegressionIndex) instanceof XYLineAndShapeRenderer renderer) {
      renderer.setSeriesPaint(0, dbeTrendDatasetColor());
    }
  }

  private static void configureCombinedAxisRanges(final @NotNull XYPlot plot,
      final @Nullable RetentionTrendDataset carbonDataset,
      final @Nullable RetentionTrendDataset dbeDataset, final int carbonAxisIndex,
      final int dbeAxisIndex) {
    if (plot.getDomainAxis() instanceof NumberAxis domainAxis) {
      double minX = Double.POSITIVE_INFINITY;
      double maxX = Double.NEGATIVE_INFINITY;
      if (carbonDataset != null) {
        for (int i = 0; i < carbonDataset.getItemCount(0); i++) {
          final double value = carbonDataset.getXValue(0, i);
          if (Double.isFinite(value)) {
            minX = Math.min(minX, value);
            maxX = Math.max(maxX, value);
          }
        }
      }
      if (dbeDataset != null) {
        for (int i = 0; i < dbeDataset.getItemCount(0); i++) {
          final double value = dbeDataset.getXValue(0, i);
          if (Double.isFinite(value)) {
            minX = Math.min(minX, value);
            maxX = Math.max(maxX, value);
          }
        }
      }
      setAxisRangeToData(domainAxis, minX, maxX, true);
    }

    if (carbonAxisIndex >= 0 && carbonDataset != null && plot.getRangeAxis(
        carbonAxisIndex) instanceof NumberAxis carbonAxis) {
      setAxisRangeToData(carbonAxis, minDatasetY(carbonDataset), maxDatasetY(carbonDataset),
          false);
    }
    if (dbeAxisIndex >= 0 && dbeDataset != null && plot.getRangeAxis(
        dbeAxisIndex) instanceof NumberAxis dbeAxis) {
      setAxisRangeToData(dbeAxis, minDatasetY(dbeDataset), maxDatasetY(dbeDataset), false);
    }
  }

  private static double minDatasetY(final @NotNull RetentionTrendDataset dataset) {
    double min = Double.POSITIVE_INFINITY;
    for (int i = 0; i < dataset.getItemCount(0); i++) {
      final double value = dataset.getYValue(0, i);
      if (Double.isFinite(value)) {
        min = Math.min(min, value);
      }
    }
    return min;
  }

  private static double maxDatasetY(final @NotNull RetentionTrendDataset dataset) {
    double max = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < dataset.getItemCount(0); i++) {
      final double value = dataset.getYValue(0, i);
      if (Double.isFinite(value)) {
        max = Math.max(max, value);
      }
    }
    return max;
  }

  private static void setAxisRangeToData(final @NotNull NumberAxis axis, final double min,
      final double max, final boolean lockLowerToFirstPoint) {
    if (!Double.isFinite(min) || !Double.isFinite(max)) {
      return;
    }
    axis.setAutoRange(false);
    axis.setAutoRangeIncludesZero(false);
    axis.setAutoRangeStickyZero(false);

    if (max <= min) {
      final double delta = Math.max(Math.abs(min) * 0.05d, 0.2d);
      final double lower = lockLowerToFirstPoint ? min : min - delta;
      axis.setRange(lower, min + delta);
      return;
    }

    final double span = max - min;
    final double lowerPadding = lockLowerToFirstPoint ? 0d : span * 0.03d;
    final double upperPadding = span * 0.03d;
    axis.setRange(min - lowerPadding, max + upperPadding);
  }

  private static void appendRegressionDatasetIfValid(final @NotNull XYPlot plot,
      final int datasetIndex, final @NotNull double[] regression,
      final @NotNull RetentionTrendDataset dataset, final int rangeAxisIndex,
      final @NotNull Paint linePaint) {
    if (datasetIndex < 0 || dataset.getItemCount(0) < 2 || !Double.isFinite(regression[0])
        || !Double.isFinite(regression[1])) {
      return;
    }
    plot.setDataset(datasetIndex, createRegressionDataset(regression[0], regression[1], dataset));
    plot.mapDatasetToRangeAxis(datasetIndex, rangeAxisIndex);
    final XYLineAndShapeRenderer regressionRenderer = new XYLineAndShapeRenderer(true, false);
    regressionRenderer.setSeriesPaint(0, linePaint);
    regressionRenderer.setSeriesStroke(0, new java.awt.BasicStroke(1.6f));
    regressionRenderer.setSeriesVisibleInLegend(0, false);
    plot.setRenderer(datasetIndex, regressionRenderer);
  }

  private static @NotNull XYLineAndShapeRenderer createTrendPointRenderer(
      final @NotNull RetentionTrendDataset dataset, final @NotNull Paint seriesPaint,
      final @NotNull java.awt.Shape seriesShape) {
    final XYLineAndShapeRenderer pointRenderer = new XYLineAndShapeRenderer(false, true);
    pointRenderer.setSeriesShape(0, seriesShape);
    pointRenderer.setSeriesPaint(0, seriesPaint);
    pointRenderer.setDefaultItemLabelGenerator(
        (xyDataset, series, item) -> dataset.getLabel(item));
    pointRenderer.setDefaultItemLabelPaint(retentionLabelPaint());
    pointRenderer.setDefaultItemLabelsVisible(true);
    pointRenderer.setDefaultToolTipGenerator(
        (xyDataset, series, item) -> dataset.getTooltip(item));
    pointRenderer.setSeriesVisibleInLegend(0, false);
    return pointRenderer;
  }

  private void updatePaneTitle(final @NotNull String title) {
    paneTitle.set(title);
  }

  static double clampToUnit(final double value) {
    return Math.max(0d, Math.min(1d, value));
  }

  private @Nullable FeatureListRow findRowForLipid(@NotNull MatchedLipid clickedLipid) {
    for (FeatureListRow candidate : getCurrentRowsWithLipidIds()) {
      final List<MatchedLipid> matches = candidate.getLipidMatches();
      if (!matches.isEmpty() && clickedLipid.equals(matches.getFirst())) {
        return candidate;
      }
    }
    return null;
  }

  private @NotNull List<FeatureListRow> getCurrentRowsWithLipidIds() {
    if (rowsWithLipidIds.isEmpty()) {
      return List.of();
    }
    final List<FeatureListRow> current = rowsWithLipidIds.stream()
        .filter(EquivalentCarbonNumberPane::rowHasMatchedLipidSignals).toList();
    rowsWithLipidIds = current;
    return current;
  }

  private static void highlightSelectedLipid(EquivalentCarbonNumberChart chart,
      final @NotNull FeatureListRow row, final @NotNull MatchedLipid selectedMatch) {
    final int carbons = extractCarbons(selectedMatch.getLipidAnnotation());
    if (carbons < 0 || row.getAverageRT() == null) {
      return;
    }
    addSelectedOverlayPoint(chart.getChart().getXYPlot(), 2, 0, row.getAverageRT(), carbons,
        SELECTED_POINT_COLOR, new Ellipse2D.Double(-5d, -5d, 10d, 10d));
  }

  private static void highlightSelectedTrendPoint(final @NotNull EChartViewer chart,
      final @NotNull FeatureListRow row, final double yValue) {
    if (row.getAverageRT() == null || !Double.isFinite(yValue)) {
      return;
    }
    addSelectedOverlayPoint(chart.getChart().getXYPlot(), 2, 0, row.getAverageRT(), yValue,
        SELECTED_POINT_COLOR, new Ellipse2D.Double(-5d, -5d, 10d, 10d));
  }

  private static void highlightSelectedCombinedTrendPoints(final @NotNull EChartViewer chart,
      final @NotNull FeatureListRow row, final double carbonValue, final double dbeValue,
      final @NotNull CombinedTrendChartResult chartResult) {
    if (row.getAverageRT() == null) {
      return;
    }
    final XYPlot plot = chart.getChart().getXYPlot();
    plot.clearDomainMarkers();
    plot.setDataset(10, null);
    plot.setRenderer(10, null);
    plot.setDataset(11, null);
    plot.setRenderer(11, null);

    final double rt = row.getAverageRT();
    final Paint selectedColor = SELECTED_POINT_COLOR;
    final boolean hasCarbonPoint =
        chartResult.carbonsAxisIndex() >= 0 && Double.isFinite(carbonValue);
    final boolean hasDbePoint = chartResult.dbeAxisIndex() >= 0 && Double.isFinite(dbeValue);
    if (!hasCarbonPoint || !hasDbePoint) {
      final ValueMarker selectionMarker = new ValueMarker(rt);
      selectionMarker.setPaint(selectedColor);
      selectionMarker.setStroke(new java.awt.BasicStroke(1.6f, java.awt.BasicStroke.CAP_BUTT,
          java.awt.BasicStroke.JOIN_BEVEL, 0f, new float[]{6f, 4f}, 0f));
      selectionMarker.setAlpha(0.75f);
      plot.addDomainMarker(selectionMarker);
    } else {
      synchronizeSelectedPointAcrossAxes(plot, chartResult.carbonsAxisIndex(),
          chartResult.dbeAxisIndex(), carbonValue, dbeValue);
    }

    if (hasCarbonPoint) {
      addSelectedOverlayPoint(plot, 10, chartResult.carbonsAxisIndex(), rt, carbonValue,
          selectedColor, new Ellipse2D.Double(-5d, -5d, 10d, 10d));
    }
    if (hasDbePoint) {
      addSelectedOverlayPoint(plot, 11, chartResult.dbeAxisIndex(), rt, dbeValue, selectedColor,
          new Ellipse2D.Double(-5d, -5d, 10d, 10d));
    }
  }

  private static void addSelectedOverlayPoint(final @NotNull XYPlot plot, final int datasetIndex,
      final int rangeAxisIndex, final double xValue, final double yValue,
      final @NotNull Paint strokePaint, final @NotNull java.awt.Shape marker) {
    final XYSeries overlaySeries = new XYSeries("Selected lipid");
    overlaySeries.add(xValue, yValue);
    final XYSeriesCollection overlayDataset = new XYSeriesCollection();
    overlayDataset.addSeries(overlaySeries);

    final XYLineAndShapeRenderer overlayRenderer = new XYLineAndShapeRenderer(false, true);
    overlayRenderer.setSeriesPaint(0, strokePaint);
    overlayRenderer.setSeriesStroke(0, new java.awt.BasicStroke(2f));
    overlayRenderer.setSeriesShape(0, marker);
    overlayRenderer.setDefaultShapesFilled(true);
    overlayRenderer.setUseOutlinePaint(true);
    overlayRenderer.setSeriesOutlinePaint(0,
        ConfigService.getConfiguration().isDarkMode() ? Color.WHITE : Color.BLACK);
    overlayRenderer.setSeriesOutlineStroke(0, new java.awt.BasicStroke(1.1f));
    overlayRenderer.setSeriesVisibleInLegend(0, false);

    plot.setDataset(datasetIndex, overlayDataset);
    plot.mapDatasetToRangeAxis(datasetIndex, rangeAxisIndex);
    plot.setRenderer(datasetIndex, overlayRenderer);
  }

  private static void synchronizeSelectedPointAcrossAxes(final @NotNull XYPlot plot,
      final int primaryAxisIndex, final int secondaryAxisIndex, final double primaryValue,
      final double secondaryValue) {
    if (!(plot.getRangeAxis(primaryAxisIndex) instanceof NumberAxis primaryAxis)
        || !(plot.getRangeAxis(secondaryAxisIndex) instanceof NumberAxis secondaryAxis)) {
      return;
    }
    final SelectedPointAxisSynchronizer synchronizer = new SelectedPointAxisSynchronizer(
        primaryAxis, secondaryAxis, primaryValue, secondaryValue);
    synchronizer.install();
    synchronizer.syncSecondaryToPrimary();
  }

  static int extractDbe(final @NotNull ILipidAnnotation lipidAnnotation) {
    if (lipidAnnotation instanceof MolecularSpeciesLevelAnnotation molecularAnnotation) {
      return MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
          molecularAnnotation.getAnnotation()).getValue();
    }
    if (lipidAnnotation instanceof SpeciesLevelAnnotation speciesAnnotation) {
      return MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
          speciesAnnotation.getAnnotation()).getValue();
    }
    return -1;
  }

  static int extractCarbons(final @NotNull ILipidAnnotation lipidAnnotation) {
    if (lipidAnnotation instanceof MolecularSpeciesLevelAnnotation molecularAnnotation) {
      return MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
          molecularAnnotation.getAnnotation()).getKey();
    }
    if (lipidAnnotation instanceof SpeciesLevelAnnotation speciesAnnotation) {
      return MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
          speciesAnnotation.getAnnotation()).getKey();
    }
    return -1;
  }

  private static @NotNull Paint retentionLabelPaint() {
    return ConfigService.getConfiguration().isDarkMode() ? new Color(230, 230, 230)
        : new Color(35, 35, 35);
  }

  private static @NotNull Color ecnTrendDatasetColor() {
    return ConfigService.getDefaultColorPalette().getPositiveColorAWT();
  }

  private static @NotNull Color dbeTrendDatasetColor() {
    return ConfigService.getDefaultColorPalette().getNeutralColorAWT();
  }

  private static void configureNoCrosshair(final @NotNull XYPlot plot) {
    plot.setDomainCrosshairVisible(false);
    plot.setRangeCrosshairVisible(false);
  }

  private void showPlaceholder(final @NotNull String text) {
    placeholder.setText(text);
    setCenter(placeholder);
  }
}
