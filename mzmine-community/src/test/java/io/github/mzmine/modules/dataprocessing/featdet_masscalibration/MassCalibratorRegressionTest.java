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

package io.github.mzmine.modules.dataprocessing.featdet_masscalibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.datamodel.impl.SimpleDataPoint;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration.charts.OLSRegressionTrend;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration.errormodeling.BiasEstimator;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration.errormodeling.DistributionExtractor;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration.errormodeling.DistributionRange;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration.errormodeling.errortypes.PpmError;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration.standardslist.StandardsList;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration.standardslist.StandardsListItem;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import java.util.ArrayList;
import java.util.List;
import org.jfree.data.xy.XYSeries;
import org.junit.jupiter.api.Test;

/**
 * Regression tests that lock down the behavior of the legacy mass-calibration math before it is
 * refactored into the new unified mass-calibration module. All cases operate on plain arrays/lists
 * and do not require a {@code RawDataFile} or {@code MZmineCore} initialization.
 */
class MassCalibratorRegressionTest {

  private static final double EPS = 1e-9;

  // ---------------------------------------------------------------------------------------------
  // PpmError
  // ---------------------------------------------------------------------------------------------

  @Test
  void ppmErrorCalculateAndCalibrateAreInverse() {
    final PpmError ppm = new PpmError();

    // 100.0005 measured against 100.0 actual -> 5 ppm
    assertEquals(5.0, ppm.calculateError(100.0005, 100.0), 1e-6);
    // negative direction
    assertEquals(-5.0, ppm.calculateError(99.9995, 100.0), 1e-6);

    // calibrating a measured value against its own error recovers the actual value
    final double measured = 100.0005;
    final double error = ppm.calculateError(measured, 100.0);
    assertEquals(100.0, ppm.calibrateAgainstError(measured, error), 1e-9);
  }

  // ---------------------------------------------------------------------------------------------
  // BiasEstimator
  // ---------------------------------------------------------------------------------------------

  @Test
  void arithmeticMeanOfEmptyListIsZero() {
    assertEquals(0.0, BiasEstimator.arithmeticMean(List.of()), EPS);
  }

  @Test
  void arithmeticMeanComputesMean() {
    assertEquals(5.0, BiasEstimator.arithmeticMean(List.of(4.0, 5.0, 6.0)), EPS);
    assertEquals(2.5, BiasEstimator.arithmeticMean(List.of(1.0, 2.0, 3.0, 4.0)), EPS);
  }

  // ---------------------------------------------------------------------------------------------
  // DistributionExtractor
  // ---------------------------------------------------------------------------------------------

  @Test
  void wholeRangeKeepsAllItems() {
    // wholeRange does NOT sort; it spans index 0..size-1 and builds the value range from the
    // first and last element, so callers must pass pre-sorted data.
    final List<Double> items = new ArrayList<>(List.of(1.0, 2.0, 3.0));
    final DistributionRange range = DistributionExtractor.wholeRange(items);
    assertEquals(3, range.getExtractedItems().size());
    assertEquals(Range.closed(1.0, 3.0), range.getValueRange());
  }

  @Test
  void fixedLengthRangePicksDensestWindow() {
    // a tight cluster around 5 plus two far outliers
    final List<Double> items = new ArrayList<>(List.of(-50.0, 4.9, 5.0, 5.1, 5.0, 60.0));
    final DistributionRange range = DistributionExtractor.fixedLengthRange(items, 1.0);
    final List<Double> extracted = range.getExtractedItems();
    // the 4 clustered values within a 1.0-wide window are kept, outliers dropped
    assertEquals(4, extracted.size());
    for (double v : extracted) {
      assertTrue(v >= 4.9 && v <= 5.1, "unexpected value kept: " + v);
    }
  }

  @Test
  void mostPopulatedRangeClusterSplitsByTolerance() {
    final List<Double> items = new ArrayList<>(List.of(1.0, 1.2, 1.3, 10.0, 10.1));
    final DistributionRange range = DistributionExtractor.mostPopulatedRangeCluster(items, 0.5);
    assertEquals(3, range.getExtractedItems().size());
    assertEquals(Range.closed(1.0, 1.3), range.getValueRange());
  }

