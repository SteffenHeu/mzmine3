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

package io.github.mzmine.modules.tools.tools_autoparam.optimizer.gui;

import io.github.mzmine.javafx.components.factories.FxButtons;
import io.github.mzmine.javafx.components.factories.TableColumns;
import io.github.mzmine.javafx.components.factories.TableColumns.ColumnAlignment;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.javafx.util.FxIcons;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.ParameterSweepRunner;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.SweepMetric;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.SweepMetricResult;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.moeaframework.core.variable.BinaryIntegerVariable;
import org.moeaframework.core.variable.RealVariable;
import org.moeaframework.core.variable.Variable;

public class SweepResultsViewBuilder extends FxViewBuilder<SweepResultsModel> {

  private final NumberFormat threeDecimals = new DecimalFormat("0.###");

  private final Runnable onApplyPressed;
  private final Runnable onSynthesizePressed;
  @Nullable
  private final Stage stage;

  protected SweepResultsViewBuilder(SweepResultsModel model, Runnable onApplyPressed,
      Runnable onSynthesizePressed, @Nullable Stage stage) {
    super(model);
    this.onApplyPressed = onApplyPressed;
    this.onSynthesizePressed = onSynthesizePressed;
    this.stage = stage;
  }

  @Override
  public Region build() {
    final TableView<SweepMetricResult> table = new TableView<>();

    // Fixed: parameter name column
    final TableColumn<SweepMetricResult, String> paramCol = TableColumns.createColumn("Parameter",
        160, 240, ColumnAlignment.LEFT, String::compareTo,
        r -> new ReadOnlyStringWrapper(r.parameterName()));

    // Value column — cell factory is replaced when results arrive to bold optimal rows
    final TableColumn<SweepMetricResult, String> valueCol = new TableColumn<>("Value");
    valueCol.setMinWidth(120);
    valueCol.setMaxWidth(160);
    valueCol.setCellValueFactory(
        row -> new ReadOnlyStringWrapper(formatVariable(row.getValue().variable())));

    table.getColumns().addAll(paramCol, valueCol);

    model.resultsProperty().subscribe(results -> {
      if (!results.isEmpty() && table.getColumns().size() == 2) {
        // Compute which rows are optimal for at least one metric in their parameter group
        final Set<SweepMetricResult> optimal = computeAllOptimal(results);

        // Bold the value cell for optimal rows
        valueCol.setCellFactory(column -> new TableCell<>() {
          @Override
          protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
              setText(null);
              setStyle("");
            } else {
              setText(item);
              setAlignment(Pos.CENTER_RIGHT);
              final SweepMetricResult row = getTableView().getItems().get(getIndex());
              setStyle(optimal.contains(row) ? "-fx-font-weight: bold;" : "");
            }
          }
        });

        addMetricColumns(table, results);
      }
      table.getItems().setAll(results);
    });

    table.getSelectionModel().selectedItemProperty().subscribe(r -> model.setSelectedResult(r));

    // Buttons
    final Button synthesizeButton = FxButtons.createButton("Apply synthesized to wizard",
        FxIcons.GRAPH_UP, null, onSynthesizePressed);
    final Button applyButton = FxButtons.createButton("Apply selected to wizard",
        FxIcons.CHECK_CIRCLE, null, onApplyPressed);
    applyButton.disableProperty().bind(model.selectedResultProperty().isNull());

    final ButtonBar buttonBar = new ButtonBar();
    buttonBar.getButtons().addAll(synthesizeButton, applyButton);
    if (stage != null) {
      buttonBar.getButtons()
          .add(FxButtons.createButton("Close", FxIcons.CANCEL, null, stage::hide));
    }

    final BorderPane root = new BorderPane();
    root.setCenter(table);
    root.setBottom(buttonBar);

    TableColumns.autoFitLastColumn(table);
    return root;
  }

  /**
   * Adds one plain formatted column per metric; no bold highlighting in these columns.
   */
  private void addMetricColumns(TableView<SweepMetricResult> table,
      @NotNull List<SweepMetricResult> results) {
    final List<SweepMetric> metrics = results.getFirst().metrics();
    for (int i = 0; i < metrics.size(); i++) {
      final SweepMetric metric = metrics.get(i);
      final int index = i;
      final String header = metric.name() + (metric.higherIsBetter() ? " ↑" : " ↓");
      final TableColumn<SweepMetricResult, Number> col = TableColumns.createColumn(header, 130,
          threeDecimals, ColumnAlignment.RIGHT, r -> new ReadOnlyDoubleWrapper(r.getScore(index)));
      table.getColumns().add(col);
    }
  }

  /**
   * Returns the set of rows that will contribute to the synthesized best sequence, using the same
   * logic as {@link ParameterSweepRunner#synthesizeBestSequence}:
   * <ul>
   *   <li>The primary metric is {@link SweepMetric.HarmonicSlawIsotopes} when present, falling
   *       back to {@link SweepMetric.SlawIntegrationScore}.</li>
   *   <li>Parameters subject to the <em>average rule</em> (see
   *       {@link ParameterSweepRunner#useAverageRule}): both the row with the highest primary
   *       score <em>and</em> the row with the lowest {@link SweepMetric.DoublePeakRatio} score are
   *       highlighted (these two are averaged to produce the final value).</li>
   *   <li>All other parameters: only the row with the highest primary score is highlighted.</li>
   * </ul>
   * Falls back to the middle row when the required metrics are absent.
   */
  private static Set<SweepMetricResult> computeAllOptimal(
      @NotNull List<SweepMetricResult> results) {
    final List<SweepMetric> metrics = results.getFirst().metrics();
    final int doublePeakIdx = ParameterSweepRunner.findMetricIndex(metrics,
        SweepMetric.DoublePeakRatio.class);
    // Mirror ParameterSweepRunner: prefer harmonic metric, fall back to slaw score
    final int harmonicIdx = ParameterSweepRunner.findMetricIndex(metrics,
        SweepMetric.HarmonicSlawIsotopes.class);
    final int primaryIdx = harmonicIdx >= 0 ? harmonicIdx
        : ParameterSweepRunner.findMetricIndex(metrics, SweepMetric.SlawIntegrationScore.class);

    final Map<String, List<SweepMetricResult>> byParam = results.stream().collect(
        Collectors.groupingBy(SweepMetricResult::parameterName, LinkedHashMap::new,
            Collectors.toList()));
    final Set<SweepMetricResult> optimal = new HashSet<>();

    for (List<SweepMetricResult> group : byParam.values()) {
      final SweepMetricResult bestByPrimary =
          primaryIdx >= 0 ? group.stream().filter(r -> !Double.isNaN(r.getScore(primaryIdx)))
              .max(Comparator.comparingDouble(r -> r.getScore(primaryIdx)))
              .orElse(group.get(group.size() / 2)) : group.get(group.size() / 2);
      optimal.add(bestByPrimary);

      final var prototype = group.getFirst().prototype();
      if (ParameterSweepRunner.useAverageRule(prototype) && doublePeakIdx >= 0) {
        group.stream().filter(r -> !Double.isNaN(r.getScore(doublePeakIdx)))
            .min(Comparator.comparingDouble(r -> r.getScore(doublePeakIdx)))
            .ifPresent(optimal::add);
      }
    }

    return optimal;
  }

  private String formatVariable(Variable variable) {
    return switch (variable) {
      case RealVariable v -> threeDecimals.format(v.getValue());
      case BinaryIntegerVariable v -> String.valueOf(v.getValue());
      default -> variable.toString();
    };
  }
}
