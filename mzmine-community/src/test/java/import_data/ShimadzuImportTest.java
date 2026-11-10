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

package import_data;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.impl.SimpleScan;
import io.github.mzmine.datamodel.otherdetectors.OtherDataFileImpl;
import io.github.mzmine.datamodel.otherdetectors.OtherFeature;
import io.github.mzmine.datamodel.otherdetectors.OtherTimeSeriesDataImpl;
import io.github.mzmine.gui.preferences.ShimadzuImportOptions;
import io.github.mzmine.gui.preferences.VendorImportParameters;
import io.github.mzmine.gui.preferences.WatersLockmassParameters;
import io.github.mzmine.modules.io.import_rawdata_all.AllSpectralDataImportModule;
import io.github.mzmine.modules.io.import_rawdata_all.AllSpectralDataImportParameters;
import io.github.mzmine.modules.io.import_rawdata_all.spectral_processor.ScanImportProcessorConfig;
import io.github.mzmine.modules.io.import_rawdata_mzml.msdk.data.ChromatogramType;
import io.github.mzmine.modules.io.import_rawdata_shimadzu.ShimadzuImportTask;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.project.ProjectService;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.files.FileAndPathUtil;
import io.github.mzmine.modules.io.import_rawdata_shimadzu.ShimadzuDataAccess;
import io.github.mzmine.project.impl.RawDataFileImpl;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import testutils.MZmineTestUtil;

/**
 * End-to-end checks against real Shimadzu files through the bridge child process.
 * <p>
 * The two reference files are committed test resources, so these run in CI. They cover complementary
 * facets: the {@code .lcd} is MRM-only with LC status curves and no importable spectra at all, the
 * {@code .qgd} is GC-MS full-scan with no non-MS facet.
 * <p>
 * Windows-only: the bridge wraps a Windows-only, x64-only COM-backed SDK.
 */
@DisabledOnOs({OS.MAC, OS.LINUX})
public class ShimadzuImportTest {

  /**
   * MRM-only triple-quad LC-MS: 39070 vendor "scans" that are all acquisition cycles, 168
   * transitions, 3 LC status curves.
   */
  private static final String MRM_RESOURCE = "rawdatafiles/shimadzu_mrm_tq.lcd";

  /**
   * GC-MS full scan: 12400 spectra, no transitions, no LSS facet.
   */
  private static final String GC_RESOURCE = "rawdatafiles/shimadzu_gc.qgd";

  @BeforeAll
  static void init() {
    MZmineTestUtil.startMzmineCore();
  }

  /**
   * The reference data is committed, but the bridge itself is not:
   * {@code external_tools/shimadzu_wrapper/} is gitignored, like the other native vendor tools. On a
   * runner without a deployed bridge these report as skipped rather than failing.
   */
  @BeforeEach
  void assumeBridgeDeployed() {
    final File exe = FileAndPathUtil.resolveInExternalToolsDir(
        "shimadzu_wrapper/ShimadzuBridge.exe");
    Assumptions.assumeTrue(exe.isFile(),
        () -> "ShimadzuBridge.exe is not deployed at " + exe
            + " (external_tools/shimadzu_wrapper is gitignored)");
  }

  /**
   * Resolve a committed test resource to a real file. The bridge is a separate process that opens
   * the path itself, so the resource has to exist on disk — which it does, both from the source tree
   * and from the test runtime classpath.
   */
  private static File resourceFile(String resource) {
    final URL url = ShimadzuImportTest.class.getClassLoader().getResource(resource);
    Assertions.assertNotNull(url, "missing test resource: " + resource);
    try {
      final File file = new File(url.toURI());
      Assertions.assertTrue(file.isFile(), "test resource is not a file: " + file);
      return file;
    } catch (URISyntaxException e) {
      throw new IllegalStateException("cannot resolve test resource " + resource, e);
    }
  }

  @Test
  public void testMrmOnlyFileReportsNoSpectra() throws Exception {
    try (final var access = new ShimadzuDataAccess(resourceFile(MRM_RESOURCE), null)) {
      final var caps = access.capabilities();

      // The core guarantee: 39070 vendor scans, zero importable spectra. Deriving
      // "has spectra" from the scan count would import them all as junk MS2.
      Assertions.assertFalse(caps.hasMassSpectra(), "MRM-only file must report no mass spectra");
      Assertions.assertEquals(39070, caps.vendorScanCount());
      Assertions.assertEquals(0, caps.expectedSpectra());
      Assertions.assertTrue(caps.hasMrmTraces());
      Assertions.assertEquals(168, caps.mrmTraceCount());
      Assertions.assertTrue(caps.hasAnalogTraces());
      Assertions.assertEquals(3, caps.analogTraceCount());

      // Facet states must stay distinguishable: the LC file supports detector data
      // channels but stores none, while it does store status curves.
      Assertions.assertEquals("empty", caps.analogDataChannelState());
      Assertions.assertEquals("present", caps.analogStatusChannelState());
    }
  }

