package io.github.mzmine.datamodel.features.types.annotations;

import io.github.mzmine.datamodel.features.types.numbers.abstr.DoubleType;
import io.github.mzmine.main.MZmineCore;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import javax.annotation.Nonnull;

public class PpmType extends DoubleType {

  private static final NumberFormat formatter = MZmineCore.getConfiguration().getPPMFormat();

  public PpmType() {
    super(new DecimalFormat("0.00"));
  }

  @Nonnull
  @Override
  public String getHeaderString() {
    return MZmineCore.getConfiguration().getUnitFormat().format("\u0394", "ppm");
  }

  @Override
  public NumberFormat getFormatter() {
    return formatter;
  }
}
