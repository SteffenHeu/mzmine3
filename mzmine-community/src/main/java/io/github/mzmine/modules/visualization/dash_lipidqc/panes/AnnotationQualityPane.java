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

package io.github.mzmine.modules.visualization.dash_lipidqc.panes;

import static io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.clampToUnit;
import static io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.computeCombinedAnnotationScore;
import static io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.computeElutionOrderMetrics;
import static io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.computeInterferenceMetrics;
import static io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.computeInterferenceScore;
import static io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.formatElutionOrderDetail;
import static io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.interferenceDetail;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.gui.chartbasics.chartutils.paintscales.PaintScaleTransform;
import io.github.mzmine.javafx.mvci.FxUpdateTask;
import io.github.mzmine.javafx.mvci.LatestTaskScheduler;
import io.github.mzmine.javafx.util.FxColorUtil;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.MSMSLipidTools;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.molecular_species.MolecularSpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.species_level.SpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.ILipidAnnotation;
import io.github.mzmine.modules.visualization.dash_lipidqc.LipidAnnotationQCDashboardModel;
import io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.ElutionOrderMetrics;
import io.github.mzmine.modules.visualization.dash_lipidqc.scoring.LipidQcScoringUtils.InterferenceMetrics;
import io.github.mzmine.util.FeatureTableFXUtil;
import io.github.mzmine.util.color.SimpleColorPalette;
import java.awt.Color;
import java.awt.Paint;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import javafx.geometry.Pos;
import javafx.scene.control.Accordion;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.renderer.PaintScale;

public class AnnotationQualityPane extends BorderPane {

  private static final double METRIC_BAR_WIDTH = 230d;
  private static final double METRIC_BAR_HEIGHT = 16d;
  private final LipidAnnotationQCDashboardModel model;
  private final @NotNull LatestTaskScheduler scheduler = new LatestTaskScheduler();
  private final javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(8);
  private final javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane();
  private final Label placeholder = new Label("Select a row with lipid annotations.");
  private final Button removeMultiRowAnnotationsButton = new Button("Remove multi-row annotations");
  private final Button setBestPerRowButton = new Button("Set all rows to highest-score annotation");
  private boolean includeRetentionTimeAnalysis = true;
  private @Nullable Runnable onAnnotationsChanged;
  private @Nullable FeatureListRow row;
  private @Nullable ModularFeatureList featureList;

  public AnnotationQualityPane(final @NotNull LipidAnnotationQCDashboardModel model) {
    this.model = model;
    scrollPane.setFitToWidth(true);
    scrollPane.setContent(content);
    removeMultiRowAnnotationsButton.setOnAction(_ -> removeMultiRowAnnotations());
    setBestPerRowButton.setOnAction(_ -> setHighestScoreAnnotationOnAllRows());
    final VBox actionBox = new VBox(6, removeMultiRowAnnotationsButton, setBestPerRowButton);
    actionBox.setAlignment(Pos.TOP_LEFT);
    final TitledPane actionsPane = new TitledPane("Annotation actions", actionBox);
    actionsPane.setCollapsible(true);
    final Accordion actionsAccordion = new Accordion(actionsPane);
    actionsAccordion.setExpandedPane(null);
    setBottom(actionsAccordion);
    setCenter(placeholder);
    BorderPane.setAlignment(placeholder, Pos.CENTER);
  }

  public void setFeatureList(final @Nullable ModularFeatureList featureList) {
    this.featureList = featureList;
    requestUpdate();
  }

  public void setRow(final @Nullable FeatureListRow row) {
    this.row = row;
    requestUpdate();
  }

  public void setIncludeRetentionTimeAnalysis(final boolean includeRetentionTimeAnalysis) {
    if (this.includeRetentionTimeAnalysis == includeRetentionTimeAnalysis) {
      return;
    }
    this.includeRetentionTimeAnalysis = includeRetentionTimeAnalysis;
    requestUpdate();
  }

  public void setOnAnnotationsChanged(final @Nullable Runnable onAnnotationsChanged) {
    this.onAnnotationsChanged = onAnnotationsChanged;
  }

  private void requestUpdate() {
    scheduler.onTaskThreadDelayed(
        new QualityComputationTask(this, row, featureList, includeRetentionTimeAnalysis),
        Duration.millis(120));
  }

