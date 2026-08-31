# Extending the parameter optimizer

The optimizer has two independent extension points: scores evaluated on a feature list and
parameters varied by the search.

## Add an evaluation metric

1. Add a separate implementation of the sealed `SweepMetric` interface under `metrics/`.
2. Add the implementation to the `permits` list in `SweepMetric`.
3. For a stateless metric, expose one singleton from `SweepMetric`.
4. Register the metric in `OptimizerParameters.ALL_METRICS`. Add it to `DEFAULT_METRICS` only if
   it should be selected for new configurations.

A metric supplies its name, optimization direction, and `evaluate(FeatureList)` implementation.
Override `applyAttributes` only for additional diagnostic values that should appear in results.
`WizardOptimizationProblem` creates one objective for every selected metric and
`OptimizationBatchEvaluator` applies the metrics after each batch.

## Add an optimization parameter

Use a `WizardParameterSolution` when changing a value in `WizardStepParameters`. Add the concrete
builder method to `WizardParameterSolutionBuilder`; continuous and ordinal implementations already
exist.

Use a `BatchParameterSolution` when overriding a parameter in a processing module's batch step.
Add its builder method to `BatchParameterSolutionBuilder` or a resolver-specific builder.

Register either kind in `OptimizationParameterRegistry`:

- `WizardParameterSolutionPrototype` pairs a display-variable supplier, explicit `SearchScale`, and
  the runtime builder.
- `BatchParameterSolutionPrototype` wraps an index-aware batch-parameter builder and explicit
  `SearchScale`.
- Add it only to the wizard presets whose generated batch queue contains the target parameter.
- Keep optional-module parameters out of `defaultSolutions()`.

Prototype names are both UI labels and XML identifiers, so every name must be unique and stable.

## Runtime boundary

`WizardOptimizationProblem` owns the search-space variables, constraints, cache, and evaluation
history. It converts a solution into a `WizardSequence`. `OptimizationBatchEvaluator` builds and
runs the reduced batch queue, evaluates metrics, and attaches diagnostics. General batch-wizard
preset factories must not depend on optimizer prototype or solution classes.
