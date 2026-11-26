package io.github.mzmine.datamodel.features.types.annotations.pfas;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasCitation;
import io.github.mzmine.datamodel.features.types.numbers.abstr.ListAsJsonDataType;
import io.github.mzmine.util.io.JsonUtils;
import java.util.List;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Literature sources of a PFAS annotation: for the candidate and for the matched fragmentation
 * rules.
 */
public class PfasCitationsType extends ListAsJsonDataType<PfasCitation> {

  private static final TypeReference<List<PfasCitation>> CITATION_LIST = new TypeReference<>() {
  };

  @Override
  public @NotNull String getUniqueID() {
    // Never change the ID for compatibility during saving/loading of type
    return "pfas_citations";
  }

  @Override
  public @NotNull String getHeaderString() {
    return "PFAS literature";
  }

  @Override
  public boolean getDefaultVisibility() {
    return false;
  }

  @Override
  public void saveToXML(final @NotNull XMLStreamWriter writer, final @Nullable Object value)
      throws XMLStreamException {
    if (value != null) {
      writer.writeCharacters(JsonUtils.writeStringOrThrow(value));
    }
  }

  @Override
  public @Nullable List<PfasCitation> loadFromXML(final @NotNull XMLStreamReader reader)
      throws XMLStreamException {
    final String text = reader.getElementText();
    if (text.isBlank()) {
      return null;
    }
    try {
      return List.copyOf(JsonUtils.MAPPER.readValue(text, CITATION_LIST));
    } catch (JsonProcessingException e) {
      throw new XMLStreamException("Cannot read PFAS citations", e);
    }
  }
}
