# QC Dashboard — Implementation Plan

Derived from the sketch in [qc-dashboard.md](qc-dashboard.md). This plan turns the
sketch into a concrete, MVCI-based, batch-safe implementation that reuses existing
mzmine frameworks (MVCI, SimpleChart, the rows filter, metadata utilities, color utils).

---

## 1. Goals & guiding constraints

- **One main MVCI** owns the shared dashboard state.
- **Each subplot is its own MVCI**, with its model **bound to the main model** for shared
  variables (bidirectional where the subplot can mutate them, one-way otherwise). Variables
  exclusive to a single plot live only in that plot's model.
- **Use `SimpleChart` (`SimpleXYChart`)** for all scatter/histogram plots.
- **All filtering that edits a feature list must be batch-safe** → reuse existing
  `MZmineProcessingModule`s (rows filter, blank subtraction) or add new ones; never bury
  list-mutating logic in the GUI.
- Purely visual filtering (sample-type selection, which files are shown) stays in the
  dashboard model and never mutates the feature list.

### Reference implementations to mirror
| Concern | Reference (study before coding) |
|---|---|
| Parent dashboard MVCI + child plot binding | `modules/dataanalysis/statsdashboard/` (`StatsDashboardController/Model/ViewBuilder/Tab/Module`) |
| Subplot MVCI bound to parent via exposed properties | `modules/dataanalysis/rowsboxplot/` (`RowsBoxplotController/Model/ViewBuilder` + `RowBoxPlotDataset`) |
| Multi-pane QC dashboard layout | `modules/visualization/dash_lipidqc/` |
| MVCI base contracts | `javafx-framework/.../javafx/mvci/{FxController,FxViewBuilder,FxInteractor,FxUpdateTask}.java` |
| Cross-controller binding helper | `gui/framework/fx/FxControllerBinding.java` + the `Selected*Binding` interfaces |
| SimpleChart scatter + provider | `gui/chartbasics/simplechart/SimpleXYChart.java`, `.../providers/PlotXYDataProvider.java`, `.../providers/ExampleXYProvider.java` |
| Multi-dataset/marker example | `modules/dataanalysis/volcanoplot/` (`VolcanoPlotViewBuilder`, `VolcanoPlotUpdateTask`, `VolcanoDatasetProvider`) |
| Reduced feature table | `modules/visualization/featurelisttable_modular/` (`FxFeatureTableController`, `FeatureTableFX`, `FeatureTableFXParameters`) |

> During implementation, invoke the **`mvci-gui`** skill for the MVCI scaffolding and the
> **`new-chart`** skill for each plot/provider/renderer.

---

## 2. Package & file layout

New package: `io.github.mzmine.modules.dataanalysis.qcdashboard`
(matches where the stats dashboard lives; it is a data-analysis visualization, not a
processing step).

```
qcdashboard/
├─ QcDashboardModule.java          // AbstractRunnableModule – opens the tab
├─ QcDashboardParameters.java      // FeatureListsParameter (aligned)
├─ QcDashboardTab.java             // SimpleTab wrapper
├─ QcDashboardController.java      // MAIN MVCI controller (owns sub-controllers)
├─ QcDashboardModel.java           // MAIN shared model
├─ QcDashboardViewBuilder.java     // MAIN layout (grid of plots + controls + table)
├─ QcDashboardInteractor.java      // derived state (ordered files, color map, QC file set)
├─ QcDashboardColorService.java    // file->color mapping helper (batch shading)
│
├─ controls/                       // the right-hand controls panel (no own MVCI needed)
│  └─ QcDashboardControlsBuilder.java
│
└─ plots/                          // one MVCI per subplot
   ├─ intensity/         (Plot 1: single-row intensity per file)
   ├─ featurecount/      (Plot 2: # detected features per file)
   ├─ sumintensity/      (Plot 3: summed intensity per file)
   ├─ detectioncount/    (Plot 4: per-feature detection count across QCs, sorted)
   └─ deviation/         (Plots 5 & 6: m/z & RT deviation per file for selected row)
```

Each `plots/<x>/` package contains `<X>Controller`, `<X>Model`, `<X>ViewBuilder`, and a
SimpleChart data provider (`<X>Provider` implementing `PlotXYDataProvider`), plus an
`FxUpdateTask` where computation is non-trivial.

---

## 3. Main model — `QcDashboardModel`

