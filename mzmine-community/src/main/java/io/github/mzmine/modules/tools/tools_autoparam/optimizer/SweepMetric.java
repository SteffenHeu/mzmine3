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

package io.github.mzmine.modules.tools.tools_autoparam.optimizer;

import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.types.annotations.shapeclassification.RtQualitySummaryType;
import io.github.mzmine.datamodel.statistics.FeaturesDataTable;
import io.github.mzmine.modules.dataanalysis.utils.StatisticUtils;
import io.github.mzmine.modules.dataanalysis.utils.imputation.ImputationFunctions;
import io.github.mzmine.modules.dataprocessing.filter_featurefilter.peak_fitter.PeakShapeClassification;
import io.github.mzmine.parameters.parametertypes.statistics.AbundanceDataTablePreparationConfig;
import io.github.mzmine.util.MathUtils;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A metric that can be evaluated on a {@link ParameterSweepResult}. Each implementation corresponds
 * to one of the objectives used in {@link LcMsOptimizationProblem}.
 * <p>
 * Convenience singleton constants are provided for all parameterless metrics. The
 * {@link BenchmarkTargetCount} record carries its own target list.
 *
 * <pre>{@code
 * double score = SweepMetric.ROWS_BELOW_CV20.evaluate(result);
 * double score = new SweepMetric.BenchmarkTargetCount(targets).evaluate(result);
 * }</pre>
 */
public sealed interface SweepMetric {

  // --- Singleton constants for parameterless metrics ---

  RowsBelowCv20 ROWS_BELOW_CV20 = new RowsBelowCv20();
  RowsWithIsosBelowCv20 ROWS_WITH_ISOS_BELOW_CV20 = new RowsWithIsosBelowCv20();
  FeaturesWithIsotopes FEATURES_WITH_ISOTOPES = new FeaturesWithIsotopes();
  DoublePeakRatio DOUBLE_PEAK_RATIO = new DoublePeakRatio();
  FillRatio FILL_RATIO = new FillRatio();
  SlawIntegrationScore CV20_ISO_ROWS_RATIO = new SlawIntegrationScore();
  HarmonicSlawIsotopes HARMONIC_SLAW_ISOTOPES = new HarmonicSlawIsotopes();

  // --- Interface contract ---

  /**
   * Human-readable metric name for display and logging.
   */
  @NotNull String name();

  /**
   * Whether a higher value means a better result.
   */
  boolean higherIsBetter();

  /**
   * Evaluates the metric on the given sweep result.
   *
   * @param result the sweep result to evaluate; returns {@link Double#NaN} if
   *               {@link ParameterSweepResult#featureList()} is {@code null}
   */
  double evaluate(@NotNull ParameterSweepResult result);

  // --- Helper ---

  /**
   * Builds a {@link FeaturesDataTable} from the feature list for abundance-based metrics.
   */
  private static @Nullable FeaturesDataTable buildDataTable(FeatureList featureList) {
    final var config = new AbundanceDataTablePreparationConfig(AbundanceMeasure.Area,
        ImputationFunctions.Zero);
    return StatisticUtils.extractAbundancesPrepareData(featureList.getRows(),
        featureList.getRawDataFiles(), config);
  }

  // --- Implementations ---

  /**
   * Maximise: number of rows whose relative standard deviation (RSD) across all files is ≤ 20 %.
   * Mirrors {@link LcMsOptimizationProblem}'s {@code maximizeCv20} objective.
   */
  record RowsBelowCv20() implements SweepMetric {

    @Override
    public @NotNull String name() {
      return "Rows below CV 20%";
    }

    @Override
    public boolean higherIsBetter() {
      return true;
    }

    @Override
    public double evaluate(@NotNull ParameterSweepResult result) {
      final FeatureList fl = result.featureList();
      if (fl == null) {
        return Double.NaN;
      }
      final FeaturesDataTable dataTable = buildDataTable(fl);
      long matching = 0;
      for (int i = 0; i < fl.getNumberOfRows(); i++) {
        final double[] abundances = dataTable.getFeatureData(fl.getRow(i), false);
        if (MathUtils.calcRelativeStd(abundances) <= 0.2) {
          matching++;
        }
      }
      return matching;
    }
  }

  /**
   * Maximise: number of rows that (a) have an isotope pattern and (b) have RSD ≤ 20 %. Mirrors
   * {@link LcMsOptimizationProblem}'s {@code maximizeCv20WithIsos} objective.
   */
  record RowsWithIsosBelowCv20() implements SweepMetric {

    @Override
    public @NotNull String name() {
      return "Rows with isotopes below CV 20%";
    }

    @Override
    public boolean higherIsBetter() {
      return true;
    }

