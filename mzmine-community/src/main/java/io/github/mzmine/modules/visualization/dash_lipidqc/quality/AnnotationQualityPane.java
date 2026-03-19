/*
 * Copyright (c) 2004-2026 The mzmine Development Team
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

package io.github.mzmine.modules.visualization.dash_lipidqc.quality;

import static io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils.clampToUnit;
import static io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils.computeCombinedAnnotationScore;
import static io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils.computeElutionOrderMetrics;
import static io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils.formatElutionOrderDetail;
import static io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils.interferenceDetail;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.gui.DesktopService;
import io.github.mzmine.gui.chartbasics.chartutils.paintscales.PaintScaleTransform;
import io.github.mzmine.javafx.components.factories.FxButtons;
import io.github.mzmine.javafx.components.factories.FxLabels;
import io.github.mzmine.javafx.util.FxColorUtil;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataprocessing.filter_lipidannotationcleanup.LipidAnnotationCleanupModule;
import io.github.mzmine.modules.dataprocessing.filter_lipidannotationcleanup.LipidAnnotationCleanupParameters;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnalysisType;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnnotationUtils;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils;
import io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils.ElutionOrderMetrics;
import io.github.mzmine.modules.dataprocessing.id_lipidid.scoring.LipidQcScoringUtils.InterferenceMetrics;
import io.github.mzmine.modules.visualization.dash_lipidqc.DashboardComputationPane;
import io.github.mzmine.modules.visualization.dash_lipidqc.LipidAnnotationQCDashboardModel;
import io.github.mzmine.modules.visualization.dash_lipidqc.LipidQcAnnotationSelectionUtils;
import io.github.mzmine.modules.visualization.dash_lipidqc.kendrick.KendrickFalseNegativeCandidate;
import io.github.mzmine.modules.visualization.dash_lipidqc.kendrick.KendrickReviewMode;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.util.ExitCode;
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
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Accordion;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.renderer.PaintScale;

/**
 * Dashboard panel that shows detailed per-metric quality cards (MS1, MS2, adduct, isotope, elution
 * order, interference) for the selected lipid annotation, and provides access to the multi-row
 * annotation cleanup workflow.
 */
public class AnnotationQualityPane extends DashboardComputationPane {

  private static final double METRIC_BAR_WIDTH = 230d;
  private static final double METRIC_BAR_HEIGHT = 16d;
  private static final double ISOMER_EXACT_MASS_TOLERANCE = 1e-6d;
  private static final double OVERALL_SCORE_TIE_TOLERANCE = 1e-6d;
  private final @NotNull LipidAnnotationQCDashboardModel model;
  private final VBox content = new VBox(8);
  private final ScrollPane scrollPane = new ScrollPane();
  private final @NotNull Button removeMultiRowAnnotationsButton;
  private final @NotNull Button setBestPerRowButton;
  private @NotNull KendrickReviewMode kendrickReviewMode = KendrickReviewMode.NONE;
  private @Nullable Runnable onAnnotationsChanged;

  public AnnotationQualityPane(final @NotNull LipidAnnotationQCDashboardModel model) {
    super("Select a row with lipid annotations.");
    this.model = model;
    model.featureListProperty().subscribe(_ -> requestUpdate());
    model.rowProperty().subscribe(_ -> requestUpdate());
    model.retentionTimeAnalysisEnabledProperty().subscribe(_ -> requestUpdate());
    scrollPane.setFitToWidth(true);
    scrollPane.setContent(content);
    removeMultiRowAnnotationsButton = FxButtons.createButton("Remove multi-row annotations",
        this::removeMultiRowAnnotations);
    setBestPerRowButton = FxButtons.createButton("Set all rows to highest-score annotation",
        this::setHighestScoreAnnotationOnAllRows);
    final VBox actionBox = new VBox(6, removeMultiRowAnnotationsButton, setBestPerRowButton);
    actionBox.setAlignment(Pos.TOP_LEFT);
    final TitledPane actionsPane = new TitledPane("Annotation actions", actionBox);
    actionsPane.setCollapsible(true);
    final Accordion actionsAccordion = new Accordion(actionsPane);
    actionsAccordion.setExpandedPane(null);
    setBottom(actionsAccordion);
  }

