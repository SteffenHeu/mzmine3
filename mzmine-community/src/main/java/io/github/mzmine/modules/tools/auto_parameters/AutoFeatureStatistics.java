/*
 * Copyright (c) 2004-2023 The MZmine Development Team
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

package io.github.mzmine.modules.tools.auto_parameters;

import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.features.Feature;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

public class AutoFeatureStatistics {

  private static final Logger logger = Logger.getLogger(AutoFeatureStatistics.class.getName());

  private final AutoFeatureResult af;

  private final double bestTolerance;

  private ResultType resultType = ResultType.VALID;

  public AutoFeatureStatistics(AutoFeatureResult af) {
    this.af = af;

    final Map<Double, IsotopeCollection> resolvedFeatures = af.getRangeResolvedFeatureMap();
    final List<IsotopeCollection> descendingTolerance = resolvedFeatures.values().stream()
        .sorted(Comparator.comparingDouble(IsotopeCollection::getAbsTolerance).reversed()).toList();

    int maxDps = 0;
    Double smallestTolWithNoZeros = null;

    for (IsotopeCollection isotopeCollection : descendingTolerance) {
      final List<Feature> isotopeFeatures = isotopeCollection.getIsotopeFeatures();
      maxDps = Math.max(isotopeCollection.getIsotopeFeature(0).getNumberOfDataPoints(), maxDps);

      final IonTimeSeries<? extends Scan> chrom = isotopeCollection.getIsotopeFeature(0)
          .getFeatureData();
      if (!hasZeroIntensity(chrom)) {
        smallestTolWithNoZeros = Math.min(isotopeCollection.getAbsTolerance(),
            Objects.requireNonNullElse(smallestTolWithNoZeros, 1d));
      }
    }

    if (smallestTolWithNoZeros == null) {
      resultType = ResultType.INVALID;
      bestTolerance = 0d;
      return;
    }

    var resultA = findBestToleranceA(descendingTolerance);
    var resultB = findBestToleranceB(descendingTolerance);

    if (resultA == null || resultB == null) {
      resultType = ResultType.INVALID;
      bestTolerance = 0d;
      logger.info("Invalid result for m/z %.4f".formatted(af.getMz()));
      return;
    }

    if (resultA == resultB) {
      logger.info(
          "Best tolerance for m/z %.4f matches at %.5f m/z or %.2f ppm.".formatted(af.getMz(),
              resultA.getAbsTolerance(), resultA.getAbsTolerance() / af.getMz() * 1E6));
      bestTolerance = resultA.getAbsTolerance();
    } else {
      logger.info(
          "Best tolerance for m/z %.4f did not match. From lowest: at %.5f m/z or %.2f ppm.\tLowest most frequent: at %.5f m/z or %.2f ppm".formatted(
              af.getMz(), resultA.getAbsTolerance(), resultA.getAbsTolerance() / af.getMz() * 1E6,
              resultB.getAbsTolerance(), resultB.getAbsTolerance() / af.getMz() * 1E6));
      bestTolerance = Math.max(resultA.getAbsTolerance(), resultB.getAbsTolerance());
    }
  }

  private static boolean hasZeroIntensity(IonTimeSeries<? extends Scan> chrom) {
    for (int i = Math.min(1, chrom.getNumberOfValues());
        i < Math.max(0, chrom.getNumberOfValues() - 1); i++) {
      if (chrom.getIntensity(i) == 0d) {
        return true;
      }
    }
    return false;
  }

  /**
   * Finds the best fitting tolerance by finding the first feature with the maximum number of
   * isotopes in a collection.
   */
  @Nullable
  private static IsotopeCollection findBestToleranceA(List<IsotopeCollection> isoCol) {
    final List<IsotopeCollection> ascendingTol = isoCol.stream()
        .sorted(Comparator.comparingDouble(IsotopeCollection::getAbsTolerance)).toList();

    int maxIsotopes = ascendingTol.stream().mapToInt(col -> col.getIsotopeFeatures().size()).max()
        .orElse(0);
    if (maxIsotopes == 0) {
      return null;
    }

    for (IsotopeCollection col : ascendingTol) {
      if (col.getIsotopeFeatures().size() == maxIsotopes) {
        return col;
      }
    }
    return null;
  }

  private static IsotopeCollection findBestToleranceB(List<IsotopeCollection> isoCol) {
    final List<IsotopeCollection> ascendingTol = isoCol.stream()
        .sorted(Comparator.comparingDouble(IsotopeCollection::getAbsTolerance)).toList();

    // count the occurring number of isotopes and group by number of isotopes.
    final Map<Integer, List<IsotopeCollection>> isotopeNumberMap = ascendingTol.stream()
        .collect(Collectors.groupingBy(iso -> iso.getIsotopeFeatures().size()));

    // sort the collections by their size. the most frequently occurring number of isotopes will be the last list.
    final List<Entry<Integer, List<IsotopeCollection>>> entryList = isotopeNumberMap.entrySet()
        .stream().sorted(Comparator.comparingInt(Entry::getKey)).toList();
    if (entryList.isEmpty() || entryList.get(entryList.size() - 1).getKey() == 1 || entryList.get(
        entryList.size() - 1).getValue().isEmpty()) {
      return null;
    }

    // list with most frequently occurring number of isotopes
    List<IsotopeCollection> collectionsWithMostFrequentIsotopeNumber = entryList.get(
        entryList.size() - 1).getValue();

    // find the smallest tolerance in the list
    return collectionsWithMostFrequentIsotopeNumber.stream()
        .sorted(Comparator.comparingDouble(IsotopeCollection::getAbsTolerance)).findFirst().get();
  }

  public double getBestTolerance() {
    return bestTolerance;
  }

  public AutoFeatureResult getAf() {
    return af;
  }

  public ResultType getResultType() {
    return resultType;
  }

  public enum ResultType {
    INVALID, VALID;
  }
}
