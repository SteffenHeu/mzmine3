/*
 * Copyright (c) 2004-2024 The mzmine Development Team
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

package io.github.mzmine.datamodel.featuredata.impl;

import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.MobilityScan;
import io.github.mzmine.datamodel.featuredata.IntensitySeries;
import io.github.mzmine.datamodel.featuredata.IonMobilitySeries;
import io.github.mzmine.datamodel.featuredata.IonSpectrumSeries;
import io.github.mzmine.util.DataPointUtils;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.collections.CollectionUtils;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stores data points of several {@link MobilityScan}s. Usually wrapped in a
 * {@link SimpleIonMobilogramTimeSeries} representing the same feature with mobility resolution.
 *
 * @author https://github.com/SteffenHeu
 */
public class StorableConsecutiveIonMobilitySeries implements StorableIonMobilitySeries,
    ModifiableSpectra<MobilityScan> {

  private static final Logger logger = Logger.getLogger(
      StorableConsecutiveIonMobilitySeries.class.getName());

  protected final int indexInTrace;
  protected final int scanStartInclusive;
  protected final int scanEndInclusive;
  protected final int storageOffset;
  protected final int numValues;
  protected final SimpleIonMobilogramTimeSeries ionTrace;

  protected StorableConsecutiveIonMobilitySeries(final SimpleIonMobilogramTimeSeries ionTrace,
      final int offset, final int numValues, @NotNull List<MobilityScan> scans) {
    if (numValues != scans.size()) {
      throw new IllegalArgumentException("numPoints and number of scans scans does not match.");
    }

    final Frame frame = scans.get(0).getFrame();
    for (MobilityScan scan : scans) {
      if (frame != scan.getFrame()) {
        throw new IllegalArgumentException("All mobility scans must belong to the same frame.");
      }
    }

    assert CollectionUtils.isConsecutive(scans, MobilityScan::getMobilityScanNumber);

    this.storageOffset = offset;
    this.numValues = numValues;
    this.ionTrace = ionTrace;

    indexInTrace = ionTrace.getSpectra().indexOf(frame);
    scanStartInclusive = frame.getMobilityScans().indexOf(scans.getFirst());
    scanEndInclusive = frame.getMobilityScans().indexOf(scans.getLast());
  }

  @Override
  public double getIntensityForSpectrum(MobilityScan spectrum) {
    int index = getSpectraModifiable().indexOf(spectrum);
    if (index != -1) {
      return getIntensity(index);
    }
    return 0d;
  }

  @Override
  public double getMzForSpectrum(MobilityScan spectrum) {
    int index = getSpectraModifiable().indexOf(spectrum);
    if (index != -1) {
      return getMZ(index);
    }
    return 0d;
  }

  @Override
  public IonMobilitySeries subSeries(@Nullable MemoryMapStorage storage,
      @NotNull List<MobilityScan> subset) {
    double[] mzs = new double[subset.size()];
    double[] intensities = new double[subset.size()];

    for (int i = 0; i < subset.size(); i++) {
      mzs[i] = getMzForSpectrum(subset.get(i));
      intensities[i] = getIntensityForSpectrum(subset.get(i));
    }

    return new SimpleIonMobilitySeries(storage, mzs, intensities, subset);
  }

  @Override
  public double getIntensity(int index) {
    return ionTrace.getMobilogramIntensityValue(this, index);
  }

  @Override
  public double getMZ(int index) {
    return ionTrace.getMobilogramMzValue(this, index);
  }

  @Override
  public MemorySegment getIntensityValueBuffer() {
    return ionTrace.getMobilogramIntensityValues(this);
  }

  @Override
  public MemorySegment getMZValueBuffer() {
    return ionTrace.getMobilogramMzValues(this);
  }

  public double getMobility(int index) {
    return getSpectra().get(index).getMobility();
  }

  @Override
  public List<MobilityScan> getSpectra() {
    return List.copyOf(ionTrace.getSpectra().get(indexInTrace).getMobilityScans()
        .subList(scanStartInclusive, scanEndInclusive + 1));
  }

  @Override
  public IonSpectrumSeries<MobilityScan> copy(@Nullable MemoryMapStorage storage) {
    double[][] data = DataPointUtils.getDataPointsAsDoubleArray(getMZValueBuffer(),
        getIntensityValueBuffer());

    return new SimpleIonMobilitySeries(storage, data[0], data[1], getSpectraModifiable());
  }

  public int getStorageOffset() {
    return storageOffset;
  }

  @Override
  public int getNumberOfValues() {
    return numValues;
  }

  @Override
  public List<MobilityScan> getSpectraModifiable() {
    return ionTrace.getSpectra().get(indexInTrace).getMobilityScans()
        .subList(scanStartInclusive, scanEndInclusive + 1);
  }

  @Override
  public IonSpectrumSeries<MobilityScan> copyAndReplace(@Nullable final MemoryMapStorage storage,
      @NotNull final double[] newIntensityValues) {
    var intensities = StorageUtils.storeValuesToDoubleBuffer(storage, newIntensityValues);
    // reuse mz memory segment
    var mzs = getMZValueBuffer();
    return new SimpleIonMobilitySeries(mzs, intensities, getSpectraModifiable());
  }

  @Override
  public IonSpectrumSeries<MobilityScan> copyAndReplace(@Nullable MemoryMapStorage storage,
      @NotNull double[] newMzValues, @NotNull double[] newIntensityValues) {
    return new SimpleIonMobilitySeries(storage, newMzValues, newIntensityValues,
        getSpectraModifiable());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IonMobilitySeries)) {
      return false;
    }
    IonMobilitySeries that = (IonMobilitySeries) o;
    return numValues == that.getNumberOfValues() && Objects.equals(getSpectra(), that.getSpectra())
        && IntensitySeries.seriesSubsetEqual(this, that);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getSpectraModifiable(), numValues);
  }

  public SimpleIonMobilogramTimeSeries getIonTrace() {
    return ionTrace;
  }

  public int getScanStartInclusive() {
    return scanStartInclusive;
  }

  public int getScanEndInclusive() {
    return scanEndInclusive;
  }
}
