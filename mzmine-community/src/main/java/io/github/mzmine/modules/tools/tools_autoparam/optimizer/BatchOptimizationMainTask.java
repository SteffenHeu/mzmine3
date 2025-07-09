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
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.moeaframework.algorithm.AbstractAlgorithm;
import org.moeaframework.algorithm.NSGAII;
import org.moeaframework.core.population.NondominatedPopulation;

public class BatchOptimizationMainTask extends AbstractTask {

  private static final int MAX_EVALUATIONS = 100;
  private static final Logger logger = Logger.getLogger(BatchOptimizationMainTask.class.getName());

  private final File[] files;
  private final BatchWizardTab tab;
  private final ParameterSet params;

  @Nullable
  private AbstractAlgorithm optimizer;

  public BatchOptimizationMainTask(@Nullable MemoryMapStorage storage,
      @NotNull Instant moduleCallDate, File[] files, BatchWizardTab tab, ParameterSet params) {
    super(storage, moduleCallDate);
    this.files = files;
    this.tab = tab;
    this.params = params;

    addTaskStatusListener((_, newStatus, _) -> {
      if(newStatus == TaskStatus.CANCELED && optimizer != null) {
        optimizer.terminate();
      }
    });
  }

  @Override
  public String getTaskDescription() {
    return "Performing batch optimization. Run %d/%d".formatted(
        (optimizer != null ? optimizer.getNumberOfEvaluations() : 0), MAX_EVALUATIONS);
  }

  @Override
  public double getFinishedPercentage() {
    return optimizer != null ? (double) optimizer.getNumberOfEvaluations() / MAX_EVALUATIONS : 0;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);

    final List<RawDataFile> importedFiles = OptimizationUtils.importFilesBlocking(files);
    final List<DataFileStatistics> stats = importedFiles.stream().map(
            file -> new AutoParamTask(getMemoryMapStorage(), Instant.now(),
                AutoParamParameters.of(importedFiles), AutoParamModule.class, file)).parallel()
        .map(AutoParamTask::runAndGet).toList();
    stats.forEach(stat -> logger.info(stat.getMzToleranceForIsotopes().toString()));

    final LcMsOptimizationProblem problem = new LcMsOptimizationProblem(tab.getSequence(), stats, params);

    optimizer = new NSGAII(problem);

    final int iterations = params.getValue(OptimizerParameters.iterations);
    optimizer.run(iterations);

    final NondominatedPopulation result = optimizer.getResult();

    FxThread.runLater(() -> {
      Stage stage = new Stage();
      final OptimizationResultsController controller = new OptimizationResultsController(
          tab, problem, result, stage);
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
