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

package io.github.mzmine.modules.dataprocessing.id_pfas_annotation;

import com.google.common.collect.Range;
import com.google.common.util.concurrent.AtomicDouble;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasCompound;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasLibraryBuilder;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasLibraryParser;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import org.junit.runner.notification.RunNotifier;

public class PfasAnnotationTask extends AbstractTask {

  private final File libraryFile;
  private final Range<Integer> nRange;
  private final Range<Integer> mRange;
  private final Range<Integer> kRange;
  private final ModularFeatureList flist;

  private List<PfasCompound> compounds;


  protected PfasAnnotationTask(@Nullable MemoryMapStorage storage) {
    super(storage);
  }

  @Override
  public String getTaskDescription() {
    return null;
  }

  @Override
  public double getFinishedPercentage() {
    return 0;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);

    compounds = buildPfasLibrary();

    List<ModularFeatureListRow> rows = flist.modularStream()
        .filter(row -> !row.getAllMS2Fragmentations().isEmpty()).toList();

    AtomicInteger processed = new AtomicInteger(0);

    rows.parallelStream().forEach(row -> {
      Scan bestMsMs = null;
      double bestScore = 0d;
      for (Scan msms : row.getAllMS2Fragmentations()) {

      }
    });
  }

  private List<PfasCompound> buildPfasLibrary() {
    final PfasLibraryParser parser = new PfasLibraryParser();
    if (!parser.read(libraryFile)) {
      setErrorMessage("Cannot read library file.");
      setStatus(TaskStatus.ERROR);
      return null;
    }

    final PfasLibraryBuilder builder = new PfasLibraryBuilder(parser.getEntries(), nRange, mRange,
        kRange);

    builder.buildLibrary();

    return builder.getLibrary();
  }

}
