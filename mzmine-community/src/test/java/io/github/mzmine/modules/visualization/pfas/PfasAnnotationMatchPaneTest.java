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
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasBlockEvidence;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasCandidateSource;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasConfidenceLevel;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasEvidenceLevel;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasMatchedPeak;
import io.github.mzmine.datamodel.features.types.annotations.CompoundNameType;
import io.github.mzmine.datamodel.features.types.annotations.pfas.PfasBuildingBlocksType;
import io.github.mzmine.datamodel.features.types.annotations.pfas.PfasCandidateSourceType;
import io.github.mzmine.datamodel.features.types.annotations.pfas.PfasConfidenceLevelType;
import io.github.mzmine.datamodel.features.types.numbers.MatchingSignalsType;
import io.github.mzmine.datamodel.features.types.numbers.PrecursorMZType;
import io.github.mzmine.datamodel.features.types.numbers.scores.ExplainedIntensityPercentType;
import io.github.mzmine.javafx.concurrent.threading.FxThread;
import io.github.mzmine.modules.visualization.molstructure.Structure2DComponent;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Smoke test of the PFAS match viewer on the FX thread. The annotation is built by hand, the
 * annotation logic that creates it lives in mzmine pro.
 */
class PfasAnnotationMatchPaneTest {

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
    annotation.put(PrecursorMZType.class, 650.0123);
    annotation.put(MatchingSignalsType.class, peaks.size());
    annotation.put(ExplainedIntensityPercentType.class, 81.5f);
    annotation.put(PfasConfidenceLevelType.class, PfasConfidenceLevel.MS2_SUBSTRUCTURE);
    annotation.put(PfasCandidateSourceType.class, PfasCandidateSource.SUBSTRUCTURE);
    annotation.put(PfasBuildingBlocksType.class, List.of(
        new PfasBlockEvidence("Perfluoroalkyl segment", "C4F9",
            "FC(F)(F)C(F)(F)C(F)(F)C(F)(F)[*:1]", List.of(0, 2, 3, 4)),
        new PfasBlockEvidence("Sulfonic acid", "HO3S", "[*:2]S(=O)(=O)O", List.of(1))));
    return annotation;
  }

  @Test
  void showsACardForEveryMatchAndLabelsTheObservedFragments() throws Exception {
    final PfasAnnotation annotation = substructureAnnotation();
    runOnFxAndWait(() -> {
      final PfasAnnotationMatchPane pane = new PfasAnnotationMatchPane(
          List.of(annotation, annotation.clone()), 650.0123, null);
      final Scene scene = new Scene(pane, 1100, 690);
      scene.getRoot().applyCss();
      scene.getRoot().layout();
      final ScrollPane scroll = (ScrollPane) pane.getChildren().getFirst();
      final VBox cards = (VBox) scroll.getContent();
      Assertions.assertEquals(2, cards.getChildren().size());
      final VBox card = (VBox) cards.getChildren().getFirst();
      final HBox body = (HBox) card.getChildren().get(1);
      final PfasMatchSpectrum chart = (PfasMatchSpectrum) body.getChildren().getFirst();
      final var plot = chart.getXYPlot();
      final var series = plot.getDataset(0);
      Assertions.assertEquals(annotation.getMatchedPeaks().size(), series.getItemCount(0));
      final var renderer = plot.getRenderer(0);
      // ion on the first line, building block and evidence level on the second
      Assertions.assertEquals(
          annotation.getMatchedPeaks().getFirst().ionLabel() + "\nPerfluoroalkyl segment · generic",
          renderer.getDefaultItemLabelGenerator().generateLabel(series, 0, 0));
      final VBox metadata = (VBox) body.getChildren().get(1);
      Assertions.assertTrue(
          metadata.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
              .map(Label::getText)
              .anyMatch(text -> text.startsWith("Explained intensity") && text.contains("%")));
      Assertions.assertTrue(
          metadata.getChildren().stream().filter(VBox.class::isInstance).map(VBox.class::cast)
              .flatMap(box -> box.getChildren().stream()).anyMatch(
                  node -> node instanceof StackPane depiction && depiction.getChildren()
                      .getFirst() instanceof Structure2DComponent structure
                      && structure.getWidth() > 150));
    });
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