Plain class with JavaFX properties (same style as `StatsDashboardModel`). Fields:

**Inputs / selection (shared, parent-owned):**
- `ObjectProperty<ModularFeatureList> featureList` — the one aligned feature list.
- `ObjectProperty<FeatureListRow> selectedRow` — drives Plots 1, 5, 6. *(Bidirectional with
  the table and with click selection in plots.)*
- `ObjectProperty<AbundanceMeasure> abundance` (default `Height`) — drives Plots 1 & 3.
- `ObjectProperty<MetadataColumn<?>> batchColumn` — optional batch grouping column.
- `ObjectProperty<List<String>> sampleTypesToShow` — sample types to plot (default: QC only).

**Derived state (computed by the interactor, parent-owned, read-only to subplots):**
- `ObjectProperty<List<RawDataFile>> orderedFiles` — files filtered to the selected sample
  types, **sorted by acquisition date** (`RawDataFile.getStartTimeStamp()`, nulls last).
  The list index is the x-axis value used by all per-file plots.
- `ObjectProperty<Map<RawDataFile,Color>> fileColors` — file→color map (see §5).
- `ObjectProperty<List<RawDataFile>> qcFiles` — subset whose sample type is QC (Plot 4 +
  threshold logic).

**Detection-count thresholds (shared with Plot 4, also reused by filtering, §10):**
- `DoubleProperty goodQualityFraction` (default 0.5) — ">50% MS1 features in all QCs".
- `DoubleProperty warwickFraction` (default 0.7) — Dunn/Warwick recommendation.

Notes:
- Selection of sample types and the batch column are **visual-only** → never mutate the
  feature list; they only re-derive `orderedFiles` / `fileColors` / `qcFiles`.
- Keep the model free of JFreeChart/AWT logic; datasets live in the subplot models.

---

## 4. Main controller / interactor / view

### `QcDashboardController extends FxController<QcDashboardModel>`
Implements the relevant binding interfaces so it composes cleanly with sub-controllers:
`SelectedFeatureListsBinding` (or a single-flist analog), `SelectedRowsBinding`,
`SelectedAbundanceMeasureBinding`, `SelectedMetadataColumnBinding`.

Responsibilities (constructor):
1. Instantiate the five plot sub-controllers + the `FxFeatureTableController`.
2. Build `QcDashboardViewBuilder` passing model + all sub-controllers.
3. Wire shared state to subplots (see §6).
4. Construct `QcDashboardInteractor`; subscribe it to `featureList`, `sampleTypesToShow`,
   and `batchColumn` so it recomputes `orderedFiles`/`fileColors`/`qcFiles`.
5. `close()` propagates to sub-controllers (cancel tasks on tab close).

### `QcDashboardInteractor extends FxInteractor<QcDashboardModel>`
FX-thread derivation of:
- `orderedFiles`: take feature list raw files → keep those whose `SampleType` ∈
  `sampleTypesToShow` → sort by `getStartTimeStamp()` (nulls last, then by name).
- `qcFiles`: subset of orderedFiles with `SampleType.QC`.
- `fileColors`: delegate to `QcDashboardColorService` (§5).

If a derivation becomes heavy on very large lists, move the row/file iteration into an
`FxUpdateTask` (`process()` off-thread → `updateGuiModel()` on FX thread). For file-level
derivation this is usually unnecessary.

### `QcDashboardViewBuilder extends FxViewBuilder<QcDashboardModel>`
Layout mirrors the PowerPoint mock (img_6.png): a plot grid on the left, a controls column
+ feature table on the right.

```
BorderPane
 ├─ center: GridPane (2 rows × 3 cols of plot panes), wrapped for resize
 │     row0: [Plot4 detectionCount] [Plot2 featureCount] [Plot3 sumIntensity]
 │     row1: [Plots5/6 deviation]   [Plot1 intensity]    [(spare / before-after batch)]
 └─ right: VBox
        ├─ QcDashboardControlsBuilder (combo boxes / checks)
        └─ FxFeatureTableController.buildView()   (reduced columns, §8)
```
Use `SplitPane`s (as the stats dashboard does) so the user can resize plots vs. table.
Each plot pane = `subController.buildView()`.

### `QcDashboardTab extends SimpleTab`
Copy `StatsDashboardTab`: construct controller, `setContent(controller.buildView())`,
forward `onFeatureListSelectionChanged` (filter to aligned lists) into the model, and call
`controller.close()` on tab close.

