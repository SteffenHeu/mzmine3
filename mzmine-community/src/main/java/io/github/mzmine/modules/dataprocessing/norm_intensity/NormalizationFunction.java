/*
 * Copyright (c) 2004-2026 The mzmine Development Team
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
import io.github.mzmine.datamodel.utils.UniqueIdSupplier;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilePlaceholder;
import io.github.mzmine.util.XMLUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Function that returns a normalization factor for specific feature coordinates.
 */
public sealed interface NormalizationFunction extends UniqueIdSupplier permits
    CompositeListNormalizationFunction, FactorNormalizationFunction,
    InterpolatedNormalizationFunction, StandardCompoundNormalizationFunction {

  String XML_FUNCTION_ELEMENT = "normalizationFunction";
  String XML_FUNCTION_TYPE_ATTR = "type";
  String XML_ACQUISITION_TIMESTAMP_ATTR = "acquisitionTimestamp";

  /**
   * Merges the old and new function. If both are constant factors then the factors are merged into
   * a single function. If the functions are more complex like feature specific functions, then a
   * {@link CompositeListNormalizationFunction} is returned.
   *
   * @param old      old function
   * @param function new function
   * @return a new function instance either merged or composite
   */
  @NotNull
  static NormalizationFunction merge(@NotNull NormalizationFunction old,
      @NotNull NormalizationFunction function) {
    if (old instanceof FactorNormalizationFunction(
        var rawFile, var acquisitionTimestamp, double factor
    ) && function instanceof FactorNormalizationFunction newFactor) {
      return new FactorNormalizationFunction(rawFile, acquisitionTimestamp,
          factor * newFactor.factor());
    }

    final List<NormalizationFunction> subfunctions = new ArrayList<>();
    if (old instanceof CompositeListNormalizationFunction(var functions)) {
      subfunctions.addAll(functions);
    } else {
      subfunctions.add(old);
    }

    if (function instanceof CompositeListNormalizationFunction(var functions)) {
      subfunctions.addAll(functions);
    } else {
      subfunctions.add(function);
    }

    return new CompositeListNormalizationFunction(List.copyOf(subfunctions));
  }

  /**
   * @return true if this function is feature specific meaning that RT and or m/z affect
   * normalization factor. Otherwise, any call to {@link #getNormalizationFactor(Double, Float)}
   */
  default boolean isFeatureSpecific() {
    return !isConstantFactor();
  }

  /**
   * @return opposite of {@link #isFeatureSpecific()}. true if this is a constant factor and not
   * feature specific
   */
  default boolean isConstantFactor() {
    return this instanceof FactorNormalizationFunction;
  }

  @NotNull RawDataFilePlaceholder rawDataFilePlaceholder();

  @Nullable LocalDateTime acquisitionTimestamp();

  /**
   * @param mz the mz of the feature
   * @param rt the rt of the feature
   * @return A factor to multiply area/height with to get normalized values.
   */
  double getNormalizationFactor(@NotNull Double mz, @NotNull Float rt);

  void saveToXML(@NotNull Element functionElement);

  /**
   *
   * @return The data file this normalization applies to. may be null if the file was removed from
   * the project.
   */
  default @Nullable RawDataFile getRawDataFile() {
    return rawDataFilePlaceholder().getMatchingFile();
  }

  static void appendFunctionElement(final @NotNull Element parentElement,
      final @NotNull NormalizationFunction normalizationFunction) {
    final Document document = parentElement.getOwnerDocument();
    final Element functionElement = document.createElement(XML_FUNCTION_ELEMENT);
    normalizationFunction.saveToXML(functionElement);
    parentElement.appendChild(functionElement);
  }

  static @NotNull NormalizationFunction loadFromXML(final @NotNull Element functionElement) {
    final String functionType = XMLUtils.requireAttribute(functionElement, XML_FUNCTION_TYPE_ATTR);
    return switch (functionType) {
      case FactorNormalizationFunction.XML_TYPE ->
          FactorNormalizationFunction.loadFromXML(functionElement);
      case StandardCompoundNormalizationFunction.XML_TYPE ->
          StandardCompoundNormalizationFunction.loadFromXML(functionElement);
      case InterpolatedNormalizationFunction.XML_TYPE ->
          InterpolatedNormalizationFunction.loadFromXML(functionElement);
      default -> throw new IllegalArgumentException(
          "Unsupported normalization function type: " + functionType);
    };
  }

  static void saveAcquisitionTimestamp(final @NotNull Element functionElement,
      final @Nullable LocalDateTime acquisitionTimestamp) {
    if (acquisitionTimestamp != null) {
      functionElement.setAttribute(XML_ACQUISITION_TIMESTAMP_ATTR, acquisitionTimestamp.toString());
    }
  }

  static @Nullable LocalDateTime loadAcquisitionTimestamp(final @NotNull Element functionElement) {
    if (!functionElement.hasAttribute(XML_ACQUISITION_TIMESTAMP_ATTR)) {
      return null;
    }

    final String acquisitionTimestamp = functionElement.getAttribute(
        XML_ACQUISITION_TIMESTAMP_ATTR);
    if (acquisitionTimestamp.isBlank()) {
      return null;
    }

    return LocalDateTime.parse(acquisitionTimestamp);
  }

  /**
   * @return Create a copy of the function that points to the new file
   */
  @NotNull NormalizationFunction withRawFile(@NotNull RawDataFile file);
}
