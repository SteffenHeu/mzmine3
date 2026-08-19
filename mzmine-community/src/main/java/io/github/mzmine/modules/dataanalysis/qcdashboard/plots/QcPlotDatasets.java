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

package io.github.mzmine.modules.dataanalysis.qcdashboard.plots;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.gui.chartbasics.chartthemes.EStandardChartTheme;
import io.github.mzmine.gui.chartbasics.simplechart.SimpleXYChart;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYDataset;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.DatasetAndRenderer;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.RunOption;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYShapeRenderer;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.util.MathUtils;
import java.awt.Font;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.ui.Layer;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.TextAnchor;

/**
 * Builds per-file scatter datasets for the QC dashboard. Each file becomes its own
 * {@link ColoredXYDataset} (single point) so its color comes from the dashboard {@code fileColors}
 * map and its legend entry / tooltip is the raw file name. This honors both per-file colors (no
 * batch) and batch shading. Rendered with a {@link ColoredXYShapeRenderer}.
 * <p>
 * The x value is the index of the file in {@code orderedFiles}; files whose value is NaN are
 * skipped.
 */
public final class QcPlotDatasets {

  /**
   * Show the chart legend only up to this many legend-contributing datasets; beyond it the legend
   * is just noise. Paired normalized/corrected datasets do not contribute legend entries.
   */
  public static final int MAX_LEGEND_DATASETS = 10;

  private QcPlotDatasets() {
  }

  /**
   * Replaces the chart's datasets and toggles the legend based on the number of datasets whose
   * renderer contributes a legend entry and {@link #MAX_LEGEND_DATASETS}. Must be called on the FX
   * thread.
   */
  public static void applyTo(@NotNull SimpleXYChart<?> chart,
      @org.jetbrains.annotations.Nullable List<DatasetAndRenderer> datasets) {
    chart.applyWithNotifyChanges(false, () -> {
      chart.removeAllDatasets();
      final long legendDatasetCount = datasets == null ? 0
          : datasets.stream().filter(dataset -> dataset.renderer().isSeriesVisibleInLegend(0))
            .count();
      if (datasets != null) {
        datasets.forEach(dr -> chart.addDataset(dr.dataset(), dr.renderer()));
      }
      chart.setLegendItemsVisible(legendDatasetCount <= MAX_LEGEND_DATASETS);
    });
  }

  /**
   * @param orderedFiles files in x-axis order (index = x value)
   * @param fileColors   file -> color mapping
   * @param valueFn      maps a file to its y value; return {@link Double#NaN} to skip the point
   * @param yLabel       label of the y value used in the tooltip
   * @param valueFormat  number format for the y value in the tooltip
   */
  public static @NotNull List<DatasetAndRenderer> perFile(@NotNull List<RawDataFile> orderedFiles,
      @NotNull Map<RawDataFile, Color> fileColors, @NotNull ToDoubleFunction<RawDataFile> valueFn,
      @NotNull String yLabel, @NotNull NumberFormat valueFormat) {
    return perFile(orderedFiles, fileColors, valueFn, yLabel, valueFormat, false);
  }

