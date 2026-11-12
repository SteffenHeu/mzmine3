# Agilent 6560 IMS centroiding benchmark

Benchmark of mass detection (centroiding) on Agilent 6560 drift tube IMS profile data, with a focus
on detector saturation in the mobility dimension. The results are documented on the mzmine
documentation page *Performance & Benchmarking → Centroiding accuracy (Agilent IMS)*.

All test classes in this package are disabled (`@Disabled`). They need Windows (native Agilent
reader) and the raw data in a local folder. Remove the annotation of a class to run it.

## Data

The data folder is set with `-Dmzmine.test.agilent.centroiding.dir` (default `E:/agilent test`). It
contains one folder per dataset (named like the `CentroidingDataset` constants) with the raw `.d`
folders and the target lists. Zipped `.d` folders must be unzipped. All `.d` folders with
`AcqData/IMSFrame.bin` up to 4 levels below the dataset folder are used.

| Dataset | Raw files (from MassIVE) | Target list |
|---|---|---|
| `MSV000096020` | `raw/Data Files/240624_FIA_ESI(-)/<amount>/SAmPAP*.d` and `6.2PAP*.d` from `10uL at 5 ng per mL`, `2 uL at 500 ng per mL`, `10 uL at 500 ng per mL` | `target_list/targets_FIA_analytes.csv` |
| `MSV000085979` | `other/Supplemental/SingleField_tunemix/` (folder with `I_Raw` and `II_Preprocessed`) | `targets_tunemix_ccs.tsv` (copy of `SingleField_tunemix/TuneMix-CCS.txt`) |
| `MSV000087295` | `raw/042319_RP_1D_Al_All_0002.d.zip`, `raw/042319_RP_1D_Al_All_0002_MA-d3-c3-Min20_SR.d.zip` | `target_list/Features_original.tsv`, `target_list/Features_preprocessed.tsv` (from `other/`) |
| `MSV000088206` | `raw/Micropunch_d_data/M1_T46_357_2000_positive.d`, `raw/M1_T51_357_500_positive.d` | `target_list/targets_lipids_downloaded_samples.csv` |
| `MSV000096126` | `C1`, `C2M`, `C4M`, `C8`, `TuneMix_01` `.d.DeMP.d` from `raw/Drift Tube Anakin Sept 24 (Use Me-Hot Source)/` | `target_list/targets_EPA533_from_skyline.csv`, `target_list/targets_PFAS_CCS_library.csv` |

The `.d` folders can be downloaded with FTPS from `massive-ftp.ucsd.edu` (TLS required).

### Target list formats

Readers: `CentroidingTargetListReader`. All targets get their m/z calculated from formula and
adduct; measured m/z values only select the adduct.

- `targets_FIA_analytes.csv`: `name, abbreviation, neutral_formula, adduct, ...` with the adduct as
  mzmine ionization name, e.g. `[M-H]-`. SAmPAP `C12H11F17NO6PS` and 6:2 PAP `C8H6F13O4P`.
- `targets_tunemix_ccs.tsv`: `CCS, m/z, ExactMass, Ionization, z`. Only the listed m/z and polarity
  are used; the tune mix formulas are generated (`HP-0321` + n × C6F12) and matched to the m/z.
- `Features_*.tsv`: Agilent Mass Profiler export (`ID Result`, `m/z`, `Sat.`). Only rows with a lipid
  ID are used, lipid names are converted to formulas by `LipidNameFormulaParser`.
- `targets_lipids_downloaded_samples.csv`: `lipid, ionization_mode, ...`, extracted from SI tables S2
  and S6 of Odenkirk et al. 2022 (ACS Meas. Sci. Au, doi 10.1021/acsmeasuresciau.1c00035).
- `targets_EPA533_from_skyline.csv`: `name, neutral_formula, ion_formula, label, charge, ...`,
  extracted from the Skyline document `6560 Quant Oct 2024.sky` (only `light` precursors are used).
- `targets_PFAS_CCS_library.csv`: the sheet `Skyline Template PFAS JND_23` of `Single Field
  PFHpS.xlsx` (`Molecule Name, Molecular Formula, Precursor Adduct, ..., Label Type`).

## Running

Run the tests with a large heap, e.g. with a gradle init script
`allprojects { tasks.withType<Test>().configureEach { maxHeapSize = "20g" } }`:

```
./gradlew -I heap.init.gradle.kts :mzmine-community:test --tests "import_data.agilent_centroiding.AgilentCentroidingAccuracyTest"
```

1. `AgilentTargetIndexGeneratorTest` imports every frame centroided and writes
   `<dataset>/target_index.csv`: per target the most intense MS1 frame, its most intense mobility
   scan and the saturation label (M+1/M more than 15 % above the predicted ratio). Existing indices
   are kept unless `-Dmzmine.test.agilent.centroiding.regenerateIndex=true` is set (~13 min for all
   datasets).
2. `AgilentCentroidingAccuracyTest` imports only the indexed frames in profile mode, centroids them
   with all `MassDetectorConfig.defaults()`, the Agilent frame centroiding and the mobility edge
   approach (`MobilogramCentroider`) and writes `<timestamp>_measurements.csv` and
   `<timestamp>_summary.csv` to `centroiding_results/` (~3 min).
3. Diagnostics:
   - `SaturationProfileScanTest`: raw apex heights of M and M+1 per mobility scan and the intensity
     maxima per file (`saturation_profile_scans.csv`, `saturation_file_maxima.csv`).
   - `SaturationPlateauProbeTest`: the auto saturation plateau of frames and mobility scans per
     file and MS level, over all frames (`saturation_plateaus.csv`, ~24 min).
   - `MassDetectorSpeedTest`: processing time per spectrum (logged).
   - `CentroidingExampleExportTest`: raw profile and centroids of the peaks listed in
     `centroiding_results/plot_examples.csv` (`dataset, raw_file, target, level`), written to
     `plot_example_*.csv`.
   - `LipidNameFormulaParserTest`: checks the lipid formulas against literature values (no data
     needed).
