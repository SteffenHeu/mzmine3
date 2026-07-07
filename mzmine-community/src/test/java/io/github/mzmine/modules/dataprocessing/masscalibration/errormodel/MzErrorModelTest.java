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

package io.github.mzmine.modules.dataprocessing.masscalibration.errormodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mzmine.modules.dataprocessing.masscalibration.api.PolynomialMzErrorFit;
import io.github.mzmine.modules.dataprocessing.masscalibration.methods.standards.errormodel.AutoErrorModelModule;
import io.github.mzmine.modules.dataprocessing.masscalibration.methods.standards.errormodel.AutoErrorModelParameters;
import io.github.mzmine.modules.dataprocessing.masscalibration.methods.standards.errormodel.MzErrorModel;
import io.github.mzmine.modules.dataprocessing.masscalibration.methods.standards.errormodel.MzErrorModelModule;
import org.junit.jupiter.api.Test;

class MzErrorModelTest {

  // clean linear absolute error Δ(mz) = 1e-6 * mz
  private static final double[] MZ = {100, 200, 300, 400, 500};
  private static final double[] LINEAR_DELTA = {1e-4, 2e-4, 3e-4, 4e-4, 5e-4};

  @Test
  void meanModelReturnsConstantInterquartileMean() {
    final double[] delta = {0.004, 0.005, 0.006};
    final MzErrorModel model = MzErrorModelModule.meanModel(delta);
    assertEquals(0.005, model.deltaMz().applyAsDouble(123.0), 1e-9);
    assertEquals(0.005, model.deltaMz().applyAsDouble(999.0), 1e-9); // constant regardless of m/z
  }

  @Test
  void olsModelFixedDegreeRecoversPolynomial() {
    final MzErrorModel model = MzErrorModelModule.olsModel(MZ, LINEAR_DELTA, 1);
    assertEquals(6e-4, model.deltaMz().applyAsDouble(600.0), 1e-9);
    assertTrue(model.description().contains("degree 1"), model.description());
  }

  @Test
  void olsModelAutoDegreeSelectsAndFits() {
    final MzErrorModel model = MzErrorModelModule.olsModel(MZ, LINEAR_DELTA,
        PolynomialMzErrorFit.AUTO_DEGREE);
    // linear data -> a degree-1 (or higher) fit reproduces it with ~0 residual
    assertEquals(6e-4, model.deltaMz().applyAsDouble(600.0), 1e-8);
    assertEquals(0.0, MzErrorModelModule.ssr(model.deltaMz(), MZ, LINEAR_DELTA), 1e-12);
  }

  @Test
  void knnModelApproximatesConstantError() {
    final double[] delta = {0.003, 0.003, 0.003, 0.003, 0.003};
    final MzErrorModel model = MzErrorModelModule.knnModel(MZ, delta, 0.5);
    assertEquals(0.003, model.deltaMz().applyAsDouble(250.0), 1e-6);
  }

  @Test
  void autoPicksLowestResidualModel() {
    // clean linear data -> AUTO must pick a fitting model (OLS or KNN), never the constant mean
    final MzErrorModel model = new AutoErrorModelModule().fit(new AutoErrorModelParameters(), MZ,
        LINEAR_DELTA);
    final double autoSsr = MzErrorModelModule.ssr(model.deltaMz(), MZ, LINEAR_DELTA);
    final double meanSsr = MzErrorModelModule.ssr(MzErrorModelModule.meanModel(LINEAR_DELTA).deltaMz(),
        MZ, LINEAR_DELTA);
    assertTrue(autoSsr < meanSsr, "AUTO (SSR " + autoSsr + ") should beat the constant-mean model "
        + "(SSR " + meanSsr + "): " + model.description());
    // reproduces an interior point (both OLS and symmetric-KNN are exact at the middle sample)
    assertEquals(3e-4, model.deltaMz().applyAsDouble(300.0), 1e-6);
  }
}
