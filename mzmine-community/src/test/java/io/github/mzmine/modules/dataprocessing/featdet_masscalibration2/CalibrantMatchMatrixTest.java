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

package io.github.mzmine.modules.dataprocessing.featdet_masscalibration2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.impl.masslist.SimpleMassList;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.api.CalibrantList;
import io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.api.CalibrantMatchMatrix;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance.Unit;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import org.junit.jupiter.api.Test;

class CalibrantMatchMatrixTest {

  private static final double EPS = 1e-9;
  private static final MZTolerance MZ_TOL = new MZTolerance(0.02, 0);
  private static final RTTolerance RT_TOL = new RTTolerance(0.5f, Unit.MINUTES);

  private static MassSpectrum spectrum(double[] mz, double[] intensity) {
    return new SimpleMassList(null, mz, intensity);
  }

  @Test
  void keepsHighestIntensityPeakPerCalibrant() {
    final CalibrantList calibrants = new CalibrantList(new double[]{200.0}, new float[]{-1});
    // two peaks within tolerance of the calibrant; the more intense one must win
    final MassSpectrum scan = spectrum(new double[]{199.995, 200.004},
        new double[]{10, 100});

    final CalibrantMatchMatrix matrix = new CalibrantMatchMatrix(calibrants);
    final DoubleArrayList mz = new DoubleArrayList();
    final DoubleArrayList delta = new DoubleArrayList();
    matrix.checkMatches(scan, 0.0, 0f, MZ_TOL, null);
    matrix.addMatches(mz, delta);

    assertEquals(1, mz.size());
    assertEquals(200.004, mz.getDouble(0), EPS);
    assertEquals(200.004 - 200.0, delta.getDouble(0), EPS);
  }

  @Test
  void peakOutsideToleranceIsIgnored() {
    final CalibrantList calibrants = new CalibrantList(new double[]{200.0}, new float[]{-1});
    final MassSpectrum scan = spectrum(new double[]{200.5}, new double[]{100});

    final CalibrantMatchMatrix matrix = new CalibrantMatchMatrix(calibrants);
    final DoubleArrayList mz = new DoubleArrayList();
    final DoubleArrayList delta = new DoubleArrayList();
    matrix.checkMatches(scan, 0.0, 0f, MZ_TOL, null);
    matrix.addMatches(mz, delta);

    assertTrue(mz.isEmpty());
  }

  @Test
  void peaksBelowMinIntensityAreIgnored() {
    final CalibrantList calibrants = new CalibrantList(new double[]{200.0}, new float[]{-1});
    // the closer/only in-tolerance peak below the threshold; a higher-intensity peak above it wins
    final MassSpectrum scan = spectrum(new double[]{200.001, 200.002}, new double[]{5, 50});

    final CalibrantMatchMatrix matrix = new CalibrantMatchMatrix(calibrants);
    final DoubleArrayList mz = new DoubleArrayList();
    final DoubleArrayList delta = new DoubleArrayList();
    matrix.checkMatches(scan, 10.0, 0f, MZ_TOL, null); // minIntensity = 10
    matrix.addMatches(mz, delta);

    assertEquals(1, mz.size());
    assertEquals(200.002, mz.getDouble(0), EPS);
  }

  @Test
  void ambiguousPeakMatchingTwoCalibrantsIsDropped() {
    // two calibrants both within the m/z window of one peak -> ambiguous, no match
    final CalibrantList calibrants = new CalibrantList(new double[]{200.0, 200.01},
        new float[]{-1, -1});
    final MassSpectrum scan = spectrum(new double[]{200.005}, new double[]{100});

    final CalibrantMatchMatrix matrix = new CalibrantMatchMatrix(calibrants);
    final DoubleArrayList mz = new DoubleArrayList();
    final DoubleArrayList delta = new DoubleArrayList();
    matrix.checkMatches(scan, 0.0, 0f, MZ_TOL, null);
    matrix.addMatches(mz, delta);

    assertTrue(mz.isEmpty());
  }

  @Test
  void retentionTimeWindowGatesCalibrants() {
    // calibrant at 200 elutes at rt 2, calibrant at 300 elutes at rt 8
    final CalibrantList calibrants = new CalibrantList(new double[]{200.0, 300.0},
        new float[]{2f, 8f});
    final MassSpectrum scan = spectrum(new double[]{200.001, 300.001}, new double[]{50, 50});

    final CalibrantMatchMatrix matrix = new CalibrantMatchMatrix(calibrants);
    final DoubleArrayList mz = new DoubleArrayList();
    final DoubleArrayList delta = new DoubleArrayList();
    // scan at rt 2 -> only the 200 calibrant is within the RT window
    matrix.checkMatches(scan, 0.0, 2.0f, MZ_TOL, RT_TOL);
    matrix.addMatches(mz, delta);

    assertEquals(1, mz.size());
    assertEquals(200.001, mz.getDouble(0), EPS);
  }

  @Test
  void calibrantWithoutRtMatchesRegardlessOfRt() {
    final CalibrantList calibrants = new CalibrantList(new double[]{500.0}, new float[]{-1});
    final MassSpectrum scan = spectrum(new double[]{500.004}, new double[]{50});

    final CalibrantMatchMatrix matrix = new CalibrantMatchMatrix(calibrants);
    final DoubleArrayList mz = new DoubleArrayList();
    final DoubleArrayList delta = new DoubleArrayList();
    matrix.checkMatches(scan, 0.0, 99.0f, MZ_TOL, RT_TOL);
    matrix.addMatches(mz, delta);

    assertEquals(1, mz.size());
    assertEquals(500.004, mz.getDouble(0), EPS);
  }

  @Test
  void matchesMultipleCalibrantsAndAccumulatesAcrossScans() {
    final CalibrantList calibrants = new CalibrantList(new double[]{200.0, 500.0},
        new float[]{-1, -1});
    final CalibrantMatchMatrix matrix = new CalibrantMatchMatrix(calibrants);
    final DoubleArrayList mz = new DoubleArrayList();
    final DoubleArrayList delta = new DoubleArrayList();

    // scan 1: both calibrants present
    matrix.checkMatches(spectrum(new double[]{200.003, 500.004}, new double[]{20, 30}), 0.0, 1f,
        MZ_TOL, null);
    matrix.addMatches(mz, delta);
    // scan 2: only the 200 calibrant present -> accumulates one more match
    matrix.checkMatches(spectrum(new double[]{200.006}, new double[]{40}), 0.0, 2f, MZ_TOL, null);
    matrix.addMatches(mz, delta);

    assertEquals(3, mz.size());
    // scan 1 flushes calibrants in ascending order, then scan 2
    assertEquals(200.003, mz.getDouble(0), EPS);
    assertEquals(500.004, mz.getDouble(1), EPS);
    assertEquals(200.006, mz.getDouble(2), EPS);
    assertEquals(200.006 - 200.0, delta.getDouble(2), EPS);
  }
}
