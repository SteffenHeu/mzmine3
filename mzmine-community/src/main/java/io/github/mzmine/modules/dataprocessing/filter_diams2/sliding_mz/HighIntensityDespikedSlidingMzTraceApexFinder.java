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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntToDoubleFunction;
import org.jetbrains.annotations.NotNull;

final class HighIntensityDespikedSlidingMzTraceApexFinder {

  private static final double MINIMUM_SIGNAL_TO_THRESHOLD_RATIO = 100d;
  private static final double MAXIMUM_RELATIVE_DIP = 0.20d;

  private HighIntensityDespikedSlidingMzTraceApexFinder() {
  }

  static @NotNull SlidingMzTraceApexEvaluation evaluate(
      @NotNull final IntToDoubleFunction intensityAt, final int numberOfValues,
      final int closestIsolationIndex, @NotNull final IndexRange isolationIndexRange,
      final int maxToleranceWindow, final double minimumFragmentIntensity) {
    final Map<Integer, Double> correctedIntensities = new HashMap<>();
    final IntToDoubleFunction decisionIntensityAt = index -> {
      final Double corrected = correctedIntensities.get(index);
      return corrected == null ? intensityAt.applyAsDouble(index) : corrected;
    };
    final double minimumStrongSignal = Math.max(0d,
        minimumFragmentIntensity * MINIMUM_SIGNAL_TO_THRESHOLD_RATIO);
    final int minimumIndex = Math.max(1, isolationIndexRange.min());
    final int maximumIndex = Math.min(numberOfValues - 2, isolationIndexRange.maxInclusive());
    int correctedDips = 0;
    double deepestCorrectedDip = 0d;

    for (int i = minimumIndex; i <= maximumIndex; i++) {
      if (Math.abs(i - closestIsolationIndex) > maxToleranceWindow + 1) {
        continue;
      }
      final double center = decisionIntensityAt.applyAsDouble(i);
      final double left = decisionIntensityAt.applyAsDouble(i - 1);
      final double right = decisionIntensityAt.applyAsDouble(i + 1);
      if (left <= center || right <= center) {
        continue;
      }
      final double localMaximum = Math.max(left, right);
      final double lowerShoulder = Math.min(left, right);
      final double relativeDip =
          localMaximum <= 0d ? Double.POSITIVE_INFINITY : (lowerShoulder - center) / localMaximum;
      if (localMaximum < minimumStrongSignal || relativeDip > MAXIMUM_RELATIVE_DIP) {
        continue;
      }

      // decision: only fill isolated, shallow notches in strong traces. Raw intensities remain
      // unchanged and are still used for the final spectrum and minimum-intensity threshold.
      correctedIntensities.put(i, lowerShoulder);
      correctedDips++;
      deepestCorrectedDip = Math.max(deepestCorrectedDip, relativeDip);
    }

    // A one-point correction can expose a short, exactly level depression around the isolation
    // center. Raise only such strong, shallow central plateaus and stop as soon as either side no
    // longer rises. This retains a split saturated top without smoothing low-intensity traces.
    boolean correctedCentralPlateau;
    do {
      correctedCentralPlateau = false;
      final double centerIntensity = decisionIntensityAt.applyAsDouble(closestIsolationIndex);
      int plateauMinimum = closestIsolationIndex;
      while (plateauMinimum > minimumIndex
          && Double.compare(decisionIntensityAt.applyAsDouble(plateauMinimum - 1), centerIntensity)
          == 0) {
        plateauMinimum--;
      }
      int plateauMaximum = closestIsolationIndex;
      while (plateauMaximum < maximumIndex
          && Double.compare(decisionIntensityAt.applyAsDouble(plateauMaximum + 1), centerIntensity)
          == 0) {
        plateauMaximum++;
      }
      if (plateauMinimum <= minimumIndex || plateauMaximum >= maximumIndex) {
        continue;
      }

      final double left = decisionIntensityAt.applyAsDouble(plateauMinimum - 1);
      final double right = decisionIntensityAt.applyAsDouble(plateauMaximum + 1);
      if (left <= centerIntensity || right <= centerIntensity) {
        continue;
      }
      final double localMaximum = Math.max(left, right);
      final double lowerShoulder = Math.min(left, right);
      final double relativeDip = localMaximum <= 0d ? Double.POSITIVE_INFINITY
          : (lowerShoulder - centerIntensity) / localMaximum;
      if (localMaximum < minimumStrongSignal || relativeDip > MAXIMUM_RELATIVE_DIP) {
        continue;
      }

      for (int i = plateauMinimum; i <= plateauMaximum; i++) {
        correctedIntensities.put(i, lowerShoulder);
      }
      correctedDips += plateauMaximum - plateauMinimum + 1;
      deepestCorrectedDip = Math.max(deepestCorrectedDip, relativeDip);
      correctedCentralPlateau = true;
    } while (correctedCentralPlateau);

    final SlidingMzTraceApexResult result = SlidingMzTraceApexFinder.findApex(decisionIntensityAt,
        numberOfValues, closestIsolationIndex, isolationIndexRange, maxToleranceWindow);
    final String details = String.format(Locale.ROOT,
        "high-intensity shallow-dip decision trace corrected %d point(s), deepest %.1f%%, "
            + "minimum corrected signal %.3f (threshold ratio %.3f); %s", correctedDips,
        deepestCorrectedDip * 100d, minimumStrongSignal, MINIMUM_SIGNAL_TO_THRESHOLD_RATIO,
        result.details());
    final SlidingMzTraceApexResult annotatedResult =
        result.isAccepted() ? SlidingMzTraceApexResult.accepted(result.apexIndex(), details)
            : SlidingMzTraceApexResult.rejected(Objects.requireNonNull(result.rejectionReason()),
                details);
    return new SlidingMzTraceApexEvaluation(annotatedResult, decisionIntensityAt);
  }
}
