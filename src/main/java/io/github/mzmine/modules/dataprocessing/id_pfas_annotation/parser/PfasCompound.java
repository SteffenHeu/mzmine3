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

import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.util.ArrayUtils;
import io.github.mzmine.util.FormulaUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.interfaces.IMolecularFormula;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;

public class PfasCompound {

  private static final String FRAGMENT_BACKBONE_REGEX =
      "(" + PfasLibraryBuilder.BACKBONE_PLACEHOLDER + ")([A-Z])";

  private final IMolecularFormula formula;
  private final BuildingBlock backbone;
  private final BuildingBlock backboneLinker;
  private final BuildingBlock functionalGroup;
  private final Collection<BuildingBlock> substituent;
  private final List<BuildingBlock> blocks = new ArrayList<>();
  private final List<PfasFragment> positveFragments;
  private final List<PfasFragment> negativeFragments;
  private final double[] positiveMzs;
  private final double[] negativeMzs;
  private final int n, m, k;
  private final String name;
  private Double positiveMz = null;
  private Double negativeMz = null;

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
        .getMajorIsotopeMolecularFormula(getBlockFormula(backbone, n, m, k), inst);

    final IMolecularFormula functionalGroupFormula = MolecularFormulaManipulator
        .getMajorIsotopeMolecularFormula(getBlockFormula(functionalGroup, n, m, k), inst);
    backboneFormula.add(functionalGroupFormula);

    for (BuildingBlock subs : substituent) {
      final IMolecularFormula substituentFormula = MolecularFormulaManipulator
          .getMajorIsotopeMolecularFormula(getBlockFormula(subs, n, m, k), inst);
      backboneFormula.add(substituentFormula);
    }

    if (backboneLinker != null) {
      final IMolecularFormula backboneLinkerFormula = MolecularFormulaManipulator
          .getMajorIsotopeMolecularFormula(getBlockFormula(backboneLinker, n, m, k), inst);
      backboneFormula.add(backboneLinkerFormula);
    }

    formula = backboneFormula;

    negativeFragments = computeObservedFragments(PolarityType.NEGATIVE);
    positveFragments = computeObservedFragments(PolarityType.POSITIVE);

    positiveMzs = positveFragments.stream().mapToDouble(PfasFragment::mz).toArray();
    negativeMzs = negativeFragments.stream().mapToDouble(PfasFragment::mz).toArray();

