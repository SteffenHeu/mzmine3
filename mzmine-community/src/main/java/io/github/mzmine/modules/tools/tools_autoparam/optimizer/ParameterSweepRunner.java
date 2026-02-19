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

import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.javafx.dialogs.DialogLoggerUtil;
import io.github.mzmine.modules.batchmode.BatchModeModule;
import io.github.mzmine.modules.batchmode.BatchQueue;
import io.github.mzmine.modules.batchmode.BatchTask;
import io.github.mzmine.modules.dataprocessing.filter_isotopegrouper.IsotopeGrouperModule;
import io.github.mzmine.modules.dataprocessing.filter_rowsfilter.RowsFilterModule;
import io.github.mzmine.modules.dataprocessing.gapfill_peakfinder.multithreaded.MultiThreadPeakFinderModule;
import io.github.mzmine.modules.dataprocessing.group_metacorrelate.corrgrouping.CorrelateGroupingModule;
import io.github.mzmine.modules.dataprocessing.group_spectral_networking.MainSpectralNetworkingModule;
import io.github.mzmine.modules.dataprocessing.id_ion_identity_networking.ionidnetworking.IonNetworkingModule;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnnotationModule;
import io.github.mzmine.modules.dataprocessing.id_spectral_library_match.SpectralLibrarySearchModule;
import io.github.mzmine.modules.tools.batchwizard.WizardPart;
import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.modules.tools.batchwizard.subparameters.CustomizationWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.FilterWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.IonInterfaceHplcWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassSpectrometerWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.ParameterOverride;
import io.github.mzmine.modules.tools.batchwizard.subparameters.WizardStepParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.WorkflowDdaWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.WorkflowWizardParameterFactory;
import io.github.mzmine.modules.tools.tools_autoparam.DataFileStatistics;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.SweepMetric.SlawIntegrationScore;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.WizardParameterPrototype.BatchWizardParameterSolution;
import io.github.mzmine.modules.tools.tools_autoparam.optimizer.WizardParameterPrototype.WizardBuilderParameterSolution;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.project.ProjectService;
import io.github.mzmine.taskcontrol.SimpleRunnableTask;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.BinaryIntegerVariable;
import org.moeaframework.core.variable.RealVariable;
import org.moeaframework.core.variable.Variable;

/**
 * One-at-a-time (OAT) parameter sweep runner. For each selected {@link WizardParameterPrototype},
 * the parameter's range is sampled at {@code samplesPerParam} evenly-spaced values while all other
 * parameters are kept at their defaults. Each sample runs a full batch pipeline and the resulting
 * {@link FeatureList} is kept alive in the project for post-hoc evaluation.
 * <p>
 * Reuses {@link WizardParameterSolutionBuilder} and {@link BatchParameterSolutionBuilder} through
 * the existing {@link WizardParameterPrototype} factory mechanism.
 */
public class ParameterSweepRunner {

  private static final Logger logger = Logger.getLogger(ParameterSweepRunner.class.getName());

  private final WizardSequence initialSequence;
  private final List<WizardParameterPrototype> paramToOptimize;
  private final WizardParameterSolutionBuilder builder;
  private final File[] files;
  private final @Nullable MZTolerance mzSampleToSampleTolerance;
  private final @Nullable RTTolerance rtSampleToSampleTolerance;

  public ParameterSweepRunner(@NotNull WizardSequence initialSequence,
      @NotNull List<@NotNull DataFileStatistics> stats, @NotNull ParameterSet param) {
    this.initialSequence = initialSequence;
    this.paramToOptimize = param.getValue(OptimizerParameters.paramToOptimize);
    this.builder = new WizardParameterSolutionBuilder(stats, null);
    this.files = stats.stream().map(DataFileStatistics::file).map(RawDataFile::getAbsoluteFilePath)
        .toArray(File[]::new);

    final ModularFeatureList aligned = OptimizationUtils.alignBenchmarkFeatures(stats, null,
        new SimpleRunnableTask(() -> {
        }));
    this.mzSampleToSampleTolerance = OptimizationUtils.extractSampleToSampleMzTolerances(aligned,
        (int) (files.length * 0.8), 0.8f);
    this.rtSampleToSampleTolerance = OptimizationUtils.extractSampleToSampleRtTolerances(aligned,
        (int) (files.length * 0.8), 0.8f);
  }

