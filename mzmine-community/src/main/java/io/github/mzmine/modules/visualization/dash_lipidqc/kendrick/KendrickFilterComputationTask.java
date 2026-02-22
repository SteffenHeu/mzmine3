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

package io.github.mzmine.modules.visualization.dash_lipidqc.kendrick;

import static io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.clampToUnit;
import static io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.computeElutionOrderMetrics;
import static io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.computeInterferenceMetrics;
import static io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.computeInterferenceScore;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.gui.chartbasics.chartutils.paintscales.PaintScaleTransform;
import io.github.mzmine.javafx.mvci.FxUpdateTask;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.MSMSLipidTools;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.molecular_species.MolecularSpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.species_level.SpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.ILipidAnnotation;
import io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.ElutionOrderMetrics;
import io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.InterferenceMetrics;
import io.github.mzmine.modules.visualization.kendrickmassplot.KendrickMassPlotXYZDataset;
import io.github.mzmine.util.maths.CenterFunction;
import java.awt.Color;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.renderer.LookupPaintScale;
import org.jfree.chart.renderer.PaintScale;

final class KendrickFilterComputationTask extends FxUpdateTask<KendrickPane> {

  private final long requestId;
  private final @NotNull KendrickMassPlotXYZDataset baseDataset;
  private final @Nullable ModularFeatureList featureList;
  private final @NotNull Set<Integer> visibleRowIds;
  private final boolean includeRetentionTimeAnalysis;
  private @NotNull KendrickFilterComputationResult result;

  KendrickFilterComputationTask(final @NotNull KendrickPane pane, final long requestId,
      final @NotNull KendrickMassPlotXYZDataset baseDataset,
      final @Nullable ModularFeatureList featureList, final @NotNull Set<Integer> visibleRowIds,
      final boolean includeRetentionTimeAnalysis) {
    super("lipidqc-kendrick-filter-update", pane);
    this.requestId = requestId;
    this.baseDataset = baseDataset;
    this.featureList = featureList;
    this.visibleRowIds = Set.copyOf(visibleRowIds);
    this.includeRetentionTimeAnalysis = includeRetentionTimeAnalysis;
    final KendrickSubsetDataset fallback = new KendrickSubsetDataset(baseDataset, _ -> true);
    result = new KendrickFilterComputationResult(requestId, baseDataset, fallback, null, null,
        createColorPaintScale(fallback), new LookupPaintScale(0d, 1d, Color.GRAY));
  }

  @Override
  protected void process() {
    final Predicate<FeatureListRow> visiblePredicate =
        visibleRowIds.isEmpty() ? _ -> true : row -> visibleRowIds.contains(row.getID());
    final KendrickSubsetDataset inDataset = new KendrickSubsetDataset(baseDataset, visiblePredicate);
    final PaintScale filteredColorScale = createColorPaintScale(inDataset);
    final KendrickSubsetDataset filteredOutDataset =
        visibleRowIds.isEmpty() ? null
            : new KendrickSubsetDataset(baseDataset, row -> !visiblePredicate.test(row));
    final LookupPaintScale grayScale =
        filteredOutDataset == null ? new LookupPaintScale(0d, 1d, Color.GRAY)
            : createGrayPaintScale(filteredOutDataset);
    final KendrickSubsetDataset outlierDataset =
        featureList == null || inDataset.getItemCount(0) == 0 ? null
            : new KendrickSubsetDataset(baseDataset,
                row -> visiblePredicate.test(row) && rowOutlier(featureList, row,
                    includeRetentionTimeAnalysis));
    result = new KendrickFilterComputationResult(requestId, baseDataset, inDataset,
        filteredOutDataset, outlierDataset, filteredColorScale, grayScale);
  }

  @Override
  protected void updateGuiModel() {
    model.applyFilterComputationResult(result);
  }

  @Override
  public @NotNull String getTaskDescription() {
    return "Calculating Kendrick filter datasets";
  }

  @Override
  public double getFinishedPercentage() {
    return 0d;
  }

