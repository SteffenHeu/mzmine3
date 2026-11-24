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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.github.mzmine.datamodel.features.types.numbers.abstr.ListAsJsonDataType;
import io.github.mzmine.util.io.JsonUtils;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasBlockEvidence;
import java.util.List;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stores the building blocks and their evidence in a PFAS annotation.
 */
public class PfasBuildingBlocksType extends ListAsJsonDataType<PfasBlockEvidence> {

  private static final TypeReference<List<PfasBlockEvidence>> BLOCK_LIST = new TypeReference<>() {
  };

  @Override
  public @NotNull String getUniqueID() {
    return "pfas_building_blocks";
  }

  @Override
  public @NotNull String getHeaderString() {
    return "PFAS building blocks";
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
  public @Nullable List<PfasBlockEvidence> loadFromXML(final @NotNull XMLStreamReader reader)
      throws XMLStreamException {
    final String text = reader.getElementText();
    if (text.isBlank()) {
      return null;
    }
    try {
      return List.copyOf(JsonUtils.MAPPER.readValue(text, BLOCK_LIST));
    } catch (JsonProcessingException e) {
      throw new XMLStreamException("Cannot read PFAS building blocks", e);
    }
  }
}
