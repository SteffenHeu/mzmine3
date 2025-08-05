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

package io.github.mzmine.modules.dataprocessing.filter_rowsfilter;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.types.numbers.scores.CvType;
import io.github.mzmine.datamodel.statistics.FeaturesDataTable;
import io.github.mzmine.parameters.parametertypes.metadata.MetadataGroupSelection;
import io.github.mzmine.util.MathUtils;
import java.util.List;
import java.util.Objects;

/**
 *
 */
public final class RsdFilter {

  private final MetadataGroupSelection group;
  private final FeaturesDataTable dataTable;
  private final double maxMissingValues;
  private final double maxCvPercent;
  private final boolean keepUndetected;

  /**
   * @param group
   * @param dataTable        data table that only contains the data files that are grouped and
   *                         selected
   * @param maxMissingValues
   * @param maxCvPercent
   * @param keepUndetected
   */
  RsdFilter(MetadataGroupSelection group, FeaturesDataTable dataTable, double maxMissingValues,
      double maxCvPercent, boolean keepUndetected) {
    this.group = group;
    this.dataTable = dataTable;
    this.maxMissingValues = maxMissingValues;
    this.maxCvPercent = maxCvPercent;
    this.keepUndetected = keepUndetected;
  }

  /**
   * @return True if the row passes the filter and is thereby below the set {@link #maxCvPercent}.
   */
  public boolean matches(FeatureListRow row, final int rowIndex) {
    final double[] abundances = dataTable.getFeatureData(rowIndex, false);

    // these are the missing values from the original data
    final long missing = dataTable.getFeatureRow(rowIndex).countOriginalMissingValues();

    if (missing == abundances.length && !keepUndetected) {
      // feature not detected in QCs, will filter it out
      return false;
    }

    if (missing > maxMissingValues * abundances.length) {
      return false;
    }

    final double avg = MathUtils.calcAvg(abundances);
    final double sd = MathUtils.calcStd(abundances);
    // RSD=0 for avg 0
    final double rsd = Double.compare(avg, 0d) == 0 ? 0 : sd / avg;

    row.set(CvType.class, (float) rsd);

    return rsd <= maxCvPercent;
  }

  public List<RawDataFile> getGroupDataFiles() {
    return dataTable.getRawDataFiles();
  }

  public MetadataGroupSelection group() {
    return group;
  }

  public FeaturesDataTable dataTable() {
    return dataTable;
  }

  public double maxMissingValues() {
    return maxMissingValues;
  }

  public double maxCvPercent() {
    return maxCvPercent;
  }

  public boolean keepUndetected() {
    return keepUndetected;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    var that = (RsdFilter) obj;
    return Objects.equals(this.group, that.group) && Objects.equals(this.dataTable, that.dataTable)
        && Double.doubleToLongBits(this.maxMissingValues) == Double.doubleToLongBits(that.maxMissingValues)
        && Double.doubleToLongBits(this.maxCvPercent) == Double.doubleToLongBits(that.maxCvPercent)
        && this.keepUndetected == that.keepUndetected;
  }

  @Override
  public int hashCode() {
    return Objects.hash(group, dataTable, maxMissingValues, maxCvPercent, keepUndetected);
  }

  @Override
  public String toString() {
    return "RsdFilter[" + "group=" + group + ", " + "dataTable=" + dataTable + ", "
        + "maxMissingValues=" + maxMissingValues + ", " + "maxCvPercent=" + maxCvPercent + ", "
        + "keepUndetected=" + keepUndetected + ']';
  }


}
