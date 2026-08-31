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

import io.github.mzmine.gui.chartbasics.simplechart.SimpleXYChart;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYDataset;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.DatasetAndRenderer;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.RunOption;
import io.github.mzmine.gui.chartbasics.simplechart.providers.PlotXYDataProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.SimpleXYProvider;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYLineRenderer;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYShapeRenderer;
import io.github.mzmine.javafx.components.factories.FxButtons;
import io.github.mzmine.javafx.components.factories.FxSplitPanes;
import io.github.mzmine.javafx.components.factories.TableColumns;
import io.github.mzmine.javafx.components.factories.TableColumns.ColumnAlignment;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.javafx.util.FxIcons;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.OrdinalIntegerVariable;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.SolutionOrigin;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.WizardOptimizationProblem;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.WizardParameterSolutionBuilder;
import io.github.mzmine.util.color.SimpleColorPalette;
import java.awt.BasicStroke;
import java.awt.geom.Ellipse2D;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.axis.NumberAxis;
import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.RealVariable;
import org.moeaframework.core.variable.Variable;

public class OptimizationResultsViewBuilder extends FxViewBuilder<OptimizationResultModel> {

  private static final String SOURCE_ESTIMATE = "Raw data estimate";
  private static final String SOURCE_FRONT = "Front";
  private static final String SOURCE_EVALUATED = "Evaluated";

  private final NumberFormat threeDecimals = new DecimalFormat("0.###");
  private final NumberFormat noDecimals = new DecimalFormat("0");

  private final Runnable onAcceptPressed;
  private final Runnable openInBatch;
  private final Runnable onExportPressed;
  private final Runnable quickRun;
  @Nullable
  private final Runnable stopSearch;
  @Nullable
  private final Stage stage;

  protected OptimizationResultsViewBuilder(@NotNull OptimizationResultModel model,
      @NotNull Runnable onAcceptPressed, @NotNull Runnable openInBatch,
      @NotNull Runnable onExportPressed, @NotNull Runnable quickRun, @Nullable Runnable stopSearch,
      @Nullable final Stage stage) {
    super(model);
    this.onAcceptPressed = onAcceptPressed;
    this.openInBatch = openInBatch;
    this.onExportPressed = onExportPressed;
    this.quickRun = quickRun;
    this.stopSearch = stopSearch;
    this.stage = stage;
  }

  @Override
  public @NotNull Region build() {
    final TableView<Solution> solutionTable = new TableView<>();
    // decision: bind the model's single observable list instead of replacing the items list, so
    // listeners and bindings on it are not discarded
    solutionTable.setItems(model.getDisplayedSolutions());
    createColumns(solutionTable);

    solutionTable.getSelectionModel().selectedItemProperty()
        .subscribe(s -> model.selectedSolutionProperty().set(s));

    final SimpleXYChart<PlotXYDataProvider> progressChart = createProgressChart();
    model.getDisplayedSolutions()
        .addListener((ListChangeListener<Solution>) _ -> updateProgressChart(progressChart));
    updateProgressChart(progressChart);

    final SplitPane content = FxSplitPanes.newSplitPane(0.45, Orientation.VERTICAL, progressChart,
        solutionTable);
    final BorderPane borderPane = new BorderPane();
    borderPane.setCenter(content);

    final Button acceptButton = FxButtons.createButton("Apply to wizard", FxIcons.CHECK_CIRCLE,
        null, onAcceptPressed);
    final Button batchButton = FxButtons.createButton("Open in batch", FxIcons.BATCH, null,
        openInBatch);
    final Button exportButton = FxButtons.createButton("Export to .csv", FxIcons.FILE, null,
        onExportPressed);
    final Button quickRunButton = FxButtons.createButton("Quick run & annotate", FxIcons.BATCH,
        null, quickRun);

    final BooleanBinding noUsableSelection = model.optimizationRunningProperty()
        .or(model.selectedSolutionProperty().isNull());
    acceptButton.disableProperty().bind(noUsableSelection);
    batchButton.disableProperty().bind(noUsableSelection);
    quickRunButton.disableProperty().bind(noUsableSelection);
    exportButton.disableProperty().bind(Bindings.isEmpty(model.getDisplayedSolutions()));

    final ButtonBar buttonBar = new ButtonBar();
    buttonBar.getButtons().addAll(exportButton, batchButton, quickRunButton, acceptButton);
    borderPane.setBottom(buttonBar);

    if (stopSearch != null) {
      final Button stopButton = FxButtons.createButton("Stop search", FxIcons.STOP,
          "Finish after the current batch and keep all completed results", stopSearch);
      stopButton.disableProperty()
          .bind(model.optimizationRunningProperty().not().or(model.stopSearchRequestedProperty()));
      stopButton.textProperty().bind(
          Bindings.when(model.stopSearchRequestedProperty()).then("Stopping...")
              .otherwise("Stop search"));
      ButtonBar.setButtonData(stopButton, ButtonBar.ButtonData.LEFT);
      buttonBar.getButtons().add(0, stopButton);
    }

    if (stage != null) {
      final Button closeButton = FxButtons.createButton("Close", FxIcons.CANCEL, null, stage::hide);
      buttonBar.getButtons().add(closeButton);
    }

    return borderPane;
  }

