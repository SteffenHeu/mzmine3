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

package io.github.mzmine.modules.tools.tools_autoparam.optimizer.gui;

import io.github.mzmine.javafx.components.factories.TableColumns;
import io.github.mzmine.javafx.components.factories.TableColumns.ColumnAlignment;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.util.color.SimpleColorPalette;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.scene.control.TableColumn;
import javafx.scene.paint.Color;
import org.moeaframework.core.Solution;
import org.moeaframework.core.objective.Objective;
import org.moeaframework.core.population.NondominatedPopulation;

public record ObjectiveWrapper(String name, int index, Color color) {

  public static List<ObjectiveWrapper> extract(NondominatedPopulation result) {
    final Solution solution = result.get(0);
    final List<ObjectiveWrapper> objectives = new ArrayList<>();
    for (int i = 0; i < solution.getNumberOfObjectives(); i++) {
      Objective obj = solution.getObjective(i);

      final SimpleColorPalette palette = ConfigService.getDefaultColorPalette();
      final Color color = obj.getName().equals("Double peak ratio") ? palette.getNegativeColor()
          : palette.getPositiveColor();
      objectives.add(new ObjectiveWrapper(solution.getObjective(i).getName(), i, color));
    }
    return objectives;
  }

  public TableColumn<Solution, Number> createColumn() {
    final TableColumn<Solution, Number> column = TableColumns.createColumn(name(), 140,
        new DecimalFormat("0.###"), ColumnAlignment.RIGHT,
        s -> new ReadOnlyDoubleWrapper(s.getObjectiveValue(index())));

    column.setCellFactory(_ -> new BarTableCell(color, new DecimalFormat("0.###")));

    return column;
  }
}
