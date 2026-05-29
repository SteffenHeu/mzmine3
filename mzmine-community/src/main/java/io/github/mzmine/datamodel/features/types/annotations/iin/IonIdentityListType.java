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

package io.github.mzmine.datamodel.features.types.annotations.iin;

import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.ModularDataModel;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.datamodel.features.types.ListWithSubsType;
import io.github.mzmine.datamodel.features.types.annotations.RdbeType;
import io.github.mzmine.datamodel.features.types.annotations.formula.ConsensusFormulaListType;
import io.github.mzmine.datamodel.features.types.annotations.formula.FormulaMassType;
import io.github.mzmine.datamodel.features.types.annotations.formula.SimpleFormulaListType;
import io.github.mzmine.datamodel.features.types.modifiers.AnnotationType;
import io.github.mzmine.datamodel.features.types.numbers.MZType;
import io.github.mzmine.datamodel.features.types.numbers.MzAbsoluteDifferenceType;
import io.github.mzmine.datamodel.features.types.numbers.MzPpmDifferenceType;
import io.github.mzmine.datamodel.features.types.numbers.NeutralMassType;
import io.github.mzmine.datamodel.features.types.numbers.SizeType;
import io.github.mzmine.datamodel.features.types.numbers.scores.CombinedScoreType;
import io.github.mzmine.datamodel.features.types.numbers.scores.IsotopePatternScoreType;
import io.github.mzmine.datamodel.features.types.numbers.scores.MsMsScoreType;
import io.github.mzmine.datamodel.identities.MolecularFormulaIdentity;
import io.github.mzmine.datamodel.identities.iontype.IonIdentity;
import io.github.mzmine.datamodel.identities.iontype.IonNetwork;
import io.github.mzmine.modules.dataprocessing.id_formulaprediction.ResultFormula;
import io.github.mzmine.modules.io.projectload.version_3_0.CONST;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.XMLEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A modular annotation type displaying all subtypes for the first element in a list of
 * {@link IonIdentity} stored in {@link SimpleIonIdentityListType}
 */
public class IonIdentityListType extends ListWithSubsType<IonIdentity> implements AnnotationType {

  private static final Logger logger = Logger.getLogger(IonIdentityListType.class.getName());
  // Unmodifiable list of all subtypes
  private static final List<DataType> subTypes = List.of(new IonNetworkIDType(),
      new IonIdentityListType(),
      // start with netID
      new SizeType(), new NeutralMassType(),
      // all realtionship types
      new IINRelationshipsType(), new IINRelationshipsSummaryType(),
      // all formula types
      // list of IIN consensus formulas
      new ConsensusFormulaListType(),
      // List of formulas for this row and all related types
      new SimpleFormulaListType(), new FormulaMassType(), new RdbeType(), new MZType(),
      new MzPpmDifferenceType(), new MzAbsoluteDifferenceType(), new IsotopePatternScoreType(),
      new MsMsScoreType(), new CombinedScoreType());

  /**
   * @return best mol formula on the owning network, or {@code null} if the ion has no network or
   * the network has no formulas. Replaces the former per-ion formula lookup.
   */
  private static @Nullable ResultFormula getMolFormula(@NotNull IonIdentity ion) {
    final IonNetwork net = ion.getNetwork();
    if (net == null) {
      return null;
    }
    final List<ResultFormula> formulas = net.getMolFormulas();
    return formulas.isEmpty() ? null : formulas.get(0);
  }

  /**
   * @return best mol formula on the owning network as {@link Optional}, never null.
   */
  private static @NotNull java.util.Optional<ResultFormula> bestMolFormula(
      @NotNull IonIdentity ion) {
    return java.util.Optional.ofNullable(getMolFormula(ion));
  }

  @Override
  public @NotNull List<DataType> getSubDataTypes() {
    return subTypes;
  }

  @Override
  public double getPrefColumnWidth() {
    return IonTypeType.getFormulaPrefColumnWidth();
  }

