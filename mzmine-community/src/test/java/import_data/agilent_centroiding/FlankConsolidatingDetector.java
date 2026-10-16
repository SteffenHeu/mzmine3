package import_data.agilent_centroiding;

import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max.MobilityFlankConsolidator;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Benchmark variant of the mobility flank consolidation with any base detector and tolerance: the
 * base detector centroids every mobility scan (intensity = apex height), then the saturated
 * centroids get the m/z of their unsaturated flanks, see {@link MobilityFlankConsolidator}.
 */
public class FlankConsolidatingDetector implements MassDetector {

  private final @NotNull MassDetector base;
  private final double threshold;
  private final @NotNull MZTolerance tolerance;

  /**
   * @param base      base detector, must report the apex height as intensity
   * @param threshold saturation threshold of the mobility scans
   * @param tolerance m/z tolerance to follow an ion into the neighbouring scans
   */
  public FlankConsolidatingDetector(@NotNull final MassDetector base, final double threshold,
      @NotNull final MZTolerance tolerance) {
    this.base = base;
    this.threshold = threshold;
    this.tolerance = tolerance;
  }

  @Override
  public @NotNull List<double[][]> getMobilityScanMassValues(
      @NotNull final List<? extends MassSpectrum> mobilityScans) {
    final List<double[][]> values = new ArrayList<>(mobilityScans.size());
    final List<double[]> mzs = new ArrayList<>(mobilityScans.size());
    final List<double[]> heights = new ArrayList<>(mobilityScans.size());
    for (final MassSpectrum scan : mobilityScans) {
      final double[][] scanValues = base.getMassValues(scan);
      values.add(scanValues);
      mzs.add(scanValues[0]);
      heights.add(scanValues[1]);
    }
    final List<double[]> corrected = MobilityFlankConsolidator.consolidate(mzs, heights,
        threshold, tolerance);
    for (int i = 0; i < corrected.size(); i++) {
      if (corrected.get(i) != mzs.get(i)) {
        values.set(i, sorted(corrected.get(i), values.get(i)[1]));
      }
    }
    return values;
  }

  private static double[][] sorted(final double[] mzs, final double[] intensities) {
    if (MobilityFlankConsolidator.isSorted(mzs)) {
      return new double[][]{mzs, intensities};
    }
    final Integer[] order = new Integer[mzs.length];
    for (int i = 0; i < order.length; i++) {
      order[i] = i;
    }
    Arrays.sort(order, (a, b) -> Double.compare(mzs[a], mzs[b]));
    final double[][] result = new double[2][mzs.length];
    for (int i = 0; i < order.length; i++) {
      result[0][i] = mzs[order[i]];
      result[1][i] = intensities[order[i]];
    }
    return result;
  }

  @Override
  public double[][] getMassValues(final MassSpectrum spectrum) {
    return base.getMassValues(spectrum);
  }

  @Override
  public boolean filtersActive() {
    return true;
  }

  @Override
  public MassDetector create(final ParameterSet params) {
    throw new UnsupportedOperationException("Benchmark only");
  }

  @Override
  public @NotNull String getName() {
    return "Flank consolidation (benchmark)";
  }

  @Override
  public @Nullable Class<? extends ParameterSet> getParameterSetClass() {
    return null;
  }
}
