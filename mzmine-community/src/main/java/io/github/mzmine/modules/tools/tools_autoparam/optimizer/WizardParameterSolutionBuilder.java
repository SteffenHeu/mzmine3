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

import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.modules.tools.batchwizard.WizardPart;
import io.github.mzmine.modules.tools.batchwizard.subparameters.IonInterfaceHplcWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassDetectorWizardOptions;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassSpectrometerWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.custom_parameters.WizardMassDetectorNoiseLevels;
import io.github.mzmine.modules.tools.tools_autoparam.DataFileStatistics;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.WizardParameterSolution.DoubleWizardParameterSolution;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.WizardParameterSolution.IntegerWizardParameterSolution;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance.Unit;
import io.github.mzmine.taskcontrol.SimpleRunnableTask;
import io.github.mzmine.util.MathUtils;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.moeaframework.core.variable.BinaryIntegerVariable;
import org.moeaframework.core.variable.RealVariable;

public class WizardParameterSolutionBuilder {

  public static final MZTolerance[] ALL_TOLERANCE_OPTIONS = new MZTolerance[]{ //
      new MZTolerance(0.0005, 2), //
      new MZTolerance(0.001, 5), //
      new MZTolerance(0.003, 7), //
      new MZTolerance(0.005, 15), //
      new MZTolerance(0.008, 15), //
      new MZTolerance(0.01, 20), //
      new MZTolerance(0.015, 25), //
      new MZTolerance(0.02, 25), //
      new MZTolerance(0.05, 25)}; //
  private static final Logger logger = Logger.getLogger(
      WizardParameterSolutionBuilder.class.getName());
  private final MZTolerance[] availableTolerances;

  private final @Nullable List<DataFileStatistics> stats;
  private final double minFwhm;
  private final double maxFwhm;
  private final double minMinDp;
  private final double maxMinDp;
  private final double minNoiseLevel;
  private final double maxNoiseLevel;
  private final double minMinHeight;
  private final double maxMinHeight;
  private final double minRtSampleToSampleTol;
  private final double maxRtSampleToSampleTol;
  private @NotNull
  final MassDetectorWizardOptions massDetectorType;

  public WizardParameterSolutionBuilder(final @Nullable List<DataFileStatistics> dataFileStatistics,
      @Nullable MassDetectorWizardOptions massDetectorType) {
    this.stats = dataFileStatistics;

    // just needed for the decision if we use factor of lowest or absolute
    if (massDetectorType != null) {
      this.massDetectorType = massDetectorType;
    } else if (dataFileStatistics != null) {
      this.massDetectorType = stats.stream().map(DataFileStatistics::file).anyMatch(file -> {
        if (file instanceof IMSRawDataFile) {
          return false;
        }
        return file.getScans().stream()
            .anyMatch(scan -> scan.getMSLevel() == 1 && scan.hasInjectionTime());
      }) ? MassDetectorWizardOptions.FACTOR_OF_LOWEST_SIGNAL
          : MassDetectorWizardOptions.ABSOLUTE_NOISE_LEVEL;
    } else {
      throw new IllegalArgumentException(
          "Data file statistics must be given or ms type must be set.");
    }

    if (stats != null) {
      double[] array = stats.stream().map(DataFileStatistics::getIsotopePeakFwhms)
          .flatMapToDouble(Arrays::stream).sorted().toArray();
      minFwhm = MathUtils.calcQuantileSorted(array, 0.05);
      maxFwhm = MathUtils.calcQuantileSorted(array, 0.95);

      array = stats.stream().map(DataFileStatistics::getNumberOfLowestIsotopeDataPoints)
          .flatMapToInt(Arrays::stream).mapToDouble(i -> (i * 0.5)).sorted().toArray();
      minMinDp = MathUtils.calcQuantileSorted(array, 0.05);
      // some peaks may be super long, so use a lower quantile
      maxMinDp = Math.max(minMinDp * 2, MathUtils.calcQuantileSorted(array, 0.8));

      array = stats.stream().map(DataFileStatistics::getEdgeIntensities)
          .flatMapToDouble(Arrays::stream).sorted().toArray();
      minNoiseLevel = switch (this.massDetectorType) {
        case FACTOR_OF_LOWEST_SIGNAL -> 3;
        case ABSOLUTE_NOISE_LEVEL -> MathUtils.calcQuantileSorted(array, 0.05);
      };
      maxNoiseLevel = switch (this.massDetectorType) {
        case FACTOR_OF_LOWEST_SIGNAL -> 15;
        case ABSOLUTE_NOISE_LEVEL -> MathUtils.calcQuantileSorted(array, 0.95);
      };

      array = stats.stream().map(DataFileStatistics::getLowestIsotopeHeights)
          .flatMapToDouble(Arrays::stream).sorted().toArray();
      minMinHeight = MathUtils.calcQuantileSorted(array, 0.05);
      maxMinHeight = MathUtils.calcQuantileSorted(array, 0.95);

      final ModularFeatureList aligned = OptimizationUtils.alignBenchmarkFeatures(stats, null,
          new SimpleRunnableTask(() -> {
          }));
      minRtSampleToSampleTol = OptimizationUtils.extractSampleToSampleRtTolerances(aligned,
          (int) (stats.size() * 0.8), 0.0f).getTolerance();
      maxRtSampleToSampleTol = OptimizationUtils.extractSampleToSampleRtTolerances(aligned,
          (int) (stats.size() * 0.8), 1f).getTolerance();

    } else {

      minFwhm = 0.005;
      maxFwhm = 0.1;
      minMinDp = 4;
      maxMinDp = 10;
      minNoiseLevel = switch (this.massDetectorType) {
        case FACTOR_OF_LOWEST_SIGNAL -> 3;
        case ABSOLUTE_NOISE_LEVEL -> 10;
      };
      maxNoiseLevel = switch (this.massDetectorType) {
        case FACTOR_OF_LOWEST_SIGNAL -> 15;
        case ABSOLUTE_NOISE_LEVEL -> 1E6;
      };
      minMinHeight = 100;
      maxMinHeight = 1E8;
      minRtSampleToSampleTol = 0.01;
      maxRtSampleToSampleTol = 0.2;
    }

    availableTolerances = switch (this.massDetectorType) {
      case FACTOR_OF_LOWEST_SIGNAL -> Arrays.copyOfRange(ALL_TOLERANCE_OPTIONS, 1, 5);
      case ABSOLUTE_NOISE_LEVEL -> Arrays.copyOfRange(ALL_TOLERANCE_OPTIONS, 2, 7);
    };

    logger.info("Mass detector type: " + this.massDetectorType);
    logger.info("MS1 noise level range: " + minNoiseLevel + ", " + maxNoiseLevel);
    logger.info("Minimum height range: " + minMinHeight + ", " + maxMinHeight);
    logger.info("Minimum data points range: " + minMinDp + ", " + maxMinDp);
    logger.info("FWHM range: " + minFwhm + ", " + maxFwhm);
    logger.info(
        "Rt inter sample tol range: " + minRtSampleToSampleTol + ", " + maxRtSampleToSampleTol);
  }

