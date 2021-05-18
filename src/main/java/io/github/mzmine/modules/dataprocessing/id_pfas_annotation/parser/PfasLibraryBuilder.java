package io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser;

import io.github.mzmine.util.FormulaUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

public class PfasLibraryBuilder {

  private static final Logger logger = Logger.getLogger(PfasLibraryBuilder.class.getName());

  @Nonnull
  private final List<BuildingBlock> blocks = new ArrayList<>();

  private int backboneMinN = 1;
  private int backboneMaxN = 18;
  private int backboneMinM = 1;
  private int backboneMaxM = 18;
  private int backboneMinK = 1;
  private int backboneMaxK = 18;

  private final Map<BlockClass, List<BuildingBlock>> blockMap = new HashMap<>();

  /**
   * Creates a new Library builder.
   *
   * @param blocks The building blocks for this library. The list is copied, so changes to the
   *               original list are not reflected in this library builder.
   */
  public PfasLibraryBuilder(@Nonnull final List<BuildingBlock> blocks) {
    this.blocks.addAll(blocks);
  }

  private void buildBlockMap() {
    for (final BuildingBlock block : blocks) {
      final List<BuildingBlock> blockList = blockMap
          .computeIfAbsent(block.getBlockClass(), b -> new ArrayList<>());
      blockList.add(block);
    }
  }

  public void buildLibrary() {
    if (blocks.isEmpty()) {
      return;
    }


  }

  @Nonnull
  public List<BuildingBlock> getBlocks() {
    return blocks;
  }

  public int getBackboneMinN() {
    return backboneMinN;
  }

  public void setBackboneMinN(int backboneMinN) {
    this.backboneMinN = backboneMinN;
  }

  public int getBackboneMaxN() {
    return backboneMaxN;
  }

  public void setBackboneMaxN(int backboneMaxN) {
    this.backboneMaxN = backboneMaxN;
  }

  public int getBackboneMinM() {
    return backboneMinM;
  }

  public void setBackboneMinM(int backboneMinM) {
    this.backboneMinM = backboneMinM;
  }

  public int getBackboneMaxM() {
    return backboneMaxM;
  }

  public void setBackboneMaxM(int backboneMaxM) {
    this.backboneMaxM = backboneMaxM;
  }

  public int getBackboneMinK() {
    return backboneMinK;
  }

  public void setBackboneMinK(int backboneMinK) {
    this.backboneMinK = backboneMinK;
  }

  public int getBackboneMaxK() {
    return backboneMaxK;
  }

  public void setBackboneMaxK(int backboneMaxK) {
    this.backboneMaxK = backboneMaxK;
  }

  public static boolean isValid(BuildingBlock block) {
    switch (block.getBlockClass()) {
      case BACKBONE -> {
        return FormulaUtils.checkMolecularFormula(
            block.getGeneralFormula().replace("k", "").replace("m", "").replace("n", "")
                .replace("R", "")) && block.getGeneralFormula().contains("n") && block
            .getGeneralFormula().contains("R");
      }
      case BACKBONE_LINKER -> {
        return FormulaUtils
            .checkMolecularFormula(block.getGeneralFormula().replace("X", "").replace("R", ""))
            && block.getGeneralFormula().contains("X") && block.getGeneralFormula().contains("R");
      }
      case SUBSTITUENT -> {
        return FormulaUtils.checkMolecularFormula(block.getGeneralFormula());
      }
      case FUNCTIONAL_GROUP -> {
        return FormulaUtils
            .checkMolecularFormula(block.getGeneralFormula().replace("X", "").replace("Y", ""));
      }
    }
    return false;
  }
}
