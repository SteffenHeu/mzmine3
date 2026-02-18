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

package io.github.mzmine.modules.tools.tools_autoparam.optimizer.gui;

import static io.github.mzmine.modules.tools.batchwizard.WizardPart.WORKFLOW;

import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.compoundannotations.SimpleCompoundDBAnnotation;
import io.github.mzmine.datamodel.features.types.annotations.CompoundNameType;
import io.github.mzmine.gui.DesktopService;
import io.github.mzmine.gui.MZmineGUI;
import io.github.mzmine.javafx.concurrent.threading.FxThread;
import io.github.mzmine.javafx.dialogs.DialogLoggerUtil;
import io.github.mzmine.javafx.mvci.FxController;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.javafx.util.FxFileChooser;
import io.github.mzmine.javafx.util.FxFileChooser.FileSelectionType;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.batchmode.BatchModeModule;
import io.github.mzmine.modules.batchmode.BatchModeParameters;
import io.github.mzmine.modules.batchmode.BatchQueue;
import io.github.mzmine.modules.batchmode.BatchTask;
import io.github.mzmine.modules.tools.batchwizard.BatchWizardTab;
import io.github.mzmine.modules.tools.batchwizard.WizardPart;
import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.modules.tools.batchwizard.subparameters.WizardStepParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.WorkflowWizardParameterFactory;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.FeatureRecord;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.LcMsOptimizationProblem;
import io.github.mzmine.project.ProjectService;
import io.github.mzmine.taskcontrol.AllTasksFinishedListener;
import io.github.mzmine.taskcontrol.TaskService;
import io.github.mzmine.util.ExitCode;
import io.github.mzmine.util.FeatureListRowSorter;
import io.github.mzmine.util.FeatureListUtils;
import io.github.mzmine.util.files.ExtensionFilters;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.moeaframework.core.population.NondominatedPopulation;
import org.moeaframework.util.format.TableFormat;

public class OptimizationResultsController extends FxController<OptimizationResultModel> {

  private final BatchWizardTab wizardTab;
  private final LcMsOptimizationProblem optimization;
  @Nullable
  private final Stage stage;

  public OptimizationResultsController(@NotNull BatchWizardTab wizardTab,
      @NotNull LcMsOptimizationProblem optimization, final NondominatedPopulation result,
      @Nullable final Stage stage) {
    super(new OptimizationResultModel());
    this.wizardTab = wizardTab;
    this.optimization = optimization;
    this.stage = stage;
    model.resultProperty().set(result);
  }

  @Override
  protected @NotNull FxViewBuilder<OptimizationResultModel> getViewBuilder() {
    return new OptimizationResultsViewBuilder(this.model, this::applyToWizardSequence,
        this::openInBatch, this::exportSolutions, this::runBatchFilterResults, stage);
  }

  public void applyToWizardSequence() {

    final ButtonType choice = ((MZmineGUI) DesktopService.getDesktop()).displayConfirmation(
        "Warning", """
            Setting the parameters to the wizard does not cover all parameters as some modules are optimised specifically.
            Continue any way?""", ButtonType.YES, ButtonType.NO);
    if (choice != ButtonType.YES) {
      return;
    }

    final WizardSequence sequence = optimization.createWizardSequenceFromSolution(
        model.getSelectedSolution());

    sequence.get(WizardPart.DATA_IMPORT).ifPresent(sequence::remove);
    wizardTab.getTabPane().getSelectionModel().select(wizardTab);
    wizardTab.applyPartialSequence(sequence);
  }

  public void openInBatch() {
    final BatchQueue q = createOptimizedBatch();
    if (q == null) {
      return;
    }

    BatchModeParameters batchModeParameters = (BatchModeParameters) MZmineCore.getConfiguration()
        .getModuleParameters(BatchModeModule.class);
    batchModeParameters.getParameter(BatchModeParameters.batchQueue).setValue(q);

    if (batchModeParameters.showSetupDialog(false) == ExitCode.OK) {
      MZmineCore.runMZmineModule(BatchModeModule.class, batchModeParameters.cloneParameterSet());
    }
  }

