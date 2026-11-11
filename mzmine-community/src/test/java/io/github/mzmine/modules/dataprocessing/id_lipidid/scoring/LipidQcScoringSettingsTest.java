package io.github.mzmine.modules.dataprocessing.id_lipidid.scoring;

import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnalysisType;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnnotationModule;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnnotationParameters;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidQcWeightParameters;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LipidQcScoringSettingsTest {

  @Test
  void fromParametersReadsAllScoringSettings() {
    final var parameters = new LipidAnnotationParameters();
    parameters.setParameter(LipidAnnotationParameters.lipidAnalysisType,
        LipidAnalysisType.DIRECT_INFUSION);
    parameters.setParameter(LipidAnnotationParameters.searchForMSMSFragments, false);
    final var tolerance = new MZTolerance(0.005, 10d);
    parameters.setParameter(LipidAnnotationParameters.mzTolerance, tolerance);
    parameters.setParameter(LipidAnnotationParameters.customQcWeights, true);
    parameters.getParameter(LipidAnnotationParameters.customQcWeights).getEmbeddedParameters()
        .setParameter(LipidQcWeightParameters.ms1Weight, 7);

    final var settings = LipidQcScoringSettings.fromParameters(parameters);
    Assertions.assertEquals(LipidAnalysisType.DIRECT_INFUSION, settings.analysisType());
    Assertions.assertFalse(settings.includeMs2Score());
    Assertions.assertFalse(settings.includeElutionOrderScore());
    Assertions.assertEquals(7d, settings.weights().ms1Weight());
    Assertions.assertEquals(tolerance, settings.ms1Tolerance());
  }

  @Test
  void fromParametersUsesDefaultWeightsIfCustomWeightsAreDisabled() {
    final var parameters = new LipidAnnotationParameters();
    parameters.setParameter(LipidAnnotationParameters.lipidAnalysisType,
        LipidAnalysisType.LC_HILIC);
    parameters.setParameter(LipidAnnotationParameters.customQcWeights, false);
    parameters.getParameter(LipidAnnotationParameters.customQcWeights).getEmbeddedParameters()
        .setParameter(LipidQcWeightParameters.ms1Weight, 7);

    final var settings = LipidQcScoringSettings.fromParameters(parameters);
    Assertions.assertTrue(settings.includeElutionOrderScore());
    Assertions.assertEquals(LipidQcScoringUtils.defaultComponentWeights(LipidAnalysisType.LC_HILIC),
        settings.weights());
  }

  @Test
  void fromFeatureListUsesLatestAnnotationOrNull() {
    final var featureList = new ModularFeatureList("Lipid QC", null, List.of());
    Assertions.assertNull(LipidQcScoringSettings.fromFeatureList(featureList));
    Assertions.assertEquals(LipidQcScoringSettings.defaults(),
        LipidQcScoringSettings.fromFeatureListOrDefaults(featureList));

    final var parameters = new LipidAnnotationParameters();
    parameters.setParameter(LipidAnnotationParameters.lipidAnalysisType,
        LipidAnalysisType.LC_REVERSED_PHASE);
    featureList.addDescriptionOfAppliedTask(
        new SimpleFeatureListAppliedMethod(new LipidAnnotationModule(), parameters, Instant.EPOCH));
    parameters.setParameter(LipidAnnotationParameters.lipidAnalysisType,
        LipidAnalysisType.IMAGING);
    featureList.addDescriptionOfAppliedTask(
        new SimpleFeatureListAppliedMethod(new LipidAnnotationModule(), parameters,
            Instant.EPOCH.plusSeconds(1)));

    final var settings = LipidQcScoringSettings.fromFeatureList(featureList);
    Assertions.assertNotNull(settings);
    Assertions.assertEquals(LipidAnalysisType.IMAGING, settings.analysisType());
  }

  @Test
  void withersOnlyChangeTheirComponent() {
    final var defaults = LipidQcScoringSettings.defaults();
    final var withoutMs2 = defaults.withIncludeMs2Score(false);
    Assertions.assertFalse(withoutMs2.includeMs2Score());
    Assertions.assertEquals(defaults.weights(), withoutMs2.weights());

    final var directInfusion = defaults.withAnalysisType(LipidAnalysisType.DIRECT_INFUSION);
    Assertions.assertFalse(directInfusion.includeElutionOrderScore());
    Assertions.assertTrue(directInfusion.includeMs2Score());
  }
}
