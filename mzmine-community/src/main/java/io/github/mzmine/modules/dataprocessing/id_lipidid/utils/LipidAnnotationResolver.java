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

package io.github.mzmine.modules.dataprocessing.id_lipidid.utils;

import static io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.clampToUnit;
import static io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.computeCombinedAnnotationScore;

import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnalysisType;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipidStatus;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.molecular_species.MolecularSpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.species_level.SpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.ILipidAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.LipidAnnotationLevel;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.lipidchain.ILipidChain;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The LipidAnnotationResolver class is responsible for resolving and processing matched lipid
 * annotations associated with a feature list row. It provides methods to handle duplicate entries,
 * estimate missing species-level annotations, and limit the maximum number of matched lipids to be
 * retained. This class is designed to enhance the accuracy and usefulness of lipid annotations for
 * a given feature.
 * <p>
 * The class resolves lipids from multiple annotation runs resulting from the same or different
 * annotation parameters.
 */
public class LipidAnnotationResolver {

  private final boolean keepIsobars;
  private final boolean keepIsomers;
  private final boolean addMissingSpeciesLevelAnnotation;
  private final boolean includeMs2Score;
  private final boolean includeElutionOrderScore;
  private final double minimumOverallQualityScore;
  private final @Nullable LipidAnalysisType lipidAnalysisType;

  private int maximumIdNumber;
  private static final LipidFactory LIPID_FACTORY = new LipidFactory();

  public LipidAnnotationResolver(final boolean keepIsobars, final boolean keepIsomers,
      final boolean addMissingSpeciesLevelAnnotation) {
    this(keepIsobars, keepIsomers, addMissingSpeciesLevelAnnotation, true, true);
  }

  public LipidAnnotationResolver(final boolean keepIsobars, final boolean keepIsomers,
      final boolean addMissingSpeciesLevelAnnotation, final boolean includeMs2Score,
      final boolean includeElutionOrderScore) {
    this(keepIsobars, keepIsomers, addMissingSpeciesLevelAnnotation, includeMs2Score,
        includeElutionOrderScore, 0d, null);
  }

  public LipidAnnotationResolver(final boolean keepIsobars, final boolean keepIsomers,
      final boolean addMissingSpeciesLevelAnnotation, final boolean includeMs2Score,
      final boolean includeElutionOrderScore, final double minimumOverallQualityScore) {
    this(keepIsobars, keepIsomers, addMissingSpeciesLevelAnnotation, includeMs2Score,
        includeElutionOrderScore, minimumOverallQualityScore, null);
  }

  public LipidAnnotationResolver(final boolean keepIsobars, final boolean keepIsomers,
      final boolean addMissingSpeciesLevelAnnotation, final boolean includeMs2Score,
      final boolean includeElutionOrderScore, final double minimumOverallQualityScore,
      final @Nullable LipidAnalysisType lipidAnalysisType) {
    this.keepIsobars = keepIsobars;
    this.keepIsomers = keepIsomers;
    this.addMissingSpeciesLevelAnnotation = addMissingSpeciesLevelAnnotation;
    this.includeMs2Score = includeMs2Score;
    this.includeElutionOrderScore = includeElutionOrderScore;
    this.minimumOverallQualityScore = clampToUnit(minimumOverallQualityScore);
    this.lipidAnalysisType = lipidAnalysisType;
    this.maximumIdNumber = -1;
  }

  public LipidAnnotationResolver(final boolean keepIsobars, final boolean keepIsomers,
      final boolean addMissingSpeciesLevelAnnotation, final int maximumIdNumber) {
    this(keepIsobars, keepIsomers, addMissingSpeciesLevelAnnotation, maximumIdNumber, true, true);
  }

  public LipidAnnotationResolver(final boolean keepIsobars, final boolean keepIsomers,
      final boolean addMissingSpeciesLevelAnnotation, final int maximumIdNumber,
      final boolean includeMs2Score, final boolean includeElutionOrderScore) {
    this(keepIsobars, keepIsomers, addMissingSpeciesLevelAnnotation, includeMs2Score,
        includeElutionOrderScore, 0d, null);
    this.maximumIdNumber = maximumIdNumber;
  }

  public LipidAnnotationResolver(final boolean keepIsobars, final boolean keepIsomers,
      final boolean addMissingSpeciesLevelAnnotation, final int maximumIdNumber,
      final boolean includeMs2Score, final boolean includeElutionOrderScore,
      final double minimumOverallQualityScore) {
    this(keepIsobars, keepIsomers, addMissingSpeciesLevelAnnotation, includeMs2Score,
        includeElutionOrderScore, minimumOverallQualityScore, null);
    this.maximumIdNumber = maximumIdNumber;
  }

