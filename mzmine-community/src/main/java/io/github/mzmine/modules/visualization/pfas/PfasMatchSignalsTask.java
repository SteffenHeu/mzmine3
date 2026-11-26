package io.github.mzmine.modules.visualization.pfas;

import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasAnnotation;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasMatchSettings;
import io.github.mzmine.javafx.mvci.FxUpdateTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Recomputes the sub-formula and substructure signals of the selected PFAS annotation, building
 * the substructure index can take a moment for large structures.
 */
final class PfasMatchSignalsTask extends FxUpdateTask<PfasAnnotationMatchModel> {

  private final @NotNull PfasAnnotation annotation;
  private final @Nullable PfasMatchSettings settings;
  private @Nullable PfasMatchSignals result;

  /**
   * @param model      viewer model that receives the signals
   * @param annotation annotation to compute the signals for
   * @param settings   matching settings of the annotation run, null if unknown
   */
  PfasMatchSignalsTask(final @NotNull PfasAnnotationMatchModel model,
      final @NotNull PfasAnnotation annotation, final @Nullable PfasMatchSettings settings) {
    super("PFAS match signals", model);
    this.annotation = annotation;
    this.settings = settings;
  }

  @Override
  protected void process() {
    result = PfasMatchSignals.compute(annotation, settings);
  }

  @Override
  protected void updateGuiModel() {
    // the selection may have changed while computing
    if (result != null && model.selectedAnnotationProperty().get() == annotation) {
      model.signalsProperty().set(result);
    }
  }

  @Override
  public String getTaskDescription() {
    return "Computing PFAS sub-formula and substructure signals";
  }

  @Override
  public double getFinishedPercentage() {
    return result != null ? 1d : 0d;
  }
}
