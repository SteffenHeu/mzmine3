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

package io.github.mzmine.modules.dataanalysis.qcdashboard.plots.perfile;

import io.github.mzmine.gui.chartbasics.simplechart.SimpleXYChart;
import io.github.mzmine.gui.chartbasics.simplechart.providers.PlotXYDataProvider;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYShapeRenderer;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.modules.dataanalysis.qcdashboard.plots.QcPlotDatasets;
import javafx.scene.layout.Region;
import org.jfree.chart.plot.PlotOrientation;

public class PerFileAggregateViewBuilder extends FxViewBuilder<PerFileAggregateModel> {

  private final SimpleXYChart<PlotXYDataProvider> chart;

  public PerFileAggregateViewBuilder(PerFileAggregateModel model) {
    super(model);
    chart = new SimpleXYChart<>(model.getKind().title(), "File", model.getKind().rangeAxisLabel(),
        PlotOrientation.VERTICAL, true, true);
  }

  @Override
  public Region build() {
    chart.setDefaultRenderer(new ColoredXYShapeRenderer());
    chart.setRangeAxisNumberFormatOverride(model.getKind().numberFormat());
    model.datasetsProperty().addListener((_, _, datasets) -> {
      QcPlotDatasets.applyTo(chart, datasets);
      redrawOverlay();
    });
    model.showMeanSdIntervalProperty().addListener((_, _, _) -> redrawOverlay());
    model.meanProperty().addListener((_, _, _) -> redrawOverlay());
    model.sdProperty().addListener((_, _, _) -> redrawOverlay());
    return chart;
  }

  private void redrawOverlay() {
    QcPlotDatasets.drawMeanSdOverlay(chart, model.isShowMeanSdInterval(), model.getMean(),
        model.getSd());
  }
}
