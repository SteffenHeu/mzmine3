package io.github.mzmine.modules.dataprocessing.featdet_baselinecorrection.als;

import io.github.mzmine.modules.dataprocessing.featdet_baselinecorrection.AbstractBaselineCorrectorParameters;
import io.github.mzmine.parameters.parametertypes.DoubleParameter;
import io.github.mzmine.parameters.parametertypes.IntegerParameter;
import java.text.DecimalFormat;

public class ALSBaselineCorrectorParameters extends AbstractBaselineCorrectorParameters {

  public static final DoubleParameter lambda = new DoubleParameter("Lambda",
      "Smoothness parameter. Higher values make baseline more rigid (typically 1e5-1e9)",
      new DecimalFormat("#.##E0"), 1E5);

  public static final DoubleParameter asymmetry = new DoubleParameter("Asymmetry",
      "Asymmetry parameter (typically 0.001-0.1)", new DecimalFormat("0.000"), 0.01, 0d, 1d);

  public static final IntegerParameter iterations = new IntegerParameter("Iterations",
      "Number of iterations", 3);

  public ALSBaselineCorrectorParameters() {
    super(lambda, asymmetry, iterations);
  }
}
