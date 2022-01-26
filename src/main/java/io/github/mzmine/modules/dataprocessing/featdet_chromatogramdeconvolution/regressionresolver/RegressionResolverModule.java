package io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.regressionresolver;

import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.FeatureResolverModule;
import io.github.mzmine.parameters.ParameterSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RegressionResolverModule extends FeatureResolverModule {

  @Override
  public @NotNull String getName() {
    return "Regression resolver";
  }

  @Override
  public @Nullable Class<? extends ParameterSet> getParameterSetClass() {
    return RegressionResolverParameters.class;
  }

  @Override
  public @NotNull String getDescription() {
    return "Resolves features based on the feature slope.";
  }
}
