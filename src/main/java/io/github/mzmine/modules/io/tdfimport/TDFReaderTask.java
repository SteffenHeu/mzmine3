/*
 * Copyright 2006-2020 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */

package io.github.mzmine.modules.io.tdfimport;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.RawDataFileWriter;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.impl.SimpleFrame;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.io.tdfimport.datamodel.sql.FramePrecursorTable;
import io.github.mzmine.modules.io.tdfimport.datamodel.sql.TDFFrameMsMsInfoTable;
import io.github.mzmine.modules.io.tdfimport.datamodel.sql.TDFFrameTable;
import io.github.mzmine.modules.io.tdfimport.datamodel.sql.TDFMetaDataTable;
import io.github.mzmine.modules.io.tdfimport.datamodel.sql.TDFPasefFrameMsMsInfoTable;
import io.github.mzmine.modules.io.tdfimport.datamodel.sql.TDFPrecursorTable;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

public class TDFReaderTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(TDFReaderTask.class.getName());

  private String description;

  private final File tdf;
  private final File tdfBin;

  private final TDFMetaDataTable metaDataTable;
  private final TDFFrameTable frameTable;
  private final TDFPrecursorTable precursorTable;
  private final TDFPasefFrameMsMsInfoTable pasefFrameMsMsInfoTable;
  private final TDFFrameMsMsInfoTable frameMsMsInfoTable;
  private final FramePrecursorTable framePrecursorTable;

  private double finishedPercentage;

  /**
   * Bruker tims format:
   * - Folder
   *  - contains multiple files
   *  - one folder per analysis
   *  - .d extension
   *  - *.tdf - SQLite database; contains metadata
   *  - *.tdf_bin - contains peak data
   *
   *  - *.tdf_bin
   *   - list of frames
   *   - frame:
   *    - set of spectra at one specific time
   *    - single spectrum for "each" mobility
   *    - spectrum:
   *     - intensity vs m/z
   */

  /**
   * @param tdf
   * @param tdfBin
   */
  public TDFReaderTask(File tdf, File tdfBin) {
    this.tdf = tdf;
    this.tdfBin = tdfBin;
    metaDataTable = new TDFMetaDataTable();
    frameTable = new TDFFrameTable();
    precursorTable = new TDFPrecursorTable();
    pasefFrameMsMsInfoTable = new TDFPasefFrameMsMsInfoTable();
    frameMsMsInfoTable = new TDFFrameMsMsInfoTable();
    framePrecursorTable = new FramePrecursorTable();

    if (tdf != null && tdf.exists()) {
      setDescription("Import Bruker Daltonics " + tdf.getName());
    }
    setStatus(TaskStatus.WAITING);
  }

  @Override
  public String getTaskDescription() {
    return description;
  }

  @Override
  public double getFinishedPercentage() {
    return finishedPercentage;
  }

  @Override
  public void run() {
    if (tdf == null || tdfBin == null || !tdf.exists() || !tdf.canRead()
        || !tdfBin.exists() || !tdfBin.canRead()) {
      logger.info("Cannot open sql or bin files: " + tdf.getName() + "; " + tdfBin.getName());
      return;
    }

    setStatus(TaskStatus.PROCESSING);
    readMetadata();

    RawDataFileWriter newMZmineFile;
    try {
      newMZmineFile = MZmineCore.createNewIMSFile(tdfBin.getName());
    } catch (IOException e) {
      e.printStackTrace();
      setStatus(TaskStatus.ERROR);
      return;
    }

    long handle = TDFUtils.openFile(tdfBin);
    int scanNum = 0;
    int numFrames = frameTable.getNumberOfFrames();

    // load frame data
    for (int i = 0; i < numFrames; i++) {
      setDescription(tdfBin.getName() + " - Reading frame " + (i + 1) + "/" + numFrames);
      long frameId = (long) frameTable.getColumn(TDFFrameTable.FRAME_ID).get(i);

      SimpleFrame frame = TDFUtils
          .exctractCentroidScanForFrame(handle, frameId, scanNum, metaDataTable, frameTable);
      scanNum++;

      List<Scan> scanList = TDFUtils
          .loadScansForPASEFFrame(handle, frameId, scanNum, frameTable, metaDataTable,
              framePrecursorTable);
      assert scanList != null;

      frame.addMobilityScans(scanList);

      try {
        newMZmineFile.addScan(frame);
        scanNum += frame.getNumberOfMobilityScans();
      } catch (IOException e) {
        e.printStackTrace();
      }

//      try {
//        for (Scan scan : scanList) {
//          newMZmineFile.addScan(scan);
//          scanNum++;
//        }
//      } catch (IOException e) {
//        e.printStackTrace();
//        return;
//      }
      finishedPercentage = (double) i / (double) numFrames;
    }

    RawDataFile file;
    try {
      file = newMZmineFile.finishWriting();
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }

    MZmineCore.getProjectManager().getCurrentProject().addFile(file);

    /*long handle = tdfLibrary.tims_open(tdfBin.getAbsolutePath(), 0);
    if (handle == 0) {
      logger.info("Could not open file " + tdfBin.getAbsolutePath());
    }

    byte[] buffer = new byte[200000];*/
    TDFUtils.close(handle);
    setStatus(TaskStatus.FINISHED);
  }


  private void readMetadata() {
    setDescription("Initializing SQL...");

    try {
      Class.forName("org.sqlite.JDBC");
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      logger.info("Could not load sqlite.JDBC.");
      setStatus(TaskStatus.ERROR);
      return;
    }

    setDescription("Establishing SQL connection to " + tdf.getName());
    Connection connection;
    try {
      connection = DriverManager.getConnection("jdbc:sqlite:" + tdf.getAbsolutePath());

      setDescription("Reading metadata for " + tdf.getName());
      metaDataTable.executeQuery(connection);
      metaDataTable.print();

      setDescription("Reading frame data for " + tdf.getName());
      frameTable.executeQuery(connection);
      frameTable.print();

      setDescription("Reading precursor info for " + tdf.getName());
      precursorTable.executeQuery(connection);
      precursorTable.print();

      setDescription("Reading PASEF info for " + tdf.getName());
      pasefFrameMsMsInfoTable.executeQuery(connection);
      pasefFrameMsMsInfoTable.print();

      setDescription("Reading Frame MS/MS info for " + tdf.getName());
      frameMsMsInfoTable.executeQuery(connection);
      frameMsMsInfoTable.print();

      setDescription("Reading MS/MS-Precursor info for " + tdf.getName());
      framePrecursorTable.executeQuery(connection);
      framePrecursorTable.print();

      connection.close();
    } catch (SQLException throwable) {
      throwable.printStackTrace();
      logger.info("If stack trace contains \"out of memory\" the file was not found.");
      setStatus(TaskStatus.ERROR);
      return;
    }
  }

  private void setDescription(String desc) {
    description = desc;
  }


  /*private RawDataFile readPASEFFile(TDFLibrary tdfLib, File tdfBin, TDFFrameTable frames,
      TDFPrecursorTable precursors) {

    long binHandle = tdfLib.tims_open(tdfBin.getParentFile().getAbsolutePath(), 0);

    int numFrames = frames.getColumn(TDFFrameTable.FRAME_ID_COLUMN_NAME).getEntries().size();

    for (int i = 0; i < numFrames; i++) {

    }

  };*/
}