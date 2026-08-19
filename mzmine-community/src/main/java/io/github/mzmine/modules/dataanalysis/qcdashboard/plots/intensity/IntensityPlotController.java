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

package io.github.mzmine.modules.dataanalysis.qcdashboard.plots.intensity;

import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.DatasetAndRenderer;
import io.github.mzmine.gui.framework.fx.SelectedAbundanceMeasureBinding;
import io.github.mzmine.gui.framework.fx.SelectedRowsBinding;
import io.github.mzmine.javafx.mvci.FxController;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataanalysis.qcdashboard.plots.QcPlotDatasets;
import io.github.mzmine.util.MathUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javafx.beans.property.ObjectProperty;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;

/**
 * Controller of the intensity-of-selected-feature plot (Plot 1). Recomputes datasets whenever the
 * selected row, abundance measure, ordered files or colors change. The computation is cheap (single
 * row) so it runs on the GUI thread.
 */
public class IntensityPlotController extends FxController<IntensityPlotModel> implements
    SelectedRowsBinding, SelectedAbundanceMeasureBinding {

  private final IntensityPlotViewBuilder builder;

  public IntensityPlotController() {
    super(new IntensityPlotModel());
    builder = new IntensityPlotViewBuilder(model);

    model.selectedRowsProperty().addListener((_, _, _) -> updateDatasets());
    model.abundanceMeasureProperty().addListener((_, _, _) -> updateDatasets());
    model.orderedFilesProperty().addListener((_, _, _) -> updateDatasets());
    model.fileColorsProperty().addListener((_, _, _) -> updateDatasets());
  }

  private void updateDatasets() {
    onGuiThread(() -> {
      final List<FeatureListRow> rows = model.getSelectedRows();
      final List<RawDataFile> files = model.getOrderedFiles();
      if (rows == null || rows.isEmpty() || files == null || files.isEmpty()) {
        model.setDatasets(List.of());
        return;
      }
      final FeatureListRow row = rows.getFirst();
      final AbundanceMeasure measure = model.getAbundanceMeasure();
      final AbundanceMeasure normalizedMeasure = measure.normalizedValue();
      final Map<RawDataFile, Color> colors = model.getFileColors();
      final var format = MZmineCore.getConfiguration().getIntensityFormat();

      final List<DatasetAndRenderer> datasets = new ArrayList<>(
          QcPlotDatasets.perFile(files, colors,
              file -> measure.getOrNaN((ModularFeature) row.getFeature(file)), measure.toString(),
              format));
      // decision: Only plot the paired normalized measure when that feature data type is present.
      if (normalizedMeasure != measure && row.getFeatureList()
          .hasFeatureType(normalizedMeasure.type())) {
        datasets.addAll(QcPlotDatasets.perFile(files, colors,
            file -> normalizedMeasure.getOrNaN((ModularFeature) row.getFeature(file)),
            normalizedMeasure.toString(), format, true));
      }
      model.setDatasets(datasets);

      // Mean / SD remains tied to the raw measure selected in the control.
      final double[] values = files.stream()
          .mapToDouble(file -> measure.getOrNaN((ModularFeature) row.getFeature(file)))
          .filter(v -> !Double.isNaN(v)).toArray();
      if (values.length == 0) {
        model.setMean(Double.NaN);
        model.setSd(Double.NaN);
      } else {
        model.setMean(MathUtils.calcAvg(values));
        model.setSd(MathUtils.calcStd(values));
      }
    });
  }

  @Override
  protected @NotNull FxViewBuilder<IntensityPlotModel> getViewBuilder() {
    return builder;
  }

  @Override
  public ObjectProperty<List<FeatureListRow>> selectedRowsProperty() {
    return model.selectedRowsProperty();
  }

  @Override
  public ObjectProperty<AbundanceMeasure> abundanceMeasureProperty() {
    return model.abundanceMeasureProperty();
  }

  public ObjectProperty<List<RawDataFile>> orderedFilesProperty() {
    return model.orderedFilesProperty();
  }

  public ObjectProperty<Map<RawDataFile, Color>> fileColorsProperty() {
    return model.fileColorsProperty();
  }

  public javafx.beans.property.BooleanProperty showMeanSdIntervalProperty() {
    return model.showMeanSdIntervalProperty();
  }
}
