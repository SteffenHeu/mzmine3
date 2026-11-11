package io.github.mzmine.modules.visualization.dash_lipidqc.quality;

import io.github.mzmine.datamodel.IonizationType;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.datamodel.features.types.annotations.LipidMatchListType;
import io.github.mzmine.datamodel.features.types.annotations.PreferredAnnotationType;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnalysisType;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnnotationModule;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnnotationParameters;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidQcWeightParameters;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.ILipidAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.LipidClasses;
import io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringSettings;
import io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils;
import io.github.mzmine.modules.dataprocessing.id_lipidid.utils.LipidFactory;
import io.github.mzmine.modules.visualization.dash_lipidqc.kendrick.KendrickReviewMode;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.util.FormulaUtils;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LipidQcScoringConsistencyTest {

  private static final LipidFactory LIPID_FACTORY = new LipidFactory();
  // stored scores are floats
  private static final double FLOAT_DELTA = 1e-6;

  /**
   * Checks the stored/exported score, dashboard card and outlier warning against independent
   * expected values, including missing isotope evidence and disabled MS/MS scoring.
   *
   * @param customWeights whether custom weights are enabled
   * @param includeMs2    whether MS/MS scoring is enabled
   * @param expected      independently calculated weighted quality
   */
  @ParameterizedTest
  @CsvSource({"true, true, 0.2", "true, false, 1.0", "false, true, 0.2571428571428571",
      "false, false, 0.6"})
  void dashboardAndWarningUseLatestAnnotationSettings(final boolean customWeights,
      final boolean includeMs2, final double expected) {
    final var featureList = new ModularFeatureList("Lipid QC", null, List.of());
    final var row = new ModularFeatureListRow(featureList, 1);
    final var annotation = pc341();
    final double mz = protonatedMz(annotation);
    final var match = new MatchedLipid(annotation, mz, IonizationType.POSITIVE_HYDROGEN, Set.of(),
        0d);
    row.setAverageMZ(mz);
    row.set(LipidMatchListType.class, List.of(match));
    row.set(PreferredAnnotationType.class, match);
    featureList.addRow(row);

    final var parameters = new LipidAnnotationParameters();
    parameters.setParameter(LipidAnnotationParameters.lipidAnalysisType,
        LipidAnalysisType.DIRECT_INFUSION);
    parameters.setParameter(LipidAnnotationParameters.mzTolerance, new MZTolerance(0.005, 10d));
    parameters.setParameter(LipidAnnotationParameters.searchForMSMSFragments, !includeMs2);
    featureList.addDescriptionOfAppliedTask(
        new SimpleFeatureListAppliedMethod(new LipidAnnotationModule(), parameters, Instant.EPOCH));

    parameters.setParameter(LipidAnnotationParameters.searchForMSMSFragments, includeMs2);
    parameters.setParameter(LipidAnnotationParameters.customQcWeights, customWeights);
    final var weights = parameters.getParameter(LipidAnnotationParameters.customQcWeights)
        .getEmbeddedParameters();
    weights.setParameter(LipidQcWeightParameters.ms1Weight, 20);
    weights.setParameter(LipidQcWeightParameters.ms2Weight, 80);
    weights.setParameter(LipidQcWeightParameters.adductMatchWeight, 0);
    weights.setParameter(LipidQcWeightParameters.adductMismatchWeight, 0);
    weights.setParameter(LipidQcWeightParameters.adductNoIonIdentityWeight, 0);
    weights.setParameter(LipidQcWeightParameters.isotopeWeight, 0);
    weights.setParameter(LipidQcWeightParameters.interferenceWeight, 0);
    featureList.addDescriptionOfAppliedTask(
        new SimpleFeatureListAppliedMethod(new LipidAnnotationModule(), parameters,
            Instant.EPOCH.plusSeconds(1)));
    // populates the stored score that the dashboard reads
    LipidQcScoringUtils.rescoreOverallQualityScores(featureList);
    Assertions.assertNotNull(match.getScore());
    Assertions.assertEquals(expected, match.getScore(), FLOAT_DELTA);

    final var result = computeQualityResult(featureList, row, false);
    Assertions.assertEquals(expected, result.qualityCards().getFirst().overall(), FLOAT_DELTA);
    if (expected < 0.5d) {
      Assertions.assertEquals("overall quality %.0f%% is below 50%%".formatted(expected * 100d),
          result.falsePositiveReason());
    } else {
      Assertions.assertNull(result.falsePositiveReason());
    }
  }

  /**
   * Without a stored score (e.g., older projects) the card computes the score with the latest
   * annotation settings.
   */
  @Test
  void dashboardComputesScoreIfNoneIsStored() {
    final var featureList = new ModularFeatureList("Lipid QC", null, List.of());
    final var row = new ModularFeatureListRow(featureList, 1);
    final var annotation = pc341();
    final double mz = protonatedMz(annotation);
    final var match = new MatchedLipid(annotation, mz, IonizationType.POSITIVE_HYDROGEN, Set.of(),
        0d);
    row.setAverageMZ(mz);
    row.set(LipidMatchListType.class, List.of(match));
    featureList.addRow(row);

    final var parameters = new LipidAnnotationParameters();
    parameters.setParameter(LipidAnnotationParameters.lipidAnalysisType,
        LipidAnalysisType.DIRECT_INFUSION);
    parameters.setParameter(LipidAnnotationParameters.searchForMSMSFragments, false);
    parameters.setParameter(LipidAnnotationParameters.customQcWeights, false);
    featureList.addDescriptionOfAppliedTask(
        new SimpleFeatureListAppliedMethod(new LipidAnnotationModule(), parameters, Instant.EPOCH));

    Assertions.assertNull(match.getOverallQualityScore());
    final var result = computeQualityResult(featureList, row, false);
    // default weights without MS2: (40 * MS1 1 + 5 * interference 1) / (40 + 10 + 20 + 5)
    Assertions.assertEquals(0.6d, result.qualityCards().getFirst().overall(), FLOAT_DELTA);
    Assertions.assertNull(match.getOverallQualityScore());
  }

  /**
   * The minimum score filter removes a competing annotation, which changes the interference context
   * of the remaining one. The stored score must stay the score the filter decision was based on,
   * and the dashboard card and warning must show this stored score instead of a live recompute.
   */
  @Test
  void dashboardShowsScoreFromBeforeQualityFilter() {
    final var featureList = new ModularFeatureList("Lipid QC", null, List.of());
    final var row = new ModularFeatureListRow(featureList, 1);
    final var pc = pc341();
    final double mz = protonatedMz(pc);
    final var pcMatch = new MatchedLipid(pc, mz, IonizationType.POSITIVE_HYDROGEN, Set.of(), 0d);
    // competing class measured at the PC m/z, so its MS1 score is 0
    final var peMatch = new MatchedLipid(
        LIPID_FACTORY.buildSpeciesLevelLipid(LipidClasses.DIACYLGLYCEROPHOSPHOETHANOLAMINES, 34, 1,
            0), mz, IonizationType.POSITIVE_HYDROGEN, Set.of(), 0d);
    row.setAverageMZ(mz);
    row.set(LipidMatchListType.class, List.of(pcMatch, peMatch));
    row.set(PreferredAnnotationType.class, pcMatch);
    featureList.addRow(row);

    final var parameters = new LipidAnnotationParameters();
    parameters.setParameter(LipidAnnotationParameters.lipidAnalysisType,
        LipidAnalysisType.LC_REVERSED_PHASE);
    parameters.setParameter(LipidAnnotationParameters.searchForMSMSFragments, true);
    parameters.setParameter(LipidAnnotationParameters.customQcWeights, false);
    featureList.addDescriptionOfAppliedTask(
        new SimpleFeatureListAppliedMethod(new LipidAnnotationModule(), parameters, Instant.EPOCH));
    final LipidQcScoringSettings settings = LipidQcScoringSettings.fromParameters(parameters);
    Assertions.assertTrue(settings.includeElutionOrderScore());

    final double scoreWithCompetitor = LipidQcScoringUtils.computeCombinedAnnotationScore(
        featureList, row, pcMatch, settings);
    LipidQcScoringUtils.computeAndStoreOverallQualityScores(featureList, settings, 0.1d);

    Assertions.assertEquals(List.of(pcMatch), row.getLipidMatches());
    Assertions.assertNotNull(pcMatch.getOverallQualityScore());
    Assertions.assertEquals(scoreWithCompetitor, pcMatch.getOverallQualityScore(), FLOAT_DELTA);
    final double liveScore = LipidQcScoringUtils.computeCombinedAnnotationScore(featureList, row,
        pcMatch, settings);
    Assertions.assertTrue(liveScore > scoreWithCompetitor + 1e-3,
        "removing the competing class should lower the interference penalty");

    final var result = computeQualityResult(featureList, row, true);
    Assertions.assertEquals(scoreWithCompetitor, result.qualityCards().getFirst().overall(),
        FLOAT_DELTA);
    Assertions.assertNotNull(result.falsePositiveReason());
    Assertions.assertTrue(result.falsePositiveReason().contains(
        "overall quality %.0f%% is below 50%%".formatted(scoreWithCompetitor * 100d)));
  }

  private static @NotNull QualityComputationResult computeQualityResult(
      final @NotNull ModularFeatureList featureList, final @NotNull ModularFeatureListRow row,
      final boolean retentionTimeAnalysis) {
    final var model = new AnnotationQualityModel();
    model.setFeatureList(featureList);
    model.setRow(row);
    model.setRetentionTimeAnalysisEnabled(retentionTimeAnalysis);
    model.setKendrickReviewMode(KendrickReviewMode.POTENTIAL_FALSE_POSITIVE);
    final var task = new QualityComputationTask(model);
    task.process();
    task.updateGuiModel();
    final var result = model.getQualityResult();
    Assertions.assertNotNull(result);
    return result;
  }

  private static @NotNull ILipidAnnotation pc341() {
    return LIPID_FACTORY.buildSpeciesLevelLipid(LipidClasses.DIACYLGLYCEROPHOSPHOCHOLINES, 34, 1,
        0);
  }

  private static double protonatedMz(final @NotNull ILipidAnnotation annotation) {
    return FormulaUtils.getMonoisotopicMass(annotation.getMolecularFormula())
        + IonizationType.POSITIVE_HYDROGEN.getAddedMass();
  }
}
