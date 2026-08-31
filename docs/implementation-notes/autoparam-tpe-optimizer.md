# Auto-param optimizer: sample-efficient single-objective search

## Intention

Reach a good wizard parameter set in tens of full batch runs rather than hundreds. The immediate
budget is 80 uncached batch executions, including the raw-data estimate. Cache hits and rejected
duplicate proposals are useful diagnostics, but do not consume this budget.

The practical implementation order is:

1. make the benchmark and budget trustworthy;
2. establish an adaptive pattern-search baseline;
3. implement and evaluate GP-ARD (subsequently retired after the pilot);
4. run the same design without guidance as a Sobol control (subsequently retired);
5. implement multivariate TPE as a second, simpler surrogate approach (subsequently retired);
6. compare score as a function of elapsed time before choosing a default.

All five experimental stages were evaluated. The GP, TPE and standalone Sobol-search implementations
and options were removed after the pilots because none outperformed pattern search. Their benchmark
csv files and findings remain as evidence. Sobol sampling itself remains available for warm-start
designs and pattern-search restarts. Real-data screening remains intentionally limited to
`thermo-20y-qc` and `zenotof-feces-pos`; the current evidence ranks pattern search first. Detailed
results are recorded in
[autoparam-optimizer-evaluation.md](autoparam-optimizer-evaluation.md).

The evidence in [autoparam-optimizer-evaluation.md](autoparam-optimizer-evaluation.md) motivates
this
work, but two properties remain hypotheses rather than implementation assumptions:

- only three of the eight current parameters materially affect the result;
- lowering the MS1 noise threshold continues to improve external result quality at its bound.

The historical default-seed scores remain useful stretch targets, not definitions of the optimum:

| dataset           | estimate | historical MOEA/D result | stretch target at 80 batches |
|-------------------|---------:|-------------------------:|-----------------------------:|
| thermo-20y-qc     |     3014 |                     5690 |                at least 5690 |
| zenotof-feces-pos |    15420 |                    34369 |               at least 34369 |

## Implementation checkpoint: pattern-search screening

The measurement contract and deterministic pattern search are implemented. An 80-batch screening
campaign compared one deterministic pattern-search trajectory with MOEA/D seeds 42, 7 and 101 on
the two reference datasets. For every MOEA/D run, pattern search was truncated at that run's own
finish time before comparing scores; this avoids rewarding pattern search for batches that create
larger feature lists and therefore take longer.

| dataset           | median common time | pattern at common time | median MOEA/D | pattern advantage | pattern final at 80 |
|-------------------|-------------------:|-----------------------:|--------------:|------------------:|--------------------:|
| thermo-20y-qc     |              125 s |                   5412 |          4612 |           +17.3 % |       5766 at 235 s |
| zenotof-feces-pos |              319 s |                  38226 |         30593 |           +25.0 % |      41330 at 473 s |

Pattern search won all six matched comparisons and exceeded both historical 401-evaluation stretch
targets within 80 batches. This is strong screening evidence, not enough to change the default:
only two datasets were used, while the acceptance rule below requires the full reference set.

A follow-up deterministic run completed the first four datasets before being intentionally stopped
after `zenotof-feces-pos` to avoid the runtime of all seven:

| dataset            | estimate | pattern final |  gain | best batch | total time |
|--------------------|---------:|--------------:|------:|-----------:|-----------:|
| thermo-20y-qc      |     3014 |          5766 | 1.91x |         78 |      258 s |
| zenotof-plasma-pos |     7030 |         10974 | 1.56x |         75 |      125 s |
| zenotof-plasma-neg |     3195 |          5007 | 1.57x |         68 |      207 s |
| zenotof-feces-pos  |    15420 |         41330 | 2.68x |         66 |      479 s |

Every completed dataset used exactly 80 full batches and had only the intentional cached estimate.
The remaining three datasets were not run.

The campaign also exposed one logarithmic upper-bound round-off duplicate and a duplicate `Origin`
csv column. Both are fixed and covered by tests; neither changes the recorded scores.

