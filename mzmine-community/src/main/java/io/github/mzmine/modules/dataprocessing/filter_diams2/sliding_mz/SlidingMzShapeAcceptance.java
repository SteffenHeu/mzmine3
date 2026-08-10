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

import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

final class SlidingMzShapeAcceptance {

  private SlidingMzShapeAcceptance() {
  }

  static @NotNull SlidingMzShapeAcceptanceResult evaluate(
      @NotNull final SlidingMzShapeAcceptanceMode mode, @NotNull final IonTimeSeries<?> series,
      final double @NotNull [] isolationCenters, final int precursorCenterIndex,
      final int proposedApexIndex, final double isolationWindowWidth,
      final double edgeWindowMultiplier, final double minimumTopEdgeRatio,
      final int minimumConsecutivePoints, final int zeroSearchMarginIndices) {
    final int numberOfValues = series.getNumberOfValues();
    if (numberOfValues != isolationCenters.length) {
      throw new IllegalArgumentException(
          "Isolation-center and massogram lengths differ: %d != %d".formatted(
              isolationCenters.length, numberOfValues));
    }
    if (numberOfValues == 0 || precursorCenterIndex < 0 || precursorCenterIndex >= numberOfValues
        || proposedApexIndex < 0 || proposedApexIndex >= numberOfValues) {
      throw new IllegalArgumentException("Massogram indices are outside the available values.");
    }
    if (!(isolationWindowWidth > 0d)) {
      throw new IllegalArgumentException("The isolation-window width must be positive.");
    }
    if (!(minimumTopEdgeRatio > 0d)) {
      throw new IllegalArgumentException("The minimum top-to-edge ratio must be positive.");
    }
    if (edgeWindowMultiplier < 1d) {
      throw new IllegalArgumentException("The edge-window multiplier must be at least one.");
    }

    final double center = isolationCenters[precursorCenterIndex];
    final double coreLower = center - isolationWindowWidth * 0.5d;
    final double coreUpper = center + isolationWindowWidth * 0.5d;
    final int firstCoreIndex = firstIndexAtOrAbove(isolationCenters, coreLower);
    final int lastCoreIndex = lastIndexAtOrBelow(isolationCenters, coreUpper);
    if (firstCoreIndex < 0 || lastCoreIndex >= numberOfValues || firstCoreIndex > lastCoreIndex) {
      throw new IllegalArgumentException("The isolation window contains no massogram points.");
    }

    final int topIndex = findTopIndex(series, firstCoreIndex, lastCoreIndex, precursorCenterIndex);
    final double topIntensity = series.getIntensity(topIndex);
    final double edgeLower = center - isolationWindowWidth * edgeWindowMultiplier * 0.5d;
    final double edgeUpper = center + isolationWindowWidth * edgeWindowMultiplier * 0.5d;
    final int leftEdgeIndex = firstIndexAtOrAbove(isolationCenters, edgeLower);
    final int rightEdgeIndex = lastIndexAtOrBelow(isolationCenters, edgeUpper);
    final double leftEdgeIntensity = series.getIntensity(leftEdgeIndex);
    final double rightEdgeIntensity = series.getIntensity(rightEdgeIndex);
    final double edgeReference = Math.max(leftEdgeIntensity, rightEdgeIntensity);
    final double topEdgeRatio =
        edgeReference <= 0d ? topIntensity > 0d ? Double.POSITIVE_INFINITY : 0d
            : topIntensity / edgeReference;
    final int topConsecutivePositivePoints = consecutivePositivePoints(series, numberOfValues,
        topIndex);
    final boolean topEdgeAccepted =
        mode.usesTopEdgeRatio() && topConsecutivePositivePoints >= minimumConsecutivePoints
            && topEdgeRatio >= minimumTopEdgeRatio;

    final int proposedApexConsecutivePositivePoints = consecutivePositivePoints(series,
        numberOfValues, proposedApexIndex);
    final int leftZeroDistance = findLeftZeroDistance(series, firstCoreIndex,
        zeroSearchMarginIndices);
    final int rightZeroDistance = findRightZeroDistance(series, numberOfValues, lastCoreIndex,
        zeroSearchMarginIndices);
    final boolean zeroBoundedAccepted =
        proposedApexConsecutivePositivePoints >= minimumConsecutivePoints && leftZeroDistance > 0
            && rightZeroDistance > 0;
    final boolean accepted = mode.accepts(topEdgeAccepted, zeroBoundedAccepted);
    final int consecutivePositivePoints = mode.usesTopEdgeRatio() ? topConsecutivePositivePoints
        : proposedApexConsecutivePositivePoints;
    final int preferredApexIndex = mode.usesTopEdgeRatio() ? topIndex : proposedApexIndex;

    final String details = String.format(Locale.ROOT,
        "alternative shape mode %s: top %.3f at index %d / edge reference %.3f"
            + " (left %.3f at %d, right %.3f at %d) = %s (minimum %.3f, pass=%s);"
            + " apex-containing positive run %d (minimum %d); zero distances left=%s and"
            + " right=%s (maximum %d indices, pass=%s); isolation center %.4f,"
            + " shape window width %.4f, core indices %d-%d (%.4f-%.4f),"
            + " edge window %.2fx at indices %d-%d (%.4f-%.4f)", mode.name(), topIntensity,
        topIndex, edgeReference, leftEdgeIntensity, leftEdgeIndex, rightEdgeIntensity,
        rightEdgeIndex, formatRatio(topEdgeRatio), minimumTopEdgeRatio, topEdgeAccepted,
        consecutivePositivePoints, minimumConsecutivePoints, formatDistance(leftZeroDistance),
        formatDistance(rightZeroDistance), zeroSearchMarginIndices, zeroBoundedAccepted, center,
        isolationWindowWidth, firstCoreIndex, lastCoreIndex,
        coordinate(isolationCenters, firstCoreIndex), coordinate(isolationCenters, lastCoreIndex),
        edgeWindowMultiplier, leftEdgeIndex, rightEdgeIndex,
        coordinate(isolationCenters, leftEdgeIndex), coordinate(isolationCenters, rightEdgeIndex));
    return new SlidingMzShapeAcceptanceResult(accepted, preferredApexIndex, topEdgeAccepted,
        zeroBoundedAccepted, topEdgeRatio, consecutivePositivePoints, leftZeroDistance,
        rightZeroDistance, details);
  }

