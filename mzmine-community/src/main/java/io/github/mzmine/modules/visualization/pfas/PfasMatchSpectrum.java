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

package io.github.mzmine.modules.visualization.pfas;

import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasAnnotation;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasMatchedPeak;
import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.gui.chartbasics.simplechart.SimpleXYChart;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYDataset;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.RunOption;
import io.github.mzmine.gui.chartbasics.simplechart.providers.PlotXYDataProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.SimpleXYProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.impl.spectra.SingleSpectrumProvider;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYBarRenderer;
import io.github.mzmine.gui.preferences.NumberFormats;
import io.github.mzmine.javafx.util.FxColorUtil;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.modules.visualization.spectra.matchedlipid.StyledLipidSpectrumBarRenderer;
import io.github.mzmine.util.color.SimpleColorPalette;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/**
 * The MS/MS scan and the observed signals explained by one PFAS annotation.
 */
final class PfasMatchSpectrum extends SimpleXYChart<PlotXYDataProvider> {

  /**
   * @param annotation annotation whose scan and matched peaks should be plotted
   */
  PfasMatchSpectrum(final @NotNull PfasAnnotation annotation) {
    super("m/z", "Intensity");
    final NumberFormats formats = ConfigService.getGuiFormats();
    final SimpleColorPalette palette = ConfigService.getConfiguration().getDefaultColorPalette();
    setDomainAxisNumberFormatOverride(formats.mzFormat());
    setRangeAxisNumberFormatOverride(formats.intensityFormat());
    getXYPlot().getRangeAxis().setUpperMargin(0.18);
    setPrefSize(640, 330);
    setMaxHeight(360);
    setMinWidth(360);

    final Scan scan = annotation.getScan();
    if (scan != null) {
      // decision: the annotation matches the centroided mass list when one is available.
      final MassSpectrum spectrum = scan.getMassList() == null ? scan : scan.getMassList();
      final var provider = new SingleSpectrumProvider(spectrum, "MS/MS spectrum",
          palette.getNeutralColor());
      final ColoredXYBarRenderer renderer = new ColoredXYBarRenderer(false);
      renderer.setDefaultToolTipGenerator(getDefaultToolTipGenerator());
      addDataset(new ColoredXYDataset(provider, RunOption.THIS_THREAD), renderer);
    }

    final List<PfasMatchedPeak> peaks = annotation.getMatchedPeaks();
    if (!peaks.isEmpty()) {
      final var color = FxColorUtil.fxColorToAWT(palette.getPositiveColor());
      final double[] mzs = peaks.stream().mapToDouble(PfasMatchedPeak::observedMz).toArray();
      final double[] intensities = peaks.stream().mapToDouble(PfasMatchedPeak::intensity).toArray();
      final var provider = new SimpleXYProvider("Explained signals", color, mzs, intensities);
      final var renderer = new StyledLipidSpectrumBarRenderer(false, color);
      renderer.setDefaultItemLabelGenerator((_, _, index) -> peaks.get(index).ionLabel());
      renderer.setDefaultToolTipGenerator((_, _, index) -> {
        final PfasMatchedPeak peak = peaks.get(index);
        return "%s%nObserved m/z %s%nExpected m/z %s%n%s evidence%n%s".formatted(peak.ionLabel(),
            formats.mz(peak.observedMz()), formats.mz(peak.expectedMz()),
            peak.evidence().name().toLowerCase(Locale.ROOT).replace('_', ' '),
            String.join(", ", peak.ruleIds()));
      });
      addDataset(new ColoredXYDataset(provider, RunOption.THIS_THREAD), renderer);
    }
  }
}
