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

package io.github.mzmine.modules.dataanalysis.qcdashboard.controls;

import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.javafx.components.factories.FxComboBox;
import io.github.mzmine.javafx.components.factories.FxLabels;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataanalysis.qcdashboard.QcDashboardModel;
import io.github.mzmine.modules.dataprocessing.filter_blanksubtraction.FeatureListBlankSubtractionModule;
import io.github.mzmine.modules.dataprocessing.filter_blanksubtraction.FeatureListBlankSubtractionParameters;
import io.github.mzmine.modules.dataprocessing.filter_rowsfilter.RowsFilterModule;
import io.github.mzmine.modules.dataprocessing.filter_rowsfilter.RowsFilterParameters;
import io.github.mzmine.modules.visualization.projectmetadata.SampleType;
import io.github.mzmine.parameters.parametertypes.metadata.MetadataGroupingComponent;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsParameter;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsSelectionType;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory.DoubleSpinnerValueFactory;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.controlsfx.control.CheckComboBox;
import org.jetbrains.annotations.NotNull;

/**
 * The QC dashboard controls column. Plain (non-MVCI) builder that binds standard controls to the
 * shared {@link QcDashboardModel}: abundance measure, sample types to plot, batch grouping column
 * and the detection-count quality thresholds. These are visual-only inputs (no feature-list
 * mutation).
 */
public class QcDashboardControlsBuilder {

  private final QcDashboardModel model;

  public QcDashboardControlsBuilder(QcDashboardModel model) {
    this.model = model;
  }

  public @NotNull Region build() {
    final VBox box = new VBox(8, new Label("Controls"), buildAbundanceBox(), buildSampleTypeBox(),
        buildBatchBox(), buildThresholdSpinner("Good quality (fraction of features)",
            model.getGoodQualityFraction(), model.goodQualityFractionProperty()::set),
        buildThresholdSpinner("Warwick (fraction of QCs)", model.getWarwickFraction(),
            model.warwickFractionProperty()::set), buildFilterBox());
    box.setPadding(new Insets(6));
    final ScrollPane scroll = new ScrollPane(box);
    scroll.setFitToWidth(true);
    return scroll;
  }

  private Region buildAbundanceBox() {
    return FxComboBox.createLabeledComboBox("Abundance",
        FXCollections.observableArrayList(AbundanceMeasure.values()), model.abundanceProperty());
  }

  private Region buildSampleTypeBox() {
    final CheckComboBox<SampleType> box = new CheckComboBox<>(
        FXCollections.observableArrayList(SampleType.values()));
    // initial checks from the model (default: QC)
    final List<String> selected = model.getSampleTypesToShow();
    for (SampleType type : SampleType.values()) {
      if (selected.contains(type.toString())) {
        box.getCheckModel().check(type);
      }
    }
    box.getCheckModel().getCheckedItems()
        .addListener((ListChangeListener<SampleType>) c -> model.setSampleTypesToShow(
            c.getList().stream().map(SampleType::toString).toList()));
    return new VBox(2, FxLabels.newLabel("Sample types to plot"), box);
  }

  private Region buildBatchBox() {
    final MetadataGroupingComponent batchColumn = new MetadataGroupingComponent();
    batchColumn.valueProperty().bindBidirectional(model.batchColumnProperty());
    return new VBox(2, FxLabels.newLabel("Batch grouping column"), batchColumn);
  }

  private Region buildThresholdSpinner(String label, double initial,
      java.util.function.DoubleConsumer onChange) {
    final Spinner<Double> spinner = new Spinner<>(
        new DoubleSpinnerValueFactory(0d, 1d, initial, 0.05));
    spinner.setEditable(true);
    spinner.valueProperty().addListener((_, _, v) -> onChange.accept(v));
    return new VBox(2, FxLabels.newLabel(label), spinner);
  }

  /**
   * Buttons that launch the batch-safe filtering modules (rows filter for RSD / detections, blank
   * subtraction) prefilled with the dashboard's feature list. The user configures thresholds in the
   * standard module dialog, so the step is logged in the batch queue.
   */
  private Region buildFilterBox() {
    final Button rowsFilter = new Button("Filter rows (RSD / detections)…");
    rowsFilter.setMaxWidth(Double.MAX_VALUE);
    rowsFilter.setOnAction(_ -> launchWithFeatureList(RowsFilterModule.class,
        ConfigService.getConfiguration().getModuleParameters(RowsFilterModule.class)
            .getParameter(RowsFilterParameters.FEATURE_LISTS)));

    final Button blankSubtraction = new Button("Subtract blanks…");
    blankSubtraction.setMaxWidth(Double.MAX_VALUE);
    blankSubtraction.setOnAction(_ -> launchWithFeatureList(FeatureListBlankSubtractionModule.class,
        ConfigService.getConfiguration()
            .getModuleParameters(FeatureListBlankSubtractionModule.class)
            .getParameter(FeatureListBlankSubtractionParameters.alignedPeakList)));

    return new VBox(4, FxLabels.newLabel("Batch-safe filtering"), rowsFilter, blankSubtraction);
  }

  /**
   * Prefills the module's feature-list parameter with the dashboard feature list (if any), then
   * opens the standard module setup dialog and runs it.
   */
  private void launchWithFeatureList(
      Class<? extends io.github.mzmine.modules.MZmineRunnableModule> moduleClass,
      FeatureListsParameter flistParam) {
    final ModularFeatureList flist = model.getFeatureList();
    if (flist != null) {
      flistParam.setValue(FeatureListsSelectionType.SPECIFIC_FEATURELISTS,
          new FeatureList[]{flist});
    }
    MZmineCore.setupAndRunModule(moduleClass);
  }
}
