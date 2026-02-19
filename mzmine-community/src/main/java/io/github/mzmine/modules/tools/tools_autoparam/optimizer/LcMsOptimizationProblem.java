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

package io.github.mzmine.modules.tools.tools_autoparam.optimizer;

import com.opencsv.exceptions.CsvException;
import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.types.annotations.shapeclassification.RtQualitySummaryType;
import io.github.mzmine.datamodel.features.types.numbers.MZType;
import io.github.mzmine.datamodel.features.types.numbers.MobilityType;
import io.github.mzmine.datamodel.features.types.numbers.RTType;
import io.github.mzmine.datamodel.statistics.FeaturesDataTable;
import io.github.mzmine.modules.batchmode.BatchModeModule;
import io.github.mzmine.modules.batchmode.BatchQueue;
import io.github.mzmine.modules.batchmode.BatchTask;
import io.github.mzmine.modules.dataanalysis.utils.StatisticUtils;
import io.github.mzmine.modules.dataanalysis.utils.imputation.ImputationFunctions;
import io.github.mzmine.modules.dataprocessing.filter_featurefilter.peak_fitter.PeakShapeClassification;
import io.github.mzmine.modules.dataprocessing.filter_isotopegrouper.IsotopeGrouperModule;
import io.github.mzmine.modules.dataprocessing.filter_rowsfilter.RowsFilterModule;
import io.github.mzmine.modules.dataprocessing.gapfill_peakfinder.multithreaded.MultiThreadPeakFinderModule;
import io.github.mzmine.modules.dataprocessing.group_metacorrelate.corrgrouping.CorrelateGroupingModule;
import io.github.mzmine.modules.dataprocessing.group_spectral_networking.MainSpectralNetworkingModule;
import io.github.mzmine.modules.dataprocessing.id_ion_identity_networking.ionidnetworking.IonNetworkingModule;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnnotationModule;
import io.github.mzmine.modules.dataprocessing.id_spectral_library_match.SpectralLibrarySearchModule;
import io.github.mzmine.modules.tools.batchwizard.WizardPart;
import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.modules.tools.batchwizard.subparameters.CustomizationWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.FilterWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.IonInterfaceHplcWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassSpectrometerWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.ParameterOverride;
import io.github.mzmine.modules.tools.batchwizard.subparameters.WizardStepParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.WorkflowDdaWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.WorkflowWizardParameterFactory;
import io.github.mzmine.modules.tools.tools_autoparam.DataFileStatistics;
import io.github.mzmine.modules.tools.tools_autoparam.FeatureStatistics;
import io.github.mzmine.modules.tools.tools_autoparam.FeatureWithIsotopeTraces;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.ImportType;
import io.github.mzmine.parameters.parametertypes.statistics.AbundanceDataTablePreparationConfig;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.project.ProjectService;
import io.github.mzmine.taskcontrol.SimpleRunnableTask;
import io.github.mzmine.util.CSVParsingUtils;
import io.github.mzmine.util.MathUtils;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javafx.beans.property.SimpleStringProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.moeaframework.core.Solution;
import org.moeaframework.core.objective.Maximize;
import org.moeaframework.core.objective.Minimize;
import org.moeaframework.problem.AbstractProblem;

public class LcMsOptimizationProblem extends AbstractProblem {

  private final int NUM_PARAM;
  private final boolean maximizeNumBenchmark;
  private final boolean maximizeCv20;
  private final boolean maximizeFeaturesWithIsos;
  private final boolean minimizeDoublePeaks;
  private final boolean maximizeFillRatio;
  private final List<WizardParameterPrototype> paramToOptimize;

  private final @NotNull File[] files;
  private final WizardParameterSolutionBuilder builder;
  @Nullable
  private final List<FeatureRecord> target;
  private final WizardSequence initialSequence;

  private final @Nullable MZTolerance mzSampleToSampleTolerance;
  private final @Nullable RTTolerance rtSampleToSampleTolerance;
  private final int numWizardParam;
  private final int numBatchParam;
  private final @Nullable List<FeatureRecord> fileOnlyBenchmarkFeatures;
  private final boolean maximizeCv20WithIsos;
  private final boolean maximizeSlawIntegrationScore;
  private final boolean maximizeHarmonicSlawIsotopes;

