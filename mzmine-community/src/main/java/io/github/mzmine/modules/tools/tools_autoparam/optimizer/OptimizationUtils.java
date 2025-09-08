/*
 * Copyright (c) 2004-2025 The mzmine Development Team
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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.tools.tools_autoparam.optimizer;

import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.MobilityType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataprocessing.align_common.BaseFeatureListAligner;
import io.github.mzmine.modules.dataprocessing.align_common.FeatureCloner.SimpleFeatureCloner;
import io.github.mzmine.modules.dataprocessing.align_join.JoinAlignerParameters;
import io.github.mzmine.modules.dataprocessing.align_join.JoinRowAlignScorer;
import io.github.mzmine.modules.io.import_rawdata_all.AllSpectralDataImportModule;
import io.github.mzmine.modules.io.import_rawdata_all.AllSpectralDataImportParameters;
import io.github.mzmine.modules.tools.tools_autoparam.DataFileStatistics;
import io.github.mzmine.modules.tools.tools_autoparam.FeatureStatistics;
import io.github.mzmine.modules.tools.tools_autoparam.FeatureWithIsotopeTraces;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance.Unit;
import io.github.mzmine.parameters.parametertypes.tolerances.mobilitytolerance.MobilityTolerance;
import io.github.mzmine.project.ProjectService;
import io.github.mzmine.taskcontrol.AllTasksFinishedListener;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.taskcontrol.TaskService;
import io.github.mzmine.util.FeatureListRowSorter;
import io.github.mzmine.util.MathUtils;
import io.github.mzmine.util.MemoryMapStorage;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OptimizationUtils {

  public static List<RawDataFile> importFilesBlocking(File[] filesToImport, @Nullable File metadata) {
    final ParameterSet importParam = AllSpectralDataImportParameters.create(true, filesToImport,
        metadata, null);
    final List<Task> tasks = new ArrayList<>();
    final MZmineProject project = ProjectService.getProject();
    MZmineCore.getModuleInstance(AllSpectralDataImportModule.class)
        .runModule(project, importParam, tasks, Instant.now());
    TaskService.getController().addTasks(tasks.toArray(new Task[0]));
    final AtomicBoolean done = new AtomicBoolean(false);
    AllTasksFinishedListener.registerCallbacks(tasks, false, () -> done.set(true),
        () -> done.set(true), () -> done.set(true));

    while (!done.get()) {
      try {
        TimeUnit.MILLISECONDS.sleep(100);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    final Set<String> files = new HashSet<>(
        List.of(filesToImport).stream().map(File::getName).toList());
    return project.getCurrentRawDataFiles().stream()
        .filter(raw -> files.contains(raw.getFileName())).toList();
  }

  public static ModularFeatureList alignBenchmarkFeatures(List<DataFileStatistics> stats,
      @Nullable MemoryMapStorage storage, @NotNull Task parentTask) {

    final List<FeatureList> flists = new ArrayList<>();
    for (DataFileStatistics stat : stats) {
      final List<ModularFeature> features = stat.featureStatistics().stream()
          .map(FeatureStatistics::getBestEnvelope).flatMap(FeatureWithIsotopeTraces::streamFeatures)
          .toList();
      final ModularFeatureList flist = new ModularFeatureList(stat.file().getName(), storage,
          features.size(), features.size(), stat.file());
      final List<ModularFeature> clonedFeatures = features.stream()
          .map(f -> new ModularFeature(flist, f)).toList();
      for (int i = 0; i < clonedFeatures.size(); i++) {
        flist.addRow(new ModularFeatureListRow(flist, i, clonedFeatures.get(i)));
      }

      flists.add(flist);
    }

    final Optional<MobilityType> imsType = stats.stream().map(DataFileStatistics::file)
        .filter(IMSRawDataFile.class::isInstance).map(IMSRawDataFile.class::cast)
        .map(IMSRawDataFile::getMobilityType).findFirst();

    final ParameterSet alignmentParam = JoinAlignerParameters.create(new MZTolerance(0.02, 10), 2,
        new RTTolerance(0.2f, Unit.MINUTES), 1,
        imsType.map(type -> new MobilityTolerance(type == MobilityType.TIMS ? 0.01f : 2f))
            .orElse(null), imsType.isPresent() ? 1d : null);

    final BaseFeatureListAligner aligner = new BaseFeatureListAligner(parentTask, flists, "aligned",
        storage, new JoinRowAlignScorer(alignmentParam), new SimpleFeatureCloner(),
        FeatureListRowSorter.MZ_ASCENDING, null);
    return aligner.alignFeatureLists();
  }

  public static MZTolerance extractSampleToSampleMzTolerances(ModularFeatureList flist,
      int minDetections, float coverageQuantile) {
    if (minDetections > flist.getNumberOfRawDataFiles()) {
      throw new IllegalStateException(
          "Minimum detections (%d) larger than number of raw data files (%d)".formatted(
              minDetections, flist.getNumberOfRawDataFiles()));
    }

    final Map<MZTolerance, AtomicInteger> toleranceCounter = new HashMap<>();

    for (final FeatureListRow row : flist.getRows()) {
      if (row.getNumberOfFeatures() < minDetections) {
        continue;
      }

      final DoubleSummaryStatistics mzStats = row.streamFeatures().mapToDouble(Feature::getMZ)
          .summaryStatistics();
      final Double mz = row.getAverageMZ();
      final double maxDeviation = Math.max(Math.abs(mzStats.getMax() - mz),
          Math.abs(mz - mzStats.getMin()));

      for (final MZTolerance tol : WizardParameterSolutionBuilder.ALL_TOLERANCE_OPTIONS) {
        if (tol.getMzToleranceForMass(mz) > maxDeviation) {
          final AtomicInteger counter = toleranceCounter.computeIfAbsent(tol,
              _ -> new AtomicInteger(0));
          counter.incrementAndGet();
          break;
        }
      }
    }

    final int requiredSampleSize = (int) (
        toleranceCounter.values().stream().mapToInt(AtomicInteger::get).sum() * coverageQuantile);
    final List<Entry<MZTolerance, AtomicInteger>> sortedToleranceCounts = toleranceCounter.entrySet()
        .stream().sorted(Comparator.comparingDouble(e -> e.getKey().getMzTolerance())).toList();

    int coveredRows = 0;
    for (Entry<MZTolerance, AtomicInteger> toleranceCount : sortedToleranceCounts) {
      coveredRows += toleranceCount.getValue().get();
      if (coveredRows >= requiredSampleSize) {
        return toleranceCount.getKey();
      }
    }

    throw new IllegalStateException(
        "Unable to find sample tolerance threshold. " + sortedToleranceCounts.stream()
            .map(e -> e.getKey().toString() + ": " + e.getValue())
            .collect(Collectors.joining(", ")));
  }

  public static RTTolerance extractSampleToSampleRtTolerances(@NotNull ModularFeatureList flist,
      int minDetections, float coverageQuantile) {
    if (minDetections > flist.getNumberOfRawDataFiles()) {
      throw new IllegalStateException(
          "Minimum detections (%d) larger than number of raw data files (%d)".formatted(
              minDetections, flist.getNumberOfRawDataFiles()));
    }

    DoubleArrayList differences = new DoubleArrayList();
    for (final FeatureListRow row : flist.getRows()) {
      if (row.getNumberOfFeatures() < minDetections) {
        continue;
      }

      final Float rt = row.getAverageRT();
      differences.addAll(row.streamFeatures().map(Feature::getRT)
          .map(featureRt -> (double) Math.abs(featureRt - rt)).toList());
    }

    final double[] values = differences.doubleStream().sorted().toArray();
    return new RTTolerance((float) MathUtils.calcQuantileSorted(values, coverageQuantile),
        Unit.MINUTES);
  }
}
