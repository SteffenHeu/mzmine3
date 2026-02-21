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

import static io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.*;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.types.annotations.LipidMatchListType;
import io.github.mzmine.gui.chartbasics.chartutils.ColoredBubbleDatasetRenderer;
import io.github.mzmine.gui.chartbasics.chartutils.paintscales.PaintScaleTransform;
import io.github.mzmine.gui.chartbasics.simplechart.providers.XYItemObjectProvider;
import io.github.mzmine.javafx.mvci.FxUpdateTask;
import io.github.mzmine.javafx.mvci.LatestTaskScheduler;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.MSMSLipidTools;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.molecular_species.MolecularSpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.species_level.SpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.ILipidAnnotation;
import io.github.mzmine.modules.visualization.dash_lipidqc.LipidAnnotationQCDashboardModel;
import io.github.mzmine.modules.visualization.dash_lipidqc.data.KendrickSubsetDataset;
import io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.ElutionOrderMetrics;
import io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.InterferenceMetrics;
import io.github.mzmine.modules.visualization.kendrickmassplot.KendrickMassPlotChart;
import io.github.mzmine.modules.visualization.kendrickmassplot.KendrickMassPlotParameters;
import io.github.mzmine.modules.visualization.kendrickmassplot.KendrickMassPlotXYZDataset;
import io.github.mzmine.modules.visualization.kendrickmassplot.KendrickPlotDataTypes;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsSelection;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.FeatureTableFXUtil;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
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
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.fx.interaction.ChartMouseEventFX;
import org.jfree.chart.fx.interaction.ChartMouseListenerFX;
import org.jfree.chart.plot.CrosshairState;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.LookupPaintScale;
import org.jfree.chart.renderer.PaintScale;
import org.jfree.chart.renderer.xy.XYItemRendererState;
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
    filterScheduler.onTaskThreadDelayed(
        new KendrickFilterComputationTask(this, requestId, Objects.requireNonNull(baseDataset),
            featureList, visibleIds, includeRetentionTimeAnalysis), Duration.millis(120));
  }

  private void applyFilterComputationResult(final @NotNull KendrickFilterComputationResult result) {
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

  private record KendrickFilterComputationResult(long requestId,
                                                 @NotNull KendrickMassPlotXYZDataset baseDataset,
                                                 @NotNull KendrickSubsetDataset inDataset,
                                                 @Nullable KendrickSubsetDataset filteredOutDataset,
                                                 @Nullable KendrickSubsetDataset outlierDataset,
                                                 @NotNull PaintScale filteredColorScale,
                                                 @NotNull LookupPaintScale grayScale) {

  }

  private static final class KendrickFilterComputationTask extends FxUpdateTask<KendrickPane> {

    private final long requestId;
    private final @NotNull KendrickMassPlotXYZDataset baseDataset;
    private final @Nullable ModularFeatureList featureList;
    private final @NotNull Set<Integer> visibleRowIds;
    private final boolean includeRetentionTimeAnalysis;
    private @NotNull KendrickFilterComputationResult result;

    private KendrickFilterComputationTask(final @NotNull KendrickPane pane, final long requestId,
        final @NotNull KendrickMassPlotXYZDataset baseDataset,
        final @Nullable ModularFeatureList featureList, final @NotNull Set<Integer> visibleRowIds,
        final boolean includeRetentionTimeAnalysis) {
      super("lipidqc-kendrick-filter-update", pane);
      this.requestId = requestId;
      this.baseDataset = baseDataset;
      this.featureList = featureList;
      this.visibleRowIds = Set.copyOf(visibleRowIds);
      this.includeRetentionTimeAnalysis = includeRetentionTimeAnalysis;
      final KendrickSubsetDataset fallback = new KendrickSubsetDataset(baseDataset, _ -> true);
      result = new KendrickFilterComputationResult(requestId, baseDataset, fallback, null, null,
          createColorPaintScale(fallback), new LookupPaintScale(0d, 1d, Color.GRAY));
    }

    @Override
    protected void process() {
      final Predicate<FeatureListRow> visiblePredicate =
          visibleRowIds.isEmpty() ? _ -> true : row -> visibleRowIds.contains(row.getID());
      final KendrickSubsetDataset inDataset = new KendrickSubsetDataset(baseDataset, visiblePredicate);
      final PaintScale filteredColorScale = createColorPaintScale(inDataset);
      final KendrickSubsetDataset filteredOutDataset =
          visibleRowIds.isEmpty() ? null
              : new KendrickSubsetDataset(baseDataset, row -> !visiblePredicate.test(row));
      final LookupPaintScale grayScale =
          filteredOutDataset == null ? new LookupPaintScale(0d, 1d, Color.GRAY)
              : createGrayPaintScale(filteredOutDataset);
      final KendrickSubsetDataset outlierDataset =
          featureList == null || inDataset.getItemCount(0) == 0 ? null
              : new KendrickSubsetDataset(baseDataset,
                  row -> visiblePredicate.test(row) && rowOutlier(featureList, row,
                      includeRetentionTimeAnalysis));
      result = new KendrickFilterComputationResult(requestId, baseDataset, inDataset,
          filteredOutDataset, outlierDataset, filteredColorScale, grayScale);
    }

    @Override
    protected void updateGuiModel() {
      model.applyFilterComputationResult(result);
    }

    @Override
    public String getTaskDescription() {
      return "Calculating Kendrick filter datasets";
    }

    @Override
    public double getFinishedPercentage() {
      return 0d;
    }
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

    colorRenderer = new AlphaBubbleRenderer(1f);
    colorRenderer.setPaintScale(colorScale);
    if (tooltipGenerator != null) {
      colorRenderer.setDefaultToolTipGenerator(tooltipGenerator);
    }

    filteredOutRenderer = new AlphaBubbleRenderer(0.35f);
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
    final AxisExtrema xExtrema = collectVisibleExtrema(plot, true);
    final AxisExtrema yExtrema = collectVisibleExtrema(plot, false);
    if (plot.getDomainAxis() instanceof NumberAxis domainAxis && xExtrema.available()) {
      applyAxisExtrema(domainAxis, xExtrema);
    }
    if (plot.getRangeAxis() instanceof NumberAxis rangeAxis && yExtrema.available()) {
      applyAxisExtrema(rangeAxis, yExtrema);
    }
  }

  private static @NotNull AxisExtrema collectVisibleExtrema(final @NotNull XYPlot plot,
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
    return new AxisExtrema(min, max, Double.isFinite(min) && Double.isFinite(max));
  }

  private static void applyAxisExtrema(final @NotNull NumberAxis axis,
      final @NotNull AxisExtrema extrema) {
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

  private record AxisExtrema(double min, double max, boolean available) {
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
    plot.setRenderer(4, new SelectedOverlayRenderer(getSelectedLabel(selectedRow)));
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

  private boolean rowVisible(final @NotNull FeatureListRow row) {
    return filterState.getBarSelectedRowIds().isEmpty() || filterState.getBarSelectedRowIds().contains(
        row.getID());
  }

  private static boolean rowOutlier(final @NotNull ModularFeatureList featureList,
      final @NotNull FeatureListRow row, final boolean includeRetentionTimeAnalysis) {
    final List<MatchedLipid> matches = row.getLipidMatches();
    if (matches.isEmpty()) {
      return false;
    }
    final MatchedLipid match = matches.getFirst();
    if (!includeRetentionTimeAnalysis) {
      final double overall = computeOverallQualityScore(row, match, 0d, false);
      return overall < 0.5d;
    }
    final ElutionOrderMetrics elutionMetrics = computeElutionOrderMetrics(featureList, row,
        match);
    final double overall = computeOverallQualityScore(row, match, elutionMetrics.combinedScore(),
        true);
    final boolean poorCarbonTrend = elutionMetrics.carbonsTrend().available()
        && elutionMetrics.carbonsTrend().score() < 0.55d;
    final boolean poorDbeTrend =
        elutionMetrics.dbeTrend().available() && elutionMetrics.dbeTrend().score() < 0.55d;
    return poorCarbonTrend || poorDbeTrend || elutionMetrics.combinedScore() < 0.55d
        || overall < 0.5d;
  }

  private static double computeOverallQualityScore(final @NotNull FeatureListRow row,
      final @NotNull MatchedLipid match, final double elutionOrderScore,
      final boolean includeRetentionTimeAnalysis) {
    final double ms1Score = computeMs1Score(row, match);
    final double ms2Score = computeMs2Score(match);
    final double adductScore = computeAdductScore(row, match);
    final double isotopeScore = computeIsotopeScore(row, match);
    final InterferenceMetrics interferenceMetrics = computeInterferenceMetrics(row);
    final double interference = computeInterferenceScore(interferenceMetrics.totalPenaltyCount());
    final double scoreSum = ms1Score + ms2Score + adductScore + isotopeScore + interference
        + (includeRetentionTimeAnalysis ? elutionOrderScore : 0d);
    final int scoreCount = includeRetentionTimeAnalysis ? 6 : 5;
    return scoreSum / scoreCount;
  }

  private static double computeMs1Score(final @NotNull FeatureListRow row,
      final @NotNull MatchedLipid match) {
    final double exactMz = MatchedLipid.getExactMass(match);
    final double observedMz =
        match.getAccurateMz() != null ? match.getAccurateMz() : row.getAverageMZ();
    final double ppm = (observedMz - exactMz) / exactMz * 1e6;
    final double absPpm = Math.abs(ppm);
    if (!Double.isFinite(absPpm)) {
      return 0d;
    }
    return clampToUnit(1d - Math.min(absPpm, 5d) / 5d);
  }

  private static double computeMs2Score(final @NotNull MatchedLipid match) {
    final double explainedIntensity = match.getMsMsScore() == null ? 0d : match.getMsMsScore();
    return clampToUnit(explainedIntensity);
  }

  private static double computeAdductScore(final @NotNull FeatureListRow row,
      final @NotNull MatchedLipid match) {
    if (row.getBestIonIdentity() == null) {
      return 0d;
    }
    final String featureAdduct = normalizeAdduct(row.getBestIonIdentity().getAdduct());
    final String lipidAdduct = normalizeAdduct(match.getIonizationType().getAdductName());
    return featureAdduct.equals(lipidAdduct) ? 1d : 0d;
  }

  private static double computeIsotopeScore(final @NotNull FeatureListRow row,
      final @NotNull MatchedLipid match) {
    if (row.getBestIsotopePattern() == null || match.getIsotopePattern() == null) {
      return 0.35d;
    }
    return io.github.mzmine.modules.tools.isotopepatternscore.IsotopePatternScoreCalculator.getSimilarityScore(
        row.getBestIsotopePattern(), match.getIsotopePattern(),
        new io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance(0.003, 10d), 0d);
  }

  private static String normalizeAdduct(final @Nullable String adduct) {
    return adduct == null ? "" : adduct.replaceAll("\\s+", "").toLowerCase();
  }

  private static double computeEcnOrderScore(final @NotNull ModularFeatureList featureList,
      final @NotNull FeatureListRow row, final @NotNull MatchedLipid match) {
    return computeElutionOrderMetrics(featureList, row, match).combinedScore();
  }

  private static int extractDbe(final @NotNull ILipidAnnotation lipidAnnotation) {
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

  private static int extractCarbons(final @NotNull ILipidAnnotation lipidAnnotation) {
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

  private static double predictRtByLinearFit(final @NotNull List<double[]> points,
      final double x) {
    double sumX = 0d;
    double sumY = 0d;
    double sumXY = 0d;
    double sumXX = 0d;
    for (final double[] p : points) {
      sumX += p[0];
      sumY += p[1];
      sumXY += p[0] * p[1];
      sumXX += p[0] * p[0];
    }
    final int n = points.size();
    final double denom = n * sumXX - sumX * sumX;
    if (Math.abs(denom) < 1e-8d) {
      return sumY / n;
    }
    final double slope = (n * sumXY - sumX * sumY) / denom;
    final double intercept = (sumY - slope * sumX) / n;
    return intercept + slope * x;
  }

  private static double estimateResidualStd(final @NotNull List<double[]> points) {
    if (points.size() < 3) {
      return 0.1d;
    }
    final double meanX = points.stream().mapToDouble(p -> p[0]).average().orElse(0d);
    final double meanY = points.stream().mapToDouble(p -> p[1]).average().orElse(0d);
    double numerator = 0d;
    double denominator = 0d;
    for (final double[] p : points) {
      numerator += (p[0] - meanX) * (p[1] - meanY);
      denominator += (p[0] - meanX) * (p[0] - meanX);
    }
    final double slope = denominator == 0d ? 0d : numerator / denominator;
    final double intercept = meanY - slope * meanX;
    double ss = 0d;
    for (final double[] p : points) {
      final double residual = p[1] - (intercept + slope * p[0]);
      ss += residual * residual;
    }
    return Math.sqrt(ss / Math.max(1d, points.size() - 2d));
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

  private static @NotNull LookupPaintScale createGrayPaintScale(
      final @NotNull KendrickSubsetDataset dataset) {
    final int count = dataset.getItemCount(0);
    if (count == 0) {
      final LookupPaintScale fallback = new LookupPaintScale(0d, 1d, new Color(160, 160, 160));
      fallback.add(0d, new Color(215, 215, 215));
      fallback.add(1d, new Color(105, 105, 105));
      return fallback;
    }

    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < count; i++) {
      final double z = dataset.getZValue(0, i);
      if (Double.isFinite(z)) {
        min = Math.min(min, z);
        max = Math.max(max, z);
      }
    }
    if (!Double.isFinite(min) || !Double.isFinite(max) || min == max) {
      min = 0d;
      max = 1d;
    }
    final LookupPaintScale grayScale = new LookupPaintScale(min, max, new Color(160, 160, 160));
    final int steps = 8;
    for (int i = 0; i < steps; i++) {
      final double value = min + (max - min) * i / (steps - 1d);
      final int shade = 215 - (int) Math.round(120d * i / (steps - 1d));
      grayScale.add(value, new Color(shade, shade, shade, 95));
    }
    return grayScale;
  }

  private static @NotNull PaintScale createColorPaintScale(
      final @NotNull KendrickSubsetDataset dataset) {
    final int count = dataset.getItemCount(0);
    if (count == 0) {
      return new LookupPaintScale(0d, 1d, Color.GRAY);
    }
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < count; i++) {
      final double z = dataset.getZValue(0, i);
      if (Double.isFinite(z)) {
        min = Math.min(min, z);
        max = Math.max(max, z);
      }
    }
    if (!Double.isFinite(min) || !Double.isFinite(max) || min == max) {
      min = 0d;
      max = 1d;
    }
    return MZmineCore.getConfiguration().getDefaultPaintScalePalette()
        .toPaintScale(PaintScaleTransform.LINEAR, Range.closed(min, max));
  }

  private static @NotNull Paint selectedLabelTextPaint() {
    return MZmineCore.getConfiguration().isDarkMode() ? Color.WHITE : Color.BLACK;
  }

  private static @NotNull Paint selectedLabelBackgroundPaint() {
    return MZmineCore.getConfiguration().isDarkMode() ? new Color(0, 0, 0, 160)
        : new Color(255, 255, 255, 175);
  }

  private static @NotNull String getSelectedLabel(final @NotNull FeatureListRow row) {
    final List<MatchedLipid> matches = row.get(LipidMatchListType.class);
    if (matches == null || matches.isEmpty()) {
      return "Row " + row.getID();
    }
    final String annotation = matches.getFirst().getLipidAnnotation().getAnnotation();
    return annotation.length() > 52 ? annotation.substring(0, 49) + "..." : annotation;
  }

  private static final class AlphaBubbleRenderer extends ColoredBubbleDatasetRenderer {

    private final float alpha;

    private AlphaBubbleRenderer(final float alpha) {
      this.alpha = Math.max(0f, Math.min(1f, alpha));
    }

    @Override
    public void drawItem(final Graphics2D g2, final XYItemRendererState state,
        final Rectangle2D dataArea, final PlotRenderingInfo info, final XYPlot plot,
        final ValueAxis domainAxis, final ValueAxis rangeAxis, final XYDataset dataset,
        final int series, final int item, final CrosshairState crosshairState, final int pass) {
      final var oldComposite = g2.getComposite();
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
      try {
        super.drawItem(g2, state, dataArea, info, plot, domainAxis, rangeAxis, dataset, series,
            item, crosshairState, pass);
      } finally {
        g2.setComposite(oldComposite);
      }
    }
  }

  private static final class SelectedOverlayRenderer extends XYLineAndShapeRenderer {

    private final @NotNull String label;

    private SelectedOverlayRenderer(final @NotNull String label) {
      super(false, false);
      this.label = label;
    }

    @Override
    public void drawItem(final Graphics2D g2, final XYItemRendererState state,
        final Rectangle2D dataArea, final PlotRenderingInfo info, final XYPlot plot,
        final ValueAxis domainAxis, final ValueAxis rangeAxis, final XYDataset dataset,
        final int series, final int item, final CrosshairState crosshairState, final int pass) {
      final double x = dataset.getXValue(series, item);
      final double y = dataset.getYValue(series, item);
      if (!Double.isFinite(x) || !Double.isFinite(y)) {
        return;
      }

      final double tx = domainAxis.valueToJava2D(x, dataArea, plot.getDomainAxisEdge());
      final double ty = rangeAxis.valueToJava2D(y, dataArea, plot.getRangeAxisEdge());
      final double radius = 9d;
      g2.setPaint(ConfigService.getDefaultColorPalette().getPositiveColorAWT());
      g2.setStroke(new java.awt.BasicStroke(2.2f));
      g2.draw(new Ellipse2D.Double(tx - radius, ty - radius, radius * 2d, radius * 2d));

      final Font labelFont = new Font("SansSerif", Font.BOLD, 11);
      g2.setFont(labelFont);
      final var fm = g2.getFontMetrics(labelFont);
      final int textW = fm.stringWidth(label);
      final int textH = fm.getHeight();
      final double xPad = 6d;
      final double yPad = 4d;
      double lx = tx + 10d;
      double ly = ty - 10d;
      if (lx + textW + 2 * xPad > dataArea.getMaxX()) {
        lx = tx - (textW + 2 * xPad + 10d);
      }
      if (lx < dataArea.getMinX()) {
        lx = dataArea.getMinX() + 2d;
      }
      if (ly - textH < dataArea.getMinY()) {
        ly = ty + textH + 6d;
      }
      if (ly > dataArea.getMaxY()) {
        ly = dataArea.getMaxY() - 2d;
      }

      final Rectangle2D.Double bg = new Rectangle2D.Double(lx, ly - textH, textW + 2 * xPad,
          textH + 2 * yPad - 2d);
      g2.setPaint(selectedLabelBackgroundPaint());
      g2.fill(bg);
      g2.setPaint(ConfigService.getDefaultColorPalette().getPositiveColorAWT());
      g2.setStroke(new java.awt.BasicStroke(1.2f));
      g2.draw(bg);
      g2.setPaint(selectedLabelTextPaint());
      g2.drawString(label, (float) (lx + xPad), (float) (ly + yPad - 2d));
    }
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