  public LcMsOptimizationProblem(@NotNull final WizardSequence initialSequence,
      @NotNull List<@NotNull DataFileStatistics> stats, @NotNull final ParameterSet param) {

    fileOnlyBenchmarkFeatures = LcMsOptimizationProblem.extractFeatureRecordsFromFile(null, param);
    target = statsToTargetList(stats);
    maximizeNumBenchmark = param.getValue(OptimizerParameters.maximizeNumberOfBenchmarkFeatures);
    maximizeCv20 = param.getValue(OptimizerParameters.maximizeCv20);
    maximizeFeaturesWithIsos = param.getValue(OptimizerParameters.maximizeFeaturesWithIsotopes);
    minimizeDoublePeaks = param.getValue(OptimizerParameters.minimizeDoublePeaks);
    maximizeFillRatio = param.getValue(OptimizerParameters.maximizeRowFillRatio);
    maximizeCv20WithIsos = param.getValue(OptimizerParameters.maximizeCv20WithIsos);
    maximizeSlawIntegrationScore = param.getValue(OptimizerParameters.slawIntegrationScore);
    maximizeHarmonicSlawIsotopes = param.getValue(OptimizerParameters.harmonicSlawIsotopes);
    paramToOptimize = param.getValue(OptimizerParameters.paramToOptimize);

    super(param.getValue(OptimizerParameters.paramToOptimize).size(),
        calculateNumberOfObjectives(param, stats));

    this.NUM_PARAM = paramToOptimize.size();

    this.initialSequence = initialSequence;
    files = stats.stream().map(DataFileStatistics::file).map(RawDataFile::getAbsoluteFilePath)
        .toArray(File[]::new);

    final ModularFeatureList aligned = OptimizationUtils.alignBenchmarkFeatures(stats, null,
        new SimpleRunnableTask(() -> {
        }));
    mzSampleToSampleTolerance = OptimizationUtils.extractSampleToSampleMzTolerances(aligned,
        (int) (files.length * 0.8), 0.8f);
    rtSampleToSampleTolerance = OptimizationUtils.extractSampleToSampleRtTolerances(aligned,
        (int) (files.length * 0.8), 0.8f);

    builder = new WizardParameterSolutionBuilder(stats, null);

    numWizardParam = createWizardParameters().size();
    numBatchParam = createBatchParameters().size();

    if (numWizardParam + numBatchParam != NUM_PARAM) {
      throw new IllegalStateException(
          "Number of parameters does not match: Wizard: %d, Batch: %d, but total is: %d".formatted(
              numWizardParam, numBatchParam, NUM_PARAM));
    }
  }

  public static @Nullable List<FeatureRecord> statsToTargetList(
      @Nullable List<@NotNull DataFileStatistics> stats) {
    if (stats == null) {
      return null;
    }

    return stats.stream().map(DataFileStatistics::featureStatistics).flatMap(List::stream)
        // only use isotope traces, not the main isotopes
        .map(FeatureStatistics::getBestEnvelope).map(FeatureWithIsotopeTraces::isotopeTraces)
        .flatMap(List::stream).map(
            fwi -> new FeatureRecord(fwi.getRawDataFile(), fwi.getMZ(), fwi.getRT(),
                fwi.getMobility())).toList();
  }

  public static @NotNull List<FeatureRecord> extractFeatureRecordsFromFile(
      @Nullable List<@NotNull DataFileStatistics> stats, ParameterSet param) {
    final boolean useFeatureTypes = param.getValue(OptimizerParameters.benchmarkFeatureTypes);
    final boolean useBenchmarkFiles = param.getValue(OptimizerParameters.benchmarkFeaturesFile);

    List<FeatureRecord> featureRecordsFromFile = new ArrayList<>();
    assert useFeatureTypes && useBenchmarkFiles || (!useBenchmarkFiles && !useFeatureTypes);
    if (useBenchmarkFiles && useFeatureTypes) {
      final File benchmarkFile = param.getEmbeddedParameterValue(
          OptimizerParameters.benchmarkFeaturesFile);
      final List<ImportType<?>> types = param.getEmbeddedParameterValue(
          OptimizerParameters.benchmarkFeatureTypes);

      final Character separator = CSVParsingUtils.autoDetermineSeparator(benchmarkFile);
      final SimpleStringProperty errorMessage = new SimpleStringProperty();
      try {
        final List<String[]> csvData = CSVParsingUtils.readData(benchmarkFile,
            separator.toString());

        final List<ImportType<?>> lineIds = CSVParsingUtils.findLineIds(types, csvData.getFirst(),
            errorMessage);

        int mzId = 0;
        int rtId = 0;
        Integer mobilityId = null;
        for (ImportType lineId : lineIds) {
          switch (lineId.getDataType()) {
            case MZType _ -> mzId = lineId.getColumnIndex();
            case RTType _ -> rtId = lineId.getColumnIndex();
            case MobilityType _ -> mobilityId = lineId.getColumnIndex();
            default -> {
            }
          }
        }

        for (int i = 1; i < csvData.size(); i++) {
          final double mz = Double.parseDouble(csvData.get(i)[mzId]);
          final float rt = Float.parseFloat(csvData.get(i)[rtId]);
          if (mobilityId != null) {
            final float mobility = Float.parseFloat(csvData.get(i)[mobilityId]);
            if (stats != null) {
              featureRecordsFromFile.addAll(stats.stream().map(DataFileStatistics::file)
                  .map(f -> new FeatureRecord(f, mz, rt, mobility)).toList());
            } else {
              featureRecordsFromFile.add(new FeatureRecord(null, mz, rt, mobility));
            }
          } else {
            if (stats != null) {
              featureRecordsFromFile.addAll(stats.stream().map(DataFileStatistics::file)
                  .map(f -> new FeatureRecord(f, mz, rt, null)).toList());
            } else {
              featureRecordsFromFile.add(new FeatureRecord(null, mz, rt, null));
            }
          }
        }
      } catch (IOException | CsvException e) {
        throw new RuntimeException(e);
      }
    }
    return featureRecordsFromFile;
  }

  private static void calculateAndWriteRsdResult(int index, Solution solution, FeatureList newest,
      FeaturesDataTable dataTable) {

    // dont use rsd filter here, because we just use all data files here. they may not be of a specific sample type.
    long matchingRows = 0;
    for (int i = 0; i < newest.getNumberOfRows(); i++) {
      final double[] abundances = dataTable.getFeatureData(newest.getRow(i), false);
      final double rsd = MathUtils.calcRelativeStd(abundances);
      if (rsd <= 0.2) {
        matchingRows++;
      }
    }

    solution.setObjectiveValue(index, (double) matchingRows);
  }

  private static void calculateAndWriteRsdWithIsosResult(int index, Solution solution,
      FeatureList newest, FeaturesDataTable dataTable) {

    // dont use rsd filter here, because we just use all data files here. they may not be of a specific sample type.
    long matchingRows = 0;
    for (int i = 0; i < newest.getNumberOfRows(); i++) {
      if (newest.getRow(i).getBestIsotopePattern() == null) {
        continue;
      }
      final double[] abundances = dataTable.getFeatureData(newest.getRow(i), false);
      final double rsd = MathUtils.calcRelativeStd(abundances);
      if (rsd <= 0.2) {
        matchingRows++;
      }
    }

    solution.setObjectiveValue(index, (double) matchingRows);
  }

  private static void calculateSlawIntegrationScore(int index, Solution solution,
      FeatureList newest, FeaturesDataTable dataTable) {

    // dont use rsd filter here, because we just use all data files here. they may not be of a specific sample type.
    long matchingRows = 0;
    for (int i = 0; i < newest.getNumberOfRows(); i++) {
      if (newest.getRow(i).getNumberOfFeatures() != newest.getNumberOfRawDataFiles()) {
        continue;
      }
      final double[] abundances = dataTable.getFeatureData(newest.getRow(i), false);
      final double rsd = MathUtils.calcRelativeStd(abundances);
      if (rsd <= 0.2) {
        matchingRows++;
      }
    }

    solution.setObjectiveValue(index,
        (double) (matchingRows * matchingRows) / newest.getNumberOfRows());
  }

