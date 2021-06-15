/*
 * Copyright 2006-2020 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA
 */

package io.github.mzmine.datamodel.identities;

import io.github.mzmine.modules.dataprocessing.id_formulaprediction.ResultFormula;
import io.github.mzmine.modules.dataprocessing.id_formulaprediction.restrictions.rdbe.RDBERestrictionChecker;
import io.github.mzmine.util.FormulaUtils;
import javax.annotation.Nonnull;
import org.openscience.cdk.interfaces.IMolecularFormula;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;

public class MolecularFormulaIdentity {

  @Nonnull
  protected final IMolecularFormula cdkFormula;
  protected Double rdbe;
  protected double searchedNeutralMass;


  public MolecularFormulaIdentity(IMolecularFormula cdkFormula, double searchedNeutralMass) {
    this.cdkFormula = cdkFormula;
    rdbe = RDBERestrictionChecker.calculateRDBE(cdkFormula);
    this.searchedNeutralMass = searchedNeutralMass;
  }

  public MolecularFormulaIdentity(String formula, double searchedNeutralMass) {
    this(FormulaUtils.createMajorIsotopeMolFormula(formula), searchedNeutralMass);
  }

  public String getFormulaAsString() {
    return MolecularFormulaManipulator.getString(cdkFormula);
  }

  public String getFormulaAsHTML() {
    return MolecularFormulaManipulator.getHTML(cdkFormula);
  }

  public IMolecularFormula getFormulaAsObject() {
    return cdkFormula;
  }

  public double getExactMass() {
    return MolecularFormulaManipulator.getTotalExactMass(cdkFormula);
  }

  public double getPpmDiff(double neutralMass) {
    double exact = getExactMass();
    return (neutralMass - exact) / exact * 1E6;
  }

  @Override
  public String toString() {
    return getFormulaAsString();
  }

  /**
   * The initially searched neutral mass
   *
   * @return the neutral mass
   */
  public double getSearchedNeutralMass() {
    return searchedNeutralMass;
  }

  /**
   * Merged score
   *
   * @param ppmMax
   * @return
   */
  public double getScore(double ppmMax) {
    return getScore(ppmMax, 1, 1);
  }

  /**
   * Merged score with weights. Uses the initial set neutral mass
   *
   * @param ppmMax        weight for ppm distance
   * @param fIsotopeScore
   * @param fMSMSscore
   * @return
   */
  public double getScore(double ppmMax, double fIsotopeScore, double fMSMSscore) {
    return this.getScore(searchedNeutralMass, ppmMax, fIsotopeScore, fMSMSscore);
  }

  /**
   * Merged score with weights. {@link ResultFormula}
   *
   * @param neutralMass   overrides the initally set (searched) neutral mass
   * @param ppmMax        weight for ppm distance
   * @param fIsotopeScore
   * @param fMSMSscore
   * @return
   */
  public double getScore(double neutralMass, double ppmMax, double fIsotopeScore,
      double fMSMSscore) {
    return getPPMScore(neutralMass, ppmMax);
  }

  /**
   * Score for ppm distance
   *
   * @param ppmMax
   * @return
   */
  public double getPPMScore(double neutralMass, double ppmMax) {
    if (ppmMax <= 0) {
      ppmMax = 50;
    }
    return (ppmMax - Math.abs(getPpmDiff(neutralMass))) / ppmMax;
  }

  /**
   * Only checks molecular formula as with toString method
   *
   * @param f
   * @return
   */
  public boolean equalFormula(MolecularFormulaIdentity f) {
    return this.toString().equals(f.toString());
  }

  public Double getRDBE() {
    return rdbe;
  }

}
