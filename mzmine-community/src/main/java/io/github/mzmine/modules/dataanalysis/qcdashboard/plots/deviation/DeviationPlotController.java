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

package io.github.mzmine.modules.dataanalysis.qcdashboard.plots.deviation;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.DatasetAndRenderer;
import io.github.mzmine.gui.framework.fx.SelectedRowsBinding;
import io.github.mzmine.javafx.mvci.FxController;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.modules.dataanalysis.qcdashboard.plots.QcPlotDatasets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javafx.beans.property.ObjectProperty;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;

/**
 * Controller of a single deviation plot (m/z or RT, see {@link DeviationKind}). For the selected
 * row, plots each file's deviation from the row average. Cheap (single row) so it runs on the GUI
 * thread.
 */
public class DeviationPlotController extends FxController<DeviationPlotModel> implements
    SelectedRowsBinding {

  private final DeviationPlotViewBuilder builder;

  public DeviationPlotController(DeviationKind kind) {
    super(new DeviationPlotModel(kind));
    builder = new DeviationPlotViewBuilder(model);

    model.selectedRowsProperty().addListener((_, _, _) -> updateDatasets());
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
      final DeviationKind kind = model.getKind();
      final String primaryLabel =
          kind == DeviationKind.RT ? "Uncorrected Δ RT" : kind.rangeAxisLabel();
      final List<DatasetAndRenderer> datasets = new ArrayList<>(
          QcPlotDatasets.perFile(files, model.getFileColors(), file -> kind.deviation(row, file),
              primaryLabel, kind.numberFormat()));
      if (kind == DeviationKind.RT) {
        datasets.addAll(QcPlotDatasets.perFile(files, model.getFileColors(),
            file -> kind.correctedDeviation(row, file), "Corrected Δ RT", kind.numberFormat(),
            true));
      }
      model.setDatasets(datasets);

      // decision: Mean / SD remains tied to the uncorrected RT or m/z dataset.
      final double[] stats = QcPlotDatasets.meanSd(
          files.stream().mapToDouble(file -> kind.deviation(row, file)).toArray());
      model.setMean(stats[0]);
      model.setSd(stats[1]);
    });
  }

  @Override
  protected @NotNull FxViewBuilder<DeviationPlotModel> getViewBuilder() {
    return builder;
  }

  @Override
  public ObjectProperty<List<FeatureListRow>> selectedRowsProperty() {
    return model.selectedRowsProperty();
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
