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

package io.github.mzmine.modules.dataprocessing.filter_diams2.sliding_mz;

import com.google.common.collect.Range;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.batchmode.BatchQueue;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.minimumsearch.MinimumSearchFeatureResolverParameters;
import io.github.mzmine.modules.dataprocessing.filter_diams2.DiaCorrelationOptions;
import io.github.mzmine.modules.dataprocessing.filter_diams2.DiaMs2CorrModule;
import io.github.mzmine.modules.dataprocessing.filter_diams2.DiaMs2CorrParameters;
import io.github.mzmine.modules.dataprocessing.filter_diams2.no_corr.DiaMs2NoCorrParameters;
import io.github.mzmine.modules.dataprocessing.filter_diams2.rt_corr.DiaMs2RtCorrAdvancedParameters;
import io.github.mzmine.modules.dataprocessing.filter_diams2.rt_corr.DiaMs2RtCorrParameters;
import io.github.mzmine.modules.dataprocessing.id_spectral_library_match.SpectralLibrarySearchParameters;
import io.github.mzmine.modules.impl.MZmineProcessingStepImpl;
import io.github.mzmine.modules.io.import_rawdata_all.AdvancedSpectraImportParameters;
import io.github.mzmine.modules.io.import_rawdata_all.AllSpectralDataImportModule;
import io.github.mzmine.modules.io.import_rawdata_all.AllSpectralDataImportParameters;
import io.github.mzmine.modules.io.spectraldbsubmit.batch.LibraryBatchMetadataParameters;
import io.github.mzmine.modules.tools.batchwizard.WizardPart;
import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.modules.tools.batchwizard.builders.BaseWizardBatchBuilder;
import io.github.mzmine.modules.tools.batchwizard.subparameters.DataImportWizardParameters;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.combowithinput.DefaultOffCustomOption;
import io.github.mzmine.parameters.parametertypes.combowithinput.DefaultOffCustomValue;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsSelection;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsSelectionType;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.submodules.ModuleOptionsEnumComboParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import java.io.File;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the shortest wizard-based batch that produces resolved features and applies a selectable
 * DIA pseudo-MS2 algorithm.
 */
final class DiaSlidingMzBatchBuilder extends BaseWizardBatchBuilder {

  public static final double MZML_DIVISOR = 10d;
  private final Range<Double> importRtRange;
  private final int minRtDataPoints;
  private final RTTolerance rtFwhm;
  private final int maxIsomersInRt;
  private final DiaCorrelationOptions diaAlgorithm;
  private final DiaCorrelationOptions slidingMzPregrouping;
  private final double minMs1Intensity;
  private final double minMs2Intensity;
  private final double minNoCorrelationIntensity;
  private final double minPearson;
  private final int minCorrelatedPoints;
  private final MZTolerance diaMzTolerance;
  private final @Nullable File exportPath;

  DiaSlidingMzBatchBuilder(@NotNull final WizardSequence steps,
      @NotNull final Range<Double> importRtRange, final int minRtDataPoints,
      @NotNull final RTTolerance rtFwhm, final int maxIsomersInRt,
      @NotNull final DiaCorrelationOptions diaAlgorithm,
      @NotNull final DiaCorrelationOptions slidingMzPregrouping, final double minMs1Intensity,
      final double minMs2Intensity, final double minNoCorrelationIntensity, final double minPearson,
      final int minCorrelatedPoints, @NotNull final MZTolerance diaMzTolerance,
      @Nullable final File exportPath) {
    super(steps);
    this.importRtRange = importRtRange;
    this.minRtDataPoints = minRtDataPoints;
    this.rtFwhm = rtFwhm;
    this.maxIsomersInRt = maxIsomersInRt;
    this.diaAlgorithm = diaAlgorithm;
    this.slidingMzPregrouping = slidingMzPregrouping;
    this.minMs1Intensity = minMs1Intensity;
    this.minMs2Intensity = minMs2Intensity;
    this.minNoCorrelationIntensity = minNoCorrelationIntensity;
    this.minPearson = minPearson;
    this.minCorrelatedPoints = minCorrelatedPoints;
    this.diaMzTolerance = diaMzTolerance;
    this.exportPath = exportPath;
  }

