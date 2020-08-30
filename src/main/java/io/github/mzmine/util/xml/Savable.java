package io.github.mzmine.util.xml;

import org.w3c.dom.Element;

/**
 * Can be saved to an xml.
 */
@FunctionalInterface
public interface Savable {

  /**
   * Saves the implementing classes values to an xml file. Pass the element to save the data into as
   * an argument. Creation of a new element inside the method is not required. The new element
   * should be created by the calling method.
   *
   * @param thisElement the element to store the data <b>into</b>.
   */
  void saveValueToXML(Element thisElement);
}
