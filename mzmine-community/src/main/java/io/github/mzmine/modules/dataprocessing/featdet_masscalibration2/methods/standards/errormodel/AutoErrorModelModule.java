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

package io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.methods.standards.errormodel;

import io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.api.PolynomialMzErrorFit;
import io.github.mzmine.parameters.ParameterSet;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Error model: fits the arithmetic-mean, KNN and OLS candidates and keeps the one with the lowest
 * residual (SSR). The OLS candidate honors its own "Auto" degree option.
 */
public class AutoErrorModelModule implements MzErrorModelModule {

  private static final Logger logger = Logger.getLogger(AutoErrorModelModule.class.getName());

  public AutoErrorModelModule() {
  }

  @Override
  public MzErrorModel fit(@Nullable ParameterSet params, double[] mz, double[] deltaMz) {
    final double neighborFraction =
        params.getValue(AutoErrorModelParameters.nearestNeighborsPercentage) / 100.0;
    final int degree = PolynomialMzErrorFit.parseDegree(
        params.getValue(AutoErrorModelParameters.polynomialDegree));

    final MzErrorModel mean = MzErrorModelModule.meanModel(deltaMz);
    final MzErrorModel knn = MzErrorModelModule.knnModel(mz, deltaMz, neighborFraction);
    final MzErrorModel ols = MzErrorModelModule.olsModel(mz, deltaMz, degree);

    final double meanSsr = MzErrorModelModule.ssr(mean.deltaMz(), mz, deltaMz);
    final double knnSsr = MzErrorModelModule.ssr(knn.deltaMz(), mz, deltaMz);
    final double olsSsr = MzErrorModelModule.ssr(ols.deltaMz(), mz, deltaMz);
    logger.info("AUTO residuals (m/z^2) — mean: %.4g, knn: %.4g, ols: %.4g".formatted(meanSsr,
        knnSsr, olsSsr));

    final MzErrorModel best;
    if (meanSsr <= knnSsr && meanSsr <= olsSsr) {
      best = mean;
    } else if (knnSsr <= olsSsr) {
      best = knn;
    } else {
      best = ols;
    }
    return new MzErrorModel(best.deltaMz(), "auto -> " + best.description());
  }

  @Override
  public @NotNull String getName() {
    return "Auto (lowest residual)";
  }

  @Override
  public @Nullable Class<? extends ParameterSet> getParameterSetClass() {
    return AutoErrorModelParameters.class;
  }
}