  @Override
  protected @NotNull BatchQueue createQueueInternal() {
    final BatchQueue queue = createPreprocessingQueue();
    appendDiaProcessingAndExport(queue, diaAlgorithm, slidingMzPregrouping, minMs2Intensity,
        minNoCorrelationIntensity, exportPath, null);
    return queue;
  }

  @NotNull BatchQueue createQueueForConfigurations(
      @NotNull final List<DiaSlidingMzSweepConfig> configs, @NotNull final File exportDirectory) {
    if (configs.isEmpty()) {
      throw new IllegalArgumentException("At least one DIA configuration is required.");
    }
    double mzmlDivisor = steps.get(WizardPart.DATA_IMPORT)
        .map(p -> p.getValue(DataImportWizardParameters.fileNames)[0].getName().contains("mzML"))
        .orElse(false) ? MZML_DIVISOR : 1;

    final BatchQueue completeQueue = new BatchQueue();
    for (final DiaSlidingMzSweepConfig config : configs) {
      final BatchQueue queue = createPreprocessingQueue();
      final File configExportPath = new File(exportDirectory, config.exportFileStem());
      appendDiaProcessingAndExport(queue, config.diaAlgorithm(), config.slidingMzPregrouping(),
          config.minimumFragmentIntensity() / mzmlDivisor,
          config.minimumFragmentIntensity() / mzmlDivisor, configExportPath, config.name());
      completeQueue.addAll(queue);
    }
    return completeQueue;
  }

  private @NotNull BatchQueue createPreprocessingQueue() {
    final BatchQueue queue = new BatchQueue();
    makeAndAddRtFilteredImportTask(queue);
    makeAndAddMassDetectorSteps(queue);
    makeAndAddAdapChromatogramStep(queue, minFeatureHeight, mzTolScans, massDetectorOption,
        minRtDataPoints, importRtRange, polarity);
    makeAndAddRtLocalMinResolver(queue, null, minRtDataPoints, importRtRange, rtFwhm,
        maxIsomersInRt);
    queue.getLast().getParameterSet().setParameter(MinimumSearchFeatureResolverParameters.MIN_RATIO,
        1.7); // needed for aldoxycarb detection
    return queue;
  }

  private void appendDiaProcessingAndExport(@NotNull final BatchQueue queue,
      @NotNull final DiaCorrelationOptions algorithm,
      @NotNull final DiaCorrelationOptions pregrouping, final double minimumMs2Intensity,
      final double minimumNoCorrelationIntensity, @Nullable final File workflowExportPath,
      @Nullable final String diagnosticConfiguration) {
    makeAndAddDiaMs2GroupingStep(queue, algorithm, pregrouping, minimumMs2Intensity,
        minimumNoCorrelationIntensity, diagnosticConfiguration);
    makeAndAddLibrarySearchStep(queue, false);
    queue.getLast().getParameterSet().setParameter(SpectralLibrarySearchParameters.minMatch, 1);
    if (workflowExportPath != null) {
      makeAndAddCsvModularExportStep(queue, workflowExportPath);
      makeAndAddExportScansStep(queue, workflowExportPath, new LibraryBatchMetadataParameters(),
          false, "_ms2");
    }
  }

  private void makeAndAddRtFilteredImportTask(@NotNull final BatchQueue queue) {
    final ScanSelection scanSelection = new ScanSelection(importRtRange, null);
    final AdvancedSpectraImportParameters advancedParameters = AdvancedSpectraImportParameters.create(
        massDetectorOption.getValueType(), null, null, null, scanSelection, false);
    final ParameterSet parameters = AllSpectralDataImportParameters.create(
        ConfigService.getPreferences().getVendorImportParameters(), dataFiles,
        metadataFile.active() ? metadataFile.value() : null, libraries, advancedParameters);

    queue.add(new MZmineProcessingStepImpl<>(
        MZmineCore.getModuleInstance(AllSpectralDataImportModule.class), parameters));
  }

