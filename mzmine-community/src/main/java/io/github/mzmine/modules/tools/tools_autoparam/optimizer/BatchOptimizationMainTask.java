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

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.gui.DesktopService;
import io.github.mzmine.gui.mainwindow.SimpleTab;
import io.github.mzmine.gui.preferences.MZminePreferences;
import io.github.mzmine.javafx.concurrent.threading.FxThread;
import io.github.mzmine.javafx.dialogs.NotificationService;
import io.github.mzmine.javafx.dialogs.NotificationService.NotificationType;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.tools.batchwizard.BatchWizardTab;
import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.modules.tools.tools_autoparam.DataFileStatistics;
import io.github.mzmine.modules.tools.tools_autoparam.DataFileStatisticsDashboardPane;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.gui.OptimizationResultsController;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.moeaframework.algorithm.AbstractAlgorithm;
import org.moeaframework.algorithm.AbstractEvolutionaryAlgorithm;
import org.moeaframework.algorithm.MOEAD;
import org.moeaframework.algorithm.sa.AbstractSimulatedAnnealingAlgorithm;
import org.moeaframework.core.PRNG;
import org.moeaframework.core.Solution;
import org.moeaframework.core.TypedProperties;
import org.moeaframework.core.configuration.Configurable;
import org.moeaframework.core.initialization.Initialization;
import org.moeaframework.core.population.NondominatedPopulation;

