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
import io.github.mzmine.modules.tools.batchwizard.subparameters.WizardStepParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.IonInterfaceWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.IonMobilityWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.MassSpectrometerWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.WizardParameterFactory;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.ParameterSolutionPrototype.BatchParameterSolutionPrototype;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.ParameterSolutionPrototype.WizardParameterSolutionPrototype;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.WizardParameterSolution.DoubleWizardParameterSolution;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.moeaframework.core.variable.RealVariable;

/**
 * Defines which wizard and batch parameters are exposed to automatic optimization. Keeping this
 * registry in the optimizer package prevents general wizard presets from depending on optimizer
 * implementation types.
 */
final class OptimizationParameterRegistry {

  static final String MOBILITY_FWHM_NAME = "FWHM (mobility)";

  private static final WizardParameterSolutionBuilder DUMMY_BUILDER = new WizardParameterSolutionBuilder(
      null, MassDetectorWizardOptions.ABSOLUTE_NOISE_LEVEL, false);
  private static final List<ParameterSolutionPrototype> OPTIONAL_WAVELET_SOLUTIONS = waveletSolutions();
  private static final List<ParameterSolutionPrototype> ALL_SOLUTIONS = createAllSolutions();
  private static final List<ParameterSolutionPrototype> DEFAULT_SOLUTIONS = ALL_SOLUTIONS.stream()
      .filter(solution -> !OPTIONAL_WAVELET_SOLUTIONS.contains(solution)).toList();

  private OptimizationParameterRegistry() {
  }

  static @NotNull List<ParameterSolutionPrototype> allSolutions() {
    return ALL_SOLUTIONS;
  }

  static @NotNull List<ParameterSolutionPrototype> defaultSolutions() {
    return DEFAULT_SOLUTIONS;
  }

  static @NotNull List<ParameterSolutionPrototype> forSequence(@NotNull WizardSequence sequence) {
    return sequence.stream().map(WizardStepParameters::getFactory)
        .flatMap(factory -> forFactory(factory).stream()).distinct()
        .sorted(Comparator.comparing(ParameterSolutionPrototype::name)).toList();
  }

  private static @NotNull List<ParameterSolutionPrototype> createAllSolutions() {
    final Set<ParameterSolutionPrototype> solutions = new HashSet<>(massSpectrometerSolutions());
    for (final IonInterfaceWizardParameterFactory factory : IonInterfaceWizardParameterFactory.values()) {
      solutions.addAll(interfaceSolutions(factory));
    }
    for (final IonMobilityWizardParameterFactory factory : IonMobilityWizardParameterFactory.values()) {
      solutions.addAll(mobilitySolutions(factory));
    }
    return solutions.stream().sorted(Comparator.comparing(ParameterSolutionPrototype::name))
        .toList();
  }

  private static @NotNull List<ParameterSolutionPrototype> forFactory(
      @NotNull WizardParameterFactory factory) {
    if (factory instanceof MassSpectrometerWizardParameterFactory) {
      return massSpectrometerSolutions();
    }
    if (factory instanceof IonInterfaceWizardParameterFactory ionInterface) {
      return interfaceSolutions(ionInterface);
    }
    if (factory instanceof IonMobilityWizardParameterFactory ionMobility) {
      return mobilitySolutions(ionMobility);
    }
    return List.of();
  }

  private static @NotNull List<ParameterSolutionPrototype> massSpectrometerSolutions() {
    return List.of(
        new WizardParameterSolutionPrototype(DUMMY_BUILDER.buildMs1NoiseSolution(-1).variable(),
            WizardParameterSolutionBuilder::buildMs1NoiseSolution),
        new WizardParameterSolutionPrototype(
            DUMMY_BUILDER.buildScanToScanToleranceSolution(-1).variable(),
            WizardParameterSolutionBuilder::buildScanToScanToleranceSolution),
        new WizardParameterSolutionPrototype(DUMMY_BUILDER.buildMinHeightSolution(-1).variable(),
            WizardParameterSolutionBuilder::buildMinHeightSolution));
  }

