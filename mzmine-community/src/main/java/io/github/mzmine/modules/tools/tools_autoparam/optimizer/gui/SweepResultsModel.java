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

package io.github.mzmine.modules.tools.tools_autoparam.optimizer.gui;

import io.github.mzmine.modules.tools.tools_autoparam.optimizer.SweepMetricResult;
import java.util.List;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public class SweepResultsModel {

  private final ObjectProperty<List<SweepMetricResult>> results = new SimpleObjectProperty<>(
      List.of());
  private final ObjectProperty<SweepMetricResult> selectedResult = new SimpleObjectProperty<>();

  public ObjectProperty<List<SweepMetricResult>> resultsProperty() {
    return results;
  }

  public List<SweepMetricResult> getResults() {
    return results.get();
  }

  public void setResults(List<SweepMetricResult> value) {
    results.set(value);
  }

  public ObjectProperty<SweepMetricResult> selectedResultProperty() {
    return selectedResult;
  }

  public SweepMetricResult getSelectedResult() {
    return selectedResult.get();
  }

  public void setSelectedResult(SweepMetricResult value) {
    selectedResult.set(value);
  }
}
