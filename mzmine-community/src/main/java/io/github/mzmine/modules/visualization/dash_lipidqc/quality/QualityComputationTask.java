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

package io.github.mzmine.modules.visualization.dash_lipidqc.quality;

import static io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils.computeInterferenceMetrics;
import static io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils.computeInterferenceScore;
import static io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils.computeAdductWeight;
import static io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils.computeElutionOrderMetrics;
import static io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils.computeElutionOrderWeight;
import static io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils.computeWeightedQualityScore;
import static io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils.formatElutionOrderDetail;
import static io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils.interferenceDetail;

import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.javafx.mvci.FxUpdateTask;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.modules.visualization.dash_lipidqc.kendrick.KendrickFalseNegativeCandidate;
import io.github.mzmine.modules.visualization.dash_lipidqc.kendrick.KendrickFalseNegativeDetector;
import io.github.mzmine.modules.visualization.dash_lipidqc.kendrick.KendrickFalsePositiveUtils;
import io.github.mzmine.modules.visualization.dash_lipidqc.kendrick.KendrickReviewMode;
import io.github.mzmine.modules.visualization.dash_lipidqc.LipidQcAnnotationSelectionUtils;
import io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils.ElutionOrderMetrics;
import io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils.InterferenceMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class QualityComputationTask extends FxUpdateTask<AnnotationQualityPane> {

  private final @Nullable FeatureListRow row;
  private final @Nullable ModularFeatureList featureList;
  private final boolean includeRetentionTimeAnalysis;
  private final @NotNull KendrickReviewMode reviewMode;
  private @NotNull QualityComputationResult result;

  QualityComputationTask(final @NotNull AnnotationQualityPane pane,
      final @Nullable FeatureListRow row, final @Nullable ModularFeatureList featureList,
      final boolean includeRetentionTimeAnalysis, final @NotNull KendrickReviewMode reviewMode) {
    super("lipidqc-quality-update", pane);
    this.row = row;
    this.featureList = featureList;
    this.includeRetentionTimeAnalysis = includeRetentionTimeAnalysis;
    this.reviewMode = reviewMode;
    result = new QualityComputationResult("Select a row with lipid annotations.", null,
        new InterferenceMetrics(0, 0), List.of(), List.of(), null, null, null);
  }

  @Override
  protected void process() {
    final @Nullable String falsePositiveReason = row == null || featureList == null
        || reviewMode != KendrickReviewMode.POTENTIAL_FALSE_POSITIVE ? null
        : KendrickFalsePositiveUtils.potentialFalsePositiveReason(featureList, row,
            includeRetentionTimeAnalysis);
    final @Nullable KendrickFalseNegativeCandidate falseNegativeCandidate =
        row == null || featureList == null
            || reviewMode != KendrickReviewMode.POTENTIAL_FALSE_NEGATIVE ? null
            : new KendrickFalseNegativeDetector(featureList).detectCandidate(row);
    final @Nullable QualityCardData falseNegativeQualityCard =
        row == null || falseNegativeCandidate == null
            ? null : createQualityCardData(row, falseNegativeCandidate.match(),
            computeInterferenceMetrics(row));
    if (row == null) {
      result = new QualityComputationResult("Select a row with lipid annotations.", null,
          new InterferenceMetrics(0, 0), List.of(), List.of(), falsePositiveReason,
          falseNegativeCandidate, falseNegativeQualityCard);
      return;
    }
    final List<MatchedLipid> matches = row.getLipidMatches();
    if (matches.isEmpty()) {
      final InterferenceMetrics emptyRowInterference = computeInterferenceMetrics(row);
      final @Nullable String placeholder = falseNegativeQualityCard == null
          ? "No lipid annotations available for selected row." : null;
      result = new QualityComputationResult(placeholder, null, emptyRowInterference, List.of(),
          List.of(), falsePositiveReason, falseNegativeCandidate, falseNegativeQualityCard);
      return;
    }
    final List<MatchedLipid> matchSnapshot = List.copyOf(matches);
    final InterferenceMetrics interferenceMetrics = computeInterferenceMetrics(row);
    final MatchedLipid selectedAnnotation = java.util.Objects.requireNonNullElse(
        LipidQcAnnotationSelectionUtils.getPreferredLipidMatch(row), matchSnapshot.getFirst());
    final List<MatchedLipid> orderedMatches = new ArrayList<>(matchSnapshot.size());
    orderedMatches.add(selectedAnnotation);
    orderedMatches.addAll(
        matchSnapshot.stream().filter(match -> !Objects.equals(match, selectedAnnotation)).toList());
    final String annotationText = Objects.toString(
        selectedAnnotation.getLipidAnnotation().getAnnotation(), "");
    final List<FeatureListRow> duplicateRows = AnnotationQualityPane.findDuplicateRowsExcludingSelected(
        featureList, row.getID(), annotationText);
    final QualityMetric interference = new QualityMetric(
        computeInterferenceScore(interferenceMetrics.totalPenaltyCount()),
        interferenceDetail(interferenceMetrics));
    final List<QualityCardData> cards = new ArrayList<>(orderedMatches.size());
    for (final MatchedLipid match : orderedMatches) {
      cards.add(createQualityCardData(row, match, interferenceMetrics));
    }
    result = new QualityComputationResult(null, selectedAnnotation, interferenceMetrics,
        List.copyOf(duplicateRows), List.copyOf(cards), falsePositiveReason,
        falseNegativeCandidate, falseNegativeQualityCard);
  }

  @Override
  protected void updateGuiModel() {
    model.applyQualityResult(result);
  }

  @Override
  public @NotNull String getTaskDescription() {
    return "Calculating lipid annotation quality cards";
  }

  @Override
  public double getFinishedPercentage() {
    return 0d;
  }

  private @NotNull QualityCardData createQualityCardData(final @NotNull FeatureListRow row,
      final @NotNull MatchedLipid match, final @NotNull InterferenceMetrics interferenceMetrics) {
    final QualityMetric ms1 = AnnotationQualityPane.evaluateMs1(row, match);
    final QualityMetric ms2 = AnnotationQualityPane.evaluateMs2(match);
    final QualityMetric adduct = AnnotationQualityPane.evaluateAdduct(row, match);
    final QualityMetric isotope = AnnotationQualityPane.evaluateIsotope(row, match);
    final @Nullable ElutionOrderMetrics elutionMetrics = includeRetentionTimeAnalysis
        && featureList != null ? computeElutionOrderMetrics(featureList, row, match) : null;
    final QualityMetric elutionOrder = includeRetentionTimeAnalysis
        ? elutionMetrics != null ? new QualityMetric(elutionMetrics.combinedScore(),
            formatElutionOrderDetail(elutionMetrics))
            : new QualityMetric(0.4d, "Missing RT context")
        : new QualityMetric(0d, "Disabled for current lipid analysis mode");
    final QualityMetric interference = new QualityMetric(
        computeInterferenceScore(interferenceMetrics.totalPenaltyCount()),
        interferenceDetail(interferenceMetrics));
    final double adductWeight = computeAdductWeight(row, match);
    final double elutionOrderWeight = includeRetentionTimeAnalysis
        ? computeElutionOrderWeight(elutionMetrics)
        : 0d;
    final double overall = computeWeightedQualityScore(ms1.score(), ms2.score(), adduct.score(),
        isotope.score(), interference.score(), elutionOrder.score(), true,
        includeRetentionTimeAnalysis, adductWeight, elutionOrderWeight);
    return new QualityCardData(match, ms1, ms2, adduct, isotope, elutionOrder, interference,
        overall);
  }
}
