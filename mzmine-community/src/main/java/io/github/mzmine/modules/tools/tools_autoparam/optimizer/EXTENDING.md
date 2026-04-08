# Extending the Parameter Optimizer

This guide covers two independent extension points:

1. **Adding a new evaluation metric** — a score computed on a `FeatureList` that the optimizer
   maximises or minimises
2. **Adding a new parameter to optimize** — a wizard-level or batch-queue parameter whose value the
   optimizer searches over

---

## 1. Adding a New Evaluation Metric

Metrics live in `SweepMetric.java` as `record` implementations of the sealed interface.

### Step 1 — Add a record in `SweepMetric`

Implement the sealed interface. The record is typically parameterless (if stateless) or carries
state (e.g. `BenchmarkTargetCount` carries a target list).

```java
// in SweepMetric.java, inside the sealed interface
record MyNewMetric() implements SweepMetric {

  @Override
  public @NotNull String name() {
    return "My new metric";           // shown in the results table
  }

  @Override
  public boolean higherIsBetter() {
    return true;                      // or false to minimise
  }

  @Override
  public double evaluate(@NotNull FeatureList featureList) {
    // compute and return the score
    return featureList.getNumberOfRows(); // example
  }
}
```

If the metric needs a data table for abundance/RSD calculations, use the private helper already
present in the interface:

```java
final FeaturesDataTable dataTable = buildDataTable(featureList);
```

If the metric needs to store component scores on the `Solution` for post-processing or display
normalisation (like `HarmonicSlawIsotopes`), override `applyAttributes`:

```java

@Override
public void applyAttributes(@NotNull FeatureList featureList, @NotNull Solution solution) {
  solution.setAttribute("_my_component", someValue);
}
```

### Step 2 — Add a singleton constant (if stateless)

```java
// at the top of SweepMetric, with the other constants
MyNewMetric MY_NEW_METRIC = new MyNewMetric();
```

### Step 3 — Register the metric in `OptimizerParameters.ALL_METRICS`

In `OptimizerParameters.java`, add the singleton to the `ALL_METRICS` list:

```java
public static final List<SweepMetric> ALL_METRICS = List.of(
    // ... existing entries ...
    SweepMetric.MY_NEW_METRIC);
```

To enable it by default, also add it to `DEFAULT_METRICS`:

```java
private static final List<SweepMetric> DEFAULT_METRICS = List.of(
    // ... existing entries ...
    SweepMetric.MY_NEW_METRIC);
```

That's all. `LcMsOptimizationProblem.buildEnabledMetrics()` and `calculateNumberOfObjectives()` both
read the user's checklist selection automatically — no further changes needed there.

---

## 2. Adding a New Parameter to Optimize

Parameters come in two flavours:

| Flavour              | When to use                                                                                | Builder class                    |
|----------------------|--------------------------------------------------------------------------------------------|----------------------------------|
| **Wizard parameter** | Targets a `WizardStepParameters` field (e.g. FWHM, noise level, RT tolerance)              | `WizardParameterSolutionBuilder` |
| **Batch parameter**  | Overrides a `ParameterSet` value inside a specific batch module (e.g. resolver thresholds) | `BatchParameterSolutionBuilder`  |

### 2a. Adding a Wizard Parameter

#### Step 1 — Add a build method in `WizardParameterSolutionBuilder`

The method receives the solution-vector `index` and returns a `WizardParameterSolution`. Choose
`DoubleWizardParameterSolution` for continuous values or `IntegerWizardParameterSolution` for
integer/enum indices.

**Continuous double parameter:**

```java
public @NotNull WizardParameterSolution buildMyDoubleSolution(int index) {
  return new DoubleWizardParameterSolution(index, WizardPart.ION_INTERFACE,
      MyWizardParameters.myParam, () -> new RealVariable("My param", minValue, maxValue));
}
```

The `UserParameter` form of `DoubleWizardParameterSolution` automatically reads
`RealVariable.getReal(solution.getVariable(id))` and calls `stepParam.setParameter(myParam, value)`.
For non-trivial conversions (e.g. wrapping a double in a `RTTolerance`) use the lambda constructor:

```java
return new DoubleWizardParameterSolution(index, WizardPart.ION_INTERFACE,
    (stepParam, solution, id) ->stepParam.

setParameter(MyWizardParameters.myParam,
        new RTTolerance((float) RealVariable.

getReal(solution.getVariable(id)),Unit.MINUTES)),
    ()->new

RealVariable("My param",minValue, maxValue));
```

**Integer / enum index parameter:**

```java
public @NotNull WizardParameterSolution buildMyIntegerSolution(int index) {
  return new IntegerWizardParameterSolution(index, WizardPart.MS, MyWizardParameters.myIntParam,
      () -> new BinaryIntegerVariable("My param", 0, 5));
}
```

If the integer is an index into an options array (like `MZTolerance[]`), use the lambda constructor
to dereference the array.

#### Step 2 — Register the prototype in `OptimizerParameters.createAllSolutions()`

```java
private static List<WizardParameterPrototype> createAllSolutions() {
  final WizardParameterSolutionBuilder dummy = new WizardParameterSolutionBuilder(null,
      MassDetectorWizardOptions.ABSOLUTE_NOISE_LEVEL);

  return List.of(
      // ... existing entries ...
      new WizardBuilderParameterSolution(
          dummy.buildMyDoubleSolution(-1).variable(),   // dummy index -1 for display/XML only
          WizardParameterSolutionBuilder::buildMyDoubleSolution));
}
```

The first argument (`dummy.buildMyDoubleSolution(-1).variable()`) is used only to derive the display
name and XML key. The second argument is the factory method reference used at runtime to build the
real solution with the actual data-derived ranges and correct solution-vector index.

---

### 2b. Adding a Batch Parameter

Batch parameters override a value inside a specific batch module's `ParameterSet` via
`ParameterOverride`.

#### Step 1 — Add a build method in `BatchParameterSolutionBuilder`

```java
public static BatchParameterSolution buildMyBatchParam(int index) {
  return new DoubleBatchParameterSolution(MyBatchModule.class, MyBatchModuleParameters.MY_PARAM,
      index, ApplicationScope.FIRST,    // FIRST = applies to the first matching module in the queue
      () -> new RealVariable("My batch param", 0.5, 2.0));
}
```

Only `double`-valued batch parameters are supported via `DoubleBatchParameterSolution`. For other
types, add a new `BatchParameterSolution` record implementation.

#### Step 2 — Register the prototype in `OptimizerParameters.createAllSolutions()`

```java
new BatchWizardParameterSolution(BatchParameterSolutionBuilder::buildMyBatchParam)
```

`BatchWizardParameterSolution` derives its display name from the `variable()` supplier, so no dummy
builder is needed.

---

## Architecture Overview

```
OptimizerParameters.paramToOptimize
    └── List<WizardParameterPrototype>          — user selection, persisted in XML
            ├── WizardBuilderParameterSolution  — factory for wizard parameters
            └── BatchWizardParameterSolution    — factory for batch parameters

LcMsOptimizationProblem (per optimization run)
    ├── enabledMetrics: List<SweepMetric>       — active objectives, built from param flags
    ├── newSolution()
    │     ├── WizardParameterSolution.applyToSolution()   — sets variables
    │     ├── BatchParameterSolution.applyToSolution()    — sets variables
    │     └── SweepMetric.objective()                     — sets Maximize/Minimize objectives
    └── evaluate(Solution)
          ├── runs BatchQueue on raw files
          ├── SweepMetric.evaluate(FeatureList)            — computes score
          └── SweepMetric.applyAttributes(FeatureList, Solution)  — stores extra attributes
```
