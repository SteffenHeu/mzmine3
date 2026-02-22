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

package io.github.mzmine.modules.visualization.dash_lipidqc.kendrick;

import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.types.annotations.LipidMatchListType;
import io.github.mzmine.gui.chartbasics.chartutils.ColoredBubbleDatasetRenderer;
import io.github.mzmine.gui.chartbasics.simplechart.providers.XYItemObjectProvider;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.AlphaBubbleDatasetRenderer;
import io.github.mzmine.javafx.mvci.LatestTaskScheduler;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.modules.visualization.dash_lipidqc.LipidAnnotationQCDashboardModel;
import io.github.mzmine.modules.visualization.dash_lipidqc.kendrick.KendrickSubsetDataset;
import io.github.mzmine.modules.visualization.dash_lipidqc.state.DashboardFilterState;
import io.github.mzmine.modules.visualization.kendrickmassplot.KendrickMassPlotChart;
import io.github.mzmine.modules.visualization.kendrickmassplot.KendrickMassPlotParameters;
import io.github.mzmine.modules.visualization.kendrickmassplot.KendrickMassPlotXYZDataset;
import io.github.mzmine.modules.visualization.kendrickmassplot.KendrickPlotDataTypes;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsSelection;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.FeatureTableFXUtil;
import java.awt.Color;
import java.awt.Paint;
import java.awt.geom.Ellipse2D;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.util.Duration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.fx.interaction.ChartMouseEventFX;
import org.jfree.chart.fx.interaction.ChartMouseListenerFX;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.LookupPaintScale;
import org.jfree.chart.renderer.PaintScale;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.title.Title;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

public class KendrickPane extends BorderPane {

  private final @NotNull LipidAnnotationQCDashboardModel model;
  private final @NotNull DashboardFilterState filterState;
  private final @NotNull LatestTaskScheduler filterScheduler = new LatestTaskScheduler();
  private final @NotNull Label placeholder = new Label(
      "Select a feature list to build Kendrick plot.");
  private @Nullable ModularFeatureList featureList;
  private @Nullable KendrickMassPlotXYZDataset baseDataset;
  private @Nullable KendrickMassPlotChart chart;
  private @Nullable ColoredBubbleDatasetRenderer colorRenderer;
  private @Nullable ColoredBubbleDatasetRenderer filteredOutRenderer;
  private @Nullable FeatureListRow selectedRow;
  private boolean includeRetentionTimeAnalysis = true;
  private long filterRequestId;

  public KendrickPane(final @NotNull LipidAnnotationQCDashboardModel model,
      final @NotNull DashboardFilterState filterState) {
    this.model = model;
    this.filterState = filterState;
    setCenter(placeholder);
    BorderPane.setAlignment(placeholder, Pos.CENTER);
  }

  public void setRow(final @Nullable FeatureListRow row) {
    selectedRow = row;
    updateSelectionOverlay();
  }

  public void setFeatureList(final @NotNull ModularFeatureList featureList) {
    if (this.featureList == featureList && chart != null && baseDataset != null) {
      applyFilters();
      return;
    }

    if (baseDataset != null && baseDataset.getStatus() == TaskStatus.PROCESSING) {
      baseDataset.cancel();
    }
    discardChart();
    this.featureList = featureList;
    if (featureList.getNumberOfRows() == 0) {
      showPlaceholder("Feature list has no rows.");
      return;
    }

    showPlaceholder("Loading Kendrick mass plot...");
    final KendrickMassPlotParameters params = (KendrickMassPlotParameters) new KendrickMassPlotParameters().cloneParameterSet();
    params.setParameter(KendrickMassPlotParameters.featureList,
        new FeatureListsSelection(featureList));
    params.setParameter(KendrickMassPlotParameters.xAxisValues, KendrickPlotDataTypes.MZ);
    params.setParameter(KendrickMassPlotParameters.yAxisValues,
        KendrickPlotDataTypes.KENDRICK_MASS_DEFECT);
    params.setParameter(KendrickMassPlotParameters.yAxisCustomKendrickMassBase, "CH2");
    params.setParameter(KendrickMassPlotParameters.colorScaleValues,
        KendrickPlotDataTypes.RETENTION_TIME);
    params.setParameter(KendrickMassPlotParameters.bubbleSizeValues,
        KendrickPlotDataTypes.INTENSITY);

    final KendrickMassPlotXYZDataset dataset = new KendrickMassPlotXYZDataset(params, 1, 1);
    baseDataset = dataset;
    dataset.addTaskStatusListener((_, newStatus, _) -> {
      if (dataset != baseDataset) {
        return;
      }
      if (newStatus == TaskStatus.FINISHED) {
        Platform.runLater(() -> buildChart(dataset));
      } else if (newStatus == TaskStatus.ERROR || newStatus == TaskStatus.CANCELED) {
        Platform.runLater(() -> showPlaceholder("Kendrick mass plot could not be created."));
      }
    });
  }

