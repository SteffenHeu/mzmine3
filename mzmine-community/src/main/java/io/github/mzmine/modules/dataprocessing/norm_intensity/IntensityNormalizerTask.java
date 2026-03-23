/*
 * Copyright (c) 2004-2026 The mzmine Development Team
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

package io.github.mzmine.modules.dataprocessing.norm_intensity;

import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.featuredata.FeatureDataUtils;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.datamodel.features.types.DataTypes;
import io.github.mzmine.datamodel.features.types.numbers.NormalizedAreaType;
import io.github.mzmine.datamodel.features.types.numbers.NormalizedHeightType;
import io.github.mzmine.modules.visualization.projectmetadata.table.MetadataTable;
import io.github.mzmine.modules.visualization.projectmetadata.table.MetadataTableUtils;
import io.github.mzmine.modules.visualization.projectmetadata.table.MetadataTableUtils.InterpolationWeights;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.OriginalFeatureListHandlingParameter.OriginalFeatureListOption;
import io.github.mzmine.parameters.parametertypes.submodules.ValueWithParameters;
import io.github.mzmine.project.ProjectService;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.FeatureListUtils;
import io.github.mzmine.util.MemoryMapStorage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class IntensityNormalizerTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(IntensityNormalizerTask.class.getName());

  private final OriginalFeatureListOption handleOriginal;

  private final MZmineProject project;
  private final ModularFeatureList originalFeatureList;
  private ModularFeatureList normalizedFeatureList;

  private final long totalRows;
  private long processedRows;

  private final String suffix;
  private final NormalizationType normalizationType;
  private final NormalizationTypeModule normalizationTypeModule;
  private final ParameterSet normalizationTypeModuleParameters;
  private final ParameterSet mainParameters;

  // Pre-normalization: metadata (dilution factor, sample weight, injection volume)
  private final boolean preNormMetadataEnabled;
  private final String preNormMetadataColumn;

  // Pre-normalization: internal standards
  private final boolean preNormISEnabled;
  private final StandardCompoundNormalizationTypeParameters preNormISParams;

  // Batch-aware main normalization
  private final boolean batchIdEnabled;

  // Post-normalization: global median
  private final boolean postNormEnabled;
  private final FactorNormalizationModuleParameters postNormParams;

  public IntensityNormalizerTask(MZmineProject project, FeatureList featureList,
      ParameterSet parameters, @Nullable MemoryMapStorage storage,
      @NotNull Instant moduleCallDate) {
    super(storage, moduleCallDate); // no new data stored -> null

    this.project = project;
    this.originalFeatureList = (ModularFeatureList) featureList;
    this.mainParameters = parameters;

    suffix = parameters.getParameter(IntensityNormalizerParameters.suffix).getValue();

    final ValueWithParameters<NormalizationType> normalizationTypeWithParameters = parameters.getParameter(
        IntensityNormalizerParameters.normalizationType).getValueWithParameters();
    normalizationType = normalizationTypeWithParameters.value();
    normalizationTypeModule = normalizationType.getModuleInstance();
    normalizationTypeModuleParameters = normalizationTypeWithParameters.parameters();

    handleOriginal = parameters.getParameter(IntensityNormalizerParameters.handleOriginal)
        .getValue();
    totalRows = originalFeatureList.getNumberOfRows();

    // Pre-normalization: metadata
    final var preMetaParam = parameters.getParameter(
        IntensityNormalizerParameters.preNormMetadata);
    preNormMetadataEnabled = Boolean.TRUE.equals(preMetaParam.getValue());
    preNormMetadataColumn =
        preNormMetadataEnabled ? preMetaParam.getEmbeddedParameter().getValue() : null;

    // Pre-normalization: internal standards
    final var preISParam = parameters.getParameter(
        IntensityNormalizerParameters.preNormInternalStandards);
    preNormISEnabled = Boolean.TRUE.equals(preISParam.getValue());
    preNormISParams = preNormISEnabled ? preISParam.getEmbeddedParameters() : null;

    // Batch-aware main normalization
    final var batchParam = parameters.getParameter(IntensityNormalizerParameters.batchIdColumn);
    batchIdEnabled = Boolean.TRUE.equals(batchParam.getValue());

    // Post-normalization: global median
    final var postParam = parameters.getParameter(IntensityNormalizerParameters.postNormGlobal);
    postNormEnabled = Boolean.TRUE.equals(postParam.getValue());
    postNormParams = postNormEnabled ? postParam.getEmbeddedParameters() : null;
  }

  public double getFinishedPercentage() {
    return (double) processedRows / (double) totalRows;
  }

  public String getTaskDescription() {
    return "Intensity normalization of " + originalFeatureList + " by " + normalizationType;
  }

  public void run() {

    setStatus(TaskStatus.PROCESSING);
    logger.info("Running Intensity normalizer");

    // Create new feature list and copy all rows up front.
    normalizedFeatureList = new ModularFeatureList(originalFeatureList + " " + suffix,
        getMemoryMapStorage(), originalFeatureList.getRawDataFiles());
    FeatureListUtils.transferMetadata(originalFeatureList, normalizedFeatureList, true);

    final NormalizedAreaType normAreaType = DataTypes.get(NormalizedAreaType.class);
    final NormalizedHeightType normHeightType = DataTypes.get(NormalizedHeightType.class);
    normalizedFeatureList.addRowType(normHeightType);
    normalizedFeatureList.addRowType(normAreaType);

    for (final FeatureListRow originalRow : originalFeatureList.getRowsCopy()) {
      if (isCanceled()) {
        return;
      }
      final ModularFeatureListRow newRow = new ModularFeatureListRow(normalizedFeatureList,
          (ModularFeatureListRow) originalRow, true);
      normalizedFeatureList.addRow(newRow);
    }

    final MetadataTable metadata = ProjectService.getMetadata();

    // ── Pass 1: pre-normalization by metadata column (dilution factor, sample weight, …) ──
    if (preNormMetadataEnabled) {
      final MetadataColumnNormalizationTypeModule metadataModule = new MetadataColumnNormalizationTypeModule();
      final MetadataColumnNormalizationTypeParameters metadataModuleParams = MetadataColumnNormalizationTypeParameters.create(
          preNormMetadataColumn);
      // MetadataColumn normalization covers all files (no interpolation needed).
      final List<RawDataFile> allFiles = normalizedFeatureList.getRawDataFiles();
      final Map<RawDataFile, NormalizationFunction> functions;
      try {
        functions = metadataModule.createReferenceFunctions(allFiles, normalizedFeatureList,
            metadata, mainParameters, metadataModuleParams);
      } catch (IllegalStateException e) {
        error("Pre-normalization metadata: " + e.getMessage());
        return;
      }
      applyFunctionsToFeatures(normalizedFeatureList, functions, false);
      if (isCanceled()) {
        return;
      }
    }

    // ── Pass 2: pre-normalization by internal standard compounds ──
    if (preNormISEnabled) {
      final StandardCompoundNormalizationTypeModule isModule = new StandardCompoundNormalizationTypeModule();
      // Use normalized abundances as base for IS metric computation if pass 1 already ran.
      final ParameterSet effectiveMain = preNormMetadataEnabled ? withNormalizedAbundanceMeasure(
          mainParameters) : mainParameters;
      final Map<RawDataFile, NormalizationFunction> functions;
      try {
        functions = buildAllFileFunctions(isModule, preNormISParams, normalizedFeatureList,
            effectiveMain, metadata);
      } catch (IllegalStateException e) {
        error("Pre-normalization internal standards: " + e.getMessage());
        return;
      }
      applyFunctionsToFeatures(normalizedFeatureList, functions, false);
      if (isCanceled()) {
        return;
      }
    }

    // ── Pass 3: main normalization (QC drift correction, optionally batch-aware) ──
    {
      final boolean priorPassRan = preNormMetadataEnabled || preNormISEnabled;
      final ParameterSet effectiveMain = priorPassRan ? withNormalizedAbundanceMeasure(
          mainParameters) : mainParameters;
      final Map<RawDataFile, NormalizationFunction> mainFunctions;
      try {
        mainFunctions = buildAllFileFunctions(normalizationTypeModule,
            normalizationTypeModuleParameters, normalizedFeatureList, effectiveMain, metadata);
      } catch (IllegalStateException e) {
        error("Main normalization: " + e.getMessage());
        return;
      }
      applyFunctionsToFeatures(normalizedFeatureList, mainFunctions, true);
      if (isCanceled()) {
        return;
      }

      // Store main normalization functions for gap filling / manual integration re-use.
      final List<NormalizationFunction> finalNormalizationFunctions = new ArrayList<>(
          originalFeatureList.getNumberOfRawDataFiles());
      for (final RawDataFile rawDataFile : originalFeatureList.getRawDataFiles()) {
        final NormalizationFunction fn = mainFunctions.get(rawDataFile);
        if (fn == null) {
          throw new IllegalStateException(
              "No normalization function available for file: " + rawDataFile.getName());
        }
        finalNormalizationFunctions.add(fn);
      }

      final ParameterSet appliedMethodParameters = mainParameters.cloneParameterSet(true);
      appliedMethodParameters.setParameter(IntensityNormalizerParameters.normalizationFunctions,
          List.copyOf(finalNormalizationFunctions));

      normalizedFeatureList.addDescriptionOfAppliedTask(
          new SimpleFeatureListAppliedMethod(
              "Intensity normalization by " + normalizationType
                  + (batchIdEnabled ? " (batch-aware)" : "")
                  + (preNormMetadataEnabled ? ", pre: metadata" : "")
                  + (preNormISEnabled ? ", pre: IS" : "")
                  + (postNormEnabled ? ", post: global median" : ""),
              IntensityNormalizerModule.class, appliedMethodParameters, getModuleCallDate()));
    }

    // ── Pass 4: post-normalization — global median across all samples ──
    if (postNormEnabled) {
      final MedianFeatureIntensityNormalizationTypeModule medianModule = new MedianFeatureIntensityNormalizationTypeModule();
      // Post step uses normalized abundances and runs on ALL sample types (no batch logic).
      final ParameterSet effectiveMain = withNormalizedAbundanceMeasureNoBatch(mainParameters);
      final Map<RawDataFile, NormalizationFunction> functions;
      try {
        functions = buildAllFileFunctions(medianModule, postNormParams, normalizedFeatureList,
            effectiveMain, metadata);
      } catch (IllegalStateException e) {
        error("Post-normalization global median: " + e.getMessage());
        return;
      }
      applyFunctionsToFeatures(normalizedFeatureList, functions, false);
      if (isCanceled()) {
        return;
      }
    }

    // Add normalized feature list to the project.
    handleOriginal.reflectNewFeatureListToProject(suffix, project, normalizedFeatureList,
        originalFeatureList);

    logger.info("Finished intensity normalization");
    setStatus(TaskStatus.FINISHED);
  }

  /**
   * Builds a complete file → {@link NormalizationFunction} map for all files in the feature list.
   * Reference files are handled by the module; non-reference files are interpolated between the
   * nearest reference files by acquisition timestamp.
   */
  private @NotNull Map<RawDataFile, NormalizationFunction> buildAllFileFunctions(
      @NotNull final NormalizationTypeModule module, @NotNull final ParameterSet moduleParams,
      @NotNull final ModularFeatureList featureList, @NotNull final ParameterSet effectiveMain,
      @NotNull final MetadataTable metadata) {

    final List<RawDataFile> referenceFiles = module.getReferenceSamples(featureList, moduleParams);
    if (referenceFiles.isEmpty()) {
      throw new IllegalStateException(
          "No reference files found for normalization module: " + module.getName());
    }

    final Map<RawDataFile, NormalizationFunction> fileToFunction = module.createReferenceFunctions(
        referenceFiles, featureList, metadata, effectiveMain, moduleParams);

    // Interpolate any files not yet covered (batch-aware path already covers all files).
    for (final RawDataFile fileToInterpolate : featureList.getRawDataFiles()) {
      if (fileToFunction.containsKey(fileToInterpolate)) {
        continue;
      }
      final InterpolationWeights result = MetadataTableUtils.extractAcquisitionDateInterpolationWeights(
          fileToInterpolate, referenceFiles, metadata);
      final NormalizationFunction previousFunction = fileToFunction.get(result.previousRun());
      final NormalizationFunction nextFunction = fileToFunction.get(result.nextRun());
      if (previousFunction == null || nextFunction == null) {
        throw new IllegalStateException(
            "No reference normalization functions available for file: "
                + fileToInterpolate.getName());
      }
      fileToFunction.put(fileToInterpolate,
          module.createInterpolatedFunction(fileToInterpolate, previousFunction, nextFunction,
              result, metadata, effectiveMain, moduleParams));
    }

    return fileToFunction;
  }

  /**
   * Applies the normalization functions to all features in the feature list, accumulating on top of
   * any previously applied normalization pass.
   *
   * @param trackProgress when {@code true}, increments {@link #processedRows} per row so that the
   *                      progress bar is updated. Only one pass should set this to {@code true}.
   */
  private void applyFunctionsToFeatures(@NotNull final ModularFeatureList featureList,
      @NotNull final Map<RawDataFile, NormalizationFunction> fileToFunction,
      final boolean trackProgress) {
    for (final FeatureListRow row : featureList.getRowsCopy()) {
      if (isCanceled()) {
        return;
      }
      for (final ModularFeature feature : ((ModularFeatureListRow) row).getFeatures()) {
        final RawDataFile file = feature.getRawDataFile();
        final NormalizationFunction fn = fileToFunction.get(file);
        if (fn == null) {
          throw new IllegalStateException(
              "No normalization function available for file: " + file.getName());
        }
        FeatureDataUtils.accumulateNormalization(feature, fn);
      }
      if (trackProgress) {
        processedRows++;
      }
    }
  }

  /**
   * Returns a clone of {@code mainParameters} with the feature measurement type switched from
   * Height/Area to NormalizedHeight/NormalizedArea. Used for passes 2+ so that modules read the
   * already-normalized values produced by prior passes.
   */
  private @NotNull ParameterSet withNormalizedAbundanceMeasure(
      @NotNull final ParameterSet mainParameters) {
    final ParameterSet cloned = mainParameters.cloneParameterSet();
    final AbundanceMeasure original = mainParameters.getValue(
        IntensityNormalizerParameters.featureMeasurementType);
    final AbundanceMeasure normalized = switch (original) {
      case Height -> AbundanceMeasure.NORMALIZED_HEIGHT;
      case Area -> AbundanceMeasure.NORMALIZED_AREA;
      default -> original; // already a normalized measure
    };
    cloned.setParameter(IntensityNormalizerParameters.featureMeasurementType, normalized);
    return cloned;
  }

  /**
   * Like {@link #withNormalizedAbundanceMeasure(ParameterSet)} but also disables
   * {@code batchIdColumn} so the post-normalization global step is never batch-scoped.
   */
  private @NotNull ParameterSet withNormalizedAbundanceMeasureNoBatch(
      @NotNull final ParameterSet mainParameters) {
    final ParameterSet cloned = withNormalizedAbundanceMeasure(mainParameters);
    cloned.getParameter(IntensityNormalizerParameters.batchIdColumn).setValue(false);
    return cloned;
  }
}