    @Override
    public double evaluate(@NotNull ParameterSweepResult result) {
      final FeatureList fl = result.featureList();
      if (fl == null) {
        return Double.NaN;
      }
      final FeaturesDataTable dataTable = buildDataTable(fl);
      long matching = 0;
      for (int i = 0; i < fl.getNumberOfRows(); i++) {
        if (fl.getRow(i).getBestIsotopePattern() == null
          /*|| fl.getRow(i).getNumberOfFeatures() != fl.getNumberOfRawDataFiles()*/) {
          continue;
        }
        final double[] abundances = dataTable.getFeatureData(fl.getRow(i), false);
        if (MathUtils.calcRelativeStd(abundances) <= 0.2) {
          matching++;
        }
      }
      return matching;
    }
  }

  /**
   * Maximise: number of features that carry an isotope pattern. Mirrors
   * {@link LcMsOptimizationProblem}'s {@code maximizeFeaturesWithIsos} objective.
   */
  record FeaturesWithIsotopes() implements SweepMetric {

    @Override
    public @NotNull String name() {
      return "Features with isotopes";
    }

    @Override
    public boolean higherIsBetter() {
      return true;
    }

    @Override
    public double evaluate(@NotNull ParameterSweepResult result) {
      final FeatureList fl = result.featureList();
      final double noise = MathUtils.calcQuantile(
          fl.streamFeatures(false).mapToDouble(Feature::getHeight).toArray(), 0.03);

      long score = 0;
      for (RawDataFile file : fl.getRawDataFiles()) {
        int numPeaks = 0;
        int numWithIsos = 0;
        for (FeatureListRow row : fl.getRows()) {
          Feature f = row.getFeature(file);
          if (f == null || f.getHeight() < noise) {
            continue;
          }
          numPeaks++;
          if (f.getIsotopePattern() != null) {
            numWithIsos++;
          }
        }
        score += ((long) numWithIsos * numWithIsos) / numPeaks;
      }

      return score;
    }
  }

  /**
   * Minimise: ratio of features classified as double-Gaussian peaks to total features. Mirrors
   * {@link LcMsOptimizationProblem}'s {@code minimizeDoublePeaks} objective.
   */
  record DoublePeakRatio() implements SweepMetric {

    @Override
    public @NotNull String name() {
      return "Double peak ratio";
    }

    @Override
    public boolean higherIsBetter() {
      return false;
    }

    @Override
    public double evaluate(@NotNull ParameterSweepResult result) {
      final FeatureList fl = result.featureList();
      if (fl == null) {
        return Double.NaN;
      }
      final long numFeatures = fl.streamFeatures().count();
      if (numFeatures == 0) {
        return 0;
      }
      final long numDoublePeaks = fl.streamFeatures(true)
          .map(f -> f.get(RtQualitySummaryType.class))
          .filter(s -> s != null && s.classification() == PeakShapeClassification.DOUBLE_GAUSSIAN)
          .count();
      return (double) numDoublePeaks / numFeatures;
    }
  }

  /**
   * Maximise: ratio of detected features to the theoretical maximum (rows × files). Mirrors
   * {@link LcMsOptimizationProblem}'s {@code maximizeFillRatio} objective.
   */
  record FillRatio() implements SweepMetric {

    @Override
    public @NotNull String name() {
      return "Fill ratio";
    }

    @Override
    public boolean higherIsBetter() {
      return true;
    }

    @Override
    public double evaluate(@NotNull ParameterSweepResult result) {
      final FeatureList fl = result.featureList();
      if (fl == null) {
        return Double.NaN;
      }
      final int maxFeatures = fl.getNumberOfRows() * fl.getNumberOfRawDataFiles();
      if (maxFeatures == 0) {
        return 0;
      }
      final long numFeatures = fl.streamFeatures().count();
      return (double) numFeatures / maxFeatures;
    }
  }

  /**
   * Maximise: number of benchmark target features found in the result. Mirrors
   * {@link LcMsOptimizationProblem}'s {@code maximizeNumBenchmark} objective.
   *
   * @param targets the list of expected benchmark features to match against
   */
  record BenchmarkTargetCount(@NotNull List<FeatureRecord> targets) implements SweepMetric {

    @Override
    public @NotNull String name() {
      return "Benchmark target count";
    }

    @Override
    public boolean higherIsBetter() {
      return true;
    }

    @Override
    public double evaluate(@NotNull ParameterSweepResult result) {
      final FeatureList fl = result.featureList();
      if (fl == null) {
        return Double.NaN;
      }
      final List<FeatureListRow> rows = fl.getRowsCopy();
      rows.sort(Comparator.comparing(FeatureListRow::getAverageMZ));
      return targets.stream().parallel().filter(r -> r.isPresent(rows)).count();
    }
  }

