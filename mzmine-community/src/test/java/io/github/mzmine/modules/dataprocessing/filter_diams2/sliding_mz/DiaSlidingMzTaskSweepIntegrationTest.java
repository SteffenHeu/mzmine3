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
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.batchmode.BatchModeModule;
import io.github.mzmine.modules.batchmode.BatchModeParameters;
import io.github.mzmine.modules.batchmode.BatchQueue;
import io.github.mzmine.modules.dataprocessing.filter_diams2.DiaCorrelationOptions;
import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance.Unit;
import io.github.mzmine.util.XMLUtils;
import io.github.mzmine.util.files.ExtensionFilters;
import io.github.mzmine.util.files.FileAndPathUtil;
import io.github.mzmine.util.spectraldb.entry.DBEntryField;
import io.github.mzmine.util.spectraldb.entry.SpectralLibrary;
import io.github.mzmine.util.spectraldb.entry.SpectralLibraryEntry;
import io.github.mzmine.util.spectraldb.parser.UnsupportedFormatException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import testutils.MZmineTestUtil;
import testutils.TaskResult;

/**
 * Expensive local comparison of DIA pseudo-MS2 workflows over the full RT-annotated library.
 * Run/debug with at least {@code -Xmx12g}.
 */
class DiaSlidingMzTaskSweepIntegrationTest {

