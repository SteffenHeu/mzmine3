---
name: Vendor MS data import (mzmine)
description: How to add mzmine raw-data import for a new vendor format (.d/.raw/...) via a per-file DataAccess class, a one-file-per-task import task, a delegator, and file-type dispatch. Use when wiring a vendor SDK (native lib or external bridge process) into mzmine's import. Blueprints - import_rawdata_masslynx (Waters, in-process native lib) and import_rawdata_agilent_d (Agilent, external bridge process).
---

Build the import from four pieces. Mirror an existing package: **`import_rawdata_masslynx`** (
Waters; in-process native lib via FFM) or **`import_rawdata_agilent_d`** (Agilent; external
subprocess "bridge" — see the `Vendor SDK bridge process` skill for the other side).

```
import_rawdata_<vendor>/
  <Vendor>DataAccess.java        // the ONLY touch point to the SDK/bridge; AutoCloseable
  <Vendor>ImportTask.java        // extends AbstractTask, RawDataImportTask; ONE file per task
  <Vendor>ImportTaskDelegator.java // extends AbstractSimpleTask; native-vs-msconvert switch
```

## 1. DataAccess — the single touch point

`public class <Vendor>DataAccess implements AutoCloseable`. The task calls *only* these methods;
nothing else in mzmine sees the SDK/wire details. Constructor opens the file (launch bridge / open
native handle), reads file-level metadata. Expose:

- `boolean isIms()`, `int getFrameCount()` / `long getScanCount()`.
- `RawDataFileImpl createDataFile()` — `IMSRawDataFileImpl` for IMS else `RawDataFileImpl` (both
  `(name, absPath, storage)`); imaging → `IMSImagingRawDataFileImpl`/`ImagingRawDataFileImpl`.
- non-IMS: `@Nullable SimpleScan readScan(RawDataFileImpl, int id)`.
- IMS: `@Nullable SimpleFrame readFrame(IMSRawDataFileImpl, int frameId)`.
- `@Nullable CCSCalibration getCcsCalibration()`; MRM/analog readers (below).
- `close()` releases the handle / kills the subprocess.

## 2. ImportTask — one file per task

`extends AbstractTask implements RawDataImportTask`, ctor
`(storage, moduleCallDate, file, module, parameters, project, processor)`. In `run()`:

1. `setStatus(PROCESSING)`;
   `VendorImportParameters vp = parameters.getValue(AllSpectralDataImportParameters.vendorOptions)`.
2. try-with-resources `<Vendor>DataAccess`; `dataFile = da.createDataFile()`.
3. Iterate: IMS → `for frame in 1..frameCount: readFrame`; non-IMS →
   `for id in 0..scanCount: readScan`. Collect non-null into `List<SimpleScan>` (SimpleFrame IS-A
   SimpleScan). Check `isCanceled()` each iteration; `loadedItems++`.
4. CCS: `if (dataFile instanceof IMSRawDataFile ims) ims.setCCSCalibration(da.getCcsCalibration())`.
5. Sort `Comparator.comparingDouble(Scan::getRetentionTime).thenComparing(Scan::getScanDefinition)`,
   then `scan.setScanNumber(i+1); dataFile.addScan(scan)`.
6.
`dataFile.getAppliedMethods().add(new SimpleFeatureListAppliedMethod(module, parameters, getModuleCallDate()))`;
`project.addFile(dataFile)`; `setStatus(FINISHED)`.
7. catch → `error(e.getMessage())`. `getImportedRawDataFiles()` returns `List.of(dataFile)` only
   when finished.

## 3. Delegator + dispatch

- `<Vendor>ImportTaskDelegator extends AbstractSimpleTask implements RawDataImportTask`, ctor
  `(storage, moduleCallDate, file, processorConfig, project, parameters, moduleClass)`. Pick native
  vs msconvert from `ConfigService.getPreference(MZminePreferences.<vendor>ImportChoice)`;
  `process()` runs `actualTask.run()` and propagates status; forward
  `addTaskStatusListener((_, s, _) -> actualTask.setStatus(s))`.
- `RawDataFileType` (util): add `VENDOR_X` / `VENDOR_X_IMS` if missing; `RawDataFileTypeDetector`
  detects the folder (e.g. `.d`) and the IMS sub-type by a marker file (Agilent:
  `AcqData/IMSFrame.bin`).
- `AllSpectralDataImportModule.createTask` AND `createAdvancedTask`: route your file type(s) to the
  delegator (split them out of the msconvert `case`).

## 4. Import-choice toggle (preferences)

- Options enum in `gui.preferences` implementing `UniqueIdSupplier` (e.g. `{NATIVE, MSCONVERT}`,
  default NATIVE).
- In `VendorImportParameters`: a `ComponentWrapperParameter<Opt, ComboParameter<Opt>>` (mirror
  `massLynxImportChoice`) with `createJumpToPrefButton`; add to the `super(...)` ctor list, to
  `create(...)`, `createDefault()`, `createFromPreferences()` (read the preference there). If you
  add a param to `create(...)`, update all callers (tests).
