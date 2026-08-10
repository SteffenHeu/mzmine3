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

package io.github.mzmine.modules.dataprocessing.featdet_masscalibration2;

import static io.github.mzmine.javafx.components.factories.FxTexts.boldText;
import static io.github.mzmine.javafx.components.factories.FxTexts.hyperlinkText;
import static io.github.mzmine.javafx.components.factories.FxTexts.linebreak;
import static io.github.mzmine.javafx.components.factories.FxTexts.text;

import io.github.mzmine.javafx.components.factories.FxTextFlows;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.gui.MassCalibrationPreviewPane;
import io.github.mzmine.parameters.dialogs.ParameterDialogWithPreviewPanes;
import io.github.mzmine.parameters.dialogs.ParameterSetupDialog;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesParameter;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelectionParameter;
import io.github.mzmine.parameters.parametertypes.submodules.ModuleOptionsEnumComboParameter;
import io.github.mzmine.util.ExitCode;
import javafx.application.Platform;
import javafx.scene.layout.Region;
import org.jetbrains.annotations.Nullable;

public class MassCalibrationParameters extends SimpleParameterSet {

  public static final RawDataFilesParameter dataFiles = new RawDataFilesParameter();

  public static final ScanSelectionParameter scanSelection = new ScanSelectionParameter(
      "Scans to recalibrate",
      "Scans whose mass lists will be recalibrated. Defaults to all scans (MS1 and MSn); MSn scans "
          + "are corrected using the calibration at the closest available retention time.",
      ScanSelection.ALL_SCANS);

  public static final ModuleOptionsEnumComboParameter<MzCalibrationMethods> calibrationMethod = new ModuleOptionsEnumComboParameter<>(
      "Calibration method", "The mass-calibration strategy and its parameters.",
      MzCalibrationMethods.LOCKMASS);

  public MassCalibrationParameters() {
    super(dataFiles, scanSelection, calibrationMethod);
  }

  @Override
  public ExitCode showSetupDialog(boolean valueCheckRequired) {
    assert Platform.isFxApplicationThread();
    if (parameters == null || parameters.length == 0) {
      return ExitCode.OK;
    }
    final ParameterSetupDialog dialog = new ParameterDialogWithPreviewPanes(valueCheckRequired,
        this, getMessage(), MassCalibrationPreviewPane::new, true);
    dialog.showAndWait();
    return dialog.getExitCode();
  }

  @Override
  public @Nullable Region getMessage() {
    return FxTextFlows.newTextFlowInAccordion("Citation", text("When using the calibrant list "),
        boldText("Agilent Tune mix"), text(" please cite "),
        hyperlinkText("Stow et al.", "https://pubs.acs.org/doi/abs/10.1021/acs.analchem.7b01729"),
        text("."), linebreak(), text("When using the contaminant calibration with the "),
        boldText("Universal calibrants"), text(" list, please cite "),
        hyperlinkText("Keller et. al.", "https://doi.org/10.1016/j.aca.2008.04.043"),
        text(" and/or "), hyperlinkText("Hawkes et. al.", " https://doi.org/10.1002/lom3.10364"),
        text("."));
  }
}