  record SlawIntegrationScore() implements SweepMetric {

    @Override
    public @NotNull String name() {
      return "Slaw integration score";
    }

    @Override
    public boolean higherIsBetter() {
      return true;
    }

    @Override
    public double evaluate(@NotNull ParameterSweepResult result) {
      final FeatureList fl = result.featureList();
      if (fl == null) {
        return Double.NaN;
      }
      final FeaturesDataTable dataTable = buildDataTable(fl);
      // dont use rsd filter here, because we just use all data files here. they may not be of a specific sample type.
      long matchingRows = 0;
      for (int i = 0; i < fl.getNumberOfRows(); i++) {
        if (fl.getRow(i).getNumberOfFeatures() != fl.getNumberOfRawDataFiles()) {
          continue;
        }
        final double[] abundances = dataTable.getFeatureData(fl.getRow(i), false);
        final double rsd = MathUtils.calcRelativeStd(abundances);
        if (rsd <= 0.2) {
          matchingRows++;
        }
      }

      return (double) (matchingRows * matchingRows) / fl.getNumberOfRows();
    }
  }

  /**
   * Maximise: harmonic mean of the {@link SlawIntegrationScore} and {@link FeaturesWithIsotopes}
   * scores. The harmonic mean rewards solutions that perform well on <em>both</em> components
   * simultaneously and penalises extreme imbalances between the two.
   * <p>
   * {@link #evaluate} computes the raw harmonic mean (used by the MOEA optimizer). For the
   * parameter sweep, use {@link #computeNormalizedScores} instead, which min-max normalises each
   * component to [0, 1] across all results before combining them.
   * <p>
   * Returns {@link Double#NaN} when the feature list is {@code null} or when either component score
   * is NaN. Returns 0 when both components are 0.
   */
  record HarmonicSlawIsotopes() implements SweepMetric {

    @Override
    public @NotNull String name() {
      return "Harmonic slaw-isotopes";
    }

    @Override
    public boolean higherIsBetter() {
      return true;
    }

    /**
     * Raw harmonic mean of the two component scores (used by the MOEA optimizer). For the parameter
     * sweep, prefer {@link #computeNormalizedScores} instead.
     */
    @Override
    public double evaluate(@NotNull ParameterSweepResult result) {
      final double slawScore = CV20_ISO_ROWS_RATIO.evaluate(result);
      final double isoScore = FEATURES_WITH_ISOTOPES.evaluate(result);
      if (Double.isNaN(slawScore) || Double.isNaN(isoScore)) {
        return Double.NaN;
      }
      final double sum = slawScore + isoScore;
      return sum == 0.0 ? 0.0 : 2.0 * slawScore * isoScore / sum;
    }

    /**
     * Computes normalised harmonic mean scores across all results. Each component ({@code slaw} and
     * {@code iso}) is normalised to [0, 1] by dividing by the maximum value across all results
     * before the harmonic mean is applied. This avoids the combined score being dominated by
     * whichever component has larger raw values, while keeping scores proportional (the score is 0
     * only when the raw component value is 0, not merely when it is the minimum in the population).
     * <p>
     * NaN entries in either input propagate to NaN in the output. When the maximum of a component
     * is 0 (all values are 0) the normalised value is treated as 1 for every result.
     *
     * @param slawScores pre-computed {@link SlawIntegrationScore} values, one per result
     * @param isoScores  pre-computed {@link FeaturesWithIsotopes} values, one per result
     * @return array of length {@code slawScores.length} with the normalised harmonic scores
     */
    public static double @NotNull [] computeNormalizedScores(double @NotNull [] slawScores,
        double @NotNull [] isoScores) {
      double slawMax = 0;
      double isoMax = 0;
      for (int i = 0; i < slawScores.length; i++) {
        if (!Double.isNaN(slawScores[i])) {
          slawMax = Math.max(slawMax, slawScores[i]);
        }
        if (!Double.isNaN(isoScores[i])) {
          isoMax = Math.max(isoMax, isoScores[i]);
        }
      }

      final double[] result = new double[slawScores.length];
      for (int i = 0; i < slawScores.length; i++) {
        if (Double.isNaN(slawScores[i]) || Double.isNaN(isoScores[i])) {
          result[i] = Double.NaN;
          continue;
        }
        final double normSlaw = slawMax > 0 ? slawScores[i] / slawMax : 1.0;
        final double normIso = isoMax > 0 ? isoScores[i] / isoMax : 1.0;
        final double sum = normSlaw + normIso;
        result[i] = sum == 0.0 ? 0.0 : 2.0 * normSlaw * normIso / sum;
      }
      return result;
    }
  }
}
