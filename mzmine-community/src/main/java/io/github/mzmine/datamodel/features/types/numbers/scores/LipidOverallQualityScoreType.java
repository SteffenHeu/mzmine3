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

package io.github.mzmine.datamodel.features.types.numbers.scores;


import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.datamodel.features.types.modifiers.SubColumnsFactory;
import io.github.mzmine.datamodel.features.types.numbers.abstr.ScoreType;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import java.util.List;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.TreeTableColumn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Feature data type that displays the overall lipid annotation quality score stored in
 * {@link MatchedLipid}. The score is computed once by {@code LipidAnnotationTask} after all
 * annotations are complete and persisted with the project.
 */
public class LipidOverallQualityScoreType extends ScoreType {

  @Override
  public @NotNull String getUniqueID() {
    return "lipid_overall_quality_score";
  }

  @Override
  public @NotNull String getHeaderString() {
    return "Overall quality score";
  }

  @Override
  public @Nullable TreeTableColumn<ModularFeatureListRow, Object> createColumn(
      final @Nullable RawDataFile raw, final @Nullable SubColumnsFactory parentType,
      final int subColumnIndex) {
    final TreeTableColumn<ModularFeatureListRow, Object> column = DataType.createStandardColumn(
        this, raw, parentType, subColumnIndex);
    column.setCellValueFactory(cdf -> {
      final ModularFeatureListRow row = cdf.getValue().getValue();
      final MatchedLipid match = resolvePrimaryMatch(row, parentType);
      if (match == null) {
        return null;
      }
      final Float score = match.getOverallQualityScore();
      return score == null ? null : new ReadOnlyObjectWrapper<>(score);
    });
    return column;
  }

  @Override
  public double getPrefColumnWidth() {
    return 120d;
  }

  private static @Nullable MatchedLipid resolvePrimaryMatch(final @NotNull ModularFeatureListRow row,
      final @Nullable SubColumnsFactory parentType) {
    if (parentType instanceof DataType<?> parentDataType) {
      final Object value = row.get(parentDataType);
      if (value instanceof List<?> matches && !matches.isEmpty()) {
        final Object first = matches.getFirst();
        if (first instanceof MatchedLipid match) {
          return match;
        }
      }
    }

    final List<MatchedLipid> lipidMatches = row.getLipidMatches();
    return lipidMatches.isEmpty() ? null : lipidMatches.getFirst();
  }
}
