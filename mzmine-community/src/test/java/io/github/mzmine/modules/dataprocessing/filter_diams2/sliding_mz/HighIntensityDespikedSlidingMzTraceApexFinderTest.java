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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntToDoubleFunction;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class HighIntensityDespikedSlidingMzTraceApexFinderTest {

  @Test
  void retainsShallowSplitApex() {
    final SlidingMzTraceApexEvaluation evaluation = evaluate(
        new double[]{0, 10, 1000, 900, 980, 10, 0}, 3, 3, 5);

    Assertions.assertAll(() -> Assertions.assertTrue(evaluation.result().isAccepted(),
            evaluation.result().details()),
        () -> Assertions.assertEquals(2, evaluation.result().apexIndex()),
        () -> Assertions.assertEquals(980d, evaluation.decisionIntensityAt().applyAsDouble(3)));
  }

  @Test
  void rejectsSameJaggednessAtLowSignal() {
    final SlidingMzTraceApexResult result = evaluate(new double[]{0, 1, 10, 9, 9.8, 1, 0}, 3, 3,
        0.2).result();

    Assertions.assertAll(() -> Assertions.assertFalse(result.isAccepted()),
        () -> Assertions.assertEquals(ExpectedFragmentRejectionReason.INVALID_LOCAL_SLOPE,
            result.rejectionReason()));
  }

  @Test
  void rejectsDeepCentralValley() {
    final SlidingMzTraceApexResult result = evaluate(new double[]{0, 10, 1000, 400, 980, 10, 0}, 3,
        3, 5).result();

    Assertions.assertAll(() -> Assertions.assertFalse(result.isAccepted()),
        () -> Assertions.assertEquals(ExpectedFragmentRejectionReason.INVALID_LOCAL_SLOPE,
            result.rejectionReason()));
  }

  @Test
  void inspectsOnlyTheLocalIsolationWindow() {
    final int localStart = 100_000;
    final double[] localValues = {0, 0, 10, 1000, 900, 980, 10, 0, 0, 0, 0};
    final AtomicInteger accesses = new AtomicInteger();
    final IntToDoubleFunction intensityAt = index -> {
      accesses.incrementAndGet();
      final int localIndex = index - localStart + 2;
      if (localIndex < 0 || localIndex >= localValues.length) {
        throw new AssertionError("Finder accessed non-local index " + index);
      }
      return localValues[localIndex];
    };

    final SlidingMzTraceApexResult result = HighIntensityDespikedSlidingMzTraceApexFinder.evaluate(
        intensityAt, 1_000_000, localStart + 3, IndexRange.ofInclusive(localStart, localStart + 6),
        3, 5).result();
    Assertions.assertAll(() -> Assertions.assertTrue(result.isAccepted(), result.details()),
        () -> Assertions.assertTrue(accesses.get() < 200,
            "Local apex evaluation used %d intensity reads".formatted(accesses.get())));
  }

  private static @NotNull SlidingMzTraceApexEvaluation evaluate(
      final double @NotNull [] intensities, final int closestIsolationIndex,
      final int maxToleranceWindow, final double minimumFragmentIntensity) {
    return HighIntensityDespikedSlidingMzTraceApexFinder.evaluate(index -> intensities[index],
        intensities.length, closestIsolationIndex,
        IndexRange.ofInclusive(0, intensities.length - 1), maxToleranceWindow,
        minimumFragmentIntensity);
  }
}