  private static int findTopIndex(@NotNull final IonTimeSeries<?> series, final int firstInclusive,
      final int lastInclusive, final int precursorCenterIndex) {
    int topIndex = firstInclusive;
    double topIntensity = series.getIntensity(topIndex);
    for (int i = firstInclusive + 1; i <= lastInclusive; i++) {
      final double intensity = series.getIntensity(i);
      if (intensity > topIntensity || Double.compare(intensity, topIntensity) == 0
          && Math.abs(i - precursorCenterIndex) < Math.abs(topIndex - precursorCenterIndex)) {
        topIndex = i;
        topIntensity = intensity;
      }
    }
    return topIndex;
  }

  private static int firstIndexAtOrAbove(final double @NotNull [] values, final double threshold) {
    for (int i = 0; i < values.length; i++) {
      if (values[i] >= threshold) {
        return i;
      }
    }
    return values.length;
  }

  private static int lastIndexAtOrBelow(final double @NotNull [] values, final double threshold) {
    for (int i = values.length - 1; i >= 0; i--) {
      if (values[i] <= threshold) {
        return i;
      }
    }
    return -1;
  }

  private static int consecutivePositivePoints(@NotNull final IonTimeSeries<?> series,
      final int numberOfValues, final int apexIndex) {
    if (series.getIntensity(apexIndex) <= 0d) {
      return 0;
    }
    int first = apexIndex;
    while (first > 0 && series.getIntensity(first - 1) > 0d) {
      first--;
    }
    int last = apexIndex;
    while (last + 1 < numberOfValues && series.getIntensity(last + 1) > 0d) {
      last++;
    }
    return last - first + 1;
  }

  private static int findLeftZeroDistance(@NotNull final IonTimeSeries<?> series,
      final int firstCoreIndex, final int maximumDistance) {
    for (int distance = 1; distance <= maximumDistance; distance++) {
      final int index = firstCoreIndex - distance;
      if (index < 0) {
        break;
      }
      if (series.getIntensity(index) <= 0d) {
        return distance;
      }
    }
    return -1;
  }

  private static int findRightZeroDistance(@NotNull final IonTimeSeries<?> series,
      final int numberOfValues, final int lastCoreIndex, final int maximumDistance) {
    for (int distance = 1; distance <= maximumDistance; distance++) {
      final int index = lastCoreIndex + distance;
      if (index >= numberOfValues) {
        break;
      }
      if (series.getIntensity(index) <= 0d) {
        return distance;
      }
    }
    return -1;
  }

  private static @NotNull String formatDistance(final int distance) {
    return distance < 0 ? "not found" : Integer.toString(distance);
  }

  private static @NotNull String formatRatio(final double ratio) {
    return Double.isInfinite(ratio) ? "infinite" : String.format(Locale.ROOT, "%.3f", ratio);
  }

  private static double coordinate(final double @NotNull [] coordinates, final int index) {
    return index < 0 || index >= coordinates.length ? Double.NaN : coordinates[index];
  }
}