  /**
   * Builds per-file points with either filled or outlined markers. Outlined markers distinguish a
   * paired normalized abundance measure from its raw counterpart while retaining each file's
   * color.
   */
  public static @NotNull List<DatasetAndRenderer> perFile(
      final @NotNull List<RawDataFile> orderedFiles,
      final @NotNull Map<RawDataFile, Color> fileColors,
      final @NotNull ToDoubleFunction<RawDataFile> valueFn, final @NotNull String yLabel,
      final @NotNull NumberFormat valueFormat, final boolean drawOutlinesOnly) {

    final List<DatasetAndRenderer> datasets = new ArrayList<>(orderedFiles.size());
    for (int i = 0; i < orderedFiles.size(); i++) {
      final RawDataFile file = orderedFiles.get(i);
      final double value = valueFn.applyAsDouble(file);
      if (Double.isNaN(value)) {
        continue;
      }
      final Color color = fileColors.getOrDefault(file, file.getColor());
      final String tooltip = file.getName() + "\n" + yLabel + ": " + valueFormat.format(value);
      final String seriesKey =
          drawOutlinesOnly ? file.getName() + " (" + yLabel + ")" : file.getName();
      final QcFilePointsProvider provider = new QcFilePointsProvider(seriesKey, color,
          new double[]{i}, new double[]{value}, List.of(file), new String[]{tooltip});
      final ColoredXYShapeRenderer renderer = new ColoredXYShapeRenderer(drawOutlinesOnly);
      // decision: Paired normalized/corrected datasets must not duplicate the per-file legend.
      renderer.setSeriesVisibleInLegend(0, !drawOutlinesOnly);
      datasets.add(new DatasetAndRenderer(new ColoredXYDataset(provider, RunOption.THIS_THREAD),
          renderer));
    }
    return datasets;
  }

  /**
   * @return {mean, standard deviation} of the finite values, or {NaN, NaN} if there are none.
   */
  public static double[] meanSd(double[] values) {
    final double[] finite = java.util.Arrays.stream(values).filter(v -> !Double.isNaN(v)).toArray();
    if (finite.length == 0) {
      return new double[]{Double.NaN, Double.NaN};
    }
    return new double[]{MathUtils.calcAvg(finite), MathUtils.calcStd(finite)};
  }

  /**
   * Draws mean and mean ± SD markers with an RSD label.
   */
  public static void drawMeanRsdOverlay(final @NotNull SimpleXYChart<?> chart, final boolean show,
      final double mean, final double sd) {
    final double rsdPercent = mean != 0 ? sd / mean * 100 : 0;
    drawMeanSdOverlay(chart, show, mean, sd, "Mean (RSD %.1f%%)".formatted(rsdPercent));
  }

  /**
   * Draws mean and mean ± SD markers with an absolute SD label.
   */
  public static void drawMeanSdOverlay(final @NotNull SimpleXYChart<?> chart, final boolean show,
      final double mean, final double sd, final @NotNull NumberFormat valueFormat) {
    drawMeanSdOverlay(chart, show, mean, sd, "Mean (SD %s)".formatted(valueFormat.format(sd)));
  }

  /**
   * Draws horizontal mean and mean ± SD range markers on a per-file plot. Labels are right-anchored
   * so they do not collide with the y-axis. Existing range markers are cleared first.
   */
  private static void drawMeanSdOverlay(final @NotNull SimpleXYChart<?> chart, final boolean show,
      final double mean, final double sd, final @NotNull String label) {
    chart.getXYPlot().clearRangeMarkers();
    if (!show || Double.isNaN(mean)) {
      return;
    }
    final java.awt.Color color = ConfigService.getConfiguration().getDefaultColorPalette()
        .getNeutralColorAWT();
    final ValueMarker meanMarker = new ValueMarker(mean, color,
        EStandardChartTheme.DEFAULT_MARKER_STROKE);
    meanMarker.setLabel(label);
    meanMarker.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
    meanMarker.setLabelTextAnchor(TextAnchor.TOP_RIGHT);
    meanMarker.setLabelFont(new Font("Arial", Font.PLAIN, 14));
    chart.getXYPlot().addRangeMarker(0, meanMarker, Layer.FOREGROUND);

    if (!Double.isNaN(sd) && sd > 0) {
      chart.getXYPlot().addRangeMarker(0,
          new ValueMarker(mean + sd, color, EStandardChartTheme.DEFAULT_MARKER_STROKE),
          Layer.FOREGROUND);
      chart.getXYPlot().addRangeMarker(0,
          new ValueMarker(mean - sd, color, EStandardChartTheme.DEFAULT_MARKER_STROKE),
          Layer.FOREGROUND);
    }
  }
}
