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

package io.github.mzmine.modules.dataprocessing.featdet_baselinecorrection;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.utils.UniqueIdSupplier;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.Resolver;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.ResolvingDimension;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.minimumsearch.MinimumSearchFeatureResolver;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.mzioresolver.MzioResolvers;
import io.github.mzmine.parameters.ParameterSet;
import java.lang.reflect.InvocationTargetException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum PeakRemoval implements UniqueIdSupplier {
  LOCAL_MIN, WAVELET, NONE;

  public @Nullable Resolver getResolver(ModularFeatureList flist) {

    return switch (this) {
      case LOCAL_MIN ->
          new MinimumSearchFeatureResolver(flist, ResolvingDimension.RETENTION_TIME, 0.5, 0.04,
              0.005, 1, 2.5, Range.closed(0d, 50d), 5);
      case WAVELET -> {
        try {
          ParameterSet param = MzioResolvers.WAVELET.getDefaultBaselineCorrectionParameters(flist);
          yield MzioResolvers.WAVELET.getResolverInstance(param, flist);
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException |
                 NoSuchMethodException | InvocationTargetException e) {
          throw new RuntimeException(
              "Cannot instantiate WaveletResolver. This module is only available in mzio-authored versions of mzmine.");
        }
      }
      default -> null;
    };
  }

  @Override
  public @NotNull String getUniqueID() {
    return switch (this) {
      case LOCAL_MIN -> "local_min";
      case WAVELET -> "wavelet";
      case NONE -> "none";
    };
  }
}
