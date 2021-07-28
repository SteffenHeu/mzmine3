/*
 *  Copyright 2006-2020 The MZmine Development Team
 *
 *  This file is part of MZmine.
 *
 *  MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 *  General Public License as published by the Free Software Foundation; either version 2 of the
 *  License, or (at your option) any later version.
 *
 *  MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 *  the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 *  Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with MZmine; if not,
 *  write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 *  USA
 */

package io.github.mzmine.modules.io.import_rawdata_bruker_tsf;

import io.github.mzmine.datamodel.ImagingRawDataFile;
import io.github.mzmine.datamodel.ImagingScan;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.MassSpectrumType;
import io.github.mzmine.modules.io.import_rawdata_bruker_tdf.datamodel.BrukerScanMode;
import io.github.mzmine.modules.io.import_rawdata_bruker_tdf.datamodel.sql.TDFMaldiFrameInfoTable;
import io.github.mzmine.modules.io.import_rawdata_bruker_tdf.datamodel.sql.TDFMaldiFrameLaserInfoTable;
import io.github.mzmine.modules.io.import_rawdata_bruker_tdf.datamodel.sql.TDFMetaDataTable;
import io.github.mzmine.modules.io.import_rawdata_imzml.ImagingParameters;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.logging.Logger;

public class TSFImportTask extends AbstractTask {

  private static Logger logger = Logger.getLogger(TSFImportTask.class.getName());

  private final TDFMetaDataTable metaDataTable;
  private final TDFMaldiFrameInfoTable maldiFrameInfoTable;
  private final TDFMaldiFrameLaserInfoTable maldiFrameLaserInfoTable;
  private final TSFFrameTable frameTable;
  private final MZmineProject project;
  private final File dirPath;
  private final ImagingRawDataFile newMZmineFile;
  private final String rawDataFileName;
  private boolean isMaldi = false;
  private String description;
  private File tsf;
  private File tsf_bin;
  private int totalScans = 1;
  private int processedScans = 0;

  public TSFImportTask(MZmineProject project, File fileName, ImagingRawDataFile newMZmineFile) {
    super(null);

    this.project = project;
    this.dirPath = fileName;
    this.newMZmineFile = newMZmineFile;
    rawDataFileName = newMZmineFile.getName();

    metaDataTable = new TDFMetaDataTable();
    maldiFrameInfoTable = new TDFMaldiFrameInfoTable();
    frameTable = new TSFFrameTable();
    maldiFrameLaserInfoTable = new TDFMaldiFrameLaserInfoTable();

    setDescription("Importing " + rawDataFileName + ": Waiting.");
  }

  @Override
  public String getTaskDescription() {
    return description;
  }

  @Override
  public double getFinishedPercentage() {
    return processedScans / (double) totalScans;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);

    setDescription("Importing " + rawDataFileName + ": opening files.");
    File[] files = getDataFilesFromDir(dirPath);
    if (files == null || files.length != 2 || files[0] == null || files[1] == null) {
      setErrorMessage("Could not find tsf files.");
      setStatus(TaskStatus.ERROR);
      return;
    }

    tsf = files[0];
    tsf_bin = files[1];

    setDescription("Importing " + rawDataFileName + ": Initialising tsf reader.");
    final TSFUtils tsfUtils;
    try {
      tsfUtils = new TSFUtils();
    } catch (IOException e) {
      e.printStackTrace();
      setErrorMessage(e.getMessage());
      setStatus(TaskStatus.ERROR);
      return;
    }

    setDescription("Importing " + rawDataFileName + ": Opening " + tsf.getAbsolutePath());
    final long handle = tsfUtils.openFile(tsf_bin);
    if (handle == 0L) {
      setErrorMessage("Could not open " + tsf_bin.getAbsolutePath());
      setStatus(TaskStatus.ERROR);
      return;
    }

    setDescription("Importing " + rawDataFileName + ": Reading metadata");
    readMetadata();

