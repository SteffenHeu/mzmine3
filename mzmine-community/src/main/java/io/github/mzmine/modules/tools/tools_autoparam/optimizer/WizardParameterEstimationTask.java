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
import io.github.mzmine.modules.tools.batchwizard.WizardPart;
import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassDetectorWizardOptions;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassSpectrometerWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.WizardStepParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.custom_parameters.WizardMassDetectorNoiseLevels;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.MassSpectrometerWizardParameterFactory;
import io.github.mzmine.modules.tools.tools_autoparam.DataFileStatistics;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
import java.io.File;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Computes raw-file statistics and wizard estimates without running an optimization batch.
 */
public final class WizardParameterEstimationTask extends AbstractTask {

  private final File @NotNull [] files;
  private final @Nullable File metadataFile;
  private final @NotNull WizardSequence sequence;
  private final @NotNull Consumer<WizardParameterEstimationResult> onFinished;
  private volatile double progress;

  public WizardParameterEstimationTask(@Nullable MemoryMapStorage storage,
      @NotNull Instant moduleCallDate, File @NotNull [] files, @Nullable File metadataFile,
      @NotNull WizardSequence sequence,
      @NotNull Consumer<WizardParameterEstimationResult> onFinished) {
    super(storage, moduleCallDate, "Estimate wizard parameters");
    this.files = files.clone();
    this.metadataFile = metadataFile;
    this.sequence = sequence;
    this.onFinished = onFinished;
  }

  @Override
  public @NotNull String getTaskDescription() {
    return "Estimating wizard parameters from %d raw data file(s)".formatted(files.length);
  }

  @Override
  public double getFinishedPercentage() {
    return progress;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    try {
      final List<RawDataFile> importedFiles = OptimizationUtils.importFilesBlocking(files,
          metadataFile);
      progress = 0.35;
      if (isCanceled()) {
        return;
      }
      if (importedFiles.isEmpty()) {
        throw new IllegalStateException("None of the selected wizard files could be imported.");
      }

      final List<DataFileStatistics> statistics = OptimizationUtils.computeFileStatistics(
          importedFiles, null, getMemoryMapStorage());
      progress = 0.8;
      if (isCanceled()) {
        return;
      }

      final @Nullable MassDetectorWizardOptions detectorType = selectedMassDetector();
      final boolean lowResolution = sequence.get(WizardPart.MS)
          .map(WizardStepParameters::getFactory)
          .map(MassSpectrometerWizardParameterFactory.LOW_RES::equals).orElse(false);
      final WizardParameterSolutionBuilder builder = new WizardParameterSolutionBuilder(statistics,
          detectorType, lowResolution);
      final Map<String, Double> estimates = new LinkedHashMap<>(
          SinglePassParameterEstimation.estimate(statistics, builder, sequence));
      if (statistics.size() < 2 || builder.getInterSampleRtStatistics().isEmpty()) {
        // decision: sample-to-sample RT requires at least one observed cross-file deviation.
        estimates.remove("Inter sample RT tolerance");
      }

      final WizardParameterEstimationResult result = new WizardParameterEstimationResult(statistics,
          builder, estimates);
      progress = 1d;
      FxThread.runLater(() -> onFinished.accept(result));
      setStatus(TaskStatus.FINISHED);
    } catch (Exception e) {
      error("Could not estimate wizard parameters: " + e.getMessage(), e);
    }
  }

  private @Nullable MassDetectorWizardOptions selectedMassDetector() {
    return sequence.get(WizardPart.MS)
        .map(step -> step.getValue(MassSpectrometerWizardParameters.massDetectorOption))
        .map(WizardMassDetectorNoiseLevels::getValueType).orElse(null);
  }
}
