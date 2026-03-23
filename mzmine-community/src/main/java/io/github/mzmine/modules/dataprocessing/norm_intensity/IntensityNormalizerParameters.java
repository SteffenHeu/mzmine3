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
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.modules.visualization.projectmetadata.ProjectMetadataColumnParameters.AvailableTypes;
import io.github.mzmine.modules.visualization.projectmetadata.SampleType;
import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.ComboParameter;
import io.github.mzmine.parameters.parametertypes.HiddenParameter;
import io.github.mzmine.parameters.parametertypes.OptionalParameter;
import io.github.mzmine.parameters.parametertypes.OriginalFeatureListHandlingParameter;
import io.github.mzmine.parameters.parametertypes.OriginalFeatureListHandlingParameter.OriginalFeatureListOption;
import io.github.mzmine.parameters.parametertypes.StringParameter;
import io.github.mzmine.parameters.parametertypes.metadata.MetadataGroupingParameter;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsParameter;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsSelection;
import io.github.mzmine.parameters.parametertypes.submodules.ModuleOptionsEnumComboParameter;
import io.github.mzmine.parameters.parametertypes.submodules.OptionalModuleParameter;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IntensityNormalizerParameters extends SimpleParameterSet {

  public static final FeatureListsParameter featureLists = new FeatureListsParameter();

  public static final StringParameter suffix = new StringParameter("Name suffix",
      "Suffix to be added to feature list name", "norm");

  public static final ComboParameter<AbundanceMeasure> featureMeasurementType = new ComboParameter<AbundanceMeasure>(
      "Feature measurement type", "Measure features using",
      List.of(AbundanceMeasure.Area, AbundanceMeasure.Height), AbundanceMeasure.Height);

  public static final OriginalFeatureListHandlingParameter handleOriginal = new OriginalFeatureListHandlingParameter(
      "Original feature list",
      "Defines the processing.\nKEEP is to keep the original feature list and create a new"
          + "processed list.\nREMOVE saves memory.", false);

  // ── Pre-normalization step 1: metadata-based (dilution factor, sample weight, injection volume)
  /**
   * Applied first, before IS and QC-based corrections. Each sample is divided by its metadata
   * value (e.g. dilution factor, injection volume, sample weight).
   */
  public static final OptionalParameter<MetadataGroupingParameter> preNormMetadata = new OptionalParameter<>(
      new MetadataGroupingParameter(NormalizationType.MetadataColumn.toString(), """
          Select numeric metadata values used to normalize each raw file. Each data file must have a value in that column.
          Use 0 to disable normalization for that file.""", AvailableTypes.NUMBER), false);

  // ── Pre-normalization step 2: internal standard compounds
  /**
   * Applied after metadata normalization. Each feature is corrected by the nearest/weighted
   * internal standard compound(s) in m/z and RT space, correcting for extraction efficiency and
   * matrix effects.
   */
  public static final ModuleOptionsEnumComboParameter<NormalizationType> preNormInternalStandards = new ModuleOptionsEnumComboParameter<>(
      "Sample-internal normalization",
      "Normalize by internal standard compounds before QC drift correction. "
          + "Corrects for extraction efficiency and matrix effects. "
          + "Each feature is corrected by the nearest or weighted IS compound(s).",
      NormalizationType.internalSampleNormalizers());

  // ── Main normalization: QC-based signal drift correction
  public static final ModuleOptionsEnumComboParameter<NormalizationType> normalizationType = new ModuleOptionsEnumComboParameter<>(
      "Sample batch correction", """
      Runs after metadata sample injection normalization and after internal standards normalization.
      Normalizes intensities intra-batch by the selected algorithm and then inter-batch.
      Algorithms may use QCs like pooled QCs to correct drifts within a sample batch and between batches. The correction is then applied to all samples.
      Inter-batch normalization is performed by aligning the QC median of each batch to the global QC median.""",
      NormalizationType.intraBatchDriftNormalizers());

  /**
   * When set, QC drift correction is computed independently per batch (intra-batch correction),
   * then batches are aligned to the global QC median (inter-batch correction / pooled QC
   * normalization). Without a batch ID, all QC samples form one continuous reference sequence.
   */
  public static final OptionalParameter<MetadataGroupingParameter> batchIdColumn = new OptionalParameter<>(
      new MetadataGroupingParameter("Batch ID metadata column", """
          Metadata text column identifying the analytical batch (samples measured under comparable conditions).
          When set, QC drift correction is applied within each batch separately, \
          then batches are aligned by median QC scaling (pooled QC normalization)."""), false);

  // ── Post-normalization: global median (optional, apply last)
  /**
   * Applied after IS and QC batch correction to remove any remaining sample-to-sample abundance
   * differences. Only appropriate when the total metabolite content is assumed equal across all
   * samples (e.g. technical replicates, same tissue type with same cell count). Do NOT use when
   * global metabolite differences are biologically meaningful.
   */
  public static final OptionalModuleParameter<FactorNormalizationModuleParameters> postNormGlobal = new OptionalModuleParameter<>(
      "Post-normalization: Global median",
      "After IS and QC batch correction, apply median normalization across all samples to "
          + "remove remaining abundance differences. Only use if the total metabolite content "
          + "is assumed equal across all samples (e.g. technical replicates, same tissue type). "
          + "Do NOT use if global metabolite changes are biologically meaningful.",
      createPostNormDefaultParams(), false);

  /**
   * Holds the result of the normalization in a
   * {@link io.github.mzmine.datamodel.features.FeatureList.FeatureListAppliedMethod} so it can
   * later be applied to newly added features by gap filling and manual integration. Use
   * {@link IntensityNormalizerModule#getNormalizationFunctionOfLatestCallForFile(FeatureList,
   * RawDataFile)} and
   * {@link IntensityNormalizerModule#getNormalizationFunctionsOfLatestCall(FeatureList)} to extract
   * these {@link NormalizationFunction}s.
   */
  public static final HiddenParameter<List<NormalizationFunction>> normalizationFunctions = new HiddenParameter<>(
      new NormalizationFunctionsParameter());

  public IntensityNormalizerParameters() {
    super(new Parameter[]{featureLists, suffix, preNormMetadata, preNormInternalStandards,
            normalizationType, batchIdColumn, postNormGlobal, featureMeasurementType,
            handleOriginal, normalizationFunctions},
        "https://mzmine.github.io/mzmine_documentation/module_docs/norm_intensity/norm_intensity.html");
  }

  private static FactorNormalizationModuleParameters createPostNormDefaultParams() {
    return FactorNormalizationModuleParameters.create(List.of(SampleType.values()));
  }

  public static @NotNull IntensityNormalizerParameters create(
      final @NotNull FeatureListsSelection selectedFeatureLists,
      final @NotNull String selectedSuffix, final @Nullable String selectedPreNormMetadata,
      final @Nullable ParameterSet selectedPreNormISParameters,
      final @NotNull NormalizationType selectedNormalizationType,
      final @NotNull ParameterSet selectedNormalizationTypeParameters,
      final @Nullable String selectedBatchIdColumn,
      final @Nullable ParameterSet selectedPostNormParameters,
      final @NotNull AbundanceMeasure selectedFeatureMeasurementType,
      final @NotNull OriginalFeatureListOption selectedOriginalFeatureListHandling,
      final @NotNull List<NormalizationFunction> selectedNormalizationFunctions) {
    final IntensityNormalizerParameters parameters = (IntensityNormalizerParameters) new IntensityNormalizerParameters().cloneParameterSet();
    parameters.setParameter(IntensityNormalizerParameters.featureLists, selectedFeatureLists);
    parameters.setParameter(IntensityNormalizerParameters.suffix, selectedSuffix);
    parameters.setParameter(IntensityNormalizerParameters.preNormMetadata,
        selectedPreNormMetadata != null, selectedPreNormMetadata);
    final var isParam = parameters.getParameter(
        IntensityNormalizerParameters.preNormInternalStandards);
    isParam.setValue(selectedPreNormISParameters != null);
    if (selectedPreNormISParameters != null) {
      isParam.setEmbeddedParameters(
          (StandardCompoundNormalizationTypeParameters) selectedPreNormISParameters);
    }
    parameters.getParameter(IntensityNormalizerParameters.normalizationType)
        .setValue(selectedNormalizationType,
            selectedNormalizationTypeParameters.cloneParameterSet());
    parameters.setParameter(IntensityNormalizerParameters.batchIdColumn,
        selectedBatchIdColumn != null, selectedBatchIdColumn);
    final var postParam = parameters.getParameter(IntensityNormalizerParameters.postNormGlobal);
    postParam.setValue(selectedPostNormParameters != null);
    if (selectedPostNormParameters != null) {
      postParam.setEmbeddedParameters(
          (FactorNormalizationModuleParameters) selectedPostNormParameters);
    }
    parameters.setParameter(IntensityNormalizerParameters.featureMeasurementType,
        selectedFeatureMeasurementType);
    parameters.setParameter(IntensityNormalizerParameters.handleOriginal,
        selectedOriginalFeatureListHandling);
    parameters.setParameter(IntensityNormalizerParameters.normalizationFunctions,
        selectedNormalizationFunctions);
    return parameters;
  }
}
