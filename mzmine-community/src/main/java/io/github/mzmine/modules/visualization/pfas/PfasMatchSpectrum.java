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

import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasAnnotation;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasBlockEvidence;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasExplainedSignals;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasExplainedSignals.SubFormulaPeak;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasIonLabels;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasMatchedPeak;
import io.github.mzmine.datamodel.identities.iontype.IonType;
import io.github.mzmine.gui.chartbasics.simplechart.SimpleXYChart;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYDataset;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.RunOption;
import io.github.mzmine.gui.chartbasics.simplechart.providers.PlotXYDataProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.SimpleXYProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.impl.spectra.SingleSpectrumProvider;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYBarRenderer;
import io.github.mzmine.gui.preferences.NumberFormats;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.modules.visualization.spectra.matchedlipid.StyledLipidSpectrumBarRenderer;
import java.awt.Color;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The MS/MS scan and the observed signals explained by one PFAS annotation: rule-matched peaks and,
 * if the matching settings are known, peaks explained by sub-formulas of the precursor ion. The
 * selected peak is the domain value of the {@link #cursorPositionProperty()}.
 */
final class PfasMatchSpectrum extends SimpleXYChart<PlotXYDataProvider> {

  private final @NotNull PfasMatchColors colors;

  /**
   * @param colors dataset colors
   */
  PfasMatchSpectrum(final @NotNull PfasMatchColors colors) {
    super("m/z", "Intensity");
    this.colors = colors;
    final NumberFormats formats = ConfigService.getGuiFormats();
    setDomainAxisNumberFormatOverride(formats.mzFormat());
    setRangeAxisNumberFormatOverride(formats.intensityFormat());
    getXYPlot().getRangeAxis().setUpperMargin(0.18);
    setPrefSize(640, 330);
    setMaxHeight(360);
    setMinWidth(360);
  }

  /**
   * Replaces the plotted datasets and clears the selected peak.
   *
   * @param annotation annotation whose scan and matched peaks are plotted, null to clear the chart
   * @param signals    recomputed signals of this annotation, null while they are computed
   */
  void setAnnotation(final @Nullable PfasAnnotation annotation,
      final @Nullable PfasMatchSignals signals) {
    applyWithNotifyChanges(false, () -> {
      removeAllDatasets();
      setCursorPosition(null);
      if (annotation != null) {
        addDatasets(annotation, signals != null && signals.annotation() == annotation ? signals
            : null);
      }
    });
  }

  /**
   * @param annotation annotation whose scan and matched peaks are plotted
   * @param signals    recomputed signals of this annotation, or null
   */
  private void addDatasets(final @NotNull PfasAnnotation annotation,
      final @Nullable PfasMatchSignals signals) {
    final NumberFormats formats = ConfigService.getGuiFormats();
    final MassSpectrum spectrum = PfasMatchSignals.spectrum(annotation);
    if (spectrum != null) {
      final var provider = new SingleSpectrumProvider(spectrum, "MS/MS spectrum",
          colors.spectrum());
      final ColoredXYBarRenderer renderer = new ColoredXYBarRenderer(false);
      renderer.setDefaultToolTipGenerator(getDefaultToolTipGenerator());
      addDataset(new ColoredXYDataset(provider, RunOption.THIS_THREAD), renderer);
    }

    final IonType ionType = annotation.getAdductType();
    final String ionName = ionType == null ? null : ionType.toString();
    final PfasExplainedSignals subFormulas = signals == null ? null : signals.subFormulas();
    if (subFormulas != null && ionName != null) {
      addSubFormulas(
          subFormulas.subFormulaPeaks().stream().filter(SubFormulaPeak::isSubstructure).toList(),
          "Substructure signals",
          "Substructure of the structure, no rule. Click to highlight it in the structure.",
          colors.substructure(), ionName, formats);
      addSubFormulas(
          subFormulas.subFormulaPeaks().stream().filter(p -> !p.isSubstructure()).toList(),
          "Sub-formula signals", "Sub-formula of the precursor ion, no substructure or rule",
          colors.subFormula(), ionName, formats);
    }

    final List<PfasMatchedPeak> peaks = annotation.getMatchedPeaks();
    if (peaks.isEmpty()) {
      return;
    }
    final boolean withStructure = signals != null && signals.molecule() != null;
    final double[] mzs = peaks.stream().mapToDouble(PfasMatchedPeak::observedMz).toArray();
    final double[] intensities = peaks.stream().mapToDouble(PfasMatchedPeak::intensity).toArray();
    final var provider = new SimpleXYProvider("Rule-based signals", colors.rule(), mzs,
        intensities);
    final var renderer = new StyledLipidSpectrumBarRenderer(false, colors.rule());
    renderer.setDefaultItemLabelGenerator((_, _, index) -> {
      final PfasMatchedPeak peak = peaks.get(index);
      return peak.ionLabel(ionName) + "\n" + origin(annotation, index, peak);
    });
    renderer.setDefaultToolTipGenerator((_, _, index) -> {
      final PfasMatchedPeak peak = peaks.get(index);
      return "%s%n%s%nObserved m/z %s%nExpected m/z %s%n%s%s".formatted(peak.ionLabel(ionName),
          origin(annotation, index, peak), formats.mz(peak.observedMz()),
          formats.mz(peak.expectedMz()), String.join(", ", peak.ruleIds()),
          withStructure ? "%nClick to highlight its substructure.".formatted() : "");
    });
    addDataset(new ColoredXYDataset(provider, RunOption.THIS_THREAD), renderer);
  }

  /**
   * @param annotation annotation with building block evidence
   * @param index      index of the peak in the matched peak list
   * @param peak       matched peak
   * @return building blocks supported by this peak and the evidence level
   */
  private static @NotNull String origin(final @NotNull PfasAnnotation annotation, final int index,
      final @NotNull PfasMatchedPeak peak) {
    final String blocks = annotation.getBuildingBlocks().stream()
        .filter(b -> b.peakIndices().contains(index)).map(PfasBlockEvidence::name)
        .collect(Collectors.joining(", "));
    final String evidence = PfasIonLabels.evidence(peak.evidence());
    return blocks.isEmpty() ? evidence : blocks + " · " + evidence;
  }

  /**
   * Adds sub-formula explained peaks as one labelled dataset.
   *
   * @param peaks       peaks of this dataset, nothing is added if empty
   * @param name        dataset name in the legend
   * @param description tooltip line that explains the signal type
   * @param color       dataset color
   * @param ionName     precursor ion type for the loss notation
   * @param formats     number formats
   */
  private void addSubFormulas(final @NotNull List<SubFormulaPeak> peaks,
      final @NotNull String name, final @NotNull String description, final @NotNull Color color,
      final @NotNull String ionName, final @NotNull NumberFormats formats) {
    if (peaks.isEmpty()) {
      return;
    }
    final var provider = new SimpleXYProvider(name, color,
        peaks.stream().mapToDouble(SubFormulaPeak::mz).toArray(),
        peaks.stream().mapToDouble(SubFormulaPeak::intensity).toArray());
    final var renderer = new StyledLipidSpectrumBarRenderer(false, color);
    renderer.setDefaultItemLabelGenerator((_, _, index) -> fragmentLabel(peaks.get(index)));
    renderer.setDefaultToolTipGenerator((_, _, index) -> {
      final SubFormulaPeak peak = peaks.get(index);
      final String note = peak.substructure() == null ? null
          : PfasAnnotationMatchInteractor.note(peak.substructure(), peak.charge());
      return "%s = %s%nObserved m/z %s%n%s%s".formatted(fragmentLabel(peak),
          PfasIonLabels.loss(ionName, peak.lossFormula()), formats.mz(peak.mz()), description,
          note == null ? "" : "%nSubstructure with %s".formatted(note));
    });
    addDataset(new ColoredXYDataset(provider, RunOption.THIS_THREAD), renderer);
  }

  /**
   * @param peak sub-formula explained peak
   * @return fragment ion formula with charge, e.g. C7F7⁻; only loss rules show the loss notation
   */
  private static @NotNull String fragmentLabel(final @NotNull SubFormulaPeak peak) {
    return PfasIonLabels.ion(peak.fragmentFormula(), peak.charge());
  }
}