    name = getBackboneName() + " " + getBackboneLinkerName() + " " + getFunctionalGroupName() + " "
        + getSubstituentNames();
  }

  private String getBlockFormula(@Nonnull BuildingBlock block, int n, int m, int k) {
    return switch (block.getBlockClass()) {
      case BACKBONE -> block.getGeneralFormula().replaceAll("\\)n", ")" + n)
          .replaceAll("\\)m", ")" + m).replaceAll("\\)k", ")" + k)
          .replaceAll(PfasLibraryBuilder.FG_PLACEHOLDER, "");
      case BACKBONE_LINKER -> block.getGeneralFormula()
          .replaceAll(PfasLibraryBuilder.BACKBONE_PLACEHOLDER, "")
          .replaceAll(PfasLibraryBuilder.FG_PLACEHOLDER, "");
      case FUNCTIONAL_GROUP -> block.getGeneralFormula()
          .replaceAll(PfasLibraryBuilder.BACKBONE_PLACEHOLDER, "")
          .replaceAll("(" + PfasLibraryBuilder.SUBSTITUENT_PLACEHOLDER + ")([0-9]?)", "");
      case SUBSTITUENT -> block.getGeneralFormula();
    };
  }

  @Nonnull
  private String getBackboneFragmentFormula(@Nonnull String formula, int n, int m, int k) {
    String f = formula.replaceAll("\\)n", ")" + n).replaceAll("\\)m", ")" + m)
        .replaceAll("\\)k", ")" + k).replaceAll(PfasLibraryBuilder.FG_PLACEHOLDER, "");
    return f;
  }

  public List<BuildingBlock> getBlocks() {
    return List.copyOf(blocks);
  }

  public IMolecularFormula getFormula() {
    return formula;
  }

  public BuildingBlock getBackbone() {
    return backbone;
  }

  public String getBackboneName() {
    return backbone.getName();
  }

  public BuildingBlock getBackboneLinker() {
    return backboneLinker;
  }

  public String getBackboneLinkerName() {
    return backboneLinker != null ? backboneLinker.getName() : "";
  }

  public BuildingBlock getFunctionalGroup() {
    return functionalGroup;
  }

  public String getFunctionalGroupName() {
    return functionalGroup.getName();
  }

  public Collection<BuildingBlock> getSubstituent() {
    return substituent;
  }

  public String getSubstituentNames() {
    StringBuilder name = new StringBuilder();
    final Iterator<BuildingBlock> iterator = substituent.iterator();
    while (iterator.hasNext()) {
      final BuildingBlock block = iterator.next();
      name.append(block.getName());
      if (iterator.hasNext()) {
        name.append(" ");
      }
    }

    return name.toString();
  }

  @Nonnull
  public String getName() {
    return name;
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

  private boolean evaluateRequirement(Collection<BuildingBlock> blocks, String reqs) {
    final String[] req = reqs.split("\\+");
    final boolean[] reqOk = new boolean[req.length];

    for (BuildingBlock b : blocks) {
      if (b == null) {
        continue;
      }
      for(int i = 0; i < reqOk.length; i++) {
        if (b.getName().equals(req[i]) || (b.getTypes() != null && b.getTypes().contains(req[i]))) {
          reqOk[i] = true;
        }
      }
    }
    return ArrayUtils.allEquals(reqOk, true);
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

  /**
   * @param polarityType The polarity type.
   * @return A computed fragment spectrum. Null if no fragment spectrum can be generated for the
   * given polarity type.
   */
  /*@Nullable
  public MassSpectrum getFragmentSpectrum(PolarityType polarityType) {
    final List<PfasFragment> fragments = computeObservedFragments(polarityType);
    return null;
  }*/
  public List<PfasFragment> getObservedIons(final PolarityType polarityType) {
    return polarityType == PolarityType.POSITIVE ? positveFragments : negativeFragments;
  }

  public double[] getPositiveIonMzs() {
    return positiveMzs;
  }

  public double[] getNegativeIonMzs() {
    return negativeMzs;
  }

  public double[] getIonMzs(PolarityType polarityType) {
    return polarityType == PolarityType.POSITIVE ? positiveMzs : negativeMzs;
  }

  /**
   * @param polarityType
   * @return A list of all observed fragments including neutral losses.
   */
  private List<PfasFragment> computeObservedFragments(final PolarityType polarityType) {
    assert polarityType != PolarityType.UNKNOWN;
    assert polarityType != PolarityType.NEUTRAL;

    final double precursorMz = getPrecursorMz(polarityType);

    final List<PfasFragment> fragments = computeFragments(polarityType);
    fragments.addAll(computeNeutralLosses(polarityType, precursorMz));

    fragments.sort(Comparator.comparingDouble(PfasFragment::mz));

    return fragments;
  }

  /**
   * @param polarityType The ion polarity (positive or negative)
   * @return The m/z or null, if protonation cannot be adjusted.
   */
  public Double getPrecursorMz(PolarityType polarityType) {
    assert polarityType == PolarityType.POSITIVE || polarityType == PolarityType.NEGATIVE;

    if (positiveMz != null && polarityType == PolarityType.POSITIVE) {
      return positiveMz;
    } else if (negativeMz != null && polarityType == PolarityType.NEGATIVE) {
      return negativeMz;
    }

    IMolecularFormula chargedFormula;
    if (formula.getCharge() == null || formula.getCharge() == 0) {
      chargedFormula = FormulaUtils.cloneFormula(formula);
      MolecularFormulaManipulator.adjustProtonation(chargedFormula, polarityType.getSign());
    } else if (formula.getCharge() >= 1 && polarityType == PolarityType.POSITIVE) {
      chargedFormula = formula;
    } else if (formula.getCharge() <= -1 && polarityType == PolarityType.NEGATIVE) {
      chargedFormula = formula;
    } else {
      final int charge = formula.getCharge();
      chargedFormula = FormulaUtils.cloneFormula(formula);
      if (!MolecularFormulaManipulator
          .adjustProtonation(chargedFormula, (charge * -1) + polarityType.getSign())) {
        return null;
      }
    }

    if (polarityType == PolarityType.POSITIVE) {
      positiveMz = FormulaUtils.calculateMzRatio(chargedFormula);
      return positiveMz;
    } else {
      negativeMz = FormulaUtils.calculateMzRatio(chargedFormula);
      return negativeMz;
    }
  }

  private List<PfasFragment> computeFragments(final PolarityType polarityType) {
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
          fragments.addAll(computeBackboneFragments(formulas.get(j), block));
        } else {

          // in case some fragments require the backbone to be known, we have to compute the mass here.
          if (formulas.get(j).matches(FRAGMENT_BACKBONE_REGEX) && masses.get(j) == null) {
            final String formula = getBackboneFragmentFormula(formulas.get(j), n, m, k);
            formulas.set(j, formula);
            masses.set(j, FormulaUtils.calculateMzRatio(formula));
          }

          fragments.add(new PfasFragment(masses.get(j), formulas.get(j), block));
        }
      }
    }

    return fragments;
  }

  private List<PfasFragment> computeBackboneFragments(String fragmentFormula,
      BuildingBlock backbone) {

    final List<PfasFragment> fragments = new ArrayList<>();

    // generate fragments from the general formula
    for (int n = 0; n < this.n; n++) {
      if (this.m != -1) {
        if (k != -1) {
          for (int k = 0; 0 < this.k; k++) {
            final String formula = getBackboneFragmentFormula(fragmentFormula, n, m, k);
            final double mz = FormulaUtils.calculateMzRatio(formula);
            fragments.add(new PfasFragment(mz, formula, backbone));
          }
        } else {
          for (int m = 0; m < this.m; m++) {
            final String formula = getBackboneFragmentFormula(fragmentFormula, n, m, -1);
            final double mz = FormulaUtils.calculateMzRatio(formula);
            fragments.add(new PfasFragment(mz, formula, backbone));
          }
        }
      } else {
        final String formula = getBackboneFragmentFormula(fragmentFormula, n, -1, -1);
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
  private List<PfasFragment> computeNeutralLosses(PolarityType polarityType, double baseMass) {
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
