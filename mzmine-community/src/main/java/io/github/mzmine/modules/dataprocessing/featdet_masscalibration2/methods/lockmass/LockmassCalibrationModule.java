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

package io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.methods.lockmass;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.ScanDataType;
import io.github.mzmine.datamodel.data_access.ScanDataAccess;
import io.github.mzmine.gui.chartbasics.simplechart.providers.PlotXYDataProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.impl.AnyXYProvider;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.api.MzCalibrationFunction;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.api.MzCalibrationMethod;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.api.PolynomialMzErrorFit;
import io.github.mzmine.modules.dataprocessing.norm_rtcalibration2.MovingAverage;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.color.SimpleColorPalette;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Lockmass calibration: a reference ion present in (most) MS1 scans is located in each spectrum and
 * used to derive a per-spectrum ppm correction. With multiple lockmasses the correction is a
 * polynomial of measured m/z; the polynomial coefficients are then smoothed over retention time
 * with a centered moving average (over a configurable number of lockmass spectra) so
 * single-spectrum outliers and missing spectra are handled robustly.
 */
public class LockmassCalibrationModule implements MzCalibrationMethod {

  private static final Logger logger = Logger.getLogger(LockmassCalibrationModule.class.getName());

  private final ParameterSet parameters;
  private final double[] lockmasses;
  private final MZTolerance mzTolerance;
  private final double minIntensity;
  private final int movingAverageScans;
  private final int degreeOption;
  private final List<PlotXYDataProvider> additionalData = new ArrayList<>();

  /**
   * No-arg constructor used by the module registry. Produces an unconfigured instance; use
   * {@link #newInstance(ParameterSet, MemoryMapStorage)} for an actual run.
   */
  public LockmassCalibrationModule() {
    this.parameters = null;
    this.lockmasses = new double[0];
    this.mzTolerance = null;
    this.minIntensity = 0;
    this.movingAverageScans = 5;
    this.degreeOption = PolynomialMzErrorFit.AUTO_DEGREE;
  }

  private LockmassCalibrationModule(ParameterSet parameters) {
    this.parameters = parameters;
    this.lockmasses = LockmassCalibrationParameters.resolveLockmasses(
        parameters.getValue(LockmassCalibrationParameters.lockmass));
    this.mzTolerance = parameters.getValue(LockmassCalibrationParameters.mzTolerance);
    this.minIntensity = parameters.getValue(LockmassCalibrationParameters.minIntensity);
    this.movingAverageScans = parameters.getValue(LockmassCalibrationParameters.movingAverageScans);
    this.degreeOption = PolynomialMzErrorFit.parseDegree(
        parameters.getValue(LockmassCalibrationParameters.polynomialDegree));
  }

  @Override
  public MzCalibrationMethod newInstance(@NotNull ParameterSet parameters,
      @Nullable MemoryMapStorage storage) {
    return new LockmassCalibrationModule(parameters);
  }

  @Override
  public @NotNull String getName() {
    return "Lockmass calibration";
  }

  @Override
  public @Nullable Class<? extends ParameterSet> getParameterSetClass() {
    return LockmassCalibrationParameters.class;
  }

  @Override
  public List<PlotXYDataProvider> getAdditionalPreviewData() {
    return additionalData;
  }

