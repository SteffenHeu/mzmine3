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
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasAnnotation;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasBlockEvidence;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasExplainedSignals;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasExplainedSignals.SubFormulaPeak;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasIonLabels;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasMatchSettings;
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
import io.github.mzmine.javafx.util.FxColorUtil;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.modules.visualization.spectra.matchedlipid.StyledLipidSpectrumBarRenderer;
import io.github.mzmine.util.FormulaUtils;
import io.github.mzmine.util.color.SimpleColorPalette;
import java.awt.Color;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openscience.cdk.interfaces.IMolecularFormula;

/**
 * The MS/MS scan and the observed signals explained by one PFAS annotation: rule-matched peaks and,
 * if the matching settings are known, peaks explained by sub-formulas of the precursor ion.
 */
final class PfasMatchSpectrum extends SimpleXYChart<PlotXYDataProvider> {

  /**
   * @param annotation  annotation whose scan and matched peaks should be plotted
   * @param subFormulas sub-formula explained peaks, or null if they cannot be rebuilt
   */
  PfasMatchSpectrum(final @NotNull PfasAnnotation annotation,
      final @Nullable PfasExplainedSignals subFormulas) {
    super("m/z", "Intensity");
    final NumberFormats formats = ConfigService.getGuiFormats();
    final SimpleColorPalette palette = ConfigService.getConfiguration().getDefaultColorPalette();
    setDomainAxisNumberFormatOverride(formats.mzFormat());
    setRangeAxisNumberFormatOverride(formats.intensityFormat());
    getXYPlot().getRangeAxis().setUpperMargin(0.18);
    setPrefSize(640, 330);
    setMaxHeight(360);
    setMinWidth(360);

    final MassSpectrum spectrum = spectrum(annotation);
    if (spectrum != null) {
      final var provider = new SingleSpectrumProvider(spectrum, "MS/MS spectrum",
          palette.getNeutralColor());
      final ColoredXYBarRenderer renderer = new ColoredXYBarRenderer(false);
      renderer.setDefaultToolTipGenerator(getDefaultToolTipGenerator());
      addDataset(new ColoredXYDataset(provider, RunOption.THIS_THREAD), renderer);
    }

    final IonType ionType = annotation.getAdductType();
    final String ionName = ionType == null ? null : ionType.toString();
    if (subFormulas != null && !subFormulas.subFormulaPeaks().isEmpty() && ionName != null) {
      final List<SubFormulaPeak> peaks = subFormulas.subFormulaPeaks();
      final Color color = subFormulaColor(palette);
      final var provider = new SimpleXYProvider("Sub-formula signals", color,
          peaks.stream().mapToDouble(SubFormulaPeak::mz).toArray(),
          peaks.stream().mapToDouble(SubFormulaPeak::intensity).toArray());
      final var renderer = new StyledLipidSpectrumBarRenderer(false, color);
      renderer.setDefaultItemLabelGenerator(
          (_, _, index) -> PfasIonLabels.loss(ionName, peaks.get(index).lossFormula()));
      renderer.setDefaultToolTipGenerator((_, _, index) -> {
        final SubFormulaPeak peak = peaks.get(index);
        return "%s%nObserved m/z %s%nSub-formula of the precursor ion, no rule".formatted(
            PfasIonLabels.loss(ionName, peak.lossFormula()), formats.mz(peak.mz()));
      });
      addDataset(new ColoredXYDataset(provider, RunOption.THIS_THREAD), renderer);
    }

    final List<PfasMatchedPeak> peaks = annotation.getMatchedPeaks();
    if (!peaks.isEmpty()) {
      final var color = FxColorUtil.fxColorToAWT(palette.getPositiveColor());
      final double[] mzs = peaks.stream().mapToDouble(PfasMatchedPeak::observedMz).toArray();
      final double[] intensities = peaks.stream().mapToDouble(PfasMatchedPeak::intensity).toArray();
      final var provider = new SimpleXYProvider("Rule-based signals", color, mzs, intensities);
      final var renderer = new StyledLipidSpectrumBarRenderer(false, color);
      renderer.setDefaultItemLabelGenerator((_, _, index) -> {
        final PfasMatchedPeak peak = peaks.get(index);
        return peak.ionLabel(ionName) + "\n" + origin(annotation, index, peak);
      });
      renderer.setDefaultToolTipGenerator((_, _, index) -> {
        final PfasMatchedPeak peak = peaks.get(index);
        return "%s%n%s%nObserved m/z %s%nExpected m/z %s%n%s".formatted(peak.ionLabel(ionName),
            origin(annotation, index, peak), formats.mz(peak.observedMz()),
            formats.mz(peak.expectedMz()), String.join(", ", peak.ruleIds()));
      });
      addDataset(new ColoredXYDataset(provider, RunOption.THIS_THREAD), renderer);
    }
  }

  /**
   * @param annotation annotation with an optional scan
   * @return the centroided mass list of the scan if available, else the scan, or null
   */
  private static @Nullable MassSpectrum spectrum(final @NotNull PfasAnnotation annotation) {
    final Scan scan = annotation.getScan();
    // decision: the annotation matches the centroided mass list when one is available.
    return scan == null ? null : scan.getMassList() == null ? scan : scan.getMassList();
  }

  /**
   * Rebuilds the sub-formula explained peaks with the settings of the annotation run.
   *
   * @param annotation annotation with scan, formula and ion type
   * @param settings   matching settings of the annotation run, or null if unknown
   * @return explained signals, or null if they cannot be rebuilt
   */
  static @Nullable PfasExplainedSignals subFormulas(final @NotNull PfasAnnotation annotation,
      final @Nullable PfasMatchSettings settings) {
    final MassSpectrum spectrum = spectrum(annotation);
    final IonType ionType = annotation.getAdductType();
    final Double precursorMz = annotation.getPrecursorMZ();
    final IMolecularFormula neutral = FormulaUtils.parse(annotation.getFormula());
    if (settings == null || spectrum == null || ionType == null || precursorMz == null
        || neutral == null) {
      return null;
    }
    final IMolecularFormula ion = ionType.addToFormula(neutral, true).orElse(null);
    if (ion == null) {
      return null;
    }
    final double[] mzs = spectrum.getMzValues(new double[spectrum.getNumberOfDataPoints()]);
    final double[] intensities = spectrum.getIntensityValues(
        new double[spectrum.getNumberOfDataPoints()]);
    // rule-matched peaks were taken from the same spectrum, so their m/z values are identical
    final Set<Double> matched = annotation.getMatchedPeaks().stream()
        .map(PfasMatchedPeak::observedMz).collect(Collectors.toSet());
    final Set<Integer> rulePeaks = new HashSet<>();
    for (int i = 0; i < mzs.length; i++) {
      if (matched.contains(mzs[i])) {
        rulePeaks.add(i);
      }
    }
    return PfasExplainedSignals.compute(mzs, intensities, precursorMz, ion, rulePeaks, settings);
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
   * @param palette color palette
   * @return a palette color different from the rule-based and spectrum colors
   */
  private static @NotNull Color subFormulaColor(final @NotNull SimpleColorPalette palette) {
    final Color positive = FxColorUtil.fxColorToAWT(palette.getPositiveColor());
    final Color neutral = FxColorUtil.fxColorToAWT(palette.getNeutralColor());
    return palette.getColorsAWT().stream().filter(c -> !c.equals(positive) && !c.equals(neutral))
        .findFirst().orElse(Color.GRAY);
  }
}