public class BatchOptimizationMainTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(BatchOptimizationMainTask.class.getName());

  /**
   * Initial population size for population-based MOEA algorithms. One evaluation is a full batch
   * run, so the MOEA Framework default of 100 would spend the entire batch budget on initialization
   * and leave no generations for the actual search.
   */
  private static final int EVOLUTIONARY_POPULATION_SIZE = 20;

  /**
   * Safety limit for cheap duplicate proposals per expensive batch execution.
   */
  private static final int PROPOSAL_BUDGET_MULTIPLIER = 10;

  /**
   * Lowest shape rejection limit in percent. Without a floor a dataset whose estimate rejects almost
   * nothing would make every candidate infeasible.
   */
  private static final double MIN_SHAPE_REJECTION_LIMIT = 5d;

  /**
   * Seed every run uses unless a caller asks for another one. Fixed on purpose: the same data and
   * the same settings have to give the same result for every user on every machine, without anyone
   * having to configure anything. Only a programmatic caller that deliberately wants to vary the
   * random draws - a benchmark measuring how much of a result is chance - passes its own.
   */
  public static final long DEFAULT_RANDOM_SEED = 42;

  private final File[] files;
  @Nullable
  private final File metadata;
  /**
   * Source of the parameter presets. Captured up front instead of read from the tab inside
   * {@link #run()}, so the task does not touch a GUI object from its own thread.
   */
  private final @NotNull WizardSequence sequence;
  /**
   * Null when there is no wizard to return to, i.e. when the optimization is driven headlessly. The
   * results window is then skipped and the outcome is read through {@link #getOutcome()}.
   */
  private final @Nullable BatchWizardTab tab;
  private final OptimizerParameters params;
  /**
   * Set once the optimization finished, so a headless caller can read the estimate, the front and
   * every evaluated solution back.
   */
  private @Nullable OptimizationOutcome outcome;
  private final long randomSeed;
  private final AtomicReference<TaskStatus> externalStatus = new AtomicReference<>(
      TaskStatus.PROCESSING);
  /**
   * Maximum number of uncached full batch executions, including the raw-data estimate.
   */
  private int totalBatchExecutions;

  @Nullable
  private AbstractAlgorithm optimizer;
  @Nullable
  private WizardOptimizationProblem problem;

  public BatchOptimizationMainTask(@Nullable MemoryMapStorage storage,
      @NotNull Instant moduleCallDate, @NotNull File[] files, @Nullable File metadata,
      @NotNull BatchWizardTab tab, @NotNull OptimizerParameters params) {
    this(storage, moduleCallDate, files, metadata, tab.getSequence(), tab, params);
  }

  /**
   * Runs an optimization without a wizard tab, for tests and scripted comparisons. No results
   * window is opened; read the result through {@link #getOutcome()} once the task finished.
   */
  public BatchOptimizationMainTask(@Nullable MemoryMapStorage storage,
      @NotNull Instant moduleCallDate, @NotNull File[] files, @Nullable File metadata,
      @NotNull WizardSequence sequence, @NotNull OptimizerParameters params) {
    this(storage, moduleCallDate, files, metadata, sequence, params, DEFAULT_RANDOM_SEED);
  }

  /**
   * Runs headlessly with an explicit random seed, so a caller can measure how much of a result
   * comes from the data and how much from the draw. Use {@link #DEFAULT_RANDOM_SEED} to reproduce
   * what a user would get.
   */
  public BatchOptimizationMainTask(@Nullable MemoryMapStorage storage,
      @NotNull Instant moduleCallDate, @NotNull File[] files, @Nullable File metadata,
      @NotNull WizardSequence sequence, @NotNull OptimizerParameters params, long randomSeed) {
    this(storage, moduleCallDate, files, metadata, sequence, null, params, randomSeed);
  }

  private BatchOptimizationMainTask(@Nullable MemoryMapStorage storage,
      @NotNull Instant moduleCallDate, @NotNull File[] files, @Nullable File metadata,
      @NotNull WizardSequence sequence, @Nullable BatchWizardTab tab,
      @NotNull OptimizerParameters params) {
    this(storage, moduleCallDate, files, metadata, sequence, tab, params, DEFAULT_RANDOM_SEED);
  }

  private BatchOptimizationMainTask(@Nullable MemoryMapStorage storage,
      @NotNull Instant moduleCallDate, @NotNull File[] files, @Nullable File metadata,
      @NotNull WizardSequence sequence, @Nullable BatchWizardTab tab,
      @NotNull OptimizerParameters params, long randomSeed) {
    super(storage, moduleCallDate);
    this.files = files;
    this.metadata = metadata;
    this.sequence = sequence;
    this.tab = tab;
    this.params = params;
    this.randomSeed = randomSeed;

    addTaskStatusListener((_, newStatus, _) -> {
      if (newStatus == TaskStatus.CANCELED && optimizer != null) {
        optimizer.terminate();
      }
    });
  }

  /**
   * @return the finished optimization's estimate, front and evaluated solutions, or null while the
   * task has not completed.
   */
  public @Nullable OptimizationOutcome getOutcome() {
    return outcome;
  }

  @Override
  public String getTaskDescription() {
    final int max = totalBatchExecutions > 0 ? totalBatchExecutions : 100;
    final WizardOptimizationProblem currentProblem = problem;
    final int completed = currentProblem != null ? currentProblem.getBatchExecutionCount() : 0;
    return "Performing batch optimization. Full batch %d/%d".formatted(completed, max);
  }

  @Override
  public double getFinishedPercentage() {
    final int max = totalBatchExecutions > 0 ? totalBatchExecutions : 100;
    final WizardOptimizationProblem currentProblem = problem;
    return currentProblem != null ? Math.min(1d,
        (double) currentProblem.getBatchExecutionCount() / max) : 0d;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);

    addTaskStatusListener((_, newStatus, _) -> externalStatus.set(newStatus));

    final List<RawDataFile> importedFiles = OptimizationUtils.importFilesBlocking(files, metadata);
    final List<FeatureRecord> benchmarkFeatures = WizardOptimizationProblem.extractFeatureRecordsFromFile(
        null, params);

    final List<DataFileStatistics> stats = OptimizationUtils.computeFileStatistics(importedFiles,
        benchmarkFeatures, getMemoryMapStorage());
    stats.forEach(stat -> logger.info(stat.getMzToleranceForIsotopes().toString()));

    if (DesktopService.isGUI()) {
      final List<DataFileStatistics> dashboardStats = List.copyOf(stats);
      FxThread.runLater(() -> MZmineCore.getDesktop().addTab(new SimpleTab("Auto Param Statistics",
          new DataFileStatisticsDashboardPane(dashboardStats))));
    }

    // set a specific seed to make the results deterministic, see DEFAULT_RANDOM_SEED
    PRNG.setSeed(randomSeed);

    // store all in ram while optimizing
    addTaskStatusListener((_, _, _) -> ConfigService.getPreference(MZminePreferences.memoryOption)
        .enforceToMemoryMapping());
    MemoryMapStorage.setStoreAllInRam(true);

    totalBatchExecutions = Math.max(params.getValue(OptimizerParameters.iterations), 30);
    final WizardOptimizationProblem optimizationProblem = new WizardOptimizationProblem(sequence,
        stats, params, externalStatus, totalBatchExecutions);
    problem = optimizationProblem;

    optimizer = params.getValue(OptimizerParameters.optimizers).getOptimizer(optimizationProblem);

    final boolean initWithGuesses = params.getValue(
        OptimizerParameters.initializeWithRawDataGuesses);

    final Map<String, Double> singlePassEstimates = SinglePassParameterEstimation.estimate(stats,
        optimizationProblem.getBuilder());
    final Solution singlePassSolution = optimizationProblem.newSolution();

    // decision: always derive and evaluate the raw data estimate, also when it is not used to
    // warm-start the optimizer, so the results table can always show it next to the optimized
    // solutions and the logged comparison is meaningful in both cases
    SinglePassParameterEstimation.applyToSolution(singlePassSolution, singlePassEstimates);
    SolutionOrigin.ESTIMATE.applyTo(singlePassSolution);
    optimizationProblem.evaluate(singlePassSolution);

    // decision: derive the shape rejection limit from the estimate's own measured rate, so the
    // limit adapts to the dataset instead of being an absolute guess
    if (params.getValue(OptimizerParameters.maxShapeRejectionFactor)) {
      final double factor = params.getEmbeddedParameterValue(
          OptimizerParameters.maxShapeRejectionFactor);
      final Object measured = singlePassSolution.getAttribute(
          ShapeScoreDiagnostic.ATTR_REMOVE_PERCENT);
      final double baseline = measured instanceof Number n ? n.doubleValue() : 0d;
      // assumption: a floor keeps a near-perfect baseline from making everything infeasible
      optimizationProblem.setShapeRejectionLimitPercent(
          Math.max(baseline * factor, MIN_SHAPE_REJECTION_LIMIT));
    }

    final List<Solution> injected;
    if (initWithGuesses) {
      // decision: fill the whole evolutionary population from the estimate because uniform random
      // samples measured far worse. Sequential pattern search consumes only the exact estimate and
      // creates its coordinate polls itself.
      final int initialDesignSize = initialDesignSize(optimizer);
      injected = SinglePassParameterEstimation.createWarmStartSolutions(optimizationProblem,
          singlePassEstimates, initialDesignSize,
          params.getValue(OptimizerParameters.warmStartSampling));
      logger.info("Warm-start enabled for %s: injected %d solutions, batch budget %d".formatted(
          optimizer.getName(), injected.size(), totalBatchExecutions));

      NotificationService.show(NotificationType.INFO, "Starting optimizer", """
          Using %d attempts around raw-data based estimations and %d full batch executions.
          Estimates:
          %s""".formatted(injected.size(), totalBatchExecutions,
          singlePassEstimates.entrySet().stream()
              .map(e -> "%s: %.2f".formatted(e.getKey(), e.getValue()))
              .collect(Collectors.joining("\n"))));
    } else {
      injected = List.of();
    }

    // decision: size population-based algorithms explicitly. Without this the optimizer keeps the
    // MOEA Framework default of 100, which equals or exceeds the whole batch budget.
    configureInitialPopulation(optimizer, optimizationProblem, injected);

    try {
      final int maxProposals = Math.multiplyExact(totalBatchExecutions, PROPOSAL_BUDGET_MULTIPLIER);
      optimizer.run(new TaskStatusTerminationCondition(totalBatchExecutions, maxProposals,
          optimizationProblem::getBatchExecutionCount, this::getStatus));
    } catch (BatchExecutionLimitReachedException e) {
      // MOEA checks termination between generations, so the problem stops a partial generation at
      // the exact full-batch boundary.
      logger.fine(e.getMessage());
      optimizer.terminate();
    } catch (RuntimeException e) {
      if (getStatus() != TaskStatus.CANCELED && getStatus() != TaskStatus.ERROR) {
        throw e;
      }
    }

    // A hard budget stop can interrupt a generation after some offspring were evaluated but before
    // the algorithm incorporated them. Build the result from every completed observation so those
    // expensive final batches cannot be lost.
    final NondominatedPopulation result = new NondominatedPopulation();
    result.addAll(optimizer.getResult());
    result.addAll(optimizationProblem.getEvaluatedSolutions());

    // log comparison: single-pass vs best MOEA result
    SinglePassParameterEstimation.logResults(singlePassSolution, singlePassEstimates,
        optimizationProblem.getEnabledMetrics());
    SinglePassParameterEstimation.logComparison(singlePassSolution, result,
        optimizationProblem.getEnabledMetrics());

    outcome = new OptimizationOutcome(singlePassEstimates, singlePassSolution, result,
        optimizationProblem);

    // decision: the results window needs a wizard to apply its selection to, so a headless run only
    // records the outcome
    if (tab == null) {
      setStatus(TaskStatus.FINISHED);
      return;
    }

    final BatchWizardTab resultTab = tab;
    FxThread.runLater(() -> {
      Stage stage = new Stage();
      final OptimizationResultsController controller = new OptimizationResultsController(resultTab,
          optimizationProblem, result, singlePassSolution, stage);
      final Region region = controller.buildView();
      stage.setTitle("Optimization Results");
      stage.initOwner(MZmineCore.getDesktop().getMainWindow());
      Scene scene = new Scene(region);
      ConfigService.getConfiguration().getTheme().apply(scene.getStylesheets());
      stage.setScene(scene);
      stage.show();
      stage.setMaxWidth(Screen.getPrimary().getBounds().getWidth() * 0.8);
      stage.centerOnScreen();
    });

    setStatus(TaskStatus.FINISHED);
  }

  /**
   * Sets the initial population size and injects the raw data-derived warm-start solutions through
   * the channel supported by the concrete algorithm.
   *
   * @param injected solutions to seed the search with. May be empty, in which case population-based
   *                 algorithms initialize randomly and pattern search starts at the box center.
   */
  private void configureInitialPopulation(@NotNull AbstractAlgorithm algorithm,
      @NotNull WizardOptimizationProblem problem, @NotNull List<Solution> injected) {

    // decision: populationSize is an annotated @Property on every algorithm that exposes it, so it
    // can be set uniformly instead of per class. Algorithms without the property (CMAES, OMOPSO,
    // SMPSO) ignore the key rather than failing.
    if (algorithm instanceof Configurable configurable) {
      final TypedProperties config = new TypedProperties();
      config.setInt("populationSize", EVOLUTIONARY_POPULATION_SIZE);
      configurable.applyConfiguration(config);
    }

    // the initialization is not a scalar property, so it still needs a type check
    final Initialization initialization = new OriginTaggingInitialization(problem, injected);
    switch (algorithm) {
      case MOEAD moead -> {
        moead.setInitialization(initialization);
        // at the MOEA/D default of 20 the neighborhood equals the population, so mating draws
        // from the whole population and the decomposition loses the locality it relies on.
        // assumption: the floor of 4 keeps the neighborhood above the differential evolution arity
        // of 4 minus 1, which MOEA/D requires now that the solution vector is all real-valued
        moead.setNeighborhoodSize(Math.max(4, EVOLUTIONARY_POPULATION_SIZE / 5));
        // the variation operator is chosen in the MOEAD constructor from whether every variable is
        // real-valued, so log it - "de+pm" confirms MOEA/D-DE, "sbx+pm+hux+bf" means the vector
        // still contains a non-real variable and the decomposition fell back to SBX
        logger.info("MOEA/D using variation %s, neighborhood %d, population %d".formatted(
            moead.getVariation().getName(), moead.getNeighborhoodSize(),
            EVOLUTIONARY_POPULATION_SIZE));
      }
      // covers NSGA-II/III, U-NSGA-III, eps-NSGA-II, AGE-MOEA-II, GDE3, IBEA, RVEA, SMS-EMOA,
      // SPEA2, eps-MOEA, DBEA and PAES
      case AbstractEvolutionaryAlgorithm ea -> ea.setInitialization(initialization);
      case AbstractSimulatedAnnealingAlgorithm sa -> sa.setInitialization(initialization);
      case PatternSearchAlgorithm patternSearch -> patternSearch.setInitialSolutions(injected);
      // OMOPSO, SMPSO and CMAES do not expose the initialization and start from their own defaults
      default -> logger.warning(
          "%s cannot be warm-started and will initialize randomly.".formatted(algorithm.getName()));
    }
  }

  private int initialDesignSize(@NotNull AbstractAlgorithm algorithm) {
    return switch (algorithm) {
      case PatternSearchAlgorithm _ -> PatternSearchAlgorithm.INITIAL_DESIGN_SIZE;
      default -> EVOLUTIONARY_POPULATION_SIZE;
    };
  }
}
