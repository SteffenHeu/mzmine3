/*
 * Copyright (c) 2004-2026 The mzmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.mzioresolver;

import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.Resolver;
import io.github.mzmine.parameters.ParameterSet;
import java.lang.reflect.InvocationTargetException;
import org.jetbrains.annotations.NotNull;

public enum MzioResolvers {
  WAVELET("io.mzio.mzminepro.modules.featdet_resolving.wavelet.WaveletResolver",
      "io.mzio.mzminepro.modules.featdet_resolving.wavelet.WaveletResolverParameters");

  private final String className;
  private final String parameterSetClassName;

  MzioResolvers(String className, String parameterSetClassName) {
    this.className = className;
    this.parameterSetClassName = parameterSetClassName;
  }

  public @NotNull Resolver getResolverInstance(@NotNull final ParameterSet parameters,
      @NotNull final FeatureList featureList)
      throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
    return (Resolver) switch (this) {
      case WAVELET -> Class.forName(className)
          .getDeclaredConstructor(ModularFeatureList.class, ParameterSet.class)
          .newInstance(featureList, parameters);
    };
  }

  public @NotNull ParameterSet getDefaultBaselineCorrectionParameters(
      @NotNull final ModularFeatureList flist)
      throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
    return (ParameterSet) switch (this) {
      case WAVELET -> Class.forName(parameterSetClassName)
          .getMethod("getDefaultBaselineCorrectionParameters", ModularFeatureList.class)
          .invoke(null, flist);
    };
  }
}
