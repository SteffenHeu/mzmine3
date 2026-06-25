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

package io.github.mzmine.modules.dataanalysis.qcdashboard.plots.detectioncount;

import io.github.mzmine.gui.chartbasics.chartthemes.EStandardChartTheme;
import io.github.mzmine.gui.chartbasics.simplechart.SimpleXYChart;
import io.github.mzmine.gui.chartbasics.simplechart.providers.PlotXYDataProvider;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYShapeRenderer;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataanalysis.qcdashboard.plots.QcPlotDatasets;
import java.awt.Color;
import java.awt.Stroke;
import java.text.NumberFormat;
import javafx.scene.layout.Region;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.ui.Layer;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.chart.ui.TextAnchor;

public class DetectionCountViewBuilder extends FxViewBuilder<DetectionCountModel> {

  private final Stroke markerStroke = EStandardChartTheme.DEFAULT_MARKER_STROKE;
  private final SimpleXYChart<PlotXYDataProvider> chart = new SimpleXYChart<>(
      "Detection count (QC)", "Feature #", "Detections", PlotOrientation.VERTICAL, false, true);

  public DetectionCountViewBuilder(DetectionCountModel model) {
    super(model);
  }

  @Override
  public Region build() {
    chart.setDefaultRenderer(new ColoredXYShapeRenderer());
    chart.setRangeAxisNumberFormatOverride(NumberFormat.getIntegerInstance());

    model.datasetsProperty().addListener((_, _, datasets) -> {
      QcPlotDatasets.applyTo(chart, datasets);
      redrawMarkers();
    });
    // markers depend on the feature count, QC file count and the threshold fractions
    model.featureListProperty().addListener((_, _, _) -> redrawMarkers());
    model.qcFilesProperty().addListener((_, _, _) -> redrawMarkers());
    model.goodQualityFractionProperty().addListener((_, _, _) -> redrawMarkers());
    model.warwickFractionProperty().addListener((_, _, _) -> redrawMarkers());
    return chart;
  }

  /**
   * Two thresholds: the "good quality" line is a vertical (domain) marker at
   * {@code goodFraction × #features} (i.e. are at least that fraction of features detected in all
   * QCs); the Warwick line is a horizontal (range) marker at {@code warwickFraction × #QC} (keep
   * features detected in &gt; that fraction of QCs).
   */
  private void redrawMarkers() {
    chart.getXYPlot().clearDomainMarkers();
    chart.getXYPlot().clearRangeMarkers();
    final Color color = MZmineCore.getConfiguration().getDefaultColorPalette().getNeutralColorAWT();

    final ModularFeatureList flist = model.getFeatureList();
    final int totalFeatures = flist == null ? 0 : flist.getNumberOfRows();
    if (totalFeatures > 0) {
      final ValueMarker good = new ValueMarker(model.getGoodQualityFraction() * totalFeatures,
          color, markerStroke);
      good.setLabel("Good: " + percent(model.getGoodQualityFraction()));
      // anchor the label to the top-left of the line with an offset, away from the top axis
      good.setLabelAnchor(RectangleAnchor.TOP_LEFT);
      good.setLabelTextAnchor(TextAnchor.TOP_LEFT);
      good.setLabelOffset(new RectangleInsets(6, 6, 0, 0));
      chart.getXYPlot().addDomainMarker(0, good, Layer.FOREGROUND);
    }

    final int qcCount = model.getQcFiles().size();
    if (qcCount > 0) {
      final ValueMarker warwick = new ValueMarker(model.getWarwickFraction() * qcCount, color,
          markerStroke);
      warwick.setLabel("Warwick: " + percent(model.getWarwickFraction()));
      // right-anchor so the label sits at the right edge, not behind the y-axis
      warwick.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
      warwick.setLabelTextAnchor(TextAnchor.TOP_RIGHT);
      warwick.setLabelOffset(new RectangleInsets(2, 0, 0, 8));
      chart.getXYPlot().addRangeMarker(0, warwick, Layer.FOREGROUND);
    }
  }

  private static String percent(double fraction) {
    return Math.round(fraction * 100) + "%";
  }
}
