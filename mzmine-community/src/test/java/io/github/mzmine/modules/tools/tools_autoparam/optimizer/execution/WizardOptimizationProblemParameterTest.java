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

package io.github.mzmine.modules.tools.tools_autoparam.optimizer.execution;

import io.github.mzmine.modules.tools.batchwizard.WizardPart;
import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.modules.tools.batchwizard.subparameters.IonInterfaceHplcWizardParameters;
import io.github.mzmine.modules.tools.tools_autoparam.estimation.ParameterDefinition;
import io.github.mzmine.modules.tools.tools_autoparam.estimation.ParameterEstimationContext;
import io.github.mzmine.modules.tools.tools_autoparam.estimation.ParameterEstimationTestData;
import io.github.mzmine.modules.tools.tools_autoparam.estimation.PreparedParameterSet;
import io.github.mzmine.modules.tools.tools_autoparam.estimation.domain.OrdinalIntegerVariable;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.OptimizerParameters;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.metrics.SweepMetric;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.search.OptimizerOptions;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.search.WarmStartInitialization;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.search.WarmStartSampling;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.RealVariable;
import testutils.MZmineTestUtil;

class WizardOptimizationProblemParameterTest {

  @BeforeAll
  static void initialize() {
    MZmineTestUtil.startMzmineCore();
  }

  private static @NotNull ParameterEstimationContext context() {
    final WizardSequence sequence = ParameterEstimationTestData.sequence();
    for (final WizardPart part : WizardPart.values()) {
      if (sequence.get(part).isEmpty()) {
        sequence.set(part, part.getDefaultPresets()[0].create());
      }
    }
    return ParameterEstimationTestData.context(sequence);
  }

  private static @NotNull WizardOptimizationProblem problem(
      @NotNull ParameterEstimationContext context, @NotNull PreparedParameterSet prepared,
      @NotNull List<ParameterDefinition<?>> selected) {
    final ParameterSet parameters = OptimizerParameters.create(
        List.of(SweepMetric.IPO_ISOTOPE_SCORE), 30);
    parameters.setParameter(OptimizerParameters.paramToOptimize, selected);
    return new WizardOptimizationProblem(context, prepared, parameters,
        new AtomicReference<>(TaskStatus.PROCESSING), 30, () -> false);
  }

  @Test
  void moeadCreatesAnObjectiveForEachSelectedMetric() {
    final OptimizerParameters parameters = new OptimizerParameters();
    OptimizerParameters.setOptimizerAndTargets(parameters, OptimizerOptions.MOEAD,
        List.of(SweepMetric.IPO_ISOTOPE_SCORE, SweepMetric.SLAW_INTEGRATION_SCORE));
    Assertions.assertEquals(2,
        WizardOptimizationProblem.calculateNumberOfObjectives(parameters, null));
  }

  @Test
  void selectedAndUnselectedRtUseTheSamePreparedEstimate() {
    final ParameterEstimationContext context = context();
    final PreparedParameterSet prepared = PreparedParameterSet.prepare(context);
    final double expected = ParameterEstimationTestData.INTER_SAMPLE_RT.prepare(context)
        .initialValue().getToleranceInMinutes();

    for (final List<ParameterDefinition<?>> selection : List.<List<ParameterDefinition<?>>>of(
        List.of(ParameterEstimationTestData.MINIMUM_FEATURE_HEIGHT),
        List.of(ParameterEstimationTestData.INTER_SAMPLE_RT))) {
      final WizardOptimizationProblem problem = problem(context, prepared, selection);
      final WizardSequence applied = problem.createWizardSequenceFromSolution(
          problem.newSolution());
      Assertions.assertEquals(expected, applied.get(WizardPart.ION_INTERFACE).orElseThrow()
          .getValue(IonInterfaceHplcWizardParameters.interSampleRTTolerance)
          .getToleranceInMinutes());
    }
  }

  @Test
  void warmStartsUseTheBoundInitialValuesForEverySamplingStrategy() {
    final ParameterEstimationContext context = context();
    final PreparedParameterSet prepared = PreparedParameterSet.prepare(context);
    final WizardOptimizationProblem problem = problem(context, prepared,
        List.of(ParameterEstimationTestData.MINIMUM_CONSECUTIVE_SCANS,
            ParameterEstimationTestData.MINIMUM_FEATURE_HEIGHT,
            ParameterEstimationTestData.MZ_TOLERANCE));
    final Solution initial = problem.newSolution();
    for (final WarmStartSampling sampling : WarmStartSampling.values()) {
      final List<Solution> solutions = WarmStartInitialization.createSolutions(problem, 8,
          sampling);
      Assertions.assertEquals(8, solutions.size());
      for (int i = 0; i < initial.getNumberOfVariables(); i++) {
        Assertions.assertEquals(OrdinalIntegerVariable.effectiveValue(initial, i),
            OrdinalIntegerVariable.effectiveValue(solutions.getFirst(), i));
        for (final Solution solution : solutions) {
          final RealVariable variable = (RealVariable) solution.getVariable(i);
          Assertions.assertTrue(Double.isFinite(variable.getValue()));
          Assertions.assertTrue(variable.getValue() >= variable.getLowerBound());
          Assertions.assertTrue(variable.getValue() <= variable.getUpperBound());
        }
      }
    }
  }
}
