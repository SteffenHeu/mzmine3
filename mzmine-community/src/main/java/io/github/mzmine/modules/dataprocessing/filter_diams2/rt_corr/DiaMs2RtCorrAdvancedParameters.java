/*
 * Copyright (c) 2004-2026 The mzmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.dataprocessing.filter_diams2.rt_corr;

import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.IntegerParameter;
import io.github.mzmine.parameters.parametertypes.OptionalParameter;
import io.github.mzmine.parameters.parametertypes.PercentParameter;
import io.github.mzmine.parameters.parametertypes.combowithinput.DefaultOffCustomOption;
import io.github.mzmine.parameters.parametertypes.combowithinput.DefaultOffCustomParameter;
import io.github.mzmine.parameters.parametertypes.combowithinput.DefaultOffCustomValue;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DiaMs2RtCorrAdvancedParameters extends SimpleParameterSet {

  public static final double DEFAULT_CORR_THRESHOLD = 0.001;
  public static final DefaultOffCustomValue<Integer> DEFAULT_POINTS_PER_EDGE = new DefaultOffCustomValue<>(
      DefaultOffCustomOption.DEFAULT, 2);

  public static final OptionalParameter<PercentParameter> correlationThreshold = new OptionalParameter<>(
      new PercentParameter("Relative intensity threshold",
          "Specify a percentage of the maximum intensity below which no further correlation will be performed. The default is 0.1% (0.1% mzmine ≥ 4.8 and 10% mzmine < 4.8)",
          DEFAULT_CORR_THRESHOLD, 0d, 1d));

  public static final DefaultOffCustomParameter<Integer> pointsPerEdge = new DefaultOffCustomParameter<>(
      new IntegerParameter("Minimum correlated points per edge",
          "Select the minimum number of correlated points per edge of a feature. Default = 2."), 2,
      2, false);

  public DiaMs2RtCorrAdvancedParameters() {
    super(correlationThreshold, pointsPerEdge);
  }

  @Override
  public Map<String, Parameter<?>> getNameParameterMap() {
    var map = super.getNameParameterMap();
    map.put("Correlation threshold", getParameter(correlationThreshold));
    return map;
  }

  public static DiaMs2RtCorrAdvancedParameters create(@Nullable Double corrThreshold,
      @NotNull DefaultOffCustomValue<Integer> pointsPerEdge) {
    ParameterSet parameterSet = new DiaMs2RtCorrAdvancedParameters().cloneParameterSet();
    parameterSet.setParameter(DiaMs2RtCorrAdvancedParameters.correlationThreshold,
        corrThreshold != null, Objects.requireNonNullElse(corrThreshold, DEFAULT_CORR_THRESHOLD));
    parameterSet.setParameter(DiaMs2RtCorrAdvancedParameters.pointsPerEdge, pointsPerEdge);
    return (DiaMs2RtCorrAdvancedParameters) parameterSet;
  }

  @Override
  public void handleLoadedParameters(Map<String, Parameter<?>> loadedParams, int loadedVersion) {
    super.handleLoadedParameters(loadedParams, loadedVersion);
    if (!loadedParams.containsKey(DiaMs2RtCorrAdvancedParameters.pointsPerEdge.getName())) {
      setParameter(DiaMs2RtCorrAdvancedParameters.pointsPerEdge, DEFAULT_POINTS_PER_EDGE);
    }
  }
}
