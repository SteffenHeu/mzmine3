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
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.ScanDataType;
import io.github.mzmine.datamodel.data_access.ScanDataAccess;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ExpandedDataPoint;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.exceptions.MissingMassListException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XICBuilderTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(XICBuilderTask.class.getName());
  private final ScanSelection selection;
  private final MZTolerance initTol;
  private final double minStartIntensity;
  private final RawDataFile file;
  private final int maxNumZeros;
  private final int numSeeds;
  private final double rangeTolerance;
  private final @NotNull ParameterSet parameters;
  private final XICMergeMethod xicMergeMethod;
  private final ModularFeatureList flist;
  private final MZmineProject project;
  private double progress = 0d;

  public XICBuilderTask(@Nullable MemoryMapStorage storage, @NotNull Instant moduleCallDate,
      RawDataFile file, @NotNull ParameterSet parameters, @NotNull MZmineProject project) {
    super(storage, moduleCallDate);
    this.file = file;
    selection = parameters.getValue(XICBuilderParameters.scans);
    initTol = parameters.getValue(XICBuilderParameters.mzTol);
    minStartIntensity = parameters.getValue(XICBuilderParameters.minHighestIntensity);
    maxNumZeros = parameters.getValue(XICBuilderParameters.maxNumZeros);
    numSeeds = parameters.getValue(XICBuilderParameters.numSeeds);
    rangeTolerance = parameters.getValue(XICBuilderParameters.mzRangeTolerance);
    xicMergeMethod = parameters.getValue(XICBuilderParameters.mergeMode);
    this.parameters = parameters;
    flist = new ModularFeatureList(file.getName(), getMemoryMapStorage(), file);
    this.project = project;
  }

  @Override
  public String getTaskDescription() {
    return null;
  }

  @Override
  public double getFinishedPercentage() {
    return progress;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);

    final ScanDataAccess access = EfficientDataAccess.of(file, ScanDataType.MASS_LIST, selection);
    // make a list of all the data points

    logger.finest(() -> "Extracting data points above " + minStartIntensity + " intensity.");

    final List<ExpandedDataPoint> dps = extractStartDataPoints(access);
    if (isCanceled()) {
      return;
    }

    logger.finest(() -> "Extracted " + dps.size() + " data points.");
