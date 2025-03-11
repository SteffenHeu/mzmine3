package io.github.mzmine.modules.dataprocessing.featdet_baselinecorrection.als;

import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.gui.chartbasics.simplechart.providers.impl.AnyXYProvider;
import io.github.mzmine.modules.dataprocessing.featdet_baselinecorrection.AbstractBaselineCorrectorParameters;
import io.github.mzmine.modules.dataprocessing.featdet_baselinecorrection.AbstractResolverBaselineCorrector;
import io.github.mzmine.modules.dataprocessing.featdet_baselinecorrection.BaselineCorrectionParameters;
import io.github.mzmine.modules.dataprocessing.featdet_baselinecorrection.BaselineCorrector;
import static io.github.mzmine.modules.dataprocessing.featdet_baselinecorrection.als.AlsCorrection.asymmetricLeastSquaresBaseline;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.minimumsearch.MinimumSearchFeatureResolver;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.util.MemoryMapStorage;
import java.awt.Color;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ALSBaselineCorrector extends AbstractResolverBaselineCorrector {

  private static final Logger logger = Logger.getLogger(ALSBaselineCorrector.class.getName());

  private final double lambda;
  private final double p;
  private final int iterations;

  public ALSBaselineCorrector() {
    super(null, 5, "bl", null);
    lambda = 1E5;
    p = 0.01;
    iterations = 1;
  }

  public ALSBaselineCorrector(@Nullable MemoryMapStorage storage, int numSamples,
      @NotNull String suffix, @Nullable MinimumSearchFeatureResolver resolver,
      ParameterSet parameters) {
    super(storage, numSamples, suffix, resolver);
    lambda = parameters.getValue(ALSBaselineCorrectorParameters.lambda);
    p = parameters.getValue(ALSBaselineCorrectorParameters.asymmetry);
    iterations = parameters.getValue(ALSBaselineCorrectorParameters.iterations);
  }

  @Override
  protected void subSampleAndCorrect(double[] xDataToCorrect, double[] yDataToCorrect,
      int numValues, double[] xDataFiltered, double[] yDataFiltered, int numValuesFiltered,
      boolean addPreview) {

    final double[] alsGpt = asymmetricLeastSquaresBaseline(yDataToCorrect, lambda, p, iterations);

    for (int i = 0; i < yDataToCorrect.length; i++) {
      yDataToCorrect[i] = Math.max(0, yDataToCorrect[i] - alsGpt[i]);
    }

    if (addPreview) {
      additionalData.add(
          new AnyXYProvider(Color.BLUE, "baseline", alsGpt.length, j -> xDataToCorrect[j],
              j -> alsGpt[j]));
    }
  }

  @Override
  public BaselineCorrector newInstance(ParameterSet parameters, MemoryMapStorage storage,
      FeatureList flist) {
    final ParameterSet embedded = parameters.getParameter(
        BaselineCorrectionParameters.correctionAlgorithm).getEmbeddedParameters();
    final MinimumSearchFeatureResolver resolver =
        embedded.getValue(AbstractBaselineCorrectorParameters.applyPeakRemoval)
            ? initializeLocalMinResolver((ModularFeatureList) flist) : null;

    return new ALSBaselineCorrector(storage, 5, "bl", resolver, embedded);
  }

  @Override
  public @NotNull String getName() {
    return "Asymmetric least squares (ALS)";
  }

  @Override
  public @Nullable Class<? extends ParameterSet> getParameterSetClass() {
    return ALSBaselineCorrectorParameters.class;
  }
}
