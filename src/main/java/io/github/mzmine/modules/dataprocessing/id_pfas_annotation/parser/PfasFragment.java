package io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser;


import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

public record PfasFragment(double mz, String formula, BuildingBlock block) {

  /**
   * Only used for {link #toString}.
   */
  private static final NumberFormat mzFormat = new DecimalFormat("0.000000",
      new DecimalFormatSymbols(Locale.US));

  @Override
  public String toString() {
    return new StringBuilder(this.getClass().getSimpleName()).append("[mz=")
        .append(mzFormat.format(mz)).append(", formula=").append(formula).append(", block=")
        .append(block.toString()).append("]").toString();
  }
}