## Decisions

**Scope.** Pattern search is the retained single-objective algorithm. MOEA/D remains available for
genuine multi-objective searches. Pattern search does not become the default until it passes the
real-data benchmark below.

**Retire the GP implementation after its pilot.** The deterministic GP-ARD experiment was
numerically sound, but it did not outperform pattern search at matched elapsed time on the two
screening datasets and carried substantially more implementation and numerical complexity. Remove
its selectable option, runtime classes and dedicated tests. Preserve its benchmark csv files and
evaluation findings so the rejected approach remains traceable.

**Retire TPE and the standalone Sobol control after their pilots.** TPE was about 12% worse than
pattern search at matched elapsed time on both screening datasets. The standalone Sobol search was
useful only as an unguided experimental control and did not improve beyond its initial design.
Remove both options, implementations, origins and dedicated tests while preserving their csv files
and evaluation findings. Keep the shared Sobol sequence used by warm-start sampling and
pattern-search restarts.

**Return the result shape expected by the existing task.** The sequential algorithms implement
`getResult()` and return a `NondominatedPopulation` containing the single best feasible observation.
If cancellation occurs before any feasible observation exists, return the least-violating observed
solution so the existing result and comparison code still receives a non-empty population.

**Count work, not proposals.** `WizardOptimizationProblem` will track uncached batch executions
separately from algorithm evaluations. Termination and progress use the former. Benchmark results
are compared primarily against elapsed optimization time; batch executions, proposals and cache
hits remain secondary efficiency diagnostics. A generous proposal cap prevents an algorithm that
repeatedly generates duplicates from running forever. Because MOEA Framework checks its termination
condition only between steps, the problem atomically reserves a batch immediately before launch and
rejects the next launch at the exact limit. The task then builds the returned non-dominated result
from every completed observation, including offspring from an interrupted partial generation.

**Measure time to result directly.** The cumulative optimization clock starts when
`WizardOptimizationProblem` is constructed: after shared raw-file import and statistics, but before
optimizer construction and the raw-data estimate. Each solution is stamped when its result becomes
available. This includes proposal generation and future surrogate fitting between evaluations, as
well as diagnostics and full batches. Cache hits receive the current elapsed time rather than the
runtime copied from their original result. The existing per-solution `Runtime / s` remains the
runtime of that solution's batch queue and is zero for cache hits. Its sum estimates total batch
processing time, but not time to result because it excludes optimizer and diagnostic overhead.

**Show completed evaluations live in GUI runs.** After evaluating the raw-data estimate,
`BatchOptimizationMainTask` opens the results window and attaches an optional completion observer to
`WizardOptimizationProblem`. The problem publishes only completed `Solution` objects and has no
JavaFX dependency; `OptimizationResultsController` transfers snapshots to the GUI thread and updates
one observable table list via `setAll`. The plot above the table uses proposal/evaluation number on
the x axis, every finite score as a dot, the best feasible score as a step line, and the raw-data
estimate as a dashed baseline. Objective canonical values determine improvement, so both minimise
and maximise metrics work; infeasible points remain visible but cannot advance the best line.
Single-objective optimizers use their only objective. For a MOEA/D multi-objective run the chart
labels and shows the first objective, while the table retains every objective. Actions that consume
a selected solution stay disabled until the final front is available. Headless runs do not attach
the observer or create a window. The live window provides a **Stop search** action: it lets the
currently running batch finish, prevents another candidate from starting even inside a population
generation, and then finalizes normally with every completed evaluation. This is not task
cancellation; the result actions become available after the graceful stop. Headless callers can
request the same behavior programmatically.

**Preserve benchmark evidence by configuration.** Benchmark filenames encode the optimizer,
metric, sampling design, batch ceiling, seeds and selected datasets. A short explicit campaign label
may replace that generated suffix. Rerunning the same campaign may replace its own files; changing
the configuration creates different files.

