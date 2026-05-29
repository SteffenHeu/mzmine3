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

package io.github.mzmine.datamodel.identities.iontype;

import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.identities.iontype.networks.IonNetworkRelation;
import io.github.mzmine.modules.dataprocessing.id_formulaprediction.ResultFormula;
import io.github.mzmine.modules.io.projectload.version_3_0.CONST;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.util.ParsingUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.XMLEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An annotation network full of ions that point to the same neutral molecule (neutral mass)
 *
 * @author Robin Schmid (robinschmid@uni-muenster.de)
 */
public class IonNetwork implements Comparable<IonNetwork> {

  private final List<IonNetworkNode> nodes = new ArrayList<>();
  // possible formulas for this neutral mass
  private final ObservableList<ResultFormula> molFormulas = FXCollections.observableArrayList();
  // network id — assigned at construction time via ModularFeatureList.nextIonNetworkId() and
  // immutable thereafter so the registry can key by id safely.
  private final int id;
  // neutral mass of central molecule which is described by all members of this network
  private Double neutralMass = null;
  // maximum absolute deviation from neutral mass average
  private Double maxDev = null;
  // average retention time of network
  private double avgRT;
  // summed height
  private double heightSum = 0;
  // can be used to stream all networks only once
  // lowest row id
  private int lowestID = -1;
  // relationship to other IonNetworks (neutral molecules)
  // marks as modification of:
  private Map<IonNetwork, IonNetworkRelation> relations;

  public IonNetwork(int id) {
    super();
    this.id = id;
  }

  /**
   * The ion types are undefined M+?
   *
   * @return
   */
  public boolean isUndefined() {
    return streamIons().map(IonIdentity::getIonType).anyMatch(IonType::isUndefinedAdduct);
  }

  private @NotNull Stream<IonIdentity> streamIons() {
    return getNodes().stream().map(IonNetworkNode::ion);
  }

  /**
   *
   * @return unmodifiable copy of list
   */
  public List<IonNetworkNode> getNodes() {
    return Collections.unmodifiableList(nodes);
  }

  /**
   *
   * @return a list copy of the rows
   */
  public List<FeatureListRow> getRows() {
    List<FeatureListRow> rows = new ArrayList<>(nodes.size());
    for (IonNetworkNode node : nodes) {
      rows.add(node.row());
    }
    return rows;
  }


  /**
   * Network ID
   *
   * @return
   */
  public int getID() {
    return id;
  }

  @NotNull
  public List<ResultFormula> getMolFormulas() {
    return molFormulas;
  }

  public Map<IonNetwork, IonNetworkRelation> getRelations() {
    return Objects.requireNonNullElse(relations, Map.of());
  }

  /**
   * Add a relation to another ion network. This relation could be a modification
   *
   * @param net
   * @param rel
   */
  public void addRelation(IonNetwork net, IonNetworkRelation rel) {
    if (relations == null) {
      relations = new TreeMap<>();
    }
    relations.put(net, rel);
  }

  /**
   * Remove a relation to another ion network. This relation could be a modification
   */
  public void removeRelation(IonNetwork net) {
    if (relations == null) {
      return;
    }
    relations.remove(net);
  }

  /**
   * Clear
   */
  public void clearRelation() {
    relations = null;
  }

  public int size() {
    return nodes.size();
  }

  /**
   * Create relations identity
   */
  public String concatRelationshipsToString() {
    String name = "";
    if (relations != null) {
      name = relations.values().stream().filter(Objects::nonNull).map(rel -> rel.getName(this))
          .collect(Collectors.joining(", "));
    }

    return name;
  }

  public void clearMolFormulas() {
    molFormulas.clear();
  }

  /**
   * The first formula should be the best
   *
   * @param molFormulas
   */
  public void addMolFormulas(List<ResultFormula> molFormulas) {
    this.molFormulas.removeAll(molFormulas);
    this.molFormulas.addAll(molFormulas);
  }

  /**
   * The first formula should be the best
   *
   * @param molFormulas
   */
  public void addMolFormulas(ResultFormula... molFormulas) {
    this.molFormulas.removeAll(molFormulas);
    this.molFormulas.addAll(molFormulas);
  }

  public void addMolFormula(ResultFormula formula) {
    addMolFormula(formula, false);
  }

  public void addMolFormula(ResultFormula formula, boolean asBest) {
    if (!molFormulas.isEmpty()) {
      molFormulas.remove(formula);
    }

    if (asBest) {
      this.molFormulas.add(0, formula);
    } else {
      this.molFormulas.add(formula);
    }
  }