  private @NotNull SimpleXYChart<PlotXYDataProvider> createProgressChart() {
    final Solution template = model.getDisplayedSolutions().getFirst();
    final String objectiveName = template.getObjective(0).getName();
    final SimpleXYChart<PlotXYDataProvider> chart = new SimpleXYChart<>(
        "Candidate evaluations and best-so-far", "Evaluation number", objectiveName);
    chart.setItemLabelsVisible(false);
    chart.setShowCrosshair(true);
    chart.setMinHeight(260d);
    final NumberAxis evaluationAxis = (NumberAxis) chart.getXYPlot().getDomainAxis();
    evaluationAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
    return chart;
  }

  private void updateProgressChart(@NotNull SimpleXYChart<PlotXYDataProvider> chart) {
    final List<Solution> solutions = List.copyOf(model.getDisplayedSolutions());
    if (solutions.isEmpty()) {
      chart.removeAllDatasets();
      return;
    }

    // assumption: the first objective is the score shown in the live chart. Multi-objective runs
    // retain all objective columns in the table; the chart label states which one is plotted.
    final OptimizationProgressData progress = OptimizationProgressData.create(solutions,
        model.getSinglePassSolution(), 0);
    chart.setRangeAxisLabel(progress.objectiveName());

    final SimpleColorPalette palette = ConfigService.getDefaultColorPalette();
    final List<DatasetAndRenderer> datasets = new ArrayList<>(3);

    final SimpleXYProvider candidates = new SimpleXYProvider("Candidate score",
        palette.getPositiveColorAWT(), progress.evaluations(), progress.scores(), noDecimals,
        threeDecimals);
    final ColoredXYShapeRenderer candidateRenderer = new ColoredXYShapeRenderer(false,
        new Ellipse2D.Double(-3.5d, -3.5d, 7d, 7d), true);
    datasets.add(new DatasetAndRenderer(new ColoredXYDataset(candidates, RunOption.THIS_THREAD),
        candidateRenderer));

    final SimpleXYProvider best = new SimpleXYProvider("Best so far", palette.getAWT(1),
        progress.bestEvaluations(), progress.bestScores(), noDecimals, threeDecimals);
    final ColoredXYLineRenderer bestRenderer = new ColoredXYLineRenderer();
    bestRenderer.setDefaultStroke(new BasicStroke(2.2f));
    datasets.add(
        new DatasetAndRenderer(new ColoredXYDataset(best, RunOption.THIS_THREAD), bestRenderer));

    if (Double.isFinite(progress.estimateScore()) && progress.evaluations().length > 0) {
      final double firstEvaluation = progress.evaluations()[0];
      final double lastEvaluation = progress.evaluations()[progress.evaluations().length - 1];
      final double start =
          firstEvaluation == lastEvaluation ? firstEvaluation - 0.5d : firstEvaluation;
      final double end = firstEvaluation == lastEvaluation ? lastEvaluation + 0.5d : lastEvaluation;
      final SimpleXYProvider estimate = new SimpleXYProvider("Raw data estimate",
          palette.getNeutralColorAWT(), new double[]{start, end},
          new double[]{progress.estimateScore(), progress.estimateScore()}, noDecimals,
          threeDecimals);
      final ColoredXYLineRenderer estimateRenderer = new ColoredXYLineRenderer();
      estimateRenderer.setDefaultStroke(
          new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
              new float[]{6f, 4f}, 0f));
      datasets.add(new DatasetAndRenderer(new ColoredXYDataset(estimate, RunOption.THIS_THREAD),
          estimateRenderer));
    }

