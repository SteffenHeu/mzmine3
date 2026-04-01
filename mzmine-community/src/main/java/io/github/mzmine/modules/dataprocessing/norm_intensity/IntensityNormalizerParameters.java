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
import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.AbundanceMeasureParameter;
import io.github.mzmine.parameters.parametertypes.HiddenParameter;
import io.github.mzmine.parameters.parametertypes.OptionalParameter;
import io.github.mzmine.parameters.parametertypes.OriginalFeatureListHandlingParameter;
import io.github.mzmine.parameters.parametertypes.OriginalFeatureListHandlingParameter.OriginalFeatureListOption;
import io.github.mzmine.parameters.parametertypes.StringParameter;
import io.github.mzmine.parameters.parametertypes.metadata.MetadataGroupingParameter;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsParameter;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsSelection;
import io.github.mzmine.parameters.parametertypes.submodules.ModuleOptionsEnumComboParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IntensityNormalizerParameters extends SimpleParameterSet {

  public static final FeatureListsParameter featureLists = new FeatureListsParameter();

  public static final StringParameter suffix = new StringParameter("Name suffix",
      "Suffix to be added to feature list name", "norm");

  public static final AbundanceMeasureParameter featureMeasurementType = new AbundanceMeasureParameter(
      AbundanceMeasure.rawValues(), AbundanceMeasure.Height);

  public static final OriginalFeatureListHandlingParameter handleOriginal = new OriginalFeatureListHandlingParameter(
      true, OriginalFeatureListOption.PROCESS_IN_PLACE);

  // ── Pre-normalization step 1: metadata-based (dilution factor, sample weight, injection volume)
  /**
   * Applied first, before IS and QC-based corrections. Each sample is divided by its metadata value
   * (e.g. dilution factor, injection volume, sample weight). either divide or multiply
   */
  public static final OptionalParameter<MetadataNormalizationConfigParameter> metadataNormFactorCol = new OptionalParameter<>(
      new MetadataNormalizationConfigParameter(NormalizationType.MetadataColumn.toString(), """
          Select numeric metadata values used to normalize each raw file. Each data file must have a value in that column.
          Use 0 to disable normalization for that file.
          Choose to divide or multiply the sample intensities, e.g., for divide by weight or multiply by dilution factor.""",
          MetadataNormalizationConfig.getDefault()), false);

  // ── Pre-normalization step 2: internal standard compounds
  /**
   * Applied after metadata normalization. Each feature is corrected by the nearest/weighted
   * internal standard compound(s) in m/z and RT space, correcting for extraction efficiency and
   * matrix effects.
   */
  public static final ModuleOptionsEnumComboParameter<NormalizationType> internalStandardization = new ModuleOptionsEnumComboParameter<>(
      "Sample-internal normalization",
      "Normalize by internal standard compounds before QC drift correction. "
          + "Corrects for extraction efficiency and matrix effects. "
          + "Each feature is corrected by the nearest or weighted IS compound(s).",
      NormalizationType.internalSampleNormalizers(), false);

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

  /**
   * Holds the result of the normalization in a
   * {@link io.github.mzmine.datamodel.features.FeatureList.FeatureListAppliedMethod} so it can
   * later be applied to newly added features by gap filling and manual integration. Use
   * {@link IntensityNormalizerModule#getNormalizationFunctionsOfLatestCallForFile(FeatureList,
   * RawDataFile)} and
   * {@link IntensityNormalizerModule#getNormalizationFunctionsOfLatestCall(FeatureList)} to extract
   * these {@link NormalizationFunction}s.
   */
  public static final HiddenParameter<IntensityNormalizationSimpleSummary> hiddenNormalizationSummary = new HiddenParameter<>(
      new NormalizationFunctionsParameter());

  public IntensityNormalizerParameters() {
    super(new Parameter[]{featureLists, suffix, handleOriginal, featureMeasurementType,
            batchIdColumn, metadataNormFactorCol, internalStandardization, normalizationType,
            hiddenNormalizationSummary},
        "https://mzmine.github.io/mzmine_documentation/module_docs/norm_intensity/norm_intensity.html");
  }

  public static @NotNull IntensityNormalizerParameters create(
      final @NotNull FeatureListsSelection selectedFeatureLists,
      final @NotNull String selectedSuffix, final @Nullable MetadataNormalizationConfig selectedMetadataNorm,
      final @NotNull NormalizationType selectedInternalNorm,
      final @Nullable ParameterSet selectedInternalNormParam,
      final @NotNull NormalizationType selectedNormalizationType,
      final @NotNull ParameterSet selectedNormalizationTypeParameters,
      final @Nullable String selectedBatchIdColumn,
      final @NotNull AbundanceMeasure selectedFeatureMeasurementType,
      final @NotNull OriginalFeatureListOption selectedOriginalFeatureListHandling,
      final @Nullable IntensityNormalizationSimpleSummary normalizationSummary) {
    final IntensityNormalizerParameters parameters = (IntensityNormalizerParameters) new IntensityNormalizerParameters().cloneParameterSet();
    parameters.setParameter(IntensityNormalizerParameters.featureLists, selectedFeatureLists);
    parameters.setParameter(IntensityNormalizerParameters.suffix, selectedSuffix);
    parameters.setParameter(IntensityNormalizerParameters.metadataNormFactorCol,
        selectedMetadataNorm != null, selectedMetadataNorm);

    // internal standards
    final ModuleOptionsEnumComboParameter<NormalizationType> internalNormParent = parameters.getParameter(
        IntensityNormalizerParameters.internalStandardization);
    if (selectedInternalNormParam != null) {
      internalNormParent.setValue(selectedInternalNorm, selectedInternalNormParam);
    } else {
      internalNormParent.setValue(selectedInternalNorm);
    }

    // intra and inter batch correction
    parameters.getParameter(IntensityNormalizerParameters.normalizationType)
        .setValue(selectedNormalizationType,
            selectedNormalizationTypeParameters.cloneParameterSet());
    parameters.setParameter(IntensityNormalizerParameters.batchIdColumn,
        selectedBatchIdColumn != null, selectedBatchIdColumn);
    parameters.setParameter(IntensityNormalizerParameters.featureMeasurementType,
        selectedFeatureMeasurementType);
    parameters.setParameter(IntensityNormalizerParameters.handleOriginal,
        selectedOriginalFeatureListHandling);
    parameters.setParameter(IntensityNormalizerParameters.hiddenNormalizationSummary,
        normalizationSummary);
    return parameters;
  }
}
