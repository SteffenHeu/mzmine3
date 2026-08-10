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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SlidingMzShapeAcceptanceTest {

  private static final double[] ISOLATION_CENTERS = {0d, 1d, 2d, 3d, 4d, 5d, 6d};

  @Test
  void diaTaskUsesValidatedProductionDefaults() {
    Assertions.assertAll(() -> Assertions.assertEquals(SlidingMzShapeAcceptanceMode.TOP_TO_MAX_EDGE,
            DiaSlidingMzTask.SHAPE_ACCEPTANCE_MODE),
        () -> Assertions.assertEquals(2d, DiaSlidingMzTask.SHAPE_EDGE_WINDOW_MULTIPLIER),
        () -> Assertions.assertEquals(2d, DiaSlidingMzTask.MINIMUM_SHAPE_TOP_EDGE_RATIO),
        () -> Assertions.assertEquals(5, DiaSlidingMzTask.MINIMUM_SHAPE_CONSECUTIVE_POINTS));
  }

  @Test
  void maximumEdgeReferenceUsesHigherEdge() {
    final IonTimeSeries<?> series = series(0d, 5d, 6d, 10d, 7d, 1d, 0d);

    final SlidingMzShapeAcceptanceResult maximumEdge = evaluate(
        SlidingMzShapeAcceptanceMode.TOP_TO_MAX_EDGE, series, 3d, 5);

    Assertions.assertAll(() -> Assertions.assertFalse(maximumEdge.accepted()),
        () -> Assertions.assertEquals(2d, maximumEdge.topEdgeRatio(), 1e-12));
  }

  @Test
  void topEdgeRatioRequiresMinimumConsecutivePoints() {
    final IonTimeSeries<?> series = series(0d, 0d, 2d, 10d, 2d, 0d, 0d);

    final SlidingMzShapeAcceptanceResult result = evaluate(
        SlidingMzShapeAcceptanceMode.TOP_TO_MAX_EDGE, series, 3d, 5);

    Assertions.assertAll(() -> Assertions.assertFalse(result.accepted()),
        () -> Assertions.assertEquals(3, result.consecutivePositivePoints()),
        () -> Assertions.assertTrue(result.topEdgeRatio() >= 3d));
  }

  @Test
  void topIndexIsSelectedInsideIsolationWindow() {
    final IonTimeSeries<?> series = series(0d, 2d, 4d, 5d, 12d, 2d, 0d);

    final SlidingMzShapeAcceptanceResult result = evaluate(
        SlidingMzShapeAcceptanceMode.TOP_TO_MAX_EDGE, series, 2d, 5);

    Assertions.assertAll(() -> Assertions.assertTrue(result.accepted()),
        () -> Assertions.assertEquals(4, result.preferredApexIndex()),
        () -> Assertions.assertEquals(6d, result.topEdgeRatio(), 1e-12));
  }

  @Test
  void zeroBoundedModeRemainsAvailable() {
    final IonTimeSeries<?> series = series(0d, 2d, 4d, 10d, 4d, 2d, 0d);

    final SlidingMzShapeAcceptanceResult result = SlidingMzShapeAcceptance.evaluate(
        SlidingMzShapeAcceptanceMode.ZERO_BOUNDED, series, ISOLATION_CENTERS, 3, 3, 2d, 1d, 2d, 5,
        2);

    Assertions.assertAll(() -> Assertions.assertTrue(result.accepted()),
        () -> Assertions.assertTrue(result.zeroBoundedAccepted()),
        () -> Assertions.assertEquals(3, result.preferredApexIndex()));
  }

  private static @NotNull SlidingMzShapeAcceptanceResult evaluate(
      @NotNull final SlidingMzShapeAcceptanceMode mode, @NotNull final IonTimeSeries<?> series,
      final double minimumRatio, final int minimumConsecutivePoints) {
    return SlidingMzShapeAcceptance.evaluate(mode, series, ISOLATION_CENTERS, 3, 3, 4d, 1d,
        minimumRatio, minimumConsecutivePoints, 2);
  }

  @Test
  void expandedEdgeWindowKeepsTopInsideOriginalIsolationWindow() {
    final IonTimeSeries<?> series = series(0d, 5d, 6d, 10d, 7d, 1d, 20d);

    final SlidingMzShapeAcceptanceResult result = SlidingMzShapeAcceptance.evaluate(
        SlidingMzShapeAcceptanceMode.TOP_TO_MAX_EDGE, series, ISOLATION_CENTERS, 3, 3, 2d, 2d, 1.5d,
        5, 2);

    Assertions.assertAll(() -> Assertions.assertTrue(result.accepted()),
        () -> Assertions.assertEquals(3, result.preferredApexIndex()),
        () -> Assertions.assertEquals(2d, result.topEdgeRatio(), 1e-12));
  }

  @SuppressWarnings("unchecked")
  private static @NotNull IonTimeSeries<?> series(final double... intensities) {
    final IonTimeSeries<?> series = Mockito.mock(IonTimeSeries.class);
    Mockito.when(series.getNumberOfValues()).thenReturn(intensities.length);
    Mockito.when(series.getIntensity(Mockito.anyInt()))
        .thenAnswer(invocation -> intensities[invocation.getArgument(0)]);
    return series;
  }
}
