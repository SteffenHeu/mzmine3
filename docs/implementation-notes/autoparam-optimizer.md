# Automatic parameter optimizer

## Intention

Find a strong wizard parameter set with a practical number of full batch executions. The search
must be reproducible, expose time to result, work in GUI and headless runs, and preserve the
meaning of the selected quality metric.

Detailed measurements and rejected algorithm pilots are recorded in
[autoparam-optimizer-evaluation.md](autoparam-optimizer-evaluation.md).

## Decisions

- Retain deterministic pattern search for single-objective optimization and MOEA/D for genuine
  multi-objective searches. MOEA/D remains the default until the smaller pattern-search campaign
  is accepted as sufficient product validation.
- GP-ARD, multivariate TPE, and standalone Sobol search were removed after matched pilot runs did
  not outperform pattern search. Sobol remains shared infrastructure for MOEA/D warm starts and
  pattern-search restarts. The pilot CSVs remain under
  [the optimizer test resources](../../mzmine-community/src/test/resources/io/github/mzmine/modules/tools/tools_autoparam/optimizer/results/).
- The requested iteration limit counts uncached full batch executions, including the raw-data
  estimate. Cache hits remain visible but consume no batch budget. A separate proposal cap prevents
  duplicate loops.
- Elapsed optimization time is the primary efficiency measure. It starts after import/statistics
  and includes the estimate, proposal generation, diagnostics, and batch execution. Per-batch
  runtime and batch/proposal indices remain available as diagnostics.
- Pattern search starts directly at the raw-data estimate. MOEA/D uses a fixed population of 20;
  its warm-start sampling setting controls how that population is distributed around the estimate.
  All random behavior uses the seeded MOEA Framework PRNG. Pattern search and Sobol designs are
  deterministic.
- Candidates are materialized through `problem.newSolution()`, represented canonically on
  `[0, 1]`, tagged with their origin before evaluation, and deduplicated by their effective values.
  Every registered parameter explicitly declares a linear or logarithmic `SearchScale`; scale is
  based on the parameter's meaning rather than inferred from its current bounds. Continuous
  tolerances, widths, intensities, heights, and SNR are multiplicative; thresholds, ratios, counts,
  and enum choices are linear. Ordinal values are rounded before comparison.
- The optional peak-shape rejection remains a constraint rather than part of the score. Its limit
  is derived from the estimate with a floor for nearly perfect baselines. The expensive shape
  diagnostic runs only when the constraint is enabled; the cheap precision diagnostic always runs.
- GUI runs publish completed solutions to one observable table and a score/best-so-far chart. Stop
  search finishes the current batch, prevents the next proposal, and returns all completed results.
  Headless runs create no JavaFX state and expose the same result through `OptimizationOutcome`.
- The statistics dashboard shows the cross-file absolute RT-deviation distribution together with
  the inter-sample RT estimate and search bounds. Because it is inherently a cross-file statistic,
  this plot is unchanged by the dashboard's single-file/overlay display mode. Optimizer GUI runs
  reuse the deviations already calculated for the search range.
- General wizard preset factories do not depend on optimizer classes. Optimizable parameters are
  registered centrally in `OptimizationParameterRegistry`; optional Wavelet parameters are excluded
  from new configurations explicitly, not by list position.
- `WizardOptimizationProblem` owns the search space, cache, constraints, and evaluation history.
  `OptimizationBatchEvaluator` owns reduced batch construction/execution, scoring, and diagnostics.
  `BenchmarkFeatureLoader` owns optional CSV and statistics-derived target loading.
- Real-data benchmark tests are opt-in with `-Dmzmine.test.autoparam.run=true`. Data paths are
  relative to `mzmine.test.autoparam.dataRoot`, and generated output defaults to
  `build/autoparam-benchmarks` so normal tests and source roots remain clean.

## Validation contract

Compare best feasible score as a function of elapsed time, with the raw estimate and equal full
batch ceilings. Retain complete trajectories, cache information, constraints, and precision/shape
diagnostics. Prefer the simpler method when final scores differ by less than 5%; do not promote a
new default from a single dataset or a single stochastic MOEA/D seed.
