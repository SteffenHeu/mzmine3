/*
 * Copyright (c) 2004-2026 The mzmine Development Team
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

package io.github.mzmine.modules.visualization.dash_lipidqc.retention;

import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.LipidAnnotationLevel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.data.xy.AbstractXYDataset;

/**
 * JFreeChart XY dataset built from a predicate-filtered set of lipid-annotated feature list rows,
 * mapping a configurable y-value (e.g. ECN or DBE) against retention time.
 */
final class RetentionTrendDataset extends AbstractXYDataset {

  private final @NotNull double[] xValues;
  private final @NotNull double[] yValues;
  private final @NotNull MatchedLipid[] matchedLipids;
  private final @NotNull FeatureListRow[] rows;

  RetentionTrendDataset(final @NotNull List<FeatureListRow> rows,
      final @NotNull Predicate<MatchedLipid> predicate,
      final @NotNull ToDoubleFunction<MatchedLipid> yValueExtractor) {
    final List<Double> xList = new ArrayList<>();
    final List<Double> yList = new ArrayList<>();
    final List<MatchedLipid> lipidList = new ArrayList<>();
    final List<FeatureListRow> rowList = new ArrayList<>();
    for (final FeatureListRow row : rows) {
      final Float rowRt = row.getAverageRT();
      if (rowRt == null || !Float.isFinite(rowRt)) {
        continue;
      }
      final List<MatchedLipid> rowMatches = row.getLipidMatches();
      if (rowMatches.isEmpty()) {
        continue;
      }
      final Set<Double> addedYValues = new HashSet<>();
      for (final MatchedLipid match : rowMatches) {
        if (!predicate.test(match)) {
          continue;
        }
        final double y = yValueExtractor.applyAsDouble(match);
        if (!Double.isFinite(y) || !addedYValues.add(y)) {
          continue;
        }
        lipidList.add(match);
        rowList.add(row);
        xList.add(rowRt.doubleValue());
        yList.add(y);
      }
    }
    xValues = xList.stream().mapToDouble(Double::doubleValue).toArray();
    yValues = yList.stream().mapToDouble(Double::doubleValue).toArray();
    matchedLipids = lipidList.toArray(new MatchedLipid[0]);
    this.rows = rowList.toArray(new FeatureListRow[0]);
  }

  @Override
  public int getSeriesCount() {
    return 1;
  }

  @Override
  public @NotNull Comparable<?> getSeriesKey(final int series) {
    return "Retention trend";
  }

  @Override
  public int getItemCount(final int series) {
    return xValues.length;
  }

  @Override
  public @NotNull Number getX(final int series, final int item) {
    return xValues[item];
  }

  @Override
  public @NotNull Number getY(final int series, final int item) {
    return yValues[item];
  }

  @NotNull double[] getXValues() {
    return xValues;
  }

  @Nullable MatchedLipid getMatchedLipid(final int item) {
    return item >= 0 && item < matchedLipids.length ? matchedLipids[item] : null;
  }

  @Nullable FeatureListRow getRow(final int item) {
    return item >= 0 && item < rows.length ? rows[item] : null;
  }

  @Nullable String getTooltip(final int item, final @NotNull LipidAnnotationLevel level) {
    final MatchedLipid lipid = getMatchedLipid(item);
    return lipid == null ? null : lipid.getLipidAnnotation().getAnnotation(level);
  }

  @Nullable String getLabel(final int item, final @NotNull LipidAnnotationLevel level) {
    final MatchedLipid lipid = getMatchedLipid(item);
    if (lipid == null) {
      return null;
    }
    final double yValue = yValues[item];
    for (int i = 0; i < yValues.length; i++) {
      if (i == item || yValues[i] != yValue) {
        continue;
      }
      final MatchedLipid other = getMatchedLipid(i);
      final double score = lipid.getMsMsScore() == null ? 0d : lipid.getMsMsScore();
      final double otherScore = other == null || other.getMsMsScore() == null ? 0d
          : other.getMsMsScore();
      if (score < otherScore) {
        return null;
      }
    }
    return lipid.getLipidAnnotation().getAnnotation(level);
  }
}
