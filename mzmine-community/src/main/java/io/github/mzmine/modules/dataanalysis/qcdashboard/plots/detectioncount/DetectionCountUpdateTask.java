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
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYDataset;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.DatasetAndRenderer;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.RunOption;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYShapeRenderer;
import io.github.mzmine.javafx.mvci.FxUpdateTask;
import io.github.mzmine.main.ConfigService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Computes, off the FX thread, how often each row is detected across the QC files, sorted
 * descending, and builds the single scatter dataset.
 */
class DetectionCountUpdateTask extends FxUpdateTask<DetectionCountModel> {

  private final ModularFeatureList flist;
  private final List<RawDataFile> qcFiles;

  private @Nullable List<DatasetAndRenderer> result;

  DetectionCountUpdateTask(DetectionCountModel model) {
    super("qc_detectioncount", model);
    this.flist = model.getFeatureList();
    this.qcFiles = model.getQcFiles();
  }

  @Override
  public boolean checkPreConditions() {
    return flist != null && !qcFiles.isEmpty();
  }

  @Override
  protected void process() {
    if (!checkPreConditions()) {
      return;
    }
    // detection count per row across QC files
    final List<FeatureListRow> rows = new ArrayList<>(flist.getRows());
    final int[] countByRowIndex = new int[rows.size()];
    for (int i = 0; i < rows.size(); i++) {
      if (isCanceled()) {
        return;
      }
      final FeatureListRow row = rows.get(i);
      int c = 0;
      for (RawDataFile qc : qcFiles) {
        if (row.getFeature(qc) != null) {
          c++;
        }
      }
      countByRowIndex[i] = c;
    }

    // sort rows descending by detection count
    final Integer[] order = new Integer[rows.size()];
    for (int i = 0; i < order.length; i++) {
      order[i] = i;
    }
    java.util.Arrays.sort(order, Comparator.comparingInt((Integer i) -> countByRowIndex[i]).reversed());

    final int[] sortedCounts = new int[order.length];
    final List<FeatureListRow> sortedRows = new ArrayList<>(order.length);
    for (int rank = 0; rank < order.length; rank++) {
      sortedCounts[rank] = countByRowIndex[order[rank]];
      sortedRows.add(rows.get(order[rank]));
    }

    final DetectionCountProvider provider = new DetectionCountProvider(
        ConfigService.getDefaultColorPalette().getMainColor(), sortedCounts, sortedRows);
    result = List.of(new DatasetAndRenderer(new ColoredXYDataset(provider, RunOption.THIS_THREAD),
        new ColoredXYShapeRenderer()));
  }

  @Override
  protected void updateGuiModel() {
    model.setDatasets(result != null ? result : List.of());
  }

  @Override
  public String getTaskDescription() {
    return "Updating QC dashboard detection count";
  }

  @Override
  public double getFinishedPercentage() {
    return result != null ? 1d : 0d;
  }
}
