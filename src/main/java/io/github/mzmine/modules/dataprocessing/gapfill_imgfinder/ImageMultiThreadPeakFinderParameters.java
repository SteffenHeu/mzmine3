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

import io.github.mzmine.datamodel.IMSImagingRawDataFile;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.impl.IonMobilitySupport;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.IntegerParameter;
import io.github.mzmine.parameters.parametertypes.StringParameter;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsParameter;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.MZToleranceParameter;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;

public class ImageMultiThreadPeakFinderParameters extends SimpleParameterSet {

  public static final FeatureListsParameter peakLists = new FeatureListsParameter();

  public static final RawDataFilesParameter rawFile = new RawDataFilesParameter(1, 1);

  public static final StringParameter suffix = new StringParameter("Name suffix",
      "Suffix to be added to feature list name", "gap-filled");

  public static final MZToleranceParameter MZTolerance = new MZToleranceParameter();

  public static final IntegerParameter minDataPoints = new IntegerParameter("Minimum data points",
      "Only fill gaps with features with minimum number of data points. \n Usually lower number of data points are used.",
      1, 1, Integer.MAX_VALUE);

  public ImageMultiThreadPeakFinderParameters() {
    super(new Parameter[]{peakLists, rawFile, suffix, MZTolerance, minDataPoints},
        "https://mzmine.github.io/mzmine_documentation/module_docs/gapfill_peak_finder/gap-filling.html");
  }

  @Override
  public boolean checkParameterValues(Collection<String> errorMessages) {
    final boolean superCheck = super.checkParameterValues(errorMessages);

    var fileSelection = getParameter(rawFile).cloneParameter().getValue();
    final RawDataFile[] files = fileSelection.getMatchingRawDataFiles();
    if (files == null || files.length == 0) {
      return superCheck;
    }
    final RawDataFile file = files[0];
    if (!(file instanceof IMSImagingRawDataFile)) {
      errorMessages.add("Selected raw data file is not an IMS-imaging file.");
      return false;
    }
    return superCheck;
  }

  @Override
  public @NotNull IonMobilitySupport getIonMobilitySupport() {
    return IonMobilitySupport.SUPPORTED;
  }
}
