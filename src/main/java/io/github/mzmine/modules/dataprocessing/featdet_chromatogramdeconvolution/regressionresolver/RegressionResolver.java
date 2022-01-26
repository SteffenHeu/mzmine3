package io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.regressionresolver;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.AbstractResolver;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.util.MathUtils;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class RegressionResolver extends AbstractResolver {

  private final int numPoints;
  private final double startSlope;
  private final double stopSlope;
  private final double minHeight;
  private final int minPointsInFeature;
  private final double topToEdge;
  private final double threshold;
  private final Range<Double> rtRange;

  public RegressionResolver(@NotNull ParameterSet parameters, @NotNull ModularFeatureList flist) {
    super(parameters, flist);

    minHeight = parameters.getValue(RegressionResolverParameters.minHeight);
    startSlope = Math.abs(parameters.getValue(RegressionResolverParameters.minSlope));
    stopSlope = -startSlope;
    minPointsInFeature = parameters.getValue(RegressionResolverParameters.minNumPoints);
    numPoints = parameters.getValue(RegressionResolverParameters.slopeCalcPoints);
    topToEdge = parameters.getValue(RegressionResolverParameters.topToEdgeRatio);
    rtRange = parameters.getValue(RegressionResolverParameters.rtRange);
    threshold = parameters.getValue(RegressionResolverParameters.chromatographicThreshold)
        ? parameters.getParameter(RegressionResolverParameters.chromatographicThreshold)
        .getEmbeddedParameter().getValue() : 0d;
  }

  @Override
  public @NotNull List<Range<Double>> resolve(double[] x, double[] y) {

    final double minIntensity = MathUtils.calcQuantile(y, threshold);
    double maxIntensity = 0;
    for (int i = 0; i < y.length; i++) {
      maxIntensity = Math.max(maxIntensity, y[i]);
    }
    for (int i = 0; i < y.length; i++) {
      if (y[i] < minIntensity) {
        y[i] = 0;
      } else {
        y[i] /= maxIntensity;
      }
    }

    final List<Range<Double>> features = new ArrayList<>();

    int index = 0;
    Integer currentFeatureStart = null;
    Integer currentFeatureEnd = null;
    Integer currentFeatureTop = null;

    while (index < x.length - 1 - numPoints) {

      currentFeatureStart = findFeatureStart(x, y, index, numPoints, startSlope);
      if (currentFeatureStart == null) {
        break;
      }
      index = currentFeatureStart;

      currentFeatureTop = findFeatureTop(x, y, index, numPoints, stopSlope);
      if (currentFeatureTop == null) {
        break;
      }
      index = currentFeatureTop;

      if(!rtRange.contains(x[currentFeatureTop] - x[currentFeatureStart])) {
        index++;
        continue;
      }

      currentFeatureEnd = findFeatureEnd(x, y, index, numPoints, stopSlope);
      if (currentFeatureEnd == null) {
        break;
      }
      if(!rtRange.contains(x[currentFeatureEnd] - x[currentFeatureStart])) {
        index++;
        continue;
      }


      if ((y[currentFeatureTop] / y[currentFeatureStart] > topToEdge
          || y[currentFeatureTop] / y[currentFeatureEnd] > topToEdge) && (
          currentFeatureEnd - currentFeatureStart >= minPointsInFeature) && (
          y[currentFeatureTop] * maxIntensity > minHeight)) {
        features.add(Range.closed(x[currentFeatureStart], x[currentFeatureEnd]));
      }
      index = currentFeatureEnd + 1;
    }

    return features;
  }

  private Integer findFeatureEnd(double[] x, double[] y, int index, int numPoints,
      double stopSlope) {

    // we start at the top, so we must surpass a gradient of "stopSlope" first, get steeper and
    // then end the next time the slope gets bigger than the stop slope
    boolean steeperThanStopSlope = false;
    boolean allowedToStop = false;
    double beginSlope = 0d;
    while (index < x.length - 1 - numPoints) {
      final double slope = getSlope(x, y, index, numPoints);
      if (slope < stopSlope && beginSlope == 0d) {
        steeperThanStopSlope = true;
        beginSlope = slope;
      }

//      if (slope < beginSlope && steeperThanStopSlope) {
//        allowedToStop = true;
//      }

      if (/*allowedToStop && */slope > stopSlope) {
        if(slope < 0) { // keep progressing to go the the true end of the feature
          while (getSlope(x, y, index, numPoints) <= 0 && index < x.length - 1 - numPoints) {
            index++;
          }
        }
        return index;
      }
      index++;
    }
    return null;
  }

  private Integer findFeatureTop(double[] x, double[] y, int index, int numPoints,
      double stopSlope) {

//    double lastSlope = getSlope(x, y, index, numPoints);
//    index++;
    double maxIntensity = 0;
    Integer maxIndex = null;
    while (index < x.length - 1 - numPoints) {
      final double slope = getSlope(x, y, index, numPoints);
      if (y[index] > maxIntensity) {
        maxIntensity = y[index];
        maxIndex = index;
      }
      if (slope
          < stopSlope) { // we have to find the decreasing edge, then we know we're past the top
        return maxIndex;
      }
      index++;
    }
    return maxIndex;
  }

  private Integer findFeatureStart(double[] x, double[] y, int index, int numPoints,
      double startSlope) {
    while (index < x.length - 1 - numPoints) {
      final double slope = getSlope(x, y, index, numPoints);
      if (slope > startSlope) {
        return index;
      }
      index++;
    }
    return null;
  }

  public static double getSlope(double[] x, double[] y, int startIndex, int numPoints) {
    double avgX = 0;
    double avgY = 0;
    int actualPoints = 0;
    for (int i = startIndex; i < startIndex + numPoints && i < x.length; i++) {
      avgX += x[i];
      avgY += y[i];
      actualPoints++;
    }
    avgX /= actualPoints;
    avgY /= actualPoints;

    double upperTerm = 0;
    double lowerTerm = 0;
    for (int i = startIndex; i < startIndex + actualPoints; i++) {
      upperTerm += (x[i] - avgX) * (y[i] - avgY);
      lowerTerm += Math.pow((x[i] - avgX), 2);
    }

    return upperTerm / lowerTerm;
  }

  @Override
  public @NotNull Class<? extends MZmineModule> getModuleClass() {
    return RegressionResolverModule.class;
  }
}
