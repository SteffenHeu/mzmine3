package io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser;

import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import io.github.mzmine.util.FormulaUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PfasLibraryBuilder {

  protected static final String BACKBONE_PLACEHOLDER = "X";
  protected static final String FG_PLACEHOLDER = "R";
  protected static final String SUBSTITUENT_PLACEHOLDER = "Z";

  private static final Logger logger = Logger.getLogger(PfasLibraryBuilder.class.getName());

  @NotNull
  private final List<BuildingBlock> blocks = new ArrayList<>();

  private final Range<Integer> nRange;
  private final Range<Integer> mRange;
  private final Range<Integer> kRange;

  private final Map<BlockClass, List<BuildingBlock>> blockMap = new HashMap<>();
  private final Map<Integer, Set<Set<BuildingBlock>>> substituentCombinations = new HashMap<>();

  private List<PfasCompound> library;

  /**
   * Creates a new Library builder.
   *
   * @param blocks The building blocks for this library. The list is copied, so changes to the
   *               original list are not reflected in this library builder.
   */
  public PfasLibraryBuilder(@NotNull final List<BuildingBlock> blocks) {
    this(blocks, Range.closed(4, 8), Range.closed(1, 6), Range.closed(1, 6));
  }

  public PfasLibraryBuilder(@NotNull final List<BuildingBlock> blocks,
      @NotNull final Range<Integer> nRange, @NotNull final Range<Integer> mRange,
      @NotNull final Range<Integer> kRange) {
    this.blocks.addAll(blocks);
    this.nRange = nRange;
    this.mRange = mRange;
    this.kRange = kRange;

    logger.finest(() -> "Initialised builder. n=" + nRange + " m=" + mRange + " k=" + kRange);
  }

  public static boolean isValid(BuildingBlock block) {
    switch (block.getBlockClass()) {
      case BACKBONE -> {
        return FormulaUtils.checkMolecularFormula(
            block.getGeneralFormula().replace("k", "").replace("m", "").replace("n", "")
                .replace(FG_PLACEHOLDER, "")) && block.getGeneralFormula().contains("n") && block
            .getGeneralFormula().contains(FG_PLACEHOLDER);
      }
      case BACKBONE_LINKER -> {
        return FormulaUtils.checkMolecularFormula(
            block.getGeneralFormula().replace(BACKBONE_PLACEHOLDER, "").replace(FG_PLACEHOLDER, ""))
            && block.getGeneralFormula().contains(BACKBONE_PLACEHOLDER) && block.getGeneralFormula()
            .contains(FG_PLACEHOLDER);
      }
      case SUBSTITUENT -> {
        return FormulaUtils.checkMolecularFormula(block.getGeneralFormula());
      }
      case FUNCTIONAL_GROUP -> {
        return FormulaUtils.checkMolecularFormula(
            block.getGeneralFormula().replace(BACKBONE_PLACEHOLDER, "")
                .replace(SUBSTITUENT_PLACEHOLDER, ""));
      }
    }
    return false;
  }

  private void buildBlockMap() {
    if (!blockMap.isEmpty()) {
      throw new RuntimeException("Block map has already been build.");
    }

    for (final BuildingBlock block : blocks) {
      final List<BuildingBlock> blockList = blockMap
          .computeIfAbsent(block.getBlockClass(), b -> new ArrayList<>());
//      if (isValid(block)) {
      blockList.add(block);
//      }
    }

    // add null, so we automatically build compounds without linkers.
    blockMap.computeIfAbsent(BlockClass.BACKBONE_LINKER, b -> new ArrayList<>()).add(null);

    StringBuilder log = new StringBuilder("Block map initialised: ");
    for (Entry<BlockClass, List<BuildingBlock>> entry : blockMap.entrySet()) {
      log.append(entry.getKey()).append(": ").append(entry.getValue().size()).append("; ");
    }
    logger.finest(log::toString);
  }

  public void buildLibrary() {
    buildBlockMap();

    if (blockMap.isEmpty()) {
      return;
    }

    substituentCombinations.computeIfAbsent(1, i -> {
      Set<Set<BuildingBlock>> combinations = new HashSet<>();
      blockMap.get(BlockClass.SUBSTITUENT).forEach(b -> combinations.add(Set.of(b)));
      return combinations;
    });
    if (blockMap.get(BlockClass.SUBSTITUENT).size() > 1) {
      substituentCombinations.computeIfAbsent(2,
          i -> Sets.combinations(new HashSet<>(blockMap.get(BlockClass.SUBSTITUENT)), 2));
    }

    final List<PfasCompound> compounds = new ArrayList<>();
    final PfasCompoundBuilder builder = new PfasCompoundBuilder(nRange, mRange, kRange);

    final Pattern pat = Pattern.compile("(Z)([0-9]?)");

    for (BuildingBlock backbone : blockMap.get(BlockClass.BACKBONE)) {
      for (BuildingBlock backboneLinker : blockMap.get(BlockClass.BACKBONE_LINKER)) {
        for (BuildingBlock funcGroup : blockMap.get(BlockClass.FUNCTIONAL_GROUP)) {
          final Matcher m = pat.matcher(funcGroup.getGeneralFormula());
          int numSubstituents =
              m.find() && !m.group(2).isEmpty() ? Integer.parseInt(m.group(2)) : 1;
          final Set<Set<BuildingBlock>> subCombinations = substituentCombinations
              .computeIfAbsent(numSubstituents,
                  i -> Sets.combinations(new HashSet<>(blockMap.get(BlockClass.SUBSTITUENT)), i));

          for (Set<BuildingBlock> subCombination : subCombinations) {
            compounds
                .addAll(builder.getCompounds(backbone, backboneLinker, funcGroup, subCombination));
          }
        }
      }
    }

    library = compounds;
    logger.finest(
        () -> "Built " + compounds.size() + " compounds from " + blocks.size() + " blocks.");
  }

  @Nullable
  public List<PfasCompound> getLibrary() {
    return library;
  }

  @NotNull
  public List<BuildingBlock> getBlocks() {
    return blocks;
  }

  public Range<Integer> getnRange() {
    return nRange;
  }

  public Range<Integer> getmRange() {
    return mRange;
  }

  public Range<Integer> getkRange() {
    return kRange;
  }


}
