/*
 * Copyright 2006-2021 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package io.github.mzmine.datamodel.features.types.annotations;

import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.datamodel.features.types.ModularType;
import io.github.mzmine.datamodel.features.types.ModularTypeProperty;
import io.github.mzmine.datamodel.features.types.annotations.iin.IonAdductType;
import io.github.mzmine.datamodel.features.types.modifiers.AnnotationType;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipidutils.MatchedLipid;
import java.util.List;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;

public class LipidAnnotationType extends ModularType implements AnnotationType {

  private final List<DataType> subTypes = List.of(//
      new LipidAnnotationSummaryType(), //
      new IonAdductType(), //
      new FormulaType(), //
      new CommentType(), //
      new LipidMsOneErrorType(), //
      new LipidAnnotationMsMsScoreType(), //
      new LipidSpectrumType());

  @Override
  public List<DataType> getSubDataTypes() {
    return subTypes;
  }

  @Override
  public String getHeaderString() {
    return "Lipid Annotation";
  }

  @Override
  public ModularTypeProperty createProperty() {
    ModularTypeProperty property = super.createProperty();

    // add bindings: If first element in summary column changes - update all other columns based on
    // this object
    property.get(LipidAnnotationSummaryType.class)
        .addListener((ListChangeListener<MatchedLipid>) change -> {
          ObservableList<? extends MatchedLipid> summaryProperty = change.getList();
          boolean firstElementChanged = false;
          while (change.next()) {
            firstElementChanged = firstElementChanged || change.getFrom() == 0;
          }
          if (firstElementChanged) {
            // first list elements has changed - set all other fields
            setCurrentElement(property, summaryProperty.isEmpty() ? null : summaryProperty.get(0),
                summaryProperty.size());
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
  private void setCurrentElement(ModularTypeProperty data, MatchedLipid match,
      int numberOfAnnotations) {
    if (match == null) {
      for (DataType type : this.getSubDataTypes()) {
        if (!(type instanceof LipidAnnotationSummaryType)) {
          data.set(type, null);
        }
      }
    } else {

      // update selected values
      data.set(FormulaType.class,
          MolecularFormulaManipulator.getString(match.getLipidAnnotation().getMolecularFormula()));
      data.set(IonAdductType.class, match.getIonizationType().getAdductName());
      if (match.getComment() != null) {
        data.set(CommentType.class, match.getComment());
      }
      // Calc rel mass deviation;
      double exactMass =
          MolecularFormulaManipulator.getMass(match.getLipidAnnotation().getMolecularFormula(),
              AtomContainerManipulator.MonoIsotopic) + match.getIonizationType().getAddedMass();
      double relMassDev = ((exactMass - match.getAccurateMz()) / exactMass) * 1000000;
      data.set(LipidMsOneErrorType.class, relMassDev);
      data.set(LipidAnnotationMsMsScoreType.class, match.getMsMsScore());
      data.set(LipidSpectrumType.class, null);// ??????
    }
  }
}
