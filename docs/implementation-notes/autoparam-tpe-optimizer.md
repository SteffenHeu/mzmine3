# Auto-param optimizer: sample-efficient single-objective search

## Intention

Reach a good wizard parameter set in tens of full batch runs rather than hundreds. The immediate
budget is 80 uncached batch executions, including the raw-data estimate. Cache hits and rejected
duplicate proposals are useful diagnostics, but do not consume this budget.

This replaces the earlier TPE-first plan. The implementation order is:

1. make the benchmark and budget trustworthy;
2. establish an adaptive pattern-search baseline;
3. validate pattern search on representative reference datasets;
4. implement local Gaussian-process Bayesian optimisation only if pattern search leaves a measured
   gap;
5. consider TPE only if the preceding results give a concrete reason to do so.

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

**Scope.** Pattern search and GP optimisation are single-objective algorithms. MOEA/D remains
available for genuine multi-objective searches. Neither new algorithm becomes the default until it
passes the real-data benchmark below.

**Return the result shape expected by the existing task.** Both new algorithms implement
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

**Preserve benchmark evidence by configuration.** Benchmark filenames encode the optimizer,
metric, sampling design, batch ceiling, seeds and selected datasets. A short explicit campaign label
may replace that generated suffix. Rerunning the same campaign may replace its own files; changing
the configuration creates different files.

**Use one canonical search representation.** Each parameter is mapped to `[0, 1]`. Parameters whose
bounds span more than 10-fold are mapped logarithmically. Integer and tolerance-option variables
are represented by their effective discrete values, not by the fractional backing value of
`OrdinalIntegerVariable`. Candidate vectors are rounded, clamped and deduplicated before evaluation.

**Treat the current activity ranking as evidence, not truth.** Both algorithms search all enabled
parameters. Pattern search may poll the three currently dominant parameters first, and GP-ARD may
learn long length scales for unimportant parameters, but neither fixes the remaining parameters at
their estimates.

**Use the estimate as a starting observation, not as a modified statistical prior.** The raw-data
estimate and a local space-filling design express the useful prior information directly. The GP
retains a regular constant mean and the TPE prior is not moved away from its standard broad form.

**Warm-start bare algorithms explicitly.** `AbstractAlgorithm` has no initialization channel, and
the current `configureInitialPopulation` switch only injects solutions into evolutionary and
simulated-annealing algorithms. Add explicit `PatternSearchAlgorithm` and
`GaussianProcessAlgorithm` cases that call each class's `setInitialSolutions(List<Solution>)`
method. An empty list means warm-starting is disabled; the algorithm then creates its configured
global initial design. The supplied list may contain the exact estimate that the task already
evaluated; both algorithms match canonical vectors and reuse that observation. Sequential
algorithms must not assume that `setInitialization()` reaches them. Introduce a shared interface
only if additional sequential algorithms later make the concrete switch cases repetitive.

**Separate population size from initial-design size.** Rename the existing constant to
`EVOLUTIONARY_POPULATION_SIZE = 20`; it configures only population-based MOEA algorithms. GP owns an
`INITIAL_DESIGN_SIZE = 17` constant, while pattern search initially consumes only the estimate and
generates its small restart block on demand. `BatchOptimizationMainTask` chooses the warm-start
count by concrete algorithm type before calling `createWarmStartSolutions`. Neither sequential
algorithm inherits the evolutionary population size accidentally.

**Prefer deterministic, inspectable proposal generation.** Production uses a fixed sequence so the
same data and settings reproduce the same result. Benchmarks use seeded shifts of the same Sobol
design, shared between comparable configurations, to measure sensitivity to the initial design.
Generate those shifts centrally from the task's `randomSeed` after `PRNG.setSeed`; pass the
resulting
initial design to the algorithm. No sampler may call `NormalDistribution.sample()` or construct an
unseeded private random generator. `NormalDistribution.inverseCumulativeProbability()` remains safe
because its input is supplied by the seeded or deterministic design. This rule also covers GP
hyperparameter fitting: every marginal-likelihood restart uses a fixed deterministic start or a
seeded Sobol start, iteration order is stable, and equal fits use a deterministic tie-breaker.

**Materialise and tag every proposal consistently.** Pattern-search and GP candidates are always
created by `problem.newSolution()`, then assigned canonical effective values. Add
`PATTERN_SEARCH` and `SURROGATE` to `SolutionOrigin` and apply the appropriate origin before calling
`evaluate()`. The existing `EVOLUTION.applyIfAbsent` remains only a fallback for legacy variation
operators. Surrogate Sobol fallbacks retain `SURROGATE` and carry a separate fallback diagnostic.

**Fail safely.** Numerical GP failures increase the diagonal jitter and retry. If fitting or
acquisition still fails, the algorithm evaluates the next unused Sobol point and continues. A
surrogate failure must not lose the best feasible result already found.

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
5. Extend `configureInitialPopulation` with explicit cases for `PatternSearchAlgorithm` and
   `GaussianProcessAlgorithm`, passing the injected list to their setters. Verify that neither
   warm-started algorithm reaches the "cannot be warm-started" fallback.
