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

import org.jetbrains.annotations.NotNull;

public enum SlidingMzShapeAcceptanceMode {
  ZERO_BOUNDED("Zero-bounded consecutive trace"), TOP_TO_MAX_EDGE("Top / higher isolation edge");

  private final @NotNull String label;

  SlidingMzShapeAcceptanceMode(@NotNull final String label) {
    this.label = label;
  }

  boolean accepts(final boolean topEdgeAccepted, final boolean zeroBoundedAccepted) {
    return switch (this) {
      case ZERO_BOUNDED -> zeroBoundedAccepted;
      case TOP_TO_MAX_EDGE -> topEdgeAccepted;
    };
  }

  boolean usesTopEdgeRatio() {
    return switch (this) {
      case TOP_TO_MAX_EDGE -> true;
      case ZERO_BOUNDED -> false;
    };
  }

  @Override
  public @NotNull String toString() {
    return label;
  }
}