  @Test
  void runFullLibraryDiaAlgorithmComparison()
      throws IOException, UnsupportedFormatException, InterruptedException {
    final File exportDirectory = new File("F:\\Testdaten\\sciex\\nist_1950_pest\\export");
    final File[] rawFiles = {new File(
        "F:\\Testdaten\\sciex\\nist_1950_pest\\Control Data\\8600_Meta_Pos_ZTScan_05.0Da_CE30_17min_060-609_NIST1950_iDQuant-0.0625_01.mzML")};
    final File spectralLibraryFile = new File(
        "F:\\Testdaten\\sciex\\nist_1950_pest\\Library\\Pesticide_Library "
            + "isotopesFiltered_withRT.msp");
    final File mzmineUserFile = new File(
        "C:\\Users\\Steffen\\.mzmine\\users\\mziosteffenheu.mzuser");
    final Path resultDirectory = exportDirectory.toPath()
        .resolve("dia_deconvolution_comparison_full_library_zt5da_060-609");

    final double rtPaddingMinutes = 0.3d;
    final double minimumExpectedRelativeIntensity = 0.001d;
    final MZTolerance diagnosticPrecursorTolerance = new MZTolerance(0.01, 25);
    final RTTolerance diagnosticRtTolerance = new RTTolerance(0.2f, Unit.MINUTES);
    final MZTolerance diagnosticFragmentTolerance = new MZTolerance(0.005, 15);
    final List<DiaSlidingMzSweepConfig> configs = List.of(
        new DiaSlidingMzSweepConfig("sliding_mz_rt_correlation", DiaCorrelationOptions.SLIDING_MZ,
            DiaCorrelationOptions.RT_CORRELATION, 100, 500),
        new DiaSlidingMzSweepConfig("rt_correlation", DiaCorrelationOptions.RT_CORRELATION,
            DiaCorrelationOptions.NO_CORRELATION, 100, 500),
        new DiaSlidingMzSweepConfig("sliding_mz_no_correlation", DiaCorrelationOptions.SLIDING_MZ,
            DiaCorrelationOptions.NO_CORRELATION, 100, 500),
        new DiaSlidingMzSweepConfig("no_correlation", DiaCorrelationOptions.NO_CORRELATION,
            DiaCorrelationOptions.NO_CORRELATION, 100, 500));
    Assertions.assertTrue(configs.stream()
            .allMatch(config -> config.ms2NoiseLevel() == configs.getFirst().ms2NoiseLevel()),
        "Appended configurations must share one MS2 mass-detection noise level.");

    Assumptions.assumeTrue(Arrays.stream(rawFiles).allMatch(File::exists),
        "ZT-scan raw data file is missing.");
    Assumptions.assumeTrue(spectralLibraryFile.exists(), "Spectral library is missing.");
    Assumptions.assumeTrue(mzmineUserFile.exists(), "MZmine user file is missing.");

    final SpectralLibrary library = DiaSlidingMzLibraryBenchmark.parseLibrary(spectralLibraryFile);
    final List<ExpectedSpectrum> expectedSpectra = createExpectedSpectra(library,
        diagnosticPrecursorTolerance, diagnosticRtTolerance, diagnosticFragmentTolerance,
        minimumExpectedRelativeIntensity);
    Assertions.assertFalse(expectedSpectra.isEmpty(),
        "The library has no entries with m/z and RT.");
    final int benchmarkTargetCount = expectedSpectra.size();
    final int expectedPeakCount = expectedSpectra.stream().mapToInt(
            spectrum -> (int) spectrum.fragments().asMapOfRanges().values().stream().distinct().count())
        .sum();
    final double minimumRt = expectedSpectra.stream()
        .mapToDouble(spectrum -> spectrum.rtRange().lowerEndpoint()).min().orElseThrow();
    final double maximumRt = expectedSpectra.stream()
        .mapToDouble(spectrum -> spectrum.rtRange().upperEndpoint()).max().orElseThrow();
    final Range<Double> importRtRange = Range.closed(Math.max(0d, minimumRt - rtPaddingMinutes),
        maximumRt + rtPaddingMinutes);

    MZmineCore.main(new String[]{"-r", "-m", "all", "-pref", "null", "-user",
        mzmineUserFile.getAbsolutePath()});
    final Logger diagnosticLogger = Logger.getLogger(DiaSlidingMzTask.class.getName());
    final Level previousLogLevel = diagnosticLogger.getLevel();
    final DiaSlidingMzDiagnosticLogHandler diagnosticHandler = new DiaSlidingMzDiagnosticLogHandler();
    diagnosticHandler.setConfigurations(configs);
    diagnosticLogger.addHandler(diagnosticHandler);
    diagnosticLogger.setLevel(Level.WARNING);

    try {
      DiaSlidingMzDiagnostics.reset();
      DiaSlidingMzDiagnostics.EXPECTED_SPECTRA.addAll(expectedSpectra);
      MZmineTestUtil.cleanProject();
      System.out.printf(Locale.ROOT,
          "Starting %d DIA workflows after one import: apex finder '%s' (signal ratio 100), "
              + "shape acceptance '%s' (edge window %.2fx, top/edge ratio %.3f, points %d,"
              + " zero margin %d), " + "MS2 noise %.1f, "
              + "fragment minimum %.1f, %d targets, %d expected peaks, RT %.4f-%.4f min.%n",
          configs.size(), HighIntensityDespikedSlidingMzTraceApexFinder.class.getSimpleName(),
          DiaSlidingMzTask.SHAPE_ACCEPTANCE_MODE, DiaSlidingMzTask.SHAPE_EDGE_WINDOW_MULTIPLIER,
          DiaSlidingMzTask.MINIMUM_SHAPE_TOP_EDGE_RATIO,
          DiaSlidingMzTask.MINIMUM_SHAPE_CONSECUTIVE_POINTS,
          DiaSlidingMzTask.SHAPE_ZERO_MARGIN_INDICES, configs.getFirst().ms2NoiseLevel(),
          configs.getFirst().minimumFragmentIntensity(), benchmarkTargetCount, expectedPeakCount,
          importRtRange.lowerEndpoint(), importRtRange.upperEndpoint());

      final BatchQueue queue = createBatchQueue(rawFiles, spectralLibraryFile, exportDirectory,
          importRtRange, configs);
      saveBatchSteps(new File(resultDirectory.toFile(), "batch.mzbatch"), queue);
      final ParameterSet batchParameters = new BatchModeParameters().cloneParameterSet();
      batchParameters.setParameter(BatchModeParameters.batchQueue, queue);
      final TaskResult result = MZmineTestUtil.callModuleWithTimeout(120, TimeUnit.MINUTES,
          BatchModeModule.class, batchParameters);
      Assertions.assertInstanceOf(TaskResult.FINISHED.class, result, result.description());
    } catch (ParserConfigurationException | TransformerException e) {
      throw new RuntimeException(e);
    } finally {
      try {
        // decision: persist diagnostics even if an expensive workflow fails after earlier steps.
        DiaSlidingMzSweepResultWriter.write(resultDirectory, diagnosticHandler.getRecords(),
            configs, benchmarkTargetCount, expectedPeakCount);
      } finally {
        DiaSlidingMzDiagnostics.reset();
        diagnosticLogger.removeHandler(diagnosticHandler);
        diagnosticLogger.setLevel(previousLogLevel);
      }
    }

    System.out.printf(Locale.ROOT, "Wrote DIA workflow comparison statistics to %s.%n",
        resultDirectory);
  }

