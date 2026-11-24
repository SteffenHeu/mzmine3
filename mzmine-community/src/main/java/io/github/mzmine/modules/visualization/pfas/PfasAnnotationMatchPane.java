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
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasMatchedPeak;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasRefinementFlag;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.features.types.DataTypes;
import io.github.mzmine.datamodel.features.types.annotations.CommentType;
import io.github.mzmine.datamodel.features.types.annotations.pfas.PfasClassType;
import io.github.mzmine.datamodel.features.types.numbers.MzPpmDifferenceType;
import io.github.mzmine.datamodel.features.types.numbers.scores.ExplainedIntensityPercentType;
import io.github.mzmine.datamodel.structures.HarmonizationOptions;
import io.github.mzmine.datamodel.structures.MolecularStructure;
import io.github.mzmine.datamodel.structures.StructureInputType;
import io.github.mzmine.datamodel.structures.StructureParser;
import io.github.mzmine.gui.preferences.NumberFormats;
import io.github.mzmine.javafx.components.factories.FxLabels;
import io.github.mzmine.javafx.components.util.FxLayout;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.modules.visualization.molstructure.Structure2DComponent;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Scrollable stack of PFAS match cards for a feature.
 */
public final class PfasAnnotationMatchPane extends StackPane {

  private static final double METADATA_WIDTH = 310;

  /**
   * @param annotations annotations for the selected feature
   * @param observedMz  measured feature m/z
   */
  public PfasAnnotationMatchPane(final @NotNull List<PfasAnnotation> annotations,
      final @Nullable Double observedMz) {
    final VBox cards = FxLayout.newVBox(Pos.TOP_LEFT, new Insets(12), true);
    cards.setSpacing(12);
    cards.getChildren()
        .setAll(annotations.stream().map(annotation -> card(annotation, observedMz)).toList());
    getChildren().add(FxLayout.newScrollPane(cards));
  }

  /**
   * Opens the annotation cards in a window.
   *
   * @param rowId       selected feature row ID
   * @param observedMz  measured feature m/z
   * @param annotations annotations of the feature
   * @param owner       feature table window, if available
   */
  public static void show(final int rowId, final @Nullable Double observedMz,
      final @NotNull List<PfasAnnotation> annotations, final @Nullable Window owner) {
    final Stage stage = new Stage();
    if (owner != null) {
      stage.initOwner(owner);
    }
    final Scene scene = new Scene(new PfasAnnotationMatchPane(annotations, observedMz), 1100, 690);
    ConfigService.getConfiguration().getTheme().apply(scene.getStylesheets());
    stage.setScene(scene);
    stage.setMinWidth(740);
    stage.setTitle("PFAS annotations · Feature " + rowId);
    stage.show();
  }

  /**
   * @param annotation match to display
   * @param observedMz measured feature m/z
   * @return one spectrum and its metadata in a card
   */
  private static @NotNull VBox card(final @NotNull PfasAnnotation annotation,
      final @Nullable Double observedMz) {
    final Label title = FxLabels.newBoldTitle(
        Objects.requireNonNullElse(annotation.getCompoundName(), "PFAS annotation"));
    final Region spectrum;
    if (annotation.getScan() == null && annotation.getMatchedPeaks().isEmpty()) {
      spectrum = new StackPane(FxLabels.newLabel("No matched MS/MS spectrum"));
      spectrum.setMinWidth(360);
      spectrum.setPrefHeight(210);
    } else {
      spectrum = new PfasMatchSpectrum(annotation);
    }
    final VBox details = metadata(annotation, observedMz);
    details.setMinWidth(METADATA_WIDTH);
    details.setPrefWidth(METADATA_WIDTH);
    details.setMaxWidth(METADATA_WIDTH);
    final HBox body = FxLayout.newHBox(Pos.TOP_LEFT, Insets.EMPTY, 14, spectrum, details);
    HBox.setHgrow(spectrum, Priority.ALWAYS);
    final VBox card = FxLayout.newVBox(Pos.TOP_LEFT, Insets.EMPTY, true, title, body);
    card.getStyleClass().add("quality-card");
    card.setSpacing(8);
    return card;
  }

