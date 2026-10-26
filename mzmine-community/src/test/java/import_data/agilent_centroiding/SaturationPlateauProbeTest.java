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

package import_data.agilent_centroiding;

import com.opencsv.CSVWriter;
import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.impl.SimpleFrame;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max.SaturationPlateauEstimator;
import io.github.mzmine.modules.io.import_rawdata_agilent_d.AgilentDataAccess;
import io.github.mzmine.project.impl.IMSRawDataFileImpl;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import testutils.MZmineTestUtil;

/**
 * Diagnostic (not an accuracy test): the saturation plateau of the auto saturation threshold of the
 * adaptive local max detector, separately for frames and mobility scans. Reads all frames of every
 * raw file in profile mode and applies {@link SaturationPlateauEstimator} to the base peaks per MS
 * level. Writes {@code saturation_plateaus.csv} to the results folder.
 */
@Disabled("Agilent centroiding benchmark, run manually")
@TestInstance(Lifecycle.PER_CLASS)
@DisabledOnOs({OS.MAC, OS.LINUX})
public class SaturationPlateauProbeTest {

  private static final Logger logger = Logger.getLogger(SaturationPlateauProbeTest.class.getName());

  @BeforeAll
  void init() {
    MZmineTestUtil.startMzmineCore();
  }

  @Test
  void estimatePlateaus() throws IOException {
    final File resultsDir = new File(AgilentCentroidingTestConfig.rootDir(),
        AgilentCentroidingTestConfig.RESULTS_DIR_NAME);
    resultsDir.mkdirs();

    int rows = 0;
    try (final CSVWriter writer = new CSVWriter(
        new FileWriter(new File(resultsDir, "saturation_plateaus.csv"), StandardCharsets.UTF_8))) {
      writer.writeNext(
          new String[]{"dataset", "raw_file", "ms_level", "abundance_limit", "frames", "frame_max",
              "frames_within_1pct", "frame_plateau", "mobility_scans", "mobility_scan_max",
              "mobility_scans_within_1pct", "mobility_scan_plateau"}, false);
      for (final CentroidingDataset dataset : CentroidingDataset.values()) {
        if (!dataset.exists()) {
          continue;
        }
        for (final File rawFile : dataset.rawFiles()) {
          rows += probeFile(dataset, rawFile, writer);
        }
      }
    }
    logger.info("Wrote %d plateau rows".formatted(rows));
    Assertions.assertTrue(rows > 0);
  }

  private static int probeFile(@NotNull final CentroidingDataset dataset,
      @NotNull final File rawFile, @NotNull final CSVWriter writer) {
    // base peaks per ms level
    final Map<Integer, DoubleArrayList> frameBasePeaks = new TreeMap<>();
    final Map<Integer, DoubleArrayList> scanBasePeaks = new TreeMap<>();
    try (final AgilentDataAccess access = AgilentCentroidingTestConfig.openProfile(rawFile)) {
      final IMSRawDataFileImpl file = (IMSRawDataFileImpl) access.createDataFile();
      for (int id = 1; id <= access.getFrameCount(); id++) {
        final SimpleFrame frame = access.readFrame(file, id);
        if (frame == null) {
          continue;
        }
        final int level = frame.getMSLevel();
        frameBasePeaks.computeIfAbsent(level, _ -> new DoubleArrayList()).add(max(frame));
        final DoubleArrayList scans = scanBasePeaks.computeIfAbsent(level,
            _ -> new DoubleArrayList());
        for (int i = 0; i < frame.getNumberOfMobilityScans(); i++) {
          scans.add(max(frame.getMobilityScan(i)));
        }
      }
    }

    final double limit = FileIntensityMaxima.readAbundanceLimit(rawFile);
    for (final int level : frameBasePeaks.keySet()) {
      final double[] frames = frameBasePeaks.get(level).toDoubleArray();
      final double[] scans = scanBasePeaks.get(level).toDoubleArray();
      writer.writeNext(
          new String[]{dataset.name(), dataset.relativePath(rawFile), String.valueOf(level),
              num(limit), String.valueOf(frames.length), num(max(frames)),
              String.valueOf(withinPlateau(frames)),
              num(SaturationPlateauEstimator.estimatePlateau(frames)), String.valueOf(scans.length),
              num(max(scans)), String.valueOf(withinPlateau(scans)),
              num(SaturationPlateauEstimator.estimatePlateau(scans))}, false);
      logger.info("%s MS%d: frame plateau %s (%d within 1 %%), mobility scan plateau %s".formatted(
          rawFile.getName(), level, num(SaturationPlateauEstimator.estimatePlateau(frames)),
          withinPlateau(frames), num(SaturationPlateauEstimator.estimatePlateau(scans))));
    }
    return frameBasePeaks.size();
  }

  /**
   * @return number of base peaks within the plateau tolerance of the highest base peak
   */
  private static int withinPlateau(final double @NotNull [] basePeaks) {
    final double start = max(basePeaks) * (1 - SaturationPlateauEstimator.PLATEAU_TOLERANCE);
    int n = 0;
    for (final double basePeak : basePeaks) {
      if (basePeak >= start) {
        n++;
      }
    }
    return n;
  }

  private static double max(final double @NotNull [] values) {
    double max = 0;
    for (final double v : values) {
      max = Math.max(max, v);
    }
    return max;
  }

  private static double max(@NotNull final MassSpectrum spectrum) {
    double max = 0;
    for (int i = 0; i < spectrum.getNumberOfDataPoints(); i++) {
      max = Math.max(max, spectrum.getIntensityValue(i));
    }
    return max;
  }

  private static @NotNull String num(final double value) {
    return Double.isNaN(value) ? "" : String.format(Locale.ROOT, "%.1f", value);
  }
}
