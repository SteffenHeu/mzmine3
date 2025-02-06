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

import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.MobilityScan;
import io.github.mzmine.datamodel.featuredata.IonMobilitySeries;
import io.github.mzmine.util.MemoryMapStorage;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout.OfInt;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.stream.IntStream;

public class MappedStoredMobilograms implements StoredMobilograms {

  private final SimpleIonMobilogramTimeSeries ionTrace;
  private final MemorySegment mzValuesSegment;
  private final MemorySegment intensityValuesSegment;

  private final MemoryLayout mobilogramInfoLayout = MemoryLayout.structLayout(
      OfInt.JAVA_INT.withName("numValues"), OfInt.JAVA_INT.withName("storageOffset"),
      OfInt.JAVA_INT.withName("mobilityScanIndicesStart"),
      OfInt.JAVA_INT.withName("mobilityScanIndicesEnd"));
  private final VarHandle numValues = mobilogramInfoLayout.varHandle(
      PathElement.groupElement("numValues"));
  private final VarHandle storageOffset = mobilogramInfoLayout.varHandle(
      PathElement.groupElement("storageOffset"));
  private final VarHandle scanIndicesStart = mobilogramInfoLayout.varHandle(
      PathElement.groupElement("mobilityScanIndicesStart"));
  private final VarHandle scanIndicesEnd = mobilogramInfoLayout.varHandle(
      PathElement.groupElement("mobilityScanIndicesEnd"));

  private final MemorySegment mobilogramData;
  private final MemorySegment mobilogramScanIndices;

  public MappedStoredMobilograms(MemoryMapStorage storage, SimpleIonMobilogramTimeSeries ionTrace,
      MemorySegment mzValuesSegment, MemorySegment intensityValuesSegment, int[] storageOffsets,
      List<StorableIonMobilitySeries> storedMobilograms) {
    this.ionTrace = ionTrace;
    this.mzValuesSegment = mzValuesSegment;
    this.intensityValuesSegment = intensityValuesSegment;
    mobilogramData = StorageUtils.allocateSegment(null, mobilogramInfoLayout,
        storedMobilograms.size());

    final int numMobilityScanIndices = storedMobilograms.stream()
        .mapToInt(IonMobilitySeries::getNumberOfValues).sum();
    mobilogramScanIndices = StorageUtils.allocateSegment(null, OfInt.JAVA_INT,
        numMobilityScanIndices);
    int scanIndicesOffset = 0;

    for (int i = 0; i < storedMobilograms.size(); i++) {
      final StorableIonMobilitySeries mobilogram = storedMobilograms.get(i);
      final long offset = i * mobilogramInfoLayout.byteSize();
      numValues.set(mobilogramData, offset, mobilogram.getNumberOfValues());
      storageOffset.set(mobilogramData, offset, storageOffsets[i]);
      scanIndicesStart.set(mobilogramData, offset, scanIndicesOffset);

      final List<MobilityScan> scans = mobilogram.getSpectra();
      for (MobilityScan scan : scans) {
        mobilogramScanIndices.setAtIndex(OfInt.JAVA_INT, scanIndicesOffset++,
            scan.getMobilityScanNumber());
      }

      scanIndicesEnd.set(mobilogramData, offset, scanIndicesOffset);
    }
  }

  @Override
  public List<IonMobilitySeries> storedMobilograms() {
    return IntStream.range(0, ionTrace.getNumberOfValues()).mapToObj(this::mobilogram).toList();
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
  public IonMobilitySeries mobilogram(int index) {
    final long offset = mobilogramInfoLayout.byteSize() * index;

    final int num = (int) numValues.get(mobilogramData, offset);
    final int storage = (int) storageOffset.get(mobilogramData, offset);
    final int startIndex = (int) scanIndicesStart.get(mobilogramData, offset);
    final int endIndex = (int) scanIndicesEnd.get(mobilogramData, offset);

    final Frame frame = ionTrace.getSpectrum(index);
    final List<MobilityScan> mobilityScans = IntStream.range(startIndex, endIndex)
        .map(i -> mobilogramScanIndices.getAtIndex(OfInt.JAVA_INT, i))
        .mapToObj(frame::getMobilityScan).toList();

    return new StorableComplexIonMobilitySeries(ionTrace, storage, num, mobilityScans);
  }
}
