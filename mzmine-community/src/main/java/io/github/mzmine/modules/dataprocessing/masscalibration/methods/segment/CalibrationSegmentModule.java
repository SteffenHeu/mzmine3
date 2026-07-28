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

package io.github.mzmine.modules.dataprocessing.masscalibration.methods.segment;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.ScanDataType;
import io.github.mzmine.datamodel.data_access.ScanDataAccess;
import io.github.mzmine.gui.chartbasics.simplechart.providers.PlotXYDataProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.impl.AnyXYProvider;
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
import io.github.mzmine.util.MemoryMapStorage;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Calibration-segment method: a calibrant mix is injected during a fixed RT window. Calibrant peaks
 * are matched to a calibrant list across the spectra of that window, a single polynomial of the
 * absolute m/z error (Da) vs. m/z is fitted, and that one function is applied to every spectrum of
 * the file.
 */
public class CalibrationSegmentModule implements MzCalibrationMethod {

  private static final Logger logger = Logger.getLogger(CalibrationSegmentModule.class.getName());

  private final ParameterSet parameters;
  private final CalibrantListSource calibrantSource;
  private final File customCalibrantFile;
  private final Range<Double> rtRange;
  private final MZTolerance mzTolerance;
  private final double minIntensity;
  private final int degreeOption;
  private final List<PlotXYDataProvider> additionalData = new ArrayList<>();

  public CalibrationSegmentModule() {
    this.parameters = null;
    this.calibrantSource = null;
    this.customCalibrantFile = null;
    this.rtRange = null;
    this.mzTolerance = null;
    this.minIntensity = 0;
    this.degreeOption = PolynomialMzErrorFit.AUTO_DEGREE;
  }

  private CalibrationSegmentModule(ParameterSet parameters) {
    this.parameters = parameters;
    final ComboWithFileInputValue<CalibrantListSource> calibrant = parameters.getValue(
        CalibrationSegmentParameters.calibrantList);
    this.calibrantSource = calibrant.getSelectedOption();
    this.customCalibrantFile = calibrant.getEmbeddedValue();
    this.rtRange = parameters.getValue(CalibrationSegmentParameters.rtRange);
    this.mzTolerance = parameters.getValue(CalibrationSegmentParameters.mzTolerance);
    this.minIntensity = parameters.getValue(CalibrationSegmentParameters.minIntensity);
    this.degreeOption = PolynomialMzErrorFit.parseDegree(
        parameters.getValue(CalibrationSegmentParameters.polynomialDegree));
  }

  @Override
  public MzCalibrationMethod newInstance(@NotNull ParameterSet parameters,
      @Nullable MemoryMapStorage storage) {
    return new CalibrationSegmentModule(parameters);
  }

  @Override
  public @NotNull String getName() {
    return "Calibration segment";
  }

  @Override
  public @Nullable Class<? extends ParameterSet> getParameterSetClass() {
    return CalibrationSegmentParameters.class;
  }

  @Override
  public List<PlotXYDataProvider> getAdditionalPreviewData() {
    return additionalData;
  }

  @Override
  public @Nullable MzCalibrationFunction buildCalibration(@NotNull RawDataFile file) {
    additionalData.clear();
    if (calibrantSource == null) {
      logger.warning("No calibrant list configured.");
      return null;
    }

    final CalibrantList calibrants = CalibrantList.fromSource(calibrantSource, customCalibrantFile);
    if (calibrants == null) {
      return null;
    }
    if (calibrants.isEmpty()) {
      logger.warning("Calibrant list is empty.");
      return null;
    }

    final Range<Float> rtFloat = Range.closed(rtRange.lowerEndpoint().floatValue(),
        rtRange.upperEndpoint().floatValue());
    final ScanDataAccess data = EfficientDataAccess.of(file, ScanDataType.MASS_LIST,
        new ScanSelection(1, rtFloat));

    // collect (measured m/z, absolute error Da) matches across the segment window
    final DoubleArrayList mzList = new DoubleArrayList();
    final DoubleArrayList deltaList = new DoubleArrayList();
    final CalibrantMatchMatrix matchMatrix = new CalibrantMatchMatrix(calibrants);
    while (data.hasNextScan()) {
      final Scan scan = data.nextScan();
      if (scan == null) {
        continue;
      }
      final double rt = scan.getRetentionTime();
      if (!rtRange.contains(rt)) {
        continue;
      }

      // pass the mass-list access (data), not the underlying raw scan, so mass-list peaks are read
      matchMatrix.checkMatches(data, minIntensity, scan.getRetentionTime(), mzTolerance, null);
      matchMatrix.addMatches(mzList, deltaList);
    }

    if (mzList.size() < 2) {
      logger.warning(
          "Too few calibrant matches (%d) in the segment to fit a calibration.".formatted(
              mzList.size()));
      return null;
    }

    final double[] mz = mzList.toDoubleArray();
    final double[] delta = deltaList.toDoubleArray();

    int degree = degreeOption;
    if (degree == PolynomialMzErrorFit.AUTO_DEGREE) {
      degree = PolynomialMzErrorFit.chooseDegreeByLowestResidual(mz, delta,
          PolynomialMzErrorFit.MAX_UI_DEGREE);
    }

    final PolynomialFunction poly;
    try {
      poly = PolynomialMzErrorFit.fit(mz, delta, degree);
    } catch (Exception e) {
      logger.warning("Calibration-segment polynomial fit failed: " + e.getMessage());
      return null;
    }

    double minMz = Double.POSITIVE_INFINITY;
    double maxMz = Double.NEGATIVE_INFINITY;
    for (double m : mz) {
      minMz = Math.min(minMz, m);
      maxMz = Math.max(maxMz, m);
    }

    final String desc = "Calibration segment (degree %d, %d calibrant matches)".formatted(degree,
        mz.length);
    SegmentCalibrationFunction calibration = new SegmentCalibrationFunction(poly, minMz, maxMz,
        desc);
    buildPreview(mz, delta, calibration);
    return calibration;
  }

  private void buildPreview(double[] mz, double[] delta, SegmentCalibrationFunction cali) {
    additionalData.add(
        new AnyXYProvider(java.awt.Color.GRAY, "calibrant error (m/z)", mz.length, i -> mz[i],
            i -> delta[i]));

    double max = Double.NEGATIVE_INFINITY;
    for (double m : mz) {
      max = Math.max(max, m);
    }
    final double lo = 0;
    final double hi = max * 1.3;
    final int steps = 200;
    additionalData.add(new AnyXYProvider(java.awt.Color.RED, "fit (m/z)", steps,
        i -> lo + (hi - lo) * i / (steps - 1), i -> {
      final double x = lo + (hi - lo) * i / (steps - 1);
      return x - cali.getCalibratedMz(x, 0);
    }));
  }
}
