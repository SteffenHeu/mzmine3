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

package io.github.mzmine.modules.io.import_library_sqlite.cfm_sqlite;

import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.spectraldb.entry.SpectralDBEntry;
import io.github.mzmine.util.spectraldb.entry.SpectralLibrary;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CFMLibraryImportTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(CFMLibraryImportTask.class.getName());

  private final File databaseFile;
  private final JoinedDataTable table = new JoinedDataTable();
  private final MZmineProject project;
  private final ParameterSet parameters;
  private String description = "Importing sqlite CFM-ID library.";
  private double progress = 0;

  protected CFMLibraryImportTask(MZmineProject project, ParameterSet parameters,
      @Nullable MemoryMapStorage storage, @NotNull Instant moduleCallDate) {
    super(storage, moduleCallDate);
    this.project = project;
    this.parameters = parameters;
    databaseFile = parameters.getValue(CFMLibraryImportParameters.fileName);
  }

  @Override
  public String getTaskDescription() {
    return description;
  }

  @Override
  public double getFinishedPercentage() {
    return progress;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    readLibrary();
    if (isCanceled()) {
      return;
    }

    final List<SpectralDBEntry> entries = new ArrayList<>();

    for (int i = 0; i < table.size(); i++) {
      entries.addAll(table.getEntriesForCompound(i));
      setDescription(
          "Importing database " + databaseFile.getName() + " compound " + i + "/" + table.size());
      progress = i / (double) table.size();
      if (isCanceled()) {
        return;
      }
    }

    SpectralLibrary library = new SpectralLibrary(databaseFile, entries);
    project.addSpectralLibrary(library);

    setStatus(TaskStatus.FINISHED);
  }

  private void readLibrary() {
    setDescription("Initializing SQL...");

    logger.finest(() -> "Initialising SQL...");
    try {
      Class.forName("org.sqlite.JDBC");
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      logger.info("Could not load sqlite.JDBC.");
      setStatus(TaskStatus.ERROR);
      return;
    }
    logger.finest(() -> "SQl initialised.");

    setDescription("Establishing SQL connection to " + databaseFile.getName());
    logger.finest(() -> "Establishing SQL connection to " + databaseFile.getName());

    synchronized (org.sqlite.JDBC.class) {
      Connection connection;
      try {
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        logger.finest(() -> "Connection established. " + connection.toString());

        logger.finest("Executing query.");
        setDescription("Executing query.");
        table.executeQuery(connection);
        logger.finest("Query executed.");
        setDescription("Query executed.");

        table.print();

        connection.close();
      } catch (Throwable t) {
        t.printStackTrace();
        logger.info("If stack trace contains \"out of memory\" the file was not found.");
        setStatus(TaskStatus.ERROR);
        setErrorMessage(t.toString());
      }
    }
  }

  private void setDescription(String s) {
    description = s;
  }
}
