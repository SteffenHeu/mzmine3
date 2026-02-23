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

import static io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.computeInterferenceMetrics;
import static io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.computeInterferenceScore;
import static io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.computeWeightedQualityScore;
import static io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.interferenceDetail;

import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.javafx.mvci.FxUpdateTask;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.InterferenceMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class QualityComputationTask extends FxUpdateTask<AnnotationQualityPane> {

  private final @Nullable FeatureListRow row;
  private final @Nullable ModularFeatureList featureList;
  private final boolean includeRetentionTimeAnalysis;
  private @NotNull QualityComputationResult result;

  QualityComputationTask(final @NotNull AnnotationQualityPane pane,
      final @Nullable FeatureListRow row, final @Nullable ModularFeatureList featureList,
      final boolean includeRetentionTimeAnalysis) {
    super("lipidqc-quality-update", pane);
    this.row = row;
    this.featureList = featureList;
    this.includeRetentionTimeAnalysis = includeRetentionTimeAnalysis;
    result = new QualityComputationResult("Select a row with lipid annotations.", null,
        new InterferenceMetrics(0, 0), List.of(), List.of());
  }

  @Override
  protected void process() {
    if (row == null) {
      result = new QualityComputationResult("Select a row with lipid annotations.", null,
          new InterferenceMetrics(0, 0), List.of(), List.of());
      return;
    }
    final List<MatchedLipid> matches = row.getLipidMatches();
    if (matches.isEmpty()) {
      result = new QualityComputationResult("No lipid annotations available for selected row.",
          null, new InterferenceMetrics(0, 0), List.of(), List.of());
      return;
    }
    final List<MatchedLipid> matchSnapshot = List.copyOf(matches);
    final InterferenceMetrics interferenceMetrics = computeInterferenceMetrics(row);
    final MatchedLipid selectedAnnotation = matchSnapshot.getFirst();
    final String annotationText = Objects.toString(
        selectedAnnotation.getLipidAnnotation().getAnnotation(), "");
    final List<FeatureListRow> duplicateRows = AnnotationQualityPane.findDuplicateRowsExcludingSelected(
        featureList, row.getID(), annotationText);
    final QualityMetric interference = new QualityMetric(
        computeInterferenceScore(interferenceMetrics.totalPenaltyCount()),
        interferenceDetail(interferenceMetrics));
    final List<QualityCardData> cards = new ArrayList<>(matchSnapshot.size());
    for (final MatchedLipid match : matchSnapshot) {
      final QualityMetric ms1 = AnnotationQualityPane.evaluateMs1(row, match);
      final QualityMetric ms2 = AnnotationQualityPane.evaluateMs2(match);
      final QualityMetric adduct = AnnotationQualityPane.evaluateAdduct(row, match);
      final QualityMetric isotope = AnnotationQualityPane.evaluateIsotope(row, match);
      final QualityMetric elutionOrder = includeRetentionTimeAnalysis
          ? AnnotationQualityPane.evaluateElutionOrder(featureList, row, match)
          : new QualityMetric(0d, "Disabled for current lipid analysis mode");
      final double overall = computeWeightedQualityScore(ms1.score(), ms2.score(), adduct.score(),
          isotope.score(), interference.score(), elutionOrder.score(), true,
          includeRetentionTimeAnalysis);
      cards.add(
          new QualityCardData(match, ms1, ms2, adduct, isotope, elutionOrder, interference,
              overall));
    }
    result = new QualityComputationResult(null, selectedAnnotation, interferenceMetrics,
        List.copyOf(duplicateRows), List.copyOf(cards));
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
}
