package io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser;

public enum BlockClass {
  BACKBONE, BACKBONE_LINKER, SUBSTITUENT, FUNCTIONAL_GROUP;

  public static BlockClass get(String str) {
    for (BlockClass c : BlockClass.values()) {
      if (c.toString().equalsIgnoreCase(str) || c.toString()
          .equalsIgnoreCase(str.trim().replace(" ", "_"))) {
        return c;
      }
    }
    return null;
  }
}
