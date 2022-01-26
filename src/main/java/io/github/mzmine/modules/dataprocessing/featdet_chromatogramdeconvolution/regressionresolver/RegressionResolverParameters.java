package io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.regressionresolver;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.FeatureResolver;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.FeatureResolverSetupDialog;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.GeneralResolverParameters;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.Resolver;
import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.impl.IonMobilitySupport;
import io.github.mzmine.parameters.parametertypes.DoubleParameter;
import io.github.mzmine.parameters.parametertypes.IntegerParameter;
import io.github.mzmine.parameters.parametertypes.OptionalParameter;
import io.github.mzmine.parameters.parametertypes.PercentParameter;
import io.github.mzmine.parameters.parametertypes.ranges.RTRangeParameter;
import io.github.mzmine.util.ExitCode;
import java.text.DecimalFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RegressionResolverParameters extends GeneralResolverParameters {

  public static final DoubleParameter minHeight = new DoubleParameter("Minimum height",
      "Minimum height of a feature.", MZmineCore.getConfiguration().getIntensityFormat(), 1E3, 0d,
      Double.MAX_VALUE);

  public static final DoubleParameter minSlope = new DoubleParameter("Minimum slope",
      "Minimum normalized slope to recognize a feature start and end.", new DecimalFormat("0.00"),
      0.1);

  public static final IntegerParameter slopeCalcPoints = new IntegerParameter(
      "Points for slope", "Number of points to calculate the slope with.", 4);

  public static final IntegerParameter minNumPoints = new IntegerParameter(
      "Minimum number of points", "Minimum number of points in a feature.", 5);

  public static final DoubleParameter topToEdgeRatio = new DoubleParameter("Top to edge ratio",
      "Ratio of a feature's start/end to the feature's top.", new DecimalFormat("0.0"), 2d);

  public static final OptionalParameter<PercentParameter> chromatographicThreshold = new OptionalParameter<>(new PercentParameter(
      "Chromatographic threshold", "Percentile threshold for removing noise.\n"
      + "The algorithm will remove the lowest abundant X % data points from a chromatogram and only consider\n"
      + "the remaining (highest) values. Important filter for noisy chromatograms.", 0.85d, 0d, 1d));

  public static final RTRangeParameter rtRange = new RTRangeParameter("Feature duration",
      "The allowed duration of a feature.", true, Range.closed(0.0d, 10d));

  public RegressionResolverParameters() {
    super(new Parameter[]{PEAK_LISTS, SUFFIX, handleOriginal, groupMS2Parameters, dimension,
        minHeight, minSlope, slopeCalcPoints, minNumPoints, topToEdgeRatio, chromatographicThreshold, rtRange});
  }

  @Override
  public ExitCode showSetupDialog(boolean valueCheckRequired) {
    final FeatureResolverSetupDialog dialog = new FeatureResolverSetupDialog(valueCheckRequired,
        this, null);
    dialog.showAndWait();
    return dialog.getExitCode();
  }

  @Override
  public FeatureResolver getResolver() {
    throw new UnsupportedOperationException("Legacy resolver method. Unsupported in local min.");
  }

  @Nullable
  @Override
  public Resolver getResolver(ParameterSet parameters, ModularFeatureList flist) {
    return new RegressionResolver(parameters, flist);
  }

  @NotNull
  @Override
  public IonMobilitySupport getIonMobilitySupport() {
    return IonMobilitySupport.SUPPORTED;
  }
}