  @Override
  public @Nullable MzCalibrationFunction buildCalibration(@NotNull RawDataFile file) {
    additionalData.clear();
    if (lockmasses.length == 0) {
      logger.warning("No lockmass m/z configured for " + file.getName());
      return null;
    }

    // 1) collect per-spectrum lockmass points from MS1 scans
    final List<SpectrumAnchor> anchors = collectAnchors(file);
    if (anchors.isEmpty()) {
      logger.warning("No lockmass peaks found in any MS1 scan of " + file.getName());
      return null;
    }

    // 2) choose polynomial degree (Auto = lowest residual over pooled points)
    int degree = degreeOption;
    if (degree == PolynomialMzErrorFit.AUTO_DEGREE) {
      degree = chooseAutoDegree(anchors);
    }
    // can only fit degree d in a spectrum with at least d+1 lockmass points; reduce if necessary
    int usableDegree = Math.min(degree, lockmasses.length - 1);
    while (usableDegree > 0 && !hasSpectrumWithAtLeast(anchors, usableDegree + 1)) {
      usableDegree--;
    }

    // 3) fit each usable spectrum and collect (rt, polynomial coefficients) anchors
    final int fitDegree = usableDegree;
    final List<double[]> coeffAnchors = new ArrayList<>(); // each: [rt, beta0, beta1, ...]
    for (SpectrumAnchor anchor : anchors) {
      if (anchor.size() < fitDegree + 1) {
        continue; // missing/incomplete spectrum, interpolated via RT smoothing
      }
      final PolynomialFunction poly;
      try {
        poly = PolynomialMzErrorFit.fit(anchor.mz(), anchor.deltaMz(), fitDegree);
      } catch (Exception e) {
        continue; // skip spectra whose fit fails
      }
      final double[] beta = poly.getCoefficients(); // [c0, c1, ...], length <= fitDegree+1
      final double[] row = new double[fitDegree + 2];
      row[0] = anchor.rt();
      // copy coefficients (pad with zeros if the fit returned fewer, e.g. trailing zero terms)
      System.arraycopy(beta, 0, row, 1, Math.min(beta.length, fitDegree + 1));
      coeffAnchors.add(row);
    }
    if (coeffAnchors.isEmpty()) {
      logger.warning("No spectrum had enough lockmass matches to fit degree " + fitDegree);
      return null;
    }

    // sort by rt and merge duplicate retention times by averaging
    coeffAnchors.sort((a, b) -> Double.compare(a[0], b[0]));
    final List<double[]> merged = mergeDuplicateRts(coeffAnchors);

    final double[] rts = merged.stream().mapToDouble(r -> r[0]).toArray();
    final double minRt = rts[0];
    final double maxRt = rts[rts.length - 1];

    // 4) build per-coefficient RT functions (moving-average-smoothed) or constant fallback
    final LockmassCalibrationFunction function = buildFunction(fitDegree, merged, rts, minRt,
        maxRt);

    buildPreview(anchors, function, fitDegree);
    return function;
  }

  private static boolean hasSpectrumWithAtLeast(List<SpectrumAnchor> anchors, int minPoints) {
    for (SpectrumAnchor a : anchors) {
      if (a.size() >= minPoints) {
        return true;
      }
    }
    return false;
  }

  private List<SpectrumAnchor> collectAnchors(RawDataFile file) {
    final ScanDataAccess data = EfficientDataAccess.of(file, ScanDataType.MASS_LIST,
        ScanSelection.MS1);
    final List<SpectrumAnchor> anchors = new ArrayList<>();
    while (data.hasNextScan()) {
      final Scan scan = data.nextScan();
      if (scan == null) {
        continue;
      }
      final List<Double> mzList = new ArrayList<>();
      final List<Double> deltaList = new ArrayList<>();
      for (double expected : lockmasses) {
        final double measured = findLockmassPeak(data, expected);
        if (!Double.isNaN(measured)) {
          mzList.add(measured);
          deltaList.add(measured - expected); // absolute m/z error (Da)
        }
      }
      if (!mzList.isEmpty()) {
        anchors.add(new SpectrumAnchor(scan.getRetentionTime(),
            mzList.stream().mapToDouble(Double::doubleValue).toArray(),
            deltaList.stream().mapToDouble(Double::doubleValue).toArray()));
      }
    }
    return anchors;
  }

  /**
   * Find the most intense peak within tolerance of the expected lockmass m/z in the current scan of
   * the data access.
   *
   * @return the measured m/z, or {@link Double#NaN} if no qualifying peak was found
   */
  private double findLockmassPeak(ScanDataAccess data, double expected) {
    final Range<Double> range = mzTolerance.getToleranceRange(expected);
    double bestMz = Double.NaN;
    double bestIntensity = minIntensity;
    final int n = data.getNumberOfDataPoints();
    for (int i = 0; i < n; i++) {
      final double mz = data.getMzValue(i);
      if (mz < range.lowerEndpoint()) {
        continue;
      }
      if (mz > range.upperEndpoint()) {
        break; // mass lists are sorted ascending
      }
      final double intensity = data.getIntensityValue(i);
      if (intensity >= bestIntensity) {
        bestIntensity = intensity;
        bestMz = mz;
      }
    }
    return bestMz;
  }

  private int chooseAutoDegree(List<SpectrumAnchor> anchors) {
    int total = 0;
    for (SpectrumAnchor a : anchors) {
      total += a.size();
    }
    final double[] mz = new double[total];
    final double[] delta = new double[total];
    int idx = 0;
    for (SpectrumAnchor a : anchors) {
      for (int i = 0; i < a.size(); i++) {
        mz[idx] = a.mz()[i];
        delta[idx] = a.deltaMz()[i];
        idx++;
      }
    }
    return PolynomialMzErrorFit.chooseDegreeByLowestResidual(mz, delta, lockmasses.length - 1);
  }

