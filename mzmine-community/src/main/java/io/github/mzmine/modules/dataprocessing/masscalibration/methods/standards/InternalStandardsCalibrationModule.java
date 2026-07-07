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

package io.github.mzmine.modules.dataprocessing.masscalibration.methods.standards;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.ScanDataType;
import io.github.mzmine.datamodel.data_access.ScanDataAccess;
import io.github.mzmine.gui.chartbasics.simplechart.providers.PlotXYDataProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.impl.AnyXYProvider;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration.charts.ArithmeticMeanKnnTrend;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration.errormodeling.BiasEstimator;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration.errormodeling.DistributionExtractor;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration.errormodeling.DistributionRange;
import io.github.mzmine.modules.dataprocessing.masscalibration.api.CalibrantList;
import io.github.mzmine.modules.dataprocessing.masscalibration.api.CalibrantListSource;
import io.github.mzmine.modules.dataprocessing.masscalibration.api.CalibrantMatchMatrix;
import io.github.mzmine.modules.dataprocessing.masscalibration.api.MzCalibrationFunction;
import io.github.mzmine.modules.dataprocessing.masscalibration.api.MzCalibrationMethod;
import io.github.mzmine.modules.dataprocessing.masscalibration.api.PolynomialMzErrorFit;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.combowithinput.ComboWithFileInputValue;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.util.MemoryMapStorage;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.logging.Logger;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.jfree.data.xy.XYSeries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Internal-standards / contaminant recalibration. Calibrant ions occur at different retention times
 * in different spectra; they are matched against a calibrant list, a single absolute-m/z-error model
 * (Da) is estimated for the file, and that model is applied to every spectrum.
 * <p>
 * Self-contained (no {@code MassCalibrator}): matching runs directly over {@link ScanDataAccess};
 * the OLS fit uses commons-math3; KNN reuses the standalone {@link ArithmeticMeanKnnTrend}; the
 * arithmetic-mean bias reuses {@link DistributionExtractor} (interquartile trim) +
 * {@link BiasEstimator}. AUTO fits all three and keeps the lowest residual.
 */
public class InternalStandardsCalibrationModule implements MzCalibrationMethod {

  private static final Logger logger = Logger.getLogger(
      InternalStandardsCalibrationModule.class.getName());

  private final ParameterSet parameters;
  private final CalibrantListSource standardsSource;
  private final File customStandardsFile;
  private final MZTolerance mzTolerance;
  private final RTTolerance rtTolerance;
  private final double minIntensity;
  private final InternalStandardsBiasMethod biasMethod;
  private final double knnPercentage;
  private final int olsDegree;
  private final List<PlotXYDataProvider> additionalData = new ArrayList<>();

  public InternalStandardsCalibrationModule() {
    this.parameters = null;
    this.standardsSource = null;
    this.customStandardsFile = null;
    this.mzTolerance = null;
    this.rtTolerance = null;
    this.minIntensity = 0;
    this.biasMethod = InternalStandardsBiasMethod.ARITHMETIC_MEAN;
    this.knnPercentage = 10;
    this.olsDegree = 1;
  }

  private InternalStandardsCalibrationModule(ParameterSet parameters) {
    this.parameters = parameters;
    final ComboWithFileInputValue<CalibrantListSource> standards = parameters.getValue(
        InternalStandardsParameters.standardsList);
    this.standardsSource = standards.getSelectedOption();
    this.customStandardsFile = standards.getEmbeddedValue();
    this.mzTolerance = parameters.getValue(InternalStandardsParameters.mzTolerance);
    this.rtTolerance = parameters.getValue(InternalStandardsParameters.rtTolerance);
    this.minIntensity = parameters.getValue(InternalStandardsParameters.minIntensity);
    this.biasMethod = parameters.getValue(InternalStandardsParameters.biasMethod);
    this.knnPercentage = parameters.getValue(InternalStandardsParameters.nearestNeighborsPercentage);
    this.olsDegree = parameters.getValue(InternalStandardsParameters.polynomialDegree);
  }

