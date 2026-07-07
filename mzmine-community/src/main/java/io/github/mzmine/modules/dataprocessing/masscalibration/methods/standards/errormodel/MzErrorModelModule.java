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

import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration.charts.ArithmeticMeanKnnTrend;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration.errormodeling.BiasEstimator;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration.errormodeling.DistributionExtractor;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration.errormodeling.DistributionRange;
import io.github.mzmine.modules.dataprocessing.masscalibration.api.PolynomialMzErrorFit;
import io.github.mzmine.parameters.ParameterSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.jetbrains.annotations.Nullable;
import org.jfree.data.xy.XYSeries;

/**
 * An algorithm that fits an internal-standards absolute-m/z-error model (Da) from matched
 * (measured m/z, error) pairs. Each implementation is a stateless {@link MZmineModule} selected
 * through {@link MzErrorModels} (a {@code ModuleOptionsEnum}), carrying only its own parameters.
 * <p>
 * The concrete fitting is provided as {@code static} helpers here so the composite
 * {@link AutoErrorModelModule} can reuse the individual models.
 */
public interface MzErrorModelModule extends MZmineModule {

  /**
   * Fit the model from matched calibrant points.
   *
   * @param params  the embedded parameters of the selected option (may be {@code null} for options
   *                without parameters)
   * @param mz      measured m/z of the matched calibrants
   * @param deltaMz absolute m/z errors (Da), parallel to {@code mz}
   * @return the fitted model
   */
  MzErrorModel fit(@Nullable ParameterSet params, double[] mz, double[] deltaMz);

  /**
   * Constant offset: the arithmetic mean of the interquartile (25–75 %) range of the absolute
   * errors (robust to outliers), reusing {@link DistributionExtractor} + {@link BiasEstimator}.
   */
  static MzErrorModel meanModel(double[] deltaMz) {
    final List<Double> errors = new ArrayList<>(deltaMz.length);
    for (double d : deltaMz) {
      errors.add(d);
    }
    Collections.sort(errors); // interpercentileRange expects ascending input
    final DistributionRange range = DistributionExtractor.interpercentileRange(errors, 25, 75);
    final double bias = BiasEstimator.arithmeticMean(range.getExtractedItems());
    return new MzErrorModel(mz -> bias, "mean offset %.5f m/z".formatted(bias));
  }

  /**
   * K-nearest-neighbor regression of error vs. m/z via {@link ArithmeticMeanKnnTrend}.
   *
   * @param neighborFraction fraction (0–1) of points used as neighbors
   */
  static MzErrorModel knnModel(double[] mz, double[] deltaMz, double neighborFraction) {
    final ArithmeticMeanKnnTrend trend = new ArithmeticMeanKnnTrend(neighborFraction);
    final XYSeries series = new XYSeries("standards", false, true);
    for (int i = 0; i < mz.length; i++) {
      series.add(mz[i], deltaMz[i]);
    }
    trend.setDataset(series);
    final DoubleUnaryOperator op = trend::getValue;
    return new MzErrorModel(op, "KNN (SSR %.3g)".formatted(ssr(op, mz, deltaMz)));
  }

  /**
   * Polynomial (OLS) fit of error vs. m/z.
   *
   * @param degreeOrAuto a fixed polynomial degree, or {@link PolynomialMzErrorFit#AUTO_DEGREE} to
   *                     iterate degrees and keep the one with the lowest residual
   */
  static MzErrorModel olsModel(double[] mz, double[] deltaMz, int degreeOrAuto) {
    final int degree = degreeOrAuto == PolynomialMzErrorFit.AUTO_DEGREE
        ? PolynomialMzErrorFit.chooseDegreeByLowestResidual(mz, deltaMz,
            PolynomialMzErrorFit.MAX_UI_DEGREE) : degreeOrAuto;
    final PolynomialFunction poly = PolynomialMzErrorFit.fit(mz, deltaMz, degree);
    final DoubleUnaryOperator op = poly::value;
    return new MzErrorModel(op, "OLS degree %d (SSR %.3g)".formatted(degree, ssr(op, mz, deltaMz)));
  }

  /**
   * Sum of squared residuals between the observed errors and a model.
   */
  static double ssr(DoubleUnaryOperator model, double[] mz, double[] deltaMz) {
    double ssr = 0;
    for (int i = 0; i < mz.length; i++) {
      final double r = deltaMz[i] - model.applyAsDouble(mz[i]);
      ssr += r * r;
    }
    return ssr;
  }
}