//    dps.sort(Comparator.comparingDouble(ExpandedDataPoint::getIntensity).reversed());
    NavigableSet<ExpandedDataPoint> sortedDps = new TreeSet<>((p1, p2) -> {
      final int comp1 = Double.compare(p1.getIntensity(), p2.getIntensity()) * -1;
      if (comp1 != 0) {
        return comp1;
      }

      final int comp2 = Double.compare(p1.getMZ(), p2.getMZ());
      if (comp2 != 0) {
        return comp2;
      }

      return p1.getScan().compareTo(p2.getScan());
    });
    sortedDps.addAll(dps);
    logger.finest(() -> "Sorted data points.");

    List<XIC> xics = new ArrayList<>();

    int size = sortedDps.size();
    while (sortedDps.size() > 0) {
      if (isCanceled()) {
        return;
      }

      final ExpandedDataPoint seed = sortedDps.first();
      sortedDps.remove(seed);

      final XIC xic = new XIC(seed, initTol, numSeeds, maxNumZeros, rangeTolerance);
      final Collection<ExpandedDataPoint> dataPoints = xic.findDataPoints(access);

      for (ExpandedDataPoint dp : dataPoints) {
        if (dp.getIntensity() >= minStartIntensity) {
          sortedDps.remove(dp);
        }
      }

//      final int prevSize = size;
//      size = sortedDps.size();
//      logger.finest(() -> "Removed " + (prevSize - sortedDps.size()) + " data points. xic-length: "
//          + dataPoints.size() + ". " + sortedDps.size() + " remaining.");
//      assert prevSize - size == dataPoints.size();

      progress = (1 - (sortedDps.size()) / (double) dps.size()) * 0.9;
      if ((dataPoints.size() - 2 * maxNumZeros) >= numSeeds) {
        xics.add(xic);
      }
    }

    // we may have a lot of duplicates now, so we need to merge them
    xics = mergeXICs(xics);

    AtomicInteger id = new AtomicInteger(1);
    final List<ModularFeatureListRow> rows = xics.parallelStream().map(
            xic -> new ModularFeature(flist, file, xic.toIonTimeSeries(getMemoryMapStorage()),
                FeatureStatus.DETECTED)).sequential().sorted(Comparator.comparingDouble(Feature::getRT))
        .map(f -> new ModularFeatureListRow(flist, id.getAndIncrement(), f)).toList();

    logger.finest(() -> "Adding rows to feature list.");

    rows.forEach(flist::addRow);
    flist.getAppliedMethods().add(
        new SimpleFeatureListAppliedMethod(XICBuilderModule.class, parameters,
            getModuleCallDate()));
    flist.setSelectedScans(file, List.of(selection.getMatchingScans(file)));
    project.addFeatureList(flist);

    setStatus(TaskStatus.FINISHED);
  }

  private List<XIC> mergeXICs(List<XIC> xics) {
    logger.finest(() -> "Merging duplicate xics.");

    RangeMap<Double, List<XIC>> xicMap = TreeRangeMap.create();

    List<XIC> unmergedXICs = new ArrayList<>();

    for (final XIC xic : xics) {
      if(isCanceled()) {
        return List.of();
      }

      final double centerMz = xic.getCenterMz();

      final Entry<Range<Double>, List<XIC>> entry = xicMap.getEntry(centerMz);
      final Range<Double> range = xic.getRange();
      final Entry<Range<Double>, List<XIC>> lower = xicMap.getEntry(range.lowerEndpoint());
      final Entry<Range<Double>, List<XIC>> upper = xicMap.getEntry(range.upperEndpoint());

      double lowerBound = range.lowerEndpoint();
      double upperBound = range.upperEndpoint();

      if (lower != null || upper != null || entry != null) {
        final int newSize =
            (entry != null ? entry.getValue().size() : 0) + (lower != null ? lower.getValue().size()
                : 0) + (upper != null ? upper.getValue().size() : 0) + 1;
        List<XIC> newValue = new ArrayList<>(newSize);

        if (entry != null) {
          newValue.addAll(entry.getValue());
          lowerBound = Math.min(lowerBound, entry.getKey().lowerEndpoint());
          upperBound = Math.max(upperBound, entry.getKey().upperEndpoint());
        }
        if (lower != null && lower != entry) {
          newValue.addAll(lower.getValue());
          lowerBound = Math.min(lowerBound, lower.getKey().lowerEndpoint());
        }
        if (upper != null && upper != entry && upper != lower) {
          newValue.addAll(upper.getValue());
          upperBound = Math.max(upperBound, upper.getKey().upperEndpoint());
        }
        newValue.add(xic);

        // put to the map, merging the mz ranges
        xicMap.putCoalescing(Range.closed(lowerBound, upperBound), newValue);
      } else {
        final List<XIC> list = new ArrayList<>();
        list.add(xic);
        xicMap.put(xic.getRange(), list);
      }
    }

    final Map<Range<Double>, List<XIC>> rangeListMap = xicMap.asMapOfRanges();
    List<XIC> mergedXICs = new ArrayList<>();
    for (List<XIC> xicList : rangeListMap.values()) {
      xicList.sort((x1, x2) -> -1 * Double.compare(x1.getSeed().getIntensity(),
          x2.getSeed().getIntensity()));
      final XIC mainXIC = xicList.get(0);
      xicList.remove(0);

      for (XIC xic : xicList) {
        /*final int numUnequalPoints = mainXIC.getNumberOfUnequalOverlappingPoints(xic.getDps());
        if (numUnequalPoints < 5) {
          mainXIC.merge(xic, xicMergeMethod);
        } else {
          logger.finest("Will not merge, too many unequal overlapping points. " + numUnequalPoints);
          unmergedXICs.add(xic);
        }*/
        final XIC remaining = mainXIC.merge(xic, xicMergeMethod);
        if(remaining != null) {
          unmergedXICs.add(remaining);
        }
      }
      mergedXICs.add(mainXIC);
    }

    if(unmergedXICs.size() > 1) {
      mergedXICs.addAll(mergeXICs(unmergedXICs));
    }

    logger.finest("Merged " + xics.size() + " XICs. " + mergedXICs.size() + " XICs remaining.");
    return mergedXICs;
  }

  private List<ExpandedDataPoint> extractStartDataPoints(ScanDataAccess access) {
    final List<ExpandedDataPoint> allMzValues = new ArrayList<>();

    while (access.hasNextScan()) {
      if (isCanceled()) {
        return allMzValues;
      }

      Scan scan;
      try {
        scan = access.nextScan();
      } catch (MissingMassListException e) {
        setStatus(TaskStatus.ERROR);
        StringBuilder b = new StringBuilder("Scan #");
        b.append(access.getCurrentScan().getScanNumber()).append(" from ");
        b.append(file.getName());
        b.append(
            " does not have a mass list. Please run \"Raw data methods\" -> \"Mass detection\"");
        if (access instanceof IMSRawDataFile) {
          b.append("\nIMS files require mass detection on the frame level (Scan type = \"Frames ");
          b.append("only\" or \"All scan types\"");
        }
        setErrorMessage(b.toString());
        e.printStackTrace();
        return allMzValues;
      }

      int dps = access.getNumberOfDataPoints();
      for (int i = 0; i < dps; i++) {
        final double intensity = access.getIntensityValue(i);
        if (intensity > minStartIntensity) {
          ExpandedDataPoint curDatP = new ExpandedDataPoint(access.getMzValue(i), intensity, scan);
          allMzValues.add(curDatP);
        }
      }
    }
    return allMzValues;
  }
}