  private static boolean rowOutlier(final @NotNull ModularFeatureList featureList,
      final @NotNull FeatureListRow row, final boolean includeRetentionTimeAnalysis) {
    final List<MatchedLipid> matches = row.getLipidMatches();
    if (matches.isEmpty()) {
      return false;
    }
    final MatchedLipid match = matches.getFirst();
    if (!includeRetentionTimeAnalysis) {
      final double overall = computeOverallQualityScore(row, match, 0d, false);
      return overall < 0.5d;
    }
    final ElutionOrderMetrics elutionMetrics = computeElutionOrderMetrics(featureList, row, match);
    final double overall = computeOverallQualityScore(row, match, elutionMetrics.combinedScore(),
        true);
    final boolean poorCarbonTrend =
        elutionMetrics.carbonsTrend().available() && elutionMetrics.carbonsTrend().score() < 0.55d;
    final boolean poorDbeTrend =
        elutionMetrics.dbeTrend().available() && elutionMetrics.dbeTrend().score() < 0.55d;
    return poorCarbonTrend || poorDbeTrend || elutionMetrics.combinedScore() < 0.55d
        || overall < 0.5d;
  }

  private static double computeOverallQualityScore(final @NotNull FeatureListRow row,
      final @NotNull MatchedLipid match, final double elutionOrderScore,
      final boolean includeRetentionTimeAnalysis) {
    final double ms1Score = computeMs1Score(row, match);
    final double ms2Score = computeMs2Score(match);
    final double adductScore = computeAdductScore(row, match);
    final double isotopeScore = computeIsotopeScore(row, match);
    final InterferenceMetrics interferenceMetrics = computeInterferenceMetrics(row);
    final double interference = computeInterferenceScore(interferenceMetrics.totalPenaltyCount());
    final double scoreSum = ms1Score + ms2Score + adductScore + isotopeScore + interference
        + (includeRetentionTimeAnalysis ? elutionOrderScore : 0d);
    final int scoreCount = includeRetentionTimeAnalysis ? 6 : 5;
    return scoreSum / scoreCount;
  }

  private static double computeMs1Score(final @NotNull FeatureListRow row,
      final @NotNull MatchedLipid match) {
    final double exactMz = MatchedLipid.getExactMass(match);
    final double observedMz =
        match.getAccurateMz() != null ? match.getAccurateMz() : row.getAverageMZ();
    final double ppm = (observedMz - exactMz) / exactMz * 1e6;
    final double absPpm = Math.abs(ppm);
    if (!Double.isFinite(absPpm)) {
      return 0d;
    }
    return clampToUnit(1d - Math.min(absPpm, 5d) / 5d);
  }

  private static double computeMs2Score(final @NotNull MatchedLipid match) {
    final double explainedIntensity = match.getMsMsScore() == null ? 0d : match.getMsMsScore();
    return clampToUnit(explainedIntensity);
  }

  private static double computeAdductScore(final @NotNull FeatureListRow row,
      final @NotNull MatchedLipid match) {
    if (row.getBestIonIdentity() == null) {
      return 0d;
    }
    final String featureAdduct = normalizeAdduct(row.getBestIonIdentity().getAdduct());
    final String lipidAdduct = normalizeAdduct(match.getIonizationType().getAdductName());
    return featureAdduct.equals(lipidAdduct) ? 1d : 0d;
  }

  private static double computeIsotopeScore(final @NotNull FeatureListRow row,
      final @NotNull MatchedLipid match) {
    if (row.getBestIsotopePattern() == null || match.getIsotopePattern() == null) {
      return 0.35d;
    }
    return io.github.mzmine.modules.tools.isotopepatternscore.IsotopePatternScoreCalculator.getSimilarityScore(
        row.getBestIsotopePattern(), match.getIsotopePattern(),
        new io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance(0.003, 10d), 0d);
  }

  private static @NotNull String normalizeAdduct(final @Nullable String adduct) {
    return adduct == null ? "" : adduct.replaceAll("\\s+", "").toLowerCase();
  }