  public void removeMolFormula(ResultFormula formula) {
    if (molFormulas != null && !molFormulas.isEmpty()) {
      molFormulas.remove(formula);
    }
  }

  /**
   * Best molecular formula (first in list)
   *
   * @return
   */
  public ResultFormula getBestMolFormula() {
    return molFormulas == null || molFormulas.isEmpty() ? null : molFormulas.get(0);
  }

  public void setBestMolFormula(ResultFormula formula) {
    addMolFormula(formula, true);
  }

  /**
   * Neutral mass of center molecule which is described by all members of this network
   */
  public double getNeutralMass() {
    return neutralMass == null ? calcNeutralMass() : neutralMass;
  }

  public IonIdentity put(FeatureListRow row, IonIdentity value) {
    remove(row);

    nodes.add(new IonNetworkNode(row, value));

    if (row.getID() < lowestID || lowestID == -1) {
      lowestID = row.getID();
    }

    value.setNetwork(this);

    fireChanged();
    return value;
  }

  public void remove(FeatureListRow key) {
    nodes.removeIf(node -> {
      if (node.row().equals(key)) {
        node.ion().setNetwork(null);
        if (node.row().getID() <= lowestID) {
          recalcMinID();
          fireChanged();
        }
        return true;
      }
      return false;
    });
  }

  /**
   * Finds the minimum row id
   */
  public int recalcMinID() {
    lowestID = streamRows().mapToInt(FeatureListRow::getID).min().orElse(-1);
    return lowestID;
  }

  private @NotNull Stream<FeatureListRow> streamRows() {
    return nodes.stream().map(IonNetworkNode::row);
  }

  public void clear() {
    nodes.clear();
    lowestID = -1;
    fireChanged();
  }

  public void fireChanged() {
    resetNeutralMass();
    resetMaxDev();
  }

  public void resetNeutralMass() {
    neutralMass = null;
  }

  /**
   * Maximum absolute deviation from central neutral mass
   */
  public void resetMaxDev() {
    maxDev = null;
  }

  /**
   * Calculates and sets the neutral mass average and average rt
   *
   * @return
   */
  public double calcNeutralMass() {
    neutralMass = null;
    if (size() == 0) {
      return 0;
    }

    double mass = 0;
    avgRT = 0;
    heightSum = 0;
    for (var e : nodes) {
      mass += e.ion().getIonType().getMass(e.row().getAverageMZ());
      avgRT += e.row().getAverageRT();
      // sum of heighest peaks heights
      Float height = e.row().getMaxHeight();
      heightSum += height == null || Float.isNaN(height) ? 1 : height;
    }
    avgRT = avgRT / size();
    neutralMass = mass / size();
    return neutralMass;
  }

  public double getAvgRT() {
    if (neutralMass == null) {
      calcNeutralMass();
    }
    return avgRT;
  }

  public double getHeightSum() {
    if (neutralMass == null) {
      calcNeutralMass();
    }
    return heightSum;
  }

  /**
   * calculates the maximum deviation from the average mass
   *
   * @return
   */
  public double calcMaxDev() {
    maxDev = null;
    if (size() == 0) {
      return 0;
    }

    neutralMass = getNeutralMass();
    if (neutralMass == null || neutralMass == 0) {
      return 0;
    }

    double max = 0;
    for (var e : nodes) {
      double mass = getMass(e);
      max = Math.max(Math.abs(neutralMass - mass), max);
    }
    maxDev = max;
    return maxDev;
  }

  /**
   * Neutral mass of entry
   *
   * @param e
   * @return
   */
  public double getMass(IonNetworkNode e) {
    return e.ion().getIonType().getMass(e.row().getAverageMZ());
  }

  public double getMaxDev() {
    return maxDev == null ? calcMaxDev() : maxDev;
  }

  /**
   * All rows point to the same neutral mass
   *
   * @param mzTol
   * @return
   */
  public boolean checkAllWithinMZTol(MZTolerance mzTol) {
    double neutralMass = getNeutralMass();
    double maxDev = getMaxDev();
    return mzTol.checkWithinTolerance(neutralMass, neutralMass + maxDev);
  }

  public int[] getAllIDs() {
    return streamRows().mapToInt(FeatureListRow::getID).toArray();
  }

  public void setNetworkToAllRows() {
    streamIons().forEach(id -> id.setNetwork(this));
  }

