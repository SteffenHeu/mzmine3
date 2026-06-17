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

package io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max;

import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.MassSpectrumType;
import io.github.mzmine.datamodel.SimpleRange.SimpleIntegerRange;
import io.github.mzmine.datamodel.impl.SimpleMassSpectrum;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectorPreprocessor;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.PreprocessedIntensitiesProvider;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.util.collections.IndexRange;
import io.github.mzmine.util.maths.Weighting;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Experimental variant of {@link LocalMaxMassDetector} with an adaptive centroid m/z calculation,
 * kept separate so it can be profiled against the baseline. Detection (maxima, edges) is identical;
 * only the per-peak m/z estimation changes, with a three-way branch applied to intense peaks:
 * <ol>
 *   <li><b>Flat top / detector saturation</b> (a run of near-maximum points): the geometric center
 *   of the plateau (mean m/z of the near-max points), ignoring an apex spike on the plateau edge.</li>
 *   <li><b>Sharp and asymmetric</b>: an apex-centered least-squares parabola vertex, which tracks
 *   the mode and avoids the tail bias of the weighted centroid.</li>
 *   <li><b>Otherwise</b>: the baseline weighted centroid (intensity-weighted mean over the symmetric
 *   window above {@link #MZ_WEIGHTING_THRESHOLD}).</li>
 * </ol>
 * Weak peaks always use the weighted centroid (the fits are unstable at low S/N and the bias is
 * small there).
 */
public class AdaptiveLocalMaxMassDetector implements MassDetector, PreprocessedIntensitiesProvider {

  public static final String NAME = "Adaptive local maximum mass detector";
  private static final Logger logger = Logger.getLogger(
      AdaptiveLocalMaxMassDetector.class.getName());

  // Thresholds for peak resolving and centroiding
  private static final double MZ_WEIGHTING_THRESHOLD = 0.4;
  private static final double VALLEY_FACTOR = 0.7;
  private static final double RISE_FACTOR = 1 / 0.7;
  private static final Weighting mzWeighting = Weighting.LINEAR;

  // Adaptive centroid tuning
  // only intense peaks get the fit / plateau treatment; weak peaks keep the weighted centroid
  private static final double ADAPTIVE_MIN_PERCENT_BASE = 1.0;
  // apex left/right point-count ratio above which a peak is treated as asymmetric (-> parabola)
  private static final double ASYMMETRY_THRESHOLD = 1.5;
  // number of points (apex +/- (N-1)/2) used for the apex-centered parabola
  private static final int PARABOLA_POINTS = 5;
  // a run of at least this many near-equal high points is treated as a saturated plateau
  private static final int FLAT_TOP_MIN_POINTS = 3;
  // consecutive points within this fraction of the apex height count as "equal" (clipping)
  private static final double SATURATION_TOL = 0.005;
  // a saturation plateau must sit at or above this fraction of the apex height
  private static final double SATURATION_MIN_FRACTION = 0.90;

  /**
   * Minimum peak length in points excluding zeros
   */
  private final int minNonZeroDp;

  private final double noiseLevel;
  private final AbundanceMeasure intensityCalculation;

  /**
   * Optional preprocessing of the intensities (e.g. smoothing) before maximum and edge detection.
   * Returns the original intensities when no preprocessing is configured.
   */
  private final @NotNull MassDetectorPreprocessor preprocessor;

  /**
   * Preprocessed (e.g. smoothed) intensities of the most recently processed spectrum, or null if no
   * preprocessing was applied. Kept for the preview to display the preprocessed series.
   */
  private double @Nullable [] lastPreprocessedIntensities;

  /**
   * Base peak intensity of the spectrum currently being processed, used to gate the adaptive
   * centroid to intense peaks.
   */
  private double spectrumBaseIntensity;

  public AdaptiveLocalMaxMassDetector() {
    this(0, AbundanceMeasure.Height, 3, new LocalMaxNoSmoothingModule());
  }

  public AdaptiveLocalMaxMassDetector(final double noiseLevel,
      final AbundanceMeasure intensityCalculation, final int minNonZeroDp) {
    this(noiseLevel, intensityCalculation, minNonZeroDp, new LocalMaxNoSmoothingModule());
  }

  public AdaptiveLocalMaxMassDetector(final double noiseLevel,
      final AbundanceMeasure intensityCalculation, final int minNonZeroDp,
      final @NotNull MassDetectorPreprocessor preprocessor) {
    this.noiseLevel = noiseLevel;
    this.intensityCalculation = intensityCalculation;
    this.minNonZeroDp = minNonZeroDp;
    this.preprocessor = preprocessor;
  }

  /**
   * The maximum difference between two consecutive values. Either first 2 non zero values from left
   * or right, which ever is greater. Also assumes and checks that trailing and leading 0 intensity
   * values are available. Otherwise, returns a default value.
   */
  private static double getMaxMzDiff(final double[] mzs, final double[] intensities,
      final int numPoints) {
    double maxDistance = -1;

    // TOF and it seems like Orbitrap have highest mass difference between values at the end of the spectrum
    int top = numPoints - 1;
    for (; top > 1; top--) {
      // tof mz value distances are proportional to sqrt(m/z)
      // so the biggest mass diff will be at the top of the spectrum
      if (intensities[top] > 0 && intensities[top - 1] > 0) {
        // use first two points that are non zero. Who knows if padding zeros are actually spaced
        // according to the digitizer times.
        maxDistance = Math.abs(mzs[top] - mzs[top - 1]);
        break;
      }
    }

    // some detectors may have highest distance in beginning so better just add check here
    double leftMaxDistance = -1;
    for (int i = 1; i < top; i++) {
      // tof mz value distances are proportional to sqrt(m/z)
      // so the biggest mass diff will be at the top of the spectrum
      if (intensities[i] > 0 && intensities[i - 1] > 0) {
        // use first two points that are non zero. Who knows if padding zeros are actually spaced
        // according to the digitizer times.
        leftMaxDistance = Math.abs(mzs[i] - mzs[i - 1]);
        break;
      }
    }

    maxDistance = Math.max(leftMaxDistance, maxDistance);
    if (Double.compare(-1, maxDistance) == 0) {
      return 0.1; // nothing found so use default value
    }

    return maxDistance;
  }

  @Override
  public MassDetector create(final ParameterSet params) {
    final LocalMaxSmoothingOptions smoothingOption = params.getValue(
        LocalMaxMassDetectorParameters.smoothing);
    final ParameterSet smoothingParams = params.getEmbeddedParameterValue(
        LocalMaxMassDetectorParameters.smoothing);
    final MassDetectorPreprocessor preprocessor = smoothingOption.createPreprocessor(
        smoothingParams);

    return new AdaptiveLocalMaxMassDetector(
        params.getValue(LocalMaxMassDetectorParameters.noiseLevel),
        params.getValue(LocalMaxMassDetectorParameters.intensityCalculation),
        params.getValue(LocalMaxMassDetectorParameters.minNumberOfDp), preprocessor);
  }

  @Override
  public boolean filtersActive() {
    return true;
  }

  @Override
  public double[][] getMassValues(final MassSpectrum spectrum) {
    final PreparedSpectrum prepared = prepareSpectrum(spectrum);
    if (prepared == null) {
      return new double[2][0];
    }

    final DoubleArrayList resultMzs = new DoubleArrayList();
    final DoubleArrayList resultIntensities = new DoubleArrayList();

    for (final IndexRange range : prepared.ranges()) {
      findAndCentroidPeaks(prepared.mzs(), prepared.detectIntensities(), prepared.origIntensities(),
          range, prepared.absMinIntensity(), resultMzs, resultIntensities, null);
    }

    final double[][] result = new double[2][];
    result[0] = resultMzs.toDoubleArray();
    result[1] = resultIntensities.toDoubleArray();

    return result;
  }

  /**
   * Detects peaks like {@link #getMassValues(MassSpectrum)} but returns rich descriptors (apex,
   * edges, centroid m/z and height) instead of only the centroid arrays. Intended for
   * analysis/diagnostics, not the hot import path.
   */
  public @NotNull List<LocalMaxPeak> detectPeaks(final MassSpectrum spectrum) {
    final PreparedSpectrum prepared = prepareSpectrum(spectrum);
    if (prepared == null) {
      return List.of();
    }

    final List<LocalMaxPeak> peaks = new ArrayList<>();
    // the result arrays are required by the shared detection method but are not needed here
    final DoubleArrayList ignoredMzs = new DoubleArrayList();
    final DoubleArrayList ignoredIntensities = new DoubleArrayList();

    for (final IndexRange range : prepared.ranges()) {
      findAndCentroidPeaks(prepared.mzs(), prepared.detectIntensities(), prepared.origIntensities(),
          range, prepared.absMinIntensity(), ignoredMzs, ignoredIntensities, peaks);
    }

    return peaks;
  }

  /**
   * Shared preparation: extracts the spectrum arrays, detects the consecutive ranges, applies the
   * optional preprocessing and records the preprocessed series. Returns null if the spectrum is too
   * small to process.
   */
  private @Nullable PreparedSpectrum prepareSpectrum(final MassSpectrum spectrum) {
    // reset so the preview never shows a stale preprocessed series for an unprocessed spectrum
    lastPreprocessedIntensities = null;

    final int numPoints = spectrum.getNumberOfDataPoints();
    if (numPoints < minNonZeroDp) {
      return null;
    }

    // Extract data to local arrays for faster access (avoiding MemorySegment overhead)
    final double[] mzs = new double[numPoints];
    final double[] intensities = new double[numPoints];

    // Bulk extraction loop, also tracks the base peak intensity for the adaptive gate
    double baseIntensity = 0;
    for (int i = 0; i < numPoints; i++) {
      mzs[i] = spectrum.getMzValue(i);
      final double intensity = spectrum.getIntensityValue(i);
      intensities[i] = intensity;
      if (intensity > baseIntensity) {
        baseIntensity = intensity;
      }
    }
    spectrumBaseIntensity = baseIntensity;

    final double maxDiff = getMaxMzDiff(mzs, intensities, numPoints);

    final List<IndexRange> consecutiveRanges = new ArrayList<>();

    double absMinIntensity = Double.MAX_VALUE;
    if (intensities[0] > 0) {
      absMinIntensity = intensities[0];
    }

    int currentRegionStart = 0;
    double lastMz = mzs[0];
    boolean onePointAboveNoise = false;
    for (int i = 1; i < numPoints; i++) {
      final double thisMz = mzs[i];
      final double thisInt = intensities[i];

      // Track absolute minimum intensity > 0
      if (thisInt > 0 && thisInt < absMinIntensity) {
        absMinIntensity = thisInt;
      }

      if (thisInt > noiseLevel) {
        onePointAboveNoise = true;
      }

      final double mzDelta = thisMz - lastMz;

      // If the gap is too large, we close the current region and start a new one
      if (mzDelta >= maxDiff) {
        // Only add regions that contain enough data points to form a peak (e.g., > 2 points)
        // data point at i was a jump to a new region so exclude this point
        if (i - currentRegionStart >= minNonZeroDp && onePointAboveNoise) {
          consecutiveRanges.add(IndexRange.ofInclusive(currentRegionStart, i - 1));
        }
        currentRegionStart = i;
        onePointAboveNoise = false;
      }

      lastMz = thisMz;
    }

    // Add the final region if valid
    if (numPoints - currentRegionStart >= minNonZeroDp && onePointAboveNoise) {
      consecutiveRanges.add(IndexRange.ofInclusive(currentRegionStart, numPoints - 1));
    }

    // Handle case where spectrum was all zeros or empty
    if (absMinIntensity == Double.MAX_VALUE) {
      absMinIntensity = 0.0;
    }

    // Optional preprocessing: maximum and edge detection run on the (potentially smoothed)
    // intensities, while the intensity calculation keeps using the original intensities.
    final double[] detectIntensities = preprocessor.preprocessIntensities(intensities,
        consecutiveRanges);
    // store only if preprocessing actually changed the data (NONE returns the original array)
    lastPreprocessedIntensities = (detectIntensities == intensities) ? null : detectIntensities;

    return new PreparedSpectrum(mzs, intensities, detectIntensities, consecutiveRanges,
        absMinIntensity);
  }

  /**
   * Identifies peaks within a continuous region using the valley/rise logic, then centroids them.
   *
   * @param detectIntensities the (potentially smoothed) intensities used for maximum and edge
   *                          detection.
   * @param origIntensities   the original intensities used for the intensity calculation.
   */
  private void findAndCentroidPeaks(final double[] mzs, final double[] detectIntensities,
      final double[] origIntensities, final IndexRange range, final double minIntensity,
      final DoubleArrayList resultMzs, final DoubleArrayList resultIntensities,
      @Nullable final List<LocalMaxPeak> peakSink) {

    // Find all raw local maxima (candidates) in the region above noise
    final IntArrayList candidateIndices = findLocalMaximaIndices(detectIntensities, range);

    if (candidateIndices.isEmpty()) {
      return;
    }

    // Filter and merge candidates based on the * 0.7 to / 0.7 rule
    int activePeakIdx = candidateIndices.getInt(0);
    int leftBoundary = range.min(); // Start of the region

    // todo check what happens if there are consecutive but zero intensities in the range
    for (int i = 1; i < candidateIndices.size(); i++) {
      final int nextCandidateIdx = candidateIndices.getInt(i);

      // Find the deepest valley between the current active peak and the next candidate (to the right)
      final int valleyIdx = findLowestValleyIndex(detectIntensities, activePeakIdx,
          nextCandidateIdx);

      final double activePeakInt = detectIntensities[activePeakIdx];
      final double nextCandidateInt = detectIntensities[nextCandidateIdx];
      final double valleyInt = detectIntensities[valleyIdx];

      // Rule: Separate if it drops below 0.7 * active AND rises 1.3 * valley
      final boolean dropsEnough = valleyInt < (VALLEY_FACTOR * activePeakInt);
      final boolean risesEnough = nextCandidateInt > (RISE_FACTOR * valleyInt);

      if (dropsEnough && risesEnough) {
        // They are separate peaks.
        // The right boundary for the current peak is the valley.
        // include valleyIdx in this peak and also as potential new peak - similar to belows use of range.maxExclusive
        processSinglePeak(mzs, origIntensities, leftBoundary, valleyIdx + 1, minIntensity,
            resultMzs, resultIntensities, peakSink);

        // Move to the next peak
        activePeakIdx = nextCandidateIdx;
        leftBoundary = valleyIdx; // Next peak starts from this valley
      } else {
        // They are not resolved enough; merge them.
        // Update the active peak index to be the higher of the two.
        // left boundary is kept to merge the peaks
        if (nextCandidateInt > activePeakInt) {
          activePeakIdx = nextCandidateIdx;
        }
      }
    }

    processSinglePeak(mzs, origIntensities, leftBoundary, range.maxExclusive(), minIntensity,
        resultMzs, resultIntensities, peakSink);
  }

  /**
   * Helper to find all indices in range that are local maxima > noise.
   */
  private IntArrayList findLocalMaximaIndices(final double[] intensities, final IndexRange range) {
    final IntArrayList indices = new IntArrayList();
    final int start = range.min();
    final int end = range.maxExclusive();

    for (int i = start; i < end; i++) {
      final double currentInt = intensities[i];

      final double leftInt = (i == start) ? 0 : intensities[i - 1];
      final double rightInt = (i == end - 1) ? 0 : intensities[i + 1];

      // Check local max
      if (currentInt >= leftInt && currentInt > rightInt) {
        indices.add(i);
      }
    }
    return indices;
  }

  /**
   * Finds the index with minimum intensity between two indices.
   */
  private int findLowestValleyIndex(final double[] intensities, final int startIdx,
      final int endIdx) {
    int minIdx = startIdx;
    double minVal = Double.MAX_VALUE;

    // Search strictly between the peaks
    for (int i = startIdx + 1; i < endIdx; i++) {
      final double val = intensities[i];
      if (val < minVal) {
        minVal = val;
        minIdx = i;
      }
    }
    return minIdx;
  }

  /**
   * Calculates the adaptive centroid and intensity for a defined peak and adds to results. The peak
   * edges ({@code startIdx}, {@code endIdx}) are determined on the (potentially smoothed) detection
   * series, but the intensity calculation (apex, height, area, centroid) runs on the original
   * intensities passed here.
   *
   * @param startIdx        Inclusive start of peak region (valley or range start).
   * @param endIdx          Exclusive end of peak region (valley or range end).
   * @param absMinIntensity Absolute minimum intensity of the whole spectrum.
   */
  private void processSinglePeak(final double[] mzs, final double[] intensities, final int startIdx,
      final int endIdx, final double absMinIntensity, final DoubleArrayList resultMzs,
      final DoubleArrayList resultIntensities, @Nullable final List<LocalMaxPeak> peakSink) {

    if (endIdx - startIdx < minNonZeroDp) {
      return;
    }

    // Locate the apex on the original intensities within the detected edges.
    int peakMaxIdx = startIdx;
    double maxIntensity = intensities[startIdx];
    for (int i = startIdx + 1; i < endIdx; i++) {
      if (intensities[i] > maxIntensity) {
        maxIntensity = intensities[i];
        peakMaxIdx = i;
      }
    }

    final double detectionThreshold = Math.max(noiseLevel, 2 * absMinIntensity);

    // check the actual intensity as noise level not the area which is harder to optimize
    // the peak detection should pick the same peaks for the same noise level no matter
    // if AREA or HEIGHT is selected for intensity representation
    if (maxIntensity < detectionThreshold) {
      return;
    }

    final double mzWeightingCutoff = maxIntensity * MZ_WEIGHTING_THRESHOLD;
    final int minPointsPerEdge = Math.min(peakMaxIdx - startIdx, endIdx - peakMaxIdx);
    final SimpleIntegerRange peakSymmetryRange = new SimpleIntegerRange(
        peakMaxIdx - minPointsPerEdge, peakMaxIdx + minPointsPerEdge);

    double sumMzInt = 0.0;
    double sumIntForMz = 0.0;
    double totalArea = 0.0;
    int nonZeroPoints = 0;

    // Integrate Valley-to-Valley
    for (int i = startIdx; i < endIdx; i++) {
      final double intensity = intensities[i];
      final double mz = mzs[i];

      // Integration: Sum ALL points in the valley-to-valley region
      totalArea += intensity;

      if (intensity > mzWeightingCutoff && peakSymmetryRange.contains(i)) {
        sumMzInt += (mz * mzWeighting.transform(intensity));
        sumIntForMz += mzWeighting.transform(intensity);
      }
      if (intensity > 0) {
        nonZeroPoints++;
      }
    }

    // Safety check if no points met the weighting criteria (unlikely if max > detectionThreshold)
    if (sumIntForMz == 0.0 || nonZeroPoints < minNonZeroDp) {
      return;
    }

    final double centroidMz = adaptiveCentroidMz(mzs, intensities, startIdx, endIdx, peakMaxIdx,
        maxIntensity, sumMzInt / sumIntForMz);
    final double finalIntensity =
        (intensityCalculation == AbundanceMeasure.Area) ? totalArea : maxIntensity;

    resultMzs.add(centroidMz);
    resultIntensities.add(finalIntensity);

    if (peakSink != null) {
      peakSink.add(new LocalMaxPeak(peakMaxIdx, startIdx, endIdx, centroidMz, maxIntensity));
    }
  }

  /**
   * Three-way adaptive m/z. For peaks below {@link #ADAPTIVE_MIN_PERCENT_BASE} of the base peak the
   * weighted centroid is kept (fits are unstable at low S/N). For intense peaks: a saturated
   * plateau uses the plateau center; a sharp asymmetric peak uses an apex-centered parabola;
   * everything else keeps the weighted centroid. Any degenerate fit falls back to the weighted
   * centroid.
   */
  private double adaptiveCentroidMz(final double[] mzs, final double[] intensities,
      final int startIdx, final int endIdx, final int peakMaxIdx, final double maxIntensity,
      final double weightedCentroidMz) {

    final boolean intenseEnough =
        maxIntensity >= spectrumBaseIntensity * (ADAPTIVE_MIN_PERCENT_BASE / 100.0);
    if (!intenseEnough) {
      return weightedCentroidMz;
    }

    // 1. flat top / detector saturation -> center of the clipped plateau (a run of near-equal high
    // points), robust to a noise spike sitting just above the plateau
    final double plateau = saturationPlateauCenter(mzs, intensities, startIdx, endIdx,
        maxIntensity);
    if (!Double.isNaN(plateau)) {
      return plateau;
    }

    // 2. sharp and asymmetric -> apex-centered parabola vertex
    final int leftPoints = peakMaxIdx - startIdx;
    final int rightPoints = endIdx - 1 - peakMaxIdx;
    final int lo = Math.min(leftPoints, rightPoints);
    final int hi = Math.max(leftPoints, rightPoints);
    final boolean asymmetric = lo == 0 || (double) hi / lo >= ASYMMETRY_THRESHOLD;
    if (asymmetric) {
      final double fit = apexParabolaVertexMz(mzs, intensities, peakMaxIdx, startIdx, endIdx,
          PARABOLA_POINTS);
      if (!Double.isNaN(fit)) {
        return fit;
      }
    }

    // 3. otherwise the weighted centroid
    return weightedCentroidMz;
  }

  /**
   * Center (mean m/z) of the longest run of consecutive near-equal high points - the clipped top of
   * a saturated peak. A point joins the run only if it is at or above
   * {@link #SATURATION_MIN_FRACTION} of the apex and within {@link #SATURATION_TOL} of the previous
   * point's intensity, so a single apex spike sitting above the plateau is excluded. Returns NaN if
   * no run of at least {@link #FLAT_TOP_MIN_POINTS} points exists (i.e. not a flat top).
   */
  private static double saturationPlateauCenter(final double[] mzs, final double[] intensities,
      final int startIdx, final int endIdx, final double maxIntensity) {
    final double minLevel = maxIntensity * SATURATION_MIN_FRACTION;
    final double tol = maxIntensity * SATURATION_TOL;

    int bestStart = -1;
    int bestLen = 0;
    int i = startIdx;
    while (i < endIdx) {
      if (intensities[i] < minLevel) {
        i++;
        continue;
      }
      int j = i + 1;
      while (j < endIdx && intensities[j] >= minLevel
          && Math.abs(intensities[j] - intensities[j - 1]) <= tol) {
        j++;
      }
      if (j - i > bestLen) {
        bestLen = j - i;
        bestStart = i;
      }
      i = j;
    }

    if (bestLen < FLAT_TOP_MIN_POINTS) {
      return Double.NaN;
    }
    double sum = 0;
    for (int k = bestStart; k < bestStart + bestLen; k++) {
      sum += mzs[k];
    }
    return sum / bestLen;
  }

  /**
   * Vertex m/z of the least-squares parabola over the {@code n} points centered on the apex
   * ({@code apex +/- (n-1)/2}), clamped to the peak edges. Apex-centered (not the n highest points)
   * so a tailing flank does not skew the window. Returns NaN if there are fewer than 3 points, the
   * top is too flat (degenerate), or the vertex falls outside the peak edges.
   */
  private static double apexParabolaVertexMz(final double[] mzs, final double[] intensities,
      final int peakMaxIdx, final int startIdx, final int endIdx, final int n) {
    final int half = (n - 1) / 2;
    final int from = Math.max(startIdx, peakMaxIdx - half);
    final int to = Math.min(endIdx - 1, peakMaxIdx + half);
    final int count = to - from + 1;
    if (count < 3) {
      return Double.NaN;
    }

    // least squares y = a*u^2 + b*u + c with u = mz - apexMz (centering improves conditioning)
    final double xCenter = mzs[peakMaxIdx];
    final double s0 = count;
    double s1 = 0, s2 = 0, s3 = 0, s4 = 0, t0 = 0, t1 = 0, t2 = 0;
    for (int i = from; i <= to; i++) {
      final double u = mzs[i] - xCenter;
      final double y = intensities[i];
      final double u2 = u * u;
      s1 += u;
      s2 += u2;
      s3 += u2 * u;
      s4 += u2 * u2;
      t0 += y;
      t1 += u * y;
      t2 += u2 * y;
    }
    final double det = det3(s4, s3, s2, s3, s2, s1, s2, s1, s0);
    if (Math.abs(det) < 1e-30) {
      return Double.NaN;
    }
    final double a = det3(t2, s3, s2, t1, s2, s1, t0, s1, s0) / det;
    final double b = det3(s4, t2, s2, s3, t1, s1, s2, t0, s0) / det;
    if (Math.abs(a) < 1e-30) {
      return Double.NaN; // too flat -> degenerate vertex
    }
    final double vertex = xCenter - b / (2 * a);
    // reject runaway vertices outside the peak (e.g. nearly-flat noisy tops)
    if (vertex < mzs[startIdx] || vertex > mzs[endIdx - 1]) {
      return Double.NaN;
    }
    return vertex;
  }

  private static double det3(final double a, final double b, final double c, final double d,
      final double e, final double f, final double g, final double h, final double i) {
    return a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
  }

  @Override
  public @NotNull String getName() {
    return NAME;
  }

  @Override
  public @Nullable Class<? extends ParameterSet> getParameterSetClass() {
    return LocalMaxMassDetectorParameters.class;
  }

  @Override
  public double[][] getMassValues(double[] mzs, double[] intensities,
      @NotNull MassSpectrumType type) {
    return getMassValues(new SimpleMassSpectrum(mzs, intensities, type));
  }

  @Override
  public double @Nullable [] getLastPreprocessedIntensities() {
    return lastPreprocessedIntensities;
  }

  /**
   * Shared, prepared spectrum data used by both {@link #getMassValues(MassSpectrum)} and
   * {@link #detectPeaks(MassSpectrum)}.
   */
  private record PreparedSpectrum(double[] mzs, double[] origIntensities,
                                  double[] detectIntensities, List<IndexRange> ranges,
                                  double absMinIntensity) {

  }
}
