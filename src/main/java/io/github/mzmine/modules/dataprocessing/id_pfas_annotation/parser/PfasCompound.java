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

import org.openscience.cdk.interfaces.IMolecularFormula;

public class PfasCompound {

  private final IMolecularFormula formula;
  private final BuildingBlock backbone;
  private final BuildingBlock backboneLinker;
  private final BuildingBlock functionalGroup;
  private final BuildingBlock substituent;

  private final int n, m, k;

  public PfasCompound(BuildingBlock backbone, BuildingBlock backboneLinker,
      BuildingBlock functionalGroup, BuildingBlock substituent, int n, int m, int k) {
    this.backbone = backbone;
    this.backboneLinker = backboneLinker;
    this.functionalGroup = functionalGroup;
    this.substituent = substituent;
    this.n = n;
    this.m = m;
    this.k = k;

    String backboneFormula = backbone.getGeneralFormula().replaceAll("\\)n", ")" + n)
        .replaceAll("\\)m", ")" + m).replaceAll("\\)k", ")" + k);
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

  public BuildingBlock getSubstituent() {
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
}
