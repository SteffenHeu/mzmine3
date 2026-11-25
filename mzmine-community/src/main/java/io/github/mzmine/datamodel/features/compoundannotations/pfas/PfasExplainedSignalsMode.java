package io.github.mzmine.datamodel.features.compoundannotations.pfas;

import io.github.mzmine.datamodel.utils.UniqueIdSupplier;
import org.jetbrains.annotations.NotNull;

/**
 * Which signals count as explained for the minimum explained intensity of a PFAS annotation.
 */
public enum PfasExplainedSignalsMode implements UniqueIdSupplier {
  /**
   * Only peaks matched by fragmentation rules
   */
  RULES("Rules"),
  /**
   * Rule-matched peaks and sub-formulas of the precursor that are a substructure of the structure
   */
  RULES_AND_SUBSTRUCTURES("Rules + substructures"),
  /**
   * Rule-matched peaks and all sub-formulas of the precursor ion
   */
  ALL("Rules + substructures + sub-formulas");

  private final @NotNull String label;

  PfasExplainedSignalsMode(@NotNull String label) {
    this.label = label;
  }

  @Override
  public @NotNull String getUniqueID() {
    return switch (this) {
      case RULES -> "rules";
      case RULES_AND_SUBSTRUCTURES -> "rules_substructures";
      case ALL -> "all";
    };
  }

  @Override
  public String toString() {
    return label;
  }
}
