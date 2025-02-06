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

package io.github.mzmine.datamodel.featuredata.impl;

import io.github.mzmine.datamodel.featuredata.IonMobilitySeries;
import io.github.mzmine.util.collections.CollectionUtils;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

public class RectangularMobilogramStorageResult implements StoredMobilograms {

  private final SimpleIonMobilogramTimeSeries ionTrace;
  private final MemorySegment mzValuesSegment;
  private final MemorySegment intensityValuesSegment;
  private final int[] storageOffsets;
  private final int scanStartInclusive;
  private final int scanEndInclusive;

  public RectangularMobilogramStorageResult(
      List<StorableConsecutiveIonMobilitySeries> storedMobilograms,
      SimpleIonMobilogramTimeSeries ionTrace, MemorySegment mzValuesSegment,
      MemorySegment intensityValuesSegment) {
    this.ionTrace = ionTrace;
    this.mzValuesSegment = mzValuesSegment;
    this.intensityValuesSegment = intensityValuesSegment;

    if (!storedMobilograms.stream()
        .allMatch(StorableConsecutiveIonMobilitySeries.class::isInstance)) {
      throw new IllegalStateException("Not all mobilograms are consecutive.");
    }

    // only assertions here as this is checked before
    assert ionTrace.getSpectra().size() == storedMobilograms.size();
    assert CollectionUtils.areAllEqual(storedMobilograms,
        StorableConsecutiveIonMobilitySeries::getScanStartInclusive);
    assert CollectionUtils.areAllEqual(storedMobilograms,
        StorableConsecutiveIonMobilitySeries::getScanEndInclusive);

    scanStartInclusive = storedMobilograms.getFirst().getScanStartInclusive();
    scanEndInclusive = storedMobilograms.getFirst().getScanEndInclusive();

    storageOffsets = storedMobilograms.stream()
        .mapToInt(StorableConsecutiveIonMobilitySeries::getStorageOffset).toArray();
  }

  @Override
  public List<IonMobilitySeries> storedMobilograms() {
    final List<IonMobilitySeries> list = new ArrayList<>();
    for (int i = 0; i < storageOffsets.length; i++) {
      list.add(mobilogram(i));
    }
    return list;
  }

  @Override
  public MemorySegment storedMzValues() {
    return mzValuesSegment;
  }

  @Override
  public MemorySegment storedIntensityValues() {
    return intensityValuesSegment;
  }

  @Override
  public StorableIonMobilitySeries mobilogram(int index) {
    final int numValues;
    if (index <= storageOffsets.length - 2) {
      numValues = storageOffsets[index + 1] - storageOffsets[index];
    } else {
      numValues = (int) (StorageUtils.numDoubles(mzValuesSegment) - storageOffsets[index]);
    }
    return new StorableConsecutiveIonMobilitySeries(ionTrace, storageOffsets[index], numValues,
        List.copyOf(ionTrace.getSpectrum(index).getMobilityScans()
            .subList(scanStartInclusive, scanEndInclusive + 1)));
  }
}
