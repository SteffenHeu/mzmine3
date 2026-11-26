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

package io.github.mzmine.datamodel.features.compoundannotations.pfas;

import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.compoundannotations.CompoundDBAnnotation;
import io.github.mzmine.datamodel.features.compoundannotations.FeatureAnnotation;
import io.github.mzmine.datamodel.features.compoundannotations.SimpleCompoundDBAnnotation;
import io.github.mzmine.datamodel.identities.iontype.IonType;
import io.github.mzmine.datamodel.identities.iontype.IonTypeParser;
import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.datamodel.features.types.DataTypes;
import io.github.mzmine.datamodel.features.types.annotations.CommentType;
import io.github.mzmine.datamodel.features.types.annotations.CompoundNameType;
import io.github.mzmine.datamodel.features.types.annotations.iin.IonTypeType;
import io.github.mzmine.datamodel.features.types.annotations.pfas.PfasBuildingBlocksType;
import io.github.mzmine.datamodel.features.types.annotations.pfas.PfasCandidateSourceType;
import io.github.mzmine.datamodel.features.types.annotations.pfas.PfasConfidenceLevelType;
import io.github.mzmine.datamodel.features.types.annotations.pfas.PfasMatchListType;
import io.github.mzmine.datamodel.features.types.numbers.MatchingSignalsType;
import io.github.mzmine.datamodel.features.types.numbers.MzPpmDifferenceType;
import io.github.mzmine.datamodel.features.types.numbers.PrecursorMZType;
import io.github.mzmine.datamodel.features.types.numbers.scores.ExplainedIntensityPercentType;
import io.github.mzmine.modules.io.projectload.version_3_0.CONST;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.util.ParsingUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A rule-based PFAS annotation. Reuses the flexible data map of a compound database annotation and
 * adds the matched MS2 scan and the assigned peaks.
 */
public class PfasAnnotation extends SimpleCompoundDBAnnotation {

  public static final String XML_ATTR = "pfas_annotation";
  public static final String DATABASE_NAME = "mzmine PFAS rules";
  public static final String PROPOSED_STRUCTURE_NOTE = "proposed structure assembled from building blocks (linear isomer)";
  private static final String XML_PEAK = "pfas_matched_peak";
  private static final String XML_IN_SOURCE_FORM = "pfas_in_source_form";
  private static final String XML_PARENT_ROW_ATTR = "in_source_parent_row";
  private static final String XML_EVIDENCE_ATTR = "in_source_evidence";
  private static final String XML_CLASS_ATTR = "pfas_class_id";
  private static final String XML_N_ATTR = "chain_lengths";
  private static final String XML_POSITIONS_ATTR = "ether_positions";
  private static final String XML_FLAGS_ATTR = "refinement_flags";
  private static final String XML_ORIGINAL_LEVEL_ATTR = "level_before_refinement";

  private @Nullable Scan scan;
  private @NotNull List<PfasMatchedPeak> matchedPeaks = List.of();
  // predicted in-source fragments of an MS2 annotated precursor
  private @NotNull List<PfasInSourceForm> inSourceForms = List.of();
  // set if this annotation describes an in-source fragment feature of another row
  private @Nullable Integer inSourceParentRowId;
  private @Nullable PfasInSourceEvidence inSourceEvidence;
  // structured candidate identity for homologue series, null for substructures
  private @Nullable String classId;
  private @NotNull List<Integer> chainLengths = List.of();
  private @NotNull List<Integer> etherPositions = List.of();
  // refinement results, reset on every refinement run
  private final @NotNull EnumSet<PfasRefinementFlag> flags = EnumSet.noneOf(
      PfasRefinementFlag.class);
  private @Nullable PfasConfidenceLevel levelBeforeRefinement;

  /**
   * Empty annotation for loading.
   */
  public PfasAnnotation() {
  }

  /**
   * @return true if the structure was assembled from blocks instead of curated
   */
  public boolean isProposedStructure() {
    final String comment = get(CommentType.class);
    return comment != null && comment.contains(PROPOSED_STRUCTURE_NOTE);
  }

