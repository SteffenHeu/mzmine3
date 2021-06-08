package io.github.mzmine.modules.dataprocessing.id_pfas_annotation;

import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasCompound;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasFragment;
import java.util.List;

public class PfasMatch {

  private final ModularFeatureListRow row;
  private final PfasCompound compound;
  private final double coverageScore;
  private final List<PfasFragment> matchedFragments;

  public PfasMatch(ModularFeatureListRow row, PfasCompound compound, double coverageScore,
      List<PfasFragment> matchedFragments) {
    this.row = row;
    this.compound = compound;
    this.coverageScore = coverageScore;
    this.matchedFragments = matchedFragments;
  }

  public ModularFeatureListRow getRow() {
    return row;
  }

  public PfasCompound getCompound() {
    return compound;
  }

  public double getCoverageScore() {
    return coverageScore;
  }

  public List<PfasFragment> getMatchedFragments() {
    return matchedFragments;
  }

  @Override
  public String toString() {
    return compound.getName();
  }
}