  private void makeAndAddDiaMs2GroupingStep(@NotNull final BatchQueue queue,
      @NotNull final DiaCorrelationOptions algorithm,
      @NotNull final DiaCorrelationOptions pregrouping, final double minimumMs2Intensity,
      final double minimumNoCorrelationIntensity, @Nullable final String diagnosticConfiguration) {
    final ParameterSet parameters = MZmineCore.getConfiguration()
        .getModuleParameters(DiaMs2CorrModule.class).cloneParameterSet();
    parameters.setParameter(DiaMs2CorrParameters.ms2ScanSelection, new ScanSelection(2));
    parameters.setParameter(DiaMs2CorrParameters.flists,
        new FeatureListsSelection(FeatureListsSelectionType.BATCH_LAST_FEATURELISTS));
    parameters.setParameter(DiaMs2CorrParameters.algorithm, algorithm);

    final ModuleOptionsEnumComboParameter<DiaCorrelationOptions> algorithmParameter = parameters.getParameter(
        DiaMs2CorrParameters.algorithm);
    final ParameterSet algorithmParameters = switch (algorithm) {
      case NO_CORRELATION -> createNoCorrelationParameters(minimumNoCorrelationIntensity);
      case RT_CORRELATION -> createRtCorrelationParameters(minimumMs2Intensity);
      case SLIDING_MZ ->
          createSlidingMzParameters(pregrouping, minimumMs2Intensity, minimumNoCorrelationIntensity,
              diagnosticConfiguration);
    };
    algorithmParameter.setEmbeddedParameters(algorithmParameters);

    queue.add(new MZmineProcessingStepImpl<>(MZmineCore.getModuleInstance(DiaMs2CorrModule.class),
        parameters));
  }

  private @NotNull ParameterSet createSlidingMzParameters(
      @NotNull final DiaCorrelationOptions pregrouping, final double minimumMs2Intensity,
      final double minimumNoCorrelationIntensity, @Nullable final String diagnosticConfiguration) {
    final ParameterSet parameters = new DiaSlidingMzParameters().cloneParameterSet();
    parameters.setParameter(DiaSlidingMzParameters.pregrouping, pregrouping);

    final ModuleOptionsEnumComboParameter<DiaCorrelationOptions> pregroupingParameter = parameters.getParameter(
        DiaSlidingMzParameters.pregrouping);
    final ParameterSet pregroupingParameters = switch (pregrouping) {
      case NO_CORRELATION -> createNoCorrelationParameters(minimumNoCorrelationIntensity);
      case RT_CORRELATION -> createRtCorrelationParameters(minimumMs2Intensity);
      case SLIDING_MZ -> throw new IllegalArgumentException(
          "Sliding m/z cannot be used to pre-group another sliding m/z run.");
    };
    pregroupingParameter.setEmbeddedParameters(pregroupingParameters);
    if (diagnosticConfiguration != null) {
      DiaSlidingMzDiagnostics.CONFIGURATION_LABELS.put(pregrouping, diagnosticConfiguration);
    }
    return parameters;
  }

  private @NotNull ParameterSet createNoCorrelationParameters(final double minimumIntensity) {
    final ParameterSet parameters = new DiaMs2NoCorrParameters().cloneParameterSet();
    parameters.setParameter(DiaMs2NoCorrParameters.replaceExisting, true);
    parameters.setParameter(DiaMs2NoCorrParameters.minIntensity, minimumIntensity);
    return parameters;
  }

  private @NotNull ParameterSet createRtCorrelationParameters(
      final double minimumFragmentIntensity) {
    final ParameterSet parameters = new DiaMs2RtCorrParameters().cloneParameterSet();
    parameters.setParameter(DiaMs2RtCorrParameters.minMs1Intensity, minMs1Intensity);
    parameters.setParameter(DiaMs2RtCorrParameters.minMs2Intensity, minimumFragmentIntensity);
    parameters.setParameter(DiaMs2RtCorrParameters.numCorrPoints, minCorrelatedPoints);
    parameters.setParameter(DiaMs2RtCorrParameters.minPearson, minPearson);
    parameters.setParameter(DiaMs2RtCorrParameters.ms2ScanToScanAccuracy, diaMzTolerance);
    DiaMs2RtCorrAdvancedParameters advanced = DiaMs2RtCorrAdvancedParameters.create(0.001,
        new DefaultOffCustomValue<>(DefaultOffCustomOption.CUSTOM, 1));
    parameters.setParameter(DiaMs2RtCorrParameters.advanced, true);
    parameters.getParameter(DiaMs2RtCorrParameters.advanced).setEmbeddedParameters(advanced);
    return parameters;
  }
}
