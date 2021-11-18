package io.github.mzmine.modules.dataprocessing.featdet_ms2builder;

import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.impl.IonMobilitySupport;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesParameter;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelectionParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.MZToleranceParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance.Unit;
import io.github.mzmine.parameters.parametertypes.tolerances.RTToleranceParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.mobilitytolerance.MobilityToleranceParameter;
import org.jetbrains.annotations.NotNull;

public class Ms2FeatureListBuilderParameters extends SimpleParameterSet {

  public static final RawDataFilesParameter files = new RawDataFilesParameter();

  public static final MZToleranceParameter mzTolerance = new MZToleranceParameter(
      "Precursor m/z tolerance",
      "Defines a m/z tolerance between precursors. If two precursor m/zs are within the given tolerance, they will be merged",
      0.005, 10, false);

  public static final RTToleranceParameter rtTolerance = new RTToleranceParameter(
      "Precursor rt tolerance",
      "Defines a rt tolerance between precursors. If two precursor rts are within the given tolerance, they will be merged",
      new RTTolerance(0.1f, Unit.MINUTES));

  public static final MobilityToleranceParameter mobTolerance = new MobilityToleranceParameter(
      "Precursor Mobility tolerance",
      "Defines a precursor tolerance between precursors. If two precursor mobilities are within the given tolerance, they will be merged");

  public static final ScanSelectionParameter scanSelection = new ScanSelectionParameter(
      new ScanSelection(2));

  public Ms2FeatureListBuilderParameters() {
    super(new Parameter[]{files, scanSelection, mzTolerance, rtTolerance, mobTolerance});
  }

  @Override
  public @NotNull IonMobilitySupport getIonMobilitySupport() {
    return IonMobilitySupport.ONLY;
  }
}
