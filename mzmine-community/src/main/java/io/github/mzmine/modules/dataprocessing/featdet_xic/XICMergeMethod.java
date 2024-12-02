package io.github.mzmine.modules.dataprocessing.featdet_xic;

public enum XICMergeMethod {
  MOST_INTENSE, MOST_INTENSE_ITERATE, OVERRIDE_ZEROS;


  @Override
  public String toString() {
    return switch (this) {
      case MOST_INTENSE -> "Most intense";
      case OVERRIDE_ZEROS -> "Override zeros";
      case MOST_INTENSE_ITERATE -> "Iterative (most intense)";
    };
  }
}
