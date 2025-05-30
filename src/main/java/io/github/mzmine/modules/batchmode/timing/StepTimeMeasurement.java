/*
 * Copyright (c) 2004-2024 The MZmine Development Team
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

package io.github.mzmine.modules.batchmode.timing;

import java.time.Duration;
import org.jetbrains.annotations.Nullable;

public record StepTimeMeasurement(int step, double secondsToFinish, String name,
                                  @Nullable String usedHeapGB) {

  /**
   * @param trackMemory memory measurements are only precise when combined with GC before, if active
   *                    - please perform gc before this constructor
   */
  public StepTimeMeasurement(final int stepNumber, final String name, final Duration duration,
      final boolean trackMemory) {
    this(stepNumber, duration.toMillis() / 1000.0, name,
        trackMemory ? "%.2f".formatted(getUsedMemoryGB()) : null);
  }

  @Override
  public String toString() {
    String heap = usedHeapGB == null ? "" : "%s".formatted(usedHeapGB);
    return "Step\t%d\t%s\t%.3f\t%s".formatted(step + 1, name, secondsToFinish,
        heap);
  }

  public static double getUsedMemoryGB() {
    final double GB = 1 << 30; // 1 GB
    final double totalMemGB = Runtime.getRuntime().totalMemory() / GB;
    return totalMemGB - Runtime.getRuntime().freeMemory() / GB;
  }
}