- In `MZminePreferences`:
  `<vendor>ImportChoice = VendorImportParameters.<vendor>ImportChoice.getEmbeddedParameter().cloneParameter()`;
  register in the ctor `Parameter[]` and the "MS data import" `ParameterGroup`.
- Per-instance centroiding reuses `VendorImportParameters.applyVendorCentroiding`.

## Data-model construction

- Scan:
  `new SimpleScan(file, scanNumber, msLevel, rt /*float min*/, msMsInfo, mz[], intensity[], MassSpectrumType, PolarityType, scanDefinition, Range<Double> scanMzRange)`.
- Frame:
  `new SimpleFrame(file, frameNumber, msLevel, rt, mz[], intensity[], type, polarity, def, mzRange, MobilityType.DRIFT_TUBE /*Agilent DTIMS; Waters=TRAVELING_WAVE*/, Set<IonMobilityMsMsInfo> precursorInfos /*null for MS1*/, Float accumulationTime)`;
  then `frame.setMobilityScans(List<BuildingMobilityScan>, massDetect)` and
  `frame.setMobilities(double[] driftTimes)`.
- Mobility scan: `new BuildingMobilityScan(binIndex, mz[], intensity[], MassSpectrumType)`.
- MsMsInfo: DDA →
  `DDAMsMsInfoImpl(precursorMz, charge, collisionEnergy, null, null, msLevel, ActivationMethod.CID, isolationWindow)`;
  non-IMS DIA / All-Ions / MSE (fragmentation, no isolated precursor) →
  `DIAMsMsInfoImpl(ce, null, msLevel, ActivationMethod.CID, isolationWindow)`; IMS MS2 →
  `DIAImsMsMsInfoImpl(Range.closed(0, numBins-1), ce, null, isolationWindow)`. Isolation window only
  when the vendor reports a real range (else null → precursor m/z only).
- CCS (drift tube, single field): `new DriftTubeCCSCalibration(beta, tfix, -1, -1)`. Or reuse the
  external XML reader (e.g. `AgilentImsCalibrationReader`).

## MRM/SRM and non-MS detectors = OtherTimeSeries, NOT spectra

MRM transitions and DAD/UV/instrument curves are chromatograms, never scans. Skip them in the scan
loop (Agilent: SDK reports them as `MultipleReaction` scans → bridge flags `hasMsScans`/
`msScanType`, importer skips the loop for MRM-only files and per-scan otherwise). Build:
`OtherDataFileImpl(dataFile)` → `OtherTimeSeriesDataImpl` (
`setChromatogramType(ChromatogramType.MRM_SRM)`, range/domain labels+units) → per trace
`new SimpleOtherTimeSeries(storage, float[] rts, double[] intensities, label, tsd)` +
`new OtherFeatureImpl(series)`; for MRM
`OtherFeatureUtils.applyMrmInfo(q1, q3, ActivationMethod.CID, null, feature)`;
`tsd.addRawTrace(feature)`; `dataFile.addOtherDataFiles(List.of(file))`. Group analog channels by
unit. See `MassLynxDataAccess.readMrm` / `readAndAddAnalogChannels`.

## Spectral processor (filter + mass detection on import)

Apply `ScanImportProcessorConfig`:

- Build a metadata-only `SimpleBuildingScan(0, msLevel, polarity, type, rt, precursorMz, 0)` (
  `datamodel.impl.builders`) for the filter and processor context.
- Filter:
  `if (processor.hasProcessors() && processor.scanFilter().isActiveFilter() && !processor.scanFilter().matches(meta)) skip`.
  With a metadata/peak split you can check the filter *before* fetching peak data and skip the peak
  read entirely.
- Process:
  `SimpleSpectralArrays out = processor.processor().processScan(meta, new SimpleSpectralArrays(mz, intensity))`;
  build the scan from `out`. Add `new ScanPointerMassList(scan)` when
  `processor.isMassDetectActive(msLevel)`.
- `spectrumType = centroided || processor.isMassDetectActive(msLevel) ? CENTROIDED : PROFILE`. If
  the SDK can't centroid (common for IMS), import PROFILE and let mzmine mass detection centroid.

## MS level for All-Ions / MSE (no explicit MS2 flag)

Vendors often store fragmentation scans/frames as MS-level "MS". Derive MS2: IMS → from the frame's
fragmentation class (HighEnergy → MS2); non-IMS All-Ions/MSE → from a non-zero **collision energy
** (CE>0 → MS2, CE=0 survey → MS1).

## Deployment & verification

- Native lib or bridge exe lives under `external_tools/<vendor>/`; resolve with
  `FileAndPathUtil.resolveInExternalToolsDir("<vendor>/<file>")`. Bridge: launch with
  `ProcessBuilder`, working dir = the exe folder so the runtime finds sibling DLLs. Windows-only
  SDK → delegator falls back to msconvert off-Windows.
- Compile: `./gradlew :mzmine-community:compileJava`. Then import a real file in the GUI and
  sanity-check against msconvert output. Add an import test mirroring `MassLynxImportTest` (skipped
  when the tool/test data is absent).
