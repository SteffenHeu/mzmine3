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

package io.github.mzmine.modules.dataprocessing.masscalibration.methods.standards.errormodel;

import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.submodules.ModuleOptionsEnum;
import org.jetbrains.annotations.Nullable;

/**
 * The selectable error-model algorithms for the internal-standards calibration method, used through a
 * {@link io.github.mzmine.parameters.parametertypes.submodules.ModuleOptionsEnumComboParameter} so
 * each option shows only its own parameters (mean: none, KNN: neighbor %, OLS: degree, auto: both).
 */
public enum MzErrorModels implements ModuleOptionsEnum<MzErrorModelModule> {

  ARITHMETIC_MEAN, KNN, OLS, AUTO;

  /**
   * Fit the selected model with its embedded parameters.
   */
  public MzErrorModel fit(@Nullable ParameterSet params, double[] mz, double[] deltaMz) {
    return getModuleInstance().fit(params, mz, deltaMz);
  }

  @Override
  public Class<? extends MzErrorModelModule> getModuleClass() {
    return switch (this) {
      case ARITHMETIC_MEAN -> MeanErrorModelModule.class;
      case KNN -> KnnErrorModelModule.class;
      case OLS -> OlsErrorModelModule.class;
      case AUTO -> AutoErrorModelModule.class;
    };
  }

  @Override
  public String getStableId() {
    return switch (this) {
      case ARITHMETIC_MEAN -> "arithmetic_mean";
      case KNN -> "knn_regression";
      case OLS -> "ols_regression";
      case AUTO -> "auto";
    };
  }

  @Override
  public String toString() {
    return switch (this) {
      case ARITHMETIC_MEAN -> "Arithmetic mean";
      case KNN -> "KNN regression";
      case OLS -> "OLS regression";
      case AUTO -> "Auto (lowest residual)";
    };
  }
}
