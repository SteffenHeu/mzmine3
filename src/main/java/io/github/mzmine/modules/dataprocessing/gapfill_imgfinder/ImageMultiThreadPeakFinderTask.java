/*
 * Copyright 2006-2021 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package io.github.mzmine.modules.dataprocessing.gapfill_imgfinder;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.IMSImagingRawDataFile;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.data_access.BinningMobilogramDataAccess;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.MobilityScanDataType;
import io.github.mzmine.datamodel.data_access.MobilityScanDataAccess;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.types.numbers.MobilityType;
import io.github.mzmine.modules.dataprocessing.gapfill_peakfinder.Gap;
import io.github.mzmine.modules.dataprocessing.gapfill_peakfinder.multithreaded.MultiThreadPeakFinderParameters;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskPriority;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;

class ImageMultiThreadPeakFinderTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(
      ImageMultiThreadPeakFinderTask.class.getName());

  private final ModularFeatureList processedPeakList;
  private final double intTolerance;
  private final MZTolerance mzTolerance;
  private final RTTolerance rtTolerance;
  private final AtomicInteger processedScans = new AtomicInteger(0);
  private final IMSImagingRawDataFile imagingFile;
  // start and end (exclusive) for raw data file processing
  private final int taskIndex;
  private final int minDataPoints;
  private final List<FeatureListRow> rows;
  private final AtomicInteger processedRows;
  private int totalScans;

  private final int numRows;

  ImageMultiThreadPeakFinderTask(ModularFeatureList processedPeakList, ParameterSet parameters,
      List<FeatureListRow> rows, IMSImagingRawDataFile imagingFile, int taskIndex,
      AtomicInteger processedRows, @NotNull Instant moduleCallDate) {
    super(null, moduleCallDate);
    this.imagingFile = imagingFile;

    this.taskIndex = taskIndex;
    this.processedPeakList = processedPeakList;

    intTolerance = parameters.getValue(MultiThreadPeakFinderParameters.intTolerance);
    mzTolerance = parameters.getValue(MultiThreadPeakFinderParameters.MZTolerance);
    rtTolerance = parameters.getValue(MultiThreadPeakFinderParameters.RTTolerance);
    minDataPoints = parameters.getValue(MultiThreadPeakFinderParameters.minDataPoints);
    this.rows = rows;
    this.processedRows = processedRows;
    totalScans = imagingFile.getNumOfScans(1);
    numRows = rows.size();
  }

  public void run() {

    setStatus(TaskStatus.PROCESSING);
    logger.info("Running multithreaded gap filler " + taskIndex + " on " + numRows + " rows of pkl:"
        + processedPeakList);

    final BinningMobilogramDataAccess mobilogramAccess = new BinningMobilogramDataAccess(
        imagingFile, BinningMobilogramDataAccess.getRecommendedBinWidth(imagingFile));

    // Process all raw data files
    List<Gap> gaps = new ArrayList<>();

    int filled = 0;
    for (FeatureListRow row : rows) {

      // Canceled?
      if (isCanceled()) {
        return;
      }

      // Fill each row of this raw data file column, create new empty gaps if necessary
      Range<Double> mzRange = mzTolerance.getToleranceRange(row.getAverageMZ());
      Range<Float> rtRange = imagingFile.getDataRTRange();

      Range<Float> mobilityRange = row.getMobilityRange();
      Gap newGap = new ImageImsGap(row, imagingFile, mzRange, rtRange, mobilityRange, intTolerance,
          mobilogramAccess);
      gaps.add(newGap);
    }

    // Stop processing this file if there are no gaps
    if (gaps.isEmpty()) {
      return;
    }

    // Get all scans of this data file
    processFile(imagingFile, gaps);

    if (isCanceled()) {
      return;
    }
    // Finalize gaps and add to feature list
    for (Gap gap : gaps) {
      if (gap.noMoreOffers(minDataPoints)) {
        filled++;
      }
      processedRows.getAndIncrement();
    }

    logger.info(String.format(
        "Finished sub task: Multithreaded gap filler %d on %d rows. (Gaps filled: %d)", taskIndex,
        numRows, filled));

    setStatus(TaskStatus.FINISHED);
  }

  /**
   * Needed high priority so that sub tasks do not wait for free task slot. Main task is also taking
   * one slot. Main task cannot be high because batch mode wont wait for that.
   */
  @Override
  public TaskPriority getTaskPriority() {
    return TaskPriority.NORMAL;
  }

  public double getFinishedPercentage() {
    if (totalScans == 0) {
      return 0;
    }
    return (double) processedScans.get() / (double) totalScans;
  }

  public String getTaskDescription() {
    return "Sub task " + taskIndex + ": Gap filling on " + (numRows) + " rows of pkl:"
        + processedPeakList.getName();
  }

  private void processFile(RawDataFile file, List<Gap> gaps) {
    if (file instanceof IMSRawDataFile imsFile && processedPeakList.hasFeatureType(
        MobilityType.class)) {
      final List<Frame> frames = (List<Frame>) ((IMSRawDataFile) file).getFrames(1);
      processedPeakList.setSelectedScans(file, frames);
      final MobilityScanDataAccess access = new MobilityScanDataAccess(imsFile,
          MobilityScanDataType.CENTROID, frames);
      List<ImageImsGap> imsGaps = (List<ImageImsGap>) (List<? extends Gap>) gaps;

      while (access.hasNextFrame()) {
        if (isCanceled()) {
          return;
        }

        final Frame frame = access.nextFrame();
        for (ImageImsGap gap : imsGaps) {
          access.resetMobilityScan();
          gap.offerNextScan(access);
        }
        processedScans.incrementAndGet();
      }

    } else {
      throw new IllegalStateException("Not an ims file");
    }
  }
}