  private void applyQualityResult(final @NotNull QualityComputationResult result) {
    if (result.placeholderText() != null) {
      showPlaceholder(result.placeholderText());
      return;
    }
    content.getChildren().clear();
    content.setStyle("-fx-padding: 8;");
    final InterferenceMetrics interferenceMetrics = result.interferenceMetrics();
    if (interferenceMetrics.totalPenaltyCount() > 0) {
      final Label warning = new Label(
          "Potential interference: " + interferenceDetail(interferenceMetrics));
      warning.setStyle(qualityWarningStyle());
      content.getChildren().add(warning);
    }
    final @Nullable Region duplicateRowsAlert = result.selectedAnnotation() == null ? null
        : createDuplicateRowsAlert(result.selectedAnnotation(), result.duplicateRows());
    if (duplicateRowsAlert != null) {
      content.getChildren().add(duplicateRowsAlert);
    }
    for (final QualityCardData qualityCardData : result.qualityCards()) {
      content.getChildren().add(createQualityCard(qualityCardData));
    }
    setCenter(scrollPane);
  }

  private void update() {
    requestUpdate();
  }

  private static @NotNull List<FeatureListRow> findDuplicateRowsExcludingSelected(
      final @Nullable ModularFeatureList featureList, final int selectedRowId,
      final @NotNull String annotation) {
    if (featureList == null || annotation.isBlank()) {
      return List.of();
    }
    return featureList.getRows().stream().filter(r -> !r.getLipidMatches().isEmpty()).filter(
            r -> Objects.equals(r.getLipidMatches().getFirst().getLipidAnnotation().getAnnotation(),
                annotation)).filter(r -> r.getID() != selectedRowId)
        .sorted((a, b) -> Integer.compare(a.getID(), b.getID())).map(r -> (FeatureListRow) r)
        .toList();
  }

  private Region createQualityCard(final @NotNull QualityCardData qualityCardData) {
    final javafx.scene.layout.VBox card = new javafx.scene.layout.VBox(6);
    card.setStyle(qualityCardStyle());
    card.getChildren().add(createQualityCardHeader(qualityCardData.match()));
    card.getChildren().add(createMetricRow("Overall quality", qualityCardData.overall(),
        qualityCardData.overall() >= 0.75 ? "High confidence"
            : qualityCardData.overall() >= 0.5 ? "Moderate confidence" : "Low confidence"));
    card.getChildren().add(createMetricRow("MS1 mass accuracy", qualityCardData.ms1().score(),
        qualityCardData.ms1().detail()));
    card.getChildren().add(createMetricRow("MS2 diagnostics", qualityCardData.ms2().score(),
        qualityCardData.ms2().detail()));
    card.getChildren().add(
        createMetricRow("Lipid Ion vs Ion Identity", qualityCardData.adduct().score(),
            qualityCardData.adduct().detail()));
    card.getChildren().add(createMetricRow("Isotope pattern", qualityCardData.isotope().score(),
        qualityCardData.isotope().detail()));
    if (includeRetentionTimeAnalysis) {
      card.getChildren().add(
          createMetricRow("Elution order score", qualityCardData.elutionOrder().score(),
              qualityCardData.elutionOrder().detail()));
    }
    card.getChildren().add(
        createMetricRow("Interference risk", qualityCardData.interference().score(),
            qualityCardData.interference().detail()));
    return card;
  }

  private @NotNull Region createQualityCardHeader(final @NotNull MatchedLipid match) {
    final Label annotation = new Label(match.getLipidAnnotation().getAnnotation());
    annotation.setStyle("-fx-font-weight: bold;");
    final Button deleteButton = new Button("Delete annotation");
    deleteButton.setOnAction(_ -> deleteAnnotationFromSelectedRowWithConfirmation(match));

    final Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    final HBox header = new HBox(8, annotation, spacer, deleteButton);
    header.setAlignment(Pos.CENTER_LEFT);
    return header;
  }

  private void deleteAnnotationFromSelectedRowWithConfirmation(final @NotNull MatchedLipid match) {
    if (row == null) {
      return;
    }
    if (!confirmDeleteAnnotation(match)) {
      return;
    }
    deleteAnnotationFromSelectedRow(match);
    refreshAfterAnnotationDelete(row);
  }

