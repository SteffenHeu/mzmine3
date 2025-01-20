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

import io.github.mzmine.modules.tools.auto_parameters.AutoFeatureStatistics.ResultType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AutoParametersResultCollection {

  private final List<AutoFeatureResult> chromCollection;
  private final Map<AutoFeatureResult, AutoFeatureStatistics> featureStatisticsMap = new LinkedHashMap<>();

  private final List<AutoFeatureStatistics> validResults;

  public AutoParametersResultCollection(List<AutoFeatureResult> chromCollection) {
    this.chromCollection = chromCollection;
    for (AutoFeatureResult autoFeatureResult : chromCollection) {
      featureStatisticsMap.computeIfAbsent(autoFeatureResult, AutoFeatureStatistics::new);
    }

    validResults = featureStatisticsMap.values().stream()
        .filter(s -> s.getResultType() == ResultType.VALID).toList();

    /*for (AutoFeatureStatistics statistic : featureStatisticsMap.values()) {
      if(statistic.getResultType() == ResultType.INVALID) {
        continue;
      }
    }*/
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    var that = (AutoParametersResultCollection) obj;
    return Objects.equals(this.chromCollection, that.chromCollection);
  }

  public List<AutoFeatureResult> getChromCollection() {
    return chromCollection;
  }

  public Map<AutoFeatureResult, AutoFeatureStatistics> getFeatureStatisticsMap() {
    return featureStatisticsMap;
  }

  public List<AutoFeatureStatistics> getValidResults() {
    return validResults;
  }

  @Override
  public int hashCode() {
    return Objects.hash(chromCollection);
  }

  @Override
  public String toString() {
    return "AutoParametersResultCollection[" + "chromCollection=" + chromCollection + ']';
  }


}
