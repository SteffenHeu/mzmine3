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

package io.github.mzmine.modules.dataprocessing.filter_diams2.sliding_mz;

import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.impl.SimpleDataPoint;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.batchmode.BatchModeModule;
import io.github.mzmine.modules.batchmode.BatchModeParameters;
import io.github.mzmine.modules.batchmode.BatchQueue;
import io.github.mzmine.modules.dataprocessing.filter_diams2.DiaCorrelationOptions;
import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.modules.tools.batchwizard.subparameters.AnnotationWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.DataImportWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.FilterWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.IonInterfaceHplcWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassDetectorWizardOptions;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassSpectrometerWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.WizardStepParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.custom_parameters.WizardMassDetectorNoiseLevels;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.AnnotationWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.DataImportWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.FilterWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.IonInterfaceWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.IonMobilityWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.MassSpectrometerWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.workflows.WorkflowDIA;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.OriginalFeatureListHandlingParameter.OriginalFeatureListOption;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance.Unit;
import io.github.mzmine.project.ProjectService;
import io.github.mzmine.util.scans.ScanAlignment;
import io.github.mzmine.util.scans.similarity.SpectralSimilarity;
import io.github.mzmine.util.spectraldb.entry.DBEntryField;
import io.github.mzmine.util.spectraldb.entry.SpectralDBAnnotation;
import io.github.mzmine.util.spectraldb.entry.SpectralLibrary;
import io.github.mzmine.util.spectraldb.entry.SpectralLibraryEntry;
import io.github.mzmine.util.spectraldb.parser.UnsupportedFormatException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import testutils.MZmineTestUtil;
import testutils.TaskResult;

/**
 * Run/debug with at least {@code -Xmx12g}; Gradle's default 512 MB test heap is too small for this
 * WIFF2/sliding-m/z data set.
 */
class DiaSlidingMzTaskIntegrationTest {

  @BeforeEach
  void resetDiagnosticsBeforeTest() {
    DiaSlidingMzDiagnostics.reset();
  }

  @AfterEach
  void resetDiagnosticsAfterTest() {
    DiaSlidingMzDiagnostics.reset();
  }

  @Test
  void runDiaOnWiff2ForInspection() throws InterruptedException {
    final File[] rawFiles = {new File(
        "F:\\Testdaten\\sciex\\nist_1950_pest\\Control Data\\8600_Meta_Pos_ZTScan_05.0Da_CE30_17min_060-609_NIST1950_iDQuant-0.0625_01.mzML")};
    final Range<Double> rtRange = Range.closed(11.1, 11.3);
    final double targetFeatureMz = 353.2244;
    final float targetFeatureRt = Float.NaN;

    // Optional sliding-m/z diagnostics. Add the expected fragments and, if available, their
    // relative intensities. Leave both arrays empty to disable diagnostic logging.
    final String diagnosticSpectrumLabel = "target feature";
    final double[] diagnosticExpectedFragmentMzs = {};
    final double[] diagnosticExpectedFragmentIntensities = {};
    runDiaForInspection(rawFiles, rtRange, targetFeatureMz, targetFeatureRt,
        diagnosticSpectrumLabel, diagnosticExpectedFragmentMzs,
        diagnosticExpectedFragmentIntensities);
  }