### `QcDashboardModule extends AbstractRunnableModule`
Copy `StatsDasboardModule`: `runModule` resolves the aligned feature list from
`QcDashboardParameters`, builds the tab, `MZmineCore.getDesktop().addTab(tab)`.
**Registration:** add to the same places as `StatsDasboardModule` (workspace menus —
`AcademicWorkspace`, `MainMenu.fxml`, `MainWindowController`, and the module
instantiation/init list). Category `MZmineModuleCategory.DATAANALYSIS`.

---

## 5. File → color mapping (`QcDashboardColorService`)

Per the sketch:
- **No batch column selected** → color = `rawDataFile.getColorAWT()` (existing per-file color).
- **Batch column selected** → assign one base color per batch group (from the project
  `SimpleColorPalette`), then derive per-file shades within the group using
  `ColorUtils.colorFadeLighter(steps, baseColor, range)` so files within a batch are
  distinguishable.

Implementation: take `orderedFiles` + `batchColumn`; group files via metadata utilities
(see `Metadata*GroupSelection` / `MetadataTable.getColumnData`); produce
`Map<RawDataFile,Color>`. Store on the model so all per-file plots read the same colors.

---

## 6. Shared-vs-exclusive variables & binding strategy

The parent model is the single source of truth. Wiring options, in order of preference:

1. **`FxControllerBinding.bindExposedProperties(this, subController)`** — for properties that
   already have a `Selected*Binding` interface (`abundance`, `selectedRow(s)`, `batchColumn`).
   This auto-creates the bidirectional binds, exactly as the stats dashboard does.
2. **Manual one-way bind** (`subModel.prop().bind(mainModel.prop())`) — for parent-derived,
   read-only-to-subplot state: `orderedFiles`, `fileColors`, `qcFiles`, the threshold
   fractions. Subplots never write these.
3. **Manual bidirectional** — for `selectedRow` if a plot can change selection by clicking a
   point (Plot 1 click → select that file's row? more likely table-driven; keep `selectedRow`
   bidirectional between table and plots so a click in any plot can update it).

**Shared (bound) per plot:**

| Variable | Plot 1 | Plot 2 | Plot 3 | Plot 4 | Plots 5/6 |
|---|---|---|---|---|---|
| `orderedFiles` | ✓ | ✓ | ✓ | (qcFiles) | ✓ |
| `fileColors` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `selectedRow` | ✓ | — | — | — | ✓ |
| `abundance` | ✓ | — | ✓ | — | — |
| `qcFiles` + thresholds | — | — | — | ✓ | — |

**Plot-exclusive (live only in the subplot model):** the JFreeChart datasets, SD/RSD overlay
toggles (Plot 1), scatter-vs-histogram toggle (Plot 4), the computed sorted detection list.

---

## 7. Subplots (each its own MVCI, SimpleChart-based)

Common pattern per plot (follow `RowsBoxplotController` + `new-chart` skill):
- `*Model`: shared bound properties (subset above) + `ObjectProperty<...Dataset>`/chart.
- `*Controller`: listens to its bound input properties; on change, (re)builds dataset —
  cheap builds on the FX thread (`onGuiThread`), heavy builds via `FxUpdateTask`.
- `*ViewBuilder`: creates a `SimpleXYChart<PlotXYDataProvider>`, binds the dataset, adds
  markers, tooltips, axis labels.
- `*Provider implements PlotXYDataProvider` (+ `XYItemObjectProvider` to map a point back to
  a `RawDataFile`/`FeatureListRow` for tooltips & click selection).

