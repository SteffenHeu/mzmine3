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
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.datamodel.features.types.DataTypes;
import io.github.mzmine.datamodel.features.types.numbers.NormalizedAreaType;
import io.github.mzmine.datamodel.features.types.numbers.NormalizedHeightType;
import io.github.mzmine.modules.dataprocessing.norm_intensity.IntensityNormalizationSummaryStep.Type;
import io.github.mzmine.modules.visualization.projectmetadata.table.MetadataTable;
import io.github.mzmine.modules.visualization.projectmetadata.table.MetadataTableUtils;
import io.github.mzmine.modules.visualization.projectmetadata.table.MetadataTableUtils.InterpolationWeights;
import io.github.mzmine.modules.visualization.projectmetadata.table.columns.MetadataColumn;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.OriginalFeatureListHandlingParameter.OriginalFeatureListOption;
import io.github.mzmine.parameters.parametertypes.submodules.ValueWithParameters;
import io.github.mzmine.project.ProjectService;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.FeatureListUtils;
import io.github.mzmine.util.MathUtils;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.StringUtils;
import io.github.mzmine.util.objects.ObjectUtils;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class IntensityNormalizerTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(IntensityNormalizerTask.class.getName());

  private final OriginalFeatureListOption handleOriginal;

  private final MZmineProject project;
  private final ModularFeatureList originalFeatureList;
  private ModularFeatureList normalizedFeatureList;

  private final long totalFiles;
  private long processedFiles;

  private final String suffix;

  private final boolean intraBatchCorrectionEnabled;
  private final NormalizationType normalizationType;
  private final NormalizationTypeModule normalizationTypeModule;
  private final ParameterSet normalizationTypeModuleParameters;
  private final ParameterSet mainParameters;

  // Pre-normalization: metadata (dilution factor, sample weight, injection volume)
  private final boolean byMetadataEnabled;
  private final MetadataNormalizationConfig byMetadataColumn;

  // Pre-normalization: internal standards
  private final NormalizationType internalStandardNormalizer;
  private final boolean internalStandardEnabled;
  private final ParameterSet internalStandardParams;

  // Batch-aware main normalization
  private final @Nullable String batchIdColumn;
  private final boolean batchIdEnabled;

  private int totalNormalizationSteps;

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
    intraBatchCorrectionEnabled = normalizationType.isActive();
    normalizationTypeModule = normalizationType.getModuleInstance();
    normalizationTypeModuleParameters = normalizationTypeWithParameters.parameters();

    handleOriginal = parameters.getParameter(IntensityNormalizerParameters.handleOriginal)
        .getValue();
    totalFiles = originalFeatureList.getNumberOfRawDataFiles();

    // Pre-normalization: metadata (optional)
    final var preMetaParam = parameters.getParameter(
        IntensityNormalizerParameters.metadataNormFactorCol);
    byMetadataEnabled = preMetaParam.getValue();
    byMetadataColumn = byMetadataEnabled ? preMetaParam.getEmbeddedParameter().getValue() : null;

    // Pre-normalization: internal standards (optional)
    final var internalParam = parameters.getParameter(
        IntensityNormalizerParameters.internalStandardization).getValueWithParameters();
    internalStandardNormalizer = internalParam.value();
    internalStandardEnabled = internalStandardNormalizer.isActive();
    internalStandardParams = internalStandardEnabled ? internalParam.parameters() : null;

    // Batch-aware main normalization
    batchIdColumn = parameters.getOptionalValue(IntensityNormalizerParameters.batchIdColumn)
        .map(String::strip).filter(StringUtils::hasValue).orElse(null);
    batchIdEnabled = batchIdColumn != null;

    totalNormalizationSteps = ObjectUtils.countTrue(byMetadataEnabled, internalStandardEnabled,
        intraBatchCorrectionEnabled);
  }

  public double getFinishedPercentage() {
    return (double) processedFiles / (double) totalFiles / (double) totalNormalizationSteps;
  }

  public String getTaskDescription() {
    return "Intensity normalization of " + originalFeatureList + " by " + normalizationType;
  }

  public void run() {

    setStatus(TaskStatus.PROCESSING);
    logger.info("Running Intensity normalizer");

    // build up summary as we go
    final IntensityNormalizationSummary summary = new IntensityNormalizationSummary(
        new ArrayList<>(totalNormalizationSteps));

    // create copy or prepare in place featurelist
    prepareNormalizedFeatureList();

    final MetadataTable metadata = ProjectService.getMetadata();

    // split samples into batches so that the QC of 2nd batch does not influence the first batch
    final @NotNull List<SamplesBatch> sampleBatches = splitSampleBatches(metadata);
    totalNormalizationSteps += sampleBatches.size()<=1 ? 0 : 1;

    for (SamplesBatch samplesBatch : sampleBatches) {
      normalizeSamplesBatch(samplesBatch, summary);
      if (isCanceled()) {
        return;
      }
    }

    // only if intra batch correction is enabled then also apply inter batch correction
    if (intraBatchCorrectionEnabled && sampleBatches.size() > 1) {
      // inter batch normalization by median intensities in reference samples
      normalizeSamplesInterBatches(sampleBatches, summary);
    }

    if (isCanceled()) {
      return;
    }
    final ParameterSet appliedMethodParameters = mainParameters.cloneParameterSet(true);
    appliedMethodParameters.setParameter(IntensityNormalizerParameters.hiddenNormalizationSummary,
        summary);

    normalizedFeatureList.addDescriptionOfAppliedTask(new SimpleFeatureListAppliedMethod(
        "Intensity normalization by " + normalizationType + (batchIdEnabled
            ? " (batch-aware; column %s)".formatted(batchIdColumn) : "") + (byMetadataEnabled
            ? ", pre: metadata (%s)".formatted(byMetadataColumn) : "") + (internalStandardEnabled
            ? ", pre: IS" : ""), IntensityNormalizerModule.class, appliedMethodParameters,
        getModuleCallDate()));

    // Add normalized feature list to the project.
    handleOriginal.reflectNewFeatureListToProject(suffix, project, normalizedFeatureList,
        originalFeatureList);

    logger.info("Finished intensity normalization");
    setStatus(TaskStatus.FINISHED);
  }

  /**
   * Normalize by median intensity of reference samples inter batch
   *
   * @param sampleBatches
   * @param summary
   */
  private void normalizeSamplesInterBatches(@NotNull List<SamplesBatch> sampleBatches,
      IntensityNormalizationSummary summary) {
    final MetadataTable metadata = ProjectService.getMetadata();

    // norm factors are already set by
    final double[] batchNormMetrics = sampleBatches.stream()
        .mapToDouble(SamplesBatch::getMedianReferenceNormMetric).filter(Double::isFinite).toArray();

    // values might be NaN if they are unset
    if (batchNormMetrics.length != sampleBatches.size()) {
      logger.info(
          "Skipping inter batch correction as it seems to be not supported by this batch normalizer: "
              + normalizationTypeModule.getName());
      return;
    }

    logger.info("Normalizing samples inter batches n=" + sampleBatches.size());
    final double interBatchMedian = MathUtils.calcMedian(batchNormMetrics);

    final Map<RawDataFile, NormalizationFunction> functions = HashMap.newHashMap(
        normalizedFeatureList.getRawDataFiles().size());

    // apply those to all samples in the same batch
    for (SamplesBatch sampleBatch : sampleBatches) {
      final double normFactor = interBatchMedian / sampleBatch.getMedianReferenceNormMetric();
      logger.fine("Normalizing %d samples in batch %s inter batches norm metric=%.4f".formatted(
          sampleBatch.size(), sampleBatch.getGroupMetadataValueStr(), normFactor));

      for (RawDataFile raw : sampleBatch.getRaws()) {

        final LocalDateTime runDate = MetadataTableUtils.getRunDate(metadata, raw);
        final FactorNormalizationFunction fileFunction = new FactorNormalizationFunction(raw,
            runDate, normFactor);
        functions.put(raw, fileFunction);
      }
    }

    applyFunctionsToFeatures(normalizedFeatureList, functions);
    summary.steps().add(
        new IntensityNormalizationSummaryStep(Type.INTER_BATCH, List.copyOf(functions.values())));
  }

  private void normalizeSamplesBatch(SamplesBatch samplesBatch,
      IntensityNormalizationSummary summary) {
    final MetadataTable metadata = ProjectService.getMetadata();
    // first use height or area for normalization, then switch to normalized height or area
    // in subsequent steps to apply cummulative normalization
    ParameterSet effectiveMainParams = mainParameters;

    // ── Pass 1: pre-normalization by metadata column (dilution factor, sample weight, …) ──
    if (byMetadataEnabled) {
      normalizeByMetadataColumn(samplesBatch, summary, metadata);
      if (isCanceled()) {
        return;
      }
      // use normalized abundance for next steps
      effectiveMainParams = withNormalizedAbundanceMeasure(mainParameters);
    }

    // ── Pass 2: pre-normalization by internal standard compounds ──
    // usually applied by internal standards to each sample
    // but this may be applied to internal standards in QCs and then interpolated to samples
    if (internalStandardEnabled) {
      // Use normalized abundances as base for IS metric computation if pass 1 already ran.
      normalizeSampleInternalStandards(samplesBatch, summary, effectiveMainParams, metadata);
      if (isCanceled()) {
        return;
      }
      effectiveMainParams = withNormalizedAbundanceMeasure(mainParameters);
    }

    // ── Pass 3: main normalization (QC drift correction, optionally batch-aware) ──
    if (intraBatchCorrectionEnabled) {
      try {
        final Map<RawDataFile, NormalizationFunction> mainFunctions = buildAllFileFunctions(
            samplesBatch, normalizationTypeModule, normalizationTypeModuleParameters,
            normalizedFeatureList, effectiveMainParams, metadata);
        applyFunctionsToFeatures(normalizedFeatureList, mainFunctions);
        // Store main normalization functions for gap filling / manual integration re-use.
        summary.steps().add(new IntensityNormalizationSummaryStep(Type.INTRA_BATCH,
            List.copyOf(mainFunctions.values())));

      } catch (IllegalStateException e) {
        error("Error during %s step by %s: ".formatted(
            IntensityNormalizerParameters.normalizationType.getName(),
            normalizationTypeModule.getName()) + e.getMessage());
        return;
      }
      effectiveMainParams = withNormalizedAbundanceMeasure(mainParameters);
    }
  }

  private void normalizeSampleInternalStandards(SamplesBatch samplesBatch,
      IntensityNormalizationSummary summary, ParameterSet effectiveMainParams,
      MetadataTable metadata) {
    try {
      final Map<RawDataFile, NormalizationFunction> functions = buildAllFileFunctions(samplesBatch,
          internalStandardNormalizer.getModuleInstance(), internalStandardParams,
          normalizedFeatureList, effectiveMainParams, metadata);
      applyFunctionsToFeatures(normalizedFeatureList, functions);
      summary.steps().add(new IntensityNormalizationSummaryStep(Type.SAMPLE_INTERNAL,
          List.copyOf(functions.values())));
    } catch (IllegalStateException e) {
      error("Pre-normalization internal standards: " + e.getMessage());
    }
  }

  private void normalizeByMetadataColumn(SamplesBatch samplesBatch,
      IntensityNormalizationSummary summary, MetadataTable metadata) {
    final MetadataColumnNormalizationTypeModule metadataModule = new MetadataColumnNormalizationTypeModule();
    final MetadataColumnNormalizationTypeParameters metadataModuleParams = MetadataColumnNormalizationTypeParameters.create(
        byMetadataColumn);
    // MetadataColumn normalization covers all files (no interpolation needed).
    try {
      final Map<RawDataFile, NormalizationFunction> functions = metadataModule.createReferenceFunctions(
          samplesBatch.getRaws(), normalizedFeatureList, samplesBatch, metadata, mainParameters,
          metadataModuleParams);
      applyFunctionsToFeatures(normalizedFeatureList, functions);
      summary.steps().add(
          new IntensityNormalizationSummaryStep(Type.METADATA, List.copyOf(functions.values())));
    } catch (IllegalStateException e) {
      error("Error during pre-normalization by metadata column (" + byMetadataColumn + "): "
          + e.getMessage());
    }
  }

  private @NotNull List<SamplesBatch> splitSampleBatches(MetadataTable metadata) {
    final List<SamplesBatch> sampleBatches;
    if (batchIdEnabled) {
      final MetadataColumn<?> column = metadata.getColumnByName(batchIdColumn);
      if (column == null) {
        throw new IllegalArgumentException("Batch ID column not found: " + batchIdColumn);
      }
      sampleBatches = metadata.groupFilesByColumnIncludeNull(
              normalizedFeatureList.getRawDataFiles(), column).stream()
          .map(g -> new SamplesBatch(g.files(), g.value())).toList();
    } else {
      // handle all samples as one batch
      sampleBatches = List.of(new SamplesBatch(normalizedFeatureList.getRawDataFiles(), null));
    }
    return sampleBatches;
  }

  private void prepareNormalizedFeatureList() {
    // Create new feature list and copy all rows up front.
    normalizedFeatureList = handleOriginal.isProcessInPlace() ? originalFeatureList
        : FeatureListUtils.createCopy(originalFeatureList, suffix, storage, true);

    final NormalizedAreaType normAreaType = DataTypes.get(NormalizedAreaType.class);
    final NormalizedHeightType normHeightType = DataTypes.get(NormalizedHeightType.class);

    if (normalizedFeatureList.hasFeatureType(normAreaType)) {
      // clear old normalization
      FeatureDataUtils.clearIntensityNormalization(normalizedFeatureList);
    } else {
      // add as feature types and row type will be added as binding
      normalizedFeatureList.addFeatureType(normHeightType);
      normalizedFeatureList.addFeatureType(normAreaType);
    }
  }

  /**
   * Builds a complete file → {@link NormalizationFunction} map for all files in the feature list.
   * Reference files are handled by the module; non-reference files are interpolated between the
   * nearest reference files by acquisition timestamp.
   */
  private @NotNull Map<RawDataFile, NormalizationFunction> buildAllFileFunctions(
      @NotNull SamplesBatch samplesBatch, @NotNull final NormalizationTypeModule module,
      @NotNull final ParameterSet moduleParams, @NotNull final ModularFeatureList featureList,
      @NotNull final ParameterSet effectiveMain, @NotNull final MetadataTable metadata) {

    // select reference samples (like pooled QCs)
    final List<RawDataFile> referenceFiles = module.getReferenceSamples(featureList, samplesBatch,
        moduleParams);
    if (referenceFiles.isEmpty()) {
      throw new IllegalStateException(
          "No reference files found for batch with ID %s for normalization module: %s".formatted(
              samplesBatch.getGroupMetadataValueStr(), module.getName()));
    }

    final Map<RawDataFile, NormalizationFunction> fileToFunction = module.createReferenceFunctions(
        referenceFiles, featureList, samplesBatch, metadata, effectiveMain, moduleParams);

    // Interpolate any files not yet covered (batch-aware path already covers all files).
    for (final RawDataFile fileToInterpolate : samplesBatch.getRaws()) {
      if (fileToFunction.containsKey(fileToInterpolate)) {
        continue;
      }
      final InterpolationWeights result = MetadataTableUtils.extractAcquisitionDateInterpolationWeights(
          fileToInterpolate, referenceFiles, metadata);
      final NormalizationFunction previousFunction = fileToFunction.get(result.previousRun());
      final NormalizationFunction nextFunction = fileToFunction.get(result.nextRun());
      if (previousFunction == null || nextFunction == null) {
        throw new IllegalStateException("No reference normalization functions available for file: "
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
   */
  private void applyFunctionsToFeatures(@NotNull final ModularFeatureList featureList,
      @NotNull final Map<RawDataFile, NormalizationFunction> fileToFunction) {

    // functions may only apply to a sample batch or to all files.
    for (Entry<RawDataFile, NormalizationFunction> entry : fileToFunction.entrySet()) {
      final NormalizationFunction fn = entry.getValue();
      final RawDataFile raw = entry.getKey();
      if (isCanceled()) {
        return;
      }
      for (final FeatureListRow row : featureList.getRows()) {
        final Feature feature = row.getFeature(raw);
        if (!(feature instanceof ModularFeature mfeature)) {
          continue;
        }

        FeatureDataUtils.accumulateNormalization(mfeature, fn);
      }

      // each function is one raw data files finished
      processedFiles++;
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
      case Height, NORMALIZED_HEIGHT -> AbundanceMeasure.NORMALIZED_HEIGHT;
      case Area, NORMALIZED_AREA -> AbundanceMeasure.NORMALIZED_AREA;
    };
    cloned.setParameter(IntensityNormalizerParameters.featureMeasurementType, normalized);
    return cloned;
  }

}
