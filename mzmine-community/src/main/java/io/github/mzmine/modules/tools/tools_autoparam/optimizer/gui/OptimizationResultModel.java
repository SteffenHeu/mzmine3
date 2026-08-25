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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.moeaframework.core.Solution;
import org.moeaframework.core.population.NondominatedPopulation;

public class OptimizationResultModel {

  private final ObjectProperty<@Nullable NondominatedPopulation> result = new SimpleObjectProperty<>();
  private final ObjectProperty<@Nullable Solution> selectedSolution = new SimpleObjectProperty<>();
  private final ObjectProperty<@Nullable Solution> singlePassSolution = new SimpleObjectProperty<>();

  /**
   * Every solution shown in the results table: the raw data estimate first, followed by the
   * optimizer result.
   * <p>
   * decision: a single observable list that is only ever updated via setAll, so bindings and
   * listeners registered on it are not discarded.
   */
  private final ObservableList<Solution> displayedSolutions = FXCollections.observableArrayList();

  @Nullable
  public NondominatedPopulation getResult() {
    return result.get();
  }

  public ObjectProperty<@Nullable NondominatedPopulation> resultProperty() {
    return result;
  }

  public @Nullable Solution getSelectedSolution() {
    return selectedSolution.get();
  }

  public ObjectProperty<@Nullable Solution> selectedSolutionProperty() {
    return selectedSolution;
  }

  /**
   * The single-pass raw data estimate. It is always evaluated, also when it was not used to
   * warm-start the optimizer, so it can be compared against the optimized solutions.
   */
  public @Nullable Solution getSinglePassSolution() {
    return singlePassSolution.get();
  }

  public ObjectProperty<@Nullable Solution> singlePassSolutionProperty() {
    return singlePassSolution;
  }

  public @NotNull ObservableList<Solution> getDisplayedSolutions() {
    return displayedSolutions;
  }
}