  private static @NotNull List<ParameterSolutionPrototype> interfaceSolutions(
      @NotNull IonInterfaceWizardParameterFactory factory) {
    return switch (factory) {
      case HPLC, UHPLC, HILIC, GC_CI -> List.of(
          new WizardParameterSolutionPrototype(DUMMY_BUILDER.buildFwhmSolution(-1).variable(),
              WizardParameterSolutionBuilder::buildFwhmSolution),
          new WizardParameterSolutionPrototype(
              DUMMY_BUILDER.buildMinConsecutiveSolution(-1).variable(),
              WizardParameterSolutionBuilder::buildMinConsecutiveSolution),
          new WizardParameterSolutionPrototype(
              DUMMY_BUILDER.buildSampleToSampleRtTolSolution(-1).variable(),
              WizardParameterSolutionBuilder::buildSampleToSampleRtTolSolution),
          new BatchParameterSolutionPrototype(BatchParameterSolutionBuilder::buildTopToEdgeRatio),
          new BatchParameterSolutionPrototype(BatchParameterSolutionBuilder::buildChromThreshold));
      case LC_WAVELET -> {
        final List<ParameterSolutionPrototype> solutions = new ArrayList<>(List.of(
            new WizardParameterSolutionPrototype(
                DUMMY_BUILDER.buildMinConsecutiveSolution(-1).variable(),
                WizardParameterSolutionBuilder::buildMinConsecutiveSolution),
            new WizardParameterSolutionPrototype(
                DUMMY_BUILDER.buildSampleToSampleRtTolSolution(-1).variable(),
                WizardParameterSolutionBuilder::buildSampleToSampleRtTolSolution)));
        solutions.addAll(OPTIONAL_WAVELET_SOLUTIONS);
        yield List.copyOf(solutions);
      }
      case GC_EI -> List.of(
          new WizardParameterSolutionPrototype(DUMMY_BUILDER.buildFwhmSolution(-1).variable(),
              WizardParameterSolutionBuilder::buildFwhmSolution),
          new WizardParameterSolutionPrototype(
              DUMMY_BUILDER.buildMinConsecutiveSolution(-1).variable(),
              WizardParameterSolutionBuilder::buildMinConsecutiveSolution),
          new WizardParameterSolutionPrototype(
              DUMMY_BUILDER.buildSampleToSampleRtTolSolution(-1).variable(),
              WizardParameterSolutionBuilder::buildSampleToSampleRtTolSolution));
      case MALDI, LDI, DESI, SIMS, DIRECT_INFUSION, FLOW_INJECT -> List.of();
    };
  }

  private static @NotNull List<ParameterSolutionPrototype> mobilitySolutions(
      @NotNull IonMobilityWizardParameterFactory factory) {
    return switch (factory) {
      case TIMS -> List.of(mobilitySolution(0.003, 0.02));
      case IMS -> List.of(mobilitySolution(0.01, 5));
      case DTIMS, TWIMS -> List.of(mobilitySolution(0.1, 5));
      case SLIM -> List.of(mobilitySolution(1, 20));
      case NO_IMS -> List.of();
    };
  }

  private static @NotNull List<ParameterSolutionPrototype> waveletSolutions() {
    return List.of(
        new BatchParameterSolutionPrototype(WaveletBatchParameterSolutionBuilder::buildWaveletSnr),
        new BatchParameterSolutionPrototype(
            WaveletBatchParameterSolutionBuilder::buildWaveletNoiseCalculation),
        new BatchParameterSolutionPrototype(
            WaveletBatchParameterSolutionBuilder::buildWaveletBaselineMethod));
  }

  private static @NotNull ParameterSolutionPrototype mobilitySolution(double lowerBound,
      double upperBound) {
    return new WizardParameterSolutionPrototype(
        () -> new RealVariable(MOBILITY_FWHM_NAME, lowerBound, upperBound),
        (_, index) -> new DoubleWizardParameterSolution(index, WizardPart.IMS,
            IonMobilityWizardParameters.approximateImsFWHM,
            () -> new RealVariable(MOBILITY_FWHM_NAME, lowerBound, upperBound)));
  }
}
