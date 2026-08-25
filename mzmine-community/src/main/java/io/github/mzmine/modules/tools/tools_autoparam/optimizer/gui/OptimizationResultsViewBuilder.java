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
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.WizardParameterSolutionBuilder;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.BinaryIntegerVariable;
import org.moeaframework.core.variable.RealVariable;
import org.moeaframework.core.variable.Variable;

public class OptimizationResultsViewBuilder extends FxViewBuilder<OptimizationResultModel> {

  private static final String SOURCE_ESTIMATE = "Raw data estimate";
  private static final String SOURCE_OPTIMIZER = "Optimizer";

  private final NumberFormat threeDecimals = new DecimalFormat("0.###");
  private final NumberFormat noDecimals = new DecimalFormat("0");

  private final Runnable onAcceptPressed;
  private final Runnable openInBatch;
  private final Runnable onExportPressed;
  private final Runnable quickRun;
  @Nullable
  private final Stage stage;

  protected OptimizationResultsViewBuilder(OptimizationResultModel model, Runnable onAcceptPressed,
      Runnable openInBatch, Runnable onExportPressed, Runnable quickRun, @Nullable final Stage stage) {
    super(model);
    this.onAcceptPressed = onAcceptPressed;
    this.openInBatch = openInBatch;
    this.onExportPressed = onExportPressed;
    this.quickRun = quickRun;
    this.stage = stage;
  }

  @Override
  public Region build() {
    final TableView<Solution> solutionTable = new TableView<>();
    // decision: bind the model's single observable list instead of replacing the items list, so
    // listeners and bindings on it are not discarded
    solutionTable.setItems(model.getDisplayedSolutions());
    createColumns(solutionTable);

    solutionTable.getSelectionModel().selectedItemProperty()
        .subscribe(s -> model.selectedSolutionProperty().set(s));

    BorderPane borderPane = new BorderPane();
    borderPane.setCenter(solutionTable);

    final Button acceptButton = FxButtons.createButton("Apply to wizard", FxIcons.CHECK_CIRCLE,
        null, onAcceptPressed);
    final Button batchButton = FxButtons.createButton("Open in batch", FxIcons.BATCH, null,
        openInBatch);
    final Button exportButton = FxButtons.createButton("Export to .csv", FxIcons.FILE, null,
        onExportPressed);
    final Button quickRunButton = FxButtons.createButton("Quick run & annotate", FxIcons.BATCH,
        null, quickRun);

    ButtonBar buttonBar = new ButtonBar();
    buttonBar.getButtons().addAll(exportButton, batchButton, quickRunButton, acceptButton);
    borderPane.setBottom(buttonBar);

    if (stage != null) {
      final Button closeButton = FxButtons.createButton("Close", FxIcons.CANCEL, null, stage::hide);
      buttonBar.getButtons().add(closeButton);
    }

    return borderPane;
  }

  /**
   * Derives the table columns from the first displayed solution. The raw data estimate is always
   * the first row, so it remains a valid template even when the optimizer returned no solutions,
   * for example after an early cancel.
   */
  private void createColumns(@NotNull TableView<Solution> solutionTable) {
    final List<Solution> solutions = model.getDisplayedSolutions();
    if (solutions.isEmpty()) {
      return;
    }
    final Solution template = solutions.getFirst();

    final TableColumn<Solution, String> sourceCol = TableColumns.createColumn("Source", 130,
        s -> new ReadOnlyStringWrapper(
            s == model.getSinglePassSolution() ? SOURCE_ESTIMATE : SOURCE_OPTIMIZER));
    solutionTable.getColumns().add(sourceCol);

    // assumption: the table sorts its items list in place, so the original row order is captured
    // here instead of being looked up in the live list
    final Map<Solution, Integer> originalOrder = new IdentityHashMap<>(solutions.size());
    for (int i = 0; i < solutions.size(); i++) {
      originalOrder.put(solutions.get(i), i);
    }
    final TableColumn<Solution, Number> indexCol = TableColumns.createColumn("Index", 50,
        s -> new ReadOnlyIntegerWrapper(originalOrder.getOrDefault(s, -1)));
    solutionTable.getColumns().add(indexCol);

    for (int i = 0; i < template.getNumberOfVariables(); i++) {
      final Variable variable = template.getVariable(i);
      final int finalI = i;
      switch (variable) {
        case RealVariable v -> {
          final TableColumn<Solution, Number> col = TableColumns.createColumn(v.getName(), 120,
              threeDecimals, ColumnAlignment.RIGHT,
              s -> new ReadOnlyDoubleWrapper(((RealVariable) s.getVariable(finalI)).getValue()));
          solutionTable.getColumns().add(col);
        }
        case BinaryIntegerVariable v -> {
          if (v.getName().equals("MZ tolerance option")) {
            final TableColumn<Solution, String> col = TableColumns.createColumn(v.getName(), 120,
                200, ColumnAlignment.RIGHT, String::compareTo, s -> new ReadOnlyStringWrapper(
                    WizardParameterSolutionBuilder.ALL_TOLERANCE_OPTIONS[((BinaryIntegerVariable) s.getVariable(
                        finalI)).getValue()].toString()));
            solutionTable.getColumns().add(col);
          } else {
            final TableColumn<Solution, Number> col = TableColumns.createColumn(v.getName(), 120,
                noDecimals, ColumnAlignment.RIGHT, s -> new ReadOnlyIntegerWrapper(
                    ((BinaryIntegerVariable) s.getVariable(finalI)).getValue()));
            solutionTable.getColumns().add(col);
          }
        }
        default -> {

        }
      }
    }

    final List<ObjectiveWrapper> wrappers = ObjectiveWrapper.extract(solutions);
    for (ObjectiveWrapper wrapper : wrappers) {
      final TableColumn<Solution, Number> col =
          wrapper.isHarmonic() ? wrapper.createNormalizedHarmonicColumn(solutions)
              : wrapper.createColumn();
      solutionTable.getColumns().add(col);
    }

    for (Entry<String, Serializable> attributeEntry : template.getAttributes().entrySet()) {
      final String attribute = attributeEntry.getKey();
      // Skip internal attributes (prefixed with '_') and the MOEA penalty attribute
      if (attribute.startsWith("_") || attribute.equalsIgnoreCase("penalty")) {
        continue;
      }
      final TableColumn<Solution, String> col = TableColumns.createColumn(attribute, 120,
          s -> new ReadOnlyStringWrapper(
              Objects.requireNonNullElse(s.getAttribute(attribute), "").toString()));
      solutionTable.getColumns().add(col);
    }
  }
}
