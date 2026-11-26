package io.github.mzmine.modules.visualization.pfas;

import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasAnnotation;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasMatchSettings;
import java.util.List;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openscience.cdk.interfaces.IAtomContainer;

/**
 * State of the PFAS match viewer: the selected row, its annotations, the selected annotation with
 * its recomputed signals and the selected peak in the spectrum.
 */
final class PfasAnnotationMatchModel {

  private final ObjectProperty<@NotNull List<FeatureListRow>> selectedRows = new SimpleObjectProperty<>(
      List.of());
  private final ObjectProperty<@Nullable FeatureListRow> selectedRow = new SimpleObjectProperty<>();
  private final ObservableList<PfasAnnotation> annotations = FXCollections.observableArrayList();
  private final ObjectProperty<@Nullable PfasAnnotation> selectedAnnotation = new SimpleObjectProperty<>();
  private final ObjectProperty<@Nullable PfasMatchSettings> settings = new SimpleObjectProperty<>();
  private final ObjectProperty<@Nullable PfasMatchSignals> signals = new SimpleObjectProperty<>();
  private final ObjectProperty<@Nullable Double> selectedMz = new SimpleObjectProperty<>();
  private final ObjectProperty<@Nullable IAtomContainer> highlightedStructure = new SimpleObjectProperty<>();

  /**
   * @return rows selected in a linked view, the first row is shown
   */
  @NotNull ObjectProperty<@NotNull List<FeatureListRow>> selectedRowsProperty() {
    return selectedRows;
  }

  /**
   * @return the shown row, the first of {@link #selectedRowsProperty()}
   */
  @NotNull ObjectProperty<@Nullable FeatureListRow> selectedRowProperty() {
    return selectedRow;
  }

  /**
   * @return PFAS annotations of the selected row, best first
   */
  @NotNull ObservableList<PfasAnnotation> getAnnotations() {
    return annotations;
  }

  /**
   * @return the shown annotation
   */
  @NotNull ObjectProperty<@Nullable PfasAnnotation> selectedAnnotationProperty() {
    return selectedAnnotation;
  }

  /**
   * @return matching settings of the latest PFAS annotation run on the row's feature list, null if
   * unknown, then no sub-formula signals are shown
   */
  @NotNull ObjectProperty<@Nullable PfasMatchSettings> settingsProperty() {
    return settings;
  }

  /**
   * @return recomputed signals of the selected annotation, null while they are computed
   */
  @NotNull ObjectProperty<@Nullable PfasMatchSignals> signalsProperty() {
    return signals;
  }

  /**
   * @return m/z of the peak selected in the spectrum, null if none is selected
   */
  @NotNull ObjectProperty<@Nullable Double> selectedMzProperty() {
    return selectedMz;
  }

  /**
   * @return the structure with the substructure of the selected peak highlighted, null if the
   * selected peak has no substructure
   */
  @NotNull ObjectProperty<@Nullable IAtomContainer> highlightedStructureProperty() {
    return highlightedStructure;
  }
}
