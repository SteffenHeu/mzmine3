package io.github.mzmine.modules.visualization.pfas;

import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasAnnotation;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasAnnotationMethod;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasExplainedSignals;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasExplainedSignals.SubFormulaPeak;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasMatchedPeak;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasSubstructureFragments;
import io.github.mzmine.datamodel.features.types.annotations.pfas.PfasMatchListType;
import io.github.mzmine.javafx.mvci.FxInteractor;
import io.github.mzmine.javafx.util.FxColorUtil;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.renderer.generators.standard.StandardGenerator;

/**
 * Loads the PFAS annotations of the selected row and finds the substructure of the selected
 * spectrum peak.
 */
final class PfasAnnotationMatchInteractor extends FxInteractor<PfasAnnotationMatchModel> {

  // decision: fixed red, palette colors can be close to the substructure color
  private static final Color CUT_BOND_COLOR = new Color(220, 30, 30);
  // share of the highlight color in the glow, the rest is the white background
  private static final double GLOW_OPACITY = 0.45;
  // m/z values of one mass list peak in different datasets are identical
  private static final double SAME_PEAK_MZ = 1e-6;

  private final @NotNull PfasMatchColors colors;
  // rule peak substructures are searched on first selection, per shown signals
  private final Map<PfasMatchedPeak, Optional<PfasSubstructureFragments.Match>> ruleMatches = new HashMap<>();
  private @Nullable PfasMatchSignals ruleMatchesOf;

  /**
   * @param model  viewer model
   * @param colors dataset colors, the highlight takes the color of the selected peak
   */
  PfasAnnotationMatchInteractor(final @NotNull PfasAnnotationMatchModel model,
      final @NotNull PfasMatchColors colors) {
    super(model);
    this.colors = colors;
  }

  /**
   * Loads the annotations and matching settings of the selected row and selects the best
   * annotation.
   */
  @Override
  public void updateModel() {
    final FeatureListRow row = model.selectedRowProperty().get();
    final List<PfasAnnotation> annotations =
        row == null ? List.of() : Objects.requireNonNullElse(row.get(PfasMatchListType.class),
            List.of());
    model.settingsProperty().set(row == null || row.getFeatureList() == null ? null
        : PfasAnnotationMethod.latestSettings(row.getFeatureList().getAppliedMethods()));
    model.getAnnotations().setAll(annotations);
    model.selectedAnnotationProperty().set(annotations.isEmpty() ? null : annotations.getFirst());
  }

  /**
   * Highlights the substructure of the selected peak: a substructure signal or a rule-based signal
   * whose fragment formula is a substructure.
   */
  void updateHighlight() {
    final PfasMatchSignals signals = model.signalsProperty().get();
    final Double mz = model.selectedMzProperty().get();
    final IAtomContainer molecule = signals == null ? null : signals.molecule();
    if (mz == null || molecule == null) {
      setHighlight(null, null, null);
      return;
    }
    // the cursor snaps to the closest item of any dataset, mostly the MS/MS spectrum, so peaks
    // are found by m/z, all datasets take their values from the same mass list
    final PfasExplainedSignals subFormulas = signals.subFormulas();
    final SubFormulaPeak substructure = subFormulas == null ? null
        : subFormulas.subFormulaPeaks().stream().filter(SubFormulaPeak::isSubstructure)
            .filter(p -> Math.abs(p.mz() - mz) < SAME_PEAK_MZ).findFirst().orElse(null);
    if (substructure != null && substructure.substructure() != null) {
      setHighlight(highlight(molecule, substructure.substructure(), colors.substructure()),
          note(substructure.substructure(), substructure.charge()), colors.substructure());
      return;
    }
    if (ruleMatchesOf != signals) {
      ruleMatches.clear();
      ruleMatchesOf = signals;
    }
    final PfasMatchedPeak rulePeak = signals.annotation().getMatchedPeaks().stream()
        .filter(p -> Math.abs(p.observedMz() - mz) < SAME_PEAK_MZ).findFirst().orElse(null);
    final PfasSubstructureFragments.Match rule = rulePeak == null ? null
        : ruleMatches.computeIfAbsent(rulePeak,
            p -> Optional.ofNullable(signals.fragments().find(p.formula()))).orElse(null);
    if (rule == null) {
      setHighlight(null, null, null);
    } else {
      setHighlight(highlight(molecule, rule, colors.rule()), note(rule, rulePeak.charge()),
          colors.rule());
    }
  }

  /**
   * @param structure highlighted structure, or null
   * @param note      how the fragment differs from the substructure, or null
   * @param color     dataset color of the selected peak, or null without highlight
   */
  private void setHighlight(final @Nullable IAtomContainer structure,
      final @Nullable String note, final @Nullable Color color) {
    model.highlightedStructureProperty().set(structure);
    model.highlightNoteProperty().set(note);
    // the note gets the glow color of the structure
    model.highlightColorProperty()
        .set(color == null ? null : FxColorUtil.awtColorToFX(lighter(color)));
  }

  /**
   * @param match  substructure of the selected peak
   * @param charge charge of the fragment ion
   * @return "HF loss", "3 HF loss", "H rearrangement" or both, null for the plain substructure
   */
  static @Nullable String note(final @NotNull PfasSubstructureFragments.Match match,
      final int charge) {
    final List<String> notes = new ArrayList<>();
    if (match.hfLoss() > 0) {
      notes.add(match.hfLoss() == 1 ? "HF loss" : "%d HF loss".formatted(match.hfLoss()));
    }
    if (match.isHydrogenRearrangement(charge)) {
      notes.add("H rearrangement");
    }
    return notes.isEmpty() ? null : String.join(", ", notes);
  }

  /**
   * @param molecule structure the match indices refer to, see
   *                 {@link PfasSubstructureFragments#molecule()}
   * @param match    substructure of a peak
   * @param color    color of the selected peak's dataset
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

  /**
   * @param channel color channel 0-255
   * @return the channel blended with white
   */
  private static int blend(final int channel) {
    return (int) Math.round(channel * GLOW_OPACITY + 255 * (1 - GLOW_OPACITY));
  }
}
