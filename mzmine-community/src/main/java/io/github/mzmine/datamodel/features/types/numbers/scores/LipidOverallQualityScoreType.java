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

import static io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils.clampToUnit;
import static io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils.computeCombinedAnnotationScore;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.FeatureList.FeatureListAppliedMethod;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.datamodel.features.types.modifiers.SubColumnsFactory;
import io.github.mzmine.datamodel.features.types.numbers.abstr.PercentType;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnalysisType;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnnotationModule;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnnotationParameters;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidQcWeightParameters;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils.ComponentWeights;
import java.util.List;
import java.util.Optional;
import java.util.WeakHashMap;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableColumn.CellDataFeatures;
import javafx.util.Callback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Feature data type that computes and displays the overall lipid annotation quality score,
 * combining MS1 accuracy, MS2 fragmentation, elution order, and interference metrics into a single
 * normalised percentage value.
 */
public class LipidOverallQualityScoreType extends PercentType {

  private static final WeakHashMap<ModularFeatureList, ContextCacheEntry> CONTEXT_CACHE = new WeakHashMap<>();

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
    column.setCellValueFactory(
        new Callback<CellDataFeatures<ModularFeatureListRow, Object>, ObservableValue<Object>>() {
          @Override
          public ObservableValue<Object> call(
              final CellDataFeatures<ModularFeatureListRow, Object> cellDataFeatures) {
            final ModularFeatureListRow row = cellDataFeatures.getValue().getValue();
            final MatchedLipid match = resolvePrimaryMatch(row, parentType);
            if (match == null) {
              return null;
            }
            final Float score = computeOverallScore(row, match);
            return score == null ? null : new ReadOnlyObjectWrapper<>(score);
          }
        });
    return column;
  }

  @Override
  public boolean getDefaultVisibility() {
    return false;
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

  private static @Nullable Float computeOverallScore(final @NotNull ModularFeatureListRow row,
      final @NotNull MatchedLipid match) {
    if (row.getFeatureList() instanceof ModularFeatureList featureList) {
      final ScoringContext scoringContext = resolveScoringContext(featureList, match);
      final double score = computeCombinedAnnotationScore(featureList, row, match,
          scoringContext.includeMs2Score(), scoringContext.includeElutionOrderScore(),
          scoringContext.analysisType(), scoringContext.componentWeights());
      return (float) clampToUnit(score);
    }

    final Double ms2Score = match.getMsMsScore();
    return (float) clampToUnit(ms2Score == null ? 0d : ms2Score);
  }

  private static @NotNull ScoringContext resolveScoringContext(
      final @NotNull ModularFeatureList featureList, final @NotNull MatchedLipid match) {
    final int methodCount = featureList.getAppliedMethods().size();
    synchronized (CONTEXT_CACHE) {
      final ContextCacheEntry cached = CONTEXT_CACHE.get(featureList);
      if (cached != null && cached.appliedMethodCount() == methodCount) {
        return cached.context();
      }
    }

    final ScoringContext context = detectAnnotationContext(featureList).orElseGet(
        () -> fallbackContext(featureList, match));
    synchronized (CONTEXT_CACHE) {
      CONTEXT_CACHE.put(featureList, new ContextCacheEntry(methodCount, context));
    }
    return context;
  }

  private static @NotNull Optional<ScoringContext> detectAnnotationContext(
      final @NotNull ModularFeatureList featureList) {
    final List<FeatureListAppliedMethod> methods = featureList.getAppliedMethods();
    for (int i = methods.size() - 1; i >= 0; i--) {
      final FeatureListAppliedMethod method = methods.get(i);
      if (!(method.getModule() instanceof LipidAnnotationModule)) {
        continue;
      }
      try {
        final Boolean useMs2 = method.getParameters()
            .getParameter(LipidAnnotationParameters.searchForMSMSFragments).getValue();
        final LipidAnalysisType analysisType = method.getParameters()
            .getParameter(LipidAnnotationParameters.lipidAnalysisType).getValue();
        final boolean includeElutionOrderScore =
            analysisType == null || analysisType.hasRetentionTimePattern();
        final @Nullable ComponentWeights componentWeights =
            method.getParameters().getParameter(LipidAnnotationParameters.customQcWeights).getValue()
                ? LipidQcWeightParameters.toComponentWeights(
                    method.getParameters().getParameter(LipidAnnotationParameters.customQcWeights)
                        .getEmbeddedParameters(), analysisType)
                : null;
        return Optional.of(
            new ScoringContext(Boolean.TRUE.equals(useMs2), includeElutionOrderScore,
                analysisType, componentWeights));
      } catch (RuntimeException ignored) {
        // fall through to fallback scoring if older parameter versions are loaded
      }
    }
    return Optional.empty();
  }

  private static @NotNull ScoringContext fallbackContext(
      final @NotNull ModularFeatureList featureList, final @NotNull MatchedLipid match) {
    final boolean includeMs2Score =
        match.getMatchedFragments() != null && !match.getMatchedFragments().isEmpty();
    return new ScoringContext(includeMs2Score, hasRetentionTimePattern(featureList), null, null);
  }

  private static boolean hasRetentionTimePattern(final @NotNull ModularFeatureList featureList) {
    int rtCount = 0;
    for (final var row : featureList.getRows()) {
      final Float rt = row.getAverageRT();
      if (rt != null && Float.isFinite(rt)) {
        rtCount++;
        if (rtCount >= 3) {
          return true;
        }
      }
    }
    return false;
  }

  private record ScoringContext(boolean includeMs2Score, boolean includeElutionOrderScore,
                                @Nullable LipidAnalysisType analysisType,
                                @Nullable ComponentWeights componentWeights) {

  }

  private record ContextCacheEntry(int appliedMethodCount, @NotNull ScoringContext context) {

  }
}