  @Override
  protected <K> @Nullable K map(@NotNull final DataType<K> subType, final IonIdentity ion) {
    final IonNetwork net = ion.getNetwork();
    return (K) switch (subType) {
      case IonNetworkIDType __ -> net != null ? ion.getNetID() : null;
      case SizeType __ -> net != null ? net.size() : null;
      case NeutralMassType __ -> net != null ? net.getNeutralMass() : null;
      // list of relationships has no order
      case IINRelationshipsType __ ->
          net != null ? new ArrayList<>(net.getRelations().entrySet()) : null;
      case IINRelationshipsSummaryType __ ->
          net != null && net.getRelations() != null ? net.getRelations().entrySet().stream().map(
              entry -> entry.getValue().getName(entry.getKey())).collect(Collectors.joining(";"))
              : null;
      //
      // Both consensus and simple formula columns now read from the network — there is no
      // longer a separate per-ion formula list.
      case ConsensusFormulaListType __ -> net != null ? net.getMolFormulas() : null;
      case SimpleFormulaListType __ -> net != null ? net.getMolFormulas() : null;
      case FormulaMassType __ ->
          bestMolFormula(ion).map(MolecularFormulaIdentity::getExactMass).orElse(null);
      case RdbeType __ -> bestMolFormula(ion).map(MolecularFormulaIdentity::getRDBE).orElse(null);
      case MZType __ ->
          bestMolFormula(ion).map(MolecularFormulaIdentity::getExactMass).orElse(null);
      case MzPpmDifferenceType __ ->
          bestMolFormula(ion).map(ResultFormula::getPpmDiff).orElse(null);
      case MzAbsoluteDifferenceType __ ->
          bestMolFormula(ion).map(ResultFormula::getAbsoluteMzDiff).orElse(null);
      case IsotopePatternScoreType __ ->
          bestMolFormula(ion).map(ResultFormula::getIsotopeScore).orElse(null);
      case MsMsScoreType __ -> bestMolFormula(ion).map(ResultFormula::getMSMSScore).orElse(null);
      case CombinedScoreType __ -> bestMolFormula(ion).map(f -> f.getScore(10, 3, 1)).orElse(null);
      default -> throw new UnsupportedOperationException(
          "DataType %s is not covered in map".formatted(subType.toString()));
    };
  }

  @NotNull
  @Override
  public String getHeaderString() {
    return "Ion identity";
  }

  @NotNull
  @Override
  public final String getUniqueID() {
    // Never change the ID for compatibility during saving/loading of type
    return "ion_identities";
  }

  @Override
  public <T> void valueChanged(ModularDataModel model, DataType<T> subType, int subColumnIndex,
      T newValue) {
    try {
      if (subType.getClass().equals(ConsensusFormulaListType.class) || subType.getClass()
          .equals(SimpleFormulaListType.class)) {
        // Formulas now live on the network; both columns mutate the same list.
        final IonNetwork net = model.get(this).get(0).getNetwork();
        if (net != null) {
          List<ResultFormula> formulas = net.getMolFormulas();
          formulas.remove(newValue);
          formulas.add(0, (ResultFormula) newValue);
        }
      } else if (subType.getClass().equals(IonIdentityListType.class)) {
        List<IonIdentity> ions = model.get(this);
        if (ions != null) {
          ions = new ArrayList<>(ions);
          ions.remove(newValue);
          ions.add(0, (IonIdentity) newValue);
          model.set(this, ions);
        }
      }
    } catch (Exception ex) {
      logger.log(Level.WARNING, () -> String.format(
          "Cannot handle change in subtype %s at index %d in parent type %s with new value %s",
          subType.getClass().getName(), subColumnIndex, this.getClass().getName(), newValue));
    }
  }

  @Override
  public boolean getDefaultVisibility() {
    return true;
  }

