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

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.DatasetAndRenderer;
import java.util.List;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Model of the detection-count plot (Plot 4): how often each feature is detected across the QC
 * files, sorted descending. {@code qcFileCount} and the threshold fractions drive the value
 * markers.
 */
public class DetectionCountModel {

  // bound to the main model
  private final ObjectProperty<@Nullable ModularFeatureList> featureList = new SimpleObjectProperty<>();
  private final ObjectProperty<List<RawDataFile>> qcFiles = new SimpleObjectProperty<>(List.of());
  private final DoubleProperty goodQualityFraction = new SimpleDoubleProperty(0.5);
  private final DoubleProperty warwickFraction = new SimpleDoubleProperty(0.7);

  // output
  private final ObjectProperty<List<DatasetAndRenderer>> datasets = new SimpleObjectProperty<>(
      List.of());

  public @Nullable ModularFeatureList getFeatureList() {
    return featureList.get();
  }

  public ObjectProperty<@Nullable ModularFeatureList> featureListProperty() {
    return featureList;
  }

  public List<RawDataFile> getQcFiles() {
    return qcFiles.get();
  }

  public ObjectProperty<List<RawDataFile>> qcFilesProperty() {
    return qcFiles;
  }

  public double getGoodQualityFraction() {
    return goodQualityFraction.get();
  }

  public DoubleProperty goodQualityFractionProperty() {
    return goodQualityFraction;
  }

  public double getWarwickFraction() {
    return warwickFraction.get();
  }

  public DoubleProperty warwickFractionProperty() {
    return warwickFraction;
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
