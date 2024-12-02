package io.github.mzmine.modules.dataprocessing.featdet_xic;

import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.ComboParameter;
import io.github.mzmine.parameters.parametertypes.DoubleParameter;
import io.github.mzmine.parameters.parametertypes.IntegerParameter;
import io.github.mzmine.parameters.parametertypes.PercentParameter;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesParameter;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelection;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelectionType;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelectionParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.MZToleranceParameter;

public class LoopXicBuilderParameters extends SimpleParameterSet {

  public static final RawDataFilesParameter files = new RawDataFilesParameter(
      new RawDataFilesSelection(RawDataFilesSelectionType.GUI_SELECTED_FILES));

  public static final ScanSelectionParameter scans = new ScanSelectionParameter(
      new ScanSelection(1));

  public static final MZToleranceParameter mzTol = new MZToleranceParameter("Initial m/z tolerance",
      "Scan-to-scan tolerance for building the XIC. The first \"Number of seed data points\" will be added with the given tolerance.\n"
          + "After given number of data points has been added, the m/z range of the feature will be reduced to the current m/z range.",
      0.005d, 10d);

  public static final PercentParameter mzRangeTolerance = new PercentParameter(
      "Confined m/z tolerance",
      "After \"Number of seed data points\" were added, the range is confined to those data points' m/z range plus this tolerance.",
      0.1d, 0d, 10d);

  public static final IntegerParameter numSeeds = new IntegerParameter("Number of seed data points",
      "The number of data points added to an XIC before the m/z range is confined.", 7, 0, 100);

  public static final DoubleParameter minHighestIntensity = new DoubleParameter(
      "Minimum highest intensity",
      "The minimum intensity of an XIC to be considered for XIC construction.",
      MZmineCore.getConfiguration().getIntensityFormat(), 1E3);

  public static final IntegerParameter maxNumZeros = new IntegerParameter(
      "Maximum number of consecutive 0s",
      "Maximum number of consecutive zero values for an XIC to be considered terminated.", 2);

  public static final ComboParameter<XICMergeMethod> mergeMode = new ComboParameter<>(
      "Duplicate XIC merging", """
      Select how duplicate XICs will be merged.
      Override zeros: Zeros will be overridden during merging, overlapping data points are discarded. 
      Most intense: The most intense point will be retained of both XICs have a data point in one scan, others are discarded.
      Iterative: The most intense data point will be retained, the others are used to create a new XIC.
      """, XICMergeMethod.values(), XICMergeMethod.MOST_INTENSE);

  public LoopXicBuilderParameters() {
    super(new Parameter[]{files, scans, mzTol, mzRangeTolerance, numSeeds, minHighestIntensity,
        maxNumZeros, mergeMode});
  }
}