  /**
   * Runs the one-at-a-time sweep. For each prototype in {@link #paramToOptimize}, generates
   * {@code samplesPerParam} values across its range and executes a batch for each value.
   *
   * @param samplesPerParam number of evenly-spaced values to sample per parameter
   * @return list of sweep results; feature lists are kept in the project (not removed)
   */
  public List<ParameterSweepResult> sweep(int samplesPerParam) {
    return sweep(samplesPerParam, new AtomicInteger());
  }

  /**
   * Runs the one-at-a-time sweep with progress tracking. {@code completedRuns} is incremented after
   * each individual batch run so callers can report progress.
   *
   * @param samplesPerParam number of evenly-spaced values to sample per parameter
   * @param completedRuns   counter incremented after each completed run
   * @return list of sweep results; feature lists are kept in the project (not removed)
   */
  public List<ParameterSweepResult> sweep(int samplesPerParam,
      @NotNull AtomicInteger completedRuns) {
    final List<ParameterSweepResult> results = new ArrayList<>();

    for (WizardParameterPrototype prototype : paramToOptimize) {
      final List<Variable> samples = generateSamples(prototype, samplesPerParam);
      logger.fine("Sweeping %s with %d samples".formatted(prototype.name(), samples.size()));

      for (Variable sampledVar : samples) {
        final WizardSequence sequence = createBaseWizardSequence(prototype);
        applyVariableToSequence(prototype, sampledVar, sequence);

        final BatchQueue queue = ((WorkflowWizardParameterFactory) sequence.get(WizardPart.WORKFLOW)
            .get().getFactory()).getBatchBuilder(sequence).createQueue();

        removeUnnecessarySteps(queue);

        final FeatureList featureList = runBatchAndGetNewest(queue);
        ProjectService.getProject()
            .removeFeatureLists(ProjectService.getProject().getCurrentFeatureLists());
        results.add(new ParameterSweepResult(prototype, sampledVar, featureList));
        completedRuns.incrementAndGet();
        logger.fine("Sweep run complete: %s = %s → %d rows".formatted(prototype.name(), sampledVar,
            featureList == null ? -1 : featureList.getNumberOfRows()));
      }
    }

    return results;
  }

  /**
   * Applies a single sampled variable value for the given prototype to an existing wizard sequence.
   * For {@link WizardBuilderParameterSolution} prototypes the relevant wizard step parameter is
   * updated directly; for {@link BatchWizardParameterSolution} prototypes a
   * {@link CustomizationWizardParameters} override is injected.
   */
  public void applyVariableToSequence(@NotNull WizardParameterPrototype prototype,
      @NotNull Variable variable, @NotNull WizardSequence sequence) {
    final Solution sol = new Solution(1, 0);
    sol.setVariable(0, variable);

    if (prototype instanceof WizardBuilderParameterSolution wbs) {
      final WizardParameterSolution wps = wbs.toRealSolution(builder, 0);
      final WizardStepParameters wizardStep = sequence.get(wps.part()).get();
      wps.setToParameters().accept(wizardStep, sol, 0);

    } else if (prototype instanceof BatchWizardParameterSolution bws) {
      final BatchParameterSolution bps = bws.toBatchParameterSolution(0);
      final ParameterOverride override = bps.toParameterOverride(sol);
      final WizardStepParameters customization = sequence.get(WizardPart.CUSTOMIZATION).get();
      customization.setParameter(CustomizationWizardParameters.enabled, true);
      customization.setParameter(CustomizationWizardParameters.overrides, List.of(override));
    }
  }

  /**
   * Applies the prototype and variable stored in {@code result} to the given wizard sequence. This
   * is a convenience wrapper around {@link #applyVariableToSequence}.
   */
  public void applyResult(@NotNull ParameterSweepResult result, @NotNull WizardSequence sequence) {
    applyVariableToSequence(result.prototype(), result.variable(), sequence);
  }

  /**
   * Returns a count of how many total batch runs a sweep with the given parameters will perform.
   */
  public int totalRuns(int samplesPerParam) {
    return paramToOptimize.size() * samplesPerParam;
  }

