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

package io.github.mzmine.modules.dataprocessing.norm_intensity;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilePlaceholder;
import io.github.mzmine.util.XMLUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @param functions a list of all functions to be applied. Each factor is multiplied to get the
 *                  total factor.
 */
public record CompositeNormalizationFunction(
    @NotNull List<@NotNull NormalizationFunction> functions) implements NormalizationFunction {

  public static final String XML_TYPE = "composite_list_normalization";

  public CompositeNormalizationFunction {
    if (functions.stream().map(NormalizationFunction::rawDataFilePlaceholder).distinct().count()
        > 1) {
      throw new IllegalArgumentException(
          "CompositeListNormalizationFunction requires sub functions for the same raw data files");
    }
  }

  @Override
  public @NotNull RawDataFilePlaceholder rawDataFilePlaceholder() {
    return functions.getFirst().rawDataFilePlaceholder();
  }

  @Override
  public @Nullable LocalDateTime acquisitionTimestamp() {
    return functions.getFirst().acquisitionTimestamp();
  }

  @Override
  public double getNormalizationFactor(@NotNull Double mz, @NotNull Float rt) {
    double f = 1;
    for (NormalizationFunction function : functions) {
      f *= function.getNormalizationFactor(mz, rt);
    }
    return f;
  }

  @Override
  public @NotNull String getUniqueID() {
    return XML_TYPE;
  }


  @Override
  public void saveToXML(final @NotNull Element functionElement) {
    functionElement.setAttribute(XML_FUNCTION_TYPE_ATTR, getUniqueID());

    final Element subFunctions = functionElement.getOwnerDocument().createElement("subfunctions");

    for (NormalizationFunction function : functions) {
      NormalizationFunction.appendFunctionElement(subFunctions, function);
    }

    functionElement.appendChild(subFunctions);
  }

  public static @NotNull CompositeNormalizationFunction loadFromXML(
      final @NotNull Element functionElement) {

    final ArrayList<NormalizationFunction> functions = new ArrayList<>();

    final Element subfunctions = XMLUtils.findChildElement(functionElement, "subfunctions");

    final NodeList matchingNodes = subfunctions.getElementsByTagName(XML_FUNCTION_ELEMENT);
    for (int i = 0; i < matchingNodes.getLength(); i++) {
      final Node node = matchingNodes.item(i);
      if (node instanceof final Element subFunElement) {
        final NormalizationFunction subfun = NormalizationFunction.loadFromXML(subFunElement);
        functions.add(subfun);
      }
    }

    return new CompositeNormalizationFunction(List.copyOf(functions));
  }

  @Override
  public @NotNull NormalizationFunction withRawFile(@NotNull RawDataFile file) {
    return new CompositeNormalizationFunction(
        functions.stream().map(f -> f.withRawFile(file)).toList());
  }
}
