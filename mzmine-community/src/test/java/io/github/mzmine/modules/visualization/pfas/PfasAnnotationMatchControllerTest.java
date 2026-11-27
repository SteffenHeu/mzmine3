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

import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasAnnotation;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasBlockEvidence;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasCandidateSource;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasCitation;
import io.github.mzmine.datamodel.features.types.annotations.pfas.PfasCitationsType;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasConfidenceLevel;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasEvidenceLevel;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasIonLabels;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasMatchedPeak;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasSubstructureFragments;
import io.github.mzmine.datamodel.features.types.annotations.CompoundNameType;
import io.github.mzmine.datamodel.features.types.annotations.SmilesStructureType;
import io.github.mzmine.datamodel.features.types.annotations.pfas.PfasBuildingBlocksType;
import io.github.mzmine.datamodel.features.types.annotations.pfas.PfasCandidateSourceType;
import io.github.mzmine.datamodel.features.types.annotations.pfas.PfasConfidenceLevelType;
import io.github.mzmine.datamodel.features.types.annotations.pfas.PfasMatchListType;
import io.github.mzmine.datamodel.features.types.numbers.MatchingSignalsType;
import io.github.mzmine.datamodel.features.types.numbers.PrecursorMZType;
import io.github.mzmine.datamodel.features.types.numbers.scores.ExplainedIntensityPercentType;
import io.github.mzmine.javafx.components.LabeledProgressBar;
import io.github.mzmine.javafx.concurrent.threading.FxThread;
import io.github.mzmine.project.impl.RawDataFileImpl;
import io.github.mzmine.util.FeatureListTestUtils;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Smoke test of the PFAS match viewer on the FX thread. The annotations are built by hand, the
 * annotation logic that creates them lives in mzmine pro.
 */
class PfasAnnotationMatchControllerTest {

  private static final double PRECURSOR_MZ = 650.0123;

  @BeforeAll
  static void initFx() {
    FxThread.initJavaFx();
  }

  /**
   * MS2-only substructure annotation of a C4F9 sulfonate: perfluoroalkyl anions and SO3-.
   */
  private static @NotNull PfasAnnotation substructureAnnotation() {
    final PfasAnnotation annotation = new PfasAnnotation();
    final List<PfasMatchedPeak> peaks = List.of(
        new PfasMatchedPeak(68.9958, 50, 68.9958, "CF3", -1, PfasEvidenceLevel.GENERIC,
            List.of("perfluoroalkyl.series"), null),
        new PfasMatchedPeak(79.9574, 100, 79.9574, "O3S", -1, PfasEvidenceLevel.DIAGNOSTIC,
            List.of("sulfonate.so3"), null),
        new PfasMatchedPeak(118.9926, 45, 118.9926, "C2F5", -1, PfasEvidenceLevel.GENERIC,
            List.of("perfluoroalkyl.series"), null),
        new PfasMatchedPeak(168.9894, 55, 168.9894, "C3F7", -1, PfasEvidenceLevel.GENERIC,
            List.of("perfluoroalkyl.series"), null),
        new PfasMatchedPeak(218.9862, 35, 218.9862, "C4F9", -1, PfasEvidenceLevel.GENERIC,
            List.of("perfluoroalkyl.series"), null));
    annotation.setMatchedPeaks(peaks);
    annotation.put(CompoundNameType.class, "Substructures: C4F9 chain, Sulfonic acid");
    annotation.put(PrecursorMZType.class, PRECURSOR_MZ);
    annotation.put(MatchingSignalsType.class, peaks.size());
    annotation.put(ExplainedIntensityPercentType.class, 0.815f);
    annotation.put(PfasConfidenceLevelType.class, PfasConfidenceLevel.MS2_SUBSTRUCTURE);
    annotation.put(PfasCandidateSourceType.class, PfasCandidateSource.SUBSTRUCTURE);
    annotation.put(PfasBuildingBlocksType.class, List.of(
        new PfasBlockEvidence("Perfluoroalkyl segment", "C4F9",
            "FC(F)(F)C(F)(F)C(F)(F)C(F)(F)[*:1]", List.of(0, 2, 3, 4)),
        new PfasBlockEvidence("Sulfonic acid", "HO3S", "[*:2]S(=O)(=O)O", List.of(1))));
    annotation.put(PfasCitationsType.class, List.of(
        new PfasCitation("BH17", "Barzen-Hanson et al. 2017", "https://doi.org/10.1021/x", null,
            "Sulfonic acid was reported in"),
        new PfasCitation("BACKE13", "Backe et al. 2013", null, null, null),
        new PfasCitation("BACKE13", "Backe et al. 2013", null, "sulfonate.so3", null),
        new PfasCitation("K20", "Koelmel et al. 2020", "https://doi.org/10.1021/y",
            "sulfonate.so3", null)));
    return annotation;
  }

