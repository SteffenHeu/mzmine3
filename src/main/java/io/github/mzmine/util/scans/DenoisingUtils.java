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
import io.github.mzmine.datamodel.impl.SimpleMassSpectrum;
import io.github.mzmine.datamodel.impl.masslist.SimpleMassList;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.exactmass.ExactMassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.exactmass.ExactMassDetectorParameters;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.util.DataPointSorter;
import io.github.mzmine.util.DataPointUtils;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.scans.SpectraMerging.IntensityMergingType;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public class DenoisingUtils {

  private static final MassDetector exactMass = MZmineCore.getModuleInstance(
      ExactMassDetector.class);
  private static final ParameterSet exactMassDetectorParameters = MZmineCore.getConfiguration()
      .getModuleParameters(ExactMassDetector.class);

  static {
    exactMassDetectorParameters.setParameter(
        ExactMassDetectorParameters.noiseLevel, 0d);
    exactMassDetectorParameters.setParameter(
        ExactMassDetectorParameters.detectIsotopes, false);
  }

  private DenoisingUtils() {
  }

  public static <T extends MassSpectrum> double[][] getNonNoiseDataPoints(
      Collection<T> spectra, final MZTolerance mzTolerance,
      final int minDetections) {
    if (minDetections > spectra.size()) {
      throw new RuntimeException(String.format(
          "Minimum number of detections (%d) is higher than number of input spectra (%d). Cannot denoise.",
          minDetections, spectra.size()));
    }

    final List<MassSpectrum> centroidSpectra = spectra.stream().map(s -> {
      if (s.getSpectrumType().isCentroided()) {
        return s;
      } else {
        final double[][] data = exactMass.getMassValues(s,
            exactMassDetectorParameters);
        return new SimpleMassSpectrum(data[0], data[1]);
      }
    }).toList();

    return SpectraMerging.calculatedMergedMzsAndIntensities(centroidSpectra,
        mzTolerance, IntensityMergingType.SUMMED,
        SpectraMerging.DEFAULT_CENTER_FUNCTION, null, null, minDetections);
  }

  public static <T extends MassSpectrum> Map<T, double[][]> denoiseToDataPoints(
      List<T> spectra, final MZTolerance mzTolerance, final int minDetections) {
    final double[][] nonNoiseDataPoints = getNonNoiseDataPoints(spectra,
        mzTolerance, minDetections);

    final double[][] sorted = DataPointUtils.sort(nonNoiseDataPoints[0],
        nonNoiseDataPoints[1], DataPointSorter.DEFAULT_INTENSITY);
    final RangeMap<Double, Double> mzMap = TreeRangeMap.create();

    for (int i = 0; i < sorted[0].length; i++) {
      final double mz = sorted[0][i];
      if (mzMap.get(mz) == null) {
        final Range<Double> range = SpectraMerging.createNewNonOverlappingRange(
            mzMap, mzTolerance.getToleranceRange(mz));
        mzMap.put(range, sorted[1][i]);
      }
    }

    final Map<T, double[][]> result = new HashMap<>();
    TDoubleArrayList mzs = new TDoubleArrayList();
    TDoubleArrayList intensities = new TDoubleArrayList();

    double[] mzBuffer = new double[0];
    double[] intensityBuffer = new double[0];

    for (final T spectrum : spectra) {
      mzBuffer = spectrum.getMzValues(mzBuffer);
      intensityBuffer = spectrum.getIntensityValues(intensityBuffer);

      for (int i = 0; i < spectrum.getNumberOfDataPoints(); i++) {
        if (mzMap.get(mzBuffer[i]) != null) {
          mzs.add(mzBuffer[i]);
          intensities.add(intensityBuffer[i]);
        }
      }

      result.put(spectrum,
          new double[][]{mzs.toArray(), intensities.toArray()});

      mzs.clear();
      intensities.clear();
    }

    return result;
  }

  public static <T extends MassSpectrum> Map<T, MassList> denoiseToMassList(
      List<T> spectra, final MZTolerance mzTolerance, final int minDetections,
      @Nullable MemoryMapStorage storage) {
    var denoised = denoiseToDataPoints(spectra, mzTolerance, minDetections);
    final Map<T, MassList> result = new HashMap<>();
    denoised.forEach((spectrum, dataPoints) -> result.put(spectrum,
        new SimpleMassList(storage, dataPoints)));

    return result;
  }
}
