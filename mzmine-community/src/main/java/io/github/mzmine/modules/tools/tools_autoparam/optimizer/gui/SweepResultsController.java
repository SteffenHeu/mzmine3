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

import io.github.mzmine.javafx.dialogs.DialogLoggerUtil;
import io.github.mzmine.javafx.mvci.FxController;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.modules.tools.batchwizard.BatchWizardTab;
import io.github.mzmine.modules.tools.batchwizard.WizardPart;
import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.ParameterSweepRunner;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.SweepMetricResult;
import java.util.List;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SweepResultsController extends FxController<SweepResultsModel> {

  private final BatchWizardTab wizardTab;
  private final ParameterSweepRunner runner;
  @Nullable
  private final Stage stage;

  public SweepResultsController(@NotNull BatchWizardTab wizardTab,
      @NotNull ParameterSweepRunner runner, @NotNull List<SweepMetricResult> results,
      @Nullable Stage stage) {
    super(new SweepResultsModel());
    this.wizardTab = wizardTab;
    this.runner = runner;
    this.stage = stage;
    model.setResults(results);
  }

  @Override
  protected @NotNull FxViewBuilder<SweepResultsModel> getViewBuilder() {
    return new SweepResultsViewBuilder(model, this::applyToWizardSequence, this::synthesizeAndApply,
        stage);
  }

  public void applyToWizardSequence() {
    final SweepMetricResult selected = model.getSelectedResult();
    if (selected == null) {
      return;
    }

    final WizardSequence sequence = runner.createBaseWizardSequence(selected.prototype());
    runner.applyResult(selected.sweepResult(), sequence);

    applySequenceToWizard(sequence);
    DialogLoggerUtil.showMessageDialog("Parameter applied to wizard", false,
        "%s = %s".formatted(selected.parameterName(),
            ParameterSweepRunner.formatValue(selected.variable())));
  }

  public void synthesizeAndApply() {
    final WizardSequence sequence = runner.synthesizeBestSequence(model.getResults());
    applySequenceToWizard(sequence);
  }

  private void applySequenceToWizard(@NotNull WizardSequence sequence) {
    sequence.get(WizardPart.DATA_IMPORT).ifPresent(sequence::remove);
    wizardTab.getTabPane().getSelectionModel().select(wizardTab);
    wizardTab.applyPartialSequence(sequence);
  }
}
