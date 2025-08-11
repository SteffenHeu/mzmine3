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

import io.github.mzmine.modules.tools.batchwizard.WizardPart;
import io.github.mzmine.modules.tools.batchwizard.subparameters.WizardStepParameters;
import io.github.mzmine.parameters.UserParameter;
import java.util.function.Supplier;
import org.apache.commons.lang3.function.TriConsumer;
import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.BinaryIntegerVariable;
import org.moeaframework.core.variable.RealVariable;
import org.moeaframework.core.variable.Variable;

public sealed interface WizardParameterSolution {

  int index();

  WizardPart part();

  TriConsumer<WizardStepParameters, Solution, Integer> setToParameters();

  Supplier<? extends Variable> variable();

  record IntegerWizardParameterSolution(int index, WizardPart part,
                                        TriConsumer<WizardStepParameters, Solution, Integer> setToParameters,
                                        Supplier<BinaryIntegerVariable> variable) implements
      WizardParameterSolution {

    public IntegerWizardParameterSolution(int index, WizardPart part, UserParameter param,
        Supplier<BinaryIntegerVariable> variable) {
      this(index, part, (set, solution, id) -> set.setParameter(param,
          BinaryIntegerVariable.getInt(solution.getVariable(id))), variable);
    }
  }

  record DoubleWizardParameterSolution(int index, WizardPart part,
                                       TriConsumer<WizardStepParameters, Solution, Integer> setToParameters,
                                       Supplier<RealVariable> variable) implements
      WizardParameterSolution {

    public DoubleWizardParameterSolution(int index, WizardPart part, UserParameter param,
        Supplier<RealVariable> variable) {
      this(index, part, (set, solution, id) -> set.setParameter(param,
          RealVariable.getReal(solution.getVariable(id))), variable);
    }
  }
}
