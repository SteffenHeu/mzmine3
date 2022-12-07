/*
 * Copyright (c) 2004-2022 The MZmine Development Team
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

package io.github.mzmine.util.scans;

import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import gnu.trove.list.array.TDoubleArrayList;
import io.github.mzmine.datamodel.MassList;
import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.impl.masslist.SimpleMassList;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.util.DataPointSorter;
import io.github.mzmine.util.DataPointUtils;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.scans.SpectraMerging.IntensityMergingType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public class DenoisingUtils {

  public static double[][] getNonNoiseDataPoints(List<MassSpectrum> spectra,
      final MZTolerance mzTolerance, final int minDetections) {
    if (minDetections > spectra.size()) {
      throw new RuntimeException(String.format(
          "Minimum number of detections (%d) is higher than number of input spectra (%d). Cannot denoise.",
          minDetections, spectra.size()));
    }

    return SpectraMerging.calculatedMergedMzsAndIntensities(spectra, mzTolerance,
        IntensityMergingType.SUMMED, SpectraMerging.DEFAULT_CENTER_FUNCTION, null, null,
        minDetections);
  }

  public static Map<MassSpectrum, double[][]> denoiseToDataPoints(List<MassSpectrum> spectra,
      final MZTolerance mzTolerance, final int minDetections) {
    final double[][] nonNoiseDataPoints = getNonNoiseDataPoints(spectra, mzTolerance,
        minDetections);

    final double[][] sorted = DataPointUtils.sort(nonNoiseDataPoints[0], nonNoiseDataPoints[1],
        DataPointSorter.DEFAULT_INTENSITY);
    final RangeMap<Double, Double> mzMap = TreeRangeMap.create();

    for (int i = 0; i < sorted[0].length; i++) {
      final double mz = sorted[0][i];
      final Range<Double> range = SpectraMerging.createNewNonOverlappingRange(mzMap,
          mzTolerance.getToleranceRange(mz));
      mzMap.put(range, sorted[1][i]);
    }

    final Map<MassSpectrum, double[][]> result = new HashMap<>();
    TDoubleArrayList mzs = new TDoubleArrayList();
    TDoubleArrayList intensities = new TDoubleArrayList();

    double[] mzBuffer = new double[0];
    double[] intensityBuffer = new double[0];

    for (final MassSpectrum spectrum : spectra) {
      mzBuffer = spectrum.getMzValues(mzBuffer);
      intensityBuffer = spectrum.getIntensityValues(intensityBuffer);

      for (int i = 0; i < spectrum.getNumberOfDataPoints(); i++) {
        if (mzMap.get(mzBuffer[i]) != null) {
          mzs.add(mzBuffer[i]);
          intensities.add(intensityBuffer[i]);
        }
      }

      result.put(spectrum, new double[][]{mzs.toArray(), intensities.toArray()});
    }

    return result;
  }

  public static Map<MassSpectrum, MassList> denoiseToMassList(List<MassSpectrum> spectra,
      final MZTolerance mzTolerance, final int minDetections, @Nullable MemoryMapStorage storage) {
    var denoised = denoiseToDataPoints(spectra, mzTolerance, minDetections);
    final Map<MassSpectrum, MassList> result = new HashMap<>();
    denoised.forEach(
        (spectrum, dataPoints) -> result.put(spectrum, new SimpleMassList(storage, dataPoints)));

    return result;
  }
}
