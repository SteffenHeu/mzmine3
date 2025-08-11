/*
 * Copyright (c) 2004-2025 The mzmine Development Team
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
import io.github.mzmine.modules.batchmode.BatchQueue;
import io.github.mzmine.modules.tools.batchwizard.postsetter.ModuleParameterPostSetter;
import io.github.mzmine.modules.tools.batchwizard.postsetter.ModuleSelectionRule;
import io.github.mzmine.parameters.UserParameter;
import java.util.function.Supplier;
import org.apache.commons.lang3.function.TriConsumer;
import org.jetbrains.annotations.NotNull;
import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.RealVariable;
import org.moeaframework.core.variable.Variable;

public sealed interface BatchParameterSolution {

  int index();

  TriConsumer<BatchQueue, Solution, Integer> setToQueue();

  Supplier<? extends Variable> variable();

  public final record DoubleBatchParameterSolution(
      @NotNull Class<? extends MZmineProcessingModule> module, UserParameter<Double, ?> param,
      int index, ModuleSelectionRule rule, Supplier<? extends Variable> variable) implements
      BatchParameterSolution {

    @Override
    public TriConsumer<BatchQueue, Solution, Integer> setToQueue() {
      return (q, solution, index) -> {
        final RealVariable variable = (RealVariable) solution.getVariable(index);
        final double value = variable.getValue();

        final ModuleParameterPostSetter<Double> setter = new ModuleParameterPostSetter<>(module,
            param, value, rule);
        setter.apply(q);
      };
    }
  }
}
