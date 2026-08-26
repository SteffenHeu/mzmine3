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
import io.github.mzmine.modules.tools.tools_autoparam.AutoParamModule;
import io.github.mzmine.modules.tools.tools_autoparam.AutoParamParameters;
import io.github.mzmine.modules.tools.tools_autoparam.AutoParamTask;
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
   * Initial population size applied to every optimizer. One evaluation is a full batch run, so the
   * MOEA Framework default of 100 would spend the entire evaluation budget on initialization and
   * leave no generations for the actual search.
   */
  private static final int POPULATION_SIZE = 20;

  /**
   * Lowest shape rejection limit in percent. Without a floor a dataset whose estimate rejects almost
   * nothing would make every candidate infeasible.
   */
  private static final double MIN_SHAPE_REJECTION_LIMIT = 5d;

  private final File[] files;
  @Nullable
  private final File metadata;
  private final BatchWizardTab tab;
  private final OptimizerParameters params;
  private final AtomicReference<TaskStatus> externalStatus = new AtomicReference<>(
      TaskStatus.PROCESSING);
  /**
   * Actual number of evaluations for this run. Set during {@link #run()} — may be reduced from the
   * user-configured iterations when warm-starting.
   */
  private int totalIterations;

  @Nullable
  private AbstractAlgorithm optimizer;

  public BatchOptimizationMainTask(@Nullable MemoryMapStorage storage,
      @NotNull Instant moduleCallDate, @NotNull File[] files, @Nullable File metadata,
      @NotNull BatchWizardTab tab, @NotNull OptimizerParameters params) {
    super(storage, moduleCallDate);
    this.files = files;
    this.metadata = metadata;
    this.tab = tab;
    this.params = params;

    addTaskStatusListener((_, newStatus, _) -> {
      if (newStatus == TaskStatus.CANCELED && optimizer != null) {
        optimizer.terminate();
      }
    });
  }

  @Override
  public String getTaskDescription() {
    final int max = totalIterations > 0 ? totalIterations : 100;
    return "Performing batch optimization. Run %d/%d".formatted(
        (optimizer != null ? optimizer.getNumberOfEvaluations() : 0), max);
  }

  @Override
  public double getFinishedPercentage() {
    final int max = totalIterations > 0 ? totalIterations : 100;
    return optimizer != null ? (double) optimizer.getNumberOfEvaluations() / max : 0;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);

    addTaskStatusListener((_, newStatus, _) -> externalStatus.set(newStatus));

    final List<RawDataFile> importedFiles = OptimizationUtils.importFilesBlocking(files, metadata);
    final List<FeatureRecord> benchmarkFeatures = WizardOptimizationProblem.extractFeatureRecordsFromFile(
        null, params);

    final List<DataFileStatistics> stats = importedFiles.stream().map(
        file -> new AutoParamTask(getMemoryMapStorage(), Instant.now(),
            AutoParamParameters.of(importedFiles), AutoParamModule.class, file, benchmarkFeatures,
            false)).parallel().map(AutoParamTask::runAndGet).toList();
    stats.forEach(stat -> logger.info(stat.getMzToleranceForIsotopes().toString()));

    if (DesktopService.isGUI()) {
      final List<DataFileStatistics> dashboardStats = List.copyOf(stats);
      FxThread.runLater(() -> MZmineCore.getDesktop().addTab(new SimpleTab("Auto Param Statistics",
          new DataFileStatisticsDashboardPane(dashboardStats))));
    }

    // set a specific seed to make the results deterministic.
    PRNG.setSeed(42);

    // store all in ram while optimizing
    addTaskStatusListener((_, _, _) -> ConfigService.getPreference(MZminePreferences.memoryOption)
        .enforceToMemoryMapping());
    MemoryMapStorage.setStoreAllInRam(true);

    final WizardOptimizationProblem problem = new WizardOptimizationProblem(tab.getSequence(),
        stats, params, externalStatus);

    optimizer = params.getValue(OptimizerParameters.optimizers).getOptimizer(problem);
    totalIterations = Math.max(params.getValue(OptimizerParameters.iterations), 30);

    final boolean initWithGuesses = params.getValue(
        OptimizerParameters.initializeWithRawDataGuesses);

    final Map<String, Double> singlePassEstimates = SinglePassParameterEstimation.estimate(stats,
        problem.getBuilder());
    final Solution singlePassSolution = problem.newSolution();

    // decision: always derive and evaluate the raw data estimate, also when it is not used to
    // warm-start the optimizer, so the results table can always show it next to the optimized
    // solutions and the logged comparison is meaningful in both cases
    SinglePassParameterEstimation.applyToSolution(singlePassSolution, singlePassEstimates);
    SolutionOrigin.ESTIMATE.applyTo(singlePassSolution);
    problem.evaluate(singlePassSolution);

    // decision: derive the shape rejection limit from the estimate's own measured rate, so the
    // limit adapts to the dataset instead of being an absolute guess
    if (params.getValue(OptimizerParameters.maxShapeRejectionFactor)) {
      final double factor = params.getEmbeddedParameterValue(
          OptimizerParameters.maxShapeRejectionFactor);
      final Object measured = singlePassSolution.getAttribute(
          ShapeScoreDiagnostic.ATTR_REMOVE_PERCENT);
      final double baseline = measured instanceof Number n ? n.doubleValue() : 0d;
      // assumption: a floor keeps a near-perfect baseline from making everything infeasible
      problem.setShapeRejectionLimitPercent(Math.max(baseline * factor, MIN_SHAPE_REJECTION_LIMIT));
    }

    final List<Solution> injected;
    if (initWithGuesses) {
      // decision: the whole population, not a fraction of it. Uniform random samples score far
      // below the estimate on real data - in the 200 evaluation reference runs the best of ten was
      // 55 % worse than the estimate itself, while the winner was always a perturbation of it. So
      // the slots are worth more as further perturbations than as random draws.
      injected = SinglePassParameterEstimation.createWarmStartSolutions(problem,
          singlePassEstimates, POPULATION_SIZE);
      logger.info(
          "Warm-start enabled for %s: injected %d solutions, total evaluations %d".formatted(
              optimizer.getName(), injected.size(), totalIterations));

      NotificationService.show(NotificationType.INFO, "Starting optimizer", """
          Using %d attempts around raw-data based estimations and %d total optimization iterations.
          Estimates:
          %s""".formatted(injected.size(), totalIterations,
          singlePassEstimates.entrySet().stream()
              .map(e -> "%s: %.2f".formatted(e.getKey(), e.getValue()))
              .collect(Collectors.joining("\n"))));
    } else {
      injected = List.of();
    }

    // decision: size the population explicitly in both cases. Without this the optimizer keeps the
    // MOEA Framework default of 100, which equals or exceeds the whole evaluation budget.
    configureInitialPopulation(optimizer, problem, injected);

    try {
      optimizer.run(new TaskStatusTerminationCondition(totalIterations, this::getStatus));
    } catch (RuntimeException e) {
      // we throw an exception to cancel the optimization if the user wants to stop. Termination condition is not checked continuously
    }

    final NondominatedPopulation result = optimizer.getResult();

    // log comparison: single-pass vs best MOEA result
    SinglePassParameterEstimation.logResults(singlePassSolution, singlePassEstimates,
        problem.getEnabledMetrics());
    SinglePassParameterEstimation.logComparison(singlePassSolution, result,
        problem.getEnabledMetrics());

    FxThread.runLater(() -> {
      Stage stage = new Stage();
      final OptimizationResultsController controller = new OptimizationResultsController(tab,
          problem, result, singlePassSolution, stage);
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
   * Sets the initial population size and, where the algorithm supports it, injects the raw
   * data-derived warm-start solutions.
   *
   * @param injected solutions to seed the initial population with. May be empty, in which case
   *                 {@link OriginTaggingInitialization} falls back to a fully random population.
   */
  private void configureInitialPopulation(@NotNull AbstractAlgorithm algorithm,
      @NotNull WizardOptimizationProblem problem, @NotNull List<Solution> injected) {

    // decision: populationSize is an annotated @Property on every algorithm that exposes it, so it
    // can be set uniformly instead of per class. Algorithms without the property (CMAES, OMOPSO,
    // SMPSO) ignore the key rather than failing.
    if (algorithm instanceof Configurable configurable) {
      final TypedProperties config = new TypedProperties();
      config.setInt("populationSize", POPULATION_SIZE);
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
        moead.setNeighborhoodSize(Math.max(4, POPULATION_SIZE / 5));
        // the variation operator is chosen in the MOEAD constructor from whether every variable is
        // real-valued, so log it - "de+pm" confirms MOEA/D-DE, "sbx+pm+hux+bf" means the vector
        // still contains a non-real variable and the decomposition fell back to SBX
        logger.info("MOEA/D using variation %s, neighborhood %d, population %d".formatted(
            moead.getVariation().getName(), moead.getNeighborhoodSize(), POPULATION_SIZE));
      }
      // covers NSGA-II/III, U-NSGA-III, eps-NSGA-II, AGE-MOEA-II, GDE3, IBEA, RVEA, SMS-EMOA,
      // SPEA2, eps-MOEA, DBEA and PAES
      case AbstractEvolutionaryAlgorithm ea -> ea.setInitialization(initialization);
      case AbstractSimulatedAnnealingAlgorithm sa -> sa.setInitialization(initialization);
      // OMOPSO, SMPSO and CMAES do not expose the initialization and start from their own defaults
      default -> logger.warning(
          "%s cannot be warm-started and will initialize randomly.".formatted(algorithm.getName()));
    }
  }
}