  @Test
  public void testMrmTransitionsAreImportedAsTraces() throws Exception {
    try (final var access = new ShimadzuDataAccess(resourceFile(MRM_RESOURCE), null)) {
      final var descriptors = access.listMrmTraces();
      Assertions.assertEquals(168, descriptors.size());

      // Channels of one event are distinct transitions; collapsing on
      // (segment, event) would leave a single trace here.
      final long inSegment1Event1 = descriptors.stream()
          .filter(d -> d.segment() == 1 && d.event() == 1).count();
      Assertions.assertEquals(7, inSegment1Event1,
          "segment 1 / event 1 must expose one transition per channel");

      final var first = descriptors.getFirst();
      Assertions.assertEquals("MRM", first.kind());
      Assertions.assertEquals(89.0, first.q1(), 1e-6);
      Assertions.assertEquals(72.0, first.q3(), 1e-6);

      final File mrmFile = resourceFile(MRM_RESOURCE);
      final RawDataFileImpl raw = new RawDataFileImpl(mrmFile.getName(),
          mrmFile.getAbsolutePath(), null);
      final OtherTimeSeriesDataImpl data = newTimeSeriesData(raw);

      final OtherFeature trace = access.readMrmTrace(first, data);
      Assertions.assertNotNull(trace);
      final var series = trace.getFeatureData();
      Assertions.assertEquals(347, series.getNumberOfValues());
      // First point of channel 1 matches the m/z 72.0 peak of vendor scan 1.
      Assertions.assertEquals(0f, series.getRetentionTime(0), 1e-6);
      Assertions.assertEquals(100d, series.getIntensity(0), 1e-6);
      // Retention times are minutes on the wire, not the SDK's milliseconds.
      Assertions.assertTrue(series.getRetentionTime(346) < 10f,
          "retention time must be in minutes");
    }
  }

  @Test
  public void testAnalogStatusCurvesAreImported() throws Exception {
    try (final var access = new ShimadzuDataAccess(resourceFile(MRM_RESOURCE), null)) {
      final var descriptors = access.listAnalogTraces();
      Assertions.assertEquals(3, descriptors.size());

      final var pressure = descriptors.getFirst();
      Assertions.assertEquals("Pump A Pressure", pressure.name());
      Assertions.assertEquals("LC-20AD", pressure.detectorType());
      Assertions.assertEquals("PRESSURE", pressure.signalType());
      // The raw vendor unit must survive verbatim.
      Assertions.assertEquals("MPa", pressure.rawUnit());

      final File mrmFile = resourceFile(MRM_RESOURCE);
      final RawDataFileImpl raw = new RawDataFileImpl(mrmFile.getName(),
          mrmFile.getAbsolutePath(), null);
      final OtherTimeSeriesDataImpl data = newTimeSeriesData(raw);

      final OtherFeature trace = access.readAnalogTrace(pressure, data);
      Assertions.assertNotNull(trace);
      final var series = trace.getFeatureData();
      Assertions.assertEquals(1921, series.getNumberOfValues());
      // Values are imported exactly as the SDK reports them, unconverted.
      Assertions.assertEquals(55.2d, series.getIntensity(0), 1e-6);
      Assertions.assertEquals(0f, series.getRetentionTime(0), 1e-6);
      Assertions.assertEquals(32f, series.getRetentionTime(1920), 1e-3);
    }
  }

