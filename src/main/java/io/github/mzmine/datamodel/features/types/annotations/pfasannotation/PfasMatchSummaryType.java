package io.github.mzmine.datamodel.features.types.annotations.pfasannotation;

import io.github.mzmine.datamodel.features.types.modifiers.AnnotationType;
import io.github.mzmine.datamodel.features.types.modifiers.EditableColumnType;
import io.github.mzmine.datamodel.features.types.numbers.abstr.ListDataType;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.PfasMatch;
import javax.annotation.Nonnull;

public class PfasMatchSummaryType extends ListDataType<PfasMatch> implements AnnotationType,
    EditableColumnType {

  @Nonnull
  @Override
  public String getHeaderString() {
    return "PFAS Annotation";
  }


}
