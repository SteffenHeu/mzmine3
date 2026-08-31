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

package io.github.mzmine.modules.tools.tools_autoparam.optimizer;

import io.github.mzmine.modules.tools.batchwizard.subparameters.ApplicationScope;
import io.github.mzmine.modules.tools.batchwizard.subparameters.ParameterOverride;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.BatchParameterSolution.FunctionalBatchParameterSolution;
import io.github.mzmine.parameters.UserParameter;
import org.jetbrains.annotations.NotNull;
import org.moeaframework.core.variable.RealVariable;

/**
 * Factory methods for {@link BatchParameterSolution} instances targeting the optional
 * {@code WaveletResolverModule} (mzio project). All parameter access uses reflection to avoid a
 * compile-time dependency on mzio.
 *
 * <p>These solutions should only be selected by the user when the WaveletResolverModule is
 * installed, otherwise an exception is thrown during optimization.
 */
public final class WaveletBatchParameterSolutionBuilder {

  static final String WAVELET_MODULE_CLASS = "io.mzio.mzminepro.modules.featdet_resolving.wavelet.WaveletResolverModule";
  static final String WAVELET_MODULE_SIMPLE = "WaveletResolverModule";
  static final String WAVELET_PARAMS_CLASS = "io.mzio.mzminepro.modules.featdet_resolving.wavelet.WaveletResolverParameters";
  // nested class: binary name uses '$' separator
  static final String NOISE_CALCULATION_CLASS = "io.mzio.mzminepro.modules.featdet_resolving.wavelet.WaveletResolverParameters$NoiseCalculation";
  static final String BASELINE_ESTIMATION_CLASS = "io.mzio.mzminepro.modules.featdet_resolving.wavelet.BaselineEstimation";

  private WaveletBatchParameterSolutionBuilder() {
  }

  /**
   * Optimizes {@code WaveletResolverParameters.snr} (double, range 2–20).
   */
  public static @NotNull BatchParameterSolution buildWaveletSnr(int index) {
    return new FunctionalBatchParameterSolution(index,
        () -> new RealVariable("Wavelet SNR threshold", 3, 10), solution -> {
      try {
        final double value = ((RealVariable) solution.getVariable(index)).getValue();
        return makeTopLevelDoubleOverride("snr", value);
      } catch (Exception e) {
        throw new RuntimeException("Failed to build wavelet SNR override", e);
      }
    });
  }

  /**
   * Optimizes {@code WaveletResolverParameters.noiseCalculation} (enum, 2 values).
   */
  public static @NotNull BatchParameterSolution buildWaveletNoiseCalculation(int index) {
    return new FunctionalBatchParameterSolution(index,
        // assumption: NoiseCalculation has exactly 2 values (STANDARD_DEVIATION, MEDIAN_ABSOLUTE_DEVIATION)
        () -> new OrdinalIntegerVariable("Wavelet noise calculation", 0, 1), solution -> {
      try {
        final int idx = OrdinalIntegerVariable.getInt(solution, index);
        final Object value = getEnumValue(NOISE_CALCULATION_CLASS, idx);
        return makeTopLevelEnumOverride("noiseCalculation", value);
      } catch (Exception e) {
        throw new RuntimeException("Failed to build wavelet noise calculation override", e);
      }
    });
  }

  /**
   * Optimizes {@code WaveletResolverParameters.baselineMethod} (enum, 3 values).
   */
  public static @NotNull BatchParameterSolution buildWaveletBaselineMethod(int index) {
    return new FunctionalBatchParameterSolution(index,
        // assumption: BaselineEstimation has exactly 3 values (EDGE_AVERAGE, MEDIAN, AVERAGE)
        () -> new OrdinalIntegerVariable("Wavelet baseline method", 0, 2), solution -> {
      try {
        final int idx = OrdinalIntegerVariable.getInt(solution, index);
        final Object value = getEnumValue(BASELINE_ESTIMATION_CLASS, idx);
        return makeTopLevelEnumOverride("baselineMethod", value);
      } catch (Exception e) {
        throw new RuntimeException("Failed to build wavelet baseline method override", e);
      }
    });
  }

  /**
   * Loads {@code fieldName} from {@link #WAVELET_PARAMS_CLASS} via reflection, clones it, sets the
   * given double value, and wraps it in a {@link ParameterOverride}.
   */
  @SuppressWarnings("unchecked")
  private static @NotNull ParameterOverride makeTopLevelDoubleOverride(@NotNull String fieldName,
      double value) throws Exception {
    final Class<?> paramsClass = Class.forName(WAVELET_PARAMS_CLASS);
    final UserParameter<Double, ?> param = (UserParameter<Double, ?>) paramsClass.getField(
        fieldName).get(null);
    final UserParameter<Double, ?> cloned = param.cloneParameter();
    cloned.setValue(value);
    return new ParameterOverride(WAVELET_MODULE_CLASS, WAVELET_MODULE_SIMPLE, cloned,
        ApplicationScope.FIRST);
  }

  /**
   * Loads {@code fieldName} from {@link #WAVELET_PARAMS_CLASS} via reflection, clones it, sets the
   * given enum or boolean value (raw types), and wraps it in a {@link ParameterOverride}.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static @NotNull ParameterOverride makeTopLevelEnumOverride(@NotNull String fieldName,
      @NotNull Object value) throws Exception {
    final Class<?> paramsClass = Class.forName(WAVELET_PARAMS_CLASS);
    final UserParameter param = (UserParameter) paramsClass.getField(fieldName).get(null);
    final UserParameter cloned = (UserParameter) param.cloneParameter();
    cloned.setValue(value);
    return new ParameterOverride(WAVELET_MODULE_CLASS, WAVELET_MODULE_SIMPLE, cloned,
        ApplicationScope.FIRST);
  }

  /**
   * Returns the enum constant at position {@code index} in the enum class named
   * {@code enumClassName}.
   */
  private static @NotNull Object getEnumValue(@NotNull String enumClassName, int index)
      throws Exception {
    final Object[] values = (Object[]) Class.forName(enumClassName).getMethod("values")
        .invoke(null);
    return values[index];
  }
}
