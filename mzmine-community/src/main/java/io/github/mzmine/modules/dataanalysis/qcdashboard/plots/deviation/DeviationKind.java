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

package io.github.mzmine.modules.dataanalysis.qcdashboard.plots.deviation;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.impl.SimpleScan;
import io.github.mzmine.main.MZmineCore;
import java.text.NumberFormat;
import org.jetbrains.annotations.NotNull;

/**
 * The two deviation plots of the QC dashboard (Plots 5 &amp; 6): a feature's m/z or RT in each file
 * minus the row average. Each kind carries its chart labels, the GUI number format from the config,
 * and the value function.
 */
public enum DeviationKind {

  MZ("m/z deviation of selected feature", "Δ m/z"),
  RT("RT deviation of selected feature", "Δ RT");

  private final String title;
  private final String rangeAxisLabel;

  DeviationKind(String title, String rangeAxisLabel) {
    this.title = title;
    this.rangeAxisLabel = rangeAxisLabel;
  }

  public String title() {
    return title;
  }

  public String rangeAxisLabel() {
    return rangeAxisLabel;
  }

  /**
   * @return the GUI number format from the config (m/z or RT).
   */
  public NumberFormat numberFormat() {
    return switch (this) {
      case MZ -> MZmineCore.getConfiguration().getMZFormat();
      case RT -> MZmineCore.getConfiguration().getRTFormat();
    };
  }

  /**
   * @return the (signed) deviation of the feature's value in {@code file} from the row average, or
   * {@link Double#NaN} if not available.
   */
  public double deviation(final @NotNull FeatureListRow row, final @NotNull RawDataFile file) {
    final Feature f = row.getFeature(file);
    if (f == null) {
      return Double.NaN;
    }
    return switch (this) {
      case MZ -> {
        final Double mz = f.getMZ();
        final Double avg = row.getAverageMZ();
        yield mz == null || avg == null ? Double.NaN : mz - avg;
      }
      case RT -> rtDeviation(row, f, false);
    };
  }

  /**
   * @return the deviation based on the corrected retention time of the representative scan, or
   * {@link Double#NaN} for m/z deviations and unavailable corrected retention times
   */
  public double correctedDeviation(final @NotNull FeatureListRow row,
      final @NotNull RawDataFile file) {
    return switch (this) {
      case MZ -> Double.NaN;
      case RT -> {
        final Feature feature = row.getFeature(file);
        yield feature == null ? Double.NaN : rtDeviation(row, feature, true);
      }
    };
  }

  private static double rtDeviation(final @NotNull FeatureListRow row,
      final @NotNull Feature feature, final boolean corrected) {
    final Float averageRt = row.getAverageRT();
    if (averageRt == null || !(feature.getRepresentativeScan() instanceof SimpleScan scan)) {
      return Double.NaN;
    }
    if (!corrected) {
      return scan.getUncorrectedRetentionTime() - averageRt;
    }
    final Float correctedRt = scan.getCorrectedRetentionTime();
    return correctedRt == null ? Double.NaN : correctedRt - averageRt;
  }
}