  @Test
  void runExpectedTargetFromLibraryForInspection()
      throws IOException, UnsupportedFormatException, InterruptedException {

    final int expectedTargetRowId = 2754;
    final File targetListFile = new File(
        "F:\\Testdaten\\sciex\\nist_1950_pest\\export\\sliding_dda_expected_targets.tsv");
    final File spectralLibraryFile = new File(
        "F:\\Testdaten\\sciex\\nist_1950_pest\\Library\\Pesticide_Library "
            + "isotopesFiltered_withRT.msp");
    final File[] rawFiles = {new File(
        "F:\\Testdaten\\sciex\\nist_1950_pest\\Control Data\\8600_Meta_Pos_ZTScan_05.0Da_CE30_17min_060-609_NIST1950_iDQuant-0.0625_01.mzML")};
    final double rtPaddingMinutes = 0.3d;
    final MZTolerance librarySpectrumMzTolerance = new MZTolerance(0.01, 25);
    final RTTolerance librarySpectrumRtTolerance = new RTTolerance(0.15f, Unit.MINUTES);
    final double minimumExpectedRelativeIntensity = 0.001d;

    Assumptions.assumeTrue(targetListFile.exists(),
        "Set targetListFile to the generated expected-target TSV.");
    Assumptions.assumeTrue(spectralLibraryFile.exists(),
        "Set spectralLibraryFile to the RT-enriched MSP library.");

    final DiaSlidingMzExpectedTarget target = parseExpectedTarget(targetListFile,
        expectedTargetRowId);
    final SpectralLibrary library = DiaSlidingMzLibraryBenchmark.parseLibrary(spectralLibraryFile);
    final SpectralLibraryEntry expectedLibrarySpectrum = DiaSlidingMzLibraryBenchmark.requireExpectedSpectrum(
        library, spectralLibraryFile, target, librarySpectrumMzTolerance,
        librarySpectrumRtTolerance);
    final DataPoint[] unfilteredExpectedPeaks = toDataPoints(expectedLibrarySpectrum);
    final double libraryBasePeakIntensity = Arrays.stream(unfilteredExpectedPeaks)
        .mapToDouble(DataPoint::getIntensity).max()
        .orElseThrow(() -> new IllegalArgumentException("Expected library spectrum has no peaks."));
    final DataPoint[] expectedPeaks = Arrays.stream(unfilteredExpectedPeaks).filter(
            peak -> peak.getIntensity() > libraryBasePeakIntensity * minimumExpectedRelativeIntensity)
        .toArray(DataPoint[]::new);
    final double[] expectedFragmentMzs = Arrays.stream(expectedPeaks).mapToDouble(DataPoint::getMZ)
        .toArray();
    final double[] expectedFragmentIntensities = Arrays.stream(expectedPeaks)
        .mapToDouble(DataPoint::getIntensity).toArray();
    final Range<Double> rtRange = Range.closed(Math.max(0d, target.rt() - rtPaddingMinutes),
        target.rt() + rtPaddingMinutes);
    final String diagnosticLabel = "%s / target row %d".formatted(target.compoundName(),
        target.rowId());

    System.out.printf(Locale.ROOT,
        "Expected target row %d: %s, m/z %.6f, RT %.4f, height %.3f; library '%s' "
            + "at m/z %.6f and RT %.4f with %d/%d peaks above %.3f%% relative intensity; "
            + "processing RT %.4f-%.4f min.%n", target.rowId(), target.compoundName(), target.mz(),
        target.rt(), target.height(),
        DiaSlidingMzLibraryBenchmark.entryName(expectedLibrarySpectrum),
        expectedLibrarySpectrum.getPrecursorMZ(),
        expectedLibrarySpectrum.getAsFloat(DBEntryField.RT).orElseThrow(), expectedPeaks.length,
        unfilteredExpectedPeaks.length, minimumExpectedRelativeIntensity * 100d,
        rtRange.lowerEndpoint(), rtRange.upperEndpoint());

    runDiaForInspection(rawFiles, rtRange, target.mz(), target.rt(), diagnosticLabel,
        expectedFragmentMzs, expectedFragmentIntensities);
  }

