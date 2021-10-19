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

package io.github.mzmine.util;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.MobilityScan;
import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

public class ParsingUtils {

  /**
   * Value separator for storing lists and arrays.
   */
  public static String SEPARATOR = ";";

  public static double[] stringToDoubleArray(String string) {
    final String[] strValues = string.split(ParsingUtils.SEPARATOR);
    final double[] values = new double[strValues.length];
    for (int i = 0; i < strValues.length; i++) {
      values[i] = Double.parseDouble(strValues[i]);
    }
    return values;
  }

  public static String doubleArrayToString(double[] array) {
    return doubleArrayToString(array, array.length);
  }

  public static String doubleArrayToString(double[] array, int length) {
    assert length <= array.length;

    StringBuilder b = new StringBuilder();
    for (int i = 0; i < length; i++) {
      double v = array[i];
      b.append(v);
      if (i < length - 1) {
        b.append(SEPARATOR);
      }
    }
    return b.toString();
  }

  public static String doubleBufferToString(DoubleBuffer buffer) {
    StringBuilder b = new StringBuilder();
    for (int i = 0, arrayLength = buffer.capacity(); i < arrayLength; i++) {
      double v = buffer.get(i);
      b.append(v);
      if (i < arrayLength - 1) {
        b.append(SEPARATOR);
      }
    }
    return b.toString();
  }

  public static String intArrayToString(int[] array, int length) {
    assert length <= array.length;

    StringBuilder b = new StringBuilder();
    for (int i = 0; i < length; i++) {
      int v = array[i];
      b.append(v);
      if (i < length - 1) {
        b.append(SEPARATOR);
      }
    }
    return b.toString().trim();
  }

  public static int[] stringToIntArray(String string) {
    final String[] strValues = string.split(ParsingUtils.SEPARATOR);
    final int[] values = new int[strValues.length];
    for (int i = 0; i < strValues.length; i++) {
      values[i] = Integer.parseInt(strValues[i]);
    }
    return values;
  }

  public static <T extends Object> int[] getIndicesOfSubListElements(List<T> sublist,
      List<T> fullList) {
    final int[] indices = new int[sublist.size()];

    int rawIndex = fullList.indexOf(sublist.get(0));
    int subListIndex = 0;
    indices[subListIndex] = rawIndex;

    while (subListIndex < sublist.size() && rawIndex < fullList.size()) {
      if (sublist.get(subListIndex)
          .equals(fullList.get(rawIndex))) { // don't compare identity to make robin happy
        indices[subListIndex] = rawIndex;
        subListIndex++;
      }
      rawIndex++;
    }

    if (subListIndex < sublist.size()) {
      throw new IllegalStateException(
          "Incomplete remap. Sublist did not contain all scans in the fullList.");
    }

    return indices;
  }

  public static <T extends Object> List<T> getSublistFromIndices(List<T> list, int[] indices) {
    List<T> sublist = new ArrayList<>();
    for (int index : indices) {
      sublist.add(list.get(index));
    }
    return sublist;
  }


  public static String rangeToString(Range<Comparable<?>> range) {
    return "[" + range.lowerEndpoint() + SEPARATOR + range.upperEndpoint() + "]";
  }

  public static Range<Double> stringToDoubleRange(String str) {
    Pattern regex = Pattern
        .compile("\\[([+-]?([0-9]*[.])?[0-9]+)" + SEPARATOR + "([+-]?([0-9]*[.])?[0-9]+)\\]");
    Matcher matcher = regex.matcher(str);
    if (matcher.matches()) {
      double lower = Double.parseDouble(matcher.group(1));
      double upper = Double.parseDouble(matcher.group(3));
      return Range.closed(lower, upper);
    }
    return null;
  }

  public static Range<Float> stringToFloatRange(String str) {
    Pattern regex = Pattern
        .compile("\\[([+-]?([0-9]*[.])?[0-9]+)" + SEPARATOR + "([+-]?([0-9]*[.])?[0-9]+)\\]");
    Matcher matcher = regex.matcher(str);
    if (matcher.matches()) {
      float lower = Float.parseFloat(matcher.group(1));
      float upper = Float.parseFloat(matcher.group(3));
      return Range.closed(lower, upper);
    }
    return null;
  }

  public static Range<Integer> parseIntegerRange(String str) {
    Pattern regex = Pattern.compile("\\[([0-9]+)" + SEPARATOR + "([0-9]+)\\]");
    Matcher matcher = regex.matcher(str);
    if (matcher.matches()) {
      int lower = Integer.parseInt(matcher.group(1));
      int upper = Integer.parseInt(matcher.group(2));
      return Range.closed(lower, upper);
    }
    return null;
  }

  public static String mobilityScanListToString(List<MobilityScan> scans) {
    // {frameindex}[mobilityscanindices]\\
    StringBuilder b = new StringBuilder();
    final Map<Frame, List<MobilityScan>> mapping = scans.stream()
        .collect(Collectors.groupingBy(MobilityScan::getFrame));
    for (Iterator<Entry<Frame, List<MobilityScan>>> it = mapping.entrySet().iterator();
        it.hasNext(); ) {
      Entry<Frame, List<MobilityScan>> entry = it.next();
      Frame frame = entry.getKey();
      List<MobilityScan> mobilityScans = entry.getValue();
      mobilityScans.sort(Comparator.comparingInt(MobilityScan::getMobilityScanNumber));
      b.append("{").append(frame.getDataFile().getScans().indexOf(frame)).append("}");

      int[] indices = ParsingUtils
          .getIndicesOfSubListElements(mobilityScans, frame.getMobilityScans());
      b.append("[").append(ParsingUtils.intArrayToString(indices, indices.length)).append("]");

      if (it.hasNext()) {
        b.append(SEPARATOR).append(SEPARATOR);
      }
    }
    return b.toString();
  }

  @Nullable
  public static List<MobilityScan> stringToMobilityScanList(String str, IMSRawDataFile file) {
    final List<MobilityScan> mobilityScans = new ArrayList<>();
    final String[] split = str.split(SEPARATOR + SEPARATOR);
    final Pattern pattern = Pattern.compile("\\{([0-9]+)\\}\\[([^\\n]+)\\]");

    for (final String s : split) {
      Matcher matcher = pattern.matcher(s);
      if (matcher.matches()) {
        int frameIndex = Integer.parseInt(matcher.group(1));
        Frame frame = file.getFrame(frameIndex);

        int[] indices = stringToIntArray(matcher.group(2));
        mobilityScans.addAll(ParsingUtils.getSublistFromIndices(frame.getMobilityScans(), indices));
      } else {
        throw new IllegalStateException("Pattern does not match");
      }
    }

    return mobilityScans.isEmpty() ? null : mobilityScans;
  }

  public static String stringArrayToString(String[] array) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < array.length; i++) {
      b.append(array[i]);
      if (i < array.length - 1) {
        b.append(SEPARATOR);
      }
    }
    return b.toString();
  }

  public static String[] stringToStringArray(String str) {
    return str.split(SEPARATOR);
  }
}