6. Rename `POPULATION_SIZE` to `EVOLUTIONARY_POPULATION_SIZE` and select the warm-start count by
   concrete algorithm: 20 for population algorithms, 17 for GP, and one for pattern search.
7. Extend `SolutionOrigin`, make the canonical search-space mapper materialise candidates through
   `problem.newSolution()`, and test the resulting origin, variable subclasses and constraint count.
8. Add a reproducibility integration test: the same task seed must produce the same initial design,
   fitted GP hyperparameters, canonical proposal sequence and scores, while a different benchmark
   seed must change the shifted-Sobol design.

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

### 2. Conditional local GP Bayesian optimisation

Do not start this phase merely because it was next in the original list. Implement GP only if
pattern search misses an actual product requirement, shows a clear plateau below the best known
result, or a substantially smaller time budget becomes necessary. The completed four-dataset run
gives no current evidence that GP's additional machinery is needed. Do not launch a long all-dataset
campaign merely to unblock GP implementation.

Implement the numerical pieces independently of MOEA Framework:

- `Matern52Kernel` for an ARD Matérn-5/2 covariance;
- `GaussianProcessSurrogate` for fitting and posterior predictions;
- `ExpectedImprovement` for maximisation;
- `GaussianProcessAlgorithm extends AbstractAlgorithm` for search integration.

The first model consumes 17 observations from the explicit initial design: the already evaluated
estimate plus 16 shifted-Sobol perturbations. With an 80-batch budget this leaves 63 guided
evaluations.

Model contract:

1. Train on canonical transformed vectors and standardised objective values.
2. Fit one bounded length scale per parameter, signal variance and a small diagonal noise/jitter
   term by regularised marginal likelihood. Weak length-scale priors prevent 17 observations from
   producing extreme ARD estimates. Use deterministic bounded multistart points and a stable
   tie-breaker. These are internal constants, not user parameters.
3. Refit after the initial design and then every few evaluations, warm-starting from the previous
   fit. Cholesky decomposition and triangular solves come from `commons-math3`; no dependency is
   added.
4. Select the next point by expected improvement over a deterministic Sobol candidate pool. Mix
   candidates from the full bounds with candidates in a trust region around the incumbent. Adapt
   the trust-region width after repeated successes or failures.
5. Canonicalise and deduplicate every candidate before scoring its acquisition. The likelihood and
   the actual batch must see the same rounded ordinal values. Materialise the winner with
   `problem.newSolution()` and tag it `SURROGATE` before evaluation.
6. When the shape constraint is enabled, fit its violation value as a separate surrogate and use
   constrained expected improvement. Do not mix infeasible objective values into the ordinary
   objective model as if they were simply low scores.

Unit tests cover covariance values, posterior interpolation, increasing uncertainty away from
observations, EI at known and unknown points, ARD on irrelevant dimensions, near-singular inputs,
ordinal deduplication, deterministic hyperparameter refits, the Sobol fallback, `getResult()` and
budget termination. End-to-end synthetic tests compare best-so-far curves across several fixed
designs rather than requiring one stochastic run to beat random search.

### 3. Benchmark and choose the default

Use elapsed optimization time as the primary x-axis and retain the complete best-so-far curve.
Report actual batch executions and proposal counts as secondary axes. Compare:

- raw estimate plus Sobol sampling only;
- adaptive pattern search;
- local GP-ARD with expected improvement;
- current MOEA/D at the same batch budget.

The initial screening on `thermo-20y-qc` and `zenotof-feces-pos` is complete for deterministic
pattern search and three MOEA/D seeds. A follow-up pattern run also completed both ZenoTOF plasma
datasets and was intentionally stopped after feces. Further datasets are optional product
validation, not the next implementation step. If GP is later implemented, compare it with fixed
designs using common elapsed-time checkpoints and an 80-batch safety ceiling. Report per dataset:

- best feasible score divided by the estimate score;
- median and interquartile range at fixed elapsed-time checkpoints;
- area under the best-so-far curve;
- cache hits, rejected duplicate proposals, numerical fallbacks and wall time;
- the existing precision and shape diagnostics.

Choose the simpler method when final scores differ by less than 5%. Promote a new single-objective
default only if it has the best median result at the elapsed-time limit and finishes within 5% of
the best method on every reference dataset. The two historical 401-proposal scores above are
stretch goals, not a substitute for this comparison.

### 4. Conditional TPE follow-up

Do not implement TPE merely as proof that a surrogate can work. Reconsider it only if GP maintenance
or numerical robustness is unacceptable, or if the future parameter space becomes substantially
categorical or conditional.

If that condition is met, the implementation must include discrete probability masses for ordinal
variables, candidate deduplication, explicit constraint handling and a multivariate model or direct
evidence that independent densities are adequate. It is judged by the same batch-count benchmark;
matching pattern search does not justify its additional complexity.
