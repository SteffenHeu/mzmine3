package io.github.mzmine.modules.visualization.pfas;

import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasAnnotation;
import io.github.mzmine.gui.framework.fx.SelectedRowsBinding;
import io.github.mzmine.gui.mainwindow.SimpleTab;
import io.github.mzmine.javafx.mvci.FxController;
import io.github.mzmine.javafx.mvci.FxInteractor;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.javafx.properties.PropertyUtils;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.visualization.featurelisttable_modular.FeatureTableFX;
import io.github.mzmine.util.FeatureUtils;
import io.github.mzmine.util.javafx.WeakAdapter;
import java.util.List;
import javafx.beans.property.ObjectProperty;
import javafx.scene.layout.Region;
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
   * Opens the PFAS matches of a row in a new tab that follows the row selection of the feature
   * table while its update on selection is active.
   *
   * @param table feature table whose selected row is shown, null to show only this row
   * @param row   row with PFAS annotations shown first
   */
  public static void showInTab(final @Nullable FeatureTableFX table,
      final @NotNull FeatureListRow row) {
    final PfasAnnotationMatchController controller = new PfasAnnotationMatchController();
    controller.selectedRowsProperty().set(List.of(row));
    final SimpleTab tab = new SimpleTab("PFAS annotations", controller.buildView(), true, true);
    controller.model.selectedRowProperty().subscribe(
        r -> tab.setSubTitle(r == null ? null : FeatureUtils.rowToString(r)));

    final WeakAdapter weak = new WeakAdapter();
    if (table != null) {
      weak.addListChangeListener(table, table.getSelectedTableRows(), _ -> {
        final List<FeatureListRow> rows = List.copyOf(table.getSelectedRows());
        // keep the shown row while nothing is selected
        if (!weak.isDisposed() && tab.isUpdateOnSelection() && !rows.isEmpty()) {
          controller.selectedRowsProperty().set(rows);
        }
      });
    }
    tab.setOnClosed(_ -> {
      weak.dipose();
      controller.close();
    });
    MZmineCore.getDesktop().addTab(tab);
  }

  /**
   * Match card of a single annotation, e.g. for a list of all annotations of a row. Must be called
   * on the FX thread.
   *
   * @param annotation annotation to show
   * @param row        row of the annotation for the observed m/z and the matching settings, or null
   * @return the card without annotation selection
   */
  public static @NotNull Region createPane(final @NotNull PfasAnnotation annotation,
      final @Nullable FeatureListRow row) {
    final PfasAnnotationMatchController controller = new PfasAnnotationMatchController();
    final Region view = controller.buildView();
    controller.selectedRowsProperty().set(row == null ? List.of() : List.of(row));
    // decision: the list shows every annotation in its own card, so this card shows only one
    controller.model.getAnnotations().setAll(annotation);
    controller.model.selectedAnnotationProperty().set(annotation);
    return view;
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
