package io.github.mzmine.datamodel.features.types.annotations.pfasannotation;

import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.datamodel.features.types.ModularType;
import io.github.mzmine.datamodel.features.types.ModularTypeProperty;
import io.github.mzmine.datamodel.features.types.annotations.FormulaType;
import io.github.mzmine.datamodel.features.types.annotations.IntensityCoverageType;
import io.github.mzmine.datamodel.features.types.modifiers.AnnotationType;
import io.github.mzmine.datamodel.features.types.numbers.MatchingSignalsType;
import io.github.mzmine.datamodel.features.types.numbers.MzPpmDifferenceType;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.PfasMatch;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import java.util.List;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;

public class PfasAnnotationType extends ModularType implements AnnotationType {

  private static final List<DataType> subTypes = List
      .of(new PfasMatchSummaryType(), new FormulaType(), new MzPpmDifferenceType(), new IntensityCoverageType(),
          new MatchedBlocksType(), new MatchingSignalsType());

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
  public ModularTypeProperty createProperty() {
    final ModularTypeProperty property = super.createProperty();

    property.get(PfasMatchSummaryType.class)
        .addListener((ListChangeListener<? super PfasMatch>) change -> {
          ObservableList<? extends PfasMatch> summaryProperty = change.getList();
          boolean firstElementChanged = false;
          while (change.next()) {
            firstElementChanged = firstElementChanged || change.getFrom() == 0;
          }
          if (firstElementChanged) {
            // first list elements has changed - set all other fields
            setCurrentElement(property, summaryProperty.isEmpty() ? null : summaryProperty.get(0));
          }
        });

    return property;
  }

  /**
   * On change of the first list element, change all the other sub types.
   *
   * @param data
   * @param match
   */
  private void setCurrentElement(ModularTypeProperty data, PfasMatch match) {
    if (match == null) {
      for (DataType type : this.getSubDataTypes()) {
        if (!(type instanceof PfasMatchSummaryType)) {
          data.set(type, null);
        }
      }
    } else {
      data.set(FormulaType.class,
          MolecularFormulaManipulator.getString(match.getCompound().getFormula()));
      data.set(MatchingSignalsType.class, match.getMatchedFragments().size());
      data.set(IntensityCoverageType.class, match.getCoverageScore());
      data.set(MatchedBlocksType.class, match);
      data.set(MzPpmDifferenceType.class, MZTolerance.getPpmDifference(match.getCompound()
              .getPrecursorMz(match.getRow().getBestFeature().getRepresentativeScan().getPolarity()),
          match.getRow().getAverageMZ()));
    }
  }

  @Override
  public @NotNull String getUniqueID() {
    return "pfas_annotation_modular";
  }
}
