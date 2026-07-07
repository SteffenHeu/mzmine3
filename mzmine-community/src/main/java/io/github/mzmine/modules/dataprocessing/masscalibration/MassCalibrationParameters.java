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

package io.github.mzmine.modules.dataprocessing.masscalibration;

import io.github.mzmine.modules.dataprocessing.masscalibration.gui.MassCalibrationPreviewPane;
import io.github.mzmine.parameters.dialogs.ParameterDialogWithPreviewPanes;
import io.github.mzmine.parameters.dialogs.ParameterSetupDialog;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesParameter;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelectionParameter;
import io.github.mzmine.parameters.parametertypes.submodules.ModuleOptionsEnumComboParameter;
import io.github.mzmine.util.ExitCode;
import javafx.application.Platform;

public class MassCalibrationParameters extends SimpleParameterSet {

  public static final RawDataFilesParameter dataFiles = new RawDataFilesParameter();

  public static final ScanSelectionParameter scanSelection = new ScanSelectionParameter(
      "Scans to recalibrate",
      "Scans whose mass lists will be recalibrated. Defaults to all scans (MS1 and MSn); MSn scans "
          + "are corrected using the calibration at the closest available retention time.",
      ScanSelection.ALL_SCANS);

  public static final ModuleOptionsEnumComboParameter<MzCalibrationMethods> calibrationMethod =
      new ModuleOptionsEnumComboParameter<>("Calibration method",
          "The mass-calibration strategy and its parameters.", MzCalibrationMethods.LOCKMASS);

  public MassCalibrationParameters() {
    super(dataFiles, scanSelection, calibrationMethod);
  }

  @Override
  public ExitCode showSetupDialog(boolean valueCheckRequired) {
    assert Platform.isFxApplicationThread();
    if (parameters == null || parameters.length == 0) {
      return ExitCode.OK;
    }
    final ParameterSetupDialog dialog = new ParameterDialogWithPreviewPanes(valueCheckRequired, this,
        MassCalibrationPreviewPane::new);
    dialog.showAndWait();
    return dialog.getExitCode();
  }
}