  /**
   * Annotation of a feature that is an in-source fragment of an MS2 verified parent. Keeps the
   * parent compound identity but no confidence level, the ion type carries the source loss.
   *
   * @param parent      MS2 verified parent annotation
   * @param parentRowId row id of the parent feature
   * @param form        the matched in-source form
   * @param evidence    how the two features are linked
   * @param observedMz  observed m/z of the in-source fragment feature
   * @return a new in-source annotation
   */
  public static @NotNull PfasAnnotation createInSource(@NotNull PfasAnnotation parent,
      int parentRowId, @NotNull PfasInSourceForm form, @NotNull PfasInSourceEvidence evidence,
      double observedMz) {
    final PfasAnnotation annotation = new PfasAnnotation();
    parent.data.forEach(annotation::put);
    annotation.inSourceParentRowId = parentRowId;
    annotation.classId = parent.classId;
    annotation.chainLengths = parent.chainLengths;
    annotation.etherPositions = parent.etherPositions;
    annotation.inSourceEvidence = evidence;
    annotation.data.remove(DataTypes.get(MatchingSignalsType.class));
    annotation.data.remove(DataTypes.get(ExplainedIntensityPercentType.class));
    // decision: in-source fragments carry no confidence level, only the link to their parent
    annotation.data.remove(DataTypes.get(PfasConfidenceLevelType.class));
    final IonType parentIon = parent.getAdductType();
    // [M-H]- with loss CO2 becomes [M-CO2-H]-
    final IonType ion = parentIon == null ? null : IonTypeParser.parseOptional(
        parentIon.toString().replaceFirst("^\\[M", "[M-" + form.lossFormula())).orElse(null);
    if (ion != null) {
      annotation.put(IonTypeType.class, ion);
    } else {
      annotation.data.remove(DataTypes.get(IonTypeType.class));
    }
    annotation.put(PrecursorMZType.class, form.mz());
    annotation.put(MzPpmDifferenceType.class, (float) ((observedMz - form.mz()) / form.mz() * 1e6));
    annotation.put(CommentType.class,
        "In-source fragment (-%s, %s) of row %d (%s)".formatted(form.lossFormula(), form.ruleId(),
            parentRowId, evidence));
    return annotation;
  }

  /**
   * @return true if this annotation describes an in-source fragment of another row
   */
  public boolean isInSourceFragment() {
    return inSourceParentRowId != null;
  }

  /**
   * @return the explained MS2 intensity (0-1) of the rules and, depending on the annotation
   * parameters, substructures and sub-formulas. Used as MS2 score of the annotation summary.
   */
  @Override
  public @Nullable Float getScore() {
    return get(ExplainedIntensityPercentType.class);
  }

  /**
   * @return true for MS2-only substructure annotations, which describe building blocks and not a
   * compound, and for in-source fragments, which describe the parent compound and not this ion
   */
  @Override
  public boolean isAnalogMatch() {
    return isInSourceFragment()
        || getConfidenceLevel() == PfasConfidenceLevel.MS2_SUBSTRUCTURE;
  }

  /**
   * @return row id of the MS2 verified parent, null for MS2 based annotations
   */
  public @Nullable Integer getInSourceParentRowId() {
    return inSourceParentRowId;
  }

  /**
   * @return how the in-source fragment is linked to its parent or null
   */
  public @Nullable PfasInSourceEvidence getInSourceEvidence() {
    return inSourceEvidence;
  }

  /**
   * @return predicted in-source fragments of this precursor
   */
  public @NotNull List<PfasInSourceForm> getInSourceForms() {
    return inSourceForms;
  }

  /**
   * @return the confidence level or null if missing
   */
  public @Nullable PfasConfidenceLevel getConfidenceLevel() {
    return get(PfasConfidenceLevelType.class);
  }

  /**
   * @param scan the matched MS2 scan or null
   */
  public void setScan(@Nullable Scan scan) {
    this.scan = scan;
  }

  /**
   * @param matchedPeaks assigned peaks
   */
  public void setMatchedPeaks(@NotNull List<PfasMatchedPeak> matchedPeaks) {
    this.matchedPeaks = List.copyOf(matchedPeaks);
  }

