/*
 * Copyright (c) 2004-2026 The mzmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.datamodel.features.types.annotations.pfas;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.compoundannotations.FeatureAnnotation;
import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.datamodel.features.types.ListWithSubsType;
import io.github.mzmine.datamodel.features.types.annotations.AnnotationSummaryType;
import io.github.mzmine.datamodel.features.types.annotations.CommentType;
import io.github.mzmine.datamodel.features.types.annotations.CompoundNameType;
import io.github.mzmine.datamodel.features.types.annotations.MolecularStructureType;
import io.github.mzmine.datamodel.features.types.annotations.SmilesStructureType;
import io.github.mzmine.datamodel.features.types.annotations.formula.FormulaType;
import io.github.mzmine.datamodel.features.types.annotations.iin.IonTypeType;
import io.github.mzmine.datamodel.features.types.modifiers.AnnotationType;
import io.github.mzmine.datamodel.features.types.numbers.MatchingSignalsType;
import io.github.mzmine.datamodel.features.types.numbers.MzPpmDifferenceType;
import io.github.mzmine.datamodel.features.types.numbers.PrecursorMZType;
import io.github.mzmine.datamodel.features.types.numbers.scores.ExplainedIntensityPercentType;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.modules.io.projectload.version_3_0.CONST;
import io.github.mzmine.modules.visualization.featurelisttable_modular.FeatureTableFX;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasAnnotation;
import io.github.mzmine.modules.visualization.pfas.PfasAnnotationMatchController;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Rule-based PFAS annotations of a row, best first.
 */
public class PfasMatchListType extends ListWithSubsType<PfasAnnotation> implements AnnotationType {

  private static final List<DataType> SUB_TYPES = List.of(new PfasMatchListType(),
      new AnnotationSummaryType(), new CompoundNameType(), new PfasConfidenceLevelType(),
      new PfasCandidateSourceType(), new PfasClassType(), new FormulaType(), new IonTypeType(),
      new MolecularStructureType(), new SmilesStructureType(), new PrecursorMZType(),
      new MzPpmDifferenceType(), new MatchingSignalsType(), new ExplainedIntensityPercentType(),
      new CommentType());

  @Override
  public @NotNull String getUniqueID() {
    // Never change the ID for compatibility during saving/loading of type
    return "pfas_annotations";
  }

  @Override
  public @NotNull String getHeaderString() {
    return "PFAS annotation";
  }

  @Override
  public @NotNull List<DataType> getSubDataTypes() {
    return SUB_TYPES;
  }

  @Override
  protected <K> @Nullable K map(final @NotNull DataType<K> subType,
      final @NotNull PfasAnnotation item) {
    return item.get(subType);
  }

  /**
   * Opens the PFAS matches of a feature, from a double click on the annotation quality summary.
   *
   * @param table source feature table
   * @param row   selected feature
   */
  public static void showMatches(final @Nullable FeatureTableFX table,
      final @NotNull ModularFeatureListRow row) {
    final List<PfasAnnotation> annotations = row.get(PfasMatchListType.class);
    if (annotations == null || annotations.isEmpty()) {
      return;
    }
    PfasAnnotationMatchController.show(row,
        table != null && table.getScene() != null ? table.getScene().getWindow() : null);
  }

  @Override
  public void saveToXML(@NotNull XMLStreamWriter writer, @Nullable Object value,
      @NotNull ModularFeatureList flist, @NotNull ModularFeatureListRow row,
      @Nullable ModularFeature feature, @Nullable RawDataFile file) throws XMLStreamException {
    if (value == null) {
      return;
    }
    if (!(value instanceof List<?> list)) {
      throw new IllegalArgumentException(
          "Wrong value type for data type: " + getClass().getName() + " value class: "
              + value.getClass());
    }
    for (final Object item : list) {
      if (item instanceof PfasAnnotation annotation) {
        annotation.saveToXML(writer, flist, row);
      }
    }
  }

  @Override
  public Object loadFromXML(@NotNull XMLStreamReader reader, @NotNull MZmineProject project,
      @NotNull ModularFeatureList flist, @NotNull ModularFeatureListRow row,
      @Nullable ModularFeature feature, @Nullable RawDataFile file) throws XMLStreamException {
    if (!(reader.isStartElement() && reader.getLocalName().equals(CONST.XML_DATA_TYPE_ELEMENT)
        && getUniqueID().equals(reader.getAttributeValue(null, CONST.XML_DATA_TYPE_ID_ATTR)))) {
      throw new IllegalStateException("Wrong element");
    }
    final List<PfasAnnotation> annotations = new ArrayList<>();
    while (reader.hasNext() && !(reader.isEndElement() && reader.getLocalName()
        .equals(CONST.XML_DATA_TYPE_ELEMENT))) {
      reader.next();
      if (reader.isStartElement() && reader.getLocalName().equals(FeatureAnnotation.XML_ELEMENT)
          && PfasAnnotation.XML_ATTR.equals(
          reader.getAttributeValue(null, FeatureAnnotation.XML_TYPE_ATTR))) {
        annotations.add(PfasAnnotation.loadFromXML(reader, project, flist, row));
      }
    }
    return annotations;
  }

  @Override
  public boolean requiresFeatureListContext() {
    // the matched scan is resolved from the project raw data files
    return true;
  }

  @Override
  public void saveToXML(@NotNull final XMLStreamWriter writer, @Nullable final Object value) {
    throw new UnsupportedOperationException(
        getClass().getName() + " requires feature list context for XML save/load");
  }

  @Override
  public @Nullable Object loadFromXML(@NotNull final XMLStreamReader reader) {
    throw new UnsupportedOperationException(
        getClass().getName() + " requires feature list context for XML save/load");
  }

  @Override
  public boolean getDefaultVisibility() {
    return false;
  }

  @Override
  public double getPrefColumnWidth() {
    return 150;
  }
}
