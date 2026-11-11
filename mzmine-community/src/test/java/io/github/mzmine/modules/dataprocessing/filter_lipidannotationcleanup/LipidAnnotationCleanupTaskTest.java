package io.github.mzmine.modules.dataprocessing.filter_lipidannotationcleanup;

import io.github.mzmine.datamodel.IonizationType;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnalysisType;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnnotationModule;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnnotationParameters;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.LipidClasses;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LipidAnnotationCleanupTaskTest {

  /**
   * The cleanup ranks duplicates with the MS2 setting of the latest lipid annotation and rescores
   * the stored scores afterwards.
   */
  @Test
  void cleanupUsesAnnotationSettingsAndRescores() {
    final var featureList = new ModularFeatureList("cleanup", null, List.of());
    // good MS2 but MS1 outside the default 5 ppm scoring tolerance
    final var goodMs2 = CleanupTestLipids.match(LipidClasses.DIACYLGLYCEROPHOSPHOCHOLINES, 34, 1,
        IonizationType.POSITIVE_HYDROGEN, 0.9, 10);
    final var exactMs1 = CleanupTestLipids.match(LipidClasses.DIACYLGLYCEROPHOSPHOCHOLINES, 34, 1,
        IonizationType.POSITIVE_HYDROGEN, 0, 0);
    final var goodMs2Row = CleanupTestLipids.row(featureList, 1, null, goodMs2);
    final var exactMs1Row = CleanupTestLipids.row(featureList, 2, null, exactMs1);
    // stale stored scores that must be replaced by the rescoring
    goodMs2.setOverallQualityScore(0.123f);
    exactMs1.setOverallQualityScore(0.123f);

    final var annotationParameters = new LipidAnnotationParameters();
    annotationParameters.setParameter(LipidAnnotationParameters.lipidAnalysisType,
        LipidAnalysisType.DIRECT_INFUSION);
    annotationParameters.setParameter(LipidAnnotationParameters.searchForMSMSFragments, false);
    annotationParameters.setParameter(LipidAnnotationParameters.customQcWeights, false);
    featureList.addDescriptionOfAppliedTask(
        new SimpleFeatureListAppliedMethod(new LipidAnnotationModule(), annotationParameters,
            Instant.EPOCH));

    final var cleanupParameters = new LipidAnnotationCleanupParameters();
    cleanupParameters.setParameter(LipidAnnotationCleanupParameters.ionizationPreferences,
        List.of());
    cleanupParameters.setParameter(LipidAnnotationCleanupParameters.rowHandlingMode,
        MultiRowAnnotationCleanupRowHandlingMode.DISCARD_LOWER_THAN_REMOVED);
    cleanupParameters.setParameter(LipidAnnotationCleanupParameters.lipidAnalysisType,
        LipidAnalysisType.DIRECT_INFUSION);
    cleanupParameters.setParameter(LipidAnnotationCleanupParameters.duplicateScope,
        new DuplicateAnnotationScopeFilter(DuplicateAnnotationScope.ALL_RTS, null));

    final var task = new LipidAnnotationCleanupTask(null, Instant.EPOCH, cleanupParameters,
        LipidAnnotationCleanupModule.class, featureList);
    task.run();
    Assertions.assertEquals(TaskStatus.FINISHED, task.getStatus());

    // without MS2 scoring, MS1 accuracy decides
    Assertions.assertEquals(List.of(), goodMs2Row.getLipidMatches());
    Assertions.assertEquals(List.of(exactMs1), exactMs1Row.getLipidMatches());
    // default weights without MS2: (40 * MS1 1 + 5 * interference 1) / (40 + 10 + 20 + 5)
    Assertions.assertNotNull(exactMs1.getOverallQualityScore());
    Assertions.assertEquals(0.6d, exactMs1.getOverallQualityScore(), 1e-6);
  }
}
