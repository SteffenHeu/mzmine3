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
import io.github.mzmine.modules.dataprocessing.group_compoundgrouper.CompoundGrouperModule;
import io.github.mzmine.modules.dataprocessing.group_metacorrelate.corrgrouping.CorrelateGroupingModule;
import io.github.mzmine.modules.dataprocessing.group_spectral_networking.MainSpectralNetworkingModule;
import io.github.mzmine.modules.dataprocessing.id_ion_identity_networking.ionidnetworking.IonNetworkingModule;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnnotationModule;
import io.github.mzmine.modules.dataprocessing.id_spectral_library_match.SpectralLibrarySearchModule;
import io.github.mzmine.modules.tools.batchwizard.WizardPart;
import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.modules.tools.batchwizard.subparameters.CustomizationWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.IonInterfaceHplcWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassSpectrometerWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.ParameterOverride;
import io.github.mzmine.modules.tools.batchwizard.subparameters.WizardStepParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.WorkflowDdaWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.MassSpectrometerWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.WorkflowWizardParameterFactory;
import io.github.mzmine.modules.tools.tools_autoparam.DataFileStatistics;
import io.github.mzmine.modules.tools.tools_autoparam.FeatureStatistics;
import io.github.mzmine.modules.tools.tools_autoparam.FeatureWithIsotopeTraces;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.ParameterSolutionPrototype.BatchParameterSolutionPrototype;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.ParameterSolutionPrototype.WizardParameterSolutionPrototype;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.metrics.BenchmarkTargetCount;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.metrics.SweepMetric;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.ImportType;
import io.github.mzmine.parameters.parametertypes.OptionalParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.project.ProjectService;
import io.github.mzmine.taskcontrol.SimpleRunnableTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.CSVParsingUtils;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import javafx.beans.property.SimpleStringProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.moeaframework.core.Solution;
import org.moeaframework.core.constraint.LessThanOrEqual;
import org.moeaframework.core.variable.RealVariable;
import org.moeaframework.core.variable.Variable;
import org.moeaframework.problem.AbstractProblem;

public class WizardOptimizationProblem extends AbstractProblem {

  private static final Logger logger = Logger.getLogger(WizardOptimizationProblem.class.getName());

  private final int NUM_PARAM;
  private final List<ParameterSolutionPrototype> paramToOptimize;
  @NotNull
  private final AtomicReference<TaskStatus> externalStatus;
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

  /**
   * Upper limit for the share of features the strict shape filter would reject, in percent.
   * Permissive until {@link #setShapeRejectionLimitPercent(double)} is called, so the raw data
   * estimate itself is never marked infeasible.
   */
  private double shapeRejectionLimitPercent = Double.MAX_VALUE;

  /**
   * Position of a solution in the evaluation order, so the results table can show convergence.
   */
  private static final String ATTR_EVALUATION = "Evaluation";

  /**
   * Whether the result was taken from {@link #evaluationCache} instead of running a batch. Kept
   * visible because it is the only honest way to read the runtime column: a hit carries the runtime
   * of the original evaluation, not of itself.
   */
  private static final String ATTR_CACHE_HIT = "Cache hit";

  /**
   * Attributes describing an individual evaluation instead of its parameter vector, so they must
   * never be taken from a cached result.
   */
  private static final Set<String> OWN_ATTRIBUTES = Set.of(ATTR_EVALUATION, ATTR_CACHE_HIT,
      SolutionOrigin.ATTRIBUTE);

  /**
   * Every solution that went through {@link #evaluate(Solution)}, in evaluation order. Each one
   * cost a full batch run, so keeping them lets the results table show the whole search instead of
   * only the non-dominated front — which is what makes the feasible fraction, the convergence and
   * the metric correlations readable without a separate counter.
   * <p>
   * decision: synchronized because evaluation is sequential today but is a candidate for
   * parallelisation. Only the solutions are retained, never their feature lists.
   */
  private final List<Solution> evaluatedSolutions = Collections.synchronizedList(new ArrayList<>());

  /**
   * Results of already evaluated parameter vectors, keyed by the effective values the batch is run
   * with. Differential evolution leaves a vector untouched whenever its crossover rate does not
   * fire, and a converged population produces the same offspring repeatedly, so a noticeable share
   * of a run is spent re-running batches whose result is already known — around 30 % in the
   * single-objective runs.
   * <p>
   * decision: this saves wall-clock, not budget. MOEA counts the call either way, so the optimizer
   * still stops after the configured number of evaluations and explores exactly the same parameter
   * sets in exactly the same order. The run is faster; the search is unchanged.
   */
  private final Map<List<Double>, Solution> evaluationCache = new ConcurrentHashMap<>();

