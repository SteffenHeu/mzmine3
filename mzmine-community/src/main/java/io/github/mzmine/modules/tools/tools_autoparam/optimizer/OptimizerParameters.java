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

import static io.github.mzmine.javafx.components.factories.FxTexts.boldText;
import static io.github.mzmine.javafx.components.factories.FxTexts.hyperlinkText;
import static io.github.mzmine.javafx.components.factories.FxTexts.linebreak;
import static io.github.mzmine.javafx.components.factories.FxTexts.text;

import io.github.mzmine.datamodel.features.types.numbers.MZType;
import io.github.mzmine.datamodel.features.types.numbers.MobilityType;
import io.github.mzmine.datamodel.features.types.numbers.RTType;
import io.github.mzmine.javafx.components.factories.FxTextFlows;
import io.github.mzmine.javafx.components.factories.FxTexts;
import io.github.mzmine.modules.tools.batchwizard.WizardPart;
import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassDetectorWizardOptions;
import io.github.mzmine.modules.tools.batchwizard.subparameters.WizardStepParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.WizardParameterFactory;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.metrics.BenchmarkTargetCount;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.metrics.SweepMetric;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.BooleanParameter;
import io.github.mzmine.parameters.parametertypes.ComboParameter;
import io.github.mzmine.parameters.parametertypes.ImportType;
import io.github.mzmine.parameters.parametertypes.ImportTypeParameter;
import io.github.mzmine.parameters.parametertypes.IntegerParameter;
import io.github.mzmine.parameters.parametertypes.OptionalParameter;
import io.github.mzmine.parameters.parametertypes.filenames.FileNameParameter;
import io.github.mzmine.parameters.parametertypes.filenames.FileSelectionType;
import io.github.mzmine.util.ExitCode;
import io.github.mzmine.util.files.ExtensionFilters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.scene.layout.Region;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OptimizerParameters extends SimpleParameterSet {

  /**
   * All available metrics as ordered singleton instances. {@link BenchmarkTargetCount} uses an
   * empty placeholder list — at runtime the real target list is injected by
   * {@link WizardOptimizationProblem#buildEnabledMetrics}.
   */
  public static final List<SweepMetric> ALL_METRICS = List.of(SweepMetric.IPO_ISOTOPE_SCORE,
      SweepMetric.SLAW_INTEGRATION_SCORE, SweepMetric.HARMONIC_SLAW_ISOTOPES,
      SweepMetric.YASIN_ISOTOPE_SCORE, SweepMetric.DOUBLE_PEAK_RATIO, SweepMetric.FILL_RATIO,
      SweepMetric.GC_EI_FRAGMENT_QUALITY, new BenchmarkTargetCount(List.of()));

  /**
   * Default metrics enabled at startup: features with isotopes, integration score, and the combined
   * harmonic score.
   */
  private static final List<SweepMetric> DEFAULT_METRICS = List.of(SweepMetric.IPO_ISOTOPE_SCORE,
      SweepMetric.SLAW_INTEGRATION_SCORE, SweepMetric.HARMONIC_SLAW_ISOTOPES);

  public static final SweepMetricCheckListParameter metricsToOptimize = new SweepMetricCheckListParameter(
      "Metrics to optimize", "Select which quality metrics should drive the optimization.",
      ALL_METRICS, new ArrayList<>(DEFAULT_METRICS));

  private static final List<ImportType<?>> DEFAULT_IMPORT_TYPES = List.of(
      new ImportType<>(true, "mz", new MZType()), new ImportType<>(true, "rt", new RTType()),
      new ImportType<>(false, "mobility", new MobilityType()));
  public static final ImportTypeParameter benchmarkFeatureTypes = new ImportTypeParameter(
      "Benchmark feature csv column names", "", DEFAULT_IMPORT_TYPES);

  public static final OptionalParameter<FileNameParameter> benchmarkFeaturesFile = new OptionalParameter<>(
      new FileNameParameter("Benchmark features file", "", ExtensionFilters.CSV_TSV_IMPORT,
          FileSelectionType.OPEN));

  public static final IntegerParameter iterations = new IntegerParameter("Iterations",
      "Number of iterations during optimization.", 100, 30, 10_000);

  public static final BooleanParameter initializeWithRawDataGuesses = new BooleanParameter(
      "Initialize with raw data-based defaults", "", true);

  public static final ComboParameter<OptimizerOptions> optimizers = new ComboParameter<>(
      "Optimizer", "", OptimizerOptions.values(), OptimizerOptions.MOEAD);

  /**
   * All available optimization targets as {@link WizardParameterPrototype} prototypes. Wizard
   * entries use a default-range dummy builder solely for display/XML. Batch entries wrap
   * {@link BatchParameterSolutionBuilder} method references.
   */
  private static final List<WizardParameterPrototype> ALL_SOLUTIONS = createAllSolutions();

  // decision: wavelet solutions are at the end of ALL_SOLUTIONS and disabled by default
  // because they require the optional mzio WaveletResolverModule
  private static final int WAVELET_SOLUTION_COUNT = 5;

  public static final WizardParameterSolutionCheckListParameter paramToOptimize = new WizardParameterSolutionCheckListParameter(
      "Parameters to optimize", "Select which parameters should be optimized.", ALL_SOLUTIONS,
      new ArrayList<>(ALL_SOLUTIONS.subList(0, ALL_SOLUTIONS.size() - WAVELET_SOLUTION_COUNT)));

  public OptimizerParameters() {
    super(metricsToOptimize, benchmarkFeatureTypes, benchmarkFeaturesFile, optimizers, iterations,
        initializeWithRawDataGuesses, paramToOptimize);
  }

  /**
   * Collects all optimization parameter prototypes that are relevant for the given wizard sequence
   * by querying each wizard step's factory. The dummy builder (null stats) is used only to derive
   * variable names for display and XML; actual data ranges are injected at optimization time.
   *
   * @param steps the current wizard sequence
   * @return ordered list of applicable prototypes, sourced from {@link #ALL_SOLUTIONS}
   */
  public static @NotNull List<WizardParameterPrototype> collectSolutions(
      @NotNull WizardSequence steps) {
    final WizardParameterSolutionBuilder dummy = new WizardParameterSolutionBuilder(null,
        MassDetectorWizardOptions.ABSOLUTE_NOISE_LEVEL);
    return steps.stream().map(WizardStepParameters::getFactory)
        .flatMap(f -> f.getOptimizationSolutions(steps, dummy).stream())
        .sorted(Comparator.comparing(WizardParameterPrototype::name)).toList();
  }

  private static List<WizardParameterPrototype> createAllSolutions() {
    final WizardParameterSolutionBuilder dummy = new WizardParameterSolutionBuilder(null,
        MassDetectorWizardOptions.ABSOLUTE_NOISE_LEVEL);

    final Set<WizardParameterPrototype> allSolutions = new HashSet<>();

    for (WizardPart part : WizardPart.values()) {
      for (WizardParameterFactory preset : part.getDefaultPresets()) {
        WizardSequence sequence = new WizardSequence();
        sequence.set(part, preset.create());
        allSolutions.addAll(preset.getOptimizationSolutions(sequence, dummy));
      }
    }

    return allSolutions.stream().sorted(Comparator.comparing(WizardParameterPrototype::name))
        .toList();

    /*return List.of(new WizardBuilderParameterSolution(dummy.buildMs1NoiseSolution(-1).variable(),
            WizardParameterSolutionBuilder::buildMs1NoiseSolution),
        new WizardBuilderParameterSolution(dummy.buildScanToScanToleranceSolution(-1).variable(),
            WizardParameterSolutionBuilder::buildScanToScanToleranceSolution),
        new WizardBuilderParameterSolution(dummy.buildMinHeightSolution(-1).variable(),
            WizardParameterSolutionBuilder::buildMinHeightSolution),
        new WizardBuilderParameterSolution(dummy.buildMinConsecutiveSolution(-1).variable(),
            WizardParameterSolutionBuilder::buildMinConsecutiveSolution),
        new WizardBuilderParameterSolution(dummy.buildFwhmSolution(-1).variable(),
            WizardParameterSolutionBuilder::buildFwhmSolution),
        new WizardBuilderParameterSolution(dummy.buildSampleToSampleRtTolSolution(-1).variable(),
            WizardParameterSolutionBuilder::buildSampleToSampleRtTolSolution),
        new BatchWizardParameterSolution(BatchParameterSolutionBuilder::buildTopToEdgeRatio),
        new BatchWizardParameterSolution(BatchParameterSolutionBuilder::buildChromThreshold),
        // Wavelet resolver parameters (require optional mzio WaveletResolverModule - disabled by default)
        new BatchWizardParameterSolution(WaveletBatchParameterSolutionBuilder::buildWaveletSnr),
        new BatchWizardParameterSolution(
            WaveletBatchParameterSolutionBuilder::buildWaveletNoiseCalculation),
        new BatchWizardParameterSolution(
            WaveletBatchParameterSolutionBuilder::buildWaveletBaselineMethod),
        new BatchWizardParameterSolution(
            WaveletBatchParameterSolutionBuilder::buildWaveletDipFilter),
        new BatchWizardParameterSolution(
            WaveletBatchParameterSolutionBuilder::buildWaveletEdgeDetector));*/
  }

  /**
   * Convenience factory for programmatic use (e.g. tests). Passes the given metrics as the
   * selection and leaves benchmark file options disabled.
   */
  public static ParameterSet create(@NotNull List<SweepMetric> metrics, int numIterations) {
    final ParameterSet param = new OptimizerParameters().cloneParameterSet();
    param.setParameter(metricsToOptimize, new ArrayList<>(metrics));
    param.setParameter(benchmarkFeatureTypes, DEFAULT_IMPORT_TYPES);
    param.setParameter(benchmarkFeaturesFile, false);
    param.setParameter(iterations, numIterations);
    param.setParameter(paramToOptimize,
        new ArrayList<>(ALL_SOLUTIONS.subList(0, ALL_SOLUTIONS.size() - WAVELET_SOLUTION_COUNT)));
    return param;
  }

  @Override
  public boolean checkParameterValues(final Collection<String> errorMessages,
      final boolean skipRawDataAndFeatureListParameters) {
    final boolean superCheck = super.checkParameterValues(errorMessages,
        skipRawDataAndFeatureListParameters);

    final boolean benchmarkFileSelected = getValue(benchmarkFeaturesFile);
    List<ImportType<?>> value = getValue(benchmarkFeatureTypes).stream()
        .filter(ImportType::isSelected)
        .filter(i -> i.getDataType().equals(new MZType()) || i.getDataType().equals(new RTType()))
        .toList();

    if (benchmarkFileSelected && value.size() < 2) {
      errorMessages.add(
          "If %s is selected, RT and MZ values must be imported from the csv file.".formatted(
              benchmarkFeaturesFile.getName()));
    }

    return superCheck && errorMessages.isEmpty();
  }

  @Override
  public @Nullable Region getMessage() {
    return FxTextFlows.newTextFlowInAccordion("Citations", text(
            "When optimizing on these respective metrics, please respect the following citations:"),
        linebreak(), FxTexts.boldText(SweepMetric.IPO_ISOTOPE_SCORE.name()), text(": "),
        hyperlinkText("IPO", "https://doi.org/10.1186/s12859-015-0562-8"), linebreak(),
        boldText(SweepMetric.SLAW_INTEGRATION_SCORE.name()), text(", "),
        boldText(SweepMetric.HARMONIC_SLAW_ISOTOPES.name()), text(": "),
        hyperlinkText("SLAW", "https://pubs.acs.org/doi/10.1021/acs.analchem.1c02687"));
  }

  public ExitCode showSetupDialog(boolean valueCheckRequired, @Nullable WizardSequence sequence) {
    getParameter(paramToOptimize).setWizardSequence(sequence);
    var superReturn = super.showSetupDialog(valueCheckRequired);
    getParameter(paramToOptimize).setWizardSequence(null); // always reset to zero
    return superReturn;
  }
}
