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

import io.github.mzmine.modules.dataprocessing.filter_diams2.DiaCorrelationOptions;
import org.jetbrains.annotations.NotNull;

record DiaSlidingMzSweepConfig(@NotNull String name, @NotNull DiaCorrelationOptions diaAlgorithm,
                               @NotNull DiaCorrelationOptions slidingMzPregrouping,
                               double ms2NoiseLevel, double minimumFragmentIntensity) {

  DiaSlidingMzSweepConfig {
    if (diaAlgorithm == DiaCorrelationOptions.SLIDING_MZ
        && slidingMzPregrouping == DiaCorrelationOptions.SLIDING_MZ) {
      throw new IllegalArgumentException(
          "Sliding m/z cannot be used to pre-group another sliding m/z run.");
    }
  }

  boolean usesSlidingMz() {
    return diaAlgorithm == DiaCorrelationOptions.SLIDING_MZ;
  }

  @NotNull String exportFileStem() {
    return switch (diaAlgorithm) {
      case NO_CORRELATION -> "dia_no_correlation";
      case RT_CORRELATION -> "dia_rt_correlation";
      case SLIDING_MZ -> switch (slidingMzPregrouping) {
        case NO_CORRELATION -> "dia_sliding_mz_no_correlation";
        case RT_CORRELATION -> "dia_sliding_mz_rt_correlation";
        case SLIDING_MZ -> throw new IllegalStateException(
            "Sliding m/z pre-grouping was rejected by the constructor.");
      };
    };
  }
}