  public @NotNull WizardParameterSolution buildMinHeightSolution(int index) {
    WizardParameterSolution minHeight = new DoubleWizardParameterSolution(index, WizardPart.MS,
        MassSpectrometerWizardParameters.minimumFeatureHeight,
        () -> new RealVariable("Min height", minMinHeight, maxMinHeight));
    return minHeight;
  }

  public @NotNull WizardParameterSolution buildScanToScanToleranceSolution(int index) {
    WizardParameterSolution scanToScanTolerance = new IntegerWizardParameterSolution(index,
        WizardPart.MS, (stepParam, sol, id) -> stepParam.setParameter(
        MassSpectrometerWizardParameters.scanToScanMzTolerance,
        availableTolerances[BinaryIntegerVariable.getInt(sol.getVariable(id))]),
        () -> new BinaryIntegerVariable("MZ tolerance option", 0, availableTolerances.length - 1));
    return scanToScanTolerance;
  }

  public @NotNull WizardParameterSolution buildFwhmSolution(int index) {
    return new DoubleWizardParameterSolution(index, WizardPart.ION_INTERFACE,
        (param, sol, id) -> param.setParameter(
            IonInterfaceHplcWizardParameters.approximateChromatographicFWHM,
            new RTTolerance((float) RealVariable.getReal(sol.getVariable(id)), Unit.MINUTES)),
        () -> new RealVariable("FWHM", minFwhm, maxFwhm));
  }


  public @NotNull WizardParameterSolution buildMaxPeaksSolution(int index) {
    WizardParameterSolution maxPeaks = new IntegerWizardParameterSolution(index,
        WizardPart.ION_INTERFACE, IonInterfaceHplcWizardParameters.maximumIsomersInChromatogram,
        () -> new BinaryIntegerVariable("Max peaks", 5, 100));
    return maxPeaks;
  }

  public @NotNull WizardParameterSolution buildMinConsecutiveSolution(int index) {
    return new IntegerWizardParameterSolution(index, WizardPart.ION_INTERFACE,
        IonInterfaceHplcWizardParameters.minNumberOfDataPoints,
        () -> new BinaryIntegerVariable("Min consecutive", (int) minMinDp, (int) maxMinDp));
  }

//  public @NotNull WizardParameterSolution buildTopToEdgeSolution(int index) {
//    return new DoubleWizardParameterSolution(index, WizardPart.,
//        IonInterfaceHplcWizardParameters.minNumberOfDataPoints,
//        () -> new RealVariable("Top-to-Edge", 1.2, 4));
//  }

  public @NotNull WizardParameterSolution buildMs1NoiseSolution(int index) {
    final Supplier<RealVariable> var = switch (massDetectorType) {
      case FACTOR_OF_LOWEST_SIGNAL -> () -> new RealVariable("MS1 noise level", 3, 15);
      case ABSOLUTE_NOISE_LEVEL ->
          () -> new RealVariable("MS1 noise level", minNoiseLevel, maxNoiseLevel);
    };

    return new DoubleWizardParameterSolution(index, WizardPart.MS, (stepParam, solution, id) -> {
      stepParam.setParameter(MassSpectrometerWizardParameters.massDetectorOption,
          new WizardMassDetectorNoiseLevels(massDetectorType,
              RealVariable.getReal(solution.getVariable(id)),
              RealVariable.getReal(solution.getVariable(id)) / 2.5));
    }, var);
  }

  public @NotNull MassDetectorWizardOptions getMassDetectorType() {
    return massDetectorType;
  }

  public @NotNull WizardParameterSolution buildSampleToSampleRtTolSolution(int index) {
    return new DoubleWizardParameterSolution(index, WizardPart.ION_INTERFACE,
        (param, solution, id) -> {
          param.setParameter(IonInterfaceHplcWizardParameters.interSampleRTTolerance,
              new RTTolerance((float) ((RealVariable) solution.getVariable(id)).getValue(),
                  Unit.MINUTES));
        }, () -> new RealVariable("Inter sample RT tolerance", minRtSampleToSampleTol,
        maxRtSampleToSampleTol));
  }
}
