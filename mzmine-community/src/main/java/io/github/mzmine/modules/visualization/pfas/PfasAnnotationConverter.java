package io.github.mzmine.modules.visualization.pfas;

import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasAnnotation;
import java.util.Objects;
import javafx.util.StringConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shows PFAS annotations by name and confidence level, e.g. in the annotation selection.
 */
final class PfasAnnotationConverter extends StringConverter<PfasAnnotation> {

  /**
   * @param annotation annotation to show, or null
   * @return name and confidence level, empty for null
   */
  @Override
  public @NotNull String toString(final @Nullable PfasAnnotation annotation) {
    if (annotation == null) {
      return "";
    }
    final String name = Objects.requireNonNullElse(annotation.getCompoundName(),
        "PFAS annotation");
    return annotation.getConfidenceLevel() == null ? name
        : "%s · %s".formatted(name, annotation.getConfidenceLevel());
  }

  /**
   * @param string text of the selection
   * @return always null, the selection is not editable
   */
  @Override
  public @Nullable PfasAnnotation fromString(final @Nullable String string) {
    return null;
  }
}
