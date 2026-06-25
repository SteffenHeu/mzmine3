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

import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.gui.framework.fx.FxControllerBinding;
import io.github.mzmine.gui.framework.fx.SelectedAbundanceMeasureBinding;
import io.github.mzmine.gui.framework.fx.SelectedFeatureListsBinding;
import io.github.mzmine.gui.framework.fx.SelectedMetadataColumnBinding;
import io.github.mzmine.gui.framework.fx.SelectedRowsBinding;
import io.github.mzmine.javafx.mvci.FxController;
import io.github.mzmine.javafx.mvci.FxInteractor;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.modules.dataanalysis.qcdashboard.plots.detectioncount.DetectionCountController;
import io.github.mzmine.modules.dataanalysis.qcdashboard.plots.deviation.DeviationKind;
import io.github.mzmine.modules.dataanalysis.qcdashboard.plots.deviation.DeviationPlotController;
import io.github.mzmine.modules.dataanalysis.qcdashboard.plots.intensity.IntensityPlotController;
import io.github.mzmine.modules.dataanalysis.qcdashboard.plots.perfile.FileAggregateKind;
import io.github.mzmine.modules.dataanalysis.qcdashboard.plots.perfile.PerFileAggregateController;
import io.github.mzmine.modules.visualization.featurelisttable_modular.FeatureTableFXParameters;
import io.github.mzmine.modules.visualization.featurelisttable_modular.FeatureTableOwner;
import io.github.mzmine.modules.visualization.featurelisttable_modular.FxFeatureTableController;
import io.github.mzmine.modules.visualization.projectmetadata.table.columns.MetadataColumn;
import io.github.mzmine.parameters.ParameterSet;
import java.util.List;
import java.util.Map;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Main MVCI controller of the QC dashboard. Owns the shared {@link QcDashboardModel}, the subplot
 * controllers and the feature table controller, and wires the shared state to them.
 */
