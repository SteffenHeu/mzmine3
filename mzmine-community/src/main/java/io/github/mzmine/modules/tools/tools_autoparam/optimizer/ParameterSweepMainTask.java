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
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.gui.SweepResultsController;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ParameterSweepMainTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(ParameterSweepMainTask.class.getName());

  private final File[] files;
  @Nullable
  private final File metadata;
  private final BatchWizardTab tab;
  private final OptimizerParameters params;

  private final AtomicInteger completedRuns = new AtomicInteger(0);
  private int totalRuns = 0;

  public ParameterSweepMainTask(@Nullable MemoryMapStorage storage, @NotNull Instant moduleCallDate,
      @NotNull File[] files, @Nullable File metadata, @NotNull BatchWizardTab tab,
      @NotNull OptimizerParameters params) {
    super(storage, moduleCallDate);
    this.files = files;
    this.metadata = metadata;
    this.tab = tab;
    this.params = params;
  }

  @Override
  public String getTaskDescription() {
    return "Parameter sweep: %d/%d runs complete".formatted(completedRuns.get(), totalRuns);
  }

  @Override
  public double getFinishedPercentage() {
    return totalRuns > 0 ? (double) completedRuns.get() / totalRuns : 0;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);

    final List<RawDataFile> importedFiles = OptimizationUtils.importFilesBlocking(files, metadata);
    final List<FeatureRecord> externalBenchmarkFeatures = LcMsOptimizationProblem.extractFeatureRecordsFromFile(
        null, params);

    final List<DataFileStatistics> stats = importedFiles.stream().map(
        file -> new AutoParamTask(getMemoryMapStorage(), Instant.now(),
            AutoParamParameters.of(importedFiles), AutoParamModule.class, file,
            externalBenchmarkFeatures)).parallel().map(AutoParamTask::runAndGet).toList();

    List<FeatureRecord> benchmarkFeatures = Stream.of(
            LcMsOptimizationProblem.statsToTargetList(stats), externalBenchmarkFeatures)
        .flatMap(List::stream).toList();

    final ParameterSweepRunner runner = new ParameterSweepRunner(tab.getSequence(), stats, params);

    final int n = params.getValue(OptimizerParameters.samplesPerParam);
    totalRuns = runner.totalRuns(n);

    final List<ParameterSweepResult> sweepResults = runner.sweep(n, completedRuns);

    final List<SweepMetric> metrics = buildMetrics(benchmarkFeatures);
    final List<SweepMetricResult> evaluated = sweepResults.stream()
        .map(r -> SweepMetricResult.of(r, metrics)).toList();

    FxThread.runLater(() -> {
      final Stage stage = new Stage();
      final SweepResultsController controller = new SweepResultsController(tab, runner, evaluated,
          stage);
      final Region region = controller.buildView();
      stage.setTitle("Parameter Sweep Results");
      stage.initOwner(MZmineCore.getDesktop().getMainWindow());
      final Scene scene = new Scene(region);
      ConfigService.getConfiguration().getTheme().apply(scene.getStylesheets());
      stage.setScene(scene);
      stage.show();
    });

    setStatus(TaskStatus.FINISHED);
  }

  /**
   * Builds the metric list based on the enabled objectives in {@link OptimizerParameters}.
   */
  private List<SweepMetric> buildMetrics(@NotNull List<FeatureRecord> benchmarkFeatures) {
    final List<SweepMetric> metrics = new ArrayList<>();
    if (params.getValue(OptimizerParameters.maximizeCv20)) {
      metrics.add(SweepMetric.ROWS_BELOW_CV20);
    }
    if (params.getValue(OptimizerParameters.maximizeCv20WithIsos)) {
      metrics.add(SweepMetric.ROWS_WITH_ISOS_BELOW_CV20);
    }
    if (params.getValue(OptimizerParameters.maximizeFeaturesWithIsotopes)) {
      metrics.add(SweepMetric.FEATURES_WITH_ISOTOPES);
    }
    if (params.getValue(OptimizerParameters.minimizeDoublePeaks)) {
      metrics.add(SweepMetric.DOUBLE_PEAK_RATIO);
    }
    if (params.getValue(OptimizerParameters.maximizeRowFillRatio)) {
      metrics.add(SweepMetric.FILL_RATIO);
    }
    if (params.getValue(OptimizerParameters.slawIntegrationScore)) {
      metrics.add(SweepMetric.CV20_ISO_ROWS_RATIO);
    }
    if (params.getValue(OptimizerParameters.harmonicSlawIsotopes)) {
      metrics.add(SweepMetric.HARMONIC_SLAW_ISOTOPES);
    }
    if (!benchmarkFeatures.isEmpty() || params.getValue(
        OptimizerParameters.maximizeNumberOfBenchmarkFeatures)) {
      metrics.add(new SweepMetric.BenchmarkTargetCount(benchmarkFeatures));
    }
    return metrics;
  }
}
