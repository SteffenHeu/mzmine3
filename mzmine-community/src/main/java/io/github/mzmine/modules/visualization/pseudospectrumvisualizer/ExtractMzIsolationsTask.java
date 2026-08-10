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

package io.github.mzmine.modules.visualization.pseudospectrumvisualizer;

import io.github.mzmine.datamodel.PseudoSpectrum;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYDataset;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.RunOption;
import io.github.mzmine.gui.chartbasics.simplechart.providers.impl.AnyXYProvider;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYLineRenderer;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.modules.dataprocessing.filter_diams2.rt_corr.DiaMs2RtCorrTask;
import io.github.mzmine.modules.dataprocessing.filter_diams2.sliding_mz.CycleMassograms;
import io.github.mzmine.modules.dataprocessing.filter_diams2.sliding_mz.DiaSlidingMzTask;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.taskcontrol.operations.AbstractTaskSubSupplier;
import io.github.mzmine.util.RangeUtils;
import io.github.mzmine.util.color.SimpleColorPalette;
import io.github.mzmine.util.scans.SpectraMerging;
import it.unimi.dsi.fastutil.doubles.Double2ObjectMap;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class ExtractMzIsolationsTask extends AbstractTaskSubSupplier<List<MzDatasetAndRenderer>> {

  private final Feature feature;
  private final PseudoSpectrum spectrum;
  private final MZTolerance mzTolerance = SpectraMerging.defaultMs2MergeTol;

  public ExtractMzIsolationsTask(@NotNull Feature feature, @NotNull PseudoSpectrum spectrum) {
    this.feature = feature;
    this.spectrum = spectrum;
  }


  @Override
  public @NotNull String getTaskDescription() {
    return "Extracting m/z isolations for feature %s.";
  }

  @Override
  public double getFinishedPercentage() {
    return 0;
  }

  @Override
  public @NotNull List<MzDatasetAndRenderer> get() {

    final boolean valid = switch (spectrum.getPseudoSpectrumType()) {
      case MALDI_IMAGING, GC_EI -> false;
      case SLIDING_MZ_NO_RT, SLIDING_MZ_RT_CORR -> true;
      // option to also show q1 field for rt only or no decon in zt scan files
      case LC_DIA, UNCORRELATED ->
          DiaMs2RtCorrTask.isSlidingQuadWindow(spectrum.getDataFile(), null);
    };
    if (!valid) {
      return List.of();
    }

    final ScanSelection ms2Scans = new ScanSelection(spectrum.getMSLevel(),
        feature.getRawDataPointsRTRange(), feature.getRepresentativePolarity());

    if (!ms2Scans.getMsLevelFilter().accept(2)) {
      // need isolation info below
      return List.of();
    }

    final List<Scan> ms2Cycle = DiaSlidingMzTask.getMs2CycleForRt(feature.getRT(),
        feature.getFeatureData().getSpectra(),
        ms2Scans.getMatchingScans(feature.getRawDataFile().getScans()), null);
    CycleMassograms m = new CycleMassograms(ms2Cycle, FeatureList.createDummy());
//    final double isolationWidth =
//        RangeUtils.rangeLength(fullMs2Cycle.getFirst().getMsMsInfo().getIsolationWindow())
//            * CycleMassograms.isolationWidthFactor * 3; // dont divide by 2 to capture a bit more
//    List<Scan> ms2Cycle = fullMs2Cycle.stream().filter(scan -> {
//      Double isolationCenter = RangeUtils.rangeCenter(scan.getMsMsInfo().getIsolationWindow());
//      return RangeUtils.rangeAround(isolationCenter, isolationWidth).contains(feature.getMZ());
//    }).toList();

   /* List<Range<Double>> mzRanges = new ArrayList<>();
    for (int i = 0; i < spectrum.getNumberOfDataPoints(); i++) {
      mzRanges.add(mzTolerance.getToleranceRange(spectrum.getMzValue(i)));
    }

    final ExtractMzRangesIonSeriesFunction extract = new ExtractMzRangesIonSeriesFunction(
        feature.getRawDataFile(), ms2Cycle, mzRanges, ScanDataType.MASS_LIST, null);
    @NotNull BuildingIonSeries[] buildingIonSeries = extract.get();
    final List<? extends IonTimeSeries<? extends Scan>> extracted = Arrays.stream(buildingIonSeries)
        .map(series -> series.toFullIonTimeSeries(null, ms2Cycle)).toList();*/

    double[] mzs = new double[spectrum.getNumberOfDataPoints()];
    for (int i = 0; i < spectrum.getNumberOfDataPoints(); i++) {
      mzs[i] = spectrum.getMzValue(i);
    }
    Double2ObjectMap<ModularFeature> traces = m.getTraces(mzs, mzTolerance, null);

    final List<MzDatasetAndRenderer> datasets = new ArrayList<>(traces.size());
    SimpleColorPalette palette = ConfigService.getDefaultColorPalette().clone(true);
    for (var feature : traces.values()) {
      IonTimeSeries<? extends Scan> series = feature.getFeatureData();
      AnyXYProvider provider = new AnyXYProvider(palette.getNextColorAWT(),
          ConfigService.getGuiFormats().mz(feature.getMZ()), series.getNumberOfValues(),
          j -> RangeUtils.rangeCenter(series.getSpectrum(j).getMsMsInfo().getIsolationWindow())
              .doubleValue(), j -> series.getIntensity(j));
      ColoredXYLineRenderer renderer = new ColoredXYLineRenderer();
      renderer.setDefaultItemLabelsVisible(true);
      datasets.add(new MzDatasetAndRenderer(feature.getMZ(),
          new ColoredXYDataset(provider, RunOption.THIS_THREAD), renderer));
    }

    return datasets;
  }
}