  /**
   * Harmonic mean of the slaw integration score and the features-with-isotopes score. Both
   * component scores are computed inline and combined as {@code 2*a*b / (a+b)}.
   */
  private static void calculateHarmonicSlawIsotopes(int index, @NotNull Solution solution,
      @NotNull FeatureList newest, @NotNull FeaturesDataTable dataTable) {
    // --- slaw component ---
    long matchingRows = 0;
    for (int i = 0; i < newest.getNumberOfRows(); i++) {
      if (newest.getRow(i).getNumberOfFeatures() != newest.getNumberOfRawDataFiles()) {
        continue;
      }
      final double[] abundances = dataTable.getFeatureData(newest.getRow(i), false);
      if (MathUtils.calcRelativeStd(abundances) <= 0.2) {
        matchingRows++;
      }
    }
    final double slawScore = newest.getNumberOfRows() == 0 ? 0.0
        : (double) (matchingRows * matchingRows) / newest.getNumberOfRows();

    // --- features-with-isotopes component ---
    final double noise = MathUtils.calcQuantile(
        newest.streamFeatures(false).mapToDouble(Feature::getHeight).toArray(), 0.03);
    long isoScore = 0;
    for (RawDataFile file : newest.getRawDataFiles()) {
      int numPeaks = 0;
      int numWithIsos = 0;
      for (FeatureListRow row : newest.getRows()) {
        final Feature f = row.getFeature(file);
        if (f == null || f.getHeight() < noise) {
          continue;
        }
        numPeaks++;
        if (f.getIsotopePattern() != null) {
          numWithIsos++;
        }
      }
      if (numPeaks > 0) {
        isoScore += (long) numWithIsos * numWithIsos / numPeaks;
      }
    }

    // Store raw components as private attributes so the results table can normalise them
    // across the full population before displaying the harmonic score.
    solution.setAttribute(ATTR_HARMONIC_SLAW, slawScore);
    solution.setAttribute(ATTR_HARMONIC_ISO, (double) isoScore);

    // --- harmonic mean (raw, not normalised — used by the MOEA optimizer) ---
    final double sum = slawScore + isoScore;
    solution.setObjectiveValue(index, sum == 0.0 ? 0.0 : 2.0 * slawScore * isoScore / sum);
  }

  /**
   * Attribute key under which the raw slaw component of the harmonic objective is stored.
   */
  public static final String ATTR_HARMONIC_SLAW = "_harmonic_slaw";
  /**
   * Attribute key under which the raw iso component of the harmonic objective is stored.
   */
  public static final String ATTR_HARMONIC_ISO = "_harmonic_iso";

  private static void calculateAndWriteFeaturesWithIsotopes(int index, Solution solution,
      FeatureList newest) {
//    final long featuresWithIsotopes = newest.streamFeatures()
//        .filter(f -> f.getIsotopePattern() != null).count();

    final double noise = MathUtils.calcQuantile(
        newest.streamFeatures(false).mapToDouble(Feature::getHeight).toArray(), 0.03);

    long score = 0;
    for (RawDataFile file : newest.getRawDataFiles()) {
      int numPeaks = 0;
      int numWithIsos = 0;
      for (FeatureListRow row : newest.getRows()) {
        Feature f = row.getFeature(file);
        if (f == null || f.getHeight() < noise) {
          continue;
        }
        numPeaks++;
        if (f.getIsotopePattern() != null) {
          numWithIsos++;
        }
      }
      score += (long) numWithIsos * numWithIsos / numPeaks;
    }
    solution.setObjectiveValue(index, score);
  }

