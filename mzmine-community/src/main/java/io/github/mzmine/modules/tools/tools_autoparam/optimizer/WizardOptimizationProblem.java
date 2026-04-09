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
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.types.numbers.MZType;
import io.github.mzmine.datamodel.features.types.numbers.MobilityType;
import io.github.mzmine.datamodel.features.types.numbers.RTType;
import io.github.mzmine.modules.batchmode.BatchModeModule;
import io.github.mzmine.modules.batchmode.BatchQueue;
import io.github.mzmine.modules.batchmode.BatchTask;
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
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.metrics.BenchmarkTargetCount;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.metrics.SweepMetric;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.ImportType;
import io.github.mzmine.parameters.parametertypes.OptionalParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.project.ProjectService;
import io.github.mzmine.taskcontrol.SimpleRunnableTask;
import io.github.mzmine.util.CSVParsingUtils;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javafx.beans.property.SimpleStringProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.moeaframework.core.Solution;
import org.moeaframework.problem.AbstractProblem;

public class WizardOptimizationProblem extends AbstractProblem {

  private final int NUM_PARAM;
  private final List<WizardParameterPrototype> paramToOptimize;
  private final @NotNull List<SweepMetric> enabledMetrics;

  private final @NotNull File[] files;
  private final WizardParameterSolutionBuilder builder;
  // assumption: kept as a separate field for getAllTargets() GUI accessor
  @Nullable
  private final List<FeatureRecord> target;
  private final WizardSequence initialSequence;

  private final @Nullable MZTolerance mzSampleToSampleTolerance;
  private final @Nullable RTTolerance rtSampleToSampleTolerance;
  private final int numWizardParam;
  private final int numBatchParam;
  private final @Nullable List<FeatureRecord> fileOnlyBenchmarkFeatures;