  /**
   * @param inSourceForms predicted in-source fragments of this precursor
   */
  public void setInSourceForms(@NotNull List<PfasInSourceForm> inSourceForms) {
    this.inSourceForms = List.copyOf(inSourceForms);
  }

  /**
   * Structured candidate identity of homologue series annotations.
   *
   * @param classId        PFAS class id
   * @param chainLengths   chain repeat counts, one per class chain
   * @param etherPositions ether positions, 0 for chains without ether
   */
  public void setCandidateIdentity(@NotNull String classId, @NotNull List<Integer> chainLengths,
      @NotNull List<Integer> etherPositions) {
    this.classId = classId;
    this.chainLengths = List.copyOf(chainLengths);
    this.etherPositions = List.copyOf(etherPositions);
  }

  /**
   * @return class id of the candidate or null for substructures
   */
  public @Nullable String getClassId() {
    return classId;
  }

  /**
   * @return chain lengths of the candidate
   */
  public @NotNull List<Integer> getChainLengths() {
    return chainLengths;
  }

  /**
   * @return ether positions of the candidate, 0 for chains without ether
   */
  public @NotNull List<Integer> getEtherPositions() {
    return etherPositions;
  }

  /**
   * @return refinement flags, modifiable by the refinement
   */
  public @NotNull Set<PfasRefinementFlag> getFlags() {
    return flags;
  }

  /**
   * @param flag refinement flag
   * @return true if the flag is set
   */
  public boolean hasFlag(@NotNull PfasRefinementFlag flag) {
    return flags.contains(flag);
  }

  /**
   * Changes the level after refinement and keeps the original for the next refinement run.
   *
   * @param level the refined level
   */
  public void setRefinedLevel(@NotNull PfasConfidenceLevel level) {
    if (levelBeforeRefinement == null) {
      levelBeforeRefinement = getConfidenceLevel();
    }
    put(PfasConfidenceLevelType.class, level);
  }

  /**
   * Removes all refinement results and restores the original level.
   */
  public void resetRefinement() {
    flags.clear();
    if (levelBeforeRefinement != null) {
      put(PfasConfidenceLevelType.class, levelBeforeRefinement);
      levelBeforeRefinement = null;
    }
  }

  /**
   * @return the candidate source or null if missing
   */
  public @Nullable PfasCandidateSource getCandidateSource() {
    return get(PfasCandidateSourceType.class);
  }

  /**
   * @return the matched MS2 scan or null
   */
  public @Nullable Scan getScan() {
    return scan;
  }

  /**
   * @return assigned peaks
   */
  public @NotNull List<PfasMatchedPeak> getMatchedPeaks() {
    return matchedPeaks;
  }

  /**
   * @return the captured building blocks and fragment evidence if a full structure was unavailable
   */
  public @NotNull List<PfasBlockEvidence> getBuildingBlocks() {
    return Objects.requireNonNullElse(get(PfasBuildingBlocksType.class), List.of());
  }

  @Override
  public @NotNull Class<? extends DataType> getDataType() {
    return PfasMatchListType.class;
  }

  @Override
  public @NotNull String getXmlAttributeKey() {
    return XML_ATTR;
  }