  /**
   * @param annotation match to display
   * @param observedMz measured feature m/z
   * @return metadata and structural evidence for this match
   */
  private static @NotNull VBox metadata(final @NotNull PfasAnnotation annotation,
      final @Nullable Double observedMz) {
    final NumberFormats formats = ConfigService.getGuiFormats();
    final VBox details = FxLayout.newVBox(Pos.TOP_LEFT, Insets.EMPTY, true);
    details.setSpacing(4);
    addDetail(details, "Confidence", annotation.getConfidenceLevel());
    addDetail(details, "Candidate source", annotation.getCandidateSource());
    addDetail(details, "PFAS class", annotation.get(PfasClassType.class));
    addDetail(details, "Formula", annotation.getFormula());
    addDetail(details, "Ion", annotation.getAdductType());
    addDetail(details, "Observed precursor m/z", formats.mz(observedMz));
    final Double candidateMz = annotation.getPrecursorMZ();
    final boolean candidateMass = annotation.get(MzPpmDifferenceType.class) != null;
    if (candidateMass) {
      addDetail(details, "Calculated precursor m/z", formats.mz(candidateMz));
      addDetail(details, "Mass error (ppm)",
          formats.ppm(annotation.get(MzPpmDifferenceType.class)));
    }
    final Scan scan = annotation.getScan();
    if (scan != null) {
      addDetail(details, "Scan",
          "%s · #%d".formatted(scan.getDataFile().getName(), scan.getScanNumber()));
    }
    if (!annotation.getMatchedPeaks().isEmpty()) {
      addDetail(details, "Matched signals", annotation.getMatchedPeaks().size());
    }
    final Float explainedIntensity = annotation.get(ExplainedIntensityPercentType.class);
    if (explainedIntensity != null) {
      addDetail(details, "Explained intensity", DataTypes.get(ExplainedIntensityPercentType.class)
          .getFormattedString(explainedIntensity));
    }
    addDetail(details, "MS/MS parent row", annotation.getInSourceParentRowId());
    addDetail(details, "Refinement",
        annotation.getFlags().stream().map(PfasRefinementFlag::toString)
            .collect(Collectors.joining(", ")));

    final MolecularStructure structure = annotation.getStructure();
    if (structure != null) {
      final String label = annotation.isInSourceFragment() ? "Parent structure"
          : annotation.isProposedStructure() ? "Proposed structure" : "Structure";
      details.getChildren().addAll(FxLabels.newBoldLabel(label), depiction(structure, 140));
    } else if (!annotation.getBuildingBlocks().isEmpty()) {
      final String label =
          annotation.isInSourceFragment() ? "Parent building blocks" : "Building blocks";
      details.getChildren().add(FxLabels.newBoldLabel(label));
      for (final PfasBlockEvidence block : annotation.getBuildingBlocks()) {
        details.getChildren()
            .add(blockDetails(annotation, block, candidateMass ? candidateMz : null));
      }
    }
    addDetail(details, "Notes", annotation.get(CommentType.class));
    return details;
  }

  /**
   * @param annotation  owning annotation with matched fragment peaks
   * @param block       building block with optional open attachment points
   * @param candidateMz calculated candidate precursor, if available
   * @return depiction and a summary of the available evidence
   */
  private static @NotNull VBox blockDetails(final @NotNull PfasAnnotation annotation,
      final @NotNull PfasBlockEvidence block, final @Nullable Double candidateMz) {
    final VBox box = FxLayout.newVBox(Pos.TOP_LEFT, new Insets(5, 0, 5, 6), true,
        FxLabels.newBoldLabel(block.name()));
    box.setSpacing(3);
    addDetail(box, "Formula", block.formula());
    final MolecularStructure structure = StructureParser.silent()
        .parseStructure(block.smiles(), StructureInputType.SMILES, HarmonizationOptions.NONE);
    if (structure != null) {
      box.getChildren().add(depiction(structure, 85));
    }
    final NumberFormats formats = ConfigService.getGuiFormats();
    final List<PfasMatchedPeak> peaks = annotation.getMatchedPeaks();
    final String fragments = block.peakIndices().stream()
        .filter(index -> index >= 0 && index < peaks.size()).map(peaks::get)
        .map(peak -> "%s (m/z %s)".formatted(peak.ionLabel(), formats.mz(peak.observedMz())))
        .collect(Collectors.joining(", "));
    if (!fragments.isEmpty()) {
      addDetail(box, "Rule-matched fragments", fragments);
    } else if (annotation.isInSourceFragment()) {
      addDetail(box, "Evidence",
          "Linked to MS/MS parent row " + annotation.getInSourceParentRowId());
    } else if (candidateMz != null) {
      addDetail(box, "Evidence", "Candidate precursor m/z " + formats.mz(candidateMz));
    }
    return box;
  }

  /**
   * @param structure complete molecule or substructure to depict
   * @param height    depiction height
   * @return component for the 2D depiction
   */
  private static @NotNull StackPane depiction(final @NotNull MolecularStructure structure,
      final double height) {
    final StackPane pane = new StackPane(new Structure2DComponent(structure.structure()));
    pane.setMinHeight(height);
    pane.setPrefHeight(height);
    pane.setMaxHeight(height);
    return pane;
  }

  /**
   * @param details pane receiving the detail
   * @param field   metadata label
   * @param value   value to display when available
   */
  private static void addDetail(final @NotNull VBox details, final @NotNull String field,
      final @Nullable Object value) {
    if (value != null && !value.toString().isBlank()) {
      final Label detail = FxLabels.newLabel(field + "  " + value);
      detail.setTextAlignment(TextAlignment.LEFT);
      detail.setMinHeight(Region.USE_PREF_SIZE);
      details.getChildren().add(detail);
    }
  }
}
