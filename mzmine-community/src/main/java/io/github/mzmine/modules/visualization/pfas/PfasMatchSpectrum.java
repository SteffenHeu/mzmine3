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
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasSubstructureFragments;
import io.github.mzmine.datamodel.features.types.annotations.SmilesStructureType;
import java.awt.Color;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.renderer.generators.standard.StandardGenerator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openscience.cdk.interfaces.IMolecularFormula;

/**
 * The MS/MS scan and the observed signals explained by one PFAS annotation: rule-matched peaks and,
 * if the matching settings are known, peaks explained by sub-formulas of the precursor ion. A click
 * on a rule or substructure peak highlights its substructure in {@link #highlightedProperty()}.
 */
final class PfasMatchSpectrum extends SimpleXYChart<PlotXYDataProvider> {

  private final ObjectProperty<@Nullable IAtomContainer> highlighted = new SimpleObjectProperty<>();
  // decision: fixed red, palette colors can be close to the substructure color
  private static final Color CUT_BOND_COLOR = new Color(220, 30, 30);
  // share of the highlight color in the glow, the rest is the white background
  private static final double GLOW_OPACITY = 0.45;
  // m/z values of one mass list peak in different datasets are identical
  private static final double SAME_PEAK_MZ = 1e-6;

  /**
   * @param annotation  annotation whose scan and matched peaks should be plotted
   * @param subFormulas sub-formula explained peaks, or null if they cannot be rebuilt
   * @param fragments   substructures of the structure for highlights, or null without structure
   */
  PfasMatchSpectrum(final @NotNull PfasAnnotation annotation,
      final @Nullable PfasExplainedSignals subFormulas,
      final @Nullable PfasSubstructureFragments fragments) {
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
    final List<Color> colors = otherColors(palette);
    final Color substructureColor = colors.getFirst();
    final List<SubFormulaPeak> substructures =
        subFormulas == null || ionName == null ? List.of()
            : subFormulas.subFormulaPeaks().stream().filter(SubFormulaPeak::isSubstructure)
                .toList();
    if (subFormulas != null && ionName != null) {
      addSubFormulas(substructures, "Substructure signals",
          "Substructure of the structure, no rule. Click to highlight it in the structure.",
          substructureColor, ionName, formats);
      addSubFormulas(
          subFormulas.subFormulaPeaks().stream().filter(p -> !p.isSubstructure()).toList(),
          "Sub-formula signals", "Sub-formula of the precursor ion, no substructure or rule",
          colors.get(1), ionName, formats);
    }

    final List<PfasMatchedPeak> peaks = annotation.getMatchedPeaks();
    final Color ruleColor = FxColorUtil.fxColorToAWT(palette.getPositiveColor());
    if (!peaks.isEmpty()) {
      final var color = ruleColor;
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
        return "%s%n%s%nObserved m/z %s%nExpected m/z %s%n%s%s".formatted(peak.ionLabel(ionName),
            origin(annotation, index, peak), formats.mz(peak.observedMz()),
            formats.mz(peak.expectedMz()), String.join(", ", peak.ruleIds()),
            fragments == null ? "" : "%nClick to highlight its substructure.".formatted());
      });
      addDataset(new ColoredXYDataset(provider, RunOption.THIS_THREAD), renderer);
    }

    // the cursor snaps to the closest item of any dataset, mostly the MS/MS spectrum, so peaks
    // are found by m/z, all datasets take their values from the same mass list
    final Map<PfasMatchedPeak, Optional<PfasSubstructureFragments.Match>> ruleMatches = new HashMap<>();
    cursorPositionProperty().subscribe(position -> {
      final IAtomContainer molecule = fragments == null ? null : fragments.molecule();
      if (position == null || molecule == null) {
        highlighted.set(null);
        return;
      }
      final double mz = position.getDomainValue();
      final SubFormulaPeak substructure = substructures.stream()
          .filter(p -> Math.abs(p.mz() - mz) < SAME_PEAK_MZ).findFirst().orElse(null);
      if (substructure != null && substructure.substructure() != null) {
        highlighted.set(highlight(molecule, substructure.substructure(), substructureColor));
        return;
      }
      final PfasSubstructureFragments.Match rule = peaks.stream()
          .filter(p -> Math.abs(p.observedMz() - mz) < SAME_PEAK_MZ).findFirst()
          .flatMap(p -> ruleMatches.computeIfAbsent(p,
              _ -> Optional.ofNullable(fragments.find(p.formula())))).orElse(null);
      highlighted.set(rule == null ? null : highlight(molecule, rule, ruleColor));
    });
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
    // only full structures are stored as SMILES, building block annotations get no substructures
    final String smiles = annotation.get(SmilesStructureType.class);
    return PfasExplainedSignals.compute(mzs, intensities, precursorMz, ion, rulePeaks, settings,
        () -> PfasSubstructureFragments.of(smiles));
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
    renderer.setDefaultItemLabelGenerator(
        (_, _, index) -> PfasIonLabels.loss(ionName, peaks.get(index).lossFormula()));
    renderer.setDefaultToolTipGenerator((_, _, index) -> {
      final SubFormulaPeak peak = peaks.get(index);
      final String hf = peak.substructure() == null || peak.substructure().hfLoss() == 0 ? ""
          : "%nSubstructure minus %d HF".formatted(peak.substructure().hfLoss());
      return "%s%nObserved m/z %s%n%s%s".formatted(PfasIonLabels.loss(ionName, peak.lossFormula()),
          formats.mz(peak.mz()), description, hf);
    });
    addDataset(new ColoredXYDataset(provider, RunOption.THIS_THREAD), renderer);
  }

  /**
   * @return the structure with the substructure of the clicked peak highlighted, or null if no
   * peak with a substructure is selected
   */
  @NotNull ObjectProperty<@Nullable IAtomContainer> highlightedProperty() {
    return highlighted;
  }

  /**
   * @param molecule structure the match indices refer to, see
   *                 {@link PfasSubstructureFragments#molecule()}
   * @param match    substructure of a peak
   * @param color    color of the clicked peak's dataset
   * @return a copy of the structure with the substructure highlighted and the cut bonds in red
   */
  private static @NotNull IAtomContainer highlight(final @NotNull IAtomContainer molecule,
      final @NotNull PfasSubstructureFragments.Match match, final @NotNull Color color) {
    final IAtomContainer copy;
    try {
      copy = molecule.clone();
    } catch (CloneNotSupportedException e) {
      return molecule;
    }
    final Color glow = lighter(color);
    final Set<IAtom> atoms = new HashSet<>();
    for (final int index : match.atoms()) {
      final IAtom atom = copy.getAtom(index);
      atom.setProperty(StandardGenerator.HIGHLIGHT_COLOR, glow);
      atoms.add(atom);
    }
    for (final IBond bond : copy.bonds()) {
      if (atoms.contains(bond.getBegin()) && atoms.contains(bond.getEnd())) {
        bond.setProperty(StandardGenerator.HIGHLIGHT_COLOR, glow);
      }
    }
    for (final int index : match.cutBonds()) {
      copy.getBond(index).setProperty(StandardGenerator.HIGHLIGHT_COLOR, lighter(CUT_BOND_COLOR));
    }
    return copy;
  }

  /**
   * @param color highlight color
   * @return the color blended with the white background. decision: no alpha, the glow shapes of
   * atoms and bonds overlap and would darken at every junction
   */
  private static @NotNull Color lighter(final @NotNull Color color) {
    return new Color(blend(color.getRed()), blend(color.getGreen()), blend(color.getBlue()));
  }

  private static int blend(final int channel) {
    return (int) Math.round(channel * GLOW_OPACITY + 255 * (1 - GLOW_OPACITY));
  }

  /**
   * @param palette color palette
   * @return two palette colors different from the rule-based and spectrum colors
   */
  private static @NotNull List<Color> otherColors(final @NotNull SimpleColorPalette palette) {
    final Color positive = FxColorUtil.fxColorToAWT(palette.getPositiveColor());
    final Color neutral = FxColorUtil.fxColorToAWT(palette.getNeutralColor());
    // decision: saturated colors only, black or gray would hide the structure highlight
    final List<Color> colors = new ArrayList<>(
        palette.getColorsAWT().stream().filter(c -> !c.equals(positive) && !c.equals(neutral))
            .filter(c -> Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null)[1] >= 0.4f)
            .limit(2).toList());
    while (colors.size() < 2) {
      colors.add(Color.GRAY);
    }
    return colors;
  }
}
