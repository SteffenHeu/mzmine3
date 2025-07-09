/*
 * Copyright (c) 2004-2025 The mzmine Development Team
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
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.ParameterSolutionBuilder;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
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
import org.jetbrains.annotations.Nullable;
import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.BinaryIntegerVariable;
import org.moeaframework.core.variable.RealVariable;
import org.moeaframework.core.variable.Variable;

public class OptimizationResultsViewBuilder extends FxViewBuilder<OptimizationResultModel> {

  private final NumberFormat threeDecimals = new DecimalFormat("0.###");
  private final NumberFormat noDecimals = new DecimalFormat("0");

  private final Runnable onAcceptPressed;
  @Nullable
  private final Stage stage;

  protected OptimizationResultsViewBuilder(OptimizationResultModel model, Runnable onAcceptPressed,
      @Nullable final Stage stage) {
    super(model);
    this.onAcceptPressed = onAcceptPressed;
    this.stage = stage;
  }

  @Override
  public Region build() {
    final TableView<Solution> solutionTable = new TableView<>();
    model.resultProperty().subscribe(result -> {
      if (solutionTable.getColumns().isEmpty()) {
        final Solution solution = result.get(0);
        for (int i = 0; i < solution.getNumberOfVariables(); i++) {
          final Variable variable = solution.getVariable(i);
          final int finalI = i;
          switch (variable) {
            case RealVariable v -> {
              final TableColumn<Solution, Number> col = TableColumns.createColumn(v.getName(), 120,
                  threeDecimals, ColumnAlignment.RIGHT, s -> new ReadOnlyDoubleWrapper(
                      ((RealVariable) s.getVariable(finalI)).getValue()));
              solutionTable.getColumns().add(col);
            }
            case BinaryIntegerVariable v -> {
              if (v.getName().equals("MZ tolerance option")) {
                final TableColumn<Solution, String> col = TableColumns.createColumn(v.getName(),
                    120, 200, ColumnAlignment.RIGHT, String::compareTo,
                    s -> new ReadOnlyStringWrapper(
                        ParameterSolutionBuilder.ALL_TOLERANCE_OPTIONS[((BinaryIntegerVariable) s.getVariable(
                            finalI)).getValue()].toString()));
                solutionTable.getColumns().add(col);
              } else {
                final TableColumn<Solution, Number> col = TableColumns.createColumn(v.getName(),
                    120, noDecimals, ColumnAlignment.RIGHT, s -> new ReadOnlyIntegerWrapper(
                        ((BinaryIntegerVariable) s.getVariable(finalI)).getValue()));
                solutionTable.getColumns().add(col);
              }
            }
            default -> {

            }
          }
        }

        final List<ObjectiveWrapper> wrappers = ObjectiveWrapper.extract(result);
        for (ObjectiveWrapper wrapper : wrappers) {
          final TableColumn<Solution, Number> col = wrapper.createColumn();
          solutionTable.getColumns().add(col);
        }
      }

      solutionTable.getItems().setAll(result.asList());
    });

    solutionTable.getSelectionModel().selectedItemProperty()
        .subscribe(s -> model.selectedSolutionProperty().set(s));

    BorderPane borderPane = new BorderPane();
    borderPane.setCenter(solutionTable);

    final Button acceptButton = FxButtons.createButton("Accept", FxIcons.CHECK_CIRCLE, null,
        onAcceptPressed);
    ButtonBar buttonBar = new ButtonBar();
    buttonBar.getButtons().add(acceptButton);
    borderPane.setBottom(buttonBar);

    if (stage != null) {
      final Button closeButton = FxButtons.createButton("Close", FxIcons.CANCEL, null, stage::hide);
      buttonBar.getButtons().add(closeButton);
    }

    TableColumns.autoFitLastColumn(solutionTable);
    return borderPane;
  }
}
