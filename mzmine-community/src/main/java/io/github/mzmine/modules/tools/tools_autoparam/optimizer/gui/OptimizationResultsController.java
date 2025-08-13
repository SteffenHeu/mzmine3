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

package io.github.mzmine.modules.tools.tools_autoparam.optimizer.gui;

import static io.github.mzmine.modules.tools.batchwizard.WizardPart.WORKFLOW;

import io.github.mzmine.gui.DesktopService;
import io.github.mzmine.gui.MZmineGUI;
import io.github.mzmine.javafx.components.factories.FxTexts;
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
import io.github.mzmine.modules.tools.batchwizard.BatchWizardTab;
import io.github.mzmine.modules.tools.batchwizard.WizardPart;
import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.modules.tools.batchwizard.subparameters.WizardStepParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.WorkflowWizardParameterFactory;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.LcMsOptimizationProblem;
import io.github.mzmine.util.ExitCode;
import io.github.mzmine.util.files.ExtensionFilters;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.moeaframework.core.Solution;
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
    return new OptimizationResultsViewBuilder(this.model, this::applyToWizardSequence, this::openInBatch, this::exportSolutions, stage);
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
      return;
    }

    BatchModeParameters batchModeParameters = (BatchModeParameters) MZmineCore.getConfiguration()
        .getModuleParameters(BatchModeModule.class);
    try {
      final BatchQueue q = ((WorkflowWizardParameterFactory) workflow.get()
          .getFactory()).getBatchBuilder(sequenceSteps).createQueue();

      optimization.applySolutionParametersToBatch(model.getSelectedSolution(), q);

      batchModeParameters.getParameter(BatchModeParameters.batchQueue).setValue(q);

      if (batchModeParameters.showSetupDialog(false) == ExitCode.OK) {
        MZmineCore.runMZmineModule(BatchModeModule.class, batchModeParameters.cloneParameterSet());
      }
    } catch (Exception e) {
      DialogLoggerUtil.showErrorDialog("Cannot create batch", e.getMessage());
    }
  }

  private void exportSolutions() {

    final NondominatedPopulation result = model.getResult();
    if (result == null) {
      return;
    }

    FxThread.runLater(() -> {
      final File file = FxFileChooser.openSelectDialog(FileSelectionType.SAVE,
          List.of(ExtensionFilters.CSV), null, "Export solutions");
      if(file == null) {
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
