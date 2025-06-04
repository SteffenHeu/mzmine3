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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.tools.tools_autoparam;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.ScanDataType;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.featuredata.impl.BuildingIonSeries;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.modules.dataprocessing.featdet_extract_mz_ranges.ExtractMzRangesIonSeriesFunction;
import io.github.mzmine.taskcontrol.Task;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * @param absMzTolerance absolute mz tolerance for this envelope
 * @param highestPeakMz  the m/z this envelope was created around
 * @param traces         the correlated traces
 * @param pearsonCorr    correlation coefficients for the correlated traces
 */
public record IsotopeEnvelope(double absMzTolerance, double highestPeakMz,
                              List<IonTimeSeries<Scan>> traces, double[] pearsonCorr) {

  public static IsotopeEnvelope of(RawDataFile file, BuildingIsotopeEnvelope envelope, Task parentTask) {
    final ModularFeature mainFeature = envelope.mostIntenseIsotope();
    final IonTimeSeries<? extends Scan> mainPeak = mainFeature.getFeatureData();

    final ExtractMzRangesIonSeriesFunction extraction = new ExtractMzRangesIonSeriesFunction(file,
        mainPeak.getSpectra(), envelope.isotopeMzsSorted(), ScanDataType.MASS_LIST, parentTask);
    final @NotNull BuildingIonSeries[] ionSeries = extraction.get();

    for (BuildingIonSeries isotopeSeries : ionSeries) {

    }
  }
}
