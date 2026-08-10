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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SlidingMzTraceApexFinderTest {

  @Test
  void retainsCenteredApex() {
    final SlidingMzTraceApexResult result = findApex(new double[]{0, 1, 3, 7, 3, 1, 0}, 3, 3);

    assertAcceptedAt(result, 3);
  }

  @Test
  void findsApexLeftOfIsolationCenter() {
    final SlidingMzTraceApexResult result = findApex(new double[]{0, 2, 8, 5, 3, 1, 0}, 4, 3);

    assertAcceptedAt(result, 2);
  }

  @Test
  void findsApexRightOfIsolationCenter() {
    final SlidingMzTraceApexResult result = findApex(new double[]{0, 1, 3, 5, 8, 2, 0}, 2, 3);

    assertAcceptedAt(result, 4);
  }

  @Test
  void retainsCenterPointOfFlatTopContainingIsolationCenter() {
    final SlidingMzTraceApexResult result = findApex(new double[]{0, 2, 8, 8, 8, 3, 0}, 3, 3);

    assertAcceptedAt(result, 3);
  }

  @Test
  void selectsClosestPointOfShiftedFlatTop() {
    final SlidingMzTraceApexResult result = findApex(new double[]{0, 1, 3, 7, 7, 7, 2, 0}, 2, 4);

    assertAcceptedAt(result, 3);
  }

  @Test
  void rejectsLocalMinimum() {
    final SlidingMzTraceApexResult result = findApex(new double[]{0, 5, 1, 5, 0}, 2, 2);

    assertRejectedAs(result, ExpectedFragmentRejectionReason.INVALID_LOCAL_SLOPE);
  }

  @Test
  void rejectsTraceWithoutBoundedMaximum() {
    final SlidingMzTraceApexResult result = findApex(new double[]{5, 5, 5, 5, 5}, 2, 2);

    assertRejectedAs(result, ExpectedFragmentRejectionReason.INVALID_LOCAL_SLOPE);
  }

  @Test
  void rejectsMaximumAtIsolationRangeEdge() {
    final double[] intensities = {0, 1, 2, 3, 4};
    final SlidingMzTraceApexResult result = SlidingMzTraceApexFinder.findApex(
        index -> intensities[index], intensities.length, 2, IndexRange.ofInclusive(0, 3), 3);

    assertRejectedAs(result, ExpectedFragmentRejectionReason.ISOLATION_RANGE_EDGE);
  }

  @Test
  void rejectsMaximumOutsideTolerance() {
    final SlidingMzTraceApexResult result = findApex(new double[]{0, 1, 2, 3, 5, 3, 0}, 1, 2);

    assertRejectedAs(result, ExpectedFragmentRejectionReason.APEX_OUTSIDE_TOLERANCE);
  }

  private static @NotNull SlidingMzTraceApexResult findApex(final double @NotNull [] intensities,
      final int closestIsolationIndex, final int maxToleranceWindow) {
    return SlidingMzTraceApexFinder.findApex(index -> intensities[index], intensities.length,
        closestIsolationIndex, IndexRange.ofInclusive(0, intensities.length - 1),
        maxToleranceWindow);
  }

  private static void assertAcceptedAt(@NotNull final SlidingMzTraceApexResult result,
      final int expectedApexIndex) {
    Assertions.assertAll(() -> Assertions.assertTrue(result.isAccepted(), result.details()),
        () -> Assertions.assertNull(result.rejectionReason()),
        () -> Assertions.assertEquals(expectedApexIndex, result.apexIndex()));
  }

  private static void assertRejectedAs(@NotNull final SlidingMzTraceApexResult result,
      @NotNull final ExpectedFragmentRejectionReason expectedReason) {
    Assertions.assertAll(() -> Assertions.assertFalse(result.isAccepted()),
        () -> Assertions.assertEquals(-1, result.apexIndex()),
        () -> Assertions.assertEquals(expectedReason, result.rejectionReason()));
  }
}