  private void runDiaForInspection(final File @NotNull [] rawFiles,
      @NotNull final Range<Double> rtRange, final double targetFeatureMz,
      final float targetFeatureRt, @NotNull final String diagnosticSpectrumLabel,
      final double @NotNull [] diagnosticExpectedFragmentMzs,
      final double @NotNull [] diagnosticExpectedFragmentIntensities) throws InterruptedException {
    // Input and preprocessing settings.
    final double ms1NoiseLevel = 500d;
    final double ms2NoiseLevel = 100d;
    final double minimumFeatureHeight = 1_000d;
    final int minimumRtDataPoints = 4;
    final int maximumIsomersInRt = 15;
    final RTTolerance approximateRtFwhm = new RTTolerance(0.1f, Unit.MINUTES);
    final MZTolerance scanToScanMzTolerance = new MZTolerance(0.005, 20);
    final MZTolerance featureToFeatureMzTolerance = new MZTolerance(0.0015, 3);
    final MZTolerance sampleToSampleMzTolerance = new MZTolerance(0.004, 8);

    // DIA settings. Change diaAlgorithm to RT_CORRELATION to bypass sliding m/z.
    final DiaCorrelationOptions diaAlgorithm = DiaCorrelationOptions.SLIDING_MZ;
    final DiaCorrelationOptions slidingMzPregrouping = DiaCorrelationOptions.NO_CORRELATION;
    final double minimumDiaMs1Intensity = 1_000d;
    final double minimumDiaMs2Intensity = 30d;
    final double minimumNoCorrelationIntensity = 30d;
    final double minimumPearson = 0.8d;
    final int minimumCorrelatedPoints = 5;
    final MZTolerance diaMzTolerance = new MZTolerance(0.005, 15);
    final long batchTimeoutMinutes = 20;

    // Native WIFF2 import requires a logged-in MZmine user.
    final File mzmineUserFile = new File(
        "C:\\Users\\Steffen\\.mzmine\\users\\mziosteffenheu.mzuser");

    // Optional library inspection settings.
    final File spectralLibraryFile = new File(
        "F:\\Testdaten\\sciex\\nist_1950_pest\\Library\\Pesticide_Library "
            + "isotopesFiltered_withRT.msp");
    final int featureListIndex = 0;
    final int featureRowId = -1;
    final String libraryEntryId = null;
    final MZTolerance libraryPeakTolerance = new MZTolerance(0.01, 15);
    final MZTolerance targetFeatureMzTolerance = new MZTolerance(0.01, 25);

    final MZTolerance diagnosticPrecursorTolerance = new MZTolerance(0.01, 25);
    final RTTolerance diagnosticRtTolerance = new RTTolerance(0.1f, Unit.MINUTES);
    final MZTolerance diagnosticFragmentTolerance = new MZTolerance(0.005, 15);
    configureExpectedSpectrumDiagnostics(targetFeatureMz, targetFeatureRt, diagnosticSpectrumLabel,
        diagnosticPrecursorTolerance, diagnosticRtTolerance, diagnosticFragmentTolerance,
        diagnosticExpectedFragmentMzs, diagnosticExpectedFragmentIntensities);
    DiaSlidingMzDiagnostics.LOG_SHAPE_METRICS = diagnosticExpectedFragmentMzs.length > 0;

    Assumptions.assumeTrue(Arrays.stream(rawFiles).allMatch(File::exists),
        "Set rawFiles to existing local WIFF2 files before running this integration test.");
    Assumptions.assumeTrue(mzmineUserFile != null && mzmineUserFile.exists(),
        "Set mzmineUserFile to a valid .mzuser file because native WIFF2 import requires login.");
    Assumptions.assumeTrue(spectralLibraryFile == null || spectralLibraryFile.exists(),
        "Set spectralLibraryFile to an existing library or leave it null.");

    MZmineCore.main(new String[]{"-r", "-m", "all", "-pref", "null", "-user",
        mzmineUserFile.getAbsolutePath()});
    MZmineTestUtil.cleanProject();

    final WizardSequence wizardSteps = createWizardSteps(rawFiles, spectralLibraryFile, rtRange,
        ms1NoiseLevel, ms2NoiseLevel, minimumFeatureHeight, scanToScanMzTolerance,
        featureToFeatureMzTolerance, sampleToSampleMzTolerance);
    final BatchQueue queue = new DiaSlidingMzBatchBuilder(wizardSteps, rtRange, minimumRtDataPoints,
        approximateRtFwhm, maximumIsomersInRt, diaAlgorithm, slidingMzPregrouping,
        minimumDiaMs1Intensity, minimumDiaMs2Intensity, minimumNoCorrelationIntensity,
        minimumPearson, minimumCorrelatedPoints, diaMzTolerance, null).createQueue();

    final ParameterSet batchParameters = new BatchModeParameters().cloneParameterSet();
    batchParameters.setParameter(BatchModeParameters.batchQueue, queue);
    final TaskResult result = MZmineTestUtil.callModuleWithTimeout(batchTimeoutMinutes,
        TimeUnit.MINUTES, BatchModeModule.class, batchParameters);
    Assertions.assertInstanceOf(TaskResult.FINISHED.class, result, result.description());

    final List<RawDataFile> importedRawFiles = ProjectService.getProject().getCurrentRawDataFiles();
    Assertions.assertFalse(importedRawFiles.isEmpty(), "The batch did not import any raw files.");
    Assertions.assertTrue(importedRawFiles.stream().flatMap(raw -> raw.getScans().stream())
            .allMatch(scan -> rtRange.contains((double) scan.getRetentionTime())),
        "At least one imported scan is outside the requested RT range.");

    final List<FeatureList> featureLists = ProjectService.getProject().getCurrentFeatureLists();
    Assertions.assertFalse(featureLists.isEmpty(),
        "The batch did not create a resolved feature list.");
    Assertions.assertTrue(featureListIndex >= 0 && featureListIndex < featureLists.size(),
        "featureListIndex is outside the available feature-list range.");
    final FeatureList featureList = featureLists.get(featureListIndex);
    Assertions.assertFalse(featureList.getRows().isEmpty(),
        "The resolved feature list has no rows.");

    final FeatureListRow selectedFeatureRow = selectFeatureRow(featureList, featureRowId,
        targetFeatureMz, targetFeatureRt);
    if (Double.isFinite(targetFeatureMz)) {
      Assertions.assertNotNull(selectedFeatureRow,
          "No feature was available for the target m/z %.6f".formatted(targetFeatureMz));
      Assertions.assertTrue(targetFeatureMzTolerance.checkWithinTolerance(targetFeatureMz,
              selectedFeatureRow.getAverageMZ()),
          "Closest feature m/z %.6f does not match target m/z %.6f within %s".formatted(
              selectedFeatureRow.getAverageMZ(), targetFeatureMz, targetFeatureMzTolerance));
    }
    final Scan featureMs2 =
        selectedFeatureRow == null ? null : selectedFeatureRow.getMostIntenseFragmentScan();
    final SpectralLibraryEntry requestedLibrarySpectrum = selectLibrarySpectrum(
        ProjectService.getProject().getCurrentSpectralLibraries(), libraryEntryId,
        selectedFeatureRow);
    final List<DataPoint[]> requestedEntryAlignedPeaks = alignSpectra(featureMs2,
        requestedLibrarySpectrum, libraryPeakTolerance);
    final SpectralDBAnnotation bestLibraryMatch = selectBestLibraryMatch(selectedFeatureRow);
    final List<DataPoint[]> bestMatchAlignedPeaks = getTaskAlignedPeaks(bestLibraryMatch);

    featureLists.forEach(DiaSlidingMzTaskIntegrationTest::printFeatureListSummary);
    if (selectedFeatureRow != null && requestedLibrarySpectrum != null) {
      Assertions.assertNotNull(featureMs2,
          "The selected feature does not have a pseudo-MS2 spectrum.");
      printPeakAlignment("SPECIFIC LIBRARY ENTRY", selectedFeatureRow, requestedLibrarySpectrum,
          requestedEntryAlignedPeaks);
    } else {
      System.out.println("""
          Specific-entry peak alignment not requested. Set spectralLibraryFile and either
          featureRowId or targetFeatureMz/targetFeatureRt. Optionally set libraryEntryId;
          otherwise the closest library precursor m/z is used.
          """);
    }

    if (selectedFeatureRow != null && bestLibraryMatch != null) {
      printBestLibraryMatch(selectedFeatureRow, bestLibraryMatch, bestMatchAlignedPeaks);
    } else if (selectedFeatureRow != null) {
      System.out.printf(Locale.ROOT, "Feature row %d has no spectral-library search result.%n",
          selectedFeatureRow.getID());
    }

    // Set a breakpoint here to inspect featureLists, selectedFeatureRow, featureMs2, the requested
    // library spectrum/alignment, and the best spectral-library task result/alignment.
    Assertions.assertNotNull(featureList);
  }

