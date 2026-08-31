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

package io.github.mzmine.modules.tools.tools_autoparam.optimizer;

import io.github.mzmine.modules.tools.tools_autoparam.optimizer.metrics.BenchmarkTargetCount;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.metrics.SweepMetric;
import java.util.List;

final class OptimizationMetrics {

  static final List<SweepMetric> ALL = List.of(SweepMetric.IPO_ISOTOPE_SCORE,
      SweepMetric.SLAW_INTEGRATION_SCORE, SweepMetric.YASIN_ISOTOPE_SCORE,
      SweepMetric.DOUBLE_PEAK_RATIO, SweepMetric.FILL_RATIO, SweepMetric.GC_EI_FRAGMENT_QUALITY,
      new BenchmarkTargetCount(List.of()));

  static final List<SweepMetric> DEFAULT = List.of(SweepMetric.YASIN_ISOTOPE_SCORE);

  private OptimizationMetrics() {
  }
}
