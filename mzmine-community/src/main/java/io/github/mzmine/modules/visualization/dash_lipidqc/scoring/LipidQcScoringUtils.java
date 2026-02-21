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

import io.github.mzmine.datamodel.features.FeatureList.FeatureListAppliedMethod;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnalysisType;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnnotationModule;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnnotationParameters;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.MSMSLipidTools;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.molecular_species.MolecularSpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.species_level.SpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.ILipidAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.ILipidClass;
import java.util.ArrayList;
import java.util.Arrays;
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
  private static final int HILIC_MIN_CLASS_ROWS = 4;
  private static final double HILIC_WINDOW_SUPPORT_FRACTION = 0.6d;
  private static final double HILIC_WINDOW_EDGE_TOLERANCE_MINUTES = 0.02d;
  private static final double HILIC_WINDOW_EDGE_RELATIVE_TOLERANCE = 0.02d;

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
                                    @NotNull TrendScore dbeTrend,
                                    @Nullable String detailOverride) {

  }

  public static @NotNull ElutionOrderMetrics computeElutionOrderMetrics(
      final @NotNull ModularFeatureList featureList, final @NotNull FeatureListRow row,
      final @NotNull MatchedLipid match) {
    return computeElutionOrderMetrics(featureList, row, match,
        detectLipidAnalysisType(featureList));
  }

  public static @NotNull ElutionOrderMetrics computeElutionOrderMetrics(
      final @NotNull ModularFeatureList featureList, final @NotNull FeatureListRow row,
      final @NotNull MatchedLipid match, final @Nullable LipidAnalysisType analysisType) {
    final LipidAnalysisType resolvedAnalysisType = Objects.requireNonNullElse(analysisType,
        LipidAnalysisType.LC_REVERSED_PHASE);
    return switch (resolvedAnalysisType) {
      case LC_HILIC -> computeHilicElutionOrderMetrics(featureList, row, match);
      case LC_REVERSED_PHASE -> computeTrendElutionOrderMetrics(featureList, row, match);
      case DIRECT_INFUSION, IMAGING ->
          createUnavailableElutionMetrics("Disabled for current lipid analysis mode");
    };
  }

  public static @Nullable LipidAnalysisType detectLipidAnalysisType(
      final @NotNull ModularFeatureList featureList) {
    final List<FeatureListAppliedMethod> appliedMethods = featureList.getAppliedMethods();
    for (int i = appliedMethods.size() - 1; i >= 0; i--) {
      final FeatureListAppliedMethod appliedMethod = appliedMethods.get(i);
      if (!(appliedMethod.getModule() instanceof LipidAnnotationModule)) {
        continue;
      }
      try {
        return appliedMethod.getParameters().getParameter(LipidAnnotationParameters.lipidAnalysisType)
            .getValue();
      } catch (RuntimeException ignored) {
        return null;
      }
    }
    return null;
  }

  private static @NotNull ElutionOrderMetrics computeTrendElutionOrderMetrics(
      final @NotNull ModularFeatureList featureList, final @NotNull FeatureListRow row,
      final @NotNull MatchedLipid match) {
    if (row.getAverageRT() == null) {
      return createUnavailableElutionMetrics("Missing RT context");
    }
    final int selectedCarbons = extractTrendCarbons(match.getLipidAnnotation());
    final int selectedDbe = extractTrendDbe(match.getLipidAnnotation());
    if (selectedCarbons < 0 || selectedDbe < 0) {
      return createUnavailableElutionMetrics("Missing lipid chain information");
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
    return new ElutionOrderMetrics(combined, carbonsTrend, dbeTrend, null);
  }

  private static @NotNull ElutionOrderMetrics computeHilicElutionOrderMetrics(
      final @NotNull ModularFeatureList featureList, final @NotNull FeatureListRow row,
      final @NotNull MatchedLipid match) {
    if (row.getAverageRT() == null) {
      return createUnavailableElutionMetrics("Missing RT context");
    }
    final ILipidClass selectedClass = match.getLipidAnnotation().getLipidClass();
    final double[] classRtValues = collectClassRetentionTimes(featureList, selectedClass);
    if (classRtValues.length < HILIC_MIN_CLASS_ROWS) {
      return createUnavailableElutionMetrics(
          "HILIC class-window model needs at least " + HILIC_MIN_CLASS_ROWS + " class rows");
    }

    final int supportCount = Math.max(3,
        (int) Math.ceil(classRtValues.length * HILIC_WINDOW_SUPPORT_FRACTION));
    final RtWindow dominantWindow = computeNarrowestWindow(classRtValues, supportCount);
    final double observedRt = row.getAverageRT();
    final double windowWidth = dominantWindow.upperRt() - dominantWindow.lowerRt();
    final double edgeTolerance = Math.max(HILIC_WINDOW_EDGE_TOLERANCE_MINUTES,
        windowWidth * HILIC_WINDOW_EDGE_RELATIVE_TOLERANCE);
    final boolean inWindow = observedRt >= dominantWindow.lowerRt() - edgeTolerance
        && observedRt <= dominantWindow.upperRt() + edgeTolerance;
    final double distanceToWindow = inWindow ? 0d
        : Math.min(Math.abs(observedRt - dominantWindow.lowerRt()),
            Math.abs(observedRt - dominantWindow.upperRt()));
    final double normalizedDistance = normalizeRtDeltaByMethodLength(distanceToWindow,
        computeRtMethodLength(featureList));
    final double score = inWindow ? 1d : 0d;
    final TrendScore windowScore = new TrendScore(score, distanceToWindow, normalizedDistance,
        true);
    final String detail = String.format(
        "HILIC class window %.2f-%.2f min (%d/%d rows); selected RT %.2f is %s the window",
        dominantWindow.lowerRt(), dominantWindow.upperRt(), dominantWindow.supportCount(),
        dominantWindow.totalCount(), observedRt, inWindow ? "inside" : "outside");
    return new ElutionOrderMetrics(score, windowScore, windowScore, detail);
  }

  private static @NotNull ElutionOrderMetrics createUnavailableElutionMetrics(
      final @NotNull String reason) {
    final TrendScore missing = new TrendScore(0d, Double.NaN, Double.NaN, false);
    return new ElutionOrderMetrics(0d, missing, missing, reason);
  }

  private static @NotNull double[] collectClassRetentionTimes(
      final @NotNull ModularFeatureList featureList, final @NotNull ILipidClass selectedClass) {
    final List<Double> classRts = new ArrayList<>();
    for (final FeatureListRow candidateRow : featureList.getRows()) {
      final Float candidateRt = candidateRow.getAverageRT();
      if (candidateRt == null || !Float.isFinite(candidateRt)) {
        continue;
      }
      final List<MatchedLipid> rowMatches = candidateRow.getLipidMatches();
      if (rowMatches.isEmpty()) {
        continue;
      }
      final boolean hasMatchingClass = rowMatches.stream().anyMatch(
          candidate -> candidate.getLipidAnnotation().getLipidClass().equals(selectedClass));
      if (hasMatchingClass) {
        classRts.add((double) candidateRt);
      }
    }
    return classRts.stream().mapToDouble(Double::doubleValue).toArray();
  }

  private static @NotNull RtWindow computeNarrowestWindow(final @NotNull double[] rtValues,
      final int requestedSupportCount) {
    final double[] sorted = Arrays.copyOf(rtValues, rtValues.length);
    Arrays.sort(sorted);
    final int supportCount = Math.max(2, Math.min(sorted.length, requestedSupportCount));
    int bestStart = 0;
    double bestWidth = Double.POSITIVE_INFINITY;
    for (int start = 0; start <= sorted.length - supportCount; start++) {
      final int end = start + supportCount - 1;
      final double width = sorted[end] - sorted[start];
      if (width < bestWidth) {
        bestWidth = width;
        bestStart = start;
      }
    }
    final int bestEnd = bestStart + supportCount - 1;
    return new RtWindow(sorted[bestStart], sorted[bestEnd], supportCount, sorted.length);
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
    if (metrics.detailOverride() != null && !metrics.detailOverride().isBlank()) {
      return metrics.detailOverride();
    }
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
    return computeCombinedAnnotationScore(featureList, row, match, true, true,
        detectLipidAnalysisType(featureList));
  }

  public static double computeCombinedAnnotationScore(final @NotNull ModularFeatureList featureList,
      final @NotNull FeatureListRow row, final @NotNull MatchedLipid match,
      final boolean includeMs2Score, final boolean includeElutionOrderScore) {
    return computeCombinedAnnotationScore(featureList, row, match, includeMs2Score,
        includeElutionOrderScore, detectLipidAnalysisType(featureList));
  }

  public static double computeCombinedAnnotationScore(final @NotNull ModularFeatureList featureList,
      final @NotNull FeatureListRow row, final @NotNull MatchedLipid match,
      final boolean includeMs2Score, final boolean includeElutionOrderScore,
      final @Nullable LipidAnalysisType analysisType) {
    final double elutionOrderScore;
    if (includeElutionOrderScore) {
      final ElutionOrderMetrics elutionMetrics = computeElutionOrderMetrics(featureList, row, match,
          analysisType);
      elutionOrderScore = elutionMetrics.combinedScore();
    } else {
      elutionOrderScore = 0d;
    }
    return computeOverallQualityScore(row, match, elutionOrderScore, includeMs2Score,
        includeElutionOrderScore);
  }

  private static double computeOverallQualityScore(final @NotNull FeatureListRow row,
      final @NotNull MatchedLipid match, final double elutionOrderScore,
      final boolean includeMs2Score, final boolean includeElutionOrderScore) {
    final double ms1Score = computeMs1Score(row, match);
    final double adductScore = computeAdductScore(row, match);
    final double isotopeScore = computeIsotopeScore(row, match);
    final InterferenceMetrics interferenceMetrics = computeInterferenceMetrics(row);
    final double interference = computeInterferenceScore(interferenceMetrics.totalPenaltyCount());
    double scoreSum = ms1Score + adductScore + isotopeScore + interference;
    int scoreCount = 4;
    if (includeMs2Score) {
      scoreSum += computeMs2Score(match);
      scoreCount++;
    }
    if (includeElutionOrderScore) {
      scoreSum += elutionOrderScore;
      scoreCount++;
    }
    return scoreCount == 0 ? 0d : scoreSum / scoreCount;
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

  private record RtWindow(double lowerRt, double upperRt, int supportCount, int totalCount) {

  }
}