  @Override
  public MzCalibrationMethod newInstance(@NotNull ParameterSet parameters,
      @Nullable MemoryMapStorage storage) {
    return new InternalStandardsCalibrationModule(parameters);
  }

  @Override
  public @NotNull String getName() {
    return "Internal standards calibration";
  }

  @Override
  public @Nullable Class<? extends ParameterSet> getParameterSetClass() {
    return InternalStandardsParameters.class;
  }

  @Override
  public List<PlotXYDataProvider> getAdditionalPreviewData() {
    return additionalData;
  }

  @Override
  public @Nullable MzCalibrationFunction buildCalibration(@NotNull RawDataFile file) {
    additionalData.clear();
    if (standardsSource == null) {
      logger.warning("No standards list configured.");
      return null;
    }

    final CalibrantList calibrants = CalibrantList.fromSource(standardsSource, customStandardsFile);
    if (calibrants == null) {
      return null;
    }
    if (calibrants.isEmpty()) {
      logger.warning("Standards list is empty.");
      return null;
    }

    // collect (measured m/z, absolute error Da) matches over all MS1 scans (mz + rt matching);
    // for each scan the matcher keeps the highest-intensity peak per calibrant
    final DoubleArrayList mzList = new DoubleArrayList();
    final DoubleArrayList deltaList = new DoubleArrayList();
    final ScanDataAccess data = EfficientDataAccess.of(file, ScanDataType.MASS_LIST,
        ScanSelection.MS1);
    final CalibrantMatchMatrix matchMatrix = new CalibrantMatchMatrix(calibrants);
    while (data.hasNextScan()) {
      final Scan scan = data.nextScan();
      if (scan == null) {
        continue;
      }
      matchMatrix.checkMatches(data, minIntensity, scan.getRetentionTime(), mzTolerance,
          rtTolerance);
      matchMatrix.addMatches(mzList, deltaList);
    }

    if (mzList.isEmpty()) {
      logger.warning("No standard matches found in " + file.getName());
      return null;
    }
    final double[] mz = mzList.toDoubleArray();
    final double[] delta = deltaList.toDoubleArray();

    double minMz = Double.POSITIVE_INFINITY;
    double maxMz = Double.NEGATIVE_INFINITY;
    for (double m : mz) {
      minMz = Math.min(minMz, m);
      maxMz = Math.max(maxMz, m);
    }

    final MzCalibrationFunction function = switch (biasMethod) {
      case ARITHMETIC_MEAN -> meanFunction(delta, minMz, maxMz);
      case KNN_REGRESSION -> knnFunction(mz, delta, minMz, maxMz);
      case OLS_REGRESSION -> olsFunction(mz, delta, minMz, maxMz);
      case AUTO -> auto(mz, delta, minMz, maxMz);
    };

    buildPreview(mz, delta, function);
    return function;
  }

  // --- individual models -----------------------------------------------------------------------

  private InternalStandardsCalibrationFunction meanFunction(double[] delta, double minMz,
      double maxMz) {
    final double bias = meanBias(delta);
    return new InternalStandardsCalibrationFunction(mz -> bias, minMz, maxMz,
        "Internal standards (mean offset %.5f m/z)".formatted(bias));
  }

  private InternalStandardsCalibrationFunction knnFunction(double[] mz, double[] delta, double minMz,
      double maxMz) {
    final ArithmeticMeanKnnTrend knn = knnTrend(mz, delta);
    return new InternalStandardsCalibrationFunction(knn::getValue, minMz, maxMz,
        "Internal standards (KNN, SSR %.3g)".formatted(ssr(knn::getValue, mz, delta)));
  }

  private InternalStandardsCalibrationFunction olsFunction(double[] mz, double[] delta, double minMz,
      double maxMz) {
    final PolynomialFunction poly = PolynomialMzErrorFit.fit(mz, delta, olsDegree);
    return new InternalStandardsCalibrationFunction(poly::value, minMz, maxMz,
        "Internal standards (OLS degree %d, SSR %.3g)".formatted(olsDegree, ssr(poly::value, mz, delta)));
  }

  private InternalStandardsCalibrationFunction auto(double[] mz, double[] delta, double minMz,
      double maxMz) {
    final double bias = meanBias(delta);
    final double meanSsr = ssr(x -> bias, mz, delta);

    final ArithmeticMeanKnnTrend knn = knnTrend(mz, delta);
    final double knnSsr = ssr(knn::getValue, mz, delta);

    final PolynomialFunction poly = PolynomialMzErrorFit.fit(mz, delta, olsDegree);
    final double olsSsr = ssr(poly::value, mz, delta);

    logger.info("AUTO residuals (m/z^2) — mean: %.4g, knn: %.4g, ols: %.4g".formatted(meanSsr,
        knnSsr, olsSsr));

    if (meanSsr <= knnSsr && meanSsr <= olsSsr) {
      return new InternalStandardsCalibrationFunction(x -> bias, minMz, maxMz,
          "Internal standards AUTO -> mean offset (%.5f m/z)".formatted(bias));
    } else if (knnSsr <= olsSsr) {
      return new InternalStandardsCalibrationFunction(knn::getValue, minMz, maxMz,
          "Internal standards AUTO -> KNN (SSR %.3g)".formatted(knnSsr));
    } else {
      return new InternalStandardsCalibrationFunction(poly::value, minMz, maxMz,
          "Internal standards AUTO -> OLS degree %d (SSR %.3g)".formatted(olsDegree, olsSsr));
    }
  }

  // --- helpers ---------------------------------------------------------------------------------

  /**
   * Robust constant offset: the arithmetic mean of the interquartile (25–75 %) range of the absolute
   * errors, reusing {@link DistributionExtractor} + {@link BiasEstimator}.
   */
  private static double meanBias(double[] delta) {
    final List<Double> errors = new ArrayList<>(delta.length);
    for (double d : delta) {
      errors.add(d);
    }
    Collections.sort(errors); // interpercentileRange expects ascending input
    final DistributionRange range = DistributionExtractor.interpercentileRange(errors, 25, 75);
    return BiasEstimator.arithmeticMean(range.getExtractedItems());
  }

  private ArithmeticMeanKnnTrend knnTrend(double[] mz, double[] delta) {
    final ArithmeticMeanKnnTrend trend = new ArithmeticMeanKnnTrend(knnPercentage / 100.0);
    final XYSeries series = new XYSeries("standards", false, true);
    for (int i = 0; i < mz.length; i++) {
      series.add(mz[i], delta[i]);
    }
    trend.setDataset(series);
    return trend;
  }

  private static double ssr(DoubleUnaryOperator model, double[] mz, double[] delta) {
    double ssr = 0;
    for (int i = 0; i < mz.length; i++) {
      final double r = delta[i] - model.applyAsDouble(mz[i]);
      ssr += r * r;
    }
    return ssr;
  }

  private void buildPreview(double[] mz, double[] delta, MzCalibrationFunction function) {
    additionalData.add(new AnyXYProvider(java.awt.Color.GRAY, "standard error (m/z)", mz.length,
        i -> mz[i], i -> delta[i]));

    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    for (double m : mz) {
      min = Math.min(min, m);
      max = Math.max(max, m);
    }
    final double lo = min;
    final double hi = max;
    final int steps = 200;
    // fitted model as absolute error Δ(mz) = measured - calibrated (RT-independent)
    additionalData.add(new AnyXYProvider(java.awt.Color.RED, "fit (m/z)", steps, i -> {
      final double x = lo + (hi - lo) * i / (steps - 1);
      return x;
    }, i -> {
      final double x = lo + (hi - lo) * i / (steps - 1);
      return x - function.getCalibratedMz(x, 0f);
    }));
  }
}
