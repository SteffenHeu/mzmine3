/*
 * Copyright (c) 2004-2024 The MZmine Development Team
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

package io.github.mzmine.modules.dataprocessing.featdet_xic;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.data_access.ScanDataAccess;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.featuredata.impl.SimpleIonTimeSeries;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ExpandedDataPoint;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.collections.BinarySearch.DefaultTo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XIC {

  private static final Logger logger = Logger.getLogger(XIC.class.getName());
  protected final Map<Scan, ExpandedDataPoint> dps = new HashMap<>();
  protected final ExpandedDataPoint seed;
  private final int CENTER_SIZE;
  private final int TERMINATION_THRESHOLD;
  private final double TOLERANCE;
  protected MZTolerance tolerance;
  protected Range<Double> range = null;
  protected boolean isForwardTerminated = false;
  protected boolean isBackwardTerminated = false;
  protected double centerMz;
  protected double summedIntensity = 0d;

  public XIC(ExpandedDataPoint seed, MZTolerance startTolerance, int numSeeds, int maxNumZeros,
      double rangeTolerance) {
    this.seed = seed;
    dps.put(seed.getScan(), seed);
    tolerance = startTolerance;
    centerMz = seed.getMZ();
    CENTER_SIZE = numSeeds;
    TERMINATION_THRESHOLD = maxNumZeros;
    TOLERANCE = rangeTolerance;
  }

  public XIC(List<ExpandedDataPoint> dps, MZTolerance startTolerance, int numSeeds, int maxNumZeros,
      double rangeTolerance) {
    dps = dps.stream()
        .sorted(Comparator.comparingDouble(ExpandedDataPoint::getIntensity).reversed()).toList();
    seed = dps.get(0);
    for (ExpandedDataPoint dp : dps) {
      addDataPoint(dp);
    }
    tolerance = startTolerance;
    CENTER_SIZE = numSeeds;
    TERMINATION_THRESHOLD = maxNumZeros;
    TOLERANCE = rangeTolerance;
  }

  public Collection<ExpandedDataPoint> findDataPoints(@NotNull ScanDataAccess access) {
    access.jumpToScan(seed.getScan());
    final int startIndex = access.indexOf(seed.getScan());

    int backwardTerminationCounter = 0;
    int forwardTerminationCounter = 0;
    int offset = 1;

    while (!(isForwardTerminated && isBackwardTerminated)) {
      // previous data point
      if (startIndex - offset >= 0 && !isBackwardTerminated) {
        access.jumpToIndex(startIndex - offset);
        final Scan scan = access.getCurrentScan();
        if (scan == null) {
          throw new IllegalStateException();
        }
        int closestIndex = access.binarySearch(centerMz, DefaultTo.CLOSEST_VALUE);
        if (closestIndex < 0) {
          dps.put(scan, new ExpandedDataPoint(0, 0, scan));
          backwardTerminationCounter++;
        } else if (tolerance.checkWithinTolerance(centerMz, access.getMzValue(closestIndex))) {
          final ExpandedDataPoint dp = new ExpandedDataPoint(access.getMzValue(closestIndex),
              access.getIntensityValue(closestIndex), scan);
          addDataPoint(dp);
          backwardTerminationCounter = 0;
        } else {
          dps.put(scan, new ExpandedDataPoint(0, 0, scan));
          backwardTerminationCounter++;
        }
      }

      // next data point
      if (startIndex + offset < access.getNumberOfScans() && !isForwardTerminated) {
        access.jumpToIndex(startIndex + offset);
        final Scan scan = access.getCurrentScan();
        if (scan == null) {
          throw new IllegalStateException();
        }
        int closestIndex = access.binarySearch(centerMz, DefaultTo.CLOSEST_VALUE);
        if (closestIndex < 0) {
          dps.put(scan, new ExpandedDataPoint(0, 0, scan));
          forwardTerminationCounter++;
        } else if (rangeContains(access.getMzValue(closestIndex))) {
          final ExpandedDataPoint dp = new ExpandedDataPoint(access.getMzValue(closestIndex),
              access.getIntensityValue(closestIndex), scan);
          addDataPoint(dp);
          forwardTerminationCounter = 0;
        } else {
          dps.put(scan, new ExpandedDataPoint(0, 0, scan));
          forwardTerminationCounter++;
        }
      }
      offset++;

      if (backwardTerminationCounter >= TERMINATION_THRESHOLD || startIndex - offset < 0) {
        isBackwardTerminated = true;
      }
      if (forwardTerminationCounter >= TERMINATION_THRESHOLD
          || startIndex + offset >= access.getNumberOfScans()) {
        isForwardTerminated = true;
      }
    }

    return dps.values();
  }

  private void addDataPoint(ExpandedDataPoint dp) {
    dps.put(dp.getScan(), dp);
    final double newIntensity = summedIntensity + dp.getIntensity();
    centerMz =
        centerMz * summedIntensity / newIntensity + dp.getMZ() * dp.getIntensity() / newIntensity;
    summedIntensity = newIntensity;

    if (range == null && dps.size() >= CENTER_SIZE) {
      double lower = centerMz;
      double upper = centerMz;
      for (ExpandedDataPoint value : dps.values()) {
        lower = Double.compare(value.getMZ(), 0) != 0 ? Math.min(value.getMZ(), lower) : lower;
        upper = Math.max(value.getMZ(), upper);
      }
      final double length = upper - lower;
      range = Range.closed(lower - length * TOLERANCE, upper + length * TOLERANCE);

//      final double ppmDiff = MathUtils.getPpmDiff(lower, upper);
//      logger.finest(
//          () -> String.format("Confined m/z range of XIC %.4f to %s (%.2f ppm wide)", centerMz,
//              range.toString(), ppmDiff));
//      range = tolerance.getToleranceRange(centerMz);
    }
  }

  private boolean rangeContains(double mz) {
    if (range != null) {
      return range.contains(mz);
    }
    return tolerance.checkWithinTolerance(centerMz, mz);
  }

  public IonTimeSeries<Scan> toIonTimeSeries(@Nullable MemoryMapStorage storage) {
    final int numValues = dps.size();
    double[] mzs = new double[numValues];
    double[] intensities = new double[numValues];
    List<Scan> scans = new ArrayList<>(numValues);
    final List<Entry<Scan, ExpandedDataPoint>> entries = dps.entrySet().stream()
        .sorted(Comparator.comparing(Entry::getKey)).toList();

    for (int i = 0; i < entries.size(); i++) {
      final Entry<Scan, ExpandedDataPoint> entry = entries.get(i);
      mzs[i] = entry.getValue().getMZ();
      intensities[i] = entry.getValue().getIntensity();
      scans.add(entry.getKey());
    }

    return new SimpleIonTimeSeries(storage, mzs, intensities, scans);
  }

  public double getCenterMz() {
    return centerMz;
  }

  public ExpandedDataPoint getSeed() {
    return seed;
  }

  public Range<Double> getRange() {
    if (range == null) {
      range = tolerance.getToleranceRange(centerMz);
    }
    return range;
  }

  public Map<Scan, ExpandedDataPoint> getDps() {
    return dps;
  }

  public XIC merge(XIC xic, XICMergeMethod mergeMethod) {
    // todo more sophisticated merge function
    // 1. option always use the most intense point, override all others
    // 2. option to use the more intense point and return all others as a new XIC if it specifies all previous parameters.
    // 3. only merge onto zeros

    switch (mergeMethod) {
      case MOST_INTENSE -> {
        xic.getDps().values().forEach(dp -> {
          // keep the more intense data point
          // intensities might be zero on the edges, so we keep the higher data point.
          dps.merge(dp.getScan(), dp,
              (oldValue, newValue) -> newValue.getIntensity() > oldValue.getIntensity() ? newValue
                  : oldValue);
        });
        return null;
      }
      case MOST_INTENSE_ITERATE -> {
        final List<ExpandedDataPoint> remaining = xic.getDps().values().stream()
            .<ExpandedDataPoint>mapMulti((dp, c) -> {
              final ExpandedDataPoint current = dps.get(dp.getScan());
              if (current == null) {
                dps.put(dp.getScan(), dp); // add the point
              } else if (dp.getIntensity() > current.getIntensity()) {
                dps.put(dp.getScan(), dp); // add the new point
                c.accept(current); // send the current one downstream
              } else if (!current.equals(dp)) {
                c.accept(dp); // send the dp downstream
              }
            }).toList();

        if (remaining.isEmpty()) {
          return null;
        }
        return new XIC(remaining, tolerance, CENTER_SIZE, TERMINATION_THRESHOLD, TOLERANCE);
      }
      case OVERRIDE_ZEROS -> {
        xic.getDps().values().forEach(dp -> {
          dps.merge(dp.getScan(), dp,
              (oldValue, newValue) -> Double.compare(oldValue.getIntensity(), 0d) == 0 ? newValue
                  : oldValue);
        });
        return null;
      }
      default -> throw new IllegalStateException("Unexpected value: " + mergeMethod);
    }
  }

  public int getNumberOfUnequalOverlappingPoints(Map<Scan, ExpandedDataPoint> that) {
    return ((Long) that.entrySet().stream().filter(entry -> {
      final ExpandedDataPoint myDp = dps.get(entry.getKey());
      return myDp != null && !myDp.equals(entry.getValue());
    }).count()).intValue();
  }
}