  @Override
  public PfasAnnotation clone() {
    final PfasAnnotation clone = new PfasAnnotation();
    data.forEach(clone::put);
    clone.scan = scan;
    clone.matchedPeaks = matchedPeaks;
    clone.inSourceForms = inSourceForms;
    clone.inSourceParentRowId = inSourceParentRowId;
    clone.inSourceEvidence = inSourceEvidence;
    clone.classId = classId;
    clone.chainLengths = chainLengths;
    clone.etherPositions = etherPositions;
    clone.flags.addAll(flags);
    clone.levelBeforeRefinement = levelBeforeRefinement;
    return clone;
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof PfasAnnotation other && super.equals(o) && Objects.equals(scan, other.scan)
        && matchedPeaks.equals(other.matchedPeaks) && inSourceForms.equals(other.inSourceForms)
        && Objects.equals(inSourceParentRowId, other.inSourceParentRowId)
        && inSourceEvidence == other.inSourceEvidence && Objects.equals(classId, other.classId)
        && chainLengths.equals(other.chainLengths) && etherPositions.equals(other.etherPositions)
        && flags.equals(other.flags) && levelBeforeRefinement == other.levelBeforeRefinement;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), matchedPeaks);
  }

  // ---------------------------------------------------------------- XML

  @Override
  public void saveToXML(@NotNull XMLStreamWriter writer, ModularFeatureList flist,
      ModularFeatureListRow row) throws XMLStreamException {
    writeOpeningTag(writer);
    writer.writeAttribute(CompoundDBAnnotation.XML_NUM_ENTRIES_ATTR, String.valueOf(data.size()));
    if (inSourceParentRowId != null && inSourceEvidence != null) {
      writer.writeAttribute(XML_PARENT_ROW_ATTR, String.valueOf(inSourceParentRowId));
      writer.writeAttribute(XML_EVIDENCE_ATTR, inSourceEvidence.name());
    }
    if (classId != null) {
      writer.writeAttribute(XML_CLASS_ATTR, classId);
      writer.writeAttribute(XML_N_ATTR, join(chainLengths));
      writer.writeAttribute(XML_POSITIONS_ATTR, join(etherPositions));
    }
    if (!flags.isEmpty()) {
      writer.writeAttribute(XML_FLAGS_ATTR,
          flags.stream().map(Enum::name).collect(Collectors.joining(ParsingUtils.SEPARATOR)));
    }
    if (levelBeforeRefinement != null) {
      writer.writeAttribute(XML_ORIGINAL_LEVEL_ATTR, levelBeforeRefinement.name());
    }
    for (final var entry : data.entrySet()) {
      writer.writeStartElement(CONST.XML_DATA_TYPE_ELEMENT);
      writer.writeAttribute(CONST.XML_DATA_TYPE_ID_ATTR, entry.getKey().getUniqueID());
      entry.getKey().saveToXML(writer, entry.getValue(), flist, row, null, null);
      writer.writeEndElement();
    }
    if (scan != null) {
      Scan.saveScanToXML(writer, scan);
    }
    for (final PfasMatchedPeak peak : matchedPeaks) {
      writer.writeStartElement(XML_PEAK);
      writer.writeAttribute("observed_mz", String.valueOf(peak.observedMz()));
      writer.writeAttribute("intensity", String.valueOf(peak.intensity()));
      writer.writeAttribute("expected_mz", String.valueOf(peak.expectedMz()));
      writer.writeAttribute("formula", peak.formula());
      writer.writeAttribute("charge", String.valueOf(peak.charge()));
      writer.writeAttribute("evidence", peak.evidence().name());
      writer.writeAttribute("rules", String.join(ParsingUtils.SEPARATOR, peak.ruleIds()));
      if (peak.lossFormula() != null) {
        writer.writeAttribute("loss", peak.lossFormula());
      }
      writer.writeEndElement();
    }
    for (final PfasInSourceForm form : inSourceForms) {
      writer.writeStartElement(XML_IN_SOURCE_FORM);
      writer.writeAttribute("formula", form.formula());
      writer.writeAttribute("charge", String.valueOf(form.charge()));
      writer.writeAttribute("mz", String.valueOf(form.mz()));
      writer.writeAttribute("loss", form.lossFormula());
      writer.writeAttribute("rule", form.ruleId());
      writer.writeEndElement();
    }
    writeClosingTag(writer);
  }

  private static @NotNull String join(@NotNull List<Integer> values) {
    return values.stream().map(String::valueOf).collect(Collectors.joining(","));
  }

  private static @NotNull List<Integer> parseInts(@Nullable String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    return Arrays.stream(text.split(",")).map(String::trim).map(Integer::parseInt).toList();
  }

  /**
   * @param reader  reader at the start of a PFAS annotation element
   * @param project project to resolve the scan
   * @param flist   feature list
   * @param row     row
   * @return the loaded annotation
   * @throws XMLStreamException on read errors
   */
  public static @NotNull PfasAnnotation loadFromXML(@NotNull XMLStreamReader reader,
      @NotNull MZmineProject project, @NotNull ModularFeatureList flist,
      @NotNull ModularFeatureListRow row) throws XMLStreamException {
    if (!(reader.isStartElement() && reader.getLocalName().equals(FeatureAnnotation.XML_ELEMENT)
        && XML_ATTR.equals(reader.getAttributeValue(null, FeatureAnnotation.XML_TYPE_ATTR)))) {
      throw new IllegalStateException("Invalid XML element for a PFAS annotation.");
    }
    final PfasAnnotation annotation = new PfasAnnotation();
    final String parentRow = reader.getAttributeValue(null, XML_PARENT_ROW_ATTR);
    if (parentRow != null) {
      annotation.inSourceParentRowId = Integer.parseInt(parentRow);
      annotation.inSourceEvidence = PfasInSourceEvidence.valueOf(
          reader.getAttributeValue(null, XML_EVIDENCE_ATTR));
    }
    final List<PfasMatchedPeak> peaks = new ArrayList<>();
    final List<PfasInSourceForm> forms = new ArrayList<>();
    annotation.classId = reader.getAttributeValue(null, XML_CLASS_ATTR);
    annotation.chainLengths = parseInts(reader.getAttributeValue(null, XML_N_ATTR));
    annotation.etherPositions = parseInts(reader.getAttributeValue(null, XML_POSITIONS_ATTR));
    final String flags = reader.getAttributeValue(null, XML_FLAGS_ATTR);
    if (flags != null) {
      Arrays.stream(flags.split(ParsingUtils.SEPARATOR)).filter(f -> !f.isBlank())
          .map(PfasRefinementFlag::valueOf).forEach(annotation.flags::add);
    }
    final String originalLevel = reader.getAttributeValue(null, XML_ORIGINAL_LEVEL_ATTR);
    if (originalLevel != null) {
      annotation.levelBeforeRefinement = PfasConfidenceLevel.valueOf(originalLevel);
    }
    while (reader.hasNext() && !(reader.isEndElement() && reader.getLocalName()
        .equals(FeatureAnnotation.XML_ELEMENT))) {
      reader.next();
      if (!reader.isStartElement()) {
        continue;
      }
      switch (reader.getLocalName()) {
        case CONST.XML_DATA_TYPE_ELEMENT -> {
          final DataType type = DataTypes.getTypeForId(
              reader.getAttributeValue(null, CONST.XML_DATA_TYPE_ID_ATTR));
          if (type != null) {
            final Object value = type.loadFromXML(reader, project, flist, row, null, null);
            if (value != null) {
              annotation.put(type, value);
            }
          }
        }
        case Scan.XML_SCAN_ELEMENT ->
            annotation.scan = Scan.loadScanFromXML(reader, project.getCurrentRawDataFiles());
        case XML_PEAK -> peaks.add(
            new PfasMatchedPeak(Double.parseDouble(reader.getAttributeValue(null, "observed_mz")),
                Double.parseDouble(reader.getAttributeValue(null, "intensity")),
                Double.parseDouble(reader.getAttributeValue(null, "expected_mz")),
                reader.getAttributeValue(null, "formula"),
                Integer.parseInt(reader.getAttributeValue(null, "charge")),
                PfasEvidenceLevel.valueOf(reader.getAttributeValue(null, "evidence")),
                Arrays.stream(reader.getAttributeValue(null, "rules").split(ParsingUtils.SEPARATOR))
                    .filter(s -> !s.isBlank()).toList(), reader.getAttributeValue(null, "loss")));
        case XML_IN_SOURCE_FORM -> forms.add(
            new PfasInSourceForm(reader.getAttributeValue(null, "formula"),
                Integer.parseInt(reader.getAttributeValue(null, "charge")),
                Double.parseDouble(reader.getAttributeValue(null, "mz")),
                reader.getAttributeValue(null, "loss"), reader.getAttributeValue(null, "rule")));
        default -> {
        }
      }
    }
    annotation.matchedPeaks = List.copyOf(peaks);
    annotation.inSourceForms = List.copyOf(forms);
    return annotation;
  }
}