**Use one canonical search representation.** Each parameter is mapped to `[0, 1]`. Parameters whose
bounds span more than 10-fold are mapped logarithmically. Integer and tolerance-option variables
are represented by their effective discrete values, not by the fractional backing value of
`OrdinalIntegerVariable`. Candidate vectors are rounded, clamped and deduplicated before evaluation.

**Treat the current activity ranking as evidence, not truth.** All algorithms search all enabled
parameters. Pattern search may poll the three currently dominant parameters first, but it does not
fix the remaining parameters at their estimates.

**Use the estimate as a starting observation, not as a modified statistical prior.** The raw-data
estimate and an optional local space-filling design express the useful prior information directly.

**Warm-start bare algorithms explicitly.** `AbstractAlgorithm` has no initialization channel, and
the current `configureInitialPopulation` switch only injects solutions into evolutionary and
simulated-annealing algorithms. Add an explicit `PatternSearchAlgorithm` case that calls its
`setInitialSolutions(List<Solution>)` method. An empty list means warm-starting is disabled; the
algorithm then creates its configured global initial design. The supplied list may contain the
exact estimate that the task already evaluated; all sequential algorithms match canonical vectors
and reuse that observation. Sequential algorithms must not assume that `setInitialization()`
reaches them. Introduce a shared interface only if additional sequential algorithms later make the
concrete switch cases repetitive.

**Separate population size from initial-design size.** Rename the existing constant to
`EVOLUTIONARY_POPULATION_SIZE = 20`; it configures only population-based MOEA algorithms. Pattern
search initially consumes only the estimate and generates its small restart block on demand.
`BatchOptimizationMainTask` chooses the warm-start count by concrete algorithm type before calling
`createWarmStartSolutions`. No sequential algorithm inherits the evolutionary population size
accidentally.

**Prefer deterministic, inspectable proposal generation.** Production and the primary benchmarks
use a fixed Sobol sequence so the same data and settings reproduce the same result. Sensitivity to
the initial design, if needed, is a separate experiment with explicitly shifted designs. No sampler
may call `NormalDistribution.sample()` or construct an unseeded private random generator.
`NormalDistribution.inverseCumulativeProbability()` remains safe because its input is supplied by
the deterministic design.

**Materialise and tag every proposal consistently.** Pattern-search candidates are always created
by `problem.newSolution()`, then assigned canonical effective values. Apply `PATTERN_SEARCH` before
calling `evaluate()`. The existing `EVOLUTION.applyIfAbsent` remains only a fallback for legacy
variation operators.

## Implementation plan

### 0. Establish the measurement contract

1. Add an uncached-batch counter to `WizardOptimizationProblem`. Reserve it only immediately before
   a real batch is launched; expose it to termination and progress reporting. Enforce the limit in
   the problem as well as the between-step termination condition so a generation cannot overshoot.
2. Add elapsed optimization time, proposal index and batch-execution index to every recorded
   solution and to the csv.
3. Change the optimizer termination condition to stop at the requested batch budget. Add a proposal
   cap and cancellation checks independent of that counter.
4. Preserve each benchmark campaign under a configuration-specific filename instead of overwriting
   the evidence used by the implementation note.
5. Extend `configureInitialPopulation` with an explicit `PatternSearchAlgorithm` case, passing the
   injected list to its setter. Verify that it does not reach the "cannot be warm-started" fallback.
6. Rename `POPULATION_SIZE` to `EVOLUTIONARY_POPULATION_SIZE` and select the warm-start count by
   concrete algorithm: 20 for population algorithms and one for pattern search.
7. Extend `SolutionOrigin`, make the canonical search-space mapper materialise candidates through
   `problem.newSolution()`, and test the resulting origin, variable subclasses and constraint count.
8. Add reproducibility tests: repeated runs must produce the same initial design, canonical
   proposal sequence and scores.

This phase is complete when an 80-batch run reports exactly 80 cache misses, terminates even under
duplicate proposals, and its result files identify the algorithm, seed/design and budget.

