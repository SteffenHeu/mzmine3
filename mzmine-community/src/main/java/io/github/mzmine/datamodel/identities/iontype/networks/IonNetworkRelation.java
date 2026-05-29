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

package io.github.mzmine.datamodel.identities.iontype.networks;


import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.identities.iontype.IonNetwork;
import io.github.mzmine.modules.io.projectload.version_3_0.CONST;
import java.util.Arrays;
import java.util.logging.Logger;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Relationships between {@link IonNetwork}s
 */
public interface IonNetworkRelation {

  /**
   * Name of the relationship to the argument network
   *
   * @param net the related network
   * @return relationship name to net, or null if there is no relationship
   */
  @Nullable
  String getName(IonNetwork net);

  /**
   * A general relationship description
   *
   * @return description
   */
  @NotNull
  String getDescription();

  /**
   * All IonNetworks in this relationship. A relationship might be between two IonNetworks ({@link
   * IonNetworkModificationRelation}) or between multiple (e.g., {@link IonNetworkHeteroCondensedRelation})
   *
   * @return an array of related networks
   */
  @NotNull
  IonNetwork[] getAllNetworks();

  /**
   * A method to check if net is the network with the lowest ID. Useful to only apply methods once,
   * e.g., exporting the relationship to text
   *
   * @param net the tested network (should be one of {@link #getAllNetworks()}
   * @return true if getID() is the lowest
   */
  default boolean isLowestIDNetwork(IonNetwork net) {
    return Arrays.stream(getAllNetworks()).noneMatch(n -> n.getID() < net.getID());
  }

  Logger LOGGER = Logger.getLogger(IonNetworkRelation.class.getName());

  /**
   * Type discriminators used in saved XML to pick the right subclass at load time.
   */
  String TYPE_MODIFICATION = "modification";
  String TYPE_CONDENSED = "condensed";
  String TYPE_HETERO_CONDENSED = "hetero_condensed";

  /**
   * Subclass-specific XML payload. The wrapping {@code <relation type="..."/>} element and the
   * shared list of network ids are written by {@link #saveRelation}; implementations only emit
   * their own attributes/sub-elements.
   */
  void saveOwnXML(@NotNull XMLStreamWriter writer) throws XMLStreamException;

  /**
   * @return the discriminator constant for this relation kind (see {@code TYPE_*}).
   */
  @NotNull String getRelationTypeId();

  /**
   * Persist a relation once, scoped to the network with the lowest id among
   * {@link #getAllNetworks()}. Call exactly once per relation by iterating the network's relations
   * and skipping when {@link #isLowestIDNetwork(IonNetwork)} returns false.
   * <p>
   * Format:
   * {@code <relation reltype="..." networkIds="1,2[,3]"> ...subclass payload... </relation>}.
   */
  static void saveRelation(@NotNull XMLStreamWriter writer, @NotNull IonNetworkRelation relation)
      throws XMLStreamException {
    writer.writeStartElement(CONST.XML_ION_NETWORK_RELATION_ELEMENT);
    writer.writeAttribute(CONST.XML_RELATION_TYPE_ATTR, relation.getRelationTypeId());
    final IonNetwork[] nets = relation.getAllNetworks();
    final StringBuilder ids = new StringBuilder();
    for (int i = 0; i < nets.length; i++) {
      if (i > 0) {
        ids.append(',');
      }
      ids.append(nets[i].getID());
    }
    writer.writeAttribute("networkIds", ids.toString());
    relation.saveOwnXML(writer);
    writer.writeEndElement();
  }

  /**
   * Reconstruct a relation. Reader is positioned on the {@code <relation>} start element. All
   * referenced networks must already exist in {@code flist}'s registry. The returned relation is
   * attached symmetrically to every involved network by the caller via
   * {@link IonNetwork#addRelation}.
   *
   * @return parsed relation, or {@code null} if it could not be reconstructed (e.g., unknown type
   * id or missing network).
   */
  static @Nullable IonNetworkRelation loadRelation(@NotNull XMLStreamReader reader,
      @NotNull ModularFeatureList flist) throws XMLStreamException {
    final String type = reader.getAttributeValue(null, CONST.XML_RELATION_TYPE_ATTR);
    final String idsAttr = reader.getAttributeValue(null, "networkIds");
    if (type == null || idsAttr == null) {
      LOGGER.warning(() -> "Ion network relation missing type or networkIds attribute — skipping.");
      return null;
    }
    final String[] idParts = idsAttr.split(",");
    final IonNetwork[] nets = new IonNetwork[idParts.length];
    for (int i = 0; i < idParts.length; i++) {
      final int id = Integer.parseInt(idParts[i].trim());
      nets[i] = flist.getIonNetwork(id);
      if (nets[i] == null) {
        LOGGER.warning(() -> "Ion network relation references unknown network id " + id);
        return null;
      }
    }
    return switch (type) {
      case TYPE_MODIFICATION -> IonNetworkModificationRelation.loadFromXML(reader, nets);
      case TYPE_CONDENSED -> IonNetworkCondensedRelation.loadFromXML(reader, nets);
      case TYPE_HETERO_CONDENSED -> IonNetworkHeteroCondensedRelation.loadFromXML(reader, nets);
      default -> {
        LOGGER.warning(() -> "Unknown ion network relation type: " + type);
        yield null;
      }
    };
  }
}
