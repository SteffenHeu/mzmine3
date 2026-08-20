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

package tsfimportest;

import io.github.mzmine.modules.io.import_rawdata_bruker_tsf.TSFUtils;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TSFUtils#filterZeroIntensities} - the pure zero-run deletion used when
 * loading Bruker TSF profile spectra. Kept native-free so it runs on every platform.
 */
class TSFUtilsTest {

  /**
   * @param size number of index bins
   * @return an index array [0, 1, 2, ...] mirroring the {@code profileIndexArray} used in
   * production
   */
  private static double[] indexArray(final int size) {
    final double[] array = new double[size];
    for (int i = 0; i < size; i++) {
      array[i] = i;
    }
    return array;
  }

  @Test
  void emptySpectrumReturnsZero() {
    final double[] outMzs = new double[4];
    final double[] outIntensities = new double[4];

    final int numValues = TSFUtils.filterZeroIntensities(indexArray(4), new long[4], 0, outMzs,
        outIntensities);

    Assertions.assertEquals(0, numValues);
  }

  @Test
  void allZeroSpectrumKeepsNothing() {
    final double[] outMzs = new double[4];
    final double[] outIntensities = new double[4];

    final int numValues = TSFUtils.filterZeroIntensities(indexArray(4), new long[]{0, 0, 0, 0}, 4,
        outMzs, outIntensities);

    // nothing kept -> not even a trailing conversion boundary is appended
    Assertions.assertEquals(0, numValues);
  }

  @Test
  void peakWithShouldersIsKept() {
    final double[] outMzs = new double[5];
    final double[] outIntensities = new double[5];

    final int numValues = TSFUtils.filterZeroIntensities(indexArray(5), new long[]{0, 10, 0, 20, 5},
        5, outMzs, outIntensities);

    // kept indices 1..4 plus a trailing boundary that repeats the last index with intensity 0
    Assertions.assertEquals(5, numValues);
    Assertions.assertArrayEquals(new double[]{1, 2, 3, 4, 4}, Arrays.copyOf(outMzs, numValues));
    Assertions.assertArrayEquals(new double[]{10, 0, 20, 5, 0},
        Arrays.copyOf(outIntensities, numValues));
  }

  /**
   * Regression test for the off-by-one that dropped the last real data point: a non-zero value in
   * the very last bin must survive filtering.
   */
  @Test
  void lastDataPointIsNotDropped() {
    final double[] outMzs = new double[4];
    final double[] outIntensities = new double[4];

    final int numValues = TSFUtils.filterZeroIntensities(indexArray(4), new long[]{0, 0, 0, 7}, 4,
        outMzs, outIntensities);

    // index 2 kept as the peak's left shoulder, index 3 (the last bin) kept with its real
    // intensity, then the trailing zero boundary
    Assertions.assertEquals(3, numValues);
    Assertions.assertArrayEquals(new double[]{2, 3, 3}, Arrays.copyOf(outMzs, numValues));
    Assertions.assertArrayEquals(new double[]{0, 7, 0}, Arrays.copyOf(outIntensities, numValues));
    // the last real intensity must be present in the output
    Assertions.assertTrue(
        Arrays.stream(Arrays.copyOf(outIntensities, numValues)).anyMatch(v -> v == 7d));
  }

  @Test
  void isolatedPeakKeepsBothShoulders() {
    final double[] outMzs = new double[5];
    final double[] outIntensities = new double[5];

    final int numValues = TSFUtils.filterZeroIntensities(indexArray(5), new long[]{0, 0, 5, 0, 0},
        5, outMzs, outIntensities);

    Assertions.assertEquals(4, numValues);
    Assertions.assertArrayEquals(new double[]{1, 2, 3, 4}, Arrays.copyOf(outMzs, numValues));
    Assertions.assertArrayEquals(new double[]{0, 5, 0, 0},
        Arrays.copyOf(outIntensities, numValues));
  }

  /**
   * Only the first {@code numDataPoints} entries may be read - stale data left over in a reused
   * buffer beyond that boundary must be ignored.
   */
  @Test
  void staleDataBeyondNumDataPointsIsIgnored() {
    final double[] outMzs = new double[5];
    final double[] outIntensities = new double[5];
    // indices 3 and 4 hold stale non-zero values from a hypothetical previous, larger scan
    final long[] intensities = new long[]{0, 10, 0, 999, 999};

    final int numValues = TSFUtils.filterZeroIntensities(indexArray(5), intensities, 3, outMzs,
        outIntensities);

    // index 1 (=10) and index 2 (left neighbour of nothing, kept because its predecessor is > 0),
    // then the boundary at the last valid index (2). The stale 999s are never read.
    Assertions.assertEquals(3, numValues);
    Assertions.assertArrayEquals(new double[]{1, 2, 2}, Arrays.copyOf(outMzs, numValues));
    Assertions.assertArrayEquals(new double[]{10, 0, 0}, Arrays.copyOf(outIntensities, numValues));
  }
}
