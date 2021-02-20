/*
 * Copyright 2006-2020 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */

package io.github.mzmine.util.components;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.util.DataPointUtils;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.Path2D;
import java.util.List;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import org.jfree.fx.FXGraphics2D;

/**
 * Simple lightweight component for plotting peak shape
 */
public class FeatureXICComponent extends Canvas {

  public static final Color XICColor = MZmineCore.getConfiguration().getDefaultColorPalette()
      .getMainColorAWT();
  final double nodeHeight;

  private Range<Float> rtRange;
  private double maxIntensity;
  final double nodeWidth;
  private final ModularFeature feature;

  /**
   * @param feature Picked peak to plot
   */
  public FeatureXICComponent(ModularFeature feature, int width, int height) {
    this(feature, feature.getRawDataPointsIntensityRange().upperEndpoint(), width, height);
  }

  /**
   * @param feature Picked peak to plot
   */
  public FeatureXICComponent(ModularFeature feature, double maxIntensity, int width, int height) {
    super(width, height);
    nodeHeight = height;
    nodeWidth = width;

    this.feature = feature;

    // find data boundaries
    RawDataFile dataFile = feature.getRawDataFile();
    this.rtRange = dataFile.getDataRTRange();
    this.maxIntensity = maxIntensity;

    paint();
    widthProperty().addListener(e -> paint());
    heightProperty().addListener(e -> paint());
  }

  public void paint() {

    if (!isVisible()) {
      return;
    }

    // use Graphics2D for antialiasing
    GraphicsContext gc = getGraphicsContext2D();
    FXGraphics2D g2 = new FXGraphics2D(gc);

    // for each datapoint, find [X:Y] coordinates of its point in painted
    // image

    final double rtLen = rtRange.upperEndpoint() - rtRange.lowerEndpoint();
    IonTimeSeries<? extends Scan> series = feature.getFeatureData();
    if (series.getNumberOfValues() == 0) {
      return;
    }

    Path2D path = new Path2D.Float();
    g2.setColor(XICColor);
    g2.setStroke(new BasicStroke(1f));
    path.moveTo(0, (float) nodeHeight - 1);

    double[] intensities = DataPointUtils.getDoubleBufferAsArray(series.getIntensityValues());
    List<Scan> scans = (List<Scan>) series.getSpectra();

    for (int i = 0, num = series.getNumberOfValues(); i < num; i++) {
      path.lineTo(
          (float) Math.floor(
              (scans.get(i).getRetentionTime() - rtRange.lowerEndpoint()) / rtLen * (nodeWidth
                  - 1)),
          (float) nodeHeight - (float) Math
              .floor(intensities[i] / maxIntensity * ((nodeHeight - 1))));
    }
    path.lineTo((float) nodeHeight - 1, (float) nodeWidth - 1);
    path.closePath();

    // fill the peak area
    g2.draw(path);

  }

}
