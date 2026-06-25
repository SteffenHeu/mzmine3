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

package io.github.mzmine.modules.dataanalysis.qcdashboard.plots.perfile;

import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.main.MZmineCore;
import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The two per-file aggregate plots of the QC dashboard: number of detected features per file
 * (Plot 2) and summed feature intensity per file (Plot 3). Each kind carries its chart labels,
 * tooltip number format and the per-file aggregation.
 */
public enum FileAggregateKind {

  FEATURE_COUNT("Feature count / file", "Features"),
  SUM_INTENSITY("Summed intensity / file", "Σ intensity");

  private final String title;
  private final String rangeAxisLabel;

  FileAggregateKind(String title, String rangeAxisLabel) {
    this.title = title;
    this.rangeAxisLabel = rangeAxisLabel;
  }

  public String title() {
    return title;
  }

  public String rangeAxisLabel() {
    return rangeAxisLabel;
  }

  public NumberFormat numberFormat() {
    return switch (this) {
      case FEATURE_COUNT -> NumberFormat.getIntegerInstance();
      case SUM_INTENSITY -> MZmineCore.getConfiguration().getIntensityFormat();
    };
  }

  /**
   * Aggregates a value per file (number of detected features, or summed intensity).
   *
   * @param abundance only used for {@link #SUM_INTENSITY}
   */
  public Map<RawDataFile, Double> computePerFile(ModularFeatureList flist,
      List<RawDataFile> orderedFiles, AbundanceMeasure abundance) {
    final Map<RawDataFile, Double> result = new LinkedHashMap<>();
    for (RawDataFile file : orderedFiles) {
      final List<ModularFeature> features = flist.getFeatures(file);
      final double value = switch (this) {
        case FEATURE_COUNT -> features.size();
        case SUM_INTENSITY -> {
          double sum = 0;
          for (ModularFeature f : features) {
            final float a = abundance.getOrNaN(f);
            if (!Float.isNaN(a)) {
              sum += a;
            }
          }
          yield sum;
        }
      };
      result.put(file, value);
    }
    return result;
  }
}