  private @Nullable BatchQueue createOptimizedBatch() {
    final WizardSequence sequence = optimization.createWizardSequenceFromSolution(
        model.getSelectedSolution());

    sequence.get(WizardPart.DATA_IMPORT).ifPresent(sequence::remove);
    wizardTab.getTabPane().getSelectionModel().select(wizardTab);
    wizardTab.applyPartialSequence(sequence);

    final WizardSequence sequenceSteps = wizardTab.getSequence();

    final Optional<WizardStepParameters> workflow = sequenceSteps.get(WORKFLOW);
    if (workflow.isEmpty()) {
      DialogLoggerUtil.showErrorDialog("Cannot create batch",
          "A workflow must be selected to create a batch.");
      return null;
    }

    BatchQueue q;
    try {
      q = ((WorkflowWizardParameterFactory) workflow.get().getFactory()).getBatchBuilder(
          sequenceSteps).createQueue();
    } catch (Exception e) {
      DialogLoggerUtil.showErrorDialog("Cannot create batch", e.getMessage());
      q = null;
    }
    return q;
  }

  private void runBatchFilterResults() {
    final BatchQueue q = createOptimizedBatch();
    BatchModeParameters batchModeParameters = (BatchModeParameters) MZmineCore.getConfiguration()
        .getModuleParameters(BatchModeModule.class);
    batchModeParameters.getParameter(BatchModeParameters.batchQueue).setValue(q);

    final BatchTask batchTask = new BatchTask(ProjectService.getProject(), batchModeParameters,
        Instant.now(), null);
    TaskService.getController().addTask(batchTask);
    new AllTasksFinishedListener(List.of(batchTask), l -> {
      final List<FeatureList> latestFlists = batchTask.getLatestCreatedFeatureLists();
      if (latestFlists.size() != 1) {
        throw new IllegalStateException(
            "More or less than 1 feature list as final result. Cannot annotate.");
      }
      final FeatureList flist = latestFlists.getFirst();
      final List<FeatureListRow> mzSortedRows = flist.stream()
          .sorted(FeatureListRowSorter.MZ_ASCENDING).toList();

      if (optimization.getFileOnlyBenchmarkFeatures() != null) {
        for (FeatureRecord t : optimization.getFileOnlyBenchmarkFeatures()) {
          final FeatureListRow bestMatch = t.getBestMatch(mzSortedRows);
          if (bestMatch == null) {
            continue;
          }
          final SimpleCompoundDBAnnotation a = new SimpleCompoundDBAnnotation();
          a.put(CompoundNameType.class, "target feature");
          bestMatch.addCompoundAnnotation(a);
        }
      }

      if (optimization.getAllTargets() != null) {
        for (FeatureRecord target : optimization.getAllTargets()) {
          final FeatureListRow bestMatch = target.getBestMatch(mzSortedRows);
          if (bestMatch == null) {
            continue;
          }
          final SimpleCompoundDBAnnotation a = new SimpleCompoundDBAnnotation();
          a.put(CompoundNameType.class, "benchmark feature");
          bestMatch.addCompoundAnnotation(a);
        }
      }
      final List<FeatureListRow> annotated = flist.stream().filter(FeatureListRow::isIdentified)
          .toList();
      final ModularFeatureList copy = FeatureListUtils.createCopy(flist, null, " target", null,
          false, flist.getRawDataFiles(), false, annotated.size(),
          annotated.stream().mapToInt(FeatureListRow::getNumberOfFeatures).sum());
      annotated.forEach(copy::addRow);
      FxThread.runLater(() -> ProjectService.getProject().addFeatureList(copy));
    });
  }

  private void exportSolutions() {

    final NondominatedPopulation result = model.getResult();
    if (result == null) {
      return;
    }

    FxThread.runLater(() -> {
      final File file = FxFileChooser.openSelectDialog(FileSelectionType.SAVE,
          List.of(ExtensionFilters.CSV), null, "Export solutions");
      if (file == null) {
        return;
      }
      try {
        result.asTabularData().save(TableFormat.CSV, file);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

  }
}