  private LockmassCalibrationFunction buildFunction(int degree, List<double[]> merged, double[] rts,
      double minRt, double maxRt) {
    final int nCoeff = degree + 1;
    final String desc = "Lockmass calibration (degree %d, %d RT anchors)".formatted(degree,
        merged.size());

    // bounds of the lockmass m/z range; peaks outside keep the smallest/largest lockmass shift
    double minMz = Double.POSITIVE_INFINITY;
    double maxMz = Double.NEGATIVE_INFINITY;
    for (double lm : lockmasses) {
      minMz = Math.min(minMz, lm);
      maxMz = Math.max(maxMz, lm);
    }

    if (merged.size() < 2) {
      // single anchor: constant coefficients
      final double[] constant = new double[nCoeff];
      System.arraycopy(merged.get(0), 1, constant, 0, nCoeff);
      return new LockmassCalibrationFunction(degree, constant, minMz, maxMz, desc);
    }

    // smooth each polynomial coefficient over retention time with a centered moving average, then
    // interpolate linearly between anchors for lookup at arbitrary RT
    final PolynomialSplineFunction[] splines = new PolynomialSplineFunction[nCoeff];
    int window = movingAverageScans;
    if (window % 2 == 0) {
      window++; // MovingAverage requires an odd window; normalize once to avoid repeated warnings
    }
    final boolean smooth = window > 1;
    for (int k = 0; k < nCoeff; k++) {
      final double[] coeff = new double[merged.size()];
      for (int i = 0; i < merged.size(); i++) {
        coeff[i] = merged.get(i)[k + 1];
      }
      final double[] smoothed = smooth ? MovingAverage.calculate(coeff, window) : coeff;
      splines[k] = new LinearInterpolator().interpolate(rts, smoothed);
    }
    return new LockmassCalibrationFunction(degree, splines, minRt, maxRt, minMz, maxMz, desc);
  }

  /**
   * Merge rows that share the same retention time by averaging their coefficients, keeping the rt
   * strictly increasing as required by the interpolators.
   */
  private static List<double[]> mergeDuplicateRts(List<double[]> sorted) {
    final List<double[]> merged = new ArrayList<>();
    int i = 0;
    while (i < sorted.size()) {
      int j = i + 1;
      while (j < sorted.size() && sorted.get(j)[0] == sorted.get(i)[0]) {
        j++;
      }
      if (j == i + 1) {
        merged.add(sorted.get(i));
      } else {
        final double[] avg = new double[sorted.get(i).length];
        avg[0] = sorted.get(i)[0];
        for (int r = i; r < j; r++) {
          for (int c = 1; c < avg.length; c++) {
            avg[c] += sorted.get(r)[c];
          }
        }
        for (int c = 1; c < avg.length; c++) {
          avg[c] /= (j - i);
        }
        merged.add(avg);
      }
      i = j;
    }
    return merged;
  }

  private void buildPreview(List<SpectrumAnchor> anchors, LockmassCalibrationFunction function,
      int fitDegree) {
    // scatter: the absolute m/z error of every matched lockmass, but only from scans that had
    // enough lockmasses for the polynomial degree (i.e. the scans that actually fed the fit)
    final List<double[]> observed = new ArrayList<>(); // [rt, deltaMz]
    for (SpectrumAnchor a : anchors) {
      if (a.size() < fitDegree + 1) {
        continue;
      }
      for (int i = 0; i < a.size(); i++) {
        observed.add(new double[]{a.rt(), a.deltaMz()[i]});
      }
    }
    observed.sort((x, y) -> Double.compare(x[0], y[0]));

    additionalData.add(new AnyXYProvider(Color.GRAY, "lockmass shift (m/z)", observed.size(),
        i -> observed.get(i)[0], i -> observed.get(i)[1]));

    final SimpleColorPalette colors = ConfigService.getDefaultColorPalette().clone(true);
    if (!observed.isEmpty()) {
      final double min = observed.getFirst()[0];
      final double max = observed.getLast()[0];
      final int steps = 200;
      // one smoothed model line per lockmass so each calibrant's points have a matching curve
      for (final double lockmass : lockmasses) {
        additionalData.add(new AnyXYProvider(colors.getNextColorAWT(),
            "smoothed correction (m/z) @%.4f".formatted(lockmass), steps,
            i -> min + (max - min) * i / (steps - 1),
            i -> function.modeledDeltaMz(lockmass, (float) (min + (max - min) * i / (steps - 1)))));
      }
    }
  }

  /**
   * Per-spectrum lockmass observations: matched measured m/z and the corresponding absolute m/z
   * errors (Da).
   */
  private record SpectrumAnchor(float rt, double[] mz, double[] deltaMz) {

    int size() {
      return mz.length;
    }
  }
}