  public void delete() {
    // deregister from the feature list registry before clearing nodes (clear removes the only
    // way to find the owning feature list)
    final io.github.mzmine.datamodel.features.ModularFeatureList flist = findFeatureList();
    for (IonNetworkNode node : nodes) {
      node.row().removeIonIdentity(node.ion());
    }
    if (flist != null) {
      flist.removeIonNetwork(this);
    }
    clear();
  }

  /**
   * Best-effort lookup of the owning feature list via any node's row. Returns {@code null} when the
   * network is already empty.
   */
  private @Nullable io.github.mzmine.datamodel.features.ModularFeatureList findFeatureList() {
    for (IonNetworkNode node : nodes) {
      if (node.row()
          .getFeatureList() instanceof io.github.mzmine.datamodel.features.ModularFeatureList m) {
        return m;
      }
    }
    return null;
  }

  /**
   * row has smallest id?
   *
   * @param row
   * @return
   */
  public boolean hasSmallestID(FeatureListRow row) {
    return row.getID() == lowestID;
  }


  @Override
  public int compareTo(IonNetwork net) {
    // -1 if this is better
    return Integer.compare(net.size(), this.size());
  }

  public void addAll(@NotNull IonNetwork other) {
    // avoid duplicates
    nodes.removeAll(other.getNodes());
    nodes.addAll(other.getNodes());

    for (IonNetworkNode node : other.getNodes()) {
      node.ion().setNetwork(this);
    }

    if (other.lowestID < lowestID || lowestID == -1) {
      lowestID = other.lowestID;
    }

    fireChanged();
  }

  public void forEach(BiConsumer<FeatureListRow, IonIdentity> consumer) {
    for (IonNetworkNode node : nodes) {
      consumer.accept(node.row(), node.ion());
    }
  }

  /**
   * TODO rename to contains row
   *
   */
  public boolean containsKey(FeatureListRow row) {
    for (IonNetworkNode node : nodes) {
      if (node.row().equals(row)) {
        return true;
      }
    }
    return false;
  }

  public IonIdentity get(@Nullable FeatureListRow row) {
    return getIonForRow(row);
  }

  /**
   * Look up the {@link IonIdentity} attached to {@code row} within this network. Linear scan over
   * {@link #nodes}; networks are tiny so this is cheap. Used by persistence to resolve a row's
   * IonIdentity instance from the shared network after a list of network-id references is loaded.
   *
   * @return the ion identity, or {@code null} if {@code row} is not a member of this network.
   */
  public @Nullable IonIdentity getIonForRow(@Nullable FeatureListRow row) {
    if (row == null) {
      return null;
    }
    for (IonNetworkNode node : nodes) {
      if (row.equals(node.row())) {
        return node.ion();
      }
    }
    return null;
  }

  // ---------------------------------------------------------------------------
  // Persistence
  // ---------------------------------------------------------------------------

  /**
   * Write this network as a {@code <ionnetwork>} element, including its formulas, nodes (row id +
   * adduct), and any relations for which this network has the lowest id (so each relation lands in
   * the XML exactly once even though the in-memory map is symmetric).
   */
  public void saveToXML(@NotNull XMLStreamWriter writer) throws XMLStreamException {
    writer.writeStartElement(CONST.XML_ION_NETWORK_ELEMENT);
    writer.writeAttribute(CONST.XML_ION_NETWORK_ID_ATTR, String.valueOf(id));
    // ensure derived values are populated before writing
    if (neutralMass == null) {
      calcNeutralMass();
    }
    if (neutralMass != null) {
      writer.writeAttribute(CONST.XML_ION_NETWORK_NEUTRAL_MASS_ATTR,
          ParsingUtils.numberToString(neutralMass));
    }
    writer.writeAttribute(CONST.XML_ION_NETWORK_AVG_RT_ATTR, ParsingUtils.numberToString(avgRT));
    writer.writeAttribute(CONST.XML_ION_NETWORK_HEIGHT_SUM_ATTR,
        ParsingUtils.numberToString(heightSum));

    // molFormulas — reuse ResultFormula's own XML
    writer.writeStartElement(CONST.XML_ION_NETWORK_FORMULAS_ELEMENT);
    for (ResultFormula formula : molFormulas) {
      formula.saveToXML(writer);
    }
    writer.writeEndElement();

    // nodes: each pair (row, ion adduct)
    writer.writeStartElement(CONST.XML_ION_NETWORK_NODES_ELEMENT);
    for (IonNetworkNode node : nodes) {
      writer.writeStartElement(CONST.XML_ION_NETWORK_NODE_ELEMENT);
      writer.writeAttribute(CONST.XML_ROW_ID_ATTR, String.valueOf(node.row().getID()));
      // IonType has its own XML helpers
      node.ion().getIonType().saveToXML(writer);
      writer.writeEndElement();
    }
    writer.writeEndElement();

    // relations: write only when this network has the lowest id so each relation goes once
    if (relations != null && !relations.isEmpty()) {
      writer.writeStartElement(CONST.XML_ION_NETWORK_RELATIONS_ELEMENT);
      for (IonNetworkRelation relation : relations.values()) {
        if (relation == null) {
          continue;
        }
        if (relation.isLowestIDNetwork(this)) {
          IonNetworkRelation.saveRelation(writer, relation);
        }
      }
      writer.writeEndElement();
    }

    writer.writeEndElement();
  }