    final int numScans = frameTable.getFrameIdColumn().size();
    totalScans = numScans;
    final MassSpectrumType importSpectrumType =
        metaDataTable.hasLineSpectra() ? MassSpectrumType.CENTROIDED
            : MassSpectrumType.PROFILE;

    for (int i = 0; i < numScans; i++) {
      final long frameId = frameTable.getFrameIdColumn().get(i);

      setDescription("Importing " + rawDataFileName + ": Scan " + frameId + "/" + numScans);

      final ImagingScan scan = tsfUtils
          .loadMaldiScan(newMZmineFile, handle, frameId, metaDataTable, frameTable,
              maldiFrameInfoTable, importSpectrumType);

      try {
        newMZmineFile.addScan(scan);
      } catch (IOException e) {
        e.printStackTrace();
        setErrorMessage("Could not add scan " + frameId + " to raw data file.");
        setStatus(TaskStatus.ERROR);
        return;
      }

      if (isCanceled()) {
        return;
      }
      processedScans++;
    }

    newMZmineFile.setImagingParam(
        new ImagingParameters(metaDataTable, maldiFrameInfoTable, maldiFrameLaserInfoTable));

    project.addFile(newMZmineFile);
    setStatus(TaskStatus.FINISHED);
  }

  private void readMetadata() {
    setDescription("Initializing SQL...");

    // initialize jdbc driver:
    // https://stackoverflow.com/questions/6740601/what-does-class-fornameorg-sqlite-jdbc-do/6740632
    try {
      Class.forName("org.sqlite.JDBC");
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      logger.info("Could not load sqlite.JDBC.");
      setStatus(TaskStatus.ERROR);
      return;
    }

    setDescription("Establishing SQL connection to " + tsf.getName());
    Connection connection;
    try {
      connection = DriverManager.getConnection("jdbc:sqlite:" + tsf.getAbsolutePath());

      setDescription("Reading metadata for " + tsf.getName());
      metaDataTable.executeQuery(connection);
      // metaDataTable.print();

      setDescription("Reading frame data for " + tsf.getName());
      frameTable.executeQuery(connection);
      // frameTable.print();

      isMaldi = frameTable.getScanModeColumn()
          .contains(Integer.toUnsignedLong(BrukerScanMode.MALDI.getNum()));

      if (!isMaldi) {
        throw new IllegalStateException("The tsf file to import is not a MALDI file.");
      } else {
        setDescription("MALDI info for " + tsf.getName());
        maldiFrameInfoTable.executeQuery(connection);
        maldiFrameInfoTable.process();
        maldiFrameLaserInfoTable.executeQuery(connection);
      }

      connection.close();
    } catch (Throwable t) {
      t.printStackTrace();
      logger.info("If stack trace contains \"out of memory\" the file was not found.");
      setStatus(TaskStatus.ERROR);
      setErrorMessage(t.toString());
    }

    logger.info("Metadata read successfully for " + rawDataFileName);
  }

  public void setDescription(String description) {
    this.description = description;
  }

  private File[] getDataFilesFromDir(File dir) {

    if (!dir.exists() || !dir.isDirectory()) {
      setStatus(TaskStatus.ERROR);
      throw new IllegalArgumentException("Invalid directory.");
    }

    if (!dir.getAbsolutePath().endsWith(".d")) {
      setStatus(TaskStatus.ERROR);
      throw new IllegalArgumentException("Invalid directory ending.");
    }

    File[] files = dir.listFiles(pathname -> {
      if (pathname.getAbsolutePath().endsWith(".tsf")
          || pathname.getAbsolutePath().endsWith(".tsf_bin")) {
        return true;
      }
      return false;
    });

    if (files.length != 2) {
      return null;
    }

    File tsf = Arrays.stream(files).filter(c -> {
      if (c.getAbsolutePath().endsWith(".tsf")) {
        return true;
      }
      return false;
    }).findAny().orElse(null);
    File tsf_bin = Arrays.stream(files).filter(c -> {
      if (c.getAbsolutePath().endsWith(".tsf_bin")) {
        return true;
      }
      return false;
    }).findAny().orElse(null);

    return new File[]{tsf, tsf_bin};
  }
}