  private static int extractDbe(final @NotNull ILipidAnnotation lipidAnnotation) {
    if (lipidAnnotation instanceof MolecularSpeciesLevelAnnotation molecularAnnotation) {
      return MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
          molecularAnnotation.getAnnotation()).getValue();
    }
    if (lipidAnnotation instanceof SpeciesLevelAnnotation speciesAnnotation) {
      return MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
          speciesAnnotation.getAnnotation()).getValue();
    }
    return -1;
  }

  private static int extractCarbons(final @NotNull ILipidAnnotation lipidAnnotation) {
    if (lipidAnnotation instanceof MolecularSpeciesLevelAnnotation molecularAnnotation) {
      return MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
          molecularAnnotation.getAnnotation()).getKey();
    }
    if (lipidAnnotation instanceof SpeciesLevelAnnotation speciesAnnotation) {
      return MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
          speciesAnnotation.getAnnotation()).getKey();
    }
    return -1;
  }

  private static double predictRtByLinearFit(final @NotNull List<double[]> points,
      final double x) {
    double sumX = 0d;
    double sumY = 0d;
    double sumXY = 0d;
    double sumXX = 0d;
    for (final double[] p : points) {
      sumX += p[0];
      sumY += p[1];
      sumXY += p[0] * p[1];
      sumXX += p[0] * p[0];
    }
    final int n = points.size();
    final double denom = n * sumXX - sumX * sumX;
    if (Math.abs(denom) < 1e-8d) {
      return sumY / n;
    }
    final double slope = (n * sumXY - sumX * sumY) / denom;
    final double intercept = (sumY - slope * sumX) / n;
    return intercept + slope * x;
  }

  private static double estimateResidualStd(final @NotNull List<double[]> points) {
    if (points.size() < 3) {
      return 0.1d;
    }
    final double meanX = points.stream().mapToDouble(p -> p[0]).average().orElse(0d);
    final double meanY = points.stream().mapToDouble(p -> p[1]).average().orElse(0d);
    double numerator = 0d;
    double denominator = 0d;
    for (final double[] p : points) {
      numerator += (p[0] - meanX) * (p[1] - meanY);
      denominator += (p[0] - meanX) * (p[0] - meanX);
    }
    final double slope = denominator == 0d ? 0d : numerator / denominator;
    final double intercept = meanY - slope * meanX;
    double ss = 0d;
    for (final double[] p : points) {
      final double residual = p[1] - (intercept + slope * p[0]);
      ss += residual * residual;
    }
    return Math.sqrt(ss / Math.max(1d, points.size() - 2d));
  }

  private static @NotNull LookupPaintScale createGrayPaintScale(
      final @NotNull KendrickSubsetDataset dataset) {
    final int count = dataset.getItemCount(0);
    if (count == 0) {
      final LookupPaintScale fallback = new LookupPaintScale(0d, 1d, new Color(160, 160, 160));
      fallback.add(0d, new Color(215, 215, 215));
      fallback.add(1d, new Color(105, 105, 105));
      return fallback;
    }

    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < count; i++) {
      final double z = dataset.getZValue(0, i);
      if (Double.isFinite(z)) {
        min = Math.min(min, z);
        max = Math.max(max, z);
      }
    }
    if (!Double.isFinite(min) || !Double.isFinite(max) || min == max) {
      min = 0d;
      max = 1d;
    }
    final LookupPaintScale grayScale = new LookupPaintScale(min, max, new Color(160, 160, 160));
    final int steps = 8;
    for (int i = 0; i < steps; i++) {
      final double value = min + (max - min) * i / (steps - 1d);
      final int shade = 215 - (int) Math.round(120d * i / (steps - 1d));
      grayScale.add(value, new Color(shade, shade, shade, 95));
    }
    return grayScale;
  }

  private static @NotNull PaintScale createColorPaintScale(
      final @NotNull KendrickSubsetDataset dataset) {
    final int count = dataset.getItemCount(0);
    if (count == 0) {
      return new LookupPaintScale(0d, 1d, Color.GRAY);
    }
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < count; i++) {
      final double z = dataset.getZValue(0, i);
      if (Double.isFinite(z)) {
        min = Math.min(min, z);
        max = Math.max(max, z);
      }
    }
    if (!Double.isFinite(min) || !Double.isFinite(max) || min == max) {
      min = 0d;
      max = 1d;
    }
    return ConfigService.getConfiguration().getDefaultPaintScalePalette()
        .toPaintScale(PaintScaleTransform.LINEAR, Range.closed(min, max));
  }
}
