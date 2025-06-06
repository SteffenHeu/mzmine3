/*
 * Copyright (c) 2004-2025 The mzmine Development Team
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

import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.types.annotations.shapeclassification.RtQualitySummaryType;
import io.github.mzmine.modules.batchmode.BatchModeModule;
import io.github.mzmine.modules.batchmode.BatchQueue;
import io.github.mzmine.modules.batchmode.BatchTask;
import io.github.mzmine.modules.dataanalysis.utils.StatisticUtils;
import io.github.mzmine.modules.dataanalysis.utils.imputation.ZeroImputer;
import io.github.mzmine.modules.dataprocessing.filter_featurefilter.peak_fitter.PeakShapeClassification;
import io.github.mzmine.modules.dataprocessing.filter_rowsfilter.RowsFilterModule;
import io.github.mzmine.modules.dataprocessing.filter_rowsfilter.RsdFilter;
import io.github.mzmine.modules.dataprocessing.gapfill_peakfinder.multithreaded.MultiThreadPeakFinderModule;
import io.github.mzmine.modules.dataprocessing.group_metacorrelate.corrgrouping.CorrelateGroupingModule;
import io.github.mzmine.modules.dataprocessing.id_ion_identity_networking.ionidnetworking.IonNetworkingModule;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnnotationModule;
import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.modules.tools.batchwizard.subparameters.FilterWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.WizardStepParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.WorkflowWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.AnnotationWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.DataImportWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.FilterWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.IonInterfaceWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.IonMobilityWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.MassSpectrometerWizardParameterFactory;
import io.github.mzmine.modules.tools.tools_autoparam.DataFileStatistics;
import io.github.mzmine.modules.tools.tools_autoparam.FeatureStatistics;
import io.github.mzmine.modules.tools.tools_autoparam.FeatureWithIsotopeTraces;
import io.github.mzmine.project.ProjectService;
import io.github.mzmine.util.MathUtils;
import java.io.File;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.Null;
import org.apache.commons.math3.linear.RealMatrix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.moeaframework.core.Solution;
import org.moeaframework.core.objective.Maximize;
import org.moeaframework.core.objective.Minimize;
import org.moeaframework.problem.AbstractProblem;

public class LcMsOptimizationProblem extends AbstractProblem {

  private static final int NUM_OBJECTIVES = 3;
  private static final int NUM_PARAM = 6;

  private final MassSpectrometerWizardParameterFactory msType;
  private final WorkflowWizardParameters workflowParam;
  private final @Nullable List<DataFileStatistics> stats;
  private final @NotNull File[] files;
  private final ParameterSolutionBuilder builder;
  @Nullable
  private final List<FeatureRecord> target;

  public LcMsOptimizationProblem(MassSpectrometerWizardParameterFactory msType,
      WorkflowWizardParameters workflowParam, @NotNull List<@NotNull DataFileStatistics> stats) {

    target = statsToTargetList(stats);

    super(NUM_PARAM, NUM_OBJECTIVES + (stats != null ? 1 : 0));
    this.msType = msType;
    this.workflowParam = workflowParam;
    this.stats = stats;
    files = stats.stream().map(DataFileStatistics::file).map(RawDataFile::getAbsoluteFilePath)
        .toArray(File[]::new);

    builder = new ParameterSolutionBuilder(stats, null);
  }

  public LcMsOptimizationProblem(@NotNull MassSpectrometerWizardParameterFactory msType,
      WorkflowWizardParameters workflowParam, @NotNull File @NotNull [] files,
      @Nullable List<DataFileStatistics> stats) {

    target = statsToTargetList(stats);

    super(NUM_PARAM, NUM_OBJECTIVES + (stats != null ? 1 : 0));
    this.msType = msType;
    this.workflowParam = workflowParam;
    this.stats = stats;
    this.files = files;

    builder = new ParameterSolutionBuilder(stats, msType.getDefaultMassDetector());
  }

  private static @Nullable List<FeatureRecord> statsToTargetList(
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

  private static void calculateAndWriteRsdResult(int index, Solution solution, FeatureList newest) {
    final RsdFilter rsdFilter = new RsdFilter(0.2, AbundanceMeasure.Area, newest.getRawDataFiles(),
        false);
    final long matchingRows = newest.stream().map(rsdFilter::matches).filter(Boolean::booleanValue)
        .count();
    solution.setObjectiveValue(index, (double) matchingRows);
  }

  private static void calculateAndWriteFeaturesWithIsotopes(int index, Solution solution,
      FeatureList newest) {
    final long featuresWithIsotopes = newest.streamFeatures()
        .filter(f -> f.getIsotopePattern() != null).count();
    solution.setObjectiveValue(index, featuresWithIsotopes);
  }

  private static void calculateAndSetRsds(Solution solution, FeatureList newest) {
    final RealMatrix datasetFromRows = StatisticUtils.createDatasetFromRows(newest.getRows(),
        newest.getRawDataFiles(), AbundanceMeasure.Area);
    StatisticUtils.imputeMissingValues(datasetFromRows, true, new ZeroImputer());

    final double[] rsds = new double[datasetFromRows.getColumnDimension()];
    for (int i = 0; i < datasetFromRows.getColumnDimension(); i++) {
      final double[] abundances = datasetFromRows.getColumn(i);
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

  private List<ParameterSolution> createParameters() {

    int index = 0;

    final ParameterSolution ms1Noise = builder.buildMs1NoiseSolution(index++);
    final ParameterSolution scanToScanTolerance = builder.buildScanToScanToleranceSolution(index++);
    final ParameterSolution minHeight = builder.buildMinHeightSolution(index++);
    final ParameterSolution minConsecutive = builder.buildMinConsecutiveSolution(index++);
    final ParameterSolution maxPeaks = builder.buildMaxPeaksSolution(index++);
    final ParameterSolution fwhm = builder.buildFwhmSolution(index++);

    final List<ParameterSolution> param = List.of(ms1Noise, scanToScanTolerance, minHeight,
        minConsecutive, maxPeaks, fwhm);

    if (param.size() != NUM_PARAM) {
      throw new IllegalArgumentException(
          "Number of created parameters (%d) does not match expected number (%d).".formatted(
              param.size(), NUM_PARAM));
    }

    return param;
  }

  @Override
  public void evaluate(Solution solution) {

    final WizardSequence wizardSequence = new WizardSequence();

    final WizardStepParameters dataParam = DataImportWizardParameterFactory.Data.create();
    final WizardStepParameters lcParam = IonInterfaceWizardParameterFactory.HPLC.create();
    final WizardStepParameters filterParam = FilterWizardParameterFactory.Filters.create();
    filterParam.setParameter(FilterWizardParameters.goodPeaksOnly, true);
    final WizardStepParameters imsParam = IonMobilityWizardParameterFactory.NO_IMS.create();
    final WizardStepParameters msParam = msType.create();
    final WizardStepParameters annotationParam = AnnotationWizardParameterFactory.Annotation.create();

    wizardSequence.add(dataParam);
    wizardSequence.add(lcParam);
    wizardSequence.add(filterParam);
    wizardSequence.add(imsParam);
    wizardSequence.add(msParam);
    wizardSequence.add(annotationParam);
    wizardSequence.add(workflowParam);

    for (ParameterSolution parameter : createParameters()) {
      parameter.setToParameters()
          .accept(wizardSequence.get(parameter.part()).get(), solution, parameter.index());
    }

    final BatchQueue optimizedQueue = workflowParam.getFactory().getBatchBuilder(wizardSequence)
        .createQueue();

    // gap filling screws with the optimized feature detection
    // also remove other unnecessary steps
    optimizedQueue.removeIf(step -> step.getModule() instanceof MultiThreadPeakFinderModule);
    optimizedQueue.removeIf(step -> step.getModule() instanceof RowsFilterModule);
    optimizedQueue.removeIf(step -> step.getModule() instanceof CorrelateGroupingModule);
    optimizedQueue.removeIf(step -> step.getModule() instanceof IonNetworkingModule);
    optimizedQueue.removeIf(step -> step.getModule() instanceof LipidAnnotationModule);

    // use the current project, so we dont import files on every iteration
    final MZmineProject project = ProjectService.getProject();
    final BatchTask batchTask = BatchModeModule.runBatchQueue(optimizedQueue, project,
        stats.stream().map(stats -> stats.file().getAbsoluteFilePath()).toArray(File[]::new), null,
        null, null, Instant.now());

    while (!batchTask.isFinished() && !batchTask.isCanceled()) {
      try {
        TimeUnit.MILLISECONDS.sleep(500);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    final FeatureList newest = project.getCurrentFeatureLists().stream()
        .max(Comparator.comparing(f -> f.getName().length())).get();

    calculateAndWriteRsdResult(0, solution, newest);
    calculateAndWriteFeaturesWithIsotopes(1, solution, newest);
    final long numDoublePeaks = newest.streamFeatures(true)
        .map(f -> f.get(RtQualitySummaryType.class)).filter(summary -> summary != null
            && summary.classification() == PeakShapeClassification.DOUBLE_GAUSSIAN).count();
    solution.setObjectiveValue(2, numDoublePeaks);
//    solution.setObjectiveValue(2, newest.getNumberOfRows());
    calculateAndSetRsds(solution, newest);

    if (target != null) {
      final List<FeatureListRow> rows = newest.getRowsCopy();
      rows.sort(Comparator.comparing(FeatureListRow::getAverageMZ));
      final long foundTargets = target.stream().parallel().filter(r -> r.isPresent(rows)).count();
      solution.setObjectiveValue(getNumberOfObjectives() - 1, foundTargets);
    }

    project.removeFeatureLists(project.getCurrentFeatureLists());
  }

  @Override
  public Solution newSolution() {

    final Solution solution = new Solution(getNumberOfVariables(), getNumberOfObjectives());

    for (ParameterSolution parameter : createParameters()) {
      solution.setVariable(parameter.index(), parameter.variable().get());
    }

    solution.setObjective(0, new Maximize("Rows below RSD threshold"));
    solution.setObjective(1, new Maximize("Features with isotopes"));
    solution.setObjective(2, new Minimize("Features with double peaks"));
//    solution.setObjective(2, new Maximize("Number of rows"));

    if (target != null) {
      solution.setObjective(getNumberOfObjectives() - 1, new Maximize("Found targets"));
    }
    return solution;
  }
}