X-axis convention for per-file plots: **x = index in `orderedFiles`** (numeric), with the raw
file name surfaced via `getToolTipText`. QC vs non-QC drawn as **separate datasets** so they
get distinct colors (as in volcano plot's multi-dataset approach).

### Plot 1 — Intensity of the selected row across files (`plots/intensity/`)
- Inputs: `selectedRow`, `orderedFiles`, `abundance`, `fileColors`.
- For each file i: y = `abundance.getOrNaN(row.getFeature(file))`; x = i.
- Datasets split by QC vs sample (different colors). Tooltip: file name + value.
- **Optional SD/RSD overlay**: horizontal `ValueMarker`s for mean ± k·SD (and/or %RSD band)
  computed over the QC files. Toggleable (plot-exclusive bool). *(Mock: img.png / img_1.png.)*
- Optional companion: median-intensity-across-files variant (sketch open question) — defer.
- Cheap (single row) → build on FX thread.

### Plot 2 — Number of detected features per file (`plots/featurecount/`)
- Inputs: `orderedFiles`, `fileColors`.
- For each file: count rows where `row.getFeature(file) != null`. x = file index, y = count.
- Iterates rows × files → **`FxUpdateTask`** (off-thread `process()`), recompute when
  `featureList`/`orderedFiles` change. *(Mock: img_2.png.)*

### Plot 3 — Summed feature intensity per file (`plots/sumintensity/`)
- Inputs: `orderedFiles`, `abundance`, `fileColors`.
- For each file: sum `abundance.getOrNaN(feature)` over all detected features.
- Iterates rows × files → **`FxUpdateTask`**; recompute on `abundance` change too.
  *(Mock: img_3.png.)*

### Plot 4 — Detection count per feature across QCs (`plots/detectioncount/`)
- Inputs: `qcFiles`, thresholds, `fileColors` (single color is fine).
- For each row: count detections among `qcFiles`. Sort descending.
- **Scatter mode** (default): x = rank index in sorted list, y = detection count. Add vertical
  (or rather horizontal y-`ValueMarker`s) at `goodQualityFraction·|qcFiles|` and
  `warwickFraction·|qcFiles|`. **Histogram mode**: x = detection count, y = # features —
  use `ColoredXYBarRenderer`. Mode toggle is plot-exclusive.
- Iterates all rows → **`FxUpdateTask`**. *(Mock: img_4.png.)*

### Plots 5 & 6 — m/z and RT deviation of the selected row (`plots/deviation/`)
- Inputs: `selectedRow`, `orderedFiles`, `fileColors`.
- For each file i with a feature: x = i; y(mz) = `feature.get(MZType.class) - row.getAverageMZ()`;
  y(rt) = `feature.get(RTType.class) - row.getAverageRT()` (absolute deviation vs row average).
- Two charts (m/z, RT) — implement as one controller exposing two `SimpleXYChart`s, or two
  small sibling controllers. Cheap (single row) → FX thread. *(Mock: img_5.png.)*

---

## 8. Feature table panel (row selection)

Reuse `FxFeatureTableController` + `FeatureTableFX` (as the stats dashboard does). Per the
sketch, show a **reduced** table to avoid the heavy graphical/per-file column types:

- Drive column visibility through `FeatureTableFXParameters`
  (`showRowTypeColumns`/`showFeatureTypeColumns` `DataTypeCheckListParameter`): enable
  `IDType`, `MZType`, `RTType`, and the preferred annotation column; disable graphical types
  (`FeatureShapeType`, `ImageType`, …) and raw-data-file-specific feature types.
- Bind selection both ways: table selection → `model.selectedRow`; `model.selectedRow` →
  `FeatureTableFXUtil.selectAndScrollTo(row, table)` (pattern from
  `StatsDashboardViewBuilder.initFeatureListListeners`).
- The table always reflects the dashboard's feature list (`model.featureList`).

> Decision to confirm (§12): a dedicated reduced `FeatureTableFX` vs. a lightweight custom
> `TableView<FeatureListRow>` with only ID/mz/RT/annotation. Default: reuse `FeatureTableFX`
> with disabled column types (less code, consistent UX).

---

## 9. Controls panel (`controls/QcDashboardControlsBuilder`)

Right-hand column (mock img_6.png). No separate MVCI — it just binds widgets to the main
model:
- **Abundance** combo → `model.abundance` (`AbundanceMeasure` values).
- **Sample types to plot** `CheckComboBox` (sample-type strings from metadata) →
  `model.sampleTypesToShow` (default selects QC only).
- **Batch metadata column** selector via `MetadataGroupingComponent` →
  `model.batchColumn` (String/Number column; empty → all files in one group).
- (Optional) threshold spinners for `goodQualityFraction` / `warwickFraction`.
- Buttons that launch batch-safe filtering (see §10).

---

## 10. Batch-safe filtering (list-mutating actions)

Any action that produces a filtered feature list must run through a
`MZmineProcessingModule` so it is reproducible in batch mode. The dashboard only launches
these (prefilled), it does not filter inline.

**Reuse what exists** (`modules/dataprocessing/filter_rowsfilter/`):
- **RSD "too good to be true"** (sketch: drop QC RSD < 0.1%) and the normal QC-RSD filter →
  `RsdFilterParameters` (already supports a `Metadata1GroupSelection` on sample type = QC and
  a max-RSD threshold). The existing filter removes *above* a threshold; a *minimum*-RSD
  ("too good") cutoff likely needs a small extension to `RsdFilter`/its parameters (add an
  optional lower bound). Verify against current `RsdFilter` before deciding extend-vs-new.
- **Minimum detections / "feature in ≥X% of QCs"** (Plot 4 thresholds) →
  `MinimumSamplesInOneMetadataGroupParameter` in `RowsFilterParameters` (group = QC). The
  dashboard's threshold value maps directly to this parameter.
- **Blank contribution** (sketch: blank area / QC area; remove if blank > ~5% of QC) →
  `modules/dataprocessing/filter_blanksubtraction/` (`FeatureListBlankSubtractionModule`).
  Confirm it expresses the ratio test; if not, prefer extending it over a new module.

**Launch pattern from the dashboard:** build the module's `ParameterSet`, prefill from the
current dashboard state (sample-type column, QC group, thresholds, abundance), and invoke the
module via its standard entry point (parameter dialog → `MZmineCore` runs the task), so the
step is logged in the batch queue like any other module run.

**Only add a new module if** a needed criterion has no existing home (e.g. a combined
"QC filter" convenience module). If added, follow the standard Module/Parameters/Task triad
(see `IntensityNormalizerModule` as the simplest template) and register it in
`BatchModeModulesList`.

Purely visual controls (sample types shown, batch coloring, which row is selected) stay in the
model and **never** create tasks.

---

## 11. Out-of-scope / deferred (from sketch "other")

Track but do not build in v1; revisit after the core grid works:
- Pump-pressure correlation across datasets.
- Hotelling's T² confidence ellipse on a PCA scores plot (could later embed the existing
  `PCAController`).
