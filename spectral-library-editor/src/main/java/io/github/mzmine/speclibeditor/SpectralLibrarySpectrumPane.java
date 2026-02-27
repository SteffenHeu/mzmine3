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

package io.github.mzmine.speclibeditor;

import io.github.mzmine.gui.chartbasics.simplechart.SimpleXYChart;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYDataset;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.RunOption;
import io.github.mzmine.gui.chartbasics.simplechart.providers.PlotXYDataProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.impl.AnyXYProvider;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYBarRenderer;
import io.github.mzmine.util.spectraldb.entry.DBEntryField;
import io.github.mzmine.util.spectraldb.entry.SpectralLibraryEntry;
import java.awt.Color;
import java.util.List;
import javafx.scene.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Displays the currently selected library entry spectrum in a simple chart.
 */
public final class SpectralLibrarySpectrumPane {

  private static final @NotNull Color SPECTRUM_COLOR = new Color(49, 104, 170);

  private final @NotNull SimpleXYChart<PlotXYDataProvider> chart;

  /**
   * Creates and configures the spectrum chart widget.
   */
  public SpectralLibrarySpectrumPane() {
    chart = new SimpleXYChart<>("Spectrum", "m/z", "Intensity");
    chart.setDefaultRenderer(new ColoredXYBarRenderer(false));
    chart.setLegendItemsVisible(false);
    chart.setItemLabelsVisible(false);
    chart.setStickyZeroRangeAxis(true);
    chart.setMinWidth(420);
    chart.setMinHeight(320);
  }

  /**
   * Returns the JavaFX node hosting the spectrum chart.
   *
   * @return chart node.
   */
  public @NotNull Node getNode() {
    return chart;
  }

  /**
   * Updates the chart to display the selected entry spectrum.
   *
   * @param entry selected entry or {@code null} to clear the chart.
   */
  public void setEntry(@Nullable final SpectralLibraryEntry entry) {
    if (entry == null) {
      chart.removeAllDatasets();
      chart.getChart().setTitle("Spectrum");
      return;
    }

    final AnyXYProvider provider = new AnyXYProvider(SPECTRUM_COLOR, createSeriesKey(entry),
        entry.getNumberOfDataPoints(), entry::getMzValue, entry::getIntensityValue);
    final ColoredXYDataset dataset = new ColoredXYDataset(provider, RunOption.THIS_THREAD);
    chart.setDatasets(List.of(dataset));
    chart.getChart().setTitle(createSeriesKey(entry));
  }

  /**
   * Creates the display label for the currently plotted spectrum.
   *
   * @param entry entry used for the title.
   * @return chart series title text.
   */
  private @NotNull String createSeriesKey(@NotNull final SpectralLibraryEntry entry) {
    final String name = entry.getAsString(DBEntryField.NAME)
        .or(() -> entry.getAsString(DBEntryField.ENTRY_ID)).orElse("Library entry");
    final String precursor = entry.getAsDouble(DBEntryField.PRECURSOR_MZ)
        .map(value -> String.format("%.5f", value)).orElse("n/a");
    return name + " (precursor m/z " + precursor + ")";
  }
}
