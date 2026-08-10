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

package io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.api;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.SimpleRange.SimpleDoubleRange;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.util.collections.BinarySearch.DefaultTo;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Matches the peaks of a single spectrum to a {@link CalibrantList}, keeping for each calibrant the
 * <b>single highest-intensity</b> peak that falls within the m/z (and optional retention time)
 * tolerance. A peak that is within tolerance of more than one calibrant is treated as ambiguous and
 * dropped (it matches none of them).
 * <p>
 * The matcher holds one mutable slot per calibrant ({@link #measuredMzs} / {@link #measuredIntensities},
 * both parallel to {@link CalibrantList#getMz(int)}), so a single instance is reused across all scans
 * of a file: call {@link #checkMatches} for a spectrum, then {@link #addMatches} to flush that
 * spectrum's best-per-calibrant matches into the output lists (which also resets the slots for the
 * next spectrum). It is therefore stateful and <b>not thread-safe</b>.
 * <p>
 * Both the spectrum peaks and the calibrant m/z values are sorted ascending, so matching is a single
 * forward pass over the peaks with a monotonically advancing calibrant index — roughly O(N + C) for
 * a spectrum of N peaks and C calibrants.
 */
public class CalibrantMatchMatrix {

  private final CalibrantList calibrants;
  // parallel to the calibrant list: measured m/z and intensity of the current best match per
  // calibrant; -1 marks "no match yet"
  private final double[] measuredMzs;
  private final double[] measuredIntensities;

  public CalibrantMatchMatrix(@NotNull final CalibrantList calibrants) {
    this.calibrants = calibrants;
    this.measuredMzs = new double[calibrants.size()];
    this.measuredIntensities = new double[calibrants.size()];
    Arrays.fill(measuredMzs, -1);
    Arrays.fill(measuredIntensities, -1);
  }

  /**
   * Clears all per-calibrant match slots. Called automatically at the end of {@link #addMatches}, so
   * the instance is ready to match the next spectrum.
   */
  public void resetMatches() {
    Arrays.fill(measuredMzs, -1);
    Arrays.fill(measuredIntensities, -1);
  }

  /**
   * Match the peaks of one spectrum against the calibrant list, updating the internal per-calibrant
   * best-match slots. Does not reset beforehand; the usual pattern is one {@code checkMatches} +
   * one {@link #addMatches} per spectrum.
   *
   * @param access       the spectrum to match. Pass the mass-list view (the
   *                     {@link io.github.mzmine.datamodel.data_access.ScanDataAccess} opened with
   *                     {@link io.github.mzmine.datamodel.data_access.EfficientDataAccess.ScanDataType#MASS_LIST}),
   *                     not the underlying raw {@code Scan}. Peaks must be sorted by ascending m/z
   * @param minIntensity peaks with an intensity below this threshold are ignored
   * @param rt           retention time of the spectrum (minutes), used only when {@code rtTolerance}
   *                     is not {@code null}
   * @param mzTol        m/z matching tolerance
   * @param rtTolerance  retention-time tolerance, or {@code null} to ignore RT (e.g. a segment
   *                     window already constrains RT). Calibrants without a retention time
   *                     ({@code rt < 0}) always pass the RT check
   */
  public void checkMatches(final MassSpectrum access, final double minIntensity, final float rt, final @NotNull MZTolerance mzTol,
      final @Nullable RTTolerance rtTolerance) {

    final SimpleDoubleRange calibrantsMzRange = calibrants.getCalibrantsMzRange();
    int lowerCalibrantIndex = 0;
    for (int i = access.binarySearch(
        mzTol.getToleranceRange(calibrantsMzRange.lower()).lowerEndpoint(),
        DefaultTo.CLOSEST_VALUE); i >= 0 && i < access.getNumberOfDataPoints(); i++) {

      final double mz = access.getMzValue(i);
      final double intensity = access.getIntensityValue(i);
      if(intensity < minIntensity) {
        continue;
      }

      final Range<Double> currentTolerance = mzTol.getToleranceRange(mz);

      while (lowerCalibrantIndex < calibrants.size()
          && currentTolerance.lowerEndpoint() > calibrants.getMz(lowerCalibrantIndex)) {
        lowerCalibrantIndex++;
      }

      if (currentTolerance.lowerEndpoint() > calibrantsMzRange.upper()) {
        break;
      }

      int prevCalibrantMatchIndex = -1;
      // check each potentially matching calibrant
      for (int calIndex = lowerCalibrantIndex; calIndex < calibrants.size(); calIndex++) {
        if (calibrants.getMz(calIndex) > currentTolerance.upperEndpoint()) {
          break;
        }

        // rt check
        final float calibrantRt = calibrants.getRt(calIndex);
        if (rtTolerance != null && calibrantRt >= 0 && !rtTolerance.checkWithinTolerance(rt,
            calibrantRt)) {
          continue;
        }

        if (mzTol.checkWithinTolerance(mz, calibrants.getMz(calIndex))
            && intensity > measuredIntensities[calIndex]) {
          if (prevCalibrantMatchIndex != -1 && prevCalibrantMatchIndex != calIndex) {
            // this peak already matched to another calibrant. remove both matches, it is ambiguous.
            measuredMzs[prevCalibrantMatchIndex] = -1;
            measuredIntensities[prevCalibrantMatchIndex] = -1;
            break;
          }
          // only keep the best match
          measuredMzs[calIndex] = mz;
          measuredIntensities[calIndex] = intensity;
          prevCalibrantMatchIndex = calIndex;
        }
      }
    }
  }

  /**
   * Appends the current best matches to the output lists as (measured m/z, absolute error
   * {@code measured - calibrant}) pairs — one per matched calibrant — then {@link #resetMatches()
   * resets} the slots for the next spectrum.
   *
   * @param mzList    receives the measured m/z of each matched calibrant
   * @param deltaList receives the absolute m/z error (Da) of each matched calibrant
   */
  public void addMatches(DoubleArrayList mzList, DoubleArrayList deltaList) {
    for (int i = 0; i < measuredIntensities.length; i++) {
      if (Double.compare(measuredMzs[i], -1) == 0) {
        continue;
      }
      mzList.add(measuredMzs[i]);
      deltaList.add(measuredMzs[i] - calibrants.getMz(i));
    }
    resetMatches();
  }
}
