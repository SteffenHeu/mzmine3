package io.github.mzmine.datamodel.features.types.annotations.pfasannotation;

import io.github.mzmine.datamodel.features.types.modifiers.AnnotationType;
import io.github.mzmine.datamodel.features.types.modifiers.EditableColumnType;
import io.github.mzmine.datamodel.features.types.numbers.abstr.ListDataType;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.PfasMatch;
import org.jetbrains.annotations.NotNull;

public class PfasMatchSummaryType extends ListDataType<PfasMatch> implements AnnotationType,
    EditableColumnType {

  @NotNull
  @Override
  public String getHeaderString() {
    return "PFAS Annotation";
  }


  @Override
  public @NotNull String getUniqueID() {
    return "pfas_annotation_summary";
  }
}
