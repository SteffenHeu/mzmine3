/*
 *  Copyright 2006-2020 The MZmine Development Team
 *
 *  This file is part of MZmine.
 *
 *  MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 *  General Public License as published by the Free Software Foundation; either version 2 of the
 *  License, or (at your option) any later version.
 *
 *  MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 *  the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 *  Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with MZmine; if not,
 *  write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 *  USA
 */

package io.github.mzmine.datamodel.featuredata;

import java.nio.DoubleBuffer;

/**
 * Stores a series of intensities.
 *
 * @author https://github.com/SteffenHeu
 */
public interface IntensitySeries extends SeriesValueCount {

  /**
   *
   * @return All non-zero intensities.
   */
  DoubleBuffer getIntensityValues();

  /**
   *
   * @param dst results are reflected in this array
   * @return All non-zero intensities.
   */
  default double[] getIntensityValues(double[] dst) {
    if (dst.length < getNumberOfValues()) {
      dst = new double[getNumberOfValues()];
    }
    getIntensityValues().get(0, dst, 0, getNumberOfValues());
    return dst;
  }

  /**
   *
   * @param index
   * @return The intensity at the index position. Note that this
   */
  default double getIntensity(int index) {
    return getIntensityValues().get(index);
  }

  /**
   *
   * @return The number of non-zero intensity values in this series.
   */
  default int getNumberOfValues() {
    return getIntensityValues().capacity();
  }

}
