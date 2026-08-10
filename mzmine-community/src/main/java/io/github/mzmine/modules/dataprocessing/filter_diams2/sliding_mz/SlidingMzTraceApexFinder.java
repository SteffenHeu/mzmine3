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

package io.github.mzmine.modules.dataprocessing.filter_diams2.sliding_mz;

import io.github.mzmine.util.collections.IndexRange;
import java.util.Locale;
import java.util.function.IntToDoubleFunction;
import org.jetbrains.annotations.NotNull;

final class SlidingMzTraceApexFinder {

  private SlidingMzTraceApexFinder() {
  }

  static @NotNull SlidingMzTraceApexResult findApex(@NotNull final IntToDoubleFunction intensityAt,
      final int numberOfValues, final int closestIsolationIndex,
      @NotNull final IndexRange isolationIndexRange, final int maxToleranceWindow) {
    if (numberOfValues < 1 || isolationIndexRange.isEmpty()) {
      throw new IllegalArgumentException("Cannot find an apex in an empty trace or index range.");
    }
    if (maxToleranceWindow < 0) {
      throw new IllegalArgumentException("Maximum apex distance must not be negative.");
    }

    final int minimumIndex = Math.max(0, isolationIndexRange.min());
    final int maximumIndex = Math.min(numberOfValues - 1, isolationIndexRange.maxInclusive());
    if (closestIsolationIndex < minimumIndex || closestIsolationIndex > maximumIndex) {
      throw new IllegalArgumentException(
          "Closest isolation index must be inside the available isolation range.");
    }

    final double centerIntensity = intensityAt.applyAsDouble(closestIsolationIndex);
    int centerPlateauMin = closestIsolationIndex;
    while (centerPlateauMin > minimumIndex && equalIntensity(
        intensityAt.applyAsDouble(centerPlateauMin - 1), centerIntensity)) {
      centerPlateauMin--;
    }
    int centerPlateauMax = closestIsolationIndex;
    while (centerPlateauMax < maximumIndex && equalIntensity(
        intensityAt.applyAsDouble(centerPlateauMax + 1), centerIntensity)) {
      centerPlateauMax++;
    }

    final boolean higherOnLeft = centerPlateauMin > minimumIndex
        && intensityAt.applyAsDouble(centerPlateauMin - 1) > centerIntensity;
    final boolean higherOnRight = centerPlateauMax < maximumIndex
        && intensityAt.applyAsDouble(centerPlateauMax + 1) > centerIntensity;

    if (higherOnLeft && higherOnRight) {
      return SlidingMzTraceApexResult.rejected(ExpectedFragmentRejectionReason.INVALID_LOCAL_SLOPE,
          String.format(Locale.ROOT,
              "the center plateau %d-%d at intensity %.3f is a local minimum", centerPlateauMin,
              centerPlateauMax, centerIntensity));
    }

    final int apexIndex;
    final int apexPlateauMin;
    final int apexPlateauMax;
    final double apexIntensity;
    if (!higherOnLeft && !higherOnRight) {
      if (centerPlateauMin == minimumIndex && centerPlateauMax == maximumIndex) {
        return SlidingMzTraceApexResult.rejected(
            ExpectedFragmentRejectionReason.INVALID_LOCAL_SLOPE, String.format(Locale.ROOT,
                "the trace is flat at intensity %.3f throughout isolation range %d-%d",
                centerIntensity, minimumIndex, maximumIndex));
      }
      // decision: the closest isolation index is the representative point when its plateau is a
      // local maximum.
      apexIndex = closestIsolationIndex;
      apexPlateauMin = centerPlateauMin;
      apexPlateauMax = centerPlateauMax;
      apexIntensity = centerIntensity;
    } else {
      final int searchDirection = higherOnRight ? 1 : -1;
      int currentIndex = searchDirection > 0 ? centerPlateauMax + 1 : centerPlateauMin - 1;
      int currentApexIndex = closestIsolationIndex;
      int currentApexPlateauMin = closestIsolationIndex;
      int currentApexPlateauMax = closestIsolationIndex;
      double currentMaximumIntensity = centerIntensity;

      // allow search to continue beyond the min and max indices of the search range by 1. If they lie outside
      // these boundaries later, we can reject them there. Otherwise we cannot discriminate between
      // a maximum at the edge or a maximum outside the edge.
      while (currentIndex >= Math.max(minimumIndex - 1, 0) && currentIndex <= Math.min(
          maximumIndex + 1, numberOfValues - 1)) {
        final double currentIntensity = intensityAt.applyAsDouble(currentIndex);
        final int intensityComparison = Double.compare(currentIntensity, currentMaximumIntensity);
        if (intensityComparison > 0) {
          currentMaximumIntensity = currentIntensity;
          currentApexIndex = currentIndex;
          currentApexPlateauMin = currentIndex;
          currentApexPlateauMax = currentIndex;
        } else if (intensityComparison == 0) {
          // decision: continue across a flat step, but retain its point closest to the precursor
          // isolation center as the representative apex.
          currentApexPlateauMin = Math.min(currentApexPlateauMin, currentIndex);
          currentApexPlateauMax = Math.max(currentApexPlateauMax, currentIndex);
        } else {
          break;
        }
        currentIndex += searchDirection;
      }

      apexIndex = currentApexIndex;
      apexPlateauMin = currentApexPlateauMin;
      apexPlateauMax = currentApexPlateauMax;
      apexIntensity = currentMaximumIntensity;
    }

    if (apexPlateauMin < minimumIndex || apexPlateauMax > maximumIndex) {
      return SlidingMzTraceApexResult.rejected(ExpectedFragmentRejectionReason.ISOLATION_RANGE_EDGE,
          String.format(Locale.ROOT,
              "the maximum plateau %d-%d reaches the isolation-range edge %d-%d", apexPlateauMin,
              apexPlateauMax, minimumIndex, maximumIndex));
    }

    final int apexDistance = Math.abs(apexIndex - closestIsolationIndex);
    if (apexDistance > maxToleranceWindow) {
      return SlidingMzTraceApexResult.rejected(
          ExpectedFragmentRejectionReason.APEX_OUTSIDE_TOLERANCE, String.format(Locale.ROOT,
              "the traced maximum at index %d is %d isolation step(s) from the precursor index %d; at most %d are allowed",
              apexIndex, apexDistance, closestIsolationIndex, maxToleranceWindow));
    }

    return SlidingMzTraceApexResult.accepted(apexIndex,
        String.format(Locale.ROOT, "maximum intensity %.3f at index %d with plateau %d-%d",
            apexIntensity, apexIndex, apexPlateauMin, apexPlateauMax));
  }

  private static boolean equalIntensity(final double left, final double right) {
    return Double.compare(left, right) == 0;
  }
}
