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

package io.github.mzmine.modules.dataprocessing.filter_imstoraw;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.MassSpectrumType;
import io.github.mzmine.datamodel.MobilityType;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.impl.BuildingMobilityScan;
import io.github.mzmine.datamodel.impl.SimpleFrame;
import io.github.mzmine.datamodel.impl.SimpleScan;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.project.impl.IMSRawDataFileImpl;
import io.github.mzmine.project.impl.MZmineProjectImpl;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

class ImsToRawDataFileTaskTest {

  @Test
  void convertsAndMergesContinuouslyAcrossFrames() {
    final IMSRawDataFile source = new IMSRawDataFileImpl("ims", null, null, Color.BLACK);
    source.addScan(createFrame(source, 1, 1f, 100d, 3));
    source.addScan(createFrame(source, 2, 2f, 200d, 2));

    final ParameterSet parameters = new ImsToRawDataFileParameters();
    parameters.setParameter(ImsToRawDataFileParameters.mobilityScansToMerge, 2);
    parameters.setParameter(ImsToRawDataFileParameters.maxTicRsdDeviation, 3d);
    final MZmineProjectImpl project = new MZmineProjectImpl();
    project.addFile(source);

    final ImsToRawDataFileTask task = new ImsToRawDataFileTask(project, source, parameters, null,
        Instant.now());
    task.run();

    assertEquals(TaskStatus.FINISHED, task.getStatus());
    assertEquals(2, project.getDataFiles().length);
    final RawDataFile converted = project.getDataFiles()[1];
    assertFalse(converted instanceof IMSRawDataFile);
    assertEquals(3, converted.getNumOfScans());
    converted.getScans().forEach(scan -> assertInstanceOf(SimpleScan.class, scan));

    assertEquals(7f / 6f, converted.getScan(0).getRetentionTime(), 1e-6f);
    assertEquals(11f / 6f, converted.getScan(1).getRetentionTime(), 1e-6f);
    assertEquals(2.5f, converted.getScan(2).getRetentionTime(), 1e-6f);

    // This group crosses the frame boundary: frame 1 scan 2 + frame 2 scan 0.
    final Scan boundaryGroup = converted.getScan(1);
    assertArrayEquals(new double[]{102d, 200d}, boundaryGroup.getMzValues(new double[0]), 1e-10);
    assertArrayEquals(new double[]{3d, 4d}, boundaryGroup.getIntensityValues(new double[0]),
        1e-10);
    // Five source scans with groups of two leave one final, non-empty partial group.
    assertArrayEquals(new double[]{201d}, converted.getScan(2).getMzValues(new double[0]), 1e-10);
    assertArrayEquals(new double[]{5d}, converted.getScan(2).getIntensityValues(new double[0]),
        1e-10);
    assertEquals(1d, task.getFinishedPercentage());
  }

  @Test
  void interpolationUsesZeroBasedMobilityScanIndex() {
    assertEquals(5f, ImsToRawDataFileTask.interpolateRt(5f, 7f, 0, 4));
    assertEquals(6.5f, ImsToRawDataFileTask.interpolateRt(5f, 7f, 3, 4));
  }

  @Test
  void scanSelectionFiltersFramesAndDefinesInterpolationInterval() {
    final IMSRawDataFile source = new IMSRawDataFileImpl("ims", null, null, Color.BLACK);
    source.addScan(createFrame(source, 1, 1f, 100d, 2, 1));
    source.addScan(createFrame(source, 2, 2f, 200d, 2, 2));
    source.addScan(createFrame(source, 3, 4f, 300d, 2, 1));

    final ParameterSet parameters = new ImsToRawDataFileParameters();
    parameters.getParameter(ImsToRawDataFileParameters.scanSelection)
        .setValue(true, new ScanSelection(1));
    parameters.setParameter(ImsToRawDataFileParameters.mobilityScansToMerge, 1);
    parameters.setParameter(ImsToRawDataFileParameters.maxTicRsdDeviation, 3d);
    final MZmineProjectImpl project = new MZmineProjectImpl();
    project.addFile(source);

    final ImsToRawDataFileTask task = new ImsToRawDataFileTask(project, source, parameters, null,
        Instant.now());
    task.run();

    assertEquals(TaskStatus.FINISHED, task.getStatus());
    final RawDataFile converted = project.getDataFiles()[1];
    assertEquals(4, converted.getNumOfScans());
    assertEquals(2.5f, converted.getScan(1).getRetentionTime(), 1e-6f);
    assertEquals(4, converted.getScans().stream().filter(scan -> scan.getMSLevel() == 1).count());
  }

