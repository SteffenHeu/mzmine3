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
import io.github.mzmine.javafx.concurrent.threading.FxThread;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.tools.batchwizard.BatchWizardTab;
import io.github.mzmine.modules.tools.tools_autoparam.AutoParamModule;
import io.github.mzmine.modules.tools.tools_autoparam.AutoParamParameters;
import io.github.mzmine.modules.tools.tools_autoparam.AutoParamTask;
import io.github.mzmine.modules.tools.tools_autoparam.DataFileStatistics;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.gui.OptimizationResultsController;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.moeaframework.algorithm.AGEMOEAII;
import org.moeaframework.algorithm.AbstractAlgorithm;
import org.moeaframework.algorithm.EpsilonMOEA;
import org.moeaframework.algorithm.GDE3;
import org.moeaframework.algorithm.IBEA;
import org.moeaframework.algorithm.MOEAD;
import org.moeaframework.algorithm.NSGAII;
import org.moeaframework.algorithm.RVEA;
import org.moeaframework.algorithm.SMSEMOA;
import org.moeaframework.algorithm.SPEA2;
import org.moeaframework.core.PRNG;
import org.moeaframework.core.Solution;
import org.moeaframework.core.initialization.InjectedInitialization;
import org.moeaframework.core.population.NondominatedPopulation;

public class BatchOptimizationMainTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(BatchOptimizationMainTask.class.getName());

  private final File[] files;
  @Nullable
  private final File metadata;
  private final BatchWizardTab tab;
  private final OptimizerParameters params;

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

    final List<RawDataFile> importedFiles = OptimizationUtils.importFilesBlocking(files, metadata);
    final List<FeatureRecord> benchmarkFeatures = WizardOptimizationProblem.extractFeatureRecordsFromFile(
        null, params);

    final List<DataFileStatistics> stats = importedFiles.stream().map(
            file -> new AutoParamTask(getMemoryMapStorage(), Instant.now(),
                AutoParamParameters.of(importedFiles), AutoParamModule.class, file, benchmarkFeatures))
        .parallel().map(AutoParamTask::runAndGet).toList();
    stats.forEach(stat -> logger.info(stat.getMzToleranceForIsotopes().toString()));

    // set a specific seed to make the results deterministic.
    PRNG.setSeed(42);

    final WizardOptimizationProblem problem = new WizardOptimizationProblem(tab.getSequence(),
        stats, params);

    // single-pass parameter estimation: derive best-guess values and evaluate once
    final Map<String, Double> singlePassEstimates = SinglePassParameterEstimation.estimate(stats,
        problem.getBuilder());
    final Solution singlePassSolution = problem.newSolution();
    SinglePassParameterEstimation.applyToSolution(singlePassSolution, singlePassEstimates);
    problem.evaluate(singlePassSolution);

    optimizer = params.getValue(OptimizerParameters.optimizers).getOptimizer(problem);
    totalIterations = Math.max(params.getValue(OptimizerParameters.iterations), 30);

    // Initial population is the number of evaluations before the actual evolutionary algorithm starts.
    // warm-start for MOEAD: inject pre-built solutions via InjectedInitialization and reduce
    // evaluation count since we start closer to good solutions
    final int numGuesstimatedPopulations = 10;
    final boolean initWithGuesses = params.getValue(
        OptimizerParameters.initializeWithRawDataGuesses);
    if (initWithGuesses) {
      final List<Solution> injected = SinglePassParameterEstimation.createWarmStartSolutions(
          problem, singlePassEstimates, numGuesstimatedPopulations);
      logger.info(
          "Warm-start enabled for %s: injected %d solutions, total evaluations %d".formatted(
              optimizer.getName(), injected.size(), totalIterations));

      switch (optimizer) {
        case MOEAD m -> {
          m.setInitialization(new InjectedInitialization(problem, injected));
          m.setInitialPopulationSize(Math.clamp(numGuesstimatedPopulations + 5, 20, 30));
        }
        case NSGAII n -> {
          n.setInitialization(new InjectedInitialization(problem, injected));
          n.setInitialPopulationSize(Math.clamp(numGuesstimatedPopulations + 5, 20, 30));
        }
        case AGEMOEAII a -> {
          a.setInitialization(new InjectedInitialization(problem, injected));
          a.setInitialPopulationSize(Math.clamp(numGuesstimatedPopulations + 5, 20, 30));
        }
        case GDE3 g -> {
          g.setInitialization(new InjectedInitialization(problem, injected));
          g.setInitialPopulationSize(Math.clamp(numGuesstimatedPopulations + 5, 20, 30));
        }
        case RVEA r -> {
          r.setInitialization(new InjectedInitialization(problem, injected));
          r.setInitialPopulationSize(Math.clamp(numGuesstimatedPopulations + 5, 20, 30));
        }
        case SMSEMOA s -> {
          s.setInitialization(new InjectedInitialization(problem, injected));
          s.setInitialPopulationSize(Math.clamp(numGuesstimatedPopulations + 5, 20, 30));
        }
        case SPEA2 s -> {
          s.setInitialization(new InjectedInitialization(problem, injected));
          s.setInitialPopulationSize(Math.clamp(numGuesstimatedPopulations + 5, 20, 30));
        }
        case EpsilonMOEA e -> {
          e.setInitialization(new InjectedInitialization(problem, injected));
          e.setInitialPopulationSize(Math.clamp(numGuesstimatedPopulations + 5, 20, 30));
        }
        case IBEA e -> {
          e.setInitialization(new InjectedInitialization(problem, injected));
          e.setInitialPopulationSize(Math.clamp(numGuesstimatedPopulations + 5, 20, 30));
        }
        default -> {
        }
      }
    }

    optimizer.run(new TaskStatusTerminationCondition(totalIterations, this::getStatus));

    final NondominatedPopulation result = optimizer.getResult();

    // log comparison: single-pass vs best MOEA result
    SinglePassParameterEstimation.logResults(singlePassSolution, singlePassEstimates,
        problem.getEnabledMetrics());
    SinglePassParameterEstimation.logComparison(singlePassSolution, result,
        problem.getEnabledMetrics());

    FxThread.runLater(() -> {
      Stage stage = new Stage();
      final OptimizationResultsController controller = new OptimizationResultsController(tab,
          problem, result, stage);
      final Region region = controller.buildView();
      stage.setTitle("Optimization Results");
      stage.initOwner(MZmineCore.getDesktop().getMainWindow());
      Scene scene = new Scene(region);
      ConfigService.getConfiguration().getTheme().apply(scene.getStylesheets());
      stage.setScene(scene);
      stage.show();
    });

    setStatus(TaskStatus.FINISHED);
  }
}