  private static @NotNull List<ExpectedSpectrum> createExpectedSpectra(
      @NotNull final SpectralLibrary library,
      @NotNull final MZTolerance diagnosticPrecursorTolerance,
      @NotNull final RTTolerance diagnosticRtTolerance,
      @NotNull final MZTolerance diagnosticFragmentTolerance,
      final double minimumExpectedRelativeIntensity) {
    final List<SpectralLibraryEntry> libraryEntries = library.stream().toList();
    final List<ExpectedSpectrum> expectedSpectra = new ArrayList<>(libraryEntries.size());
    for (final SpectralLibraryEntry librarySpectrum : libraryEntries) {
      final Double precursorMz = librarySpectrum.getPrecursorMZ();
      final Float libraryRt = librarySpectrum.getAsFloat(DBEntryField.RT).orElse(null);
      final String name = DiaSlidingMzLibraryBenchmark.entryName(librarySpectrum);
      if (precursorMz == null || libraryRt == null) {
        System.out.printf(Locale.ROOT, "Excluding library entry '%s': precursor m/z %s, RT %s.%n",
            name, precursorMz == null ? "missing" : String.format(Locale.ROOT, "%.6f", precursorMz),
            libraryRt == null ? "missing" : String.format(Locale.ROOT, "%.4f", libraryRt));
        continue;
      }

      final DataPoint[] allPeaks = DiaSlidingMzTaskIntegrationTest.toDataPoints(librarySpectrum);
      final double basePeakIntensity = Arrays.stream(allPeaks).mapToDouble(DataPoint::getIntensity)
          .max().orElseThrow(
              () -> new IllegalArgumentException("Library spectrum has no peaks for " + name));
      final RangeMap<Double, ExpectedFragment> fragments = TreeRangeMap.create();
      Arrays.stream(allPeaks).filter(
              peak -> peak.getIntensity() > basePeakIntensity * minimumExpectedRelativeIntensity)
          .forEach(
              peak -> fragments.put(diagnosticFragmentTolerance.getToleranceRange(peak.getMZ()),
                  new ExpectedFragment(peak.getMZ(), peak.getIntensity())));
      final String label = "%s / library m/z %.6f / RT %.4f".formatted(name, precursorMz,
          libraryRt);
      final Range<Float> targetRtRange = diagnosticRtTolerance.getToleranceRange(libraryRt);
      expectedSpectra.add(
          new ExpectedSpectrum(label, diagnosticPrecursorTolerance.getToleranceRange(precursorMz),
              Range.closed((double) targetRtRange.lowerEndpoint(),
                  (double) targetRtRange.upperEndpoint()), fragments));
    }
    return List.copyOf(expectedSpectra);
  }

  private static @NotNull BatchQueue createBatchQueue(final File @NotNull [] rawFiles,
      @NotNull final File spectralLibraryFile, @NotNull final File exportDirectory,
      @NotNull final Range<Double> importRtRange,
      @NotNull final List<DiaSlidingMzSweepConfig> configs) {
    final double ms1NoiseLevel = 500d;
    final double minimumFeatureHeight = 1000d;
    final int minimumRtDataPoints = 4;
    final int maximumIsomersInRt = 15;
    final RTTolerance approximateRtFwhm = new RTTolerance(0.04f, Unit.MINUTES);
    final MZTolerance scanToScanMzTolerance = new MZTolerance(0.005, 20);
    final MZTolerance featureToFeatureMzTolerance = new MZTolerance(0.0015, 3);
    final MZTolerance sampleToSampleMzTolerance = new MZTolerance(0.004, 8);
    final double minimumDiaMs1Intensity = 100;
    final double minimumPearson = 0.8d;
    final int minimumCorrelatedPoints = 3;
    final MZTolerance diaMzTolerance = new MZTolerance(0.005, 15);

    final WizardSequence wizardSteps = DiaSlidingMzTaskIntegrationTest.createWizardSteps(rawFiles,
        spectralLibraryFile, importRtRange, ms1NoiseLevel, configs.getFirst().ms2NoiseLevel(),
        minimumFeatureHeight, scanToScanMzTolerance, featureToFeatureMzTolerance,
        sampleToSampleMzTolerance);
    final DiaSlidingMzSweepConfig firstConfig = configs.getFirst();
    final DiaSlidingMzBatchBuilder builder = new DiaSlidingMzBatchBuilder(wizardSteps,
        importRtRange, minimumRtDataPoints, approximateRtFwhm, maximumIsomersInRt,
        firstConfig.diaAlgorithm(), firstConfig.slidingMzPregrouping(), minimumDiaMs1Intensity,
        firstConfig.minimumFragmentIntensity(), firstConfig.minimumFragmentIntensity(),
        minimumPearson, minimumCorrelatedPoints, diaMzTolerance, null);
    return builder.createQueueForConfigurations(configs, exportDirectory);
  }

  private void saveBatchSteps(File file, BatchQueue queue)
      throws ParserConfigurationException, TransformerException, IOException {

    // Create the document.
    final Document document = XMLUtils.newDocument();
    final Element element = document.createElement("batch");
    document.appendChild(element);

    // Serialize batch queue.
    queue.saveToXml(element);

    String extension = ExtensionFilters.getFirstCleanExtensionName(ExtensionFilters.MZ_BATCH);
    file = FileAndPathUtil.getRealFilePath(file, extension);

    XMLUtils.saveToFile(file, document);
  }
}
