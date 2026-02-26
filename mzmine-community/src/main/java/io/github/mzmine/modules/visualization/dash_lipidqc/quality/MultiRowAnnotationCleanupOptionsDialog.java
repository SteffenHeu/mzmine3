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

package io.github.mzmine.modules.visualization.dash_lipidqc.quality;

import io.github.mzmine.datamodel.IonizationType;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.javafx.components.factories.FxComboBox;
import io.github.mzmine.javafx.components.factories.FxLabels;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class MultiRowAnnotationCleanupOptionsDialog {

  private final @NotNull ModularFeatureList featureList;
  private final boolean includeRetentionTimeAnalysis;
  private final @NotNull Map<String, ComboBox<IonizationType>> ionComboByLipidClass = new LinkedHashMap<>();
  private final @NotNull Map<String, CheckBox> keepHighestByLipidClass = new LinkedHashMap<>();
  private final @NotNull CheckBox alwaysKeepHighestScore;
  private final @NotNull ComboBox<MultiRowAnnotationCleanupRowHandlingMode> rowHandlingModeCombo;
  private final @NotNull Label ionPreferenceLabel = FxLabels.newLabel("Preferred ion per lipid class:");
  private final @NotNull ScrollPane ionPreferenceScrollPane;
  private final @NotNull Label summaryLabel = FxLabels.newLabel("");
  private final @NotNull Dialog<ButtonType> dialog = new Dialog<>();
  private final @NotNull ButtonType applyButtonType = new ButtonType("Apply", ButtonData.OK_DONE);
  private @Nullable MultiRowAnnotationCleanupPlan lastComputedPlan;

  MultiRowAnnotationCleanupOptionsDialog(final @NotNull ModularFeatureList featureList,
      final boolean includeRetentionTimeAnalysis) {
    this.featureList = featureList;
    this.includeRetentionTimeAnalysis = includeRetentionTimeAnalysis;

    final Map<String, List<IonizationType>> ionsByClass =
        MultiRowAnnotationCleanupPlanner.collectAvailableIonizationsByLipidClass(featureList);
    final Map<String, IonizationType> defaultPreferredByClass =
        MultiRowAnnotationCleanupPlanner.defaultPreferredIonizationByLipidClass(featureList);

    alwaysKeepHighestScore = new CheckBox(
        "Always keep highest-score lipid annotation regardless of preferred ion");
    alwaysKeepHighestScore.setSelected(true);
    alwaysKeepHighestScore.selectedProperty().addListener((_, _, _) -> {
      updateIonPreferenceControlState();
      updateSummaryAndState();
    });

    rowHandlingModeCombo = FxComboBox.createComboBox(null,
        List.of(MultiRowAnnotationCleanupRowHandlingMode.values()));
    rowHandlingModeCombo.getSelectionModel()
        .select(MultiRowAnnotationCleanupRowHandlingMode.DISCARD_LOWER_THAN_REMOVED);
    rowHandlingModeCombo.valueProperty().addListener((_, _, _) -> updateSummaryAndState());

    final GridPane ionPreferences = new GridPane();
    ionPreferences.setHgap(10);
    ionPreferences.setVgap(6);

    int rowIndex = 0;
    for (final Map.Entry<String, List<IonizationType>> entry : ionsByClass.entrySet()) {
      final String lipidClass = entry.getKey();
      final List<IonizationType> ions = entry.getValue();
      final Label classLabel = FxLabels.newLabel(lipidClass);
      final ComboBox<IonizationType> ionCombo = FxComboBox.createComboBox(null, ions);
      ionCombo.setMaxWidth(Double.MAX_VALUE);
      final CheckBox keepHighestScoreCheckbox = new CheckBox("Keep highest scoring annotation");
      keepHighestScoreCheckbox.selectedProperty().addListener((_, _, _) -> {
        updateIonPreferenceControlState();
        updateSummaryAndState();
      });

      final IonizationType defaultIon = defaultPreferredByClass.get(lipidClass);
      if (defaultIon != null) {
        ionCombo.getSelectionModel().select(defaultIon);
      } else if (!ions.isEmpty()) {
        ionCombo.getSelectionModel().select(ions.getFirst());
      }
      ionCombo.valueProperty().addListener((_, _, _) -> updateSummaryAndState());
      ionComboByLipidClass.put(lipidClass, ionCombo);
      keepHighestByLipidClass.put(lipidClass, keepHighestScoreCheckbox);

      ionPreferences.add(classLabel, 0, rowIndex);
      ionPreferences.add(ionCombo, 1, rowIndex);
      ionPreferences.add(keepHighestScoreCheckbox, 2, rowIndex);
      rowIndex++;
    }

    ionPreferenceScrollPane = new ScrollPane(ionPreferences);
    ionPreferenceScrollPane.setFitToWidth(true);
    ionPreferenceScrollPane.setPrefHeight(280);
    ionPreferenceScrollPane.setMinHeight(180);

    final HBox rowHandlingRow = new HBox(8, FxLabels.newLabel("Remaining annotations:"),
        rowHandlingModeCombo);
    rowHandlingRow.setAlignment(Pos.CENTER_LEFT);

    summaryLabel.setWrapText(true);
    summaryLabel.setStyle("-fx-font-weight: bold;");

    final VBox content = new VBox(8, alwaysKeepHighestScore,
        ionPreferenceLabel, ionPreferenceScrollPane, rowHandlingRow,
        summaryLabel);
    content.setPrefWidth(640);

    dialog.setTitle("Remove multi-row annotations");
    dialog.setHeaderText("Choose retention options for duplicated annotations");
    dialog.getDialogPane().setContent(content);
    dialog.getDialogPane().getButtonTypes().addAll(applyButtonType, ButtonType.CANCEL);

    updateIonPreferenceControlState();
    updateSummaryAndState();
  }

  @NotNull Optional<MultiRowAnnotationCleanupPlan> showAndWait() {
    final Optional<ButtonType> result = dialog.showAndWait();
    if (result.isEmpty() || result.get().getButtonData() != ButtonData.OK_DONE) {
      return Optional.empty();
    }
    return Optional.ofNullable(lastComputedPlan);
  }

  private void updateSummaryAndState() {
    final MultiRowAnnotationCleanupOptions options = buildCurrentOptions();
    final MultiRowAnnotationCleanupPlan cleanupPlan = MultiRowAnnotationCleanupPlanner.buildCleanupPlan(
        featureList, includeRetentionTimeAnalysis, options);
    lastComputedPlan = cleanupPlan;

    if (cleanupPlan.hasRemovals()) {
      final String rowText = cleanupPlan.affectedRowCount() == 1 ? "1 feature list row"
          : cleanupPlan.affectedRowCount() + " feature list rows";
      summaryLabel.setText(
          "Will remove " + cleanupPlan.removedAnnotationCount() + " lipid annotations across "
              + rowText + ".");
    } else {
      summaryLabel.setText("No multi-row annotations would be removed with current options.");
    }

    final Button applyButton = (Button) dialog.getDialogPane().lookupButton(applyButtonType);
    if (applyButton != null) {
      applyButton.setDisable(!cleanupPlan.hasRemovals());
    }
  }

  private @NotNull MultiRowAnnotationCleanupOptions buildCurrentOptions() {
    final Map<String, IonizationType> preferredIonByClass = new LinkedHashMap<>();
    for (final Map.Entry<String, ComboBox<IonizationType>> entry : ionComboByLipidClass.entrySet()) {
      final IonizationType selectedIon = entry.getValue().getSelectionModel().getSelectedItem();
      if (selectedIon != null) {
        preferredIonByClass.put(entry.getKey(), selectedIon);
      }
    }
    final Set<String> keepHighestScoreByClass = new LinkedHashSet<>();
    for (final Map.Entry<String, CheckBox> entry : keepHighestByLipidClass.entrySet()) {
      if (entry.getValue().isSelected()) {
        keepHighestScoreByClass.add(entry.getKey());
      }
    }
    final MultiRowAnnotationCleanupRowHandlingMode rowHandlingMode =
        rowHandlingModeCombo.getValue() == null
            ? MultiRowAnnotationCleanupRowHandlingMode.DISCARD_LOWER_THAN_REMOVED
            : rowHandlingModeCombo.getValue();
    return new MultiRowAnnotationCleanupOptions(preferredIonByClass, keepHighestScoreByClass,
        alwaysKeepHighestScore.isSelected(), rowHandlingMode);
  }

  private void updateIonPreferenceControlState() {
    final boolean disableAllPerClassControls = alwaysKeepHighestScore.isSelected();
    ionPreferenceLabel.setDisable(disableAllPerClassControls);
    ionPreferenceScrollPane.setDisable(disableAllPerClassControls);
    for (final Map.Entry<String, ComboBox<IonizationType>> entry : ionComboByLipidClass.entrySet()) {
      final String lipidClass = entry.getKey();
      final ComboBox<IonizationType> combo = entry.getValue();
      final CheckBox keepHighest = keepHighestByLipidClass.get(lipidClass);
      final boolean disableCombo = disableAllPerClassControls
          || keepHighest != null && keepHighest.isSelected()
          || combo.getItems().isEmpty();
      combo.setDisable(disableCombo);
      if (combo.getItems().isEmpty()) {
        combo.setPromptText("No ions found");
      }
      if (keepHighest != null) {
        keepHighest.setDisable(disableAllPerClassControls);
      }
    }
  }
}
