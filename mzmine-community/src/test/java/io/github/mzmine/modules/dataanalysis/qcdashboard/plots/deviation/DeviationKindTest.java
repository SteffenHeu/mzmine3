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

package io.github.mzmine.modules.dataanalysis.qcdashboard.plots.deviation;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.impl.SimpleScan;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DeviationKindTest {

  @Test
  void rtDeviationUsesRepresentativeScanRetentionTimes() {
    final RawDataFile file = Mockito.mock(RawDataFile.class);
    final FeatureListRow row = Mockito.mock(FeatureListRow.class);
    final Feature feature = Mockito.mock(Feature.class);
    final SimpleScan scan = Mockito.mock(SimpleScan.class);
    Mockito.when(row.getFeature(file)).thenReturn(feature);
    Mockito.when(row.getAverageRT()).thenReturn(10f);
    Mockito.when(feature.getRepresentativeScan()).thenReturn(scan);
    Mockito.when(feature.getRT()).thenReturn(100f);
    Mockito.when(scan.getUncorrectedRetentionTime()).thenReturn(12f);
    Mockito.when(scan.getCorrectedRetentionTime()).thenReturn(11f);

    Assertions.assertEquals(2d, DeviationKind.RT.deviation(row, file));
    Assertions.assertEquals(1d, DeviationKind.RT.correctedDeviation(row, file));
  }

  @Test
  void correctedRtDeviationIsMissingWithoutCalibration() {
    final RawDataFile file = Mockito.mock(RawDataFile.class);
    final FeatureListRow row = Mockito.mock(FeatureListRow.class);
    final Feature feature = Mockito.mock(Feature.class);
    final SimpleScan scan = Mockito.mock(SimpleScan.class);
    Mockito.when(row.getFeature(file)).thenReturn(feature);
    Mockito.when(row.getAverageRT()).thenReturn(10f);
    Mockito.when(feature.getRepresentativeScan()).thenReturn(scan);
    Mockito.when(scan.getCorrectedRetentionTime()).thenReturn(null);

    Assertions.assertTrue(Double.isNaN(DeviationKind.RT.correctedDeviation(row, file)));
  }
}
