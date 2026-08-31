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

import io.github.mzmine.modules.tools.batchwizard.WizardPart;
import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.modules.tools.batchwizard.subparameters.IonMobilityWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassDetectorWizardOptions;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.IonMobilityWizardParameterFactory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.RealVariable;

class SinglePassParameterEstimationTest {

  @Test
  void mobilityStartsAtTheSelectedWizardPresetDefault() {
    final List<IonMobilityWizardParameterFactory> presets = List.of(
        IonMobilityWizardParameterFactory.TIMS, IonMobilityWizardParameterFactory.IMS,
        IonMobilityWizardParameterFactory.DTIMS, IonMobilityWizardParameterFactory.TWIMS,
        IonMobilityWizardParameterFactory.SLIM);

    for (final IonMobilityWizardParameterFactory preset : presets) {
      final WizardSequence sequence = new WizardSequence();
      sequence.set(WizardPart.IMS, preset.create());
      final WizardParameterSolutionBuilder builder = new WizardParameterSolutionBuilder(null,
          MassDetectorWizardOptions.ABSOLUTE_NOISE_LEVEL, false);
      final Map<String, Double> estimates = SinglePassParameterEstimation.estimate(List.of(),
          builder, sequence);

      final double expected = preset.create()
          .getValue(IonMobilityWizardParameters.approximateImsFWHM);
      Assertions.assertEquals(expected,
          estimates.get(OptimizationParameterRegistry.MOBILITY_FWHM_NAME));

      final ParameterSolutionPrototype prototype = OptimizationParameterRegistry.forSequence(
              sequence).stream().filter(
              candidate -> OptimizationParameterRegistry.MOBILITY_FWHM_NAME.equals(candidate.name()))
          .findFirst().orElseThrow();
      final RealVariable variable = (RealVariable) prototype.variable().get();
      Assertions.assertTrue(
          expected >= variable.getLowerBound() && expected <= variable.getUpperBound(),
          "%s default %.4g is outside [%.4g, %.4g]".formatted(preset, expected,
              variable.getLowerBound(), variable.getUpperBound()));

      final Solution solution = new Solution(1, 1);
      solution.setVariable(0, variable);
      Assertions.assertTrue(Double.isNaN(variable.getValue()));
      SinglePassParameterEstimation.applyToSolution(solution, estimates);
      Assertions.assertEquals(expected, RealVariable.getReal(solution.getVariable(0)));
    }
  }

  @Test
  void noMobilityDefaultIsAddedWithoutIonMobility() {
    final WizardSequence sequence = new WizardSequence();
    sequence.set(WizardPart.IMS, IonMobilityWizardParameterFactory.NO_IMS.create());
    final WizardParameterSolutionBuilder builder = new WizardParameterSolutionBuilder(null,
        MassDetectorWizardOptions.ABSOLUTE_NOISE_LEVEL, false);
    final Map<String, Double> estimates = SinglePassParameterEstimation.estimate(List.of(), builder,
        sequence);

    Assertions.assertFalse(estimates.containsKey(OptimizationParameterRegistry.MOBILITY_FWHM_NAME));
  }
}
