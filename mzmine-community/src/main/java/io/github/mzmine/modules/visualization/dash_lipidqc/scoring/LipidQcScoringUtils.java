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

package io.github.mzmine.modules.visualization.dash_lipidqc.scoring;

import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.MSMSLipidTools;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.molecular_species.MolecularSpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.species_level.SpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.ILipidAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.ILipidClass;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LipidQcScoringUtils {

  private static final double RT_DELTA_NO_PENALTY_NORMALIZED = 0.006d;
  private static final double RT_DELTA_FULL_PENALTY_NORMALIZED = 0.07d;
  private static final double RT_DELTA_PENALTY_EXPONENT = 1.35d;

  private LipidQcScoringUtils() {
  }

  public record TrendScore(double score, double residualRt, double normalizedDelta,
                           boolean available) {

  }

  public record InterferenceMetrics(int classPenaltyCount, int sameClassAdductPenaltyCount) {

    public int totalPenaltyCount() {
      return Math.max(0, classPenaltyCount) + Math.max(0, sameClassAdductPenaltyCount);
    }
  }

  public record ElutionOrderMetrics(double combinedScore, @NotNull TrendScore carbonsTrend,
                                    @NotNull TrendScore dbeTrend) {

  }

  public static @NotNull ElutionOrderMetrics computeElutionOrderMetrics(
      final @NotNull ModularFeatureList featureList, final @NotNull FeatureListRow row,
      final @NotNull MatchedLipid match) {
    if (row.getAverageRT() == null) {
      final TrendScore missing = new TrendScore(0d, Double.NaN, Double.NaN, false);
      return new ElutionOrderMetrics(0d, missing, missing);
    }
    final int selectedCarbons = extractTrendCarbons(match.getLipidAnnotation());
    final int selectedDbe = extractTrendDbe(match.getLipidAnnotation());
    if (selectedCarbons < 0 || selectedDbe < 0) {
      final TrendScore missing = new TrendScore(0d, Double.NaN, Double.NaN, false);
      return new ElutionOrderMetrics(0d, missing, missing);
    }

    final ILipidClass selectedClass = match.getLipidAnnotation().getLipidClass();
    final double observedRt = row.getAverageRT();
    final double methodLength = computeRtMethodLength(featureList);

    final List<double[]> carbonTrendPoints = new ArrayList<>();
    final List<double[]> dbeTrendPoints = new ArrayList<>();
    for (final FeatureListRow other : featureList.getRows()) {
      final List<MatchedLipid> otherMatches = other.getLipidMatches();
      if (otherMatches.isEmpty() || other.getAverageRT() == null) {
        continue;
      }
      final MatchedLipid otherMatch = otherMatches.getFirst();
      if (!otherMatch.getLipidAnnotation().getLipidClass().equals(selectedClass)) {
        continue;
      }
      final int otherCarbons = extractTrendCarbons(otherMatch.getLipidAnnotation());
      final int otherDbe = extractTrendDbe(otherMatch.getLipidAnnotation());
      if (otherCarbons < 0 || otherDbe < 0) {
        continue;
      }
      if (otherDbe == selectedDbe) {
        carbonTrendPoints.add(new double[]{otherCarbons, other.getAverageRT()});
      }
      if (otherCarbons == selectedCarbons) {
        dbeTrendPoints.add(new double[]{otherDbe, other.getAverageRT()});
      }
    }

    final TrendScore carbonsTrend = computeTrendScore(carbonTrendPoints, selectedCarbons,
        observedRt, methodLength);
    final TrendScore dbeTrend = computeTrendScore(dbeTrendPoints, selectedDbe, observedRt,
        methodLength);
    final double combined = combineTrendScores(carbonsTrend, dbeTrend);
    return new ElutionOrderMetrics(combined, carbonsTrend, dbeTrend);
  }

  private static @NotNull TrendScore computeTrendScore(final @NotNull List<double[]> points,
      final double selectedPredictor, final double observedRt, final double methodLength) {
    if (points.size() < 3) {
      return new TrendScore(0d, Double.NaN, Double.NaN, false);
    }
    final double expectedRt = predictRtByLinearFitGlobal(points, selectedPredictor);
    final double residual = Math.abs(observedRt - expectedRt);
    final double normalizedDelta = normalizeRtDeltaByMethodLength(residual, methodLength);
    final double score = computeEcnRtScoreFromDelta(residual, methodLength);
    return new TrendScore(score, residual, normalizedDelta, true);
  }

  private static double combineTrendScores(final @NotNull TrendScore carbonsTrend,
      final @NotNull TrendScore dbeTrend) {
    if (carbonsTrend.available() && dbeTrend.available()) {
      return clampToUnit((carbonsTrend.score() + dbeTrend.score()) / 2d);
    }
    if (carbonsTrend.available()) {
      return Math.max(0.05d, clampToUnit(carbonsTrend.score() * 0.8d));
    }
    if (dbeTrend.available()) {
      return Math.max(0.05d, clampToUnit(dbeTrend.score() * 0.8d));
    }
    return 0d;
  }

  public static @NotNull String formatElutionOrderDetail(
      final @NotNull ElutionOrderMetrics metrics) {
    return "C trend: " + formatTrendDetail(metrics.carbonsTrend()) + " | DBE trend: "
        + formatTrendDetail(metrics.dbeTrend());
  }

  private static @NotNull String formatTrendDetail(final @NotNull TrendScore trendScore) {
    if (!trendScore.available()) {
      return "n/a";
    }
    if (Double.isFinite(trendScore.normalizedDelta())) {
      return String.format("%.0f%% (Δ=%.2f, %.1f%% method)", trendScore.score() * 100d,
          trendScore.residualRt(), trendScore.normalizedDelta() * 100d);
    }
    return String.format("%.0f%% (Δ=%.2f)", trendScore.score() * 100d, trendScore.residualRt());
  }

  private static double predictRtByLinearFitGlobal(final @NotNull List<double[]> points,
      final double predictorValue) {
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
    return intercept + slope * predictorValue;
  }

  private static int extractTrendDbe(final @NotNull ILipidAnnotation lipidAnnotation) {
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

  private static int extractTrendCarbons(final @NotNull ILipidAnnotation lipidAnnotation) {
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

  public static double clampToUnit(final double value) {
    if (!Double.isFinite(value)) {
      return 0d;
    }
    return Math.max(0d, Math.min(1d, value));
  }

  private static double normalizeRtDeltaByMethodLength(final double deltaRt,
      final double methodLength) {
    if (!Double.isFinite(deltaRt) || !Double.isFinite(methodLength) || methodLength <= 0d) {
      return Double.NaN;
    }
    return Math.abs(deltaRt) / methodLength;
  }

  private static double computeEcnRtScoreFromDelta(final double deltaRt,
      final double methodLength) {
    final double normalizedDelta = normalizeRtDeltaByMethodLength(deltaRt, methodLength);
    if (!Double.isFinite(normalizedDelta)) {
      return 0.4d;
    }
    if (normalizedDelta <= RT_DELTA_NO_PENALTY_NORMALIZED) {
      return 1d;
    }
    final double penaltyRange = RT_DELTA_FULL_PENALTY_NORMALIZED - RT_DELTA_NO_PENALTY_NORMALIZED;
    if (penaltyRange <= 0d) {
      return clampToUnit(1d - normalizedDelta / RT_DELTA_FULL_PENALTY_NORMALIZED);
    }
    final double scaledPenalty = (normalizedDelta - RT_DELTA_NO_PENALTY_NORMALIZED) / penaltyRange;
    final double nonLinearPenalty = Math.pow(clampToUnit(scaledPenalty), RT_DELTA_PENALTY_EXPONENT);
    return clampToUnit(1d - nonLinearPenalty);
  }

  public static double computeInterferenceScore(final int interferenceCount) {
    return switch (Math.max(0, interferenceCount)) {
      case 0 -> 1d;
      case 1 -> 0.5d;
      default -> 0d;
    };
  }

  public static double computeCombinedAnnotationScore(final @NotNull ModularFeatureList featureList,
      final @NotNull FeatureListRow row, final @NotNull MatchedLipid match) {
    final ElutionOrderMetrics elutionMetrics = computeElutionOrderMetrics(featureList, row, match);
    return computeOverallQualityScore(row, match, elutionMetrics.combinedScore());
  }

  private static double computeOverallQualityScore(final @NotNull FeatureListRow row,
      final @NotNull MatchedLipid match, final double elutionOrderScore) {
    final double ms1Score = computeMs1Score(row, match);
    final double ms2Score = computeMs2Score(match);
    final double adductScore = computeAdductScore(row, match);
    final double isotopeScore = computeIsotopeScore(row, match);
    final InterferenceMetrics interferenceMetrics = computeInterferenceMetrics(row);
    final double interference = computeInterferenceScore(interferenceMetrics.totalPenaltyCount());
    return (ms1Score + ms2Score + adductScore + isotopeScore + elutionOrderScore + interference)
        / 6d;
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

  public static @NotNull InterferenceMetrics computeInterferenceMetrics(
      final @NotNull FeatureListRow row) {
    final List<MatchedLipid> matches = row.getLipidMatches();
    if (matches.isEmpty()) {
      return new InterferenceMetrics(0, 0);
    }

    final long uniqueClasses = matches.stream()
        .map(m -> m.getLipidAnnotation().getLipidClass().getName()).distinct().count();
    final int classPenaltyCount = (int) Math.max(0L, uniqueClasses - 1L);

    final Map<String, List<MatchedLipid>> byClass = new TreeMap<>();
    for (final MatchedLipid match : matches) {
      final String className = match.getLipidAnnotation().getLipidClass().getName();
      byClass.computeIfAbsent(className, _ -> new ArrayList<>()).add(match);
    }
    int sameClassAdductPenaltyCount = 0;
    for (final List<MatchedLipid> sameClassMatches : byClass.values()) {
      final long uniqueAnnotations = sameClassMatches.stream()
          .map(m -> m.getLipidAnnotation().getAnnotation()).filter(Objects::nonNull).distinct()
          .count();
      final long uniqueAdducts = sameClassMatches.stream().map(m -> normalizeAdductForInterference(
              m.getIonizationType() != null ? m.getIonizationType().getAdductName() : null))
          .filter(adduct -> !adduct.isBlank()).distinct().count();
      if (uniqueAnnotations > 1 && uniqueAdducts > 1) {
        sameClassAdductPenaltyCount++;
      }
    }
    return new InterferenceMetrics(classPenaltyCount, sameClassAdductPenaltyCount);
  }

  public static @NotNull String interferenceDetail(final @NotNull InterferenceMetrics metrics) {
    if (metrics.totalPenaltyCount() == 0) {
      return "No competing lipid classes or adduct-conflicting annotations in selected row.";
    }
    if (metrics.classPenaltyCount() > 0 && metrics.sameClassAdductPenaltyCount() > 0) {
      return "Multiple lipid classes and same-class annotations with different adducts are present.";
    }
    if (metrics.classPenaltyCount() > 0) {
      return metrics.classPenaltyCount() == 1 ? "One competing lipid class present in selected row."
          : "Multiple competing lipid classes present in selected row.";
    }
    return metrics.sameClassAdductPenaltyCount() == 1
        ? "Same lipid class has multiple annotations supported by different adducts."
        : "Multiple lipid classes show annotation ambiguity across different adducts.";
  }

  private static @NotNull String normalizeAdductForInterference(final @Nullable String adduct) {
    return adduct == null ? "" : adduct.replaceAll("\\s+", "").toLowerCase();
  }

  private static double computeRtMethodLength(final @NotNull ModularFeatureList featureList) {
    double minRt = Double.POSITIVE_INFINITY;
    double maxRt = Double.NEGATIVE_INFINITY;
    for (final FeatureListRow row : featureList.getRows()) {
      final Float rt = row.getAverageRT();
      if (rt == null || !Float.isFinite(rt)) {
        continue;
      }
      minRt = Math.min(minRt, rt);
      maxRt = Math.max(maxRt, rt);
    }
    if (!Double.isFinite(minRt) || !Double.isFinite(maxRt) || maxRt <= minRt) {
      return 0d;
    }
    return maxRt - minRt;
  }
}
