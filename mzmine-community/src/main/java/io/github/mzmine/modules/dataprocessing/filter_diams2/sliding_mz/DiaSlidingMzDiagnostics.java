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
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only state used to annotate sliding-m/z decisions with expected library fragments.
 */
public final class DiaSlidingMzDiagnostics {

  public static final List<ExpectedSpectrum> EXPECTED_SPECTRA = new CopyOnWriteArrayList<>();
  public static final Map<DiaCorrelationOptions, String> CONFIGURATION_LABELS = Collections.synchronizedMap(
      new EnumMap<>(DiaCorrelationOptions.class));
  public static volatile boolean LOG_SHAPE_METRICS = false;

  private DiaSlidingMzDiagnostics() {
  }

  public static void reset() {
    EXPECTED_SPECTRA.clear();
    CONFIGURATION_LABELS.clear();
    LOG_SHAPE_METRICS = false;
  }
}
