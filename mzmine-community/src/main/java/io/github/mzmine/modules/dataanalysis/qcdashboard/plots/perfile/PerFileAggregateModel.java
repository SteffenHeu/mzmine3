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

import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.DatasetAndRenderer;
import java.util.List;
import java.util.Map;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.Nullable;

/**
 * Model of a per-file aggregate plot (feature count or summed intensity, see
 * {@link FileAggregateKind}).
 */
public class PerFileAggregateModel {

  private final FileAggregateKind kind;

  // bound to the main model
  private final ObjectProperty<@Nullable ModularFeatureList> featureList = new SimpleObjectProperty<>();
  private final ObjectProperty<List<RawDataFile>> orderedFiles = new SimpleObjectProperty<>(
      List.of());
  private final ObjectProperty<Map<RawDataFile, Color>> fileColors = new SimpleObjectProperty<>(
      Map.of());
  private final ObjectProperty<AbundanceMeasure> abundanceMeasure = new SimpleObjectProperty<>(
      AbundanceMeasure.Height);

  // output
  private final ObjectProperty<List<DatasetAndRenderer>> datasets = new SimpleObjectProperty<>(
      List.of());
  private final javafx.beans.property.BooleanProperty showMeanSdInterval = new javafx.beans.property.SimpleBooleanProperty(
      true);
  private final javafx.beans.property.DoubleProperty mean = new javafx.beans.property.SimpleDoubleProperty(
      Double.NaN);
  private final javafx.beans.property.DoubleProperty sd = new javafx.beans.property.SimpleDoubleProperty(
      Double.NaN);

  public PerFileAggregateModel(FileAggregateKind kind) {
    this.kind = kind;
  }

  public boolean isShowMeanSdInterval() {
    return showMeanSdInterval.get();
  }

  public javafx.beans.property.BooleanProperty showMeanSdIntervalProperty() {
    return showMeanSdInterval;
  }

  public double getMean() {
    return mean.get();
  }

  public void setMean(double mean) {
    this.mean.set(mean);
  }

  public javafx.beans.property.DoubleProperty meanProperty() {
    return mean;
  }

  public double getSd() {
    return sd.get();
  }

  public void setSd(double sd) {
    this.sd.set(sd);
  }

  public javafx.beans.property.DoubleProperty sdProperty() {
    return sd;
  }

  public FileAggregateKind getKind() {
    return kind;
  }

  public @Nullable ModularFeatureList getFeatureList() {
    return featureList.get();
  }

  public ObjectProperty<@Nullable ModularFeatureList> featureListProperty() {
    return featureList;
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

  public AbundanceMeasure getAbundanceMeasure() {
    return abundanceMeasure.get();
  }

  public ObjectProperty<AbundanceMeasure> abundanceMeasureProperty() {
    return abundanceMeasure;
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
}