  public WizardOptimizationProblem(@NotNull final WizardSequence initialSequence,
      @NotNull List<@NotNull DataFileStatistics> stats, @NotNull final ParameterSet param) {

    // decision: super() must be first — use static helper for objective count before enabledMetrics field is assigned
    super(param.getValue(OptimizerParameters.paramToOptimize).size(),
        calculateNumberOfObjectives(param, stats));

    fileOnlyBenchmarkFeatures = WizardOptimizationProblem.extractFeatureRecordsFromFile(null,
        param);
    target = statsToTargetList(stats);
    paramToOptimize = param.getValue(OptimizerParameters.paramToOptimize);
    enabledMetrics = buildEnabledMetrics(param, stats);

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
      @Nullable List<@NotNull DataFileStatistics> stats, @NotNull ParameterSet param) {
    final boolean useBenchmarkFiles = param.getValue(OptimizerParameters.benchmarkFeaturesFile);

    List<FeatureRecord> featureRecordsFromFile = new ArrayList<>();
    if (useBenchmarkFiles) {
      final File benchmarkFile = param.getEmbeddedParameterValue(
          OptimizerParameters.benchmarkFeaturesFile);
      final List<ImportType<?>> types = param.getValue(OptimizerParameters.benchmarkFeatureTypes);

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

        if (lineIds.stream().filter(i -> i.getColumnIndex() != -1).filter(
                i -> i.getDataType().equals(new MZType()) || i.getDataType().equals(new RTType()))
            .toList().size() < 2) {
          throw new RuntimeException("MZ and RT columns were not found");
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

  /**
   * Builds the enabled {@link SweepMetric} list from the user's checklist selection.
   * {@link BenchmarkTargetCount} placeholder instances are replaced with real instances carrying
   * the actual target features derived from file statistics.
   */
  private static @NotNull List<SweepMetric> buildEnabledMetrics(@NotNull ParameterSet param,
      @Nullable List<@NotNull DataFileStatistics> stats) {
    final List<SweepMetric> selected = param.getValue(OptimizerParameters.metricsToOptimize);
    final List<SweepMetric> metrics = new ArrayList<>();
    for (final SweepMetric metric : selected) {
      if (metric instanceof BenchmarkTargetCount) {
        // decision: only include benchmark metric when file statistics are available to derive targets
        if (stats != null) {
          final List<FeatureRecord> targets = statsToTargetList(stats);
          if (targets != null) {
            metrics.add(new BenchmarkTargetCount(targets));
          }
        }
      } else {
        metrics.add(metric);
      }
    }
    return List.copyOf(metrics);
  }

  static int calculateNumberOfObjectives(@NotNull ParameterSet param,
      @Nullable List<DataFileStatistics> stats) {
    final List<SweepMetric> selected = param.getValue(OptimizerParameters.metricsToOptimize);
    // BenchmarkTargetCount only counts as an objective when file statistics are available
    return (int) selected.stream()
        .filter(m -> !(m instanceof BenchmarkTargetCount) || stats != null).count();
  }

  private @NotNull List<WizardParameterSolution> createWizardParameters() {

    int index = 0;
    final List<WizardParameterSolution> param = new ArrayList<>();

    for (WizardParameterPrototype factory : paramToOptimize) {
      if (factory instanceof WizardParameterPrototype.WizardBuilderParameterSolution wbs) {
        param.add(wbs.toRealSolution(builder, index++));
      }
    }

    return param;
  }

  private @NotNull List<BatchParameterSolution> createBatchParameters() {
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
  public void evaluate(@NotNull Solution solution) {

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

    int objectiveIndex = 0;
    for (SweepMetric metric : enabledMetrics) {
      solution.setObjectiveValue(objectiveIndex++, metric.evaluate(newest));
      metric.applyAttributes(newest, solution);
    }

    // for tracking only as attribute (not an objective)
    if (fileOnlyBenchmarkFeatures != null && !fileOnlyBenchmarkFeatures.isEmpty()) {
      final List<FeatureListRow> rows = newest.getRowsCopy();
      rows.sort(Comparator.comparing(FeatureListRow::getAverageMZ));
      solution.setAttribute("Target features",
          fileOnlyBenchmarkFeatures.stream().parallel().mapToLong(r -> r.getNumMatches(rows))
              .sum());
    }
    solution.setAttribute("Total features", newest.streamFeatures().count());

    project.removeFeatureLists(project.getCurrentFeatureLists());
  }

  public void applyBatchOverridesToSequence(@NotNull Solution solution,
      @NotNull WizardSequence sequence) {
    final List<ParameterOverride> overrides = createBatchParameters().stream()
        .map(bp -> bp.toParameterOverride(solution)).toList();

    if (overrides.isEmpty()) {
      return;
    }
    final WizardStepParameters customization = sequence.get(WizardPart.CUSTOMIZATION).get();
    customization.setParameter(CustomizationWizardParameters.enabled, true);
    customization.setParameter(CustomizationWizardParameters.overrides, overrides);
  }

  /**
   * Creates a wizard sequence from the solution. The wizard sequence also stores the parameters for
   * the respective steps. The new sequence is ALWAYS created from the
   * {@link WizardStepParameters#createDefaultParameterPreset()}, so potentially bad user-entered
   * parameters are ignored.
   *
   * @param solution The current solution (stores the variables we pipe into the wizard parameters)
   * @return The WizardSequence with the parameter values applied from the solution.
   */
  public @NotNull WizardSequence createWizardSequenceFromSolution(@NotNull Solution solution) {

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

    for (WizardParameterSolution parameter : createWizardParameters()) {
      parameter.setToParameters()
          .accept(wizardSequence.get(parameter.part()).get(), solution, parameter.index());
    }

    if (workflowParam.getNameParameterMap()
        .get(WorkflowDdaWizardParameters.exportPath.getName()) instanceof OptionalParameter<?>) {
      workflowParam.setParameter(WorkflowDdaWizardParameters.exportPath, false);
    }

    applyBatchOverridesToSequence(solution, wizardSequence);

    if (mzSampleToSampleTolerance != null) {
      msParam.setParameter(MassSpectrometerWizardParameters.sampleToSampleMzTolerance,
          mzSampleToSampleTolerance);
    }
    // only set benchmark feature-derived rt tolerance if it is not an optimization target.
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
      parameter.applyToSolution(solution);
    }

    for (BatchParameterSolution bp : createBatchParameters()) {
      bp.applyToSolution(solution);
    }

    int objectiveIndex = 0;
    for (SweepMetric metric : enabledMetrics) {
      solution.setObjective(objectiveIndex++, metric.objective());
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
