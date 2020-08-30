package io.github.mzmine.util.xml;

import org.w3c.dom.Element;

/**
 * Can be loaded from an xml.
 */
@FunctionalInterface
public interface Loadable {

  /**
   * Loads value from an xml element.
   *
   * @param xmlElement The xml element to load the values from.
   */
  void loadValueFromXML(Element xmlElement);
}
