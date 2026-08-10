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

import io.github.mzmine.gui.chartbasics.simplechart.SimpleXYChart;
import io.github.mzmine.gui.chartbasics.simplechart.providers.impl.AnyXYProvider;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYLineRenderer;
import io.github.mzmine.javafx.components.factories.FxSplitPanes;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.modules.visualization.chromatogram.TICPlot;
import io.github.mzmine.modules.visualization.spectra.simplespectra.SpectraPlot;
import io.github.mzmine.modules.visualization.spectra.simplespectra.SpectraVisualizerTab;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import java.awt.BasicStroke;
import java.util.Comparator;
import java.util.List;
import javafx.beans.binding.Bindings;
import javafx.geometry.Orientation;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Region;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.renderer.xy.XYItemRenderer;

public class PseudoSpectrumVisualizerViewBuilder extends
    FxViewBuilder<PseudoSpectrumVisualizerModel> {

  public PseudoSpectrumVisualizerViewBuilder(@NotNull PseudoSpectrumVisualizerModel model) {
    super(model);
  }

  @Override
  public @NotNull Region build() {
    final TICPlot ticPlot = new TICPlot();
    ticPlot.minHeight(200);
    final SpectraVisualizerTab spectraVisualizerTab = new SpectraVisualizerTab(null);
    final SpectraPlot spectraPlot = spectraVisualizerTab.getSpectrumPlot();
    spectraPlot.minHeight(150);

    final SimpleXYChart<AnyXYProvider> mzCorrelation = new SimpleXYChart<>("Quadrupole m/z",
        ConfigService.getGuiFormats().unit("Intensity", "a.u."));
    mzCorrelation.setRangeAxisNumberFormatOverride(ConfigService.getGuiFormats().intensityFormat());
    mzCorrelation.setDomainAxisNumberFormatOverride(ConfigService.getGuiFormats().mzFormat());
    mzCorrelation.setMinHeight(150);
    mzCorrelation.visibleProperty().bind(Bindings.createBooleanBinding(
        () -> model.mzDatasetsProperty().get() != null && !model.mzDatasetsProperty().get()
            .isEmpty(), model.mzDatasetsProperty()));
    mzCorrelation.managedProperty().bind(mzCorrelation.visibleProperty());
    mzCorrelation.setLegendItemsVisible(false);

    model.pseudoSpecProperty().subscribe((_, spec) -> {
      spectraVisualizerTab.loadRawData(spec);
    });

    spectraPlot.getXYPlot().cursorPositionProperty().subscribe(cursorPosition -> {
      if (cursorPosition != null) {
        model.setSelectedMz(cursorPosition.getDomainValue());
      }
    });

    model.ticDatasetsProperty()
        .subscribe((_, datasets) -> updateTicPlot(ticPlot, datasets, model.getSelectedMz()));

    model.mzDatasetsProperty()
        .subscribe((_, datasets) -> updateMzPlot(mzCorrelation, datasets, model.getSelectedMz()));

    model.selectedMzProperty().subscribe((old, selectedMz) -> {
      updateTicPlot(ticPlot, model.getTicDatasets(), selectedMz);
      updateMzPlot(mzCorrelation, model.getMzDatasets(), selectedMz);
    });

    final SplitPane splitPane = FxSplitPanes.newSplitPane(0.5, Orientation.VERTICAL, spectraPlot,
        ticPlot, mzCorrelation);

    model.mzDatasetsProperty().subscribe((_, ds) -> {
      if (ds != null && !ds.isEmpty()) {
        splitPane.setDividerPositions(0.33, 0.66);
      } else {
        splitPane.setDividerPositions(0.5, 1);
      }
    });

    return splitPane;
  }

  private void updateTicPlot(@NotNull final TICPlot ticPlot,
      @Nullable final List<MzDatasetAndRenderer> datasets, @Nullable final Double selectedPeakMz) {
    ticPlot.applyWithNotifyChanges(false, () -> {
      ticPlot.setLegendVisible(false);
      ticPlot.removeAllDataSets();
      if (datasets == null) {
        return;
      }

      final MzDatasetAndRenderer selected = findMatchingDataset(datasets, selectedPeakMz);
      ticPlot.addDataSetAndRenderers(datasets.stream().filter(ds -> !ds.equals(selected))
          .map(MzDatasetAndRenderer::datasetAndRenderer).toList());
      if (selected != null) {
        ticPlot.addDataSetAndRenderer(selected.dataset(), createHighlightRenderer(), false);
      }
    });
  }

  private void updateMzPlot(@NotNull final SimpleXYChart<AnyXYProvider> mzCorrelation,
      @Nullable final List<MzDatasetAndRenderer> datasets, @Nullable final Double selectedPeakMz) {
    mzCorrelation.applyWithNotifyChanges(false, () -> {
      mzCorrelation.removeAllDatasets();
      mzCorrelation.getXYPlot().clearDomainMarkers();
      if (datasets == null) {
        return;
      }

      final MzDatasetAndRenderer selected = findMatchingDataset(datasets, selectedPeakMz);
      for (final MzDatasetAndRenderer dataset : datasets) {
        if (dataset.equals(selected)) {
          mzCorrelation.getXYPlot().addDataset(selected.dataset(), createHighlightRenderer());
          continue;
        }
        mzCorrelation.addDataset(dataset.dataset(), dataset.renderer());
      }

      if (model.getSelectedRow() != null) {
        mzCorrelation.addDomainMarker(model.getSelectedRow().getAverageMZ(),
            ConfigService.getDefaultColorPalette().getNeutralColorAWT(), 1f);
      }
    });
  }

  private @Nullable MzDatasetAndRenderer findMatchingDataset(
      @NotNull final List<MzDatasetAndRenderer> datasets, @Nullable final Double selectedMz) {
    if (selectedMz == null) {
      return null;
    }

    final MZTolerance tolerance = model.getMzTolerance() != null ? model.getMzTolerance()
        : MZTolerance.FIFTEEN_PPM_OR_FIVE_MDA;
    return datasets.stream()
        .filter(dataset -> tolerance.checkWithinTolerance(selectedMz, dataset.mz()))
        .min(Comparator.comparingDouble(dataset -> Math.abs(selectedMz - dataset.mz())))
        .orElse(null);
  }

  private @NotNull XYItemRenderer createHighlightRenderer() {
    final ColoredXYLineRenderer renderer = new ColoredXYLineRenderer();
    renderer.setSeriesStroke(0, new BasicStroke(4f));
    renderer.setSeriesVisibleInLegend(0, false);
    renderer.setDefaultItemLabelsVisible(false);
    return renderer;
  }
}
