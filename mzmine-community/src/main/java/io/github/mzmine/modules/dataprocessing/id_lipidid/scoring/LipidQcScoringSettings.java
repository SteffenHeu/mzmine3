package io.github.mzmine.modules.dataprocessing.id_lipidid.scoring;

import io.github.mzmine.datamodel.features.FeatureList.FeatureListAppliedMethod;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnalysisType;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnnotationModule;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnnotationParameters;
import io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils.ComponentWeights;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.ParameterUtils;
import io.github.mzmine.parameters.parametertypes.ComboParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.MZToleranceParameter;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Settings that define how the overall lipid annotation quality score is computed. All score
 * computations use this single source so that stored, exported, and displayed scores are based on
 * the same lipid annotation parameters.
 *
 * @param analysisType    lipid analysis mode, decides whether the elution order is scored
 * @param includeMs2Score true to include the MS2 explained-intensity score
 * @param weights         component weights, either custom weights or the defaults
 * @param ms1Tolerance    MS1 tolerance used to scale the MS1 score, or {@code null} for the default
 */
public record LipidQcScoringSettings(@NotNull LipidAnalysisType analysisType,
                                     boolean includeMs2Score, @NotNull ComponentWeights weights,
                                     @Nullable MZTolerance ms1Tolerance) {

  /**
   * Default settings for feature lists without lipid annotation provenance: reversed-phase LC,
   * MS2 included, default weights, default MS1 tolerance.
   *
   * @return default settings
   */
  public static @NotNull LipidQcScoringSettings defaults() {
    return new LipidQcScoringSettings(LipidAnalysisType.LC_REVERSED_PHASE, true,
        LipidQcScoringUtils.defaultComponentWeights(LipidAnalysisType.LC_REVERSED_PHASE), null);
  }

  /**
   * Reads the scoring settings from a {@link LipidAnnotationParameters} set. Missing parameters of
   * older parameter set versions fall back to the values of {@link #defaults()}.
   *
   * @param parameters lipid annotation parameters
   * @return scoring settings
   */
  public static @NotNull LipidQcScoringSettings fromParameters(
      final @NotNull ParameterSet parameters) {
    // assumption: null or missing analysis type was always treated as reversed-phase LC
    final LipidAnalysisType analysisType = Objects.requireNonNullElse(
        parameters.tryGetParameter(LipidAnnotationParameters.lipidAnalysisType)
            .map(ComboParameter::getValue).orElse(null),
        LipidAnalysisType.LC_REVERSED_PHASE);
    final boolean includeMs2Score = parameters.tryGetParameter(
            LipidAnnotationParameters.searchForMSMSFragments)
        .map(parameter -> Boolean.TRUE.equals(parameter.getValue())).orElse(true);
    final ComponentWeights weights = parameters.tryGetParameter(
            LipidAnnotationParameters.customQcWeights)
        .filter(parameter -> Boolean.TRUE.equals(parameter.getValue()))
        .map(parameter -> parameter.getEmbeddedParameters().toComponentWeights(analysisType))
        .orElseGet(() -> LipidQcScoringUtils.defaultComponentWeights(analysisType));
    final MZTolerance ms1Tolerance = parameters.tryGetParameter(
        LipidAnnotationParameters.mzTolerance).map(MZToleranceParameter::getValue).orElse(null);
    return new LipidQcScoringSettings(analysisType, includeMs2Score, weights, ms1Tolerance);
  }

  /**
   * Reads the scoring settings from the latest {@link LipidAnnotationModule} call of the feature
   * list.
   *
   * @param featureList feature list with applied methods
   * @return scoring settings or {@code null} if the feature list was never lipid annotated
   */
  public static @Nullable LipidQcScoringSettings fromFeatureList(
      final @NotNull ModularFeatureList featureList) {
    final FeatureListAppliedMethod method = ParameterUtils.getLatestModuleCall(
        featureList.getAppliedMethods(), LipidAnnotationModule.class);
    // getParameters clones the parameter set, so only call it once
    return method == null ? null : fromParameters(method.getParameters());
  }

  /**
   * Reads the scoring settings from the latest {@link LipidAnnotationModule} call of the feature
   * list or uses {@link #defaults()} if the list was never lipid annotated.
   *
   * @param featureList feature list with applied methods
   * @return scoring settings
   */
  public static @NotNull LipidQcScoringSettings fromFeatureListOrDefaults(
      final @NotNull ModularFeatureList featureList) {
    return Objects.requireNonNullElseGet(fromFeatureList(featureList),
        LipidQcScoringSettings::defaults);
  }

  /**
   * @return true if the analysis mode has a retention-time pattern and the elution order is scored
   */
  public boolean includeElutionOrderScore() {
    return analysisType.hasRetentionTimePattern();
  }

  /**
   * @param includeMs2Score true to include the MS2 explained-intensity score
   * @return copy of these settings with the given MS2 inclusion
   */
  public @NotNull LipidQcScoringSettings withIncludeMs2Score(final boolean includeMs2Score) {
    return new LipidQcScoringSettings(analysisType, includeMs2Score, weights, ms1Tolerance);
  }

  /**
   * Custom weights are kept as they are. Default weights are currently identical for all analysis
   * types.
   *
   * @param analysisType lipid analysis mode
   * @return copy of these settings with the given analysis mode
   */
  public @NotNull LipidQcScoringSettings withAnalysisType(
      final @NotNull LipidAnalysisType analysisType) {
    return new LipidQcScoringSettings(analysisType, includeMs2Score, weights, ms1Tolerance);
  }
}