  @Test
  void removesMergedScansWithLowTicOutliers() {
    final IMSRawDataFile source = new IMSRawDataFileImpl("ims", null, null, Color.BLACK);
    source.addScan(createFrame(source, 1, 1f, 100d, new double[]{100d, 100d, 100d, 1d}));

    final ParameterSet parameters = new ImsToRawDataFileParameters();
    parameters.getParameter(ImsToRawDataFileParameters.scanSelection)
        .setValue(false, ScanSelection.ALL_SCANS);
    parameters.setParameter(ImsToRawDataFileParameters.mobilityScansToMerge, 1);
    parameters.setParameter(ImsToRawDataFileParameters.maxTicRsdDeviation, 1d);
    final MZmineProjectImpl project = new MZmineProjectImpl();
    project.addFile(source);

    final ImsToRawDataFileTask task = new ImsToRawDataFileTask(project, source, parameters, null,
        Instant.now());
    task.run();

    assertEquals(TaskStatus.FINISHED, task.getStatus());
    final RawDataFile converted = project.getDataFiles()[1];
    assertEquals(3, converted.getNumOfScans());
    assertArrayEquals(new double[]{100d}, converted.getScan(0).getMzValues(new double[0]), 1e-10);
    assertArrayEquals(new double[]{102d}, converted.getScan(2).getMzValues(new double[0]), 1e-10);
    assertEquals(3, converted.getScan(2).getScanNumber());
  }

  private static SimpleFrame createFrame(IMSRawDataFile file, int frameId, float rt,
      double firstMz, int numMobilityScans) {
    return createFrame(file, frameId, rt, firstMz, numMobilityScans, 1);
  }

  private static SimpleFrame createFrame(IMSRawDataFile file, int frameId, float rt,
      double firstMz, int numMobilityScans, int msLevel) {
    final double[] intensities = new double[numMobilityScans];
    for (int i = 0; i < numMobilityScans; i++) {
      intensities[i] = (frameId - 1) * 3d + i + 1d;
    }
    return createFrame(file, frameId, rt, firstMz, intensities, msLevel);
  }

  private static SimpleFrame createFrame(IMSRawDataFile file, int frameId, float rt,
      double firstMz, double[] intensities) {
    return createFrame(file, frameId, rt, firstMz, intensities, 1);
  }

  private static SimpleFrame createFrame(IMSRawDataFile file, int frameId, float rt,
      double firstMz, double[] intensities, int msLevel) {
    final SimpleFrame frame = new SimpleFrame(file, frameId, msLevel, rt, new double[0],
        new double[0],
        MassSpectrumType.CENTROIDED, PolarityType.POSITIVE, "frame " + frameId,
        Range.closed(50d, 500d), MobilityType.TIMS, null, null);
    final List<BuildingMobilityScan> mobilityScans = new ArrayList<>();
    final double[] mobilities = new double[intensities.length];
    for (int i = 0; i < intensities.length; i++) {
      mobilityScans.add(new BuildingMobilityScan(i, new double[]{firstMz + i},
          new double[]{intensities[i]}, MassSpectrumType.CENTROIDED));
      mobilities[i] = 1.2d - i * 0.1d;
    }
    frame.setMobilities(mobilities);
    frame.setMobilityScans(mobilityScans, false);
    return frame;
  }
}