  public void setIncludeRetentionTimeAnalysis(final boolean includeRetentionTimeAnalysis) {
    if (this.includeRetentionTimeAnalysis == includeRetentionTimeAnalysis) {
      return;
    }
    this.includeRetentionTimeAnalysis = includeRetentionTimeAnalysis;
    applyFilters();
  }

  public void applyFilters() {
    if (chart == null || baseDataset == null || colorRenderer == null
        || filteredOutRenderer == null) {
      return;
    }
    final boolean filterActive = !filterState.getBarSelectedRowIds().isEmpty();
    final Set<Integer> visibleIds = filterActive ? Set.copyOf(filterState.getBarSelectedRowIds())
        : Set.of();
    final long requestId = ++filterRequestId;
    filterScheduler.onTaskThreadDelayed(new KendrickFilterComputationTask(this, requestId,
        Objects.requireNonNull(baseDataset), featureList, visibleIds,
        includeRetentionTimeAnalysis), Duration.millis(120));
  }

  void applyFilterComputationResult(final @NotNull KendrickFilterComputationResult result) {
    if (result.requestId() != filterRequestId || chart == null || baseDataset == null
        || colorRenderer == null || filteredOutRenderer == null
        || result.baseDataset() != baseDataset) {
      return;
    }

    final XYPlot plot = chart.getChart().getXYPlot();
    colorRenderer.setPaintScale(result.filteredColorScale());
    updateColorScaleLegend(result.filteredColorScale());

    if (result.filteredOutDataset() != null) {
      filteredOutRenderer.setPaintScale(result.grayScale());
      plot.setDataset(0, result.filteredOutDataset());
      plot.setRenderer(0, filteredOutRenderer);
      plot.setDataset(1, result.inDataset());
      plot.setRenderer(1, colorRenderer);
    } else {
      plot.setDataset(0, result.inDataset());
      plot.setRenderer(0, colorRenderer);
      plot.setDataset(1, null);
      plot.setRenderer(1, null);
    }

    updateOutlierOverlay(result.outlierDataset());
    updateSelectionOverlay();
    optimizeVisibleKendrickAxes(plot);
    chart.getChart().fireChartChanged();
  }

  private void updateColorScaleLegend(final @NotNull PaintScale scale) {
    if (chart == null) {
      return;
    }
    final JFreeChart jChart = chart.getChart();
    Title existingLegend = null;
    for (int i = 0; i < jChart.getSubtitleCount(); i++) {
      final Title subtitle = jChart.getSubtitle(i);
      if (subtitle instanceof PaintScaleLegend) {
        existingLegend = subtitle;
        break;
      }
    }
    if (existingLegend != null) {
      jChart.removeSubtitle(existingLegend);
    }

    final XYPlot xyPlot = jChart.getXYPlot();
    final NumberAxis scaleAxis = new NumberAxis(null);
    scaleAxis.setRange(scale.getLowerBound(),
        Math.max(scale.getUpperBound(), scale.getLowerBound()));
    final Paint axisPaint = xyPlot.getDomainAxis().getAxisLinePaint();
    scaleAxis.setAxisLinePaint(axisPaint);
    scaleAxis.setTickMarkPaint(axisPaint);
    scaleAxis.setNumberFormatOverride(new DecimalFormat("0.#"));
    scaleAxis.setLabelFont(xyPlot.getDomainAxis().getLabelFont());
    scaleAxis.setLabelPaint(axisPaint);
    scaleAxis.setTickLabelFont(xyPlot.getDomainAxis().getTickLabelFont());
    scaleAxis.setTickLabelPaint(axisPaint);
    scaleAxis.setLabel("Retention time");

    final PaintScaleLegend legend = new PaintScaleLegend(scale, scaleAxis);
    legend.setPadding(5, 0, 5, 0);
    legend.setStripOutlineVisible(false);
    legend.setAxisLocation(AxisLocation.BOTTOM_OR_LEFT);
    legend.setAxisOffset(5.0);
    legend.setSubdivisionCount(500);
    legend.setPosition(RectangleEdge.RIGHT);
    legend.setBackgroundPaint(new Color(0, 0, 0, 0));
    jChart.addSubtitle(legend);
  }