  // ---------------------------------------------------------------------------
  // Persistence
  // ---------------------------------------------------------------------------
  //
  // Per-row payload is just a list of network ids. The actual IonNetwork objects (with their
  // nodes, formulas, neutral mass, relations) live in the per-flist {name}_ionnetworks.xml file
  // and are loaded by FeatureListLoadTask before rows are parsed. By the time loadFromXML runs
  // here, flist.getIonNetwork(id) returns the populated instance and network.getIonForRow(row)
  // returns the exact IonIdentity attached to this row in the network's node list, so we just
  // collect those.
  //
  // Legacy (pre-refactor) projects that wrote inline <ionidentity> payloads round-trip as no-op:
  // the parser walks past unknown elements and returns null, so the row simply has no IIN
  // annotation. This is the explicit no-legacy-support decision documented in the plan.

  @Override
  public void saveToXML(@NotNull final XMLStreamWriter writer, @Nullable final Object value,
      @NotNull final ModularFeatureList flist, @NotNull final ModularFeatureListRow row,
      @Nullable final ModularFeature feature, @Nullable final RawDataFile file)
      throws XMLStreamException {
    if (!(value instanceof List<?> ionsRaw) || ionsRaw.isEmpty()) {
      return;
    }
    writer.writeStartElement(CONST.XML_ION_NETWORK_REFS_ELEMENT);
    for (Object o : ionsRaw) {
      if (!(o instanceof IonIdentity ion)) {
        continue;
      }
      final IonNetwork net = ion.getNetwork();
      if (net == null) {
        // orphan ion (no network) — skip with a warning. See plan §6.
        logger.fine(() -> "Skipping orphan IonIdentity (no network) on row " + row.getID());
        continue;
      }
      writer.writeStartElement(CONST.XML_ION_NETWORK_REF_ELEMENT);
      writer.writeAttribute(CONST.XML_ION_NETWORK_ID_ATTR, String.valueOf(net.getID()));
      writer.writeEndElement();
    }
    writer.writeEndElement();
  }

  @Override
  public Object loadFromXML(@NotNull final XMLStreamReader reader,
      @NotNull final MZmineProject project, @NotNull final ModularFeatureList flist,
      @NotNull final ModularFeatureListRow row, @Nullable final ModularFeature feature,
      @Nullable final RawDataFile file) throws XMLStreamException {
    // reader is positioned on the <datatype type_id="ion_identities"> start element.
    // Walk forward until the matching </datatype>; collect any <ref networkId="..."/> we see.
    final List<IonIdentity> ions = new ArrayList<>();
    while (reader.hasNext()) {
      final int event = reader.next();
      if (event == XMLEvent.END_ELEMENT && CONST.XML_DATA_TYPE_ELEMENT.equals(
          reader.getLocalName())) {
        break;
      }
      if (event != XMLEvent.START_ELEMENT) {
        continue;
      }
      if (!CONST.XML_ION_NETWORK_REF_ELEMENT.equals(reader.getLocalName())) {
        continue;
      }
      final String idAttr = reader.getAttributeValue(null, CONST.XML_ION_NETWORK_ID_ATTR);
      if (idAttr == null) {
        continue;
      }
      final int netId;
      try {
        netId = Integer.parseInt(idAttr);
      } catch (NumberFormatException e) {
        logger.log(Level.WARNING, "Malformed ion network id reference: " + idAttr, e);
        continue;
      }
      final IonNetwork net = flist.getIonNetwork(netId);
      if (net == null) {
        logger.warning(() -> "Row " + row.getID() + " references missing IonNetwork " + netId
            + " — skipping.");
        continue;
      }
      final IonIdentity ion = net.getIonForRow(row);
      if (ion == null) {
        // Network exists but this row isn't a member of it — invariant violation, ignore.
        logger.warning(() -> "IonNetwork " + netId + " has no node for row " + row.getID());
        continue;
      }
      ions.add(ion);
    }
    return ions.isEmpty() ? null : ions;
  }
}
