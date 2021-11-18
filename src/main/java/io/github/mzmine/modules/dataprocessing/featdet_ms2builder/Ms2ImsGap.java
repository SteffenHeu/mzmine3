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

package io.github.mzmine.modules.dataprocessing.featdet_ms2builder;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.data_access.BinningMobilogramDataAccess;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.msms.PasefMsMsInfo;
import io.github.mzmine.modules.dataprocessing.gapfill_peakfinder.multithreaded.ImsGap;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.mobilitytolerance.MobilityTolerance;
import io.github.mzmine.util.RangeUtils;

public class Ms2ImsGap extends ImsGap {

  /**
   * Constructor: Initializes an empty gap
   *
   * @param rawDataFile
   * @param mzRange           M/Z coordinate of this empty gap
   * @param rtRange           RT coordinate of this empty gap
   * @param mobilityRange
   * @param mobilogramBinning
   */
  public Ms2ImsGap(FeatureListRow row, RawDataFile rawDataFile, Range<Double> mzRange,
      Range<Float> rtRange, Range<Float> mobilityRange,
      BinningMobilogramDataAccess mobilogramBinning) {
    super(row, rawDataFile, mzRange, rtRange, mobilityRange, Double.POSITIVE_INFINITY,
        mobilogramBinning);
  }

  public boolean contains(PasefMsMsInfo info, RTTolerance rtTol, MZTolerance mzTol,
      MobilityTolerance mobTol) {
    var parentFrame = info.getParentFrame();

    if (parentFrame == null) {
      return false;
    }

    var infoMobilityRange = Range.closed(
        parentFrame.getMobilityForMobilityScanNumber(info.getSpectrumNumberRange().lowerEndpoint()),
        parentFrame.getMobilityForMobilityScanNumber(
            info.getSpectrumNumberRange().upperEndpoint()));

    return mzTol.checkWithinTolerance(info.getIsolationMz(), RangeUtils.rangeCenter(mzRange))
        && rtTol.checkWithinTolerance(RangeUtils.rangeCenter(rtRange),
        info.getParentFrame().getRetentionTime()) && mobTol.checkWithinTolerance(
        RangeUtils.rangeCenter(getMobilityRange()),
        RangeUtils.rangeCenter(infoMobilityRange).floatValue());
  }
}
