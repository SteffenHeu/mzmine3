/*
 * Copyright (c) 2004-2025 The mzmine Development Team
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

package io.github.mzmine.modules.dataprocessing.masscalibration.gui;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.gui.chartbasics.simplechart.SimpleXYChart;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYDataset;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.DatasetAndRenderer;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.RunOption;
import io.github.mzmine.gui.chartbasics.simplechart.providers.PlotXYDataProvider;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYLineRenderer;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYShapeRenderer;
import io.github.mzmine.javafx.components.factories.FxLabels;
import io.github.mzmine.javafx.components.util.FxLayout;
import io.github.mzmine.modules.dataprocessing.masscalibration.MassCalibrationParameters;
import io.github.mzmine.modules.dataprocessing.masscalibration.MzCalibrationMethods;
import io.github.mzmine.modules.dataprocessing.masscalibration.api.MzCalibrationMethod;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.dialogs.previewpane.AbstractPreviewPane;
import io.github.mzmine.parameters.parametertypes.submodules.ValueWithParameters;
import io.github.mzmine.project.ProjectService;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.GridPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interactive preview for the unified mass calibration module. Builds the calibration for a
 * selected raw data file and plots the method's diagnostic data (e.g. correction-vs-RT for
 * lockmass, error-vs-m/z fit for the segment / internal-standards methods).
 */
public class MassCalibrationPreviewPane extends AbstractPreviewPane<RawDataFile> {

  private ComboBox<RawDataFile> fileBox;

  public MassCalibrationPreviewPane(ParameterSet parameters) {
    super(parameters);
    initialize();
  }

  private void initialize() {
    final ObservableList<RawDataFile> files = FXCollections.observableArrayList(
        ProjectService.getProjectManager().getCurrentProject().getCurrentRawDataFiles());
    fileBox = new ComboBox<>(files);
    if (!files.isEmpty()) {
      fileBox.getSelectionModel().selectFirst();
    }
    fileBox.valueProperty().addListener((_, _, _) -> updatePreview());

    final GridPane controls = new GridPane(FxLayout.DEFAULT_SPACE, FxLayout.DEFAULT_SPACE);
    controls.add(FxLabels.newLabel("Preview file: "), 0, 0);
    controls.add(fileBox, 1, 0);
    setBottom(controls);
  }

  @Override
  public @NotNull SimpleXYChart<PlotXYDataProvider> createChart() {
    final SimpleXYChart<PlotXYDataProvider> c = new SimpleXYChart<>("Calibration preview",
        formats.unit("m/z or retention time", ""), formats.unit("mass error", "abs."));
    c.setMinWidth(100);
    c.setMinHeight(200);
    c.setStickyZeroRangeAxis(false);
    return c;
  }

  @Override
  public void updateChart(@NotNull List<DatasetAndRenderer> datasets,
      @NotNull SimpleXYChart<? extends PlotXYDataProvider> chart) {
    chart.applyWithNotifyChanges(false, () -> {
      chart.removeAllDatasets();
      datasets.forEach(dsr -> chart.addDataset(dsr.dataset(), dsr.renderer()));
    });
  }

  @Override
  public @NotNull List<@NotNull DatasetAndRenderer> calculateNewDatasets(
      @Nullable RawDataFile file) {
    if (file == null || !parameters.checkParameterValues(new ArrayList<>(), true)) {
      return List.of();
    }

    final ValueWithParameters<MzCalibrationMethods> selected = parameters.getParameter(
        MassCalibrationParameters.calibrationMethod).getValueWithParameters();
    final MzCalibrationMethod method = selected.value().getModuleInstance()
        .newInstance(selected.parameters(), null);

    method.buildCalibration(file); // populates additional preview data

    final List<DatasetAndRenderer> data = new ArrayList<>();
    for (PlotXYDataProvider provider : method.getAdditionalPreviewData()) {
      final String key = String.valueOf(provider.getSeriesKey());
      final boolean line = key.contains("fit") || key.contains("smoothed");
      data.add(new DatasetAndRenderer(new ColoredXYDataset(provider, RunOption.THIS_THREAD),
          line ? new ColoredXYLineRenderer() : new ColoredXYShapeRenderer()));
    }
    return data;
  }

  @Override
  public RawDataFile getValueForPreview() {
    return fileBox == null ? null : fileBox.getValue();
  }
}
