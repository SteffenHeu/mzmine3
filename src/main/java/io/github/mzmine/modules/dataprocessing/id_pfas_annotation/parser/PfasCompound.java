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
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */

package io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.interfaces.IMolecularFormula;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;

public class PfasCompound {

  private final IMolecularFormula formula;
  private final BuildingBlock backbone;
  private final BuildingBlock backboneLinker;
  private final BuildingBlock functionalGroup;
  private final Collection<BuildingBlock> substituent;

  private final int n, m, k;

  protected PfasCompound(BuildingBlock backbone, BuildingBlock backboneLinker,
      BuildingBlock functionalGroup, Collection<BuildingBlock> substituent, int n, int m, int k)
      throws InvalidCompoundConfigurationException {
    this.backbone = backbone;
    this.backboneLinker = backboneLinker;
    this.functionalGroup = functionalGroup;
    this.substituent = substituent;
    this.n = n;
    this.m = m;
    this.k = k;

    final List<BuildingBlock> blocks = new ArrayList<>();
    blocks.add(backbone);
    blocks.add(backboneLinker);
    blocks.add(functionalGroup);
    blocks.addAll(substituent);

    if (!evaluateGeneralRequirements(blocks)) {
      throw new InvalidCompoundConfigurationException();
    }

    final IChemObjectBuilder inst = SilentChemObjectBuilder.getInstance();

    final IMolecularFormula backboneFormula = MolecularFormulaManipulator
        .getMajorIsotopeMolecularFormula(getBackboneFormula(backbone), inst);

    final IMolecularFormula functionalGroupFormula = MolecularFormulaManipulator
        .getMajorIsotopeMolecularFormula(getFgFormula(functionalGroup), inst);
    backboneFormula.add(functionalGroupFormula);

    for (BuildingBlock subs : substituent) {
      final IMolecularFormula substituentFormula = MolecularFormulaManipulator
          .getMajorIsotopeMolecularFormula(getSubstituentFormula(subs), inst);
      backboneFormula.add(substituentFormula);
    }

    if (backboneLinker != null) {
      final IMolecularFormula backboneLinkerFormula = MolecularFormulaManipulator
          .getMajorIsotopeMolecularFormula(getBackboneLinkerFormula(backboneLinker), inst);
      backboneFormula.add(backboneLinkerFormula);
    }

    formula = backboneFormula;
  }

  public IMolecularFormula getFormula() {
    return formula;
  }

  public double getExactMass() {
//    return FormulaUtils.
    return 0d;
  }

  public BuildingBlock getBackbone() {
    return backbone;
  }

  public BuildingBlock getBackboneLinker() {
    return backboneLinker;
  }

  public BuildingBlock getFunctionalGroup() {
    return functionalGroup;
  }

  public Collection<BuildingBlock> getSubstituent() {
    return substituent;
  }

  public int getN() {
    return n;
  }

  public int getM() {
    return m;
  }

  public int getK() {
    return k;
  }

  private boolean evaluateRequirement(Collection<BuildingBlock> blocks, String req) {
    for (BuildingBlock b : blocks) {
      if (b == null) {
        continue;
      }
      if (b.getName().equals(req)) {
        return true;
      }
    }
    return false;
  }

  private boolean evaluateGeneralRequirements(Collection<BuildingBlock> blocks) {
    for (var a : blocks) {
      if (a == null) {
        continue;
      }
      for (String req : a.getRequires()) {
        if (!evaluateRequirement(blocks, req)) {
          return false;
        }
      }
    }
    return true;
  }

  private String getBackboneFormula(BuildingBlock backbone) {
    String f = backbone.getGeneralFormula().replaceAll("\\)n", ")" + n).replaceAll("\\)m", ")" + m)
        .replaceAll("\\)k", ")" + k).replaceAll("R", "");
    return f;
  }

  private String getFgFormula(BuildingBlock fg) {
    String f = fg.getGeneralFormula().replaceAll("X", "").replaceAll("(Z)([0-9]?)", "");
    return f;
  }

  private String getBackboneLinkerFormula(BuildingBlock backboneLinker) {
    String f = backboneLinker.getGeneralFormula().replaceAll("X", "").replaceAll("R", "");
    return f;
  }

  private String getSubstituentFormula(BuildingBlock substituent) {
    String f = substituent.getGeneralFormula();
    return f;
  }
}
