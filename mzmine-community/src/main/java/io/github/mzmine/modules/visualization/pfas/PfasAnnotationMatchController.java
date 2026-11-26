package io.github.mzmine.modules.visualization.pfas;

import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasAnnotation;
import io.github.mzmine.gui.framework.fx.SelectedRowsBinding;
import io.github.mzmine.javafx.mvci.FxController;
import io.github.mzmine.javafx.mvci.FxInteractor;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.javafx.properties.PropertyUtils;
import io.github.mzmine.main.ConfigService;
import java.util.List;
import javafx.beans.property.ObjectProperty;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PFAS match viewer of the first selected row: one selected annotation with its MS/MS spectrum,
 * metadata and structure. Bind {@link #selectedRowsProperty()} to follow the row selection of
 * another view.
 */
public final class PfasAnnotationMatchController extends
    FxController<PfasAnnotationMatchModel> implements SelectedRowsBinding {

  private final @NotNull PfasAnnotationMatchInteractor interactor;
  private final @NotNull PfasAnnotationMatchViewBuilder viewBuilder;

  public PfasAnnotationMatchController() {
    super(new PfasAnnotationMatchModel());
    final PfasMatchColors colors = PfasMatchColors.ofDefaultPalette();
    interactor = new PfasAnnotationMatchInteractor(model, colors);
    viewBuilder = new PfasAnnotationMatchViewBuilder(model, colors);

    model.selectedRowsProperty().subscribe(rows -> model.selectedRowProperty()
        .set(rows == null || rows.isEmpty() ? null : rows.getFirst()));
    model.selectedRowProperty().subscribe(_ -> interactor.updateModel());
    PropertyUtils.onChange(this::computeSignals, model.selectedAnnotationProperty(),
        model.settingsProperty());
    PropertyUtils.onChange(interactor::updateHighlight, model.signalsProperty(),
        model.selectedMzProperty());
  }

  /**
   * Opens the PFAS matches of a row in a window.
   *
   * @param row   row with PFAS annotations
   * @param owner window of the source view, if available
   */
  public static void show(final @NotNull FeatureListRow row, final @Nullable Window owner) {
    final PfasAnnotationMatchController controller = new PfasAnnotationMatchController();
    controller.selectedRowsProperty().set(List.of(row));
    final Stage stage = new Stage();
    if (owner != null) {
      stage.initOwner(owner);
    }
    final Scene scene = new Scene(controller.buildView(), 1100, 690);
    ConfigService.getConfiguration().getTheme().apply(scene.getStylesheets());
    stage.setScene(scene);
    stage.setMinWidth(740);
    stage.setTitle("PFAS annotations · Feature " + row.getID());
    stage.setOnHidden(_ -> controller.close());
    stage.show();
  }

  /**
   * Clears the signals of the previous annotation and recomputes them off the FX thread.
   */
  private void computeSignals() {
    model.signalsProperty().set(null);
    final PfasAnnotation annotation = model.selectedAnnotationProperty().get();
    if (annotation != null) {
      onTaskThread(new PfasMatchSignalsTask(model, annotation, model.settingsProperty().get()));
    }
  }

  /**
   * @return the selected PFAS annotation, null if the row has none
   */
  public @NotNull ObjectProperty<@Nullable PfasAnnotation> selectedAnnotationProperty() {
    return model.selectedAnnotationProperty();
  }

  @Override
  public ObjectProperty<List<FeatureListRow>> selectedRowsProperty() {
    return model.selectedRowsProperty();
  }

  @Override
  protected @NotNull FxViewBuilder<PfasAnnotationMatchModel> getViewBuilder() {
    return viewBuilder;
  }

  @Override
  protected @NotNull FxInteractor<PfasAnnotationMatchModel> getInteractor() {
    return interactor;
  }
}
