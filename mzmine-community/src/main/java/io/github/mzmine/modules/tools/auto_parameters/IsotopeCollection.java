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
import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.data_access.CachedFeatureDataAccess;
import io.github.mzmine.datamodel.data_access.ScanDataAccess;
import io.github.mzmine.datamodel.featuredata.IonTimeSeriesUtils;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.correlation.CorrelationData;
import io.github.mzmine.modules.dataprocessing.group_metacorrelate.correlation.FeatureCorrelationUtil;
import io.github.mzmine.modules.tools.isotopeprediction.IsotopePatternCalculator;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.util.MemoryMapStorage;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IsotopeCollection {

  private final double mz;
  private final MZTolerance tol;
  private final MemoryMapStorage storage;
  private final ModularFeatureList flist;
  private final List<Feature> isotopeFeatures;
  private final Range<Float> rtRange;

  public IsotopeCollection(double mz, MZTolerance tol, @Nullable MemoryMapStorage storage,
      @NotNull ModularFeatureList flist, Range<Float> rtRange) {
    this.mz = mz;
    this.tol = tol;
    this.storage = storage;
    this.flist = flist;
    this.isotopeFeatures = new ArrayList<>();
    this.rtRange = rtRange;
  }

  public void createChromatograms(@NotNull ScanDataAccess access, int maxIsotopes) {

    CachedFeatureDataAccess featureDataAccess = new CachedFeatureDataAccess();
    for (int i = 0; i < maxIsotopes; i++) {
      var chrom = IonTimeSeriesUtils.extractIonTimeSeries(access,
          tol.getToleranceRange(mz + i * IsotopePatternCalculator.THIRTHEEN_C_DISTANCE), rtRange, storage);
      final Feature f = new ModularFeature(flist, access.getDataFile(), chrom,
          FeatureStatus.DETECTED);
      if (f.getArea() == 0d) {
        break;
      }

      if (isotopeFeatures.size() >= 1) {
        final CorrelationData correlationData = FeatureCorrelationUtil.corrFeatureShape(
            featureDataAccess, isotopeFeatures.get(0), f, true, f.getNumberOfDataPoints(),
            f.getNumberOfDataPoints() / 3, 0d);
        if (correlationData == null || !correlationData.isValid()
            || correlationData.getPearsonR() < 0.90) {
          continue;
        }
      }

      isotopeFeatures.add(f);
    }
  }

  @NotNull
  public List<Feature> getIsotopeFeatures() {
    return isotopeFeatures;
  }

  public Feature getIsotopeFeature(int index) {
    return isotopeFeatures.get(index);
  }

  public double getAbsTolerance() {
    return tol.getMzTolerance();
  }
}