  public WizardOptimizationProblem(@NotNull final WizardSequence initialSequence,
      @NotNull List<@NotNull DataFileStatistics> stats, @NotNull final ParameterSet param,
      @NotNull AtomicReference<TaskStatus> externalStatus) {

    // decision: super() must be first — use static helper for objective count before enabledMetrics field is assigned
    super(param.getValue(OptimizerParameters.paramToOptimize).size(),
        calculateNumberOfObjectives(param, stats), calculateNumberOfConstraints(param));

    fileOnlyBenchmarkFeatures = WizardOptimizationProblem.extractFeatureRecordsFromFile(null,
        param);
    target = statsToTargetList(stats);
    paramToOptimize = param.getValue(OptimizerParameters.paramToOptimize);
    this.externalStatus = externalStatus;
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

    builder = new WizardParameterSolutionBuilder(stats, null,
        initialSequence.get(WizardPart.MS).map(WizardStepParameters::getFactory)
            .map(MassSpectrometerWizardParameterFactory.LOW_RES::equals).orElse(false));

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

  /**
   * One constraint when the shape rejection limit is enabled, none otherwise. Must be static
   * because it is needed inside the {@code super(...)} call.
   */
  static int calculateNumberOfConstraints(@NotNull ParameterSet param) {
    return param.getValue(OptimizerParameters.maxShapeRejectionFactor) ? 1 : 0;
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

    for (ParameterSolutionPrototype factory : paramToOptimize) {
      if (factory instanceof WizardParameterSolutionPrototype wbs) {
        param.add(wbs.toRealSolution(builder, index++));
      }
    }

    return param;
  }

  private @NotNull List<BatchParameterSolution> createBatchParameters() {
    int index = numWizardParam;

    final List<BatchParameterSolution> param = new ArrayList<>();

    for (ParameterSolutionPrototype factory : paramToOptimize) {
      if (factory instanceof BatchParameterSolutionPrototype bws) {
        param.add(bws.toBatchParameterSolution(index++));
      }
    }

    return param;
  }

  @Override
  public void evaluate(@NotNull Solution solution) {

    // untagged means the variation operators built it, see SolutionOrigin#applyIfAbsent
    SolutionOrigin.EVOLUTION.applyIfAbsent(solution);

    final List<Double> cacheKey = cacheKey(solution);
    final Solution cached = evaluationCache.get(cacheKey);
    if (cached != null) {
      copyResult(cached, solution);
      solution.setAttribute(ATTR_CACHE_HIT, true);
      solution.setAttribute(ATTR_EVALUATION, evaluatedSolutions.size() + 1);
      evaluatedSolutions.add(solution);
      return;
    }

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
    optimizedQueue.removeIf(step -> step.getModule() instanceof CompoundGrouperModule);

    // use the current project, so we dont import files on every iteration
    final MZmineProject project = ProjectService.getProject();
    final BatchTask batchTask = BatchModeModule.runBatchQueue(optimizedQueue, project, files, null,
        null, null, Instant.now(), null, null);
    final double fullBatchTime = batchTask.getStepTimes().getLast().secondsToFinish();

    while (!batchTask.isFinished() && !batchTask.isCanceled()) {
      try {
        TimeUnit.MILLISECONDS.sleep(200);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    if (batchTask.isCanceled() || externalStatus.get() != TaskStatus.PROCESSING) {
      // we need to throw here, because the the optimizer does not respect
      // the termination condition with the task status
      throw new RuntimeException("Batch optimization task was canceled");
    }

    final FeatureList newest = batchTask.getLatestCreatedFeatureLists().getFirst();

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
    solution.setAttribute("Rows (incl. isotopes)", newest.getRows().size());
    solution.setAttribute("Runtime / s", fullBatchTime);

    // decision: computed only when the constraint reads it. Fitting peak models is by far the most
    // expensive diagnostic - it dominated an evaluation once the sample size was raised - and with
    // the constraint off nothing consumes the result, so it is pure cost.
    if (getNumberOfConstraints() > 0) {
      final long shapeStart = System.nanoTime();
      final ShapeScoreDiagnostic.Result shape = ShapeScoreDiagnostic.evaluate(newest,
          ShapeScoreDiagnostic.STRICT_SHAPE_SCORE);
      solution.setAttribute(ShapeScoreDiagnostic.ATTR_REMOVE_PERCENT, shape.wouldRemovePercent());
      // decision: a constraint rather than a penalty term, so no score changes meaning and the
      // algorithm's own constraint domination keeps the search out of the noisy region
      solution.setConstraintValue(0, shape.wouldRemovePercent());
      solution.setAttribute(ShapeScoreDiagnostic.ATTR_DOUBLE_PEAK_PERCENT,
          shape.doublePeakPercent());
      solution.setAttribute("Shape score sample", shape.inspected());
      logger.finest("Shape diagnostic: %s (took %.1f s)".formatted(shape,
          (System.nanoTime() - shapeStart) / 1e9));
    }

    // decision: always computed, because it is cheap and because it measures the failure mode the
    // shape diagnostic is blind to - marginal detections that fit a peak model perfectly well
    final long precisionStart = System.nanoTime();
    final PrecisionDiagnostic.Result precision = PrecisionDiagnostic.evaluate(newest);
    solution.setAttribute(PrecisionDiagnostic.ATTR_SINGLE_FILE_PERCENT,
        precision.singleFilePercent());
    solution.setAttribute(PrecisionDiagnostic.ATTR_NO_ISOTOPE_PERCENT,
        precision.withoutIsotopesPercent());
    solution.setAttribute(PrecisionDiagnostic.ATTR_MEDIAN_HEIGHT, precision.medianHeight());
    solution.setAttribute(PrecisionDiagnostic.ATTR_LOW_HEIGHT, precision.lowHeight());
    logger.finest("Precision diagnostic: %s (took %.2f s)".formatted(precision,
        (System.nanoTime() - precisionStart) / 1e9));

    solution.setAttribute(ATTR_CACHE_HIT, false);
    solution.setAttribute(ATTR_EVALUATION, evaluatedSolutions.size() + 1);
    evaluatedSolutions.add(solution);
    evaluationCache.put(cacheKey, solution);

    project.removeFeatureLists(batchTask.getLatestCreatedFeatureLists());
  }

  /**
   * Identifies a parameter vector by the values the batch is actually run with.
   * <p>
   * decision: the effective value, not the raw one. {@link OrdinalIntegerVariable} is backed by a
   * real value that is rounded when read, so two vectors differing only in the discarded fraction
   * build an identical batch queue and must share a key.
   */
  private @NotNull List<Double> cacheKey(@NotNull Solution solution) {
    final List<Double> key = new ArrayList<>(solution.getNumberOfVariables());
    for (int i = 0; i < solution.getNumberOfVariables(); i++) {
      final Variable variable = solution.getVariable(i);
      key.add(variable instanceof OrdinalIntegerVariable ? OrdinalIntegerVariable.effectiveValue(
          solution, i) : RealVariable.getReal(variable));
    }
    return List.copyOf(key);
  }

  /**
   * Copies everything the algorithm and the results table read from an evaluated solution:
   * objectives drive selection, constraints drive feasibility, attributes drive the table.
   * <p>
   * {@link #OWN_ATTRIBUTES} are excluded, because they describe this evaluation rather than the
   * parameter vector - a cache hit is its own position in the search and keeps its own origin.
   */
  private void copyResult(@NotNull Solution from, @NotNull Solution to) {
    for (int i = 0; i < to.getNumberOfObjectives(); i++) {
      to.setObjectiveValue(i, from.getObjectiveValue(i));
    }
    for (int i = 0; i < to.getNumberOfConstraints(); i++) {
      to.setConstraintValue(i, from.getConstraintValue(i));
    }
    from.getAttributes().forEach((key, value) -> {
      if (!OWN_ATTRIBUTES.contains(key)) {
        to.setAttribute(key, value);
      }
    });
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
//    filterParam.setParameter(FilterWizardParameters.goodPeaksOnly, true);
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

    final Solution solution = new Solution(getNumberOfVariables(), getNumberOfObjectives(),
        getNumberOfConstraints());

    if (getNumberOfConstraints() > 0) {
      solution.setConstraint(0, new LessThanOrEqual(ShapeScoreDiagnostic.ATTR_REMOVE_PERCENT,
          shapeRejectionLimitPercent));
    }

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

  /**
   * Sets the shape rejection limit used by the constraint. Call after the raw data estimate has
   * been evaluated, so the limit can be derived from a measured baseline rather than guessed.
   */
  public void setShapeRejectionLimitPercent(double limitPercent) {
    this.shapeRejectionLimitPercent = limitPercent;
    logger.info("Shape rejection limit set to %.1f %%".formatted(limitPercent));
  }

  /**
   * Every evaluated solution in evaluation order, including infeasible ones and the raw data
   * estimate.
   */
  public @NotNull List<Solution> getEvaluatedSolutions() {
    synchronized (evaluatedSolutions) {
      return List.copyOf(evaluatedSolutions);
    }
  }

  public @Nullable List<FeatureRecord> getAllTargets() {
    return target;
  }

  public @Nullable List<FeatureRecord> getFileOnlyBenchmarkFeatures() {
    return fileOnlyBenchmarkFeatures;
  }

  public @NotNull List<SweepMetric> getEnabledMetrics() {
    return enabledMetrics;
  }

  public @NotNull WizardParameterSolutionBuilder getBuilder() {
    return builder;
  }
}