  /**
   * Construct an empty network and populate its node list and formulas from XML. Relations are
   * deferred: pass 1 just creates the network skeletons; a second pass walks the relations
   * sub-elements once all networks for this flist are registered.
   * <p>
   * Reader must be positioned on a {@code <ionnetwork>} start element. On return the reader is
   * positioned on the matching end element.
   */
  public static @Nullable IonNetwork loadFromXML(@NotNull XMLStreamReader reader,
      @NotNull ModularFeatureList flist) throws XMLStreamException {
    if (!(reader.isStartElement() && CONST.XML_ION_NETWORK_ELEMENT.equals(reader.getLocalName()))) {
      throw new IllegalStateException(
          "Expected <ionnetwork> start element but got " + reader.getLocalName());
    }
    final int netId = Integer.parseInt(
        reader.getAttributeValue(null, CONST.XML_ION_NETWORK_ID_ATTR));
    final IonNetwork network = new IonNetwork(netId);

    // attributes — preset so neutralMass etc. are restored before any reader touches them
    final String mass = reader.getAttributeValue(null, CONST.XML_ION_NETWORK_NEUTRAL_MASS_ATTR);
    if (mass != null && !mass.equals(CONST.XML_NULL_VALUE)) {
      network.neutralMass = ParsingUtils.stringToDouble(mass);
    }
    final String avgRtStr = reader.getAttributeValue(null, CONST.XML_ION_NETWORK_AVG_RT_ATTR);
    if (avgRtStr != null) {
      final Double v = ParsingUtils.stringToDouble(avgRtStr);
      if (v != null) {
        network.avgRT = v;
      }
    }
    final String heightStr = reader.getAttributeValue(null, CONST.XML_ION_NETWORK_HEIGHT_SUM_ATTR);
    if (heightStr != null) {
      final Double v = ParsingUtils.stringToDouble(heightStr);
      if (v != null) {
        network.heightSum = v;
      }
    }

    while (reader.hasNext()) {
      final int event = reader.next();
      if (event == XMLEvent.END_ELEMENT && CONST.XML_ION_NETWORK_ELEMENT.equals(
          reader.getLocalName())) {
        break;
      }
      if (event != XMLEvent.START_ELEMENT) {
        continue;
      }
      switch (reader.getLocalName()) {
        case "molformulas" -> readMolFormulas(reader, network);
        case "nodes" -> readNodes(reader, network, flist);
        case "relations" -> skipUntilEnd(reader, CONST.XML_ION_NETWORK_RELATIONS_ELEMENT);
        default -> {
          // ignore unknown elements forward-compat
        }
      }
    }

    // refresh lowestID after rebuilding nodes
    network.recalcMinID();
    return network;
  }

  /**
   * Advance the reader past the matching end element for the named tag.
   */
  private static void skipUntilEnd(@NotNull XMLStreamReader reader, @NotNull String elementName)
      throws XMLStreamException {
    int depth = 1;
    while (reader.hasNext() && depth > 0) {
      final int event = reader.next();
      if (event == XMLEvent.START_ELEMENT && elementName.equals(reader.getLocalName())) {
        depth++;
      } else if (event == XMLEvent.END_ELEMENT && elementName.equals(reader.getLocalName())) {
        depth--;
      }
    }
  }