  private boolean confirmDeleteAnnotation(final @NotNull MatchedLipid match) {
    if (row == null) {
      return false;
    }
    final String annotation = Objects.toString(match.getLipidAnnotation().getAnnotation(),
        "Unknown annotation");
    final String message = "Delete annotation \"" + annotation + "\" from selected row #"
        + row.getID() + "?";
    final Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES,
        ButtonType.NO);
    alert.setTitle("Confirm annotation deletion");
    alert.setHeaderText("Delete annotation");
    final Optional<ButtonType> result = alert.showAndWait();
    return result.isPresent() && ButtonType.YES.equals(result.get());
  }

  private void deleteAnnotationFromSelectedRow(final @NotNull MatchedLipid match) {
    if (row == null) {
      return;
    }
    final List<MatchedLipid> remaining = row.getLipidMatches().stream()
        .filter(existingMatch -> !Objects.equals(existingMatch, match)).toList();
    row.setLipidAnnotations(remaining);
  }

  private Region createMetricRow(String name, double score, String detail) {
    final double clipped = Math.max(0d, Math.min(1d, score));
    final javafx.scene.paint.Color scoreColor = qualityScoreColor(clipped);
    final StackPane barPane = createMetricBar(name, clipped, scoreColor);

    final Label detailLabel = new Label(detail);
    detailLabel.setStyle("-fx-font-size: 11px;");
    detailLabel.setWrapText(true);
    return new VBox(3, barPane, detailLabel);
  }

  private @NotNull StackPane createMetricBar(final @NotNull String name, final double score,
      final @NotNull javafx.scene.paint.Color scoreColor) {
    final Region barTrack = new Region();
    barTrack.setMinWidth(METRIC_BAR_WIDTH);
    barTrack.setPrefWidth(METRIC_BAR_WIDTH);
    barTrack.setMaxWidth(METRIC_BAR_WIDTH);
    barTrack.setMinHeight(METRIC_BAR_HEIGHT);
    barTrack.setPrefHeight(METRIC_BAR_HEIGHT);
    barTrack.setMaxHeight(METRIC_BAR_HEIGHT);
    barTrack.setStyle(metricBarTrackStyle());

    final Region barFill = new Region();
    final double fillWidth = METRIC_BAR_WIDTH * score;
    barFill.setMinWidth(fillWidth);
    barFill.setPrefWidth(fillWidth);
    barFill.setMaxWidth(fillWidth);
    barFill.setMinHeight(METRIC_BAR_HEIGHT);
    barFill.setPrefHeight(METRIC_BAR_HEIGHT);
    barFill.setMaxHeight(METRIC_BAR_HEIGHT);
    barFill.setStyle("-fx-background-color: " + toCssColor(scoreColor)
        + "; -fx-background-radius: 3;");

    final Label barLabel = new Label(name + "  " + String.format("%.0f%%", score * 100d));
    barLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
    barLabel.setMouseTransparent(true);

    final StackPane barPane = new StackPane(barTrack, barFill, barLabel);
    StackPane.setAlignment(barTrack, Pos.CENTER_LEFT);
    StackPane.setAlignment(barFill, Pos.CENTER_LEFT);
    StackPane.setAlignment(barLabel, Pos.CENTER_LEFT);
    barPane.setAlignment(Pos.CENTER_LEFT);
    barPane.setMinWidth(METRIC_BAR_WIDTH);
    barPane.setPrefWidth(METRIC_BAR_WIDTH);
    barPane.setMaxWidth(METRIC_BAR_WIDTH);
    return barPane;
  }

  private @Nullable Region createDuplicateRowsAlert(final @NotNull MatchedLipid selectedAnnotation,
      final @NotNull List<FeatureListRow> duplicateRows) {
    if (duplicateRows.isEmpty()) {
      return null;
    }
    final javafx.scene.layout.FlowPane rowLinks = new javafx.scene.layout.FlowPane(4, 4);
    final Label title = new Label("Same annotation rows:");
    rowLinks.getChildren().add(title);
    for (final FeatureListRow duplicate : duplicateRows) {
      final javafx.scene.control.Hyperlink link = new javafx.scene.control.Hyperlink(
          "#" + duplicate.getID());
      link.setOnAction(_ -> {
        model.setRow(duplicate);
        FeatureTableFXUtil.selectAndScrollTo(duplicate, model.getFeatureTableFx());
      });
      rowLinks.getChildren().add(link);
    }
    final Button deleteSelectedRowButton = new Button("Delete selected");
    deleteSelectedRowButton.setOnAction(_ -> {
      if (row == null) {
        return;
      }
      removeAllAnnotationsFromRow(row);
      refreshAfterAnnotationDelete(row);
    });

    final Button deleteAllSameAnnotationRowsButton = new Button("Delete others");
    deleteAllSameAnnotationRowsButton.setOnAction(_ -> {
      final List<FeatureListRow> rowsToUpdate = getDuplicateRowsExcludingSelected(
          selectedAnnotation);
      for (final FeatureListRow sameAnnotationRow : rowsToUpdate) {
        removeAllAnnotationsFromRow(sameAnnotationRow);
      }
      refreshAfterAnnotationDelete(row);
    });

    final HBox actionButtons = new HBox(6, deleteSelectedRowButton,
        deleteAllSameAnnotationRowsButton);
    actionButtons.setAlignment(Pos.CENTER_LEFT);

    final VBox alertContainer = new VBox(6, rowLinks, actionButtons);
    alertContainer.setStyle(qualityWarningStyle());
    return alertContainer;
  }

  private @NotNull List<FeatureListRow> getDuplicateRowsExcludingSelected(
      final @NotNull MatchedLipid match) {
    final String annotation = Objects.toString(match.getLipidAnnotation().getAnnotation(), "");
    final int selectedRowId = row != null ? row.getID() : -1;
    return findDuplicateRowsExcludingSelected(featureList, selectedRowId, annotation);
  }

  private record QualityComputationResult(@Nullable String placeholderText,
                                          @Nullable MatchedLipid selectedAnnotation,
                                          @NotNull InterferenceMetrics interferenceMetrics,
                                          @NotNull List<FeatureListRow> duplicateRows,
                                          @NotNull List<QualityCardData> qualityCards) {

  }

  private record QualityCardData(@NotNull MatchedLipid match, @NotNull QualityMetric ms1,
                                 @NotNull QualityMetric ms2, @NotNull QualityMetric adduct,
                                 @NotNull QualityMetric isotope,
                                 @NotNull QualityMetric elutionOrder,
                                 @NotNull QualityMetric interference, double overall) {

  }

  private static final class QualityComputationTask extends FxUpdateTask<AnnotationQualityPane> {

    private final @Nullable FeatureListRow row;
    private final @Nullable ModularFeatureList featureList;
    private final boolean includeRetentionTimeAnalysis;
    private @NotNull QualityComputationResult result;

    private QualityComputationTask(final @NotNull AnnotationQualityPane pane,
        final @Nullable FeatureListRow row, final @Nullable ModularFeatureList featureList,
        final boolean includeRetentionTimeAnalysis) {
      super("lipidqc-quality-update", pane);
      this.row = row;
      this.featureList = featureList;
      this.includeRetentionTimeAnalysis = includeRetentionTimeAnalysis;
      result = new QualityComputationResult("Select a row with lipid annotations.", null,
          new InterferenceMetrics(0, 0), List.of(), List.of());
    }

    @Override
    protected void process() {
      if (row == null) {
        result = new QualityComputationResult("Select a row with lipid annotations.", null,
            new InterferenceMetrics(0, 0), List.of(), List.of());
        return;
      }
      final List<MatchedLipid> matches = row.getLipidMatches();
      if (matches.isEmpty()) {
        result = new QualityComputationResult("No lipid annotations available for selected row.",
            null, new InterferenceMetrics(0, 0), List.of(), List.of());
        return;
      }
      final List<MatchedLipid> matchSnapshot = List.copyOf(matches);
      final InterferenceMetrics interferenceMetrics = computeInterferenceMetrics(row);
      final MatchedLipid selectedAnnotation = matchSnapshot.getFirst();
      final String annotationText = Objects.toString(
          selectedAnnotation.getLipidAnnotation().getAnnotation(), "");
      final List<FeatureListRow> duplicateRows = findDuplicateRowsExcludingSelected(featureList,
          row.getID(), annotationText);
      final QualityMetric interference = new QualityMetric(
          computeInterferenceScore(interferenceMetrics.totalPenaltyCount()),
          interferenceDetail(interferenceMetrics));
      final List<QualityCardData> cards = new ArrayList<>(matchSnapshot.size());
      for (final MatchedLipid match : matchSnapshot) {
        final QualityMetric ms1 = evaluateMs1(row, match);
        final QualityMetric ms2 = evaluateMs2(match);
        final QualityMetric adduct = evaluateAdduct(row, match);
        final QualityMetric isotope = evaluateIsotope(row, match);
        final QualityMetric elutionOrder = includeRetentionTimeAnalysis
            ? evaluateElutionOrder(featureList, row, match)
            : new QualityMetric(0d, "Disabled for current lipid analysis mode");
        final double scoreSum = ms1.score() + ms2.score() + adduct.score() + isotope.score()
            + interference.score() + (includeRetentionTimeAnalysis ? elutionOrder.score() : 0d);
        final int scoreCount = includeRetentionTimeAnalysis ? 6 : 5;
        final double overall = scoreSum / scoreCount;
        cards.add(new QualityCardData(match, ms1, ms2, adduct, isotope, elutionOrder, interference,
            overall));
      }
      result = new QualityComputationResult(null, selectedAnnotation, interferenceMetrics,
          List.copyOf(duplicateRows), List.copyOf(cards));
    }

    @Override
    protected void updateGuiModel() {
      model.applyQualityResult(result);
    }

    @Override
    public String getTaskDescription() {
      return "Calculating lipid annotation quality cards";
    }

    @Override
    public double getFinishedPercentage() {
      return 0d;
    }
  }

  private static void removeAllAnnotationsFromRow(final @NotNull FeatureListRow targetRow) {
    targetRow.setLipidAnnotations(List.of());
  }

  private void removeMultiRowAnnotations() {
    if (featureList == null) {
      return;
    }
    final Map<FeatureListRow, Set<String>> removalPlan = buildMultiRowRemovalPlan(featureList,
        includeRetentionTimeAnalysis);
    final int removals = countPlannedAnnotationRemovals(removalPlan);
    if (removals <= 0) {
      MZmineCore.getDesktop()
          .displayMessage("Annotation actions", "No multi-row annotations would be removed.");
      return;
    }
    if (!confirmRemovalAction("Remove multi-row annotations", removals, removalPlan.size())) {
      return;
    }
    applyMultiRowRemovalPlan(removalPlan);
    refreshAfterAnnotationDelete(row);
  }

  private void setHighestScoreAnnotationOnAllRows() {
    if (featureList == null) {
      return;
    }
    final Map<FeatureListRow, MatchedLipid> bestMatchByRow = planBestAnnotationPerRow(featureList,
        includeRetentionTimeAnalysis);
    final int changedRows = countPlannedSetBestUpdates(bestMatchByRow);
    if (changedRows <= 0) {
      MZmineCore.getDesktop().displayMessage("Annotation actions",
          "All rows already use the highest-score annotation as selected annotation.");
      return;
    }
    if (!confirmSetBestAction(changedRows)) {
      return;
    }
    for (final Map.Entry<FeatureListRow, MatchedLipid> entry : bestMatchByRow.entrySet()) {
      final FeatureListRow candidateRow = entry.getKey();
      final MatchedLipid bestMatch = entry.getValue();
      final List<MatchedLipid> reordered = new ArrayList<>(candidateRow.getLipidMatches());
      if (!reordered.remove(bestMatch)) {
        continue;
      }
      reordered.add(0, bestMatch);
      candidateRow.setLipidAnnotations(reordered);
    }
    refreshAfterAnnotationDelete(row);
  }

  private boolean confirmRemovalAction(final @NotNull String actionName, final int removals,
      final int affectedRows) {
    final String rowText = affectedRows == 1 ? "1 row" : affectedRows + " rows";
    final String message =
        actionName + " will remove " + removals + " lipid annotations across " + rowText
            + ". Continue?";
    final Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES,
        ButtonType.NO);
    alert.setTitle("Confirm annotation cleanup");
    alert.setHeaderText(actionName);
    final Optional<ButtonType> result = alert.showAndWait();
    return result.isPresent() && ButtonType.YES.equals(result.get());
  }

  private boolean confirmSetBestAction(final int changedRows) {
    final String rowText = changedRows == 1 ? "1 row" : changedRows + " rows";
    final String message = "Set highest-score annotation as selected annotation for " + rowText
        + ". No annotations will be removed. Continue?";
    final Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES,
        ButtonType.NO);
    alert.setTitle("Confirm selected-annotation update");
    alert.setHeaderText("Set all rows to highest-score annotation");
    final Optional<ButtonType> result = alert.showAndWait();
    return result.isPresent() && ButtonType.YES.equals(result.get());
  }

  private static @NotNull Map<FeatureListRow, Set<String>> buildMultiRowRemovalPlan(
      final @NotNull ModularFeatureList featureList, final boolean includeRetentionTimeAnalysis) {
    final Map<String, List<RowAnnotationCandidate>> candidatesByAnnotation = new TreeMap<>();
    for (final FeatureListRow candidateRow : featureList.getRows()) {
      final List<MatchedLipid> rowMatches = candidateRow.getLipidMatches();
      if (rowMatches.isEmpty()) {
        continue;
      }
      for (final MatchedLipid match : rowMatches) {
        final String annotation = Objects.toString(match.getLipidAnnotation().getAnnotation(), "");
        if (annotation.isBlank()) {
          continue;
        }
        final double combinedScore = combinedAnnotationScore(featureList, candidateRow, match,
            includeRetentionTimeAnalysis);
        candidatesByAnnotation.computeIfAbsent(annotation, _ -> new ArrayList<>())
            .add(new RowAnnotationCandidate(candidateRow, match, combinedScore));
      }
    }

    final Map<FeatureListRow, Set<String>> removalPlan = new LinkedHashMap<>();
    for (final Map.Entry<String, List<RowAnnotationCandidate>> entry : candidatesByAnnotation.entrySet()) {
      final List<RowAnnotationCandidate> candidates = entry.getValue();
      if (candidates.size() <= 1) {
        continue;
      }
      final RowAnnotationCandidate best = candidates.stream()
          .max((a, b) -> Double.compare(a.combinedScore(), b.combinedScore())).orElse(null);
      if (best == null) {
        continue;
      }
      final int bestRowId = best.row().getID();
      for (final RowAnnotationCandidate candidate : candidates) {
        if (candidate.row().getID() == bestRowId) {
          continue;
        }
        removalPlan.computeIfAbsent(candidate.row(), _ -> new HashSet<>()).add(entry.getKey());
      }
    }
    return removalPlan;
  }

  private static int countPlannedAnnotationRemovals(
      final @NotNull Map<FeatureListRow, Set<String>> removalPlan) {
    int removals = 0;
    for (final Map.Entry<FeatureListRow, Set<String>> entry : removalPlan.entrySet()) {
      final FeatureListRow targetRow = entry.getKey();
      final Set<String> annotations = entry.getValue();
      for (final String annotation : annotations) {
        removals += (int) targetRow.getLipidMatches().stream()
            .filter(m -> annotation.equals(m.getLipidAnnotation().getAnnotation())).count();
      }
    }
    return removals;
  }

  private static void applyMultiRowRemovalPlan(
      final @NotNull Map<FeatureListRow, Set<String>> removalPlan) {
    for (final Map.Entry<FeatureListRow, Set<String>> entry : removalPlan.entrySet()) {
      final Set<String> annotationsToRemove = entry.getValue();
      final List<MatchedLipid> remaining = entry.getKey().getLipidMatches().stream()
          .filter(m -> !annotationsToRemove.contains(m.getLipidAnnotation().getAnnotation()))
          .toList();
      entry.getKey().setLipidAnnotations(remaining);
    }
  }

  private static @NotNull Map<FeatureListRow, MatchedLipid> planBestAnnotationPerRow(
      final @NotNull ModularFeatureList featureList, final boolean includeRetentionTimeAnalysis) {
    final Map<FeatureListRow, MatchedLipid> bestMatchByRow = new LinkedHashMap<>();
    for (final FeatureListRow candidateRow : featureList.getRows()) {
      final List<MatchedLipid> matches = candidateRow.getLipidMatches();
      if (matches.size() <= 1) {
        continue;
      }
      final MatchedLipid bestMatch = matches.stream().max(
          (a, b) -> Double.compare(
              combinedAnnotationScore(featureList, candidateRow, a, includeRetentionTimeAnalysis),
              combinedAnnotationScore(featureList, candidateRow, b,
                  includeRetentionTimeAnalysis))).orElse(null);
      if (bestMatch != null) {
        bestMatchByRow.put(candidateRow, bestMatch);
      }
    }
    return bestMatchByRow;
  }

  private static int countPlannedSetBestUpdates(
      final @NotNull Map<FeatureListRow, MatchedLipid> bestMatchByRow) {
    int changes = 0;
    for (final Map.Entry<FeatureListRow, MatchedLipid> entry : bestMatchByRow.entrySet()) {
      final List<MatchedLipid> matches = entry.getKey().getLipidMatches();
      if (!matches.isEmpty() && !Objects.equals(matches.getFirst(), entry.getValue())) {
        changes++;
      }
    }
    return changes;
  }

  private static double combinedAnnotationScore(final @NotNull ModularFeatureList featureList,
      final @NotNull FeatureListRow row, final @NotNull MatchedLipid match,
      final boolean includeRetentionTimeAnalysis) {
    return computeCombinedAnnotationScore(featureList, row, match, true, includeRetentionTimeAnalysis);
  }

  private record RowAnnotationCandidate(@NotNull FeatureListRow row, @NotNull MatchedLipid match,
                                        double combinedScore) {

  }

  private void refreshAfterAnnotationDelete(final @Nullable FeatureListRow preferredRow) {
    model.setFeatureList(model.getFeatureList());
    model.getFeatureTableFx().refresh();
    final FeatureListRow rowToSelect = preferredRow != null ? preferredRow : row;
    if (rowToSelect != null) {
      model.setRow(null);
      model.setRow(rowToSelect);
      FeatureTableFXUtil.selectAndScrollTo(rowToSelect, model.getFeatureTableFx());
    }
    if (onAnnotationsChanged != null) {
      onAnnotationsChanged.run();
    }
    refreshQualityImmediately();
  }

  private void refreshQualityImmediately() {
    scheduler.cancelTasks();
    scheduler.onTaskThread(
        new QualityComputationTask(this, row, featureList, includeRetentionTimeAnalysis));
  }

  private static @NotNull QualityMetric evaluateMs1(final @NotNull FeatureListRow row,
      final @NotNull MatchedLipid match) {
    final double exactMz = MatchedLipid.getExactMass(match);
    final double observedMz =
        match.getAccurateMz() != null ? match.getAccurateMz() : row.getAverageMZ();
    final double ppm = (observedMz - exactMz) / exactMz * 1e6;
    final double absPpm = Math.abs(ppm);
    final double score = Double.isFinite(absPpm) ? clampToUnit(1d - Math.min(absPpm, 5d) / 5d) : 0d;
    return new QualityMetric(score, String.format("%.2f ppm", ppm));
  }

  private static @NotNull QualityMetric evaluateMs2(final @NotNull MatchedLipid match) {
    final double explained = clampToUnit(match.getMsMsScore() == null ? 0d : match.getMsMsScore());
    return new QualityMetric(explained,
        String.format("%.1f", explained * 100d) + "% explained intensity");
  }

  private static @NotNull QualityMetric evaluateAdduct(final @NotNull FeatureListRow row,
      final @NotNull MatchedLipid match) {
    if (row.getBestIonIdentity() == null) {
      return new QualityMetric(0d, "No ion identity available for cross-check");
    }
    final String featureAdduct = normalizeAdduct(row.getBestIonIdentity().getAdduct());
    final String lipidAdduct = normalizeAdduct(match.getIonizationType().getAdductName());
    final boolean matchFound = featureAdduct.equals(lipidAdduct);
    final String detail = "Feature: " + row.getBestIonIdentity().getAdduct() + " vs Lipid: "
        + match.getIonizationType().getAdductName();
    return new QualityMetric(matchFound ? 1d : 0d, detail);
  }

  private static @NotNull QualityMetric evaluateIsotope(final @NotNull FeatureListRow row,
      final @NotNull MatchedLipid match) {
    if (row.getBestIsotopePattern() == null || match.getIsotopePattern() == null) {
      return new QualityMetric(0.35, "Missing measured or theoretical isotope pattern");
    }
    final float score = io.github.mzmine.modules.tools.isotopepatternscore.IsotopePatternScoreCalculator.getSimilarityScore(
        row.getBestIsotopePattern(), match.getIsotopePattern(),
        new io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance(0.003, 10d), 0d);
    return new QualityMetric(score, "Similarity score " + String.format("%.2f", score));
  }

  private static @NotNull QualityMetric evaluateElutionOrder(
      final @Nullable ModularFeatureList featureList, final @NotNull FeatureListRow row,
      final @NotNull MatchedLipid match) {
    if (featureList == null || row.getAverageRT() == null) {
      return new QualityMetric(0.4, "Missing RT context");
    }
    final ElutionOrderMetrics metrics = computeElutionOrderMetrics(featureList, row, match);
    return new QualityMetric(metrics.combinedScore(), formatElutionOrderDetail(metrics));
  }

  private static @NotNull javafx.scene.paint.Color qualityScoreColor(final double score) {
    final SimpleColorPalette defaultPalette = ConfigService.getDefaultColorPalette();
    final PaintScale scoreScale = new SimpleColorPalette(defaultPalette.getNegativeColor(),
        defaultPalette.getNeutralColor(), defaultPalette.getPositiveColor()).toPaintScale(
        PaintScaleTransform.LINEAR, Range.closed(0d, 1d));
    final Paint awtPaint = scoreScale.getPaint(clampToUnit(score));
    if (awtPaint instanceof Color awtColor) {
      return FxColorUtil.awtColorToFX(awtColor);
    }
    return javafx.scene.paint.Color.GRAY;
  }

  private static @NotNull String toCssColor(final @NotNull javafx.scene.paint.Color color) {
    return FxColorUtil.colorToHex(color);
  }

  private static String normalizeAdduct(String adduct) {
    return adduct == null ? "" : adduct.replaceAll("\\s+", "").toLowerCase();
  }

  private static int extractDbe(ILipidAnnotation lipidAnnotation) {
    if (lipidAnnotation instanceof MolecularSpeciesLevelAnnotation molecularAnnotation) {
      return MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
          molecularAnnotation.getAnnotation()).getValue();
    }
    if (lipidAnnotation instanceof SpeciesLevelAnnotation speciesAnnotation) {
      return MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
          speciesAnnotation.getAnnotation()).getValue();
    }
    return -1;
  }

  private static int extractCarbons(ILipidAnnotation lipidAnnotation) {
    if (lipidAnnotation instanceof MolecularSpeciesLevelAnnotation molecularAnnotation) {
      return MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
          molecularAnnotation.getAnnotation()).getKey();
    }
    if (lipidAnnotation instanceof SpeciesLevelAnnotation speciesAnnotation) {
      return MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
          speciesAnnotation.getAnnotation()).getKey();
    }
    return -1;
  }

  private static double predictRtByLinearFit(List<double[]> points, double x) {
    double sumX = 0;
    double sumY = 0;
    double sumXY = 0;
    double sumXX = 0;
    for (double[] p : points) {
      sumX += p[0];
      sumY += p[1];
      sumXY += p[0] * p[1];
      sumXX += p[0] * p[0];
    }
    final int n = points.size();
    final double denom = n * sumXX - sumX * sumX;
    if (Math.abs(denom) < 1e-8) {
      return sumY / n;
    }
    final double slope = (n * sumXY - sumX * sumY) / denom;
    final double intercept = (sumY - slope * sumX) / n;
    return intercept + slope * x;
  }

  private static double estimateResidualStd(List<double[]> points) {
    if (points.size() < 3) {
      return 0.1d;
    }
    double meanX = points.stream().mapToDouble(p -> p[0]).average().orElse(0d);
    double meanY = points.stream().mapToDouble(p -> p[1]).average().orElse(0d);
    double numerator = 0d;
    double denominator = 0d;
    for (double[] p : points) {
      numerator += (p[0] - meanX) * (p[1] - meanY);
      denominator += (p[0] - meanX) * (p[0] - meanX);
    }
    final double slope = denominator == 0d ? 0d : numerator / denominator;
    final double intercept = meanY - slope * meanX;
    double ss = 0d;
    for (double[] p : points) {
      final double residual = p[1] - (intercept + slope * p[0]);
      ss += residual * residual;
    }
    return Math.sqrt(ss / Math.max(1, points.size() - 2));
  }

  private void showPlaceholder(String text) {
    placeholder.setText(text);
    setCenter(placeholder);
  }

  private static String qualityWarningStyle() {
    final Color negative = ConfigService.getDefaultColorPalette().getNegativeColorAWT();
    final String borderColor = "rgb(%d,%d,%d)".formatted(negative.getRed(), negative.getGreen(),
        negative.getBlue());
    return MZmineCore.getConfiguration().isDarkMode() ?
        "-fx-background-color: transparent; -fx-border-color: " + borderColor + "; -fx-padding: 6;"
        : "-fx-background-color: #fff3cd; -fx-border-color: " + borderColor + "; -fx-padding: 6;";
  }

  private static String qualityCardStyle() {
    return MZmineCore.getConfiguration().isDarkMode()
        ? "-fx-background-color: transparent; -fx-border-color: #5b5b5b; -fx-padding: 8;"
        : "-fx-background-color: #ffffff; -fx-border-color: #d0d0d0; -fx-padding: 8;";
  }

  private static @NotNull String metricBarTrackStyle() {
    return MZmineCore.getConfiguration().isDarkMode()
        ? "-fx-background-color: rgba(255,255,255,0.14); -fx-border-color: #5b5b5b; -fx-background-radius: 3; -fx-border-radius: 3;"
        : "-fx-background-color: #f0f0f0; -fx-border-color: #d0d0d0; -fx-background-radius: 3; -fx-border-radius: 3;";
  }

  private record QualityMetric(double score, String detail) {

  }
}