  static @NotNull DiaSlidingMzExpectedTarget parseExpectedTarget(@NotNull final File targetListFile,
      final int expectedRowId) throws IOException {
    return parseExpectedTargets(targetListFile).stream()
        .filter(target -> target.rowId() == expectedRowId).findFirst().orElseThrow(
            () -> new IllegalArgumentException(
                "No row_id %d in expected-target list %s".formatted(expectedRowId,
                    targetListFile.getAbsolutePath())));
  }

  static @NotNull List<DiaSlidingMzExpectedTarget> parseExpectedTargets(
      @NotNull final File targetListFile) throws IOException {
    final List<DiaSlidingMzExpectedTarget> targets = new ArrayList<>();
    try (BufferedReader reader = Files.newBufferedReader(targetListFile.toPath(),
        StandardCharsets.UTF_8)) {
      final String headerLine = reader.readLine();
      if (headerLine == null) {
        throw new IllegalArgumentException(
            "Expected-target list is empty: " + targetListFile.getAbsolutePath());
      }

      final String[] headers = headerLine.split("\t", -1);
      final Map<String, Integer> columnIndices = new HashMap<>();
      for (int i = 0; i < headers.length; i++) {
        columnIndices.put(headers[i].replace("\uFEFF", "").strip(), i);
      }

      final int rowIdIndex = requireTargetColumn(columnIndices, "row_id", targetListFile);
      final int mzIndex = requireTargetColumn(columnIndices, "mz", targetListFile);
      final int rtIndex = requireTargetColumn(columnIndices, "rt", targetListFile);
      final int heightIndex = requireTargetColumn(columnIndices, "height", targetListFile);
      final int compoundIndex = requireTargetColumn(columnIndices, "expected_compound",
          targetListFile);
      final int requiredColumns =
          Math.max(Math.max(Math.max(rowIdIndex, mzIndex), Math.max(rtIndex, heightIndex)),
              compoundIndex) + 1;

      String line;
      int lineNumber = 1;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.isBlank()) {
          continue;
        }
        final String[] values = line.split("\t", -1);
        if (values.length < requiredColumns) {
          throw new IllegalArgumentException(
              "Expected-target row %d has only %d columns in %s".formatted(lineNumber,
                  values.length, targetListFile.getAbsolutePath()));
        }
        final int rowId = Integer.parseInt(values[rowIdIndex].strip());
        targets.add(
            new DiaSlidingMzExpectedTarget(rowId, Double.parseDouble(values[mzIndex].strip()),
                Float.parseFloat(values[rtIndex].strip()),
                Double.parseDouble(values[heightIndex].strip()), values[compoundIndex].strip()));
      }
    }
    return List.copyOf(targets);
  }

  private static int requireTargetColumn(@NotNull final Map<String, Integer> columnIndices,
      @NotNull final String columnName, @NotNull final File targetListFile) {
    final Integer index = columnIndices.get(columnName);
    if (index == null) {
      throw new IllegalArgumentException(
          "Missing column '%s' in expected-target list %s".formatted(columnName,
              targetListFile.getAbsolutePath()));
    }
    return index;
  }

  private static void configureExpectedSpectrumDiagnostics(final double precursorMz, final float rt,
      @NotNull final String label, @NotNull final MZTolerance precursorTolerance,
      @NotNull final RTTolerance rtTolerance, @NotNull final MZTolerance fragmentTolerance,
      final double @NotNull [] expectedFragmentMzs,
      final double @NotNull [] expectedFragmentIntensities) {
    if (expectedFragmentMzs.length == 0) {
      return;
    }
    if (expectedFragmentIntensities.length != 0
        && expectedFragmentIntensities.length != expectedFragmentMzs.length) {
      throw new IllegalArgumentException(
          "Expected fragment intensities must either be empty or match the fragment m/z count.");
    }

    final RangeMap<Double, ExpectedFragment> fragments = TreeRangeMap.create();
    for (int i = 0; i < expectedFragmentMzs.length; i++) {
      final double intensity =
          expectedFragmentIntensities.length == 0 ? Double.NaN : expectedFragmentIntensities[i];
      fragments.put(fragmentTolerance.getToleranceRange(expectedFragmentMzs[i]),
          new ExpectedFragment(expectedFragmentMzs[i], intensity));
    }

    final Range<Double> rtRange;
    if (Float.isFinite(rt)) {
      final Range<Float> toleranceRange = rtTolerance.getToleranceRange(rt);
      rtRange = Range.closed((double) toleranceRange.lowerEndpoint(),
          (double) toleranceRange.upperEndpoint());
    } else {
      rtRange = Range.all();
    }
    DiaSlidingMzDiagnostics.EXPECTED_SPECTRA.add(
        new ExpectedSpectrum(label, precursorTolerance.getToleranceRange(precursorMz), rtRange,
            fragments));
  }

  static @NotNull WizardSequence createWizardSteps(final File @NotNull [] rawFiles,
      @Nullable final File spectralLibraryFile, @NotNull final Range<Double> rtRange,
      final double ms1NoiseLevel, final double ms2NoiseLevel, final double minimumFeatureHeight,
      @NotNull final MZTolerance scanToScanMzTolerance,
      @NotNull final MZTolerance featureToFeatureMzTolerance,
      @NotNull final MZTolerance sampleToSampleMzTolerance) {

    final double mzmldivisior =
        rawFiles[0].getName().endsWith("mzML") ? DiaSlidingMzBatchBuilder.MZML_DIVISOR : 1;

    final WizardSequence steps = new WizardSequence();

    final DataImportWizardParameters dataImport = (DataImportWizardParameters) DataImportWizardParameterFactory.Data.create();
    dataImport.setParameter(DataImportWizardParameters.fileNames, rawFiles);
    steps.add(dataImport);

    final IonInterfaceHplcWizardParameters ionInterface = (IonInterfaceHplcWizardParameters) IonInterfaceWizardParameterFactory.UHPLC.create();
    ionInterface.setParameter(IonInterfaceHplcWizardParameters.cropRtRange, rtRange);
    ionInterface.setParameter(IonInterfaceHplcWizardParameters.smoothing, false);
    ionInterface.setParameter(IonInterfaceHplcWizardParameters.approximateChromatographicFWHM,
        new RTTolerance(0.04f, Unit.MINUTES));
    steps.add(ionInterface);
    steps.add(IonMobilityWizardParameterFactory.NO_IMS.create());

    final MassSpectrometerWizardParameters massSpectrometer = (MassSpectrometerWizardParameters) MassSpectrometerWizardParameterFactory.QTOF.create();
    massSpectrometer.setParameter(MassSpectrometerWizardParameters.massDetectorOption,
        new WizardMassDetectorNoiseLevels(MassDetectorWizardOptions.ABSOLUTE_NOISE_LEVEL,
            ms1NoiseLevel / mzmldivisior, ms2NoiseLevel / mzmldivisior));
    massSpectrometer.setParameter(MassSpectrometerWizardParameters.minimumFeatureHeight,
        minimumFeatureHeight / mzmldivisior);
    massSpectrometer.setParameter(MassSpectrometerWizardParameters.scanToScanMzTolerance,
        scanToScanMzTolerance);
    massSpectrometer.setParameter(MassSpectrometerWizardParameters.featureToFeatureMzTolerance,
        featureToFeatureMzTolerance);
    massSpectrometer.setParameter(MassSpectrometerWizardParameters.sampleToSampleMzTolerance,
        sampleToSampleMzTolerance);
    steps.add(massSpectrometer);

    final FilterWizardParameters filters = (FilterWizardParameters) FilterWizardParameterFactory.Filters.create();
    filters.setParameter(FilterWizardParameters.handleOriginalFeatureLists,
        OriginalFeatureListOption.REMOVE);
    filters.setParameter(FilterWizardParameters.goodPeaksOnly, false);
    steps.add(filters);

    final AnnotationWizardParameters annotation = (AnnotationWizardParameters) AnnotationWizardParameterFactory.Annotation.create();
    annotation.setParameter(AnnotationWizardParameters.dataBaseFiles,
        spectralLibraryFile == null ? new File[0] : new File[]{spectralLibraryFile});
    steps.add(annotation);

    final WizardStepParameters workflow = new WorkflowDIA().create();
    steps.add(workflow);
    return steps;
  }

  private static @Nullable FeatureListRow selectFeatureRow(@NotNull final FeatureList featureList,
      final int featureRowId, final double targetFeatureMz, final float targetFeatureRt) {
    if (featureRowId > 0) {
      return featureList.getRows().stream().filter(row -> row.getID() == featureRowId).findFirst()
          .orElse(null);
    }
    if (!Double.isFinite(targetFeatureMz)) {
      return null;
    }

    return featureList.getRows().stream().filter(row -> row.getAverageMZ() != null)
        // decision: generated row IDs are parameter-dependent, so m/z is the primary selector.
        .min(Comparator.<FeatureListRow>comparingDouble(
            row -> Math.abs(row.getAverageMZ() - targetFeatureMz)).thenComparingDouble(
            row -> !Float.isFinite(targetFeatureRt) || row.getAverageRT() == null ? 0d
                : Math.abs(row.getAverageRT() - targetFeatureRt))).orElse(null);
  }

  private static @Nullable SpectralLibraryEntry selectLibrarySpectrum(
      @NotNull final List<SpectralLibrary> libraries, @Nullable final String libraryEntryId,
      @Nullable final FeatureListRow featureRow) {
    if (libraries.isEmpty() || featureRow == null) {
      return null;
    }

    final List<SpectralLibraryEntry> entries = libraries.stream().flatMap(SpectralLibrary::stream)
        .toList();
    if (libraryEntryId != null && !libraryEntryId.isBlank()) {
      return entries.stream().filter(entry -> libraryEntryId.equals(
              Objects.toString(entry.getField(DBEntryField.ENTRY_ID).orElse(null), null))).findFirst()
          .orElse(null);
    }

    final Double featureMz = featureRow.getAverageMZ();
    if (featureMz == null) {
      return null;
    }
    return entries.stream().filter(entry -> entry.getPrecursorMZ() != null)
        .min(Comparator.comparingDouble(entry -> Math.abs(entry.getPrecursorMZ() - featureMz)))
        .orElse(null);
  }

  private static @Nullable SpectralDBAnnotation selectBestLibraryMatch(
      @Nullable final FeatureListRow featureRow) {
    if (featureRow == null) {
      return null;
    }
    return featureRow.getSpectralLibraryMatches().stream().max(Comparator.comparingDouble(
            match -> Objects.requireNonNullElse(match.getScore(), Float.NEGATIVE_INFINITY)))
        .orElse(null);
  }

  private static @NotNull List<DataPoint[]> alignSpectra(@Nullable final Scan featureMs2,
      @Nullable final SpectralLibraryEntry librarySpectrum,
      @NotNull final MZTolerance peakTolerance) {
    if (featureMs2 == null || librarySpectrum == null) {
      return List.of();
    }

    final DataPoint[] featurePeaks = toDataPoints(featureMs2);
    final DataPoint[] libraryPeaks = toDataPoints(librarySpectrum);
    final List<DataPoint[]> aligned = new ArrayList<>(
        ScanAlignment.align(peakTolerance, featurePeaks, libraryPeaks));
    aligned.sort(Comparator.comparingDouble(pair -> {
      final DataPoint point = pair[0] != null ? pair[0] : pair[1];
      return point.getMZ();
    }));
    return aligned;
  }

  /**
   * Reconstructs matched and unmatched pairs from the filtered spectra retained by the library
   * search task. Pair order is query/feature first, library second.
   */
  private static @NotNull List<DataPoint[]> getTaskAlignedPeaks(
      @Nullable final SpectralDBAnnotation match) {
    if (match == null) {
      return List.of();
    }

    final SpectralSimilarity similarity = match.getSimilarity();
    final DataPoint[] filteredLibrary = similarity.getLibrary();
    final DataPoint[] filteredQuery = similarity.getQuery();
    final DataPoint[][] matched = similarity.getAlignedDataPoints();
    if (filteredLibrary == null || filteredQuery == null || matched == null || matched.length < 2) {
      return List.of();
    }

    final List<DataPoint[]> result = new ArrayList<>();
    final Set<DataPoint> matchedLibrary = new HashSet<>(Arrays.asList(matched[0]));
    final Set<DataPoint> matchedQuery = new HashSet<>(Arrays.asList(matched[1]));
    for (int i = 0; i < Math.min(matched[0].length, matched[1].length); i++) {
      result.add(new DataPoint[]{matched[1][i], matched[0][i]});
    }
    Arrays.stream(filteredQuery).filter(point -> !matchedQuery.contains(point))
        .forEach(point -> result.add(new DataPoint[]{point, null}));
    Arrays.stream(filteredLibrary).filter(point -> !matchedLibrary.contains(point))
        .forEach(point -> result.add(new DataPoint[]{null, point}));
    result.sort(Comparator.comparingDouble(pair -> {
      final DataPoint point = pair[0] != null ? pair[0] : pair[1];
      return point.getMZ();
    }));
    return result;
  }

  static DataPoint @NotNull [] toDataPoints(@NotNull final MassSpectrum spectrum) {
    final int size = spectrum.getNumberOfDataPoints();
    final double[] mzs = spectrum.getMzValues(new double[size]);
    final double[] intensities = spectrum.getIntensityValues(new double[size]);
    final DataPoint[] points = new DataPoint[size];
    for (int i = 0; i < size; i++) {
      points[i] = new SimpleDataPoint(mzs[i], intensities[i]);
    }
    return points;
  }

  private static void printFeatureListSummary(@NotNull final FeatureList featureList) {
    final long featuresWithMs2 = featureList.getRows().stream().map(FeatureListRow::getBestFeature)
        .filter(Objects::nonNull)
        .filter(feature -> feature.getFeatureStatus() != FeatureStatus.UNKNOWN)
        .filter(Feature::hasMs2Fragmentation).count();
    System.out.printf(Locale.ROOT,
        "Feature list '%s': %d rows, %d features with pseudo-MS2 spectra.%n", featureList.getName(),
        featureList.getNumberOfRows(), featuresWithMs2);
  }

  private static void printBestLibraryMatch(@NotNull final FeatureListRow featureRow,
      @NotNull final SpectralDBAnnotation bestMatch,
      @NotNull final List<DataPoint[]> alignedPeaks) {
    final SpectralSimilarity similarity = bestMatch.getSimilarity();
    System.out.printf(Locale.ROOT,
        "Best spectral-library task result: score %.4f, %d matched peaks, "
            + "%.2f%% explained library intensity, function '%s'.%n", similarity.getScore(),
        similarity.getOverlap(), similarity.getExplainedLibraryIntensity() * 100d,
        similarity.getFunctionName());
    printPeakAlignment("BEST SPECTRAL-LIBRARY TASK RESULT", featureRow, bestMatch.getEntry(),
        alignedPeaks);
  }

  private static void printPeakAlignment(@NotNull final String reportTitle,
      @NotNull final FeatureListRow featureRow, @NotNull final SpectralLibraryEntry librarySpectrum,
      @NotNull final List<DataPoint[]> alignedPeaks) {
    final String entryId = Objects.toString(
        librarySpectrum.getField(DBEntryField.ENTRY_ID).orElse(""), "");
    final String name = Objects.toString(librarySpectrum.getField(DBEntryField.NAME).orElse(""),
        "");
    System.out.println(reportTitle);
    System.out.printf(Locale.ROOT,
        "Feature row %d (m/z %.6f, RT %.3f) vs library '%s' [%s], precursor m/z %s%n",
        featureRow.getID(), featureRow.getAverageMZ(), featureRow.getAverageRT(), name, entryId,
        Objects.toString(librarySpectrum.getPrecursorMZ(), ""));
    System.out.println(
        "status\tfeature_mz\tfeature_intensity\tlibrary_mz\tlibrary_intensity\tdelta_mz");

    for (final DataPoint[] pair : alignedPeaks) {
      final DataPoint featurePeak = pair[0];
      final DataPoint libraryPeak = pair[1];
      final String status = featurePeak != null && libraryPeak != null ? "MATCHED"
          : featurePeak != null ? "FEATURE_ONLY" : "LIBRARY_ONLY";
      final String deltaMz =
          featurePeak != null && libraryPeak != null ? String.format(Locale.ROOT, "%.6f",
              featurePeak.getMZ() - libraryPeak.getMZ()) : "";
      System.out.printf(Locale.ROOT, "%s\t%s\t%s\t%s\t%s\t%s%n", status, formatMz(featurePeak),
          formatIntensity(featurePeak), formatMz(libraryPeak), formatIntensity(libraryPeak),
          deltaMz);
    }
  }

  private static @NotNull String formatMz(@Nullable final DataPoint point) {
    return point == null ? "" : String.format(Locale.ROOT, "%.6f", point.getMZ());
  }

  private static @NotNull String formatIntensity(@Nullable final DataPoint point) {
    return point == null ? "" : String.format(Locale.ROOT, "%.3f", point.getIntensity());
  }
}