  /**
   * @param annotations PFAS annotations of the row, best first
   * @return row with these annotations
   */
  private static @NotNull ModularFeatureListRow row(final @NotNull List<PfasAnnotation> annotations) {
    final var file = new RawDataFileImpl("sample", null, null);
    final var featureList = new ModularFeatureList("PFAS", null, file);
    final ModularFeatureListRow row = FeatureListTestUtils.addRow(featureList, 1, List.of(file),
        List.of(100f), PRECURSOR_MZ, 5f);
    row.set(PfasMatchListType.class, annotations);
    return row;
  }

  @Test
  void showsTheSelectedAnnotationAndFollowsTheSelection() throws Exception {
    final PfasAnnotation best = substructureAnnotation();
    final PfasAnnotation second = best.clone();
    second.put(CompoundNameType.class, "Second match");
    second.put(PfasConfidenceLevelType.class, PfasConfidenceLevel.LEVEL_5A);
    second.put(ExplainedIntensityPercentType.class, null);
    final ModularFeatureListRow row = row(List.of(best, second));

    runOnFxAndWait(() -> {
      final PfasAnnotationMatchController controller = new PfasAnnotationMatchController();
      final Scene scene = new Scene(controller.buildView(), 1100, 690);
      controller.selectedRowsProperty().set(List.of(row));
      scene.getRoot().applyCss();
      scene.getRoot().layout();

      Assertions.assertSame(best, controller.selectedAnnotationProperty().get());
      final ComboBox<?> selector = find(scene.getRoot(), ComboBox.class);
      Assertions.assertEquals(2, selector.getItems().size());
      Assertions.assertTrue(selector.isVisible());

      final PfasMatchSpectrum chart = find(scene.getRoot(), PfasMatchSpectrum.class);
      final var plot = chart.getXYPlot();
      final var series = plot.getDataset(0);
      Assertions.assertEquals(best.getMatchedPeaks().size(), series.getItemCount(0));
      // m/z and ion on the first line, building block and evidence level on the second
      final String mz = ConfigService.getGuiFormats().mz(68.9958);
      Assertions.assertEquals(mz + ", CF3⁻%nPerfluoroalkyl segment, generic".formatted(),
          plot.getRenderer(0).getDefaultItemLabelGenerator().generateLabel(series, 0, 0));
      // loss rules show only the loss notation
      Assertions.assertEquals("500.0, [M-CO2-H]⁻%nCarboxylic acid, subclass".formatted(),
          PfasIonLabels.peakLabel("500.0", "C7F15", -1, "[M-H]-", "CO2", "Carboxylic acid",
              PfasEvidenceLevel.SUBCLASS_ALIGNED));
      // explained intensity as bar next to the name
      final LabeledProgressBar explained = find(scene.getRoot(), LabeledProgressBar.class);
      Assertions.assertTrue(explained.isVisible());
      Assertions.assertEquals(0.815, explained.getProgressBar().getProgress(), 1e-6);
      Assertions.assertTrue(explained.getLabel().getText().contains("%"));
      Assertions.assertTrue(visibleLabels(scene.getRoot()).contains("Building blocks"));
      // shared reference numbers: the level publication [1], candidate [2] [3], SO3 rule [3] [4]
      Assertions.assertEquals("PFAS level: MS2 substructure fragments [1]",
          flowText(scene.getRoot(), "PFAS level"));
      Assertions.assertEquals("Candidate source: MS2 substructures [2] [3]",
          flowText(scene.getRoot(), "Candidate source"));
      Assertions.assertEquals("sulfonate.so3 [3] [4]", flowText(scene.getRoot(), "sulfonate.so3"));
      final Hyperlink first = find(scene.getRoot(), Hyperlink.class);
      Assertions.assertEquals("[1]", first.getText());
      Assertions.assertTrue(first.getTooltip().getText().startsWith("PFAS confidence levels: "
          + "Charbonnet et al.") && first.getTooltip().getText()
          .endsWith("https://doi.org/10.1021/acs.estlett.2c00206"));

      // metadata follows the selected annotation
      controller.selectedAnnotationProperty().set(second);
      scene.getRoot().layout();
      final List<String> labels = visibleLabels(scene.getRoot());
      Assertions.assertTrue(labels.contains("Second match"));
      Assertions.assertTrue(
          flowText(scene.getRoot(), "PFAS level").startsWith("PFAS level: Level 5a"));
      Assertions.assertFalse(explained.isVisible());

      // the pane of the annotation list shows only its own annotation, without selection
      final Scene single = new Scene(
          (Parent) second.createAnnotationPane(row), 1100, 350);
      single.getRoot().applyCss();
      single.getRoot().layout();
      Assertions.assertFalse(find(single.getRoot(), ComboBox.class).isVisible());
      Assertions.assertTrue(visibleLabels(single.getRoot()).contains("Second match"));

      // a row without PFAS annotations shows the placeholder
      controller.selectedRowsProperty().set(List.of(row(List.of())));
      Assertions.assertNull(controller.selectedAnnotationProperty().get());
      Assertions.assertEquals(List.of("No PFAS annotation for the selected row"),
          visibleLabels(scene.getRoot()));
      controller.close();
    });
  }

