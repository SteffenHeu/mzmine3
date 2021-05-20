package io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser;

import com.google.common.collect.Range;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;

public class PfasCompoundBuilder {

  private final Range<Integer> nRange, mRange, kRange;

  public PfasCompoundBuilder(@Nonnull final Range<Integer> nRange,
      @Nonnull final Range<Integer> mRange, @Nonnull final Range<Integer> kRange) {
    this.nRange = nRange;
    this.mRange = mRange;
    this.kRange = kRange;
  }

  /**
   * Generates valid compounds for the given list of {@link BuildingBlock}s
   * @return A list of {@link PfasCompound}s
   */
  public List<PfasCompound> getCompounds(BuildingBlock backbone, BuildingBlock backboneLinker,
      BuildingBlock functionalGroup, Collection<BuildingBlock> substituent) {
    final List<PfasCompound> compounds = new ArrayList<>();

    if (backbone.getGeneralFormula().contains(")n")) {
      for (int n = nRange.lowerEndpoint(); n <= nRange.upperEndpoint(); n++) {
        if (backbone.getGeneralFormula().contains(")m")) {
          for (int m = mRange.lowerEndpoint(); m <= mRange.upperEndpoint(); m++) {
            if (backbone.getGeneralFormula().contains(")k")) {
              for (int k = kRange.lowerEndpoint(); k <= kRange.upperEndpoint(); k++) {
                generate(compounds, backbone, backboneLinker, functionalGroup, substituent, n, m,
                    k);
              }
            } else {
              generate(compounds, backbone, backboneLinker, functionalGroup, substituent, n, m, -1);
            }
          }
        } else {
          generate(compounds, backbone, backboneLinker, functionalGroup, substituent, n, -1, -1);
        }
      }
    } else {
      generate(compounds, backbone, backboneLinker, functionalGroup, substituent, -1, -1, -1);
    }

    return compounds;
  }

  private void generate(List<PfasCompound> compounds, BuildingBlock backbone,
      BuildingBlock backboneLinker, BuildingBlock functionalGroup,
      Collection<BuildingBlock> substituent, int n, int m, int k) {
    try {
      PfasCompound compound = new PfasCompound(backbone, backboneLinker, functionalGroup,
          substituent, n, m, k);
      compounds.add(compound);
    } catch (InvalidCompoundConfigurationException ignored) {
    }
  }
}
