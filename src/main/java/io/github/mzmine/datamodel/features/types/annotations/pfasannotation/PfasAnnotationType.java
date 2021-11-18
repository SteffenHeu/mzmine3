package io.github.mzmine.datamodel.features.types.annotations.pfasannotation;

import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.datamodel.features.types.ListWithSubsType;
import io.github.mzmine.datamodel.features.types.annotations.IntensityCoverageType;
import io.github.mzmine.datamodel.features.types.annotations.formula.FormulaType;
import io.github.mzmine.datamodel.features.types.modifiers.AnnotationType;
import io.github.mzmine.datamodel.features.types.numbers.MatchingSignalsType;
import io.github.mzmine.datamodel.features.types.numbers.MzPpmDifferenceType;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.PfasMatch;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

public class PfasAnnotationType extends ListWithSubsType<PfasMatch> implements AnnotationType {

  private static final List<DataType> subTypes = List.of(new PfasMatchSummaryType(),
      new FormulaType(), new MzPpmDifferenceType(), new IntensityCoverageType(),
      new MatchedBlocksType(), new MatchingSignalsType());

  private static final Map<Class<? extends DataType>, Function<PfasMatch, Object>> mapper = Map.ofEntries(
      createEntry(PfasAnnotationType.class, match -> match),
      createEntry(FormulaType.class, match -> match.getCompound().getFormula().toString()),
      createEntry(MzPpmDifferenceType.class, match -> (float) MZTolerance.getPpmDifference(
          match.getCompound().getPrecursorMz(
              match.getRow().getBestFeature().getRepresentativeScan().getPolarity()),
          match.getRow().getAverageMZ())),
      createEntry(IntensityCoverageType.class, match -> match.getCoverageScore()),
      createEntry(MatchedBlocksType.class, match -> match),
      createEntry(MatchingSignalsType.class, match -> match.getMatchedFragments().size())
  );


  @NotNull
  @Override
  public String getHeaderString() {
    return "PFAS annotation";
  }

  @NotNull
  @Override
  public List<DataType> getSubDataTypes() {
    return subTypes;
  }

  @Override
  protected Map<Class<? extends DataType>, Function<PfasMatch, Object>> getMapper() {
    return mapper;
  }

  @Override
  public @NotNull String getUniqueID() {
    return "pfas_annotation_modular";
  }
}