  private static void readMolFormulas(@NotNull XMLStreamReader reader, @NotNull IonNetwork network)
      throws XMLStreamException {
    while (reader.hasNext()) {
      final int event = reader.next();
      if (event == XMLEvent.END_ELEMENT && CONST.XML_ION_NETWORK_FORMULAS_ELEMENT.equals(
          reader.getLocalName())) {
        return;
      }
      if (event == XMLEvent.START_ELEMENT && ResultFormula.XML_ELEMENT.equals(
          reader.getLocalName())) {
        network.molFormulas.add(ResultFormula.loadFromXML(reader));
      }
    }
  }

  private static void readNodes(@NotNull XMLStreamReader reader, @NotNull IonNetwork network,
      @NotNull ModularFeatureList flist) throws XMLStreamException {
    while (reader.hasNext()) {
      final int event = reader.next();
      if (event == XMLEvent.END_ELEMENT && CONST.XML_ION_NETWORK_NODES_ELEMENT.equals(
          reader.getLocalName())) {
        return;
      }
      if (event == XMLEvent.START_ELEMENT && CONST.XML_ION_NETWORK_NODE_ELEMENT.equals(
          reader.getLocalName())) {
        final int rowId = Integer.parseInt(reader.getAttributeValue(null, CONST.XML_ROW_ID_ATTR));
        // IonType is nested directly inside <node>
        IonType ionType = null;
        while (reader.hasNext()) {
          final int inner = reader.next();
          if (inner == XMLEvent.END_ELEMENT && CONST.XML_ION_NETWORK_NODE_ELEMENT.equals(
              reader.getLocalName())) {
            break;
          }
          if (inner == XMLEvent.START_ELEMENT && IonType.XML_ELEMENT.equals(
              reader.getLocalName())) {
            ionType = IonType.loadFromXML(reader);
          }
        }
        final FeatureListRow row = flist.findRowByID(rowId);
        if (row == null) {
          // row id unknown — registry was loaded before rows existed; this should not happen
          continue;
        }
        if (ionType == null) {
          continue;
        }
        final IonIdentity ion = new IonIdentity(ionType);
        // attach to network (also sets ion.network) and to the row's IonIdentity list. The list
        // mirrors what IonIdentityListType.loadFromXML will produce later; if the type re-runs
        // load, it'll resolve the same instances back via network.getIonForRow().
        network.put(row, ion);
        row.addIonIdentity(ion);
      }
    }
  }

  /**
   * Second pass: parse {@code <relations>} children and wire them onto every involved network.
   * Reader must be positioned on the {@code <ionnetwork>} start element of this network's saved
   * XML; the method scans for the {@code <relations>} sub-element and attaches each parsed relation
   * symmetrically.
   */
  public static void loadRelationsFromXML(@NotNull XMLStreamReader reader,
      @NotNull ModularFeatureList flist) throws XMLStreamException {
    if (!(reader.isStartElement() && CONST.XML_ION_NETWORK_ELEMENT.equals(reader.getLocalName()))) {
      return;
    }
    while (reader.hasNext()) {
      final int event = reader.next();
      if (event == XMLEvent.END_ELEMENT && CONST.XML_ION_NETWORK_ELEMENT.equals(
          reader.getLocalName())) {
        return;
      }
      if (event == XMLEvent.START_ELEMENT && CONST.XML_ION_NETWORK_RELATIONS_ELEMENT.equals(
          reader.getLocalName())) {
        readRelationList(reader, flist);
      }
    }
  }

  private static void readRelationList(@NotNull XMLStreamReader reader,
      @NotNull ModularFeatureList flist) throws XMLStreamException {
    while (reader.hasNext()) {
      final int event = reader.next();
      if (event == XMLEvent.END_ELEMENT && CONST.XML_ION_NETWORK_RELATIONS_ELEMENT.equals(
          reader.getLocalName())) {
        return;
      }
      if (event == XMLEvent.START_ELEMENT && CONST.XML_ION_NETWORK_RELATION_ELEMENT.equals(
          reader.getLocalName())) {
        final IonNetworkRelation relation = IonNetworkRelation.loadRelation(reader, flist);
        if (relation == null) {
          continue;
        }
        // attach symmetrically to every involved network so the in-memory invariant is preserved
        final IonNetwork[] involved = relation.getAllNetworks();
        for (int i = 0; i < involved.length; i++) {
          final IonNetwork self = involved[i];
          for (int j = 0; j < involved.length; j++) {
            if (i == j) {
              continue;
            }
            self.addRelation(involved[j], relation);
          }
        }
      }
    }
  }
}
