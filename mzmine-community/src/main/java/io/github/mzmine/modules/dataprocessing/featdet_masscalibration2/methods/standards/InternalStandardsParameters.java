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

package io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.methods.standards;

import io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.api.CalibrantListSource;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.methods.standards.errormodel.MzErrorModels;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.DoubleParameter;
import io.github.mzmine.parameters.parametertypes.combowithinput.ComboWithFileInputParameter;
import io.github.mzmine.parameters.parametertypes.combowithinput.ComboWithFileInputValue;
import io.github.mzmine.parameters.parametertypes.filenames.FileNameParameter;
import io.github.mzmine.parameters.parametertypes.filenames.FileSelectionType;
import io.github.mzmine.parameters.parametertypes.submodules.ModuleOptionsEnumComboParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.MZToleranceParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance.Unit;
import io.github.mzmine.parameters.parametertypes.tolerances.RTToleranceParameter;
import java.text.NumberFormat;

public class InternalStandardsParameters extends SimpleParameterSet {

  public static final ComboWithFileInputParameter<CalibrantListSource> standardsList =
      new ComboWithFileInputParameter<>(
          new FileNameParameter("Custom standards list",
              "CSV/TSV file of internal standards / contaminants with an \"mz\" column "
                  + "(optional \"rt\").", FileSelectionType.OPEN),
          CalibrantListSource.internalStandardOptions(), CalibrantListSource.CUSTOM_FILE,
          new ComboWithFileInputValue<>(CalibrantListSource.UNIVERSAL_MERGED_POSITIVE, null));

  public static final MZToleranceParameter mzTolerance = new MZToleranceParameter("m/z tolerance",
      "Matching tolerance between standard m/z and measured peaks.", 0.001, 5);

  public static final RTToleranceParameter rtTolerance = new RTToleranceParameter("RT tolerance",
      "Maximum retention time difference between a standard and a measured peak.",
      new RTTolerance(0.2f, Unit.MINUTES));

  public static final DoubleParameter minIntensity = new DoubleParameter("Minimum intensity",
      "Candidate peaks below this intensity are ignored.", NumberFormat.getNumberInstance(), 0.0,
      0.0, null);

  public static final ModuleOptionsEnumComboParameter<MzErrorModels> errorModel =
      new ModuleOptionsEnumComboParameter<>("Calibration method",
          "How the mass-error trend is modeled. Each option shows only its own parameters; \"Auto\" "
              + "tries all three and keeps the lowest-residual fit.", MzErrorModels.ARITHMETIC_MEAN);

  public InternalStandardsParameters() {
    super(standardsList, mzTolerance, rtTolerance, minIntensity, errorModel);
  }
}