    chart.setDatasetsAndRenderers(datasets);
  }

  /**
   * Derives the table columns from the first displayed solution. The raw data estimate is always
   * the first row, so it remains a valid template even when the optimizer returned no solutions,
   * for example after an early cancel.
   */
  private void createColumns(@NotNull TableView<Solution> solutionTable) {
    final ObservableList<Solution> solutions = model.getDisplayedSolutions();
    if (solutions.isEmpty()) {
      return;
    }
    final Solution template = solutions.getFirst();

    final TableColumn<Solution, String> sourceCol = TableColumns.createColumn("Source", 130,
        s -> new ReadOnlyStringWrapper(s == model.getSinglePassSolution() ? SOURCE_ESTIMATE
            : model.isOnFront(s) ? SOURCE_FRONT : SOURCE_EVALUATED));
    solutionTable.getColumns().add(sourceCol);

    // decision: pinned next to Source instead of left to the generic attribute loop below, which
    // would scatter it among the diagnostics. Source says how a row got into the table, Origin says
    // which phase of the run produced its parameters.
    final TableColumn<Solution, String> originCol = TableColumns.createColumn(
        SolutionOrigin.ATTRIBUTE, 100, s -> new ReadOnlyStringWrapper(
            Objects.requireNonNullElse(s.getAttribute(SolutionOrigin.ATTRIBUTE), "").toString()));
    solutionTable.getColumns().add(originCol);

    final TableColumn<Solution, Number> indexCol = TableColumns.createColumn("Evaluation", 80,
        s -> new ReadOnlyIntegerWrapper(evaluationIndex(s)));
    solutionTable.getColumns().add(indexCol);

    for (int i = 0; i < template.getNumberOfVariables(); i++) {
      final Variable variable = template.getVariable(i);
      final int finalI = i;
      switch (variable) {
        // decision: the ordinal case must precede RealVariable, which it extends
        case OrdinalIntegerVariable v -> {
          if (v.getName().equals("MZ tolerance option")) {
            final TableColumn<Solution, String> col = TableColumns.createColumn(v.getName(), 120,
                200, ColumnAlignment.RIGHT, String::compareTo, s -> new ReadOnlyStringWrapper(
                    WizardParameterSolutionBuilder.ALL_TOLERANCE_OPTIONS[OrdinalIntegerVariable.getInt(
                        s, finalI)].toString()));
            solutionTable.getColumns().add(col);
          } else {
            final TableColumn<Solution, Number> col = TableColumns.createColumn(v.getName(), 120,
                noDecimals, ColumnAlignment.RIGHT,
                s -> new ReadOnlyIntegerWrapper(OrdinalIntegerVariable.getInt(s, finalI)));
            solutionTable.getColumns().add(col);
          }
        }
        case RealVariable v -> {
          final TableColumn<Solution, Number> col = TableColumns.createColumn(v.getName(), 120,
              threeDecimals, ColumnAlignment.RIGHT,
              s -> new ReadOnlyDoubleWrapper(((RealVariable) s.getVariable(finalI)).getValue()));
          solutionTable.getColumns().add(col);
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
      // Skip internal attributes (prefixed with '_'), the MOEA penalty attribute and the origin,
      // which already has its own column next to Source
      if (attribute.startsWith("_") || attribute.equalsIgnoreCase("penalty") || attribute.equals(
          SolutionOrigin.ATTRIBUTE) || attribute.equals(
          WizardOptimizationProblem.ATTR_PROPOSAL_INDEX)) {
        continue;
      }
      final TableColumn<Solution, String> col = TableColumns.createColumn(attribute, 120,
          s -> new ReadOnlyStringWrapper(
              Objects.requireNonNullElse(s.getAttribute(attribute), "").toString()));
      solutionTable.getColumns().add(col);
    }
  }

  private int evaluationIndex(@NotNull Solution solution) {
    final Object index = solution.getAttribute(WizardOptimizationProblem.ATTR_PROPOSAL_INDEX);
    return index instanceof Number number ? number.intValue()
        : model.getDisplayedSolutions().indexOf(solution) + 1;
  }
}