  /**
   * Generates sampled variable values for the given prototype across its full range.
   *
   * <ul>
   *   <li>{@link RealVariable}: {@code samplesPerParam} evenly-spaced doubles from lower to
   *       upper</li>
   *   <li>{@link BinaryIntegerVariable}: all integer values in [lower, upper] when the range is
   *       small, otherwise {@code samplesPerParam} evenly-spaced integers</li>
   * </ul>
   */
  private List<Variable> generateSamples(WizardParameterPrototype prototype, int n) {
    final Variable template = switch (prototype) {
      case BatchWizardParameterSolution bwp -> bwp.toBatchParameterSolution(0).variable().get();
      case WizardBuilderParameterSolution wbs -> wbs.toRealSolution(builder, 0).variable().get();
    };

    if (template instanceof RealVariable real) {
      final double lower = real.getLowerBound();
      final double upper = real.getUpperBound();
      final String name = real.getName();
      final List<Variable> vars = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
        final double value = n == 1 ? (lower + upper) / 2.0 : lower + i * (upper - lower) / (n - 1);
        final RealVariable var = new RealVariable(name, lower, upper);
        var.setValue(value);
        vars.add(var);
      }
      return vars;

    } else if (template instanceof BinaryIntegerVariable biv) {
      final int lower = biv.getLowerBound();
      final int upper = biv.getUpperBound();
      final String name = biv.getName();
      final int range = upper - lower + 1;
      final List<Variable> vars = new ArrayList<>();
      if (range <= n) {
        for (int intVal = lower; intVal <= upper; intVal++) {
          final BinaryIntegerVariable var = new BinaryIntegerVariable(name, lower, upper);
          BinaryIntegerVariable.setInt(var, intVal);
          vars.add(var);
        }
      } else {
        for (int i = 0; i < n; i++) {
          final int intVal = lower + (int) Math.round((double) i * (range - 1) / (n - 1));
          final BinaryIntegerVariable var = new BinaryIntegerVariable(name, lower, upper);
          BinaryIntegerVariable.setInt(var, intVal);
          vars.add(var);
        }
      }
      return vars;
    }