  /**
   * The recomputed signals reach the model of the still selected annotation, and selecting a rule
   * peak highlights its substructure.
   */
  @Test
  void selectedRulePeakHighlightsItsSubstructure() {
    final PfasAnnotation annotation = substructureAnnotation();
    annotation.put(SmilesStructureType.class, "OS(=O)(=O)C(F)(F)C(F)(F)C(F)(F)C(F)(F)F");
    final PfasAnnotationMatchModel model = new PfasAnnotationMatchModel();
    model.selectedAnnotationProperty().set(annotation);
    final PfasMatchSignalsTask task = new PfasMatchSignalsTask(model, annotation, null);
    task.process();
    task.updateGuiModel();
    final PfasMatchSignals signals = model.signalsProperty().get();
    Assertions.assertNotNull(signals);
    Assertions.assertNotNull(signals.molecule());

    final var interactor = new PfasAnnotationMatchInteractor(model,
        PfasMatchColors.ofDefaultPalette());
    // CF3- rule peak
    model.selectedMzProperty().set(68.9958);
    interactor.updateHighlight();
    Assertions.assertNotNull(model.highlightedStructureProperty().get());
    // CF3- is the plain CF3 end, no note
    Assertions.assertNull(model.highlightNoteProperty().get());
    Assertions.assertNotNull(model.highlightColorProperty().get());
    model.selectedMzProperty().set(null);
    interactor.updateHighlight();
    Assertions.assertNull(model.highlightedStructureProperty().get());
    Assertions.assertNull(model.highlightColorProperty().get());

    Assertions.assertEquals("HF loss", PfasAnnotationMatchInteractor.note(
        new PfasSubstructureFragments.Match(List.of(), List.of(), 1, 1), -1));
    Assertions.assertEquals("3 HF loss, H rearrangement", PfasAnnotationMatchInteractor.note(
        new PfasSubstructureFragments.Match(List.of(), List.of(), 3, -1), -1));
    Assertions.assertNull(PfasAnnotationMatchInteractor.note(
        new PfasSubstructureFragments.Match(List.of(), List.of(), 0, 0), -1));

    // results of a deselected annotation are dropped
    model.signalsProperty().set(null);
    model.selectedAnnotationProperty().set(annotation.clone());
    task.updateGuiModel();
    Assertions.assertNull(model.signalsProperty().get());
  }

  /**
   * @param root  root of the scene graph
   * @param clazz node class to find
   * @return the first node of this class, depth first
   */
  private static <T extends Node> @NotNull T find(final @NotNull Node root,
      final @NotNull Class<? super T> clazz) {
    if (clazz.isInstance(root)) {
      return (T) root;
    }
    final List<Node> children = root instanceof ScrollPane scroll ? List.of(scroll.getContent())
        : root instanceof Parent parent ? parent.getChildrenUnmodifiable() : List.of();
    for (final Node child : children) {
      try {
        return find(child, clazz);
      } catch (AssertionError _) {
        // search the next child
      }
    }
    throw new AssertionError("No " + clazz.getSimpleName() + " in the view");
  }

  /**
   * @param root   root of the scene graph
   * @param prefix start of the flow text
   * @return text of the first text flow starting with the prefix, links and labels included
   */
  private static @NotNull String flowText(final @NotNull Node root, final @NotNull String prefix) {
    if (root instanceof TextFlow flow) {
      final String text = flow.getChildren().stream().map(node -> switch (node) {
        case Text t -> t.getText();
        case Labeled l -> l.getText();
        default -> "";
      }).collect(Collectors.joining());
      if (text.startsWith(prefix)) {
        return text;
      }
    }
    final List<Node> children = root instanceof ScrollPane scroll ? List.of(scroll.getContent())
        : root instanceof Parent parent ? parent.getChildrenUnmodifiable() : List.of();
    for (final Node child : children) {
      final String text = flowText(child, prefix);
      if (!text.isEmpty()) {
        return text;
      }
    }
    return "";
  }

  /**
   * @param root root of the scene graph
   * @return texts of all visible labels
   */
  private static @NotNull List<String> visibleLabels(final @NotNull Node root) {
    if (!root.isVisible()) {
      return List.of();
    }
    if (root instanceof Label label) {
      return label.getText() == null ? List.of() : List.of(label.getText());
    }
    final List<Node> children = root instanceof ScrollPane scroll ? List.of(scroll.getContent())
        : root instanceof Parent parent ? parent.getChildrenUnmodifiable() : List.of();
    return children.stream().flatMap(child -> visibleLabels(child).stream()).toList();
  }

  /**
   * Runs assertions on the JavaFX thread and reports their outcome to the test.
   *
   * @param runnable assertions to run
   */
  private static void runOnFxAndWait(final @NotNull Runnable runnable) throws Exception {
    final FutureTask<Void> task = new FutureTask<>(runnable, null);
    Platform.runLater(task);
    task.get(30, TimeUnit.SECONDS);
  }
}
