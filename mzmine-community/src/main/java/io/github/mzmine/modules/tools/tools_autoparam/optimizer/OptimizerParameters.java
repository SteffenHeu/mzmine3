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

import io.github.mzmine.datamodel.features.types.numbers.MZType;
import io.github.mzmine.datamodel.features.types.numbers.MobilityType;
import io.github.mzmine.datamodel.features.types.numbers.RTType;
import io.github.mzmine.javafx.components.factories.FxTextFlows;
import io.github.mzmine.javafx.components.factories.FxTexts;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.metrics.BenchmarkTargetCount;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.metrics.SweepMetric;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.BooleanParameter;
import io.github.mzmine.parameters.parametertypes.ComboParameter;
import io.github.mzmine.parameters.parametertypes.DoubleParameter;
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
import java.util.List;
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
   * Default metric enabled at startup.
   */
  private static final List<SweepMetric> DEFAULT_METRICS = List.of(SweepMetric.YASIN_ISOTOPE_SCORE);

  public static final SweepMetricCheckListParameter metricsToOptimize = new SweepMetricCheckListParameter(
      "Metrics to optimize", "Select which quality metrics should drive the optimization.",
      ALL_METRICS, new ArrayList<>(DEFAULT_METRICS));

  private static final List<ImportType<?>> DEFAULT_IMPORT_TYPES = List.of(
      new ImportType<>(true, "mz", new MZType()), new ImportType<>(true, "rt", new RTType()),
      new ImportType<>(false, "mobility", new MobilityType()));
  public static final ImportTypeParameter benchmarkFeatureTypes = new ImportTypeParameter(
      "Benchmark feature csv column names", "", DEFAULT_IMPORT_TYPES);

  public static final OptionalParameter<FileNameParameter> benchmarkFeaturesFile = new OptionalParameter<>(
      new FileNameParameter("Benchmark features file (optional)",
          "Optional file with additional benchmark features.", ExtensionFilters.CSV_TSV_IMPORT,
          FileSelectionType.OPEN));

  public static final IntegerParameter iterations = new IntegerParameter("Iterations",
      "Maximum number of uncached full batch executions, including the raw-data estimate. Cached "
          + "duplicate proposals do not consume this budget.", 100, 30, 10_000);

  public static final BooleanParameter initializeWithRawDataGuesses = new BooleanParameter(
      "Initialize with raw data-based defaults", "", true);

  public static final OptionalParameter<DoubleParameter> maxShapeRejectionFactor = new OptionalParameter<>(
      new DoubleParameter("Max shape rejection factor", """
          Rejects parameter sets that produce badly shaped peaks, as a multiple of the rate measured \
          for the raw data estimate.
          Sensitivity metrics reward detecting more signals, which can be satisfied by picking up \
          noise. This limits how much worse than the estimate a solution's chromatographic shape \
          quality may get, without changing any score.""",
          ConfigService.getGuiFormats().scoreFormat(), 1.5, 1.0, 100.0), true);

  public static final ComboParameter<OptimizerOptions> optimizers = new ComboParameter<>(
      "Optimizer", "Pattern search supports "
      + "one selected metric; MOEA/D also supports multiple objectives.",
      OptimizerOptions.values(), OptimizerOptions.MOEAD);

  public static final ComboParameter<WarmStartSampling> warmStartSampling = new ComboParameter<>(
      "Warm start sampling", """
      How the MOEA/D initial population is spread around the raw data estimate. Pattern search
      starts directly at the estimate and does not use this setting.
      The initial attempts decide most of the result, and independent draws leave their coverage \
      to chance - the same data can score up to twice as differently depending only on the random \
      seed. A space-filling sequence covers the same neighbourhood the same way every run.""",
      WarmStartSampling.values(), WarmStartSampling.GAUSSIAN);

  /**
   * All available optimization targets as {@link ParameterSolutionPrototype} prototypes. Wizard
   * entries use a default-range dummy builder solely for display/XML. Batch entries wrap
   * {@link BatchParameterSolutionBuilder} method references.
   */
  private static final List<ParameterSolutionPrototype> ALL_SOLUTIONS = OptimizationParameterRegistry.allSolutions();
  private static final List<ParameterSolutionPrototype> DEFAULT_SOLUTIONS = OptimizationParameterRegistry.defaultSolutions();

  public static final WizardParameterSolutionCheckListParameter paramToOptimize = new WizardParameterSolutionCheckListParameter(
      "Parameters to optimize", "Select which parameters should be optimized.", ALL_SOLUTIONS,
      new ArrayList<>(DEFAULT_SOLUTIONS));

  public OptimizerParameters() {
    super(metricsToOptimize, benchmarkFeatureTypes, benchmarkFeaturesFile, optimizers, iterations,
        initializeWithRawDataGuesses, warmStartSampling, maxShapeRejectionFactor, paramToOptimize);
  }

  /**
   * Collects all optimization parameter prototypes that are relevant for the given wizard sequence
   * from the optimizer's central parameter registry.
   *
   * @param steps the current wizard sequence
   * @return ordered list of applicable prototypes
   */
  public static @NotNull List<ParameterSolutionPrototype> collectSolutions(
      @NotNull WizardSequence steps) {
    return OptimizationParameterRegistry.forSequence(steps);
  }

  /**
   * Convenience factory for programmatic use (e.g. tests). Passes the given metrics as the
   * selection and leaves benchmark file options disabled.
   */
  public static @NotNull ParameterSet create(@NotNull List<SweepMetric> metrics,
      int numIterations) {
    final ParameterSet param = new OptimizerParameters().cloneParameterSet();
    param.setParameter(metricsToOptimize, new ArrayList<>(metrics));
    param.setParameter(benchmarkFeatureTypes, DEFAULT_IMPORT_TYPES);
    param.setParameter(benchmarkFeaturesFile, false);
    param.setParameter(iterations, numIterations);
    param.setParameter(maxShapeRejectionFactor, false);
    param.setParameter(paramToOptimize, new ArrayList<>(DEFAULT_SOLUTIONS));
    return param;
  }

  @Override
  public boolean checkParameterValues(@NotNull final Collection<String> errorMessages,
      final boolean skipRawDataAndFeatureListParameters) {
    final boolean superCheck = super.checkParameterValues(errorMessages,
        skipRawDataAndFeatureListParameters);

    final boolean benchmarkFileSelected = getValue(benchmarkFeaturesFile);
    final List<ImportType<?>> value = getValue(benchmarkFeatureTypes).stream()
        .filter(ImportType::isSelected)
        .filter(i -> i.getDataType().equals(new MZType()) || i.getDataType().equals(new RTType()))
        .toList();

    if (benchmarkFileSelected && value.size() < 2) {
      errorMessages.add(
          "If %s is selected, RT and MZ values must be imported from the csv file.".formatted(
              benchmarkFeaturesFile.getName()));
    }

    if (getValue(optimizers) == OptimizerOptions.PATTERN_SEARCH
        && getValue(metricsToOptimize).size() != 1) {
      errorMessages.add("Pattern search requires exactly one optimization metric.");
    }

    return superCheck && errorMessages.isEmpty();
  }

  @Override
  public @Nullable Region getMessage() {
    return FxTextFlows.newTextFlowInAccordion("Citations", FxTexts.text(
            "When optimizing on these respective metrics, please respect the following citations:"),
        FxTexts.linebreak(), FxTexts.boldText(SweepMetric.IPO_ISOTOPE_SCORE.name()),
        FxTexts.text(": "),
        FxTexts.hyperlinkText("IPO", "https://doi.org/10.1186/s12859-015-0562-8"),
        FxTexts.linebreak(), FxTexts.boldText(SweepMetric.SLAW_INTEGRATION_SCORE.name()),
        FxTexts.text(", "), FxTexts.boldText(SweepMetric.HARMONIC_SLAW_ISOTOPES.name()),
        FxTexts.text(": "),
        FxTexts.hyperlinkText("SLAW", "https://pubs.acs.org/doi/10.1021/acs.analchem.1c02687"));
  }

  public @NotNull ExitCode showSetupDialog(boolean valueCheckRequired,
      @Nullable WizardSequence sequence) {
    getParameter(paramToOptimize).setWizardSequence(sequence);
    final ExitCode superReturn = super.showSetupDialog(valueCheckRequired);
    getParameter(paramToOptimize).setWizardSequence(null); // always reset to zero
    return superReturn;
  }
}
