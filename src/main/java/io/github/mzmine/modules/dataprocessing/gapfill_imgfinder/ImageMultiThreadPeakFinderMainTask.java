/*
 * Copyright 2006-2021 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package io.github.mzmine.modules.dataprocessing.gapfill_imgfinder;

import com.google.common.util.concurrent.AtomicDouble;
import io.github.mzmine.datamodel.IMSImagingRawDataFile;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.gui.preferences.MZminePreferences;
import io.github.mzmine.gui.preferences.NumOfThreadsParameter;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataprocessing.gapfill_peakfinder.multithreaded.MultiThreadPeakFinderModule;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.AllTasksFinishedListener;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The main task creates sub tasks to perform the PeakFinder algorithm on multiple threads. Each sub
 * task performs gap filling on a number of RawDataFiles.
 *
 * @author Robin Schmid (robinschmid@wwu.de)
 */
class ImageMultiThreadPeakFinderMainTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(
      ImageMultiThreadPeakFinderMainTask.class.getName());
  private final MZmineProject project;
  private final ParameterSet parameters;
  private final ModularFeatureList peakList;
  private final String suffix;
  private final AtomicDouble progress = new AtomicDouble(0);
  private ModularFeatureList processedPeakList;

  private final int rowsPerIteration = 50;

  private final IMSImagingRawDataFile imagingFile;
  private AtomicInteger processedRows = new AtomicInteger(0);

  /**
   * @param batchTasks all sub tasks are registered to the batchtasks list
   */
  public ImageMultiThreadPeakFinderMainTask(MZmineProject project, FeatureList peakList,
      ParameterSet parameters, Collection<Task> batchTasks, @Nullable MemoryMapStorage storage,
      @NotNull Instant moduleCallDate) {
    super(storage, moduleCallDate);
    this.project = project;
    this.peakList = (ModularFeatureList) peakList;
    this.parameters = parameters;

    suffix = parameters.getParameter(ImageMultiThreadPeakFinderParameters.suffix).getValue();
    final RawDataFile[] files = parameters.getParameter(
        ImageMultiThreadPeakFinderParameters.rawFile).getValue().getMatchingRawDataFiles();
    imagingFile = (IMSImagingRawDataFile) files[0];
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    logger.info(
        () -> String.format("Running multithreaded gap filler on %s.", peakList.toString()));

    List<RawDataFile> allFiles = new ArrayList<>(peakList.getRawDataFiles());
    allFiles.add(imagingFile);
    // Create new results feature list
    processedPeakList = peakList.createCopy(peakList + " " + suffix, getMemoryMapStorage(),
        allFiles, false);

    progress.getAndSet(0.1);

    // Submit the tasks to the task controller for processing
    final List<AbstractTask> tasks = createSubTasks(rowsPerIteration,
        new ArrayList<>(processedPeakList.getRows()));

    final AbstractTask thistask = this;
    new AllTasksFinishedListener(tasks, true,
        // succeed
        l -> {
          logger.info(
              "All sub tasks of multithreaded gap-filling have finished. Finalising results.");

          // Add task description to peakList
          processedPeakList.addDescriptionOfAppliedTask(
              new SimpleFeatureListAppliedMethod("Gap filling ", MultiThreadPeakFinderModule.class,
                  parameters, getModuleCallDate()));

          // update all rows by row bindings (average values)
          // this needs to be done after all tasks finish because values were not updated when
          // adding features
          processedPeakList.applyRowBindings();

          project.addFeatureList(processedPeakList);

          logger.info("Completed: Multithreaded gap-filling successfull");

          if (thistask.getStatus() == TaskStatus.PROCESSING) {
            thistask.setStatus(TaskStatus.FINISHED);
          }
        }, lerror -> {
      setErrorMessage("Error in gap filling");
      thistask.setStatus(TaskStatus.ERROR);
      for (AbstractTask task : tasks) {
        task.setStatus(TaskStatus.ERROR);
      }
    },
        // cancel if one was cancelled
        listCancelled -> cancel()) {
      @Override
      public void taskStatusChanged(Task task, TaskStatus newStatus, TaskStatus oldStatus) {
        super.taskStatusChanged(task, newStatus, oldStatus);
        // show progress
        if (oldStatus != newStatus && newStatus == TaskStatus.FINISHED) {
          progress.getAndAdd(0.9 / tasks.size());
        }
      }
    };

    // start
    MZmineCore.getTaskController().addTasks(tasks.toArray(AbstractTask[]::new));

    // wait till finish
    while (!(isCanceled() || isFinished())) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        logger.log(Level.SEVERE, "Error in GNPS export/submit task", e);
      }
    }
  }

  private int getMaxThreads() {
    int maxRunningThreads = 1;
    NumOfThreadsParameter parameter = MZmineCore.getConfiguration().getPreferences()
        .getParameter(MZminePreferences.numOfThreads);
    if (parameter.isAutomatic() || (parameter.getValue() == null)) {
      maxRunningThreads = Runtime.getRuntime().availableProcessors();
    } else {
      maxRunningThreads = parameter.getValue();
    }

    // raw files
    int raw = peakList.getNumberOfRawDataFiles();
    // raw files<?
    if (raw < maxRunningThreads) {
      maxRunningThreads = raw;
    }
    return maxRunningThreads;
  }

  /**
   * Distributes the RawDataFiles on different tasks
   */
  private List<AbstractTask> createSubTasks(int rowsPerThread, List<FeatureListRow> rows) {
    final List<AbstractTask> tasks = new ArrayList<>();

    int i = 0;
    while (!rows.isEmpty()) {
      i++;
      final List<FeatureListRow> rowsForThread = new ArrayList<>(
          rows.subList(0, Math.min(rowsPerThread, rows.size())));
      rows.removeAll(rowsForThread);

      // create task
      tasks.add(new ImageMultiThreadPeakFinderTask(processedPeakList, parameters, rowsForThread,
          imagingFile, i, processedRows, getModuleCallDate()));
    }

    return tasks;
  }

  @Override
  public double getFinishedPercentage() {
    return progress.get();
  }

  @Override
  public String getTaskDescription() {
    return "Main task: Gap filling " + peakList;
  }

}
