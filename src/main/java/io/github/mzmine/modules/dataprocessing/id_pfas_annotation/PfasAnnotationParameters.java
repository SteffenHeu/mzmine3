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
import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.OptionalParameter;
import io.github.mzmine.parameters.parametertypes.PercentParameter;
import io.github.mzmine.parameters.parametertypes.filenames.FileNameParameter;
import io.github.mzmine.parameters.parametertypes.filenames.FileSelectionType;
import io.github.mzmine.parameters.parametertypes.ranges.IntRangeParameter;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.MZToleranceParameter;

public class PfasAnnotationParameters extends SimpleParameterSet {

  public static final FeatureListsParameter featureLists = new FeatureListsParameter();

  public static final FileNameParameter databaseFile = new FileNameParameter("Database file",
      "The database used to compute the substances and their MS/MS spectra.", "csv",
      FileSelectionType.OPEN);

  public static final IntRangeParameter nRange = new IntRangeParameter(
      "Backbone formula <i>n</i> range", "Range for the n repeating unit of the backbone", true,
      Range.closed(4, 12));
  public static final IntRangeParameter mRange = new IntRangeParameter(
      "Backbone formula <i>m</i> range", "Range for the m repeating unit of the backbone", true,
      Range.closed(0, 0));

  public static final IntRangeParameter kRange = new IntRangeParameter(
      "Backbone formula <i>k</i> range", "Range for the k repeating unit of the backbone", true,
      Range.closed(0, 0));

  public static final OptionalParameter<MZToleranceParameter> checkPrecursorMz = new OptionalParameter<>(
      new MZToleranceParameter("m/z tolerance",
          "If checked, only database entries with matching precursor m/zs will be matched.", 0.01,
          15, false));

  public static final PercentParameter minimumCoverage = new PercentParameter(
      "Minimum intensity coverage",
      "Describes how much intensity (in %) of the observed MS/MS spectrum has to be explained by the expected MS/MS spectra.", 0.6d, 0d, 1d);

  public PfasAnnotationParameters() {
    super(new Parameter[]{featureLists, databaseFile, nRange, mRange, kRange, checkPrecursorMz, minimumCoverage});
  }

}
