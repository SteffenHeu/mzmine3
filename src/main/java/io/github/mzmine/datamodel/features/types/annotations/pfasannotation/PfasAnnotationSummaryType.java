package io.github.mzmine.datamodel.features.types.annotations.pfasannotation;

import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.datamodel.features.types.FormulaType;
import io.github.mzmine.datamodel.features.types.IonAdductType;
import io.github.mzmine.datamodel.features.types.ModularType;
import io.github.mzmine.datamodel.features.types.modifiers.AnnotationType;
import io.github.mzmine.datamodel.features.types.numbers.MatchingSignalsType;
import io.github.mzmine.datamodel.features.types.numbers.NeutralMassType;
import java.util.List;
import javax.annotation.Nonnull;

public class PfasAnnotationSummaryType extends ModularType implements AnnotationType {

  private static final List<DataType> subTypes = List
      .of(new PfasMatchType(), new FormulaType(), new NeutralMassType(), new IonAdductType(),
          new MatchingSignalsType());

  @Nonnull
  @Override
  public String getHeaderString() {
    return "PFAS annotation";
  }

  @Nonnull
  @Override
  public List<DataType> getSubDataTypes() {
    return subTypes;
  }
}