  @Test
  public void testGcFileImportsSpectraAndHasNoTraces() throws Exception {
    final File gcFile = resourceFile(GC_RESOURCE);
    try (final var access = new ShimadzuDataAccess(gcFile, null)) {
      final var caps = access.capabilities();
      Assertions.assertTrue(caps.hasMassSpectra());
      Assertions.assertEquals(12400, caps.vendorScanCount());
      Assertions.assertEquals(12400, caps.expectedSpectra());
      Assertions.assertFalse(caps.spectraRequireFiltering());
      Assertions.assertFalse(caps.hasMrmTraces());

      // .qgd has no LSS facet at all — that is "unsupported", not "empty".
      Assertions.assertFalse(caps.hasAnalogTraces());
      Assertions.assertEquals("unsupported", caps.analogDataChannelState());
      Assertions.assertEquals("unsupported", caps.analogStatusChannelState());

      final RawDataFileImpl raw = new RawDataFileImpl(gcFile.getName(), gcFile.getAbsolutePath(),
          null);
      final List<SimpleScan> scans = new ArrayList<>();
      final var result = access.readAllScans(raw, ScanImportProcessorConfig.createDefault(), false,
          scans::add, () -> {
          }, () -> false);

      Assertions.assertEquals(12400, result.imported());
      Assertions.assertEquals(0, result.skippedTargeted());
      Assertions.assertEquals(0, result.skippedFailed());
      Assertions.assertEquals(12400, scans.size());

      final SimpleScan first = scans.getFirst();
      Assertions.assertEquals(1, first.getScanNumber());
      Assertions.assertEquals(1, first.getMSLevel());
      Assertions.assertEquals(3.0f, first.getRetentionTime(), 1e-4);
      Assertions.assertEquals(386, first.getNumberOfDataPoints());
      // Base peak is derived from the shipped peak list, not from the vendor's
      // BPMass field, which uses a different scale on .qgd.
      Assertions.assertEquals(60.05, first.getBasePeakMz(), 1e-4);
      Assertions.assertNull(first.getMsMsInfo(), "MS1 scan must carry no MS/MS info");
    }
  }

  /**
   * Full import-task run on the MRM-only file. This is the wiring the data-access tests above cannot
   * cover: that the transitions and status curves actually end up attached to the raw data file, and
   * that no spectra are added.
   */
  @Test
  public void testFullTaskOnMrmOnlyFile() {
    final File mrmFile = resourceFile(MRM_RESOURCE);

    final VendorImportParameters vendorParam = VendorImportParameters.create(true,
        VendorImportParameters.DEFAULT_WATERS_OPTION, ShimadzuImportOptions.SHIMADZU_BRIDGE,
        VendorImportParameters.DEFAULT_WATERS_LOCKMASS_ENABLED,
        WatersLockmassParameters.createDefault(),
        VendorImportParameters.DEFAULT_THERMO_EXCEPTION_SIGNALS);
    final ParameterSet parameters = AllSpectralDataImportParameters.create(vendorParam,
        new File[]{mrmFile}, null, null);

    final ShimadzuImportTask task = new ShimadzuImportTask(null, Instant.now(), mrmFile,
        AllSpectralDataImportModule.class, parameters, ProjectService.getProject(),
        ScanImportProcessorConfig.createDefault());
    task.run();

    Assertions.assertEquals(TaskStatus.FINISHED, task.getStatus(), task.getErrorMessage());
    final List<RawDataFile> imported = task.getImportedRawDataFiles();
    Assertions.assertEquals(1, imported.size());

    final RawDataFile raw = imported.getFirst();
    // No spectra: every vendor scan in this file is an MRM acquisition cycle.
    Assertions.assertEquals(0, raw.getNumOfScans());

    // One other-data file for the transitions, plus one per analog unit (MPa, C).
    final var otherFiles = raw.getOtherDataFiles();
    Assertions.assertEquals(3, otherFiles.size());

    final var mrmOtherFile = otherFiles.stream()
        .filter(f -> "MRM/SRM".equals(f.getDescription())).findFirst().orElseThrow();
    final var mrmData = mrmOtherFile.getOtherTimeSeriesData();
    Assertions.assertEquals(ChromatogramType.MRM_SRM, mrmData.getChromatogramType());
    Assertions.assertEquals(168, mrmData.getRawTraces().size());

    final int analogTraces = otherFiles.stream().filter(f -> f != mrmOtherFile)
        .mapToInt(f -> f.getOtherTimeSeriesData().getRawTraces().size()).sum();
    Assertions.assertEquals(3, analogTraces, "3 status curves across 2 unit groups");
  }

  private static OtherTimeSeriesDataImpl newTimeSeriesData(RawDataFileImpl raw) {
    final OtherDataFileImpl otherFile = new OtherDataFileImpl(raw);
    final OtherTimeSeriesDataImpl data = new OtherTimeSeriesDataImpl(otherFile);
    otherFile.setOtherTimeSeriesData(data);
    return data;
  }
}
