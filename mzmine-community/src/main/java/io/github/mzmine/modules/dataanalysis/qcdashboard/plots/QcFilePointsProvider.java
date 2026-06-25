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

package io.github.mzmine.modules.dataanalysis.qcdashboard.plots;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.gui.chartbasics.simplechart.providers.PlotXYDataProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.XYItemObjectProvider;
import io.github.mzmine.javafx.util.FxColorUtil;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.awt.Color;
import java.util.List;
import javafx.beans.property.Property;
import org.jetbrains.annotations.Nullable;

/**
 * A precomputed scatter series for the QC dashboard: a set of (x, y) points belonging to one color
 * group, each backed by a {@link RawDataFile}. Values are computed by the caller and passed in, so
 * {@link #computeValues(Property)} is a no-op.
 */
public class QcFilePointsProvider implements PlotXYDataProvider,
    XYItemObjectProvider<RawDataFile> {

  private final String seriesKey;
  private final Color awtColor;
  private final double[] xValues;
  private final double[] yValues;
  private final List<RawDataFile> files;
  private final String[] tooltips;

  public QcFilePointsProvider(String seriesKey, javafx.scene.paint.Color fxColor, double[] xValues,
      double[] yValues, List<RawDataFile> files, String[] tooltips) {
    this.seriesKey = seriesKey;
    this.awtColor = FxColorUtil.fxColorToAWT(fxColor);
    this.xValues = xValues;
    this.yValues = yValues;
    this.files = files;
    this.tooltips = tooltips;
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
    return itemIndex >= 0 && itemIndex < tooltips.length ? tooltips[itemIndex] : null;
  }

  @Override
  public Comparable<?> getSeriesKey() {
    return seriesKey;
  }

  @Override
  public double getDomainValue(int index) {
    return xValues[index];
  }

  @Override
  public double getRangeValue(int index) {
    return yValues[index];
  }

  @Override
  public int getValueCount() {
    return xValues.length;
  }

  @Override
  public void computeValues(Property<TaskStatus> status) {
    // values are precomputed and passed in
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
  public RawDataFile getItemObject(int item) {
    return item >= 0 && item < files.size() ? files.get(item) : null;
  }
}
