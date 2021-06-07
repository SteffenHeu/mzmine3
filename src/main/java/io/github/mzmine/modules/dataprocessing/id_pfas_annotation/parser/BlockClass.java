package io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser;

public enum BlockClass {
  BACKBONE("B"), BACKBONE_LINKER("L"), SUBSTITUENT("S"), FUNCTIONAL_GROUP("F");

  private final String shortName;

  BlockClass(String shortName) {
    this.shortName = shortName;
  }

  public static BlockClass get(String str) {
    for (BlockClass c : BlockClass.values()) {
      if (c.toString().equalsIgnoreCase(str) || c.toString()
          .equalsIgnoreCase(str.trim().replace(" ", "_"))) {
        return c;
      }
    }
    return null;
  }

  public String getShortName() {
    return shortName;
  }
}