  public LipidAnnotationResolver(final boolean keepIsobars, final boolean keepIsomers,
      final boolean addMissingSpeciesLevelAnnotation, final int maximumIdNumber,
      final boolean includeMs2Score, final boolean includeElutionOrderScore,
      final double minimumOverallQualityScore,
      final @Nullable LipidAnalysisType lipidAnalysisType) {
    this(keepIsobars, keepIsomers, addMissingSpeciesLevelAnnotation, includeMs2Score,
        includeElutionOrderScore, minimumOverallQualityScore, lipidAnalysisType);
    this.maximumIdNumber = maximumIdNumber;
  }

  public @NotNull List<MatchedLipid> resolveFeatureListRowMatchedLipids(
      final @NotNull FeatureListRow featureListRow,
      final @NotNull Set<MatchedLipid> matchedLipids) {
    final List<MatchedLipid> resolvedMatchedLipidsList = removeDuplicates(matchedLipids);
    final Map<MatchedLipid, Double> preFilterScoreCache = new IdentityHashMap<>();
    final Map<MatchedLipid, Double> qualityScoreCache = new IdentityHashMap<>();
    sortByRankingScore(featureListRow, resolvedMatchedLipidsList, preFilterScoreCache);
    if (addMissingSpeciesLevelAnnotation) {
      final boolean addedSpeciesLevel = estimateMissingSpeciesLevelAnnotations(
          resolvedMatchedLipidsList);
      if (addedSpeciesLevel) {
        sortByRankingScore(featureListRow, resolvedMatchedLipidsList, preFilterScoreCache);
      }
    }
    filterByMinimumQuality(featureListRow, resolvedMatchedLipidsList, qualityScoreCache);
    //TODO: Add Keep isobars functionality

    //TODO: Add keep isomers functionality

    //add to resolved list
    if (maximumIdNumber != -1 && resolvedMatchedLipidsList.size() > maximumIdNumber) {
      filterMaximumNumberOfId(resolvedMatchedLipidsList);
    }
    return resolvedMatchedLipidsList;
  }

  private @NotNull List<MatchedLipid> removeDuplicates(
      final @NotNull Set<MatchedLipid> resolvedMatchedLipids) {
    return resolvedMatchedLipids.stream().collect(Collectors.collectingAndThen(
        Collectors.toCollection(() -> new TreeSet<>(comparatorMatchedLipids())), ArrayList::new));
  }

  private boolean estimateMissingSpeciesLevelAnnotations(
      final @NotNull List<MatchedLipid> resolvedMatchedLipidsList) {
    boolean addedAnnotations = false;
    if (resolvedMatchedLipidsList.stream().noneMatch(
        matchedLipid -> matchedLipid.getLipidAnnotation().getLipidAnnotationLevel()
            .equals(LipidAnnotationLevel.SPECIES_LEVEL))) {
      Set<MatchedLipid> estimatedSpeciesLevelMatchedLipids = new HashSet<>();
      for (MatchedLipid lipid : resolvedMatchedLipidsList) {
        ILipidAnnotation estimatedSpeciesLevelAnnotation = convertMolecularSpeciesLevelToSpeciesLevel(
            (MolecularSpeciesLevelAnnotation) lipid.getLipidAnnotation());
        if (resolvedMatchedLipidsList.stream().noneMatch(
            matchedLipid -> matchedLipid.getLipidAnnotation().getAnnotation()
                .equals(estimatedSpeciesLevelAnnotation.getAnnotation()))) {
          if ((estimatedSpeciesLevelAnnotation != null
               && estimatedSpeciesLevelMatchedLipids.isEmpty()) || (
                  estimatedSpeciesLevelAnnotation != null
                  && estimatedSpeciesLevelMatchedLipids.stream().anyMatch(
                      matchedLipid -> !Objects.equals(
                          matchedLipid.getLipidAnnotation().getAnnotation(),
                          estimatedSpeciesLevelAnnotation.getAnnotation())))) {
            MatchedLipid matchedLipidSpeciesLevel = new MatchedLipid(
                estimatedSpeciesLevelAnnotation, lipid.getAccurateMz(), lipid.getIonizationType(),
                new HashSet<>(lipid.getMatchedFragments()), 0.0,
                MatchedLipidStatus.SPECIES_DERIVED_FROM_MOLECULAR_SPECIES);
            estimatedSpeciesLevelMatchedLipids.add(matchedLipidSpeciesLevel);
          }
        }
      }
      if (!estimatedSpeciesLevelMatchedLipids.isEmpty()) {
        resolvedMatchedLipidsList.addAll(estimatedSpeciesLevelMatchedLipids);
        addedAnnotations = true;
      }
    }
    return addedAnnotations;
  }

