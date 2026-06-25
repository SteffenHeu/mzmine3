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

package io.github.mzmine.modules.dataanalysis.qcdashboard;

import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.datamodel.features.types.fx.ColumnID;
import io.github.mzmine.datamodel.features.types.fx.ColumnType;
import io.github.mzmine.datamodel.features.types.modifiers.GraphicalColumType;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.modules.dataanalysis.qcdashboard.controls.QcDashboardControlsBuilder;
import io.github.mzmine.modules.dataanalysis.qcdashboard.plots.detectioncount.DetectionCountController;
import io.github.mzmine.modules.dataanalysis.qcdashboard.plots.deviation.DeviationPlotController;
import io.github.mzmine.modules.dataanalysis.qcdashboard.plots.intensity.IntensityPlotController;
import io.github.mzmine.modules.dataanalysis.qcdashboard.plots.perfile.PerFileAggregateController;
import io.github.mzmine.modules.visualization.featurelisttable_modular.FeatureTableFX;
import io.github.mzmine.modules.visualization.featurelisttable_modular.FeatureTableFXParameters;
import io.github.mzmine.modules.visualization.featurelisttable_modular.FxFeatureTableController;
import io.github.mzmine.parameters.parametertypes.datatype.DataTypeCheckListParameter;
import io.github.mzmine.util.FeatureTableFXUtil;
import java.util.List;
import javafx.geometry.Orientation;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import org.jetbrains.annotations.NotNull;

/**
 * Builds the QC dashboard layout: a 2x3 grid of plots on the left, a controls column and the
 * (reduced) feature table on the right. Subplot panes are placeholders until the subplot
 * controllers are wired (milestones 3/4).
 */
public class QcDashboardViewBuilder extends FxViewBuilder<QcDashboardModel> {

  private final FxFeatureTableController tableController;
  private final FeatureTableFX table;
  private final IntensityPlotController intensityController;
  private final DeviationPlotController mzDeviationController;
  private final DeviationPlotController rtDeviationController;
  private final PerFileAggregateController featureCountController;
  private final PerFileAggregateController sumIntensityController;
  private final DetectionCountController detectionCountController;

  public QcDashboardViewBuilder(QcDashboardModel model, FxFeatureTableController tableController,
      IntensityPlotController intensityController, DeviationPlotController mzDeviationController,
      DeviationPlotController rtDeviationController,
      PerFileAggregateController featureCountController,
      PerFileAggregateController sumIntensityController,
      DetectionCountController detectionCountController) {
    super(model);
    this.tableController = tableController;
    this.table = tableController.getFeatureTable();
    this.intensityController = intensityController;
    this.mzDeviationController = mzDeviationController;
    this.rtDeviationController = rtDeviationController;
    this.featureCountController = featureCountController;
    this.sumIntensityController = sumIntensityController;
    this.detectionCountController = detectionCountController;
  }

  @Override
  public Region build() {
    final SplitPane main = new SplitPane();
    main.setOrientation(Orientation.HORIZONTAL);
    main.getItems().addAll(buildPlotGrid(), buildRightColumn());
    main.setDividerPositions(0.75);

    initFeatureListListeners();
    return main;
  }

  private @NotNull GridPane buildPlotGrid() {
    final GridPane grid = new GridPane();
    grid.setHgap(6);
    grid.setVgap(6);
    for (int c = 0; c < 3; c++) {
      final ColumnConstraints cc = new ColumnConstraints();
      cc.setPercentWidth(100.0 / 3);
      cc.setHgrow(Priority.ALWAYS);
      grid.getColumnConstraints().add(cc);
    }
    for (int r = 0; r < 2; r++) {
      final RowConstraints rc = new RowConstraints();
      rc.setPercentHeight(50);
      rc.setVgrow(Priority.ALWAYS);
      grid.getRowConstraints().add(rc);
    }

    // top row: detection count | feature count | summed intensity
    grid.add(detectionCountController.buildView(), 0, 0);
    grid.add(featureCountController.buildView(), 1, 0);
    grid.add(sumIntensityController.buildView(), 2, 0);
    // bottom row: m/z deviation | intensity | RT deviation
    grid.add(mzDeviationController.buildView(), 0, 1);
    grid.add(intensityController.buildView(), 1, 1);
    grid.add(rtDeviationController.buildView(), 2, 1);
    return grid;
  }

  /**
   * Hides only the graphical column types ({@link GraphicalColumType}) of the feature list, keeping
   * all other columns. Run after the table built its columns. Removes the expensive graphical cells
   * (shapes, area boxplots, structures) that otherwise slow the table down.
   */
  private void hideGraphicalColumns(ModularFeatureList flist) {
    final DataTypeCheckListParameter rowTypes = tableController.getModel().getParameters()
        .getParameter(FeatureTableFXParameters.showRowTypeColumns);
    final DataTypeCheckListParameter featureTypes = tableController.getModel().getParameters()
        .getParameter(FeatureTableFXParameters.showFeatureTypeColumns);

    for (DataType<?> type : flist.getRowTypes()) {
      if (type instanceof GraphicalColumType) {
        rowTypes.setDataTypeVisible(
            ColumnID.buildUniqueIdString(ColumnType.ROW_TYPE, type, -1), false);
      }
    }
    for (DataType<?> type : flist.getFeatureTypes()) {
      if (type instanceof GraphicalColumType) {
        featureTypes.setDataTypeVisible(
            ColumnID.buildUniqueIdString(ColumnType.FEATURE_TYPE, type, -1), false);
      }
    }
    table.applyVisibilityParametersToAllColumns();
  }

  private @NotNull Region buildRightColumn() {
    final SplitPane right = new SplitPane();
    right.setOrientation(Orientation.VERTICAL);
    right.getItems()
        .addAll(new QcDashboardControlsBuilder(model).build(), tableController.buildView());
    right.setDividerPositions(0.35);
    return right;
  }

  private void initFeatureListListeners() {
    // feed the table from the first aligned feature list, then hide graphical columns
    model.flistsProperty().addListener((_, _, flists) -> {
      final ModularFeatureList aligned = (ModularFeatureList) flists.stream()
          .filter(FeatureList::isAligned).findFirst().orElse(null);
      table.setFeatureList(aligned);
      if (aligned != null) {
        hideGraphicalColumns(aligned);
      }
    });

    // model selection -> select + scroll in the table
    model.selectedRowsProperty().addListener((_, _, rows) -> {
      if (rows == null || rows.isEmpty()) {
        return;
      }
      FeatureTableFXUtil.selectAndScrollTo(rows.getFirst(), table);
    });

    // table selection -> model selection
    table.getSelectionModel().selectedItemProperty().addListener((_, old, row) -> {
      if (row == null) {
        model.setSelectedRows(List.of());
      } else if (old == null || (row.getValue() != null && !old.equals(row))) {
        model.setSelectedRows(List.<FeatureListRow>of(row.getValue()));
      }
    });
  }
}
