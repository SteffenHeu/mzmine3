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

import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.gui.chartbasics.simplechart.providers.PlotXYDataProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.XYItemObjectProvider;
import io.github.mzmine.javafx.util.FxColorUtil;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.awt.Color;
import java.util.List;
import javafx.beans.property.Property;
import org.jetbrains.annotations.Nullable;

/**
 * Detection-count scatter (Plot 4): x = feature rank in the descending-sorted list, y = number of
 * QC files the feature was detected in. Backed by {@link FeatureListRow}s for click selection.
 */
public class DetectionCountProvider implements PlotXYDataProvider,
    XYItemObjectProvider<FeatureListRow> {

  private final Color awtColor;
  private final int[] counts;
  private final List<FeatureListRow> sortedRows;

  /**
   * @param counts     detection count per rank (already sorted descending)
   * @param sortedRows the rows in the same order as {@code counts}
   */
  public DetectionCountProvider(javafx.scene.paint.Color fxColor, int[] counts,
      List<FeatureListRow> sortedRows) {
    this.awtColor = FxColorUtil.fxColorToAWT(fxColor);
    this.counts = counts;
    this.sortedRows = sortedRows;
  }

  @Override
  public Color getAWTColor() {
    return awtColor;
  }

  @Override
  public javafx.scene.paint.Color getFXColor() {
    return FxColorUtil.awtColorToFX(awtColor);
  }

  @Override
  public @Nullable String getLabel(int index) {
    return null;
  }

  @Override
  public @Nullable String getToolTipText(int itemIndex) {
    if (itemIndex < 0 || itemIndex >= sortedRows.size()) {
      return null;
    }
    return "Row ID " + sortedRows.get(itemIndex).getID() + "\nDetections: " + counts[itemIndex];
  }

  @Override
  public Comparable<?> getSeriesKey() {
    return "Detections";
  }

  @Override
  public double getDomainValue(int index) {
    return index;
  }

  @Override
  public double getRangeValue(int index) {
    return counts[index];
  }

  @Override
  public int getValueCount() {
    return counts.length;
  }

  @Override
  public void computeValues(Property<TaskStatus> status) {
    // precomputed
  }

  @Override
  public double getComputationFinishedPercentage() {
    return 1d;
  }

  @Override
  public boolean isComputed() {
    return true;
  }

  @Override
  public FeatureListRow getItemObject(int item) {
    return item >= 0 && item < sortedRows.size() ? sortedRows.get(item) : null;
  }
}
