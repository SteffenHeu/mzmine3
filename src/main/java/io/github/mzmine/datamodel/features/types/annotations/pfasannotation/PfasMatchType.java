package io.github.mzmine.datamodel.features.types.annotations.pfasannotation;

import io.github.mzmine.datamodel.features.types.modifiers.AnnotationType;
import io.github.mzmine.datamodel.features.types.numbers.abstr.ListDataType;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.PfasMatch;
import javax.annotation.Nonnull;

public class PfasMatchType extends ListDataType<PfasMatch> implements AnnotationType {

  @Nonnull
  @Override
  public String getHeaderString() {
    return "PFAS Annotation";
  }
}
