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

package io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.jetbrains.annotations.NotNull;
import org.openscience.cdk.interfaces.IMolecularFormula;

public interface ILipidAnnotation {

  ILipidClass getLipidClass();

  String getAnnotation();

  LipidAnnotationLevel getLipidAnnotationLevel();

  IMolecularFormula getMolecularFormula();

  // total carbons/DBEs/oxygens at the species level (sum of chains for molecular species)
  int getSpeciesLevelCarbons();

  int getSpeciesLevelDBEs();

  int getSpeciesLevelOxygens();

  default @NotNull String getSpeciesLevelKey() {
    return getLipidClass().getName() + "|" + getSpeciesLevelCarbons() + ":" + getSpeciesLevelDBEs();
  }

  void saveToXML(XMLStreamWriter writer) throws XMLStreamException;

}
