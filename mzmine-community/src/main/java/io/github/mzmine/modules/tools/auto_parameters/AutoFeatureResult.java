/*
 * Copyright (c) 2004-2023 The MZmine Development Team
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

package io.github.mzmine.modules.tools.auto_parameters;

import com.google.common.collect.Range;
import com.google.common.math.Quantiles;
import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.data_access.ScanDataAccess;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.featuredata.IonTimeSeriesUtils;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.gui.preferences.NumberFormats;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.util.MathUtils;
import io.github.mzmine.util.MemoryMapStorage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AutoFeatureResult {

  private static final Logger logger = Logger.getLogger(AutoFeatureResult.class.getName());

  final Map<Double, Feature> rangeFeatureMap = new HashMap<>();
  final Map<Double, IsotopeCollection> rangeResolvedFeatureMap = new HashMap<>();
  final Scan bestScan;

  private final double mz;

  private final MemoryMapStorage storage;
  private final ModularFeatureList flist;

  public AutoFeatureResult(final ModularFeatureList flist, final Scan bestScan, final double mz,
      @Nullable MemoryMapStorage storage) {
    this.flist = flist;
    this.bestScan = bestScan;
    this.mz = mz;
    this.storage = storage;
  }

  public void createChromatograms(@NotNull List<MZTolerance> tolerances,
      @NotNull ScanDataAccess access) {
    tolerances.forEach(tol -> createChromatogram(tol, access));
  }

  private void createChromatogram(@NotNull MZTolerance tolerance, @NotNull ScanDataAccess access) {
    var chrom = IonTimeSeriesUtils.extractIonTimeSeries(access, tolerance.getToleranceRange(mz),
        Range.all(), storage);

    final Feature f = new ModularFeature(flist, access.getDataFile(), chrom,
        FeatureStatus.DETECTED);
    rangeFeatureMap.put(tolerance.getMzTolerance(), f);
  }

  public void processChromatograms(ScanDataAccess access) {

    double[] startRts = new double[rangeFeatureMap.size()];
    double[] endRts = new double[rangeFeatureMap.size()];

    int i = 0;
    for (Feature f : rangeFeatureMap.values()) {
      final IonTimeSeries<Scan> chrom = (IonTimeSeries<Scan>) f.getFeatureData();
      final int highestIndex = chrom.getSpectra().indexOf(bestScan);
      final double highestIntensity = chrom.getIntensity(highestIndex);
      final double fivePercentIntensity = highestIntensity * 0.05;

      startRts[i] = chrom.getSpectrum(
              findIntensityFromMaximum(chrom, highestIndex, fivePercentIntensity, -1))
          .getRetentionTime();
      endRts[i] = chrom.getSpectrum(
              findIntensityFromMaximum(chrom, highestIndex, fivePercentIntensity, +1))
          .getRetentionTime();
      i++;
    }

    final double peakStart = Quantiles.median().compute(startRts);
    final double peakEnd = Quantiles.median().compute(endRts);
    final Range<Float> rtRange = Range.closed((float) peakStart, (float) peakEnd);

    for (var entry : rangeFeatureMap.entrySet()) {
      final Feature feature = entry.getValue();
      final IsotopeCollection isotopes = new IsotopeCollection(feature.getMZ(),
          new MZTolerance(entry.getKey(), 0d), storage, flist, rtRange);
      isotopes.createChromatograms(access, 5);
      rangeResolvedFeatureMap.put(entry.getKey(), isotopes);
    }
  }

  private int findIntensityFromMaximum(IonTimeSeries<Scan> chrom, int highestIndex,
      double searchIntensity, int direction) {
    for (int i = highestIndex; i > 0 && i < chrom.getNumberOfValues(); i += direction) {
      final double intensity = chrom.getIntensity(i);
      if (intensity <= searchIntensity) {
        return i;
      }
    }
    return highestIndex;
  }

  public void printStatistics() {
    final List<Double> widths = rangeFeatureMap.keySet().stream().sorted().toList();
    final Double mz = bestScan.getBasePeakMz();

    for (Double width : widths) {
      final IsotopeCollection isotopeCollection = rangeResolvedFeatureMap.get(width);
      if (isotopeCollection.getIsotopeFeatures().isEmpty()) {
        continue;
      }
      var resolved = isotopeCollection.getIsotopeFeature(0);
      var unresolved = rangeFeatureMap.get(width);

      logger.info(
          "m/z %.4f, width=%.4f, resolved: area %.3e, m/z dev %.6f, m/z %.4f-%.4f, fwhm: %.2f".formatted(
              mz, width, resolved.getArea(), MathUtils.calcStd(resolved.getFeatureData()
                  .getMzValues(new double[resolved.getNumberOfDataPoints()])),
              resolved.getRawDataPointsMZRange().lowerEndpoint(),
              resolved.getRawDataPointsMZRange().upperEndpoint(), resolved.getFWHM()));
      /*logger.info(
          "m/z %.4f, width=%.4f, unresolv: area %.3e, m/z %.4f-%.6f, fwhm: %.2f".formatted(mz,
              width, unresolved.getArea(), MathUtils.calcStd(unresolved.getFeatureData()
                  .getMzValues(new double[resolved.getNumberOfDataPoints()])),
              unresolved.getRawDataPointsMZRange().lowerEndpoint(),
              unresolved.getRawDataPointsMZRange().upperEndpoint(), unresolved.getFWHM()));*/
    }
  }

  public Map<Double, Feature> getRangeFeatureMap() {
    return rangeFeatureMap;
  }

  public Map<Double, IsotopeCollection> getRangeResolvedFeatureMap() {
    return rangeResolvedFeatureMap;
  }

  public Scan getBestScan() {
    return bestScan;
  }

  public double getMz() {
    return mz;
  }

  @Override
  public String toString() {
    final NumberFormats format = MZmineCore.getConfiguration().getGuiFormats();
    return "m/z " + format.mz(bestScan.getBasePeakMz()) + " " + format.rt(
        bestScan.getRetentionTime()) + " min";
  }

}
