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

import com.google.common.collect.Range;
import com.google.common.collect.TreeRangeMap;
import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.ScanDataType;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.featuredata.impl.BuildingIonSeries;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.impl.masslist.SimpleMassList;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.modules.dataprocessing.featdet_extract_mz_ranges.ExtractMzRangesIonSeriesFunction;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.auto.AutoMassDetector;
import io.github.mzmine.modules.impl.TaskPerRawDataFileModule;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelection;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.submodules.ParameterSetParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.taskcontrol.AbstractRawDataFileTask;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.RangeUtils;
import io.github.mzmine.util.collections.BinarySearch;
import io.github.mzmine.util.collections.BinarySearch.DefaultTo;
import io.github.mzmine.util.scans.SpectraMerging;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.collections.ObservableList;
import javax.validation.constraints.Null;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AutoParamTask extends AbstractRawDataFileTask {

  RawDataFile file;
  MemoryMapStorage massListStorage;

  /**
   * @param storage        The {@link MemoryMapStorage} used to store results of this task (e.g.
   *                       RawDataFiles, MassLists, FeatureLists). May be null if results shall be
   *                       stored in ram. For now, one storage should be created per module call in
   * @param moduleCallDate the call date of module to order execution order
   * @param parameters
   * @param moduleClass
   */
  protected AutoParamTask(@Nullable MemoryMapStorage storage, @NotNull Instant moduleCallDate,
      @NotNull ParameterSet parameters, @NotNull Class<? extends MZmineModule> moduleClass) {
    super(storage, moduleCallDate, parameters, moduleClass);
  }

  @Override
  protected @NotNull List<RawDataFile> getProcessedDataFiles() {
    return List.of();
  }

  @Override
  protected void process() {
    final ScanSelection scanSelection = new ScanSelection(1);
    final List<Scan> scans = scanSelection.getMatchingScans(file.getScans());

    final double[] basePeakMzs = getMainPeakMzs(scans);
    final List<Range<Double>> mzRangesSorted = Arrays.stream(basePeakMzs).sorted()
        .mapToObj(mz -> RangeUtils.rangeAround(mz, 1d)).toList();

    final List<ModularFeature> mainFeatures = getMainSignalFeatures(scans, mzRangesSorted);

    for (ModularFeature mainPeak : mainFeatures) {

    }
  }

  /**
   *
   * @param scans The scans to search in
   * @param mzRangesSorted the mz ranges to search in
   * @return A list of features of the given mz ranges capped at 5% of the maximum intensity
   */
  private @NotNull List<ModularFeature> getMainSignalFeatures(List<Scan> scans,
      List<Range<Double>> mzRangesSorted) {
    applyZeroIntensityMassDetection(scans);
    final List<ModularFeature> mainPeaks = new ArrayList<>();

    final ExtractMzRangesIonSeriesFunction eicExtraction = new ExtractMzRangesIonSeriesFunction(
        file, scans, mzRangesSorted, ScanDataType.MASS_LIST, this);
    final @NotNull BuildingIonSeries[] ionSeries = eicExtraction.get();
    final ModularFeatureList dummyFlist = FeatureList.createDummy();

    for (BuildingIonSeries buildingSeries : ionSeries) {
      final IonTimeSeries<? extends Scan> fullChromatogram = buildingSeries.toIonTimeSeriesWithLeadingAndTrailingZero(
          getMemoryMapStorage(), scans);
      final ModularFeature fullFeature = new ModularFeature(dummyFlist, file, fullChromatogram,
          FeatureStatus.DETECTED);

      final float rt = fullFeature.getRT();
      final int mostIntenseIndex = BinarySearch.binarySearch(rt, DefaultTo.CLOSEST_VALUE, 0,
          fullChromatogram.getNumberOfValues(),
          i -> fullChromatogram.getSpectrum(i).getRetentionTime());
      final float highest = fullFeature.getHeight();
      final float edgeIntensity = highest * 0.05f;

      int i = mostIntenseIndex;
      while (i < fullChromatogram.getNumberOfValues()
          && fullChromatogram.getIntensity(i) > edgeIntensity) {
        i++;
      }
      final int rightEdge = i;
      i = mostIntenseIndex;
      while (i > 0 && fullChromatogram.getIntensity(i) > edgeIntensity) {
        i--;
      }
      final int leftEdge = i;

      final IonTimeSeries<? extends Scan> peak = fullChromatogram.subSeries(getMemoryMapStorage(),
          leftEdge, rightEdge);
      final ModularFeature mainFeature = new ModularFeature(dummyFlist, file, peak,
          FeatureStatus.DETECTED);

      // dont use features with long peaks that may just be chromatographic noise
      if (RangeUtils.rangeLength(mainFeature.getRawDataPointsRTRange())
          > RangeUtils.rangeLength(file.getDataRTRange()) * 0.1) {
        continue;
      }
      mainPeaks.add(mainFeature);
    }
    return mainPeaks;
  }

  private void applyZeroIntensityMassDetection(List<Scan> scans) {
    final AutoMassDetector detector = new AutoMassDetector(0d);
    for (Scan scan : scans) {
      scan.addMassList(new SimpleMassList(massListStorage, detector.getMassValues(scan)));
    }
  }

  private static double[] getMainPeakMzs(List<Scan> scans) {
    final MZTolerance oneMzTolerance = new MZTolerance(1, 0);
    final TreeRangeMap<Double, Scan> mzScanMap = TreeRangeMap.create();
    final List<Scan> intensitySortedScans = scans.stream().filter(s -> s.getBasePeakMz() != null)
        .sorted(Comparator.comparingDouble(Scan::getBasePeakMz).reversed()).toList();

    for (Scan scan : intensitySortedScans) {
      final double mz = scan.getBasePeakMz();
      final Scan entry = mzScanMap.get(mz);
      if (entry == null) {
        final Range<Double> range = SpectraMerging.createNewNonOverlappingRange(mzScanMap,
            oneMzTolerance.getToleranceRange(mz));
        mzScanMap.put(range, scan);
      }
    }

    final double[] basePeakMzs = mzScanMap.asMapOfRanges().entrySet().stream().map(Entry::getValue)
        .mapToDouble(Scan::getBasePeakMz).toArray();
    return basePeakMzs;
  }

  @Override
  public String getTaskDescription() {
    return "";
  }
}
