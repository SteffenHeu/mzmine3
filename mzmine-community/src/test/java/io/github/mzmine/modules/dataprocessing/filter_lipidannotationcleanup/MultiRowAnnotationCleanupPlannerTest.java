package io.github.mzmine.modules.dataprocessing.filter_lipidannotationcleanup;

import io.github.mzmine.datamodel.IonizationType;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnalysisType;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.LipidClasses;
import io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringSettings;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance.Unit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class MultiRowAnnotationCleanupPlannerTest {

  // direct infusion: no elution-order score, so scores only depend on MS1, MS2 and interference
  private static final LipidQcScoringSettings SETTINGS = LipidQcScoringSettings.defaults()
      .withAnalysisType(LipidAnalysisType.DIRECT_INFUSION);
  private static final DuplicateAnnotationScopeFilter ALL_RTS = new DuplicateAnnotationScopeFilter(
      DuplicateAnnotationScope.ALL_RTS, null);

  @Test
  void highestScoreWinsWithoutIonizationPreference() {
    final var featureList = new ModularFeatureList("cleanup", null, List.of());
    final var best = CleanupTestLipids.match(LipidClasses.DIACYLGLYCEROPHOSPHOCHOLINES, 34, 1,
        IonizationType.POSITIVE_HYDROGEN, 0.9, 0);
    final var worse = CleanupTestLipids.match(LipidClasses.DIACYLGLYCEROPHOSPHOCHOLINES, 34, 1,
        IonizationType.POSITIVE_HYDROGEN, 0.1, 0);
    final var bestRow = CleanupTestLipids.row(featureList, 1, null, best);
    final var worseRow = CleanupTestLipids.row(featureList, 2, null, worse);

    final var plan = MultiRowAnnotationCleanupPlanner.buildCleanupPlan(featureList, SETTINGS,
        options(Map.of(), Set.of(), MultiRowAnnotationCleanupRowHandlingMode.DISCARD_LOWER_THAN_REMOVED,
            ALL_RTS));
    Assertions.assertEquals(Map.of(worseRow, Set.of(worse)), plan.annotationsToRemoveByRow());

    MultiRowAnnotationCleanupPlanner.applyCleanupPlan(plan);
    Assertions.assertEquals(List.of(best), bestRow.getLipidMatches());
    Assertions.assertEquals(List.of(), worseRow.getLipidMatches());
  }

  /**
   * The winner must follow the scoring settings: an annotation with good MS2 but poor MS1 accuracy
   * wins with MS2 scoring and loses without.
   *
   * @param includeMs2   whether MS2 is scored
   * @param removedRowId the row that loses the duplicate annotation
   */
  @ParameterizedTest
  @CsvSource({"true, 2", "false, 1"})
  void winnerFollowsScoringSettings(final boolean includeMs2, final int removedRowId) {
    final var featureList = new ModularFeatureList("cleanup", null, List.of());
    // MS1 score 0 with the default 5 ppm scoring tolerance
    CleanupTestLipids.row(featureList, 1, null,
        CleanupTestLipids.match(LipidClasses.DIACYLGLYCEROPHOSPHOCHOLINES, 34, 1,
            IonizationType.POSITIVE_HYDROGEN, 0.9, 10));
    CleanupTestLipids.row(featureList, 2, null,
        CleanupTestLipids.match(LipidClasses.DIACYLGLYCEROPHOSPHOCHOLINES, 34, 1,
            IonizationType.POSITIVE_HYDROGEN, 0, 0));

    final var plan = MultiRowAnnotationCleanupPlanner.buildCleanupPlan(featureList,
        SETTINGS.withIncludeMs2Score(includeMs2),
        options(Map.of(), Set.of(), MultiRowAnnotationCleanupRowHandlingMode.DISCARD_LOWER_THAN_REMOVED,
            ALL_RTS));
    Assertions.assertEquals(1, plan.removedAnnotationCount());
    Assertions.assertEquals(removedRowId,
        plan.annotationsToRemoveByRow().keySet().iterator().next().getID());
  }

  @Test
  void preferredIonizationWinsOverHigherScoreUnlessHighestScoreIsKept() {
    final var featureList = new ModularFeatureList("cleanup", null, List.of());
    final var protonated = CleanupTestLipids.match(LipidClasses.DIACYLGLYCEROPHOSPHOCHOLINES, 34, 1,
        IonizationType.POSITIVE_HYDROGEN, 0.9, 0);
    final var sodiated = CleanupTestLipids.match(LipidClasses.DIACYLGLYCEROPHOSPHOCHOLINES, 34, 1,
        IonizationType.SODIUM, 0.1, 0);
    final var protonatedRow = CleanupTestLipids.row(featureList, 1, null, protonated);
    final var sodiatedRow = CleanupTestLipids.row(featureList, 2, null, sodiated);
    final Map<String, IonizationType> preferSodium = Map.of("PC", IonizationType.SODIUM);

    final var preferencePlan = MultiRowAnnotationCleanupPlanner.buildCleanupPlan(featureList,
        SETTINGS, options(preferSodium, Set.of(),
            MultiRowAnnotationCleanupRowHandlingMode.DISCARD_LOWER_THAN_REMOVED, ALL_RTS));
    Assertions.assertEquals(Map.of(protonatedRow, Set.of(protonated)),
        preferencePlan.annotationsToRemoveByRow());

    final var keepHighestScorePlan = MultiRowAnnotationCleanupPlanner.buildCleanupPlan(featureList,
        SETTINGS, options(preferSodium, Set.of("PC"),
            MultiRowAnnotationCleanupRowHandlingMode.DISCARD_LOWER_THAN_REMOVED, ALL_RTS));
    Assertions.assertEquals(Map.of(sodiatedRow, Set.of(sodiated)),
        keepHighestScorePlan.annotationsToRemoveByRow());
  }

  /**
   * Row 2 loses its PC annotation to row 1 and also carries a lower and a higher scored PE
   * annotation. The row handling mode decides what happens to those.
   *
   * @param mode row handling mode
   */
  @ParameterizedTest
  @EnumSource(MultiRowAnnotationCleanupRowHandlingMode.class)
  void rowHandlingModeTreatsRemainingAnnotations(
      final @NotNull MultiRowAnnotationCleanupRowHandlingMode mode) {
    final var featureList = new ModularFeatureList("cleanup", null, List.of());
    final var winner = CleanupTestLipids.match(LipidClasses.DIACYLGLYCEROPHOSPHOCHOLINES, 34, 1,
        IonizationType.POSITIVE_HYDROGEN, 0.9, 0);
    final var loser = CleanupTestLipids.match(LipidClasses.DIACYLGLYCEROPHOSPHOCHOLINES, 34, 1,
        IonizationType.POSITIVE_HYDROGEN, 0.1, 0);
    final var lowerPe = CleanupTestLipids.match(LipidClasses.DIACYLGLYCEROPHOSPHOETHANOLAMINES, 36,
        2, IonizationType.POSITIVE_HYDROGEN, 0.05, 0);
    final var higherPe = CleanupTestLipids.match(LipidClasses.DIACYLGLYCEROPHOSPHOETHANOLAMINES,
        38, 4, IonizationType.POSITIVE_HYDROGEN, 0.5, 0);
    CleanupTestLipids.row(featureList, 1, null, winner);
    final var mixedRow = CleanupTestLipids.row(featureList, 2, null, loser, lowerPe, higherPe);

    final var plan = MultiRowAnnotationCleanupPlanner.buildCleanupPlan(featureList, SETTINGS,
        options(Map.of(), Set.of(), mode, ALL_RTS));
    MultiRowAnnotationCleanupPlanner.applyCleanupPlan(plan);

    switch (mode) {
      case DISCARD_LOWER_THAN_REMOVED ->
          Assertions.assertEquals(List.of(higherPe), mixedRow.getLipidMatches());
      case DISCARD_ALL_IF_ANY_REMOVED ->
          Assertions.assertEquals(List.of(), mixedRow.getLipidMatches());
      case SELECT_REMAINING_HIGHEST_SCORE -> {
        Assertions.assertEquals(higherPe, plan.selectedRemainingAnnotationByRow().get(mixedRow));
        Assertions.assertEquals(List.of(higherPe, lowerPe), mixedRow.getLipidMatches());
      }
    }
  }

  @Test
  void onlyRowsWithinRtToleranceAreDuplicates() {
    final var featureList = new ModularFeatureList("cleanup", null, List.of());
    final var best = CleanupTestLipids.match(LipidClasses.DIACYLGLYCEROPHOSPHOCHOLINES, 34, 1,
        IonizationType.POSITIVE_HYDROGEN, 0.9, 0);
    final var closeWorse = CleanupTestLipids.match(LipidClasses.DIACYLGLYCEROPHOSPHOCHOLINES, 34,
        1, IonizationType.POSITIVE_HYDROGEN, 0.5, 0);
    final var distant = CleanupTestLipids.match(LipidClasses.DIACYLGLYCEROPHOSPHOCHOLINES, 34, 1,
        IonizationType.POSITIVE_HYDROGEN, 0.1, 0);
    CleanupTestLipids.row(featureList, 1, 1.0f, best);
    final var closeRow = CleanupTestLipids.row(featureList, 2, 1.05f, closeWorse);
    CleanupTestLipids.row(featureList, 3, 5.0f, distant);

    final var withinRt = new DuplicateAnnotationScopeFilter(
        DuplicateAnnotationScope.WITHIN_RT_TOLERANCE, new RTTolerance(0.1f, Unit.MINUTES));
    final var plan = MultiRowAnnotationCleanupPlanner.buildCleanupPlan(featureList, SETTINGS,
        options(Map.of(), Set.of(), MultiRowAnnotationCleanupRowHandlingMode.DISCARD_LOWER_THAN_REMOVED,
            withinRt));
    Assertions.assertEquals(Map.of(closeRow, Set.of(closeWorse)), plan.annotationsToRemoveByRow());
  }

  private static @NotNull MultiRowAnnotationCleanupOptions options(
      final @NotNull Map<String, IonizationType> preferredIonization,
      final @NotNull Set<String> keepHighestScoreByClass,
      final @NotNull MultiRowAnnotationCleanupRowHandlingMode mode,
      final @NotNull DuplicateAnnotationScopeFilter scope) {
    return new MultiRowAnnotationCleanupOptions(preferredIonization, keepHighestScoreByClass, false,
        mode, scope);
  }
}