  /**
   * Only set as additional metrics, not used for evaluation. see
   * {@link #calculateAndWriteRsdResult(int, Solution, FeatureList, FeaturesDataTable)} instead.
   */
  private static void calculateAndSetRsds(Solution solution, FeatureList newest,
      FeaturesDataTable dataTable) {

    final double[] rsds = new double[newest.getNumberOfRows()];
    for (int i = 0; i < newest.getNumberOfRows(); i++) {
      final double[] abundances = dataTable.getFeatureData(newest.getRow(i), false);
      final double rsd = MathUtils.calcRelativeStd(abundances);
      rsds[i] = rsd;
    }

    final DoubleSummaryStatistics stats = Arrays.stream(rsds).summaryStatistics();
    final double medianRsd = MathUtils.calcMedian(rsds);
    solution.setAttribute("Median RSD", medianRsd);
    solution.setAttribute("Average RSD", stats.getAverage());
    solution.setAttribute("Max RSD", stats.getMax());
    solution.setAttribute("Min RSD", stats.getMin());
  }

  static int calculateNumberOfObjectives(ParameterSet param, List<DataFileStatistics> stats) {
    var maximizeNumBenchmark = param.getValue(
        OptimizerParameters.maximizeNumberOfBenchmarkFeatures);
    var maximizeCv20 = param.getValue(OptimizerParameters.maximizeCv20);
    var maximizeFeaturesWithIsos = param.getValue(OptimizerParameters.maximizeFeaturesWithIsotopes);
    var minimizeDoublePeaks = param.getValue(OptimizerParameters.minimizeDoublePeaks);
    var maximizeFillRatio = param.getValue(OptimizerParameters.maximizeRowFillRatio);
    var maximizeCv20WithIsos = param.getValue(OptimizerParameters.maximizeCv20WithIsos);
    var slawIntegrationScore = param.getValue(OptimizerParameters.slawIntegrationScore);
    var harmonicSlawIsotopes = param.getValue(OptimizerParameters.harmonicSlawIsotopes);
    final int numObjectives = (int) Stream.of(maximizeCv20, maximizeFeaturesWithIsos,
            minimizeDoublePeaks, maximizeFillRatio, maximizeCv20WithIsos, slawIntegrationScore,
            harmonicSlawIsotopes, maximizeNumBenchmark && stats != null)
        .filter(Boolean::booleanValue).count();
    return numObjectives;
  }

  private List<WizardParameterSolution> createWizardParameters() {

    int index = 0;
    final List<WizardParameterSolution> param = new ArrayList<>();

    for (WizardParameterPrototype factory : paramToOptimize) {
      if (factory instanceof WizardParameterPrototype.WizardBuilderParameterSolution wbs) {
        param.add(wbs.toRealSolution(builder, index++));
      }
    }

    return param;
  }

  private List<BatchParameterSolution> createBatchParameters() {
    int index = numWizardParam;

    final List<BatchParameterSolution> param = new ArrayList<>();

    for (WizardParameterPrototype factory : paramToOptimize) {
      if (factory instanceof WizardParameterPrototype.BatchWizardParameterSolution bws) {
        param.add(bws.toBatchParameterSolution(index++));
      }
    }

    return param;
  }