public class QcDashboardController extends FxController<QcDashboardModel> implements
    SelectedFeatureListsBinding, SelectedRowsBinding, SelectedAbundanceMeasureBinding,
    SelectedMetadataColumnBinding {

  private final QcDashboardInteractor interactor;
  private final QcDashboardViewBuilder builder;
  private final FxFeatureTableController tableController;
  private final IntensityPlotController intensityController = new IntensityPlotController();
  private final DeviationPlotController mzDeviationController = new DeviationPlotController(
      DeviationKind.MZ);
  private final DeviationPlotController rtDeviationController = new DeviationPlotController(
      DeviationKind.RT);
  private final PerFileAggregateController featureCountController = new PerFileAggregateController(
      FileAggregateKind.FEATURE_COUNT);
  private final PerFileAggregateController sumIntensityController = new PerFileAggregateController(
      FileAggregateKind.SUM_INTENSITY);
  private final DetectionCountController detectionCountController = new DetectionCountController();

  public QcDashboardController() {
    super(new QcDashboardModel());

    interactor = new QcDashboardInteractor(model);
    // persistParametersOnClose=false: the dashboard hides graphical columns on its own cloned
    // parameter set; it must not write that back into the global feature table configuration.
    tableController = new FxFeatureTableController(FeatureTableOwner.QC_DASHBOARD, false);
    reduceTableColumns();
    builder = new QcDashboardViewBuilder(model, tableController, intensityController,
        mzDeviationController, rtDeviationController, featureCountController, sumIntensityController,
        detectionCountController);

    // bidirectional binds for the shared selection/abundance properties
    FxControllerBinding.bindExposedProperties(this, intensityController);
    FxControllerBinding.bindExposedProperties(this, mzDeviationController);
    FxControllerBinding.bindExposedProperties(this, rtDeviationController);
    FxControllerBinding.bindExposedProperties(this, featureCountController);
    FxControllerBinding.bindExposedProperties(this, sumIntensityController);

    // one-way binds for parent-derived, read-only state (no binding interface for these)
    bindFileState(intensityController.orderedFilesProperty(),
        intensityController.fileColorsProperty());
    bindFileState(mzDeviationController.orderedFilesProperty(),
        mzDeviationController.fileColorsProperty());
    bindFileState(rtDeviationController.orderedFilesProperty(),
        rtDeviationController.fileColorsProperty());
    bindFileState(featureCountController.orderedFilesProperty(),
        featureCountController.fileColorsProperty());
    bindFileState(sumIntensityController.orderedFilesProperty(),
        sumIntensityController.fileColorsProperty());
    featureCountController.featureListProperty().bind(model.featureListProperty());
    sumIntensityController.featureListProperty().bind(model.featureListProperty());

    // detection count: feature list + QC files + thresholds
    detectionCountController.featureListProperty().bind(model.featureListProperty());
    detectionCountController.qcFilesProperty().bind(model.qcFilesProperty());
    detectionCountController.goodQualityFractionProperty().bind(model.goodQualityFractionProperty());
    detectionCountController.warwickFractionProperty().bind(model.warwickFractionProperty());

    // global mean ± SD overlay toggle -> each per-file plot
    intensityController.showMeanSdIntervalProperty().bind(model.showMeanSdIntervalProperty());
    mzDeviationController.showMeanSdIntervalProperty().bind(model.showMeanSdIntervalProperty());
    rtDeviationController.showMeanSdIntervalProperty().bind(model.showMeanSdIntervalProperty());
    featureCountController.showMeanSdIntervalProperty().bind(model.showMeanSdIntervalProperty());
    sumIntensityController.showMeanSdIntervalProperty().bind(model.showMeanSdIntervalProperty());

    // recompute derived state (orderedFiles / qcFiles / fileColors) when inputs change
    model.flistsProperty().addListener((_, _, _) -> recomputeDerivedState());
    model.sampleTypesToShowProperty().addListener((_, _, _) -> recomputeDerivedState());
    model.batchColumnProperty().addListener((_, _, _) -> recomputeDerivedState());
  }

  private void recomputeDerivedState() {
    onGuiThread(interactor::updateModel);
  }

  @Override
  protected @NotNull FxViewBuilder<QcDashboardModel> getViewBuilder() {
    return builder;
  }

  @Override
  protected @Nullable FxInteractor<QcDashboardModel> getInteractor() {
    return interactor;
  }

  /**
   * Hide the graphical (shape/image/IMS) columns so the dashboard table stays compact.
   */
  private void reduceTableColumns() {
    final ParameterSet params = tableController.getModel().getParameters();
    params.setParameter(FeatureTableFXParameters.defaultVisibilityOfShapes, false);
    params.setParameter(FeatureTableFXParameters.defaultVisibilityOfImages, false);
    params.setParameter(FeatureTableFXParameters.defaultVisibilityOfImsFeature, false);
  }

  private void bindFileState(ObjectProperty<List<RawDataFile>> orderedFiles,
      ObjectProperty<Map<RawDataFile, Color>> fileColors) {
    orderedFiles.bind(model.orderedFilesProperty());
    fileColors.bind(model.fileColorsProperty());
  }

  @Override
  public void close() {
    super.close();
    tableController.close();
    intensityController.close();
    mzDeviationController.close();
    rtDeviationController.close();
    featureCountController.close();
    sumIntensityController.close();
    detectionCountController.close();
  }

  public @NotNull FxFeatureTableController getTableController() {
    return tableController;
  }

  // --- exposed bindings -----------------------------------------------------

  @Override
  public Property<List<FeatureList>> selectedFeatureListsProperty() {
    return model.flistsProperty();
  }

  @Override
  public ObjectProperty<List<FeatureListRow>> selectedRowsProperty() {
    return model.selectedRowsProperty();
  }

  @Override
  public ObjectProperty<AbundanceMeasure> abundanceMeasureProperty() {
    return model.abundanceProperty();
  }

  @Override
  public ObjectProperty<@Nullable MetadataColumn<?>> groupingColumnProperty() {
    return model.batchColumnProperty();
  }
}