### 1. Adaptive pattern search

Implement `PatternSearchAlgorithm extends AbstractAlgorithm` as the simplest serious challenger.

1. Consume the explicit initial-design list. When warm-starting is enabled, match its estimate to
   the already evaluated raw-data estimate by canonical vector and reuse that observation rather
   than issuing a duplicate proposal.
2. Poll positive and negative coordinate directions in transformed space. Rotate the starting
   coordinate between polls so a fixed order cannot consume every short budget.
3. Evaluate both valid directions for a coordinate and retain the best feasible improvement.
   Skip bounds, rounded duplicates and previously evaluated effective vectors.
4. Keep the step size after an improvement; halve it after a complete poll without improvement.
   Discrete parameters never use a step smaller than one effective level.
5. When all steps are at their minimum and no direction improves, evaluate a small block of unused
   Sobol perturbations around the estimate/current incumbent. Restart from a better point or return
   the best result.

Each polled point is materialised with `problem.newSolution()`, tagged `PATTERN_SEARCH`, and only
then evaluated.

The initial continuous step is the existing warm-start perturbation scale. Bounds provide the
boundary push naturally; no parameter, including MS1 noise, is forced toward a particular edge.

Unit tests use deterministic synthetic objectives and cover a monotone boundary optimum, an
interior optimum, inactive dimensions, rounded ordinal variables, duplicate avoidance, constraints
and exact budget termination. They also verify that `getResult()` returns the best feasible solution
as a singleton `NondominatedPopulation`.

### 2. Rejected GP experiment

A deterministic GP-ARD optimizer with constrained Expected Improvement and a mixed global/local
candidate pool was implemented and screened at the same 80-batch ceiling. It performed useful
guided optimization beyond its 17-point initial design, but was no better than pattern search on
Thermo and about 13% worse at matched time on ZenoTOF feces. Its runtime implementation and tests
were therefore removed. The exact configuration, measurements and retained csv evidence are in
[autoparam-optimizer-evaluation.md](autoparam-optimizer-evaluation.md).

### 3. Benchmark and choose the default

Use elapsed optimization time as the primary x-axis and retain the complete best-so-far curve.
Report actual batch executions and proposal counts as secondary axes. Compare:

- raw estimate plus Sobol sampling only;
- adaptive pattern search;
- current MOEA/D at the same batch budget.

The initial screening on `thermo-20y-qc` and `zenotof-feces-pos` is complete for deterministic
pattern search and three MOEA/D seeds. A follow-up pattern run also completed both ZenoTOF plasma
datasets and was intentionally stopped after feces. Further datasets are optional product
validation, not the next implementation step. Use common elapsed-time checkpoints and an 80-batch
safety ceiling. Report per dataset:

The retired standalone Sobol experiment was the unguided structured-search control. It consumed the
same 17-point estimate-centred design as the surrogate experiments, then evaluated unused global
canonical Sobol points sequentially until the same real-batch ceiling.

- best feasible score divided by the estimate score;
- median and interquartile range at fixed elapsed-time checkpoints;
- area under the best-so-far curve;
- cache hits, rejected duplicate proposals, numerical fallbacks and wall time;
- the existing precision and shape diagnostics.

Choose the simpler method when final scores differ by less than 5%. Promote a new single-objective
default only if it has the best median result at the elapsed-time limit and finishes within 5% of
the best method on every reference dataset. The two historical 401-proposal scores above are
stretch goals, not a substitute for this comparison.

### 4. Rejected TPE and Sobol-search experiments

Multivariate TPE and a standalone unguided Sobol control were implemented and screened under the
same 80-batch ceiling. TPE improved beyond its shared initial design, but pattern search was about
12% better at each TPE finish time. The Sobol control did not improve beyond its initial design.
Their selectable options, runtime implementations, origins and dedicated tests were therefore
removed. The benchmark configuration, measurements and retained csv evidence are in
[autoparam-optimizer-evaluation.md](autoparam-optimizer-evaluation.md).