  @Override
  public void evaluate(Solution solution) {

    final WizardSequence wizardSequence = createWizardSequenceFromSolution(solution);

    final BatchQueue optimizedQueue = ((WorkflowWizardParameterFactory) wizardSequence.get(
        WizardPart.WORKFLOW).get().getFactory()).getBatchBuilder(wizardSequence).createQueue();

    // gap filling screws with the optimized feature detection
    // also remove other unnecessary steps
    optimizedQueue.removeIf(step -> step.getModule() instanceof MultiThreadPeakFinderModule);
    optimizedQueue.removeIf(step -> step.getModule() instanceof RowsFilterModule);
    optimizedQueue.removeIf(step -> step.getModule() instanceof CorrelateGroupingModule);
    optimizedQueue.removeIf(step -> step.getModule() instanceof IonNetworkingModule);
    optimizedQueue.removeIf(step -> step.getModule() instanceof LipidAnnotationModule);
    optimizedQueue.removeIf(step -> step.getModule() instanceof SpectralLibrarySearchModule);
    optimizedQueue.removeIf(step -> step.getModule() instanceof MainSpectralNetworkingModule);
    optimizedQueue.removeIf(step -> step.getModule() instanceof IsotopeGrouperModule);
//    optimizedQueue.removeIf(step -> step.getModule() instanceof DuplicateFilterModule);

    // use the current project, so we dont import files on every iteration
    final MZmineProject project = ProjectService.getProject();
    final BatchTask batchTask = BatchModeModule.runBatchQueue(optimizedQueue, project, files, null,
        null, null, Instant.now());

    while (!batchTask.isFinished() && !batchTask.isCanceled()) {
      try {
        TimeUnit.MILLISECONDS.sleep(200);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    final FeatureList newest = project.getCurrentFeatureLists().stream()
        .max(Comparator.comparing(f -> f.getName().length())).get();
    final int maxFeatures = newest.getNumberOfRows() * newest.getNumberOfRawDataFiles();
    final long numFeatures = newest.streamFeatures().count();

    final var config = new AbundanceDataTablePreparationConfig(AbundanceMeasure.Area,
        ImputationFunctions.Zero);
    final FeaturesDataTable dataTable = StatisticUtils.extractAbundancesPrepareData(
        newest.getRows(), newest.getRawDataFiles(), config);

    int objectiveIndex = 0;
    if (maximizeCv20) {
      calculateAndWriteRsdResult(objectiveIndex++, solution, newest, dataTable);
    }
    if (maximizeFeaturesWithIsos) {
      calculateAndWriteFeaturesWithIsotopes(objectiveIndex++, solution, newest);
    }
    if (minimizeDoublePeaks) {
      final long numDoublePeaks = newest.streamFeatures(true)
          .map(f -> f.get(RtQualitySummaryType.class)).filter(summary -> summary != null
              && summary.classification() == PeakShapeClassification.DOUBLE_GAUSSIAN).count();
      solution.setObjectiveValue(objectiveIndex++, (double) numDoublePeaks / numFeatures);
    }

    if (maximizeFillRatio) {
      solution.setObjectiveValue(objectiveIndex++, (double) numFeatures / maxFeatures);
    }

    final List<FeatureListRow> rows = newest.getRowsCopy();
    rows.sort(Comparator.comparing(FeatureListRow::getAverageMZ));
    if (maximizeNumBenchmark && target != null) {
      final long foundTargets = target.stream().parallel().filter(r -> r.isPresent(rows)).count();
      solution.setObjectiveValue(objectiveIndex++, foundTargets);
    }

    if (maximizeCv20WithIsos) {
      calculateAndWriteRsdWithIsosResult(objectiveIndex++, solution, newest, dataTable);
    }

    if (maximizeSlawIntegrationScore) {
      calculateSlawIntegrationScore(objectiveIndex++, solution, newest, dataTable);
    }

    if (maximizeHarmonicSlawIsotopes) {
      calculateHarmonicSlawIsotopes(objectiveIndex++, solution, newest, dataTable);
    }

//    calculateAndSetRsds(solution, newest, dataTable);
    // for tracking only as attribute
    if (fileOnlyBenchmarkFeatures != null) {
      solution.setAttribute("Target features",
          fileOnlyBenchmarkFeatures.stream().parallel().mapToLong(r -> r.getNumMatches(rows))
              .sum());
    }
    solution.setAttribute("Total features", newest.streamFeatures().count());

    project.removeFeatureLists(project.getCurrentFeatureLists());
  }

  public void applyBatchOverridesToSequence(Solution solution, WizardSequence sequence) {
    final List<ParameterOverride> overrides = createBatchParameters().stream()
        .map(bp -> bp.toParameterOverride(solution)).toList();

    if (overrides.isEmpty()) {
      return;
    }
    final WizardStepParameters customization = sequence.get(WizardPart.CUSTOMIZATION).get();
    customization.setParameter(CustomizationWizardParameters.enabled, true);
    customization.setParameter(CustomizationWizardParameters.overrides, overrides);
  }

  public @NotNull WizardSequence createWizardSequenceFromSolution(Solution solution) {

    final WizardSequence wizardSequence = new WizardSequence();

    final WizardStepParameters dataParam = initialSequence.get(WizardPart.DATA_IMPORT).get()
        .getFactory().create();
    final WizardStepParameters lcParam = initialSequence.get(WizardPart.ION_INTERFACE).get()
        .createDefaultParameterPreset().getFactory().create();
    final WizardStepParameters filterParam = initialSequence.get(WizardPart.FILTER).get()
        .createDefaultParameterPreset().getFactory().create();
    filterParam.setParameter(FilterWizardParameters.goodPeaksOnly, true);
    final WizardStepParameters imsParam = initialSequence.get(WizardPart.IMS).get()
        .createDefaultParameterPreset().getFactory().create();
    final WizardStepParameters msParam = initialSequence.get(WizardPart.MS).get()
        .createDefaultParameterPreset().getFactory().create();
    final WizardStepParameters annotationParam = initialSequence.get(WizardPart.ANNOTATION).get()
        .getFactory().create();
    final WizardStepParameters workflowParam = initialSequence.get(WizardPart.WORKFLOW).get()
        .getFactory().create();
    final WizardStepParameters customizationParameters = initialSequence.get(
        WizardPart.CUSTOMIZATION).get().createDefaultParameterPreset().getFactory().create();

    wizardSequence.add(dataParam);
    wizardSequence.add(lcParam);
    wizardSequence.add(filterParam);
    wizardSequence.add(imsParam);
    wizardSequence.add(msParam);
    wizardSequence.add(annotationParam);
    wizardSequence.add(workflowParam);
    wizardSequence.add(customizationParameters);

//    dataParam.setParameter(DataImportWizardParameters.fileNames, files);

    for (WizardParameterSolution parameter : createWizardParameters()) {
      parameter.setToParameters()
          .accept(wizardSequence.get(parameter.part()).get(), solution, parameter.index());
    }

    workflowParam.setParameter(WorkflowDdaWizardParameters.exportPath, false);

    applyBatchOverridesToSequence(solution, wizardSequence);

    if (mzSampleToSampleTolerance != null) {
      msParam.setParameter(MassSpectrometerWizardParameters.sampleToSampleMzTolerance,
          mzSampleToSampleTolerance);
    }
    if (rtSampleToSampleTolerance != null && paramToOptimize.stream()
        .noneMatch(s -> "Inter sample RT tolerance".equals(s.name()))) {
      lcParam.setParameter(IonInterfaceHplcWizardParameters.interSampleRTTolerance,
          rtSampleToSampleTolerance);
    }

    return wizardSequence;
  }

  @Override
  public Solution newSolution() {

    final Solution solution = new Solution(getNumberOfVariables(), getNumberOfObjectives());

    for (WizardParameterSolution parameter : createWizardParameters()) {
      solution.setVariable(parameter.index(), parameter.variable().get());
    }

    for (BatchParameterSolution bp : createBatchParameters()) {
      solution.setVariable(bp.index(), bp.variable().get());
    }

    int objectiveIndex = 0;
    if (maximizeCv20) {
      solution.setObjective(objectiveIndex++, new Maximize("Rows below RSD threshold"));
    }
    if (maximizeFeaturesWithIsos) {
      solution.setObjective(objectiveIndex++, new Maximize("Features with isotopes"));
    }
    if (minimizeDoublePeaks) {
      solution.setObjective(objectiveIndex++, new Minimize("Double peak ratio"));
    }
    if (maximizeFillRatio) {
      solution.setObjective(objectiveIndex++, new Maximize("Fill ratio"));
    }
    if (target != null && maximizeNumBenchmark) {
      solution.setObjective(objectiveIndex++, new Maximize("Found targets"));
    }
    if (maximizeCv20WithIsos) {
      solution.setObjective(objectiveIndex++, new Maximize("Rows < CV 20 with isos"));
    }
    if (maximizeSlawIntegrationScore) {
      solution.setObjective(objectiveIndex++, new Maximize("Slaw integration score"));
    }
    if (maximizeHarmonicSlawIsotopes) {
      solution.setObjective(objectiveIndex++, new Maximize("Harmonic slaw-isotopes"));
    }
    return solution;
  }

  public @Nullable List<FeatureRecord> getAllTargets() {
    return target;
  }

  public @Nullable List<FeatureRecord> getFileOnlyBenchmarkFeatures() {
    return fileOnlyBenchmarkFeatures;
  }
}
