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

import io.github.mzmine.modules.MZmineProcessingModule;
import io.github.mzmine.modules.tools.batchwizard.subparameters.ApplicationScope;
import io.github.mzmine.modules.tools.batchwizard.subparameters.ParameterOverride;
import io.github.mzmine.parameters.UserParameter;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.RealVariable;
import org.moeaframework.core.variable.Variable;

public sealed interface BatchParameterSolution {

  int index();

  ParameterOverride toParameterOverride(Solution solution);

  Supplier<? extends Variable> variable();

  /**
   * Applies this parameter's variable to the given solution at the correct index.
   */
  default void applyToSolution(@NotNull Solution solution) {
    solution.setVariable(index(), variable().get());
  }

  public final record DoubleBatchParameterSolution(
      @NotNull Class<? extends MZmineProcessingModule> module, UserParameter<Double, ?> param,
      int index, ApplicationScope scope, Supplier<? extends Variable> variable) implements
      BatchParameterSolution {

    @Override
    public ParameterOverride toParameterOverride(Solution solution) {
      final double value = ((RealVariable) solution.getVariable(index)).getValue();
      final UserParameter<Double, ?> cloned = param.cloneParameter();
      cloned.setValue(value);
      return new ParameterOverride(module.getName(), module.getSimpleName(), cloned, scope);
    }
  }

  /**
   * A general-purpose {@link BatchParameterSolution} that delegates override creation to a
   * {@link Function}. Suitable for parameters that require reflection to set (e.g. optional modules
   * not available at compile time).
   *
   * @param index           position in the MOEA solution vector
   * @param variable        supplier for the optimization variable (determines search range and
   *                        type)
   * @param overrideFactory function that reads the solution variable at {@code index} and produces
   *                        the {@link ParameterOverride} to apply
   */
  public final record FunctionalBatchParameterSolution(int index,
                                                       Supplier<? extends Variable> variable,
                                                       Function<Solution, ParameterOverride> overrideFactory) implements
      BatchParameterSolution {

    @Override
    public @NotNull ParameterOverride toParameterOverride(@NotNull Solution solution) {
      return overrideFactory.apply(solution);
    }
  }
}
