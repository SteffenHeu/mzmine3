/*
 * Copyright (c) 2004-2025 The mzmine Development Team
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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.tools.tools_autoparam.optimizer;

import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.BooleanParameter;
import java.util.Arrays;

public class OptimizerParameterParameters extends SimpleParameterSet {

  public static final BooleanParameter optimizeTopEdge = new BooleanParameter("Optimize top/edge",
      "", true);
  public static final BooleanParameter optimizeMinConsecutive = new BooleanParameter(
      "Optimize min Consecutive", "", true);
  public static final BooleanParameter optimizeFWHM = new BooleanParameter("Optimize FWHM", "",
      true);
  public static final BooleanParameter optimizeMinHeight = new BooleanParameter(
      "Optimize Min height", "", true);
  public static final BooleanParameter optimizeScanToScanMzTolerance = new BooleanParameter(
      "Optimize scan to scan mz tol", "", true);
  public static final BooleanParameter optimizeNoiseLevel = new BooleanParameter(
      "Optimize Noise level", "", true);
  public static final BooleanParameter optimizeRtSampleToSample = new BooleanParameter(
      "Optimize RT sample to sample tol", "", true);
  public static final BooleanParameter optimizeChromThreshold = new BooleanParameter("Optimize Chromatographic threshold", "", true);

  public OptimizerParameterParameters() {
    super(optimizeTopEdge, optimizeFWHM, optimizeMinConsecutive, optimizeMinHeight,
        optimizeScanToScanMzTolerance, optimizeNoiseLevel, optimizeRtSampleToSample, optimizeChromThreshold);
  }

  public static OptimizerParameterParameters create(boolean topEdge, boolean minConsec,
      boolean fwhm, boolean minHeight, boolean scanToScanMz, boolean noise,
      boolean rtSampleToSample, boolean chromThresh) {

    OptimizerParameterParameters param = (OptimizerParameterParameters) new OptimizerParameterParameters().cloneParameterSet();
    param.setParameter(optimizeTopEdge, topEdge);
    param.setParameter(optimizeMinConsecutive, minConsec);
    param.setParameter(optimizeFWHM, fwhm);
    param.setParameter(optimizeMinHeight, minHeight);
    param.setParameter(optimizeScanToScanMzTolerance, scanToScanMz);
    param.setParameter(optimizeNoiseLevel, noise);
    param.setParameter(optimizeRtSampleToSample, rtSampleToSample);
    param.setParameter(optimizeChromThreshold, chromThresh);
    return param;
  }

  public int getNumSelected() {
    return (int) Arrays.stream(getParameters()).filter(param -> param instanceof BooleanParameter)
        .map(BooleanParameter.class::cast).filter(BooleanParameter::getValue).count();
  }
}
