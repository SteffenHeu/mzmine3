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
import io.github.mzmine.javafx.mvci.FxUpdateTask;
import io.github.mzmine.modules.dataanalysis.qcdashboard.plots.QcPlotDatasets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.Nullable;

/**
 * Computes the per-file aggregate datasets off the FX thread (iterating rows x files) and updates
 * the model on the FX thread if still the latest task.
 */
class PerFileAggregateUpdateTask extends FxUpdateTask<PerFileAggregateModel> {

  private final ModularFeatureList flist;
  private final List<RawDataFile> orderedFiles;
  private final Map<RawDataFile, Color> fileColors;
  private final AbundanceMeasure abundance;
  private final FileAggregateKind kind;

  private @Nullable List<DatasetAndRenderer> result;
  private double mean = Double.NaN;
  private double sd = Double.NaN;

  PerFileAggregateUpdateTask(PerFileAggregateModel model) {
    super("qc_perfile_" + model.getKind(), model);
    this.flist = model.getFeatureList();
    this.orderedFiles = model.getOrderedFiles();
    this.fileColors = model.getFileColors();
    this.abundance = model.getAbundanceMeasure();
    this.kind = model.getKind();
  }

  @Override
  public boolean checkPreConditions() {
    return flist != null && !orderedFiles.isEmpty();
  }

  @Override
  protected void process() {
    if (!checkPreConditions()) {
      return;
    }
    final Map<RawDataFile, Double> perFile = kind.computePerFile(flist, orderedFiles, abundance);
    final String valueLabel =
        kind == FileAggregateKind.SUM_INTENSITY ? abundance.toString() : kind.rangeAxisLabel();
    result = new ArrayList<>(QcPlotDatasets.perFile(orderedFiles, fileColors,
        file -> perFile.getOrDefault(file, Double.NaN), valueLabel, kind.numberFormat()));

    final AbundanceMeasure normalizedAbundance = abundance.normalizedValue();
    // decision: Only intensity sums have a paired normalized dataset.
    if (kind == FileAggregateKind.SUM_INTENSITY && normalizedAbundance != abundance
        && flist.hasFeatureType(normalizedAbundance.type())) {
      final Map<RawDataFile, Double> normalizedPerFile = kind.computePerFile(flist, orderedFiles,
          normalizedAbundance);
      result.addAll(QcPlotDatasets.perFile(orderedFiles, fileColors,
          file -> normalizedPerFile.getOrDefault(file, Double.NaN), normalizedAbundance.toString(),
          kind.numberFormat(), true));
    }

    final double[] stats = QcPlotDatasets.meanSd(
        perFile.values().stream().mapToDouble(Double::doubleValue).toArray());
    mean = stats[0];
    sd = stats[1];
  }

  @Override
  protected void updateGuiModel() {
    model.setDatasets(result != null ? result : List.of());
    model.setMean(mean);
    model.setSd(sd);
  }

  @Override
  public String getTaskDescription() {
    return "Updating QC dashboard " + kind.title();
  }

  @Override
  public double getFinishedPercentage() {
    return result != null ? 1d : 0d;
  }
}
