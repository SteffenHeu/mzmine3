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

import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.util.FormulaUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
  private final List<BuildingBlock> blocks = new ArrayList<>();

  private final int n, m, k;

  public PfasCompound(@Nonnull final BuildingBlock backbone,
      @Nullable final BuildingBlock backboneLinker, @Nonnull final BuildingBlock functionalGroup,
      @Nonnull final Collection<BuildingBlock> substituent, int n, int m, int k)
      throws InvalidCompoundConfigurationException {
    this.backbone = backbone;
    this.backboneLinker = backboneLinker;
    this.functionalGroup = functionalGroup;
    this.substituent = substituent;
    this.n = n;
    this.m = m;
    this.k = k;

    blocks.add(backbone);
    if (backboneLinker != null) {
      blocks.add(backboneLinker);
    }
    blocks.add(functionalGroup);
    blocks.addAll(substituent);

    if (!evaluateGeneralRequirements(blocks)) {
      throw new InvalidCompoundConfigurationException();
    }

    final IChemObjectBuilder inst = SilentChemObjectBuilder.getInstance();

    final IMolecularFormula backboneFormula = MolecularFormulaManipulator
        .getMajorIsotopeMolecularFormula(getBackboneFormula(backbone, n, m, k), inst);

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
      if (b.getName().equals(req) || (b.getTypes() != null && b.getTypes().contains(req))) {
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

  private String getBackboneFormula(BuildingBlock backbone, int n, int m, int k) {
    String f = backbone.getGeneralFormula().replaceAll("\\)n", ")" + n).replaceAll("\\)m", ")" + m)
        .replaceAll("\\)k", ")" + k).replaceAll("R", "");
    return f;
  }

  private String getBackboneFormula(String formula, int n, int m, int k) {
    String f = formula.replaceAll("\\)n", ")" + n).replaceAll("\\)m", ")" + m)
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

  /**
   * @param polarityType The polarity type.
   * @return A computed fragment spectrum. Null if no fragment spectrum can be generated for the
   * given polarity type.
   */
  @Nullable
  public MassSpectrum getFragmentSpectrum(PolarityType polarityType) {
    final List<PfasFragment> fragments = getObservedIons(polarityType);
    return null;
  }

  /**
   * @param polarityType
   * @return A list of all observed fragments including neutral losses.
   */
  public List<PfasFragment> getObservedIons(final PolarityType polarityType) {
    assert polarityType != PolarityType.UNKNOWN;
    assert polarityType != PolarityType.NEUTRAL;

    IMolecularFormula chargedFormula;
    if (formula.getCharge() == null || formula.getCharge() == 0) {
      chargedFormula = FormulaUtils.cloneFormula(formula);
      MolecularFormulaManipulator.adjustProtonation(chargedFormula, polarityType.getSign());
    } else if (formula.getCharge() == 1 && polarityType == PolarityType.POSITIVE) {
      chargedFormula = formula;
    } else if (formula.getCharge() == -1 && polarityType == PolarityType.NEGATIVE) {
      chargedFormula = formula;
    } else {
      return null;
    }

    final double precursorMz = FormulaUtils.calculateMzRatio(chargedFormula);

    final List<PfasFragment> fragments = getFragments(polarityType);
    fragments.addAll(getNeutralLosses(polarityType, precursorMz));

    return fragments;
  }

  public List<PfasFragment> getFragments(final PolarityType polarityType) {
    final List<PfasFragment> fragments = new ArrayList<>();

    for (int i = 0; i < blocks.size(); i++) {
      final BuildingBlock block = blocks.get(i);

      final List<Double> masses = block.getFragmentMasses(polarityType);
      final List<String> formulas = block.getFragmentFormulas(polarityType);
      final List<String> reqs = block.getFragmentReqs(polarityType);

      for (int j = 0; j < reqs.size(); j++) {
        if (reqs.get(j) != null && !evaluateRequirement(blocks, reqs.get(j))) {
          continue;
        }

        if (block.getBlockClass() == BlockClass.BACKBONE && formulas.get(j).contains(")n")) {
          fragments.addAll(generateBackboneFragments(formulas.get(j), block));
        } else {
          fragments.add(new PfasFragment(masses.get(j), formulas.get(j), block));
        }
      }
    }

    return fragments;
  }

  private List<PfasFragment> generateBackboneFragments(String fragmentFormula,
      BuildingBlock backbone) {

    final List<PfasFragment> fragments = new ArrayList<>();

    // generate fragments from the general formula
    for (int n = 0; n < this.n; n++) {
      if (this.m != -1) {
        if (k != -1) {
          for (int k = 0; 0 < this.k; k++) {
            final String formula = getBackboneFormula(fragmentFormula, n, m, k);
            final double mz = FormulaUtils.calculateMzRatio(formula);
            fragments.add(new PfasFragment(mz, formula, backbone));
          }
        } else {
          for (int m = 0; m < this.m; m++) {
            final String formula = getBackboneFormula(fragmentFormula, n, m, -1);
            final double mz = FormulaUtils.calculateMzRatio(formula);
            fragments.add(new PfasFragment(mz, formula, backbone));
          }
        }
      } else {
        final String formula = getBackboneFormula(fragmentFormula, n, -1, -1);
        final double mz = FormulaUtils.calculateMzRatio(formula);
        fragments.add(new PfasFragment(mz, formula, backbone));
      }
    }

    return fragments;
  }

  /**
   * @param polarityType The polarity type
   * @param baseMass     m/z of the precursor
   * @return
   */
  public List<PfasFragment> getNeutralLosses(PolarityType polarityType, double baseMass) {
    final List<PfasFragment> fragments = new ArrayList<>();

    for (int i = 0; i < blocks.size(); i++) {
      final BuildingBlock block = blocks.get(i);

      final List<Double> masses = block.getNeutralLossMasses(polarityType);
      final List<String> formulas = block.getNeutralLossFormulas(polarityType);
      final List<String> reqs = block.getNeutralLossReqs(polarityType);

      for (int j = 0; j < masses.size(); j++) {
        if (reqs.get(j) != null && !evaluateRequirement(blocks, reqs.get(j))) {
          continue;
        }

        fragments.add(new PfasFragment(baseMass - masses.get(j),
            "[M-H-" + formulas.get(j) + "]" + polarityType.asSingleChar(), block));
      }
    }

    return fragments;
  }
}