  private @NotNull SpeciesLevelAnnotation convertMolecularSpeciesLevelToSpeciesLevel(
      final @NotNull MolecularSpeciesLevelAnnotation lipidAnnotation) {
    final int numberOfCarbons = lipidAnnotation.getLipidChains().stream()
        .mapToInt(ILipidChain::getNumberOfCarbons).sum();
    final int numberOfDBEs = lipidAnnotation.getLipidChains().stream()
        .mapToInt(ILipidChain::getNumberOfDBEs).sum();
    return LIPID_FACTORY.buildSpeciesLevelLipid(lipidAnnotation.getLipidClass(), numberOfCarbons,
        numberOfDBEs, 0);
  }

  private void filterMaximumNumberOfId(final @NotNull List<MatchedLipid> resolvedMatchedLipids) {
    final Iterator<MatchedLipid> iterator = resolvedMatchedLipids.iterator();
    while (iterator.hasNext()) {
      final MatchedLipid lipid = iterator.next();
      if (resolvedMatchedLipids.indexOf(lipid) > maximumIdNumber) {
        iterator.remove();
      }
    }
  }

  private void filterByMinimumQuality(final @NotNull FeatureListRow featureListRow,
      final @NotNull List<MatchedLipid> resolvedMatchedLipids,
      final @NotNull Map<MatchedLipid, Double> qualityScoreCache) {
    if (minimumOverallQualityScore <= 0d) {
      return;
    }
    resolvedMatchedLipids.removeIf(matchedLipid -> qualityScoreCache.computeIfAbsent(matchedLipid,
        lipid -> computeQualityScore(featureListRow, lipid)) < minimumOverallQualityScore);
  }

  private static @NotNull Comparator<MatchedLipid> comparatorMatchedLipids() {
    return Comparator.comparing(
            (MatchedLipid lipid) -> Objects.toString(lipid.getLipidAnnotation().getAnnotation(), ""))
        .thenComparingDouble(LipidAnnotationResolver::safeMs2Score)
        .thenComparing(MatchedLipid::getAccurateMz, Comparator.nullsLast(Double::compareTo));
  }

  private void sortByRankingScore(final @NotNull FeatureListRow featureListRow,
      final @NotNull List<MatchedLipid> matchedLipids,
      final @NotNull Map<MatchedLipid, Double> scoreCache) {
    matchedLipids.sort((left, right) -> {
      final double leftScore = scoreCache.computeIfAbsent(left,
          lipid -> computePreFilterRankingScore(featureListRow, lipid));
      final double rightScore = scoreCache.computeIfAbsent(right,
          lipid -> computePreFilterRankingScore(featureListRow, lipid));
      final int byScore = Double.compare(rightScore, leftScore);
      if (byScore != 0) {
        return byScore;
      }
      final int byMs2 = Double.compare(safeMs2Score(right), safeMs2Score(left));
      if (byMs2 != 0) {
        return byMs2;
      }
      return Objects.toString(left.getLipidAnnotation().getAnnotation(), "")
          .compareTo(Objects.toString(right.getLipidAnnotation().getAnnotation(), ""));
    });
  }

  private double computePreFilterRankingScore(final @NotNull FeatureListRow featureListRow,
      final @NotNull MatchedLipid matchedLipid) {
    final double ms1Score = computeMs1Score(featureListRow, matchedLipid);
    if (includeMs2Score) {
      return (ms1Score + safeMs2Score(matchedLipid)) / 2d;
    }
    return ms1Score;
  }

  private double computeQualityScore(final @NotNull FeatureListRow featureListRow,
      final @NotNull MatchedLipid matchedLipid) {
    if (featureListRow.getFeatureList() instanceof ModularFeatureList modularFeatureList) {
      return computeCombinedAnnotationScore(modularFeatureList, featureListRow, matchedLipid,
          includeMs2Score, includeElutionOrderScore, lipidAnalysisType);
    }
    return computePreFilterRankingScore(featureListRow, matchedLipid);
  }

  private static double computeMs1Score(final @NotNull FeatureListRow featureListRow,
      final @NotNull MatchedLipid matchedLipid) {
    final double exactMz = MatchedLipid.getExactMass(matchedLipid);
    final Double accurateMz = matchedLipid.getAccurateMz();
    final double observedMz = accurateMz != null ? accurateMz : featureListRow.getAverageMZ();
    final double ppm = (observedMz - exactMz) / exactMz * 1e6;
    final double absPpm = Math.abs(ppm);
    if (!Double.isFinite(absPpm)) {
      return 0d;
    }
    return clampToUnit(1d - Math.min(absPpm, 5d) / 5d);
  }

  private static double safeMs2Score(final @Nullable MatchedLipid matchedLipid) {
    if (matchedLipid == null || matchedLipid.getMsMsScore() == null) {
      return 0d;
    }
    return clampToUnit(matchedLipid.getMsMsScore());
  }

}