  public void setOnAnnotationsChanged(final @Nullable Runnable onAnnotationsChanged) {
    this.onAnnotationsChanged = onAnnotationsChanged;
  }

  public void setKendrickReviewMode(final @NotNull KendrickReviewMode kendrickReviewMode) {
    if (this.kendrickReviewMode == kendrickReviewMode) {
      return;
    }
    this.kendrickReviewMode = kendrickReviewMode;
    requestUpdate();
  }

  public void requestUpdate() {
    scheduleUpdate(new QualityComputationTask(this, model.getRow(), model.getFeatureList(),
        model.isRetentionTimeAnalysisEnabled(), kendrickReviewMode));
  }

  void applyQualityResult(final @NotNull QualityComputationResult result) {
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
      warning.setWrapText(true);
      warning.setMaxWidth(Double.MAX_VALUE);
      warning.setStyle(qualityWarningStyle());
      content.getChildren().add(warning);
    }
    if (result.falsePositiveReason() != null && !result.falsePositiveReason().isBlank()) {
      final Label kendrickReason = new Label(
          "Potential false positive: " + result.falsePositiveReason());
      kendrickReason.setWrapText(true);
      kendrickReason.setMaxWidth(Double.MAX_VALUE);
      kendrickReason.setStyle(qualityWarningStyle());
      content.getChildren().add(kendrickReason);
    }
    final @Nullable String isomerScoreTieWarning = detectIsomerScoreTieWarning(
        result.qualityCards());
    if (isomerScoreTieWarning != null) {
      final Label tieWarning = new Label(isomerScoreTieWarning);
      tieWarning.setWrapText(true);
      tieWarning.setMaxWidth(Double.MAX_VALUE);
      tieWarning.setStyle(qualityWarningStyle());
      content.getChildren().add(tieWarning);
    }
    if (result.falseNegativeCandidate() != null && result.falseNegativeQualityCard() != null) {
      final Label falseNegativeReason = new Label(
          "Potential false negative: " + result.falseNegativeCandidate().detail());
      falseNegativeReason.setWrapText(true);
      falseNegativeReason.setMaxWidth(Double.MAX_VALUE);
      falseNegativeReason.setStyle(qualityWarningStyle());
      content.getChildren().add(falseNegativeReason);
      content.getChildren().add(createPotentialFalseNegativeCard(result.falseNegativeCandidate(),
          result.falseNegativeQualityCard()));
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

  static @NotNull List<FeatureListRow> findDuplicateRowsExcludingSelected(
      final @Nullable ModularFeatureList featureList, final int selectedRowId,
      final @NotNull String annotation) {
    if (featureList == null || annotation.isBlank()) {
      return List.of();
    }
    return featureList.getRows().stream().filter(r -> Objects.equals(
            Optional.ofNullable(LipidQcAnnotationSelectionUtils.getPreferredLipidMatch(r))
                .map(match -> match.getLipidAnnotation().getAnnotation()).orElse(null), annotation))
        .filter(r -> r.getID() != selectedRowId)
        .sorted((a, b) -> Integer.compare(a.getID(), b.getID())).map(r -> (FeatureListRow) r)
        .toList();
  }

  private Region createQualityCard(final @NotNull QualityCardData qualityCardData) {
    final VBox card = new VBox(6);
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
    if (model.isRetentionTimeAnalysisEnabled()) {
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
    final Label annotation = FxLabels.newLabel(formatCardTitle(match));
    annotation.setStyle("-fx-font-weight: bold;");
    final Button deleteButton = FxButtons.createButton("Delete annotation",
        () -> deleteAnnotationFromSelectedRowWithConfirmation(match));

    final Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    final HBox header = new HBox(8, annotation, spacer, deleteButton);
    header.setAlignment(Pos.CENTER_LEFT);
    return header;
  }

  private @NotNull Region createPotentialFalseNegativeCard(
      final @NotNull KendrickFalseNegativeCandidate candidate,
      final @NotNull QualityCardData qualityCardData) {
    final VBox card = new VBox(6);
    card.setStyle(qualityCardStyle());

    final Label title = FxLabels.newLabel(
        "Potentially missed annotation: " + formatCardTitle(candidate.match()));
    title.setStyle("-fx-font-weight: bold;");
    title.setWrapText(true);

    final Button convertButton = FxButtons.createButton("Add annotation",
        () -> addPotentialFalseNegativeAnnotation(candidate));
    final Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    final HBox header = new HBox(8, title, spacer, convertButton);
    header.setAlignment(Pos.CENTER_LEFT);

    card.getChildren().add(header);
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
    if (model.isRetentionTimeAnalysisEnabled()) {
      card.getChildren().add(
          createMetricRow("Elution order score", qualityCardData.elutionOrder().score(),
              qualityCardData.elutionOrder().detail()));
    }
    card.getChildren().add(
        createMetricRow("Interference risk", qualityCardData.interference().score(),
            qualityCardData.interference().detail()));
    return card;
  }

  private void addPotentialFalseNegativeAnnotation(
      final @NotNull KendrickFalseNegativeCandidate candidate) {
    final @Nullable FeatureListRow row = model.getRow();
    if (row == null) {
      return;
    }
    final @NotNull ModularFeatureList featureList = model.getFeatureList();
    final Set<MatchedLipid> annotationsToAdd = new HashSet<>();
    annotationsToAdd.add(candidate.match());
    final LipidAnalysisType analysisType = Objects.requireNonNullElse(
        LipidQcScoringUtils.detectLipidAnalysisType(featureList),
        LipidAnalysisType.LC_REVERSED_PHASE);
    LipidAnnotationUtils.addAnnotationsToFeatureList(row, annotationsToAdd, analysisType, false, 0d,
        null, LipidQcScoringUtils.detectMs1Tolerance(featureList));
    refreshAfterAnnotationDelete(row);
  }

  private static @NotNull String formatCardTitle(final @NotNull MatchedLipid match) {
    final String annotation = Objects.toString(match.getLipidAnnotation().getAnnotation(),
        "Unknown annotation");
    final @Nullable String adductName =
        match.getIonizationType() != null ? match.getIonizationType().getAdductName() : null;
    if (adductName == null || adductName.isBlank()) {
      return annotation;
    }
    return annotation + " " + adductName;
  }

  private void deleteAnnotationFromSelectedRowWithConfirmation(final @NotNull MatchedLipid match) {
    final @Nullable FeatureListRow row = model.getRow();
    if (row == null) {
      return;
    }
    if (!confirmDeleteAnnotation(match, row)) {
      return;
    }
    deleteAnnotationFromRow(match, row);
    refreshAfterAnnotationDelete(row);
  }

  private boolean confirmDeleteAnnotation(final @NotNull MatchedLipid match,
      final @NotNull FeatureListRow row) {
    final String annotation = Objects.toString(match.getLipidAnnotation().getAnnotation(),
        "Unknown annotation");
    final String message =
        "Delete annotation \"" + annotation + "\" from selected row #" + row.getID() + "?";
    final Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES,
        ButtonType.NO);
    alert.setTitle("Confirm annotation deletion");
    alert.setHeaderText("Delete annotation");
    final Optional<ButtonType> result = alert.showAndWait();
    return result.isPresent() && ButtonType.YES.equals(result.get());
  }

  private static void deleteAnnotationFromRow(final @NotNull MatchedLipid match,
      final @NotNull FeatureListRow row) {
    final List<MatchedLipid> remaining = row.getLipidMatches().stream()
        .filter(existingMatch -> !Objects.equals(existingMatch, match)).toList();
    row.setLipidAnnotations(remaining);
  }

  private Region createMetricRow(final @NotNull String name, final double score,
      final @NotNull String detail) {
    final double clipped = Math.max(0d, Math.min(1d, score));
    final javafx.scene.paint.Color scoreColor = qualityScoreColor(clipped);
    final StackPane barPane = createMetricBar(name, clipped, scoreColor);

    final Label detailLabel = FxLabels.newLabel(detail);
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
    barFill.setStyle(
        "-fx-background-color: " + toCssColor(scoreColor) + "; -fx-background-radius: 3;");

    final Label barLabel = FxLabels.newLabel(name + "  " + String.format("%.0f%%", score * 100d));
    barLabel.setStyle(metricBarLabelStyle(score));
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
    final FlowPane rowLinks = new FlowPane(4, 4);
    rowLinks.prefWrapLengthProperty().bind(widthProperty().subtract(36));
    final Label title = FxLabels.newLabel("Same annotation rows:");
    title.setWrapText(true);
    rowLinks.getChildren().add(title);
    for (final FeatureListRow duplicate : duplicateRows) {
      final Hyperlink link = new Hyperlink("#" + duplicate.getID());
      link.setOnAction(_ -> {
        model.setRow(duplicate);
        FeatureTableFXUtil.selectAndScrollTo(duplicate, model.getFeatureTableFx());
      });
      rowLinks.getChildren().add(link);
    }
    final Button deleteSelectedRowButton = FxButtons.createButton("Delete selected", () -> {
      final @Nullable FeatureListRow currentRow = model.getRow();
      if (currentRow == null) {
        return;
      }
      removeAllAnnotationsFromRow(currentRow);
      refreshAfterAnnotationDelete(currentRow);
    });

    final Button deleteAllSameAnnotationRowsButton = FxButtons.createButton("Delete others", () -> {
      final List<FeatureListRow> rowsToUpdate = getDuplicateRowsExcludingSelected(
          selectedAnnotation);
      for (final FeatureListRow sameAnnotationRow : rowsToUpdate) {
        removeAllAnnotationsFromRow(sameAnnotationRow);
      }
      refreshAfterAnnotationDelete(model.getRow());
    });

    final FlowPane actionButtons = new FlowPane(6, 6, deleteSelectedRowButton,
        deleteAllSameAnnotationRowsButton);
    actionButtons.prefWrapLengthProperty().bind(widthProperty().subtract(36));
    actionButtons.setAlignment(Pos.CENTER_LEFT);

    final VBox alertContainer = new VBox(6, rowLinks, actionButtons);
    alertContainer.setFillWidth(true);
    alertContainer.setStyle(qualityWarningStyle());
    return alertContainer;
  }

  private @NotNull List<FeatureListRow> getDuplicateRowsExcludingSelected(
      final @NotNull MatchedLipid match) {
    final String annotation = Objects.toString(match.getLipidAnnotation().getAnnotation(), "");
    final @Nullable FeatureListRow row = model.getRow();
    final int selectedRowId = row != null ? row.getID() : -1;
    return findDuplicateRowsExcludingSelected(model.getFeatureList(), selectedRowId, annotation);
  }


  private static void removeAllAnnotationsFromRow(final @NotNull FeatureListRow targetRow) {
    targetRow.setLipidAnnotations(List.of());
  }

  private void removeMultiRowAnnotations() {
    final ParameterSet parameters = MZmineCore.getConfiguration()
        .getModuleParameters(LipidAnnotationCleanupModule.class).cloneParameterSet();
    parameters.getParameter(LipidAnnotationCleanupParameters.featureLists)
        .setValue(new FeatureListsSelection(model.getFeatureList()));
    parameters.getParameter(LipidAnnotationCleanupParameters.includeRetentionTimeInScoring)
        .setValue(model.isRetentionTimeAnalysisEnabled());
    if (parameters.showSetupDialog(true) != ExitCode.OK) {
      return;
    }
    final FeatureListRow currentRow = model.getRow();
    final List<Task> tasks = MZmineCore.runMZmineModule(LipidAnnotationCleanupModule.class,
        parameters);
    tasks.forEach(task -> task.setOnFinished(
        () -> Platform.runLater(() -> refreshAfterAnnotationDelete(currentRow))));
  }

  private void setHighestScoreAnnotationOnAllRows() {
    final @NotNull ModularFeatureList featureList = model.getFeatureList();
    final Map<FeatureListRow, MatchedLipid> bestMatchByRow = planBestAnnotationPerRow(featureList,
        model.isRetentionTimeAnalysisEnabled());
    final int changedRows = countPlannedSetBestUpdates(bestMatchByRow);
    if (changedRows <= 0) {
      DesktopService.getDesktop().displayMessage("Annotation actions",
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
    refreshAfterAnnotationDelete(model.getRow());
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

  private static @NotNull Map<FeatureListRow, MatchedLipid> planBestAnnotationPerRow(
      final @NotNull ModularFeatureList featureList, final boolean includeRetentionTimeAnalysis) {
    final Map<FeatureListRow, MatchedLipid> bestMatchByRow = new LinkedHashMap<>();
    for (final FeatureListRow candidateRow : featureList.getRows()) {
      final List<MatchedLipid> matches = candidateRow.getLipidMatches();
      if (matches.size() <= 1) {
        continue;
      }
      final MatchedLipid bestMatch = matches.stream().max((a, b) -> Double.compare(
              combinedAnnotationScore(featureList, candidateRow, a, includeRetentionTimeAnalysis),
              combinedAnnotationScore(featureList, candidateRow, b, includeRetentionTimeAnalysis)))
          .orElse(null);
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
    return computeCombinedAnnotationScore(featureList, row, match, true,
        includeRetentionTimeAnalysis);
  }

  private void refreshAfterAnnotationDelete(final @Nullable FeatureListRow preferredRow) {
    LipidQcScoringUtils.rescoreOverallQualityScores(model.getFeatureList());
    model.getFeatureTableFx().refresh();
    final FeatureListRow rowToSelect = preferredRow != null ? preferredRow : model.getRow();
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
    scheduler.onTaskThread(new QualityComputationTask(this, model.getRow(), model.getFeatureList(),
        model.isRetentionTimeAnalysisEnabled(), kendrickReviewMode));
  }

  static @NotNull QualityMetric evaluateMs1(final @NotNull FeatureListRow row,
      final @Nullable ModularFeatureList featureList, final @NotNull MatchedLipid match) {
    final double exactMz = MatchedLipid.getExactMass(match);
    final double observedMz =
        match.getAccurateMz() != null ? match.getAccurateMz() : row.getAverageMZ();
    final double ppm = (observedMz - exactMz) / exactMz * 1e6;
    final @Nullable MZTolerance ms1Tolerance =
        featureList == null ? null : LipidQcScoringUtils.detectMs1Tolerance(featureList);
    final double score = LipidQcScoringUtils.computeMs1Score(row, match, ms1Tolerance);
    return new QualityMetric(score, String.format("%.2f ppm", ppm));
  }

  static @NotNull QualityMetric evaluateMs2(final @NotNull MatchedLipid match) {
    final double explained = clampToUnit(match.getMsMsScore() == null ? 0d : match.getMsMsScore());
    return new QualityMetric(explained,
        String.format("%.1f", explained * 100d) + "% explained intensity");
  }

  static @NotNull QualityMetric evaluateAdduct(final @NotNull FeatureListRow row,
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

  static @NotNull QualityMetric evaluateIsotope(final @NotNull FeatureListRow row,
      final @NotNull MatchedLipid match) {
    if (row.getBestIsotopePattern() == null || match.getIsotopePattern() == null) {
      return new QualityMetric(0d, "Missing measured or theoretical isotope pattern");
    }
    final float score = io.github.mzmine.modules.tools.isotopepatternscore.IsotopePatternScoreCalculator.getSimilarityScore(
        row.getBestIsotopePattern(), match.getIsotopePattern(),
        new io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance(0.003, 10d), 0d);
    return new QualityMetric(score, "Similarity score " + String.format("%.2f", score));
  }

  static @NotNull QualityMetric evaluateElutionOrder(final @Nullable ModularFeatureList featureList,
      final @NotNull FeatureListRow row, final @NotNull MatchedLipid match) {
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

  private static @NotNull String normalizeAdduct(final @Nullable String adduct) {
    return adduct == null ? "" : adduct.replaceAll("\\s+", "").toLowerCase();
  }

  private static @Nullable String detectIsomerScoreTieWarning(
      final @NotNull List<QualityCardData> qualityCards) {
    if (qualityCards.size() < 2) {
      return null;
    }

    for (int i = 0; i < qualityCards.size() - 1; i++) {
      final QualityCardData left = qualityCards.get(i);
      for (int j = i + 1; j < qualityCards.size(); j++) {
        final QualityCardData right = qualityCards.get(j);
        if (!isIsomerScoreTie(left, right)) {
          continue;
        }
        return "Potential isomer tie: " + formatCardTitle(left.match()) + " and " + formatCardTitle(
            right.match()) + " have the same overall quality score (" + String.format("%.1f%%",
            left.overall() * 100d) + ").";
      }
    }
    return null;
  }

  private static boolean isIsomerScoreTie(final @NotNull QualityCardData left,
      final @NotNull QualityCardData right) {
    if (Math.abs(left.overall() - right.overall()) > OVERALL_SCORE_TIE_TOLERANCE) {
      return false;
    }

    final String leftAnnotation = Objects.toString(
        left.match().getLipidAnnotation().getAnnotation(), "");
    final String rightAnnotation = Objects.toString(
        right.match().getLipidAnnotation().getAnnotation(), "");
    if (leftAnnotation.equals(rightAnnotation)) {
      return false;
    }

    final double leftExactMass = MatchedLipid.getExactMass(left.match());
    final double rightExactMass = MatchedLipid.getExactMass(right.match());
    return Double.isFinite(leftExactMass) && Double.isFinite(rightExactMass)
        && Math.abs(leftExactMass - rightExactMass) <= ISOMER_EXACT_MASS_TOLERANCE;
  }

  private static @NotNull String qualityWarningStyle() {
    final Color negative = ConfigService.getDefaultColorPalette().getNegativeColorAWT();
    final String borderColor = "rgb(%d,%d,%d)".formatted(negative.getRed(), negative.getGreen(),
        negative.getBlue());
    return ConfigService.getConfiguration().isDarkMode() ?
        "-fx-background-color: transparent; -fx-border-color: " + borderColor + "; -fx-padding: 6;"
        : "-fx-background-color: #fff3cd; -fx-border-color: " + borderColor + "; -fx-padding: 6;";
  }

  private static @NotNull String qualityCardStyle() {
    return ConfigService.getConfiguration().isDarkMode()
        ? "-fx-background-color: transparent; -fx-border-color: #5b5b5b; -fx-padding: 8;"
        : "-fx-background-color: #ffffff; -fx-border-color: #d0d0d0; -fx-padding: 8;";
  }

  private static @NotNull String metricBarTrackStyle() {
    return ConfigService.getConfiguration().isDarkMode()
        ? "-fx-background-color: rgba(255,255,255,0.14); -fx-border-color: #5b5b5b; -fx-background-radius: 3; -fx-border-radius: 3;"
        : "-fx-background-color: #f0f0f0; -fx-border-color: #d0d0d0; -fx-background-radius: 3; -fx-border-radius: 3;";
  }

  private static @NotNull String metricBarLabelStyle(final double score) {
    final String textColor =
        ConfigService.getConfiguration().isDarkMode() || score >= 0.5d ? "white" : "#1f1f1f";
    return "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + textColor
        + " !important;";
  }
}