  private void buildChart(final @NotNull KendrickMassPlotXYZDataset dataset) {
    final KendrickMassPlotChart newChart = new KendrickMassPlotChart("", "m/z",
        "Kendrick mass defect (CH2)", "Retention time", dataset);
    newChart.getChart().setTitle((String) null);
    final XYPlot plot = newChart.getChart().getXYPlot();
    plot.setDomainCrosshairVisible(false);
    plot.setRangeCrosshairVisible(false);
    plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);
    if (plot.getDomainAxis() instanceof NumberAxis domainAxis) {
      domainAxis.setAutoRangeIncludesZero(false);
      domainAxis.setAutoRangeStickyZero(false);
    }
    if (plot.getRangeAxis() instanceof NumberAxis rangeAxis) {
      rangeAxis.setAutoRangeIncludesZero(false);
      rangeAxis.setAutoRangeStickyZero(false);
    }

    final var baseRenderer = plot.getRenderer();
    final var tooltipGenerator =
        baseRenderer != null ? baseRenderer.getDefaultToolTipGenerator() : null;
    final PaintScale colorScale =
        baseRenderer instanceof ColoredBubbleDatasetRenderer colored ? colored.getPaintScale()
            : new LookupPaintScale(0d, 1d, Color.GRAY);

    colorRenderer = new AlphaBubbleDatasetRenderer(1f);
    colorRenderer.setPaintScale(colorScale);
    if (tooltipGenerator != null) {
      colorRenderer.setDefaultToolTipGenerator(tooltipGenerator);
    }

    filteredOutRenderer = new AlphaBubbleDatasetRenderer(0.35f);
    filteredOutRenderer.setPaintScale(new LookupPaintScale(0d, 1d, Color.GRAY));
    if (tooltipGenerator != null) {
      filteredOutRenderer.setDefaultToolTipGenerator(tooltipGenerator);
    }

    newChart.addChartMouseListener(new ChartMouseListenerFX() {
      @Override
      public void chartMouseClicked(final ChartMouseEventFX event) {
        if (event.getTrigger() == null || !event.getTrigger().isStillSincePress()
            || event.getTrigger().getButton() != MouseButton.PRIMARY) {
          return;
        }
        if (!(event.getEntity() instanceof XYItemEntity entity)) {
          return;
        }
        final FeatureListRow clickedRow = resolveClickedRow(entity.getDataset(),
            entity.getItem());
        if (clickedRow == null || Objects.equals(clickedRow, model.getRow())) {
          return;
        }
        model.setRow(clickedRow);
        FeatureTableFXUtil.selectAndScrollTo(clickedRow, model.getFeatureTableFx());
      }

      @Override
      public void chartMouseMoved(final ChartMouseEventFX event) {
      }
    });

