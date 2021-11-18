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

package io.github.mzmine.modules.dataprocessing.featdet_imagebuilder;

import io.github.mzmine.datamodel.ImagingRawDataFile;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.modules.MZmineModuleCategory;
import io.github.mzmine.modules.MZmineProcessingModule;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.util.ExitCode;
import io.github.mzmine.util.MemoryMapStorage;
import java.time.Instant;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;

/*
 * @author Ansgar Korf (ansgar.korf@uni-muenster.de)
 */
public class ImageBuilderModule implements MZmineProcessingModule {

  private static final String MODULE_NAME = "Image builder";
  private static final String MODULE_DESCRIPTION =
      "This module connects data points from mass lists and builds images.";

  @Override
  public @NotNull String getName() {
    return MODULE_NAME;
  }

  @Override
  public @NotNull String getDescription() {
    return MODULE_DESCRIPTION;
  }

  @Override
  @NotNull
  public ExitCode runModule(@NotNull MZmineProject project, @NotNull ParameterSet parameters,
      @NotNull Collection<Task> tasks, @NotNull Instant moduleCallDate) {

    RawDataFile[] files = parameters.getParameter(ImageBuilderParameters.rawDataFiles).getValue()
        .getMatchingRawDataFiles();

    MemoryMapStorage storage = MemoryMapStorage.forFeatureList();

    for (RawDataFile file : files) {
      if (!(file instanceof ImagingRawDataFile)) {
        continue;
      }
      ImageBuilderTask task = new ImageBuilderTask(project, file, parameters, storage, moduleCallDate);
      tasks.add(task);
    }

    return ExitCode.OK;
  }

  @Override
  public @NotNull MZmineModuleCategory getModuleCategory() {
    return MZmineModuleCategory.EIC_DETECTION;
  }

  @Override
  public @NotNull Class<? extends ParameterSet> getParameterSetClass() {
    return ImageBuilderParameters.class;
  }

}