  @Test
  void interpercentileRangeExtractsMiddle() {
    final List<Double> items = new ArrayList<>(List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0));
    final DistributionRange range = DistributionExtractor.interpercentileRange(items, 25.0, 75.0);
    final List<Double> extracted = range.getExtractedItems();
    // lowerIndex = ceil(0.25*8)-1 = 1, upperIndex = ceil(0.75*8)-1 = 5 -> indices 1..5 inclusive
    assertEquals(List.of(2.0, 3.0, 4.0, 5.0, 6.0), extracted);
  }

  // ---------------------------------------------------------------------------------------------
  // StandardsList
  // ---------------------------------------------------------------------------------------------

  @Test
  void standardsListMzRangeQuery() {
    final StandardsList list = new StandardsList(
        List.of(new StandardsListItem(100.0), new StandardsListItem(200.0),
            new StandardsListItem(300.0), new StandardsListItem(400.0)));

    final List<StandardsListItem> inRange = list.getInRanges(Range.closed(150.0, 350.0), null)
        .getStandardMolecules();
    assertEquals(2, inRange.size());
    assertEquals(200.0, inRange.get(0).getMzRatio(), EPS);
    assertEquals(300.0, inRange.get(1).getMzRatio(), EPS);
  }

  // ---------------------------------------------------------------------------------------------
  // MassCalibrator end-to-end on synthetic mass lists
  // ---------------------------------------------------------------------------------------------

  private static StandardsList threeStandards() {
    return new StandardsList(
        List.of(new StandardsListItem(100.0), new StandardsListItem(200.0),
            new StandardsListItem(300.0)));
  }

  /**
   * Build a mass list whose peaks sit at a uniform ppm error above the three standards.
   */
  private static DataPoint[] shiftedMassList(double ppm) {
    final PpmError ppmError = new PpmError();
    final double[] standards = {100.0, 200.0, 300.0};
    final DataPoint[] dps = new DataPoint[standards.length];
    for (int i = 0; i < standards.length; i++) {
      // invert calibrateAgainstError: measured = actual * (1 + ppm/1e6)
      final double measured = standards[i] * (1 + ppm / 1_000_000);
      dps[i] = new SimpleDataPoint(measured, 1000.0);
    }
    return dps;
  }

  @Test
  void findMassListErrorsRecoversUniformPpmError() {
    final MassCalibrator calibrator = new MassCalibrator(null, new MZTolerance(0.01, 10), 0.1, 2.0,
        threeStandards(), null);

    final ArrayList<Double> errors = calibrator.findMassListErrors(shiftedMassList(5.0), 1.0f);
    assertEquals(3, errors.size());
    for (double e : errors) {
      assertEquals(5.0, e, 1e-6);
    }
  }

  @Test
  void estimateBiasAndCalibrateRoundTrip() {
    final MassCalibrator calibrator = new MassCalibrator(null, new MZTolerance(0.01, 10), 0.1, 2.0,
        threeStandards(), null);

    final DataPoint[] massList = shiftedMassList(5.0);
    calibrator.addMassList(massList, 1.0f);
    final double bias = calibrator.estimateBias(false);
    assertEquals(5.0, bias, 1e-6);

    final DataPoint[] calibrated = calibrator.calibrateMassList(massList);
    final double[] expected = {100.0, 200.0, 300.0};
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i], calibrated[i].getMZ(), 1e-6);
      assertEquals(massList[i].getIntensity(), calibrated[i].getIntensity(), EPS);
    }
  }

  @Test
  void calibrateMassListWithExplicitBias() {
    final MassCalibrator calibrator = new MassCalibrator(null, new MZTolerance(0.01, 10), 0.1, 2.0,
        threeStandards(), null);

    final DataPoint[] massList = shiftedMassList(5.0);
    final DataPoint[] calibrated = calibrator.calibrateMassList(massList, 5.0);
    final double[] expected = {100.0, 200.0, 300.0};
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i], calibrated[i].getMZ(), 1e-6);
    }
  }

  @Test
  void estimateBiasFromErrorsUsesRangeMethod() {
    final MassCalibrator calibrator = new MassCalibrator(null, new MZTolerance(0.01, 10), 0.1, 2.0,
        threeStandards(), null);

    // tight cluster + outlier; the fixed-length range drops the outlier
    final List<Double> errors = new ArrayList<>(List.of(4.9, 5.0, 5.1, 50.0));
    final double bias = calibrator.estimateBiasFromErrors(errors, false);
    assertEquals(5.0, bias, 1e-6);
  }

  // ---------------------------------------------------------------------------------------------
  // OLSRegressionTrend
  // ---------------------------------------------------------------------------------------------

  @Test
  void olsRegressionRecoversLinearTrend() {
    // y = 2 + 3x sampled exactly; degree-1 polynomial (features x^0, x^1) must recover it
    final XYSeries series = new XYSeries("test");
    for (int x = 1; x <= 5; x++) {
      series.add((double) x, 2.0 + 3.0 * x);
    }

    final OLSRegressionTrend trend = new OLSRegressionTrend(1, false, false);
    trend.setDataset(series);

    assertEquals(2.0 + 3.0 * 10.0, trend.getValue(10.0), 1e-6);
    assertEquals(2.0 + 3.0 * 0.0, trend.getValue(0.0), 1e-6);
  }

  @Test
  void olsRegressionRecoversQuadraticTrend() {
    // y = 1 + 0x + 2x^2 ; needs degree 2
    final XYSeries series = new XYSeries("test");
    for (int x = -3; x <= 3; x++) {
      series.add((double) x, 1.0 + 2.0 * x * x);
    }

    final OLSRegressionTrend trend = new OLSRegressionTrend(2, false, false);
    trend.setDataset(series);

    assertEquals(1.0 + 2.0 * 25.0, trend.getValue(5.0), 1e-6);
  }
}