- "Before/after batch correction" comparison panel (the 6th grid cell in the mock).
- Median-intensity-across-files companion to Plot 1.

---

## 12. Resolved decisions

1. **Feature-list source:** ✅ Always the aligned feature list selected in the main window
   (like the stats dashboard). No in-dashboard list picker.
2. **Row-selection table:** ✅ Reuse `FeatureTableFX` with graphical/per-file column types
   disabled (show ID / m/z / RT / preferred annotation), per §8.
3. **RSD "too good to be true":** ✅ Extend the existing `RsdFilter` / `RsdFilterParameters`
   with an optional **lower-bound** (minimum-RSD) cutoff. Use a **default of zero** so all
   current call sites are unaffected; where the filter is constructed via a constructor,
   **add an overload** that defaults the lower bound to 0 (keep existing constructors working).
   No new module.
4. **Filtering UX:** ✅ Filter buttons **open the standard module parameter dialog prefilled**
   from dashboard state (QC group, thresholds, abundance). Runs through the normal module path
   so the step is logged in the batch queue.

Still my call during implementation (low impact):
- **Plots 5 & 6:** one controller exposing two `SimpleXYChart`s, or two sibling controllers.
  Default: one `deviation` controller exposing both charts (they share all inputs).

---

## 13. Suggested build order (milestones)

1. **Scaffold main MVCI** (`Model/Controller/ViewBuilder/Interactor/Tab/Module` + registration)
   showing an empty grid + controls + reduced feature table; feature list flows in and the
   table populates. *(mvci-gui skill)*
2. **Interactor derivations**: `orderedFiles` (date-sorted), `qcFiles`, `fileColors`
   (+ `QcDashboardColorService`); verify with logging.
3. **Plot 1 + Plots 5/6** (cheap, single-row) end-to-end to validate the subplot-MVCI +
   binding + SimpleChart + click-selection pattern. *(new-chart skill)*
4. **Plots 2, 3, 4** with `FxUpdateTask` off-thread computation + threshold markers.
5. **Controls panel** fully wired (abundance, sample types, batch column, thresholds).
6. **Batch-safe filtering hooks** (RSD, min-detections, blank) — reuse/extend modules.
7. Polish: SD/RSD overlay (Plot 1), histogram mode (Plot 4), resizing, tooltips, colors.
8. Deferred items (§11) as follow-ups.
```