    throw new IllegalStateException("Unknown variable type: " + template.getClass().getName());
  }

  /**
   * Creates a fresh base wizard sequence with default parameter values from the initial sequence.
   * No solution-specific parameter values are applied here. The {@code currentPrototype} is used
   * only to decide whether to apply the calculated sample-to-sample RT tolerance (it is skipped
   * when the prototype itself represents that parameter, so the sweep can vary it freely).
   */
  public WizardSequence createBaseWizardSequence(WizardParameterPrototype currentPrototype) {
    final WizardSequence sequence = new WizardSequence();

    final WizardStepParameters dataParam = initialSequence.get(WizardPart.DATA_IMPORT).get()
        .getFactory().create();
    final WizardStepParameters lcParam = initialSequence.get(WizardPart.ION_INTERFACE).get()
        .createDefaultParameterPreset().getFactory().create();
    final WizardStepParameters filterParam = initialSequence.get(WizardPart.FILTER).get()
        .createDefaultParameterPreset().getFactory().create();
    filterParam.setParameter(FilterWizardParameters.goodPeaksOnly, true);
    final WizardStepParameters imsParam = initialSequence.get(WizardPart.IMS).get()
        .createDefaultParameterPreset().getFactory().create();
    final WizardStepParameters msParam = initialSequence.get(WizardPart.MS).get()
        .createDefaultParameterPreset().getFactory().create();
    final WizardStepParameters annotationParam = initialSequence.get(WizardPart.ANNOTATION).get()
        .getFactory().create();
    final WizardStepParameters workflowParam = initialSequence.get(WizardPart.WORKFLOW).get()
        .getFactory().create();
    final WizardStepParameters customizationParam = initialSequence.get(WizardPart.CUSTOMIZATION)
        .get().createDefaultParameterPreset().getFactory().create();

    sequence.add(dataParam);
    sequence.add(lcParam);
    sequence.add(filterParam);
    sequence.add(imsParam);
    sequence.add(msParam);
    sequence.add(annotationParam);
    sequence.add(workflowParam);
    sequence.add(customizationParam);

    workflowParam.setParameter(WorkflowDdaWizardParameters.exportPath, false);

    if (mzSampleToSampleTolerance != null) {
      msParam.setParameter(MassSpectrometerWizardParameters.sampleToSampleMzTolerance,
          mzSampleToSampleTolerance);
    }
    if (rtSampleToSampleTolerance != null && (currentPrototype == null
        || !"Inter sample RT tolerance".equals(currentPrototype.name()))) {
      lcParam.setParameter(IonInterfaceHplcWizardParameters.interSampleRTTolerance,
          rtSampleToSampleTolerance);
    }

    return sequence;
  }

  /**
   * Synthesizes a single best-parameter {@link WizardSequence} from a completed sweep by choosing
   * one variable value per swept parameter according to the following rule:
   * <ul>
   *   <li>Parameters that follow the <em>average rule</em> (FWHM, chromatographic threshold,
   *       top-to-edge ratio — see {@link #useAverageRule}): average of the value at minimum
   *       {@link SweepMetric.DoublePeakRatio} and the value at maximum
   *       {@link SweepMetric.RowsWithIsosBelowCv20}</li>
   *   <li>All other parameters: value at maximum {@link SweepMetric.RowsWithIsosBelowCv20}</li>
   * </ul>
   * If a required metric is absent from the results the method falls back to the value at maximum
   * {@link SweepMetric.RowsWithIsosBelowCv20}, or to the middle sample if that metric is also
   * absent.
   */
  public WizardSequence synthesizeBestSequence(@NotNull List<SweepMetricResult> results) {
    if (results.isEmpty()) {
      return createBaseWizardSequence(null);
    }

    final List<SweepMetric> metrics = results.getFirst().metrics();
    final int doublePeakIdx = findMetricIndex(metrics, SweepMetric.DoublePeakRatio.class);
    // Prefer the combined harmonic metric; fall back to the slaw score alone
    final int harmonicIdx = findMetricIndex(metrics, SweepMetric.HarmonicSlawIsotopes.class);
    final int cv20isoRowsRatio =
        harmonicIdx >= 0 ? harmonicIdx : findMetricIndex(metrics, SlawIntegrationScore.class);

    // Group by parameter name, preserving prototype order
    final Map<String, List<SweepMetricResult>> grouped = results.stream().collect(
        Collectors.groupingBy(SweepMetricResult::parameterName, LinkedHashMap::new,
            Collectors.toList()));

    final WizardSequence sequence = createBaseWizardSequence(null);
    final List<ParameterOverride> batchOverrides = new ArrayList<>();
    final StringBuilder message = new StringBuilder();

    for (List<SweepMetricResult> group : grouped.values()) {
      final WizardParameterPrototype prototype = group.getFirst().prototype();
      final Variable bestVar = pickBestVariable(group, prototype, doublePeakIdx, cv20isoRowsRatio);
      message.append("%s = %s%n".formatted(prototype.name(), formatValue(bestVar)));

      final Solution sol = new Solution(1, 0);
      sol.setVariable(0, bestVar);

      switch (prototype) {
        case WizardBuilderParameterSolution wbs -> {
          final WizardParameterSolution wps = wbs.toRealSolution(builder, 0);
          final WizardStepParameters wizardStep = sequence.get(wps.part()).get();
          wps.setToParameters().accept(wizardStep, sol, 0);
        }
        case BatchWizardParameterSolution bwp ->
            batchOverrides.add(bwp.toBatchParameterSolution(0).toParameterOverride(sol));
      }
    }

    if (!batchOverrides.isEmpty()) {
      final WizardStepParameters customization = sequence.get(WizardPart.CUSTOMIZATION).get();
      customization.setParameter(CustomizationWizardParameters.enabled, true);
      customization.setParameter(CustomizationWizardParameters.overrides, batchOverrides);
    }

    DialogLoggerUtil.showMessageDialog("Synthesized parameters applied to wizard", false,
        message.toString().strip());
    return sequence;
  }

  /**
   * Picks the best variable for {@code group} (all sweep results for one parameter prototype):
   * <ul>
   *   <li>Parameters where {@link #useAverageRule} returns {@code true}: average of value at min
   *       double-peak ratio and value at max primary score</li>
   *   <li>Others: value at max primary score (middle sample as fallback)</li>
   * </ul>
   * The primary score index ({@code primaryIdx}) should point to
   * {@link SweepMetric.HarmonicSlawIsotopes} when available, otherwise
   * {@link SweepMetric.SlawIntegrationScore}.
   */
  private Variable pickBestVariable(@NotNull List<SweepMetricResult> group,
      @NotNull WizardParameterPrototype prototype, int doublePeakIdx, int primaryIdx) {

    final Variable bestByPrimary = primaryIdx >= 0 ? group.stream()
        .max(Comparator.comparingDouble(r -> r.getScore(primaryIdx))).orElseThrow().variable()
        : group.get(group.size() / 2).variable(); // middle sample as fallback

    if (useAverageRule(prototype) && doublePeakIdx >= 0) {
      final Variable bestByLowDouble = group.stream()
          .min(Comparator.comparingDouble(r -> r.getScore(doublePeakIdx))).orElseThrow().variable();
      return averageVariables(bestByPrimary, bestByLowDouble);
    }

    return cloneVariable(bestByPrimary);
  }

  /**
   * Returns {@code true} for parameters whose best value is determined by averaging the optimum for
   * double-peak suppression and isotope/CV quality: FWHM, chromatographic threshold, and
   * top-to-edge ratio.
   */
  public static boolean useAverageRule(@NotNull WizardParameterPrototype prototype) {
    return prototype instanceof BatchWizardParameterSolution || "FWHM".equals(prototype.name());
  }

  /**
   * Returns a new {@link Variable} whose value is the mean of {@code a} and {@code b}.
   */
  private static Variable averageVariables(@NotNull Variable a, @NotNull Variable b) {
    if (a instanceof RealVariable ra && b instanceof RealVariable rb) {
      final RealVariable result = new RealVariable(ra.getName(), ra.getLowerBound(),
          ra.getUpperBound());
      final double avg = (ra.getValue() + rb.getValue()) / 2.0;
      result.setValue(Math.max(ra.getLowerBound(), Math.min(ra.getUpperBound(), avg)));
      return result;
    }
    if (a instanceof BinaryIntegerVariable ba && b instanceof BinaryIntegerVariable bb) {
      final BinaryIntegerVariable result = new BinaryIntegerVariable(ba.getName(),
          ba.getLowerBound(), ba.getUpperBound());
      final int avg = (int) Math.round((ba.getValue() + (double) bb.getValue()) / 2.0);
      BinaryIntegerVariable.setInt(result,
          Math.max(ba.getLowerBound(), Math.min(ba.getUpperBound(), avg)));
      return result;
    }
    return cloneVariable(a);
  }

  /**
   * Returns a shallow clone of {@code v} with the same value.
   */
  private static Variable cloneVariable(@NotNull Variable v) {
    if (v instanceof RealVariable rv) {
      final RealVariable result = new RealVariable(rv.getName(), rv.getLowerBound(),
          rv.getUpperBound());
      result.setValue(rv.getValue());
      return result;
    }
    if (v instanceof BinaryIntegerVariable biv) {
      final BinaryIntegerVariable result = new BinaryIntegerVariable(biv.getName(),
          biv.getLowerBound(), biv.getUpperBound());
      BinaryIntegerVariable.setInt(result, biv.getValue());
      return result;
    }
    return v;
  }

  /**
   * Formats a variable's current value for display (4 significant figures for reals).
   */
  public static String formatValue(@NotNull Variable v) {
    return switch (v) {
      case RealVariable rv -> String.format("%.4g", rv.getValue());
      case BinaryIntegerVariable biv -> String.valueOf(biv.getValue());
      default -> v.toString();
    };
  }

  /**
   * Returns the index of the first metric in {@code metrics} that is an instance of {@code type},
   * or -1 if not found.
   */
  public static int findMetricIndex(@NotNull List<SweepMetric> metrics,
      @NotNull Class<? extends SweepMetric> type) {
    for (int i = 0; i < metrics.size(); i++) {
      if (type.isInstance(metrics.get(i))) {
        return i;
      }
    }
    return -1;
  }

  private static void removeUnnecessarySteps(BatchQueue queue) {
    queue.removeIf(step -> step.getModule() instanceof MultiThreadPeakFinderModule);
    queue.removeIf(step -> step.getModule() instanceof RowsFilterModule);
    queue.removeIf(step -> step.getModule() instanceof CorrelateGroupingModule);
    queue.removeIf(step -> step.getModule() instanceof IonNetworkingModule);
    queue.removeIf(step -> step.getModule() instanceof LipidAnnotationModule);
    queue.removeIf(step -> step.getModule() instanceof SpectralLibrarySearchModule);
    queue.removeIf(step -> step.getModule() instanceof MainSpectralNetworkingModule);
    queue.removeIf(step -> step.getModule() instanceof IsotopeGrouperModule);
//    queue.removeIf(step -> step.getModule() instanceof DuplicateFilterModule);
  }

  @Nullable
  private FeatureList runBatchAndGetNewest(BatchQueue queue) {
    final MZmineProject project = ProjectService.getProject();
    final BatchTask batchTask = BatchModeModule.runBatchQueue(queue, project, files, null, null,
        null, Instant.now());

    if (!batchTask.isFinished()) {
      throw new IllegalStateException("Error running batch.");
    }

//    project.getCurrentFeatureLists().stream().max(
//        Comparator.comparing(flist -> LocalDateTimeParser.parseAnyFirstDate(flist.getDateCreated()));

    return project.getCurrentFeatureLists().stream()
        .max(Comparator.comparing(f -> f.getName().length())).orElse(null);
  }
}
