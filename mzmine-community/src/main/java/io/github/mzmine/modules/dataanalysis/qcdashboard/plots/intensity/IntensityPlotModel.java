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
import io.github.mzmine.gui.chartbasics.simplechart.datasets.DatasetAndRenderer;
import java.util.List;
import java.util.Map;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.paint.Color;

/**
 * Model of the intensity-of-selected-feature plot (QC dashboard Plot 1). Bound inputs mirror the
 * main dashboard model; {@code datasets} is the computed output and {@code showRsdInterval} is the
 * plot-exclusive SD/RSD overlay toggle.
 */
public class IntensityPlotModel {

  // bound to the main model
  private final ObjectProperty<List<FeatureListRow>> selectedRows = new SimpleObjectProperty<>(
      List.of());
  private final ObjectProperty<AbundanceMeasure> abundanceMeasure = new SimpleObjectProperty<>(
      AbundanceMeasure.Height);
  private final ObjectProperty<List<RawDataFile>> orderedFiles = new SimpleObjectProperty<>(
      List.of());
  private final ObjectProperty<Map<RawDataFile, Color>> fileColors = new SimpleObjectProperty<>(
      Map.of());

  // outputs / plot-exclusive
  private final ObjectProperty<List<DatasetAndRenderer>> datasets = new SimpleObjectProperty<>(
      List.of());
  private final BooleanProperty showRsdInterval = new SimpleBooleanProperty(true);
  // mean / standard deviation of the displayed intensities (NaN when not available)
  private final DoubleProperty mean = new SimpleDoubleProperty(Double.NaN);
  private final DoubleProperty sd = new SimpleDoubleProperty(Double.NaN);

  public List<FeatureListRow> getSelectedRows() {
    return selectedRows.get();
  }

  public ObjectProperty<List<FeatureListRow>> selectedRowsProperty() {
    return selectedRows;
  }

  public AbundanceMeasure getAbundanceMeasure() {
    return abundanceMeasure.get();
  }

  public ObjectProperty<AbundanceMeasure> abundanceMeasureProperty() {
    return abundanceMeasure;
  }

  public List<RawDataFile> getOrderedFiles() {
    return orderedFiles.get();
  }

  public ObjectProperty<List<RawDataFile>> orderedFilesProperty() {
    return orderedFiles;
  }

  public Map<RawDataFile, Color> getFileColors() {
    return fileColors.get();
  }

  public ObjectProperty<Map<RawDataFile, Color>> fileColorsProperty() {
    return fileColors;
  }

  public List<DatasetAndRenderer> getDatasets() {
    return datasets.get();
  }

  public void setDatasets(List<DatasetAndRenderer> datasets) {
    this.datasets.set(datasets);
  }

  public ObjectProperty<List<DatasetAndRenderer>> datasetsProperty() {
    return datasets;
  }

  public boolean isShowRsdInterval() {
    return showRsdInterval.get();
  }

  public BooleanProperty showRsdIntervalProperty() {
    return showRsdInterval;
  }

  public double getMean() {
    return mean.get();
  }

  public void setMean(double mean) {
    this.mean.set(mean);
  }

  public DoubleProperty meanProperty() {
    return mean;
  }

  public double getSd() {
    return sd.get();
  }

  public void setSd(double sd) {
    this.sd.set(sd);
  }

  public DoubleProperty sdProperty() {
    return sd;
  }
}