    chart = newChart;
    setCenter(newChart);
    applyFilters();
  }

  private static void optimizeVisibleKendrickAxes(final @NotNull XYPlot plot) {
    final KendrickAxisExtrema xExtrema = collectVisibleExtrema(plot, true);
    final KendrickAxisExtrema yExtrema = collectVisibleExtrema(plot, false);
    if (plot.getDomainAxis() instanceof NumberAxis domainAxis && xExtrema.available()) {
      applyAxisExtrema(domainAxis, xExtrema);
    }
    if (plot.getRangeAxis() instanceof NumberAxis rangeAxis && yExtrema.available()) {
      applyAxisExtrema(rangeAxis, yExtrema);
    }
  }

  private static @NotNull KendrickAxisExtrema collectVisibleExtrema(final @NotNull XYPlot plot,
      final boolean xAxis) {
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    for (int datasetIndex = 0; datasetIndex <= 1; datasetIndex++) {
      final XYDataset dataset = plot.getDataset(datasetIndex);
      if (dataset == null || dataset.getSeriesCount() == 0) {
        continue;
      }
      for (int series = 0; series < dataset.getSeriesCount(); series++) {
        final int itemCount = dataset.getItemCount(series);
        for (int item = 0; item < itemCount; item++) {
          final double value =
              xAxis ? dataset.getXValue(series, item) : dataset.getYValue(series, item);
          if (!Double.isFinite(value)) {
            continue;
          }
          min = Math.min(min, value);
          max = Math.max(max, value);
        }
      }
    }
    return new KendrickAxisExtrema(min, max, Double.isFinite(min) && Double.isFinite(max));
  }

  private static void applyAxisExtrema(final @NotNull NumberAxis axis,
      final @NotNull KendrickAxisExtrema extrema) {
    if (!extrema.available()) {
      return;
    }
    axis.setAutoRange(false);
    axis.setAutoRangeIncludesZero(false);
    axis.setAutoRangeStickyZero(false);
    if (extrema.max() <= extrema.min()) {
      final double delta = Math.max(Math.abs(extrema.min()) * 0.02d, 0.05d);
      axis.setRange(extrema.min() - delta, extrema.max() + delta);
      return;
    }
    final double span = extrema.max() - extrema.min();
    final double padding = span * 0.02d;
    axis.setRange(extrema.min() - padding, extrema.max() + padding);
  }

  private void updateSelectionOverlay() {
    if (chart == null || baseDataset == null) {
      return;
    }
    final XYPlot plot = chart.getChart().getXYPlot();
    plot.setDataset(4, null);
    plot.setRenderer(4, null);
    if (selectedRow == null) {
      return;
    }

    int selectedIndex = -1;
    for (int i = 0; i < baseDataset.getItemCount(0); i++) {
      final FeatureListRow row = baseDataset.getItemObject(i);
      if (row != null && Objects.equals(row.getID(), selectedRow.getID())) {
        selectedIndex = i;
        break;
      }
    }
    if (selectedIndex < 0) {
      return;
    }

    final double x = baseDataset.getXValue(0, selectedIndex);
    final double y = baseDataset.getYValue(0, selectedIndex);
    if (!Double.isFinite(x) || !Double.isFinite(y)) {
      return;
    }
    final XYSeries selectedSeries = new XYSeries("Selected lipid");
    selectedSeries.add(x, y);
    final XYSeriesCollection selectedDataset = new XYSeriesCollection();
    selectedDataset.addSeries(selectedSeries);
    plot.setDataset(4, selectedDataset);
    plot.setRenderer(4, new SelectedLipidOverlayRenderer(getSelectedLabel(selectedRow)));
  }

  private void updateOutlierOverlay(final @Nullable KendrickSubsetDataset outlierDataset) {
    if (chart == null) {
      return;
    }
    final XYPlot plot = chart.getChart().getXYPlot();
    plot.setDataset(3, null);
    plot.setRenderer(3, null);
    if (outlierDataset == null || outlierDataset.getItemCount(0) == 0) {
      return;
    }

    final XYLineAndShapeRenderer outlierRenderer = new XYLineAndShapeRenderer(false, true);
    outlierRenderer.setSeriesPaint(0,
        ConfigService.getDefaultColorPalette().getNegativeColorAWT());
    outlierRenderer.setSeriesStroke(0, new java.awt.BasicStroke(1.9f));
    outlierRenderer.setSeriesShape(0, new Ellipse2D.Double(-5.5, -5.5, 11, 11));
    outlierRenderer.setDefaultShapesFilled(false);
    outlierRenderer.setSeriesVisibleInLegend(0, false);
    plot.setDataset(3, outlierDataset);
    plot.setRenderer(3, outlierRenderer);
  }

  private static @Nullable FeatureListRow resolveClickedRow(final @NotNull XYDataset dataset,
      final int item) {
    if (dataset instanceof XYItemObjectProvider<?> provider) {
      final Object obj = provider.getItemObject(item);
      if (obj instanceof FeatureListRow row) {
        return row;
      }
    }
    return null;
  }

  private static @NotNull String getSelectedLabel(final @NotNull FeatureListRow row) {
    final List<MatchedLipid> matches = row.get(LipidMatchListType.class);
    if (matches == null || matches.isEmpty()) {
      return "Row " + row.getID();
    }
    final String annotation = matches.getFirst().getLipidAnnotation().getAnnotation();
    return annotation.length() > 52 ? annotation.substring(0, 49) + "..." : annotation;
  }

  private void discardChart() {
    filterScheduler.cancelTasks();
    if (chart == null) {
      baseDataset = null;
      return;
    }
    final XYPlot plot = chart.getChart().getXYPlot();
    for (int i = 0; i < Math.max(5, plot.getDatasetCount()); i++) {
      plot.setDataset(i, null);
      plot.setRenderer(i, null);
    }
    setCenter(placeholder);
    chart = null;
    baseDataset = null;
  }

  private void showPlaceholder(final @NotNull String text) {
    placeholder.setText(text);
    setCenter(placeholder);
  }
}
