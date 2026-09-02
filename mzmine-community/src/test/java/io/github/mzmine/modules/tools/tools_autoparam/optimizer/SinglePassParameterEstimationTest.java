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
import io.github.mzmine.modules.tools.batchwizard.subparameters.CustomizationWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.IonInterfaceHplcWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.IonMobilityWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassDetectorWizardOptions;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassSpectrometerWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.ParameterOverride;
import io.github.mzmine.modules.tools.batchwizard.subparameters.custom_parameters.WizardMassDetectorNoiseLevels;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.IonInterfaceWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.IonMobilityWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.MassSpectrometerWizardParameterFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  @Test
  void appliesEstimatedWizardParametersButLeavesMobilityUnchanged() {
    final WizardSequence sequence = new WizardSequence();
    sequence.set(WizardPart.ION_INTERFACE, IonInterfaceWizardParameterFactory.HPLC.create());
    sequence.set(WizardPart.IMS, IonMobilityWizardParameterFactory.TIMS.create());
    sequence.set(WizardPart.MS, MassSpectrometerWizardParameterFactory.QTOF.create());
    final WizardParameterSolutionBuilder builder = new WizardParameterSolutionBuilder(null,
        MassDetectorWizardOptions.ABSOLUTE_NOISE_LEVEL, false);
    final double initialMobilityFwhm = sequence.get(WizardPart.IMS).orElseThrow()
        .getValue(IonMobilityWizardParameters.approximateImsFWHM);
    final Map<String, Double> estimates = Map.ofEntries(Map.entry("FWHM", 0.08d),
        Map.entry("Min consecutive", 6d), Map.entry("MS1 noise level", 500d),
        Map.entry("Min height", 12_345d), Map.entry("MZ tolerance option", 4d),
        Map.entry("Inter sample RT tolerance", 0.12d),
        Map.entry(OptimizationParameterRegistry.MOBILITY_FWHM_NAME, 0.02d));

    SinglePassParameterEstimation.applyToWizardSequence(sequence, estimates, builder);

    final var ionInterface = sequence.get(WizardPart.ION_INTERFACE).orElseThrow();
    Assertions.assertEquals(0.08d,
        ionInterface.getValue(IonInterfaceHplcWizardParameters.approximateChromatographicFWHM)
            .getToleranceInMinutes(), 1e-6);
    Assertions.assertEquals(6,
        ionInterface.getValue(IonInterfaceHplcWizardParameters.minNumberOfDataPoints));
    Assertions.assertEquals(0.12d,
        ionInterface.getValue(IonInterfaceHplcWizardParameters.interSampleRTTolerance)
            .getToleranceInMinutes(), 1e-6);

    final var massSpectrometer = sequence.get(WizardPart.MS).orElseThrow();
    final WizardMassDetectorNoiseLevels noise = massSpectrometer.getValue(
        MassSpectrometerWizardParameters.massDetectorOption);
    Assertions.assertEquals(MassDetectorWizardOptions.ABSOLUTE_NOISE_LEVEL, noise.getValueType());
    Assertions.assertEquals(500d, noise.getMs1NoiseLevel());
    Assertions.assertEquals(200d, noise.getMsnNoiseLevel());
    Assertions.assertEquals(12_345d,
        massSpectrometer.getValue(MassSpectrometerWizardParameters.minimumFeatureHeight));
    Assertions.assertEquals(WizardParameterSolutionBuilder.ALL_TOLERANCE_OPTIONS[4],
        massSpectrometer.getValue(MassSpectrometerWizardParameters.scanToScanMzTolerance));

    Assertions.assertEquals(initialMobilityFwhm, sequence.get(WizardPart.IMS).orElseThrow()
        .getValue(IonMobilityWizardParameters.approximateImsFWHM));
  }

  @Test
  void appliesBatchEstimatesToCustomizationAndExcludesOptimizedParameters() {
    final WizardSequence sequence = new WizardSequence();
    sequence.set(WizardPart.ION_INTERFACE, IonInterfaceWizardParameterFactory.HPLC.create());
    sequence.set(WizardPart.CUSTOMIZATION, CustomizationWizardParameters.createDefault());
    final WizardParameterSolutionBuilder builder = new WizardParameterSolutionBuilder(null,
        MassDetectorWizardOptions.ABSOLUTE_NOISE_LEVEL, false);

    SinglePassParameterEstimation.applyToWizardSequence(sequence,
        Map.of("Top-to-edge ratio", 1.7d, "Chrom. Threshold", 0.85d), builder,
        Set.of("Top-to-edge ratio"));

    final var customization = sequence.get(WizardPart.CUSTOMIZATION).orElseThrow();
    Assertions.assertTrue(customization.getValue(CustomizationWizardParameters.enabled));
    List<ParameterOverride> overrides = customization.getValue(
        CustomizationWizardParameters.overrides);
    Assertions.assertEquals(1, overrides.size());
    final ParameterOverride override = overrides.getFirst();
    Assertions.assertEquals("Chromatographic threshold", override.parameterWithValue().getName());
    Assertions.assertEquals(0.85d, override.parameterWithValue().getValue());

    SinglePassParameterEstimation.applyToWizardSequence(sequence,
        Map.of("Top-to-edge ratio", 1.9d), builder);

    overrides = customization.getValue(CustomizationWizardParameters.overrides);
    Assertions.assertEquals(2, overrides.size());
    final ParameterOverride topToEdge = overrides.stream()
        .filter(candidate -> "Min ratio of peak top/edge".equals(
            candidate.parameterWithValue().getName())).findFirst().orElseThrow();
    Assertions.assertEquals(1.9d, topToEdge.parameterWithValue().getValue());
  }

  @Test
  void optimizedWizardValueOverridesEstimateWithoutDroppingOtherEstimates() {
    final WizardSequence sequence = new WizardSequence();
    sequence.set(WizardPart.ION_INTERFACE, IonInterfaceWizardParameterFactory.HPLC.create());
    final WizardParameterSolutionBuilder builder = new WizardParameterSolutionBuilder(null,
        MassDetectorWizardOptions.ABSOLUTE_NOISE_LEVEL, false);
    final WizardParameterSolution optimizedFwhm = builder.buildFwhmSolution(0);
    final Solution optimizedSolution = new Solution(1, 0);
    optimizedFwhm.applyToSolution(optimizedSolution);
    ((RealVariable) optimizedSolution.getVariable(0)).setValue(0.04d);

    SinglePassParameterEstimation.applyToWizardSequence(sequence,
        Map.of("FWHM", 0.08d, "Min consecutive", 6d), builder);
    sequence.get(optimizedFwhm.part()).ifPresent(
        step -> optimizedFwhm.setToParameters().accept(step, optimizedSolution,
            optimizedFwhm.index()));

    final var ionInterface = sequence.get(WizardPart.ION_INTERFACE).orElseThrow();
    Assertions.assertEquals(0.04d,
        ionInterface.getValue(IonInterfaceHplcWizardParameters.approximateChromatographicFWHM)
            .getToleranceInMinutes(), 1e-6);
    Assertions.assertEquals(6,
        ionInterface.getValue(IonInterfaceHplcWizardParameters.minNumberOfDataPoints));
  }
}
