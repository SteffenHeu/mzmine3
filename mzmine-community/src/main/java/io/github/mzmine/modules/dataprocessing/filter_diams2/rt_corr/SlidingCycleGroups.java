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

package io.github.mzmine.modules.dataprocessing.filter_diams2.rt_corr;

import com.google.common.collect.Range;
import com.google.common.collect.TreeRangeMap;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Index of all windows because mzml files from msconvert dont have an ms2 for all zt scan scans.
 */
public class SlidingCycleGroups {

  private final TreeMap<Range<Float>, TreeMap<IsolationWindow, Scan>> cycles;

  /**
   * We need an index of all windows because mzml files from msconvert dont have an ms2 for all zt
   * scan scans.
   */
  private final TreeSet<IsolationWindow> windowsIndex = new TreeSet<>(
      Comparator.comparingDouble(w -> w.mzIsolation().lowerEndpoint()));

  /**
   * Controls how many cycles are merged during the RT correlation for sliding mz scan data.
   * <br>
   * n * 2 + 1
   */
  private final int additionalWindowsPerSide = 2;

  public SlidingCycleGroups(RawDataFile file,
      Map<IsolationWindow, List<Scan>> isolationWindowScanMap) {
    final List<Scan> ms1s = file.getScanNumbers(1);
    final TreeRangeMap<Float, TreeMap<IsolationWindow, Scan>> cycleWindows = TreeRangeMap.create();

    for (int i = 0; i < ms1s.size() - 1; i++) {
      final TreeMap<IsolationWindow, Scan> map = new TreeMap<>(
          Comparator.comparingDouble(iw -> iw.mzIsolation().lowerEndpoint()));
      final Range<Float> cycleRtRange = Range.closed(ms1s.get(i).getRetentionTime(),
          ms1s.get(i + 1).getRetentionTime());
      cycleWindows.put(cycleRtRange, map);

      for (Entry<IsolationWindow, List<Scan>> isolationWindowListEntry : isolationWindowScanMap.entrySet()) {
        final IsolationWindow window = isolationWindowListEntry.getKey();
        isolationWindowListEntry.getValue().stream()
            .filter(scan -> cycleRtRange.contains(scan.getRetentionTime())).findFirst()
            .ifPresent(scan -> map.put(window, scan));
        windowsIndex.add(window);
      }
    }

    cycles = new TreeMap<>(Comparator.comparing(Range::lowerEndpoint));
    cycles.putAll(cycleWindows.asMapOfRanges());
  }

  public WindowScans getMs2sAroundIsolation(final IsolationWindow centerWindow) {
    List<List<Scan>> result = new ArrayList<>();

    final TreeSet<IsolationWindow> includedWindows = new TreeSet<>(
        Comparator.comparingDouble(w -> w.mzIsolation().lowerEndpoint()));

    if (!windowsIndex.contains(centerWindow)) {
      return new WindowScans(centerWindow, List.of());
    }

    IsolationWindow mergedWindow = centerWindow;
    int extra = 0;
    includedWindows.add(centerWindow);
    var current = centerWindow;
    while (extra < additionalWindowsPerSide) {
      var lower = windowsIndex.lower(current);
      if (lower == null) {
        break;
      }
      includedWindows.add(lower);
      current = lower;
      extra++;
      mergedWindow = mergedWindow.merge(current);
    }
    extra = 0;
    while (extra < additionalWindowsPerSide) {
      var higher = windowsIndex.higher(current);
      if (higher == null) {
        break;
      }
      includedWindows.add(higher);
      current = higher;
      extra++;
      mergedWindow = mergedWindow.merge(current);
    }

    for (TreeMap<IsolationWindow, Scan> map : cycles.values()) {
      List<Scan> pack = new ArrayList<>(includedWindows.size());

      for (IsolationWindow window : includedWindows) {
        Scan scan = map.get(window);
        if (scan != null) {
          pack.add(scan);
        }
      }
      result.add(pack);
    }
    return new WindowScans(centerWindow, result);
  }

  public Set<IsolationWindow> getWindowsIndex() {
    return Set.copyOf(windowsIndex);
  }

  public record WindowScans(IsolationWindow window, List<List<Scan>> scans) {

  }
}
