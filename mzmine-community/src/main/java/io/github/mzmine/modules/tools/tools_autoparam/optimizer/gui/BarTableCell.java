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

import io.github.mzmine.javafx.components.factories.FxLabels;
import io.github.mzmine.javafx.components.factories.TableColumns;
import io.github.mzmine.javafx.components.util.FxLayout;
import io.github.mzmine.javafx.util.FxFileChooser;
import java.text.NumberFormat;
import java.util.Objects;
import java.util.logging.Logger;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.layout.Background;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Callback;
import org.jmol.c.HB;
import org.moeaframework.core.Solution;

public class BarTableCell extends TableCell<Solution, Number> {

  private final NumberFormat formatter;
  private final Label label;
  private final Rectangle rect;
  private final DoubleProperty widthFraction = new SimpleDoubleProperty(0d);

  private static final Logger logger = Logger.getLogger(BarTableCell.class.getName());

  public BarTableCell(Color color, NumberFormat formatter) {
    this.formatter = formatter;
    label = new Label();
    rect = new Rectangle();
    rect.setFill(new Color(color.getRed(), color.getGreen(), color.getBlue(), 0.5));
    rect.setHeight(16);
    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

    final StackPane stackPane = new StackPane();
    final FlowPane labelAlignment = FxLayout.newFlowPane(Pos.CENTER_RIGHT, Insets.EMPTY, label);
    final FlowPane rectAlignment = FxLayout.newFlowPane(Pos.CENTER_LEFT, Insets.EMPTY, rect);

    stackPane.getChildren().addAll(rectAlignment, labelAlignment);

    final BorderPane content = new BorderPane(stackPane);
    setGraphic(content);
    setMinWidth(USE_PREF_SIZE);

    rect.widthProperty().bind(widthFraction.multiply(100));

    itemProperty().subscribe(item -> {
      if (item == null) {
        setGraphic(null);
        return;
      }
      setGraphic(content);
      label.setText(formatter.format(item));

      final ObservableList<Solution> items = getTableColumn().getTableView().getItems();

      double max = 0d;
      for (int i = 0; i < items.size(); i++) {
        max = Math.max(
            Objects.requireNonNullElse(getTableColumn().getCellData(i), 0d).doubleValue(), max);
      }
      final double value = item.doubleValue();
      widthFraction.set(value / max);
      if(widthFraction.get() > 1) {
        logger.info("Width fraction is greater than 1");
      }
    });
  }
}
