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

import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.gui.chartbasics.simplechart.SimpleXYChart;
import io.github.mzmine.gui.chartbasics.simplechart.providers.PlotXYDataProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.impl.AnyXYProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.impl.series.IonTimeSeriesToXYProvider;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.util.MathUtils;
import java.awt.BasicStroke;
import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.List;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;
import org.jfree.chart.plot.ValueMarker;

public class AutoParametersPane extends BorderPane {

  private final AutoParametersResultCollection result;
  private final SimpleXYChart<PlotXYDataProvider> chromatogramsChart = new SimpleXYChart<>(
      "Chromatograms", "Retention time / min", "Intensity / a.u.");
  private final SimpleXYChart<AnyXYProvider> scatter = new SimpleXYChart<>("m/z tolerance scatter");

  private final ComboBox<AutoFeatureResult> chromCombo = new ComboBox<>();
  private final ComboBox<IsotopeCollection> toleranceCombo = new ComboBox<>();

  private final DecimalFormat format = new DecimalFormat("0.00000");

  public AutoParametersPane(final AutoParametersResultCollection result) {
    this.result = result;
    initialiseComboBoxes(result);
    initResultScatterPlot(result);

    final FlowPane comboPane = new FlowPane(chromCombo, toleranceCombo);
    var content = new SplitPane(chromatogramsChart, scatter);
    content.setDividerPosition(0, 0.5);
    setCenter(content);
    setBottom(comboPane);
    setRight(scatter);
  }

  private void initResultScatterPlot(AutoParametersResultCollection result) {
    final List<AutoFeatureStatistics> validResults = result.getValidResults();
    final AnyXYProvider dataset = new AnyXYProvider(java.awt.Color.BLACK,
        "m/z tolerance scattering", validResults.size(), i -> validResults.get(i).getAf().getMz(),
        i -> validResults.get(i).getBestTolerance());
    scatter.setDomainAxisLabel("m/z");
    scatter.setRangeAxisLabel("m/z tolerance (abs)");
    final double[] bestTolerances = validResults.stream()
        .mapToDouble(AutoFeatureStatistics::getBestTolerance).toArray();
    final double averageTolerance = MathUtils.calcAvg(bestTolerances);
    final double std = MathUtils.calcStd(bestTolerances);
    scatter.addDataset(dataset);
    final BasicStroke stroke = new BasicStroke(1.0f);
    scatter.getXYPlot()
        .addRangeMarker(new ValueMarker(averageTolerance, java.awt.Color.BLUE, stroke));
    scatter.getXYPlot().addRangeMarker(
        new ValueMarker(averageTolerance + std * 1, java.awt.Color.magenta, stroke));
    scatter.getXYPlot().addRangeMarker(
        new ValueMarker(averageTolerance + std * 2, java.awt.Color.magenta, stroke));
    scatter.getXYPlot().addRangeMarker(
        new ValueMarker(averageTolerance + std * 3, java.awt.Color.magenta, stroke));
  }

  private void initialiseComboBoxes(AutoParametersResultCollection result) {
    chromCombo.setItems(FXCollections.observableArrayList(result.getChromCollection()));
    chromCombo.getSelectionModel().selectedItemProperty().addListener(((obs, o, n) -> {
      if (n == null) {
        toleranceCombo.setItems(FXCollections.observableArrayList());
      }

      final Double currentTol =
          toleranceCombo.getValue() != null ? toleranceCombo.getValue().getAbsTolerance() : null;

      toleranceCombo.setItems(
          FXCollections.observableArrayList(n.getRangeResolvedFeatureMap().values().stream()
              .sorted(Comparator.comparingDouble(IsotopeCollection::getAbsTolerance)).toList()));

      if (currentTol != null) {
        toleranceCombo.setValue(toleranceCombo.getItems().stream()
            .filter(col -> Double.compare(col.getAbsTolerance(), currentTol) == 0).findFirst()
            .orElse(toleranceCombo.getItems().get(0)));
      } else {
        toleranceCombo.setValue(toleranceCombo.getItems().get(0));
      }
    }));

    toleranceCombo.setConverter(new StringConverter<>() {
      @Override
      public String toString(IsotopeCollection object) {
        if (object == null) {
          return "";
        }
        return "%.5f (%d)".formatted(object.getAbsTolerance(), object.getIsotopeFeatures().size());
      }

      @Override
      public IsotopeCollection fromString(String string) {
        return null;
      }
    });

    toleranceCombo.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
      selectedChromChanged(n);
    });
  }

  private void selectedChromChanged(final IsotopeCollection iso) {
    chromatogramsChart.removeAllDatasets();

    if (iso == null) {
      return;
    }

    chromatogramsChart.applyWithNotifyChanges(false, () -> {
      final SimpleObjectProperty<Color> color = new SimpleObjectProperty<>(
          MZmineCore.getConfiguration().getDefaultColorPalette().getNextColor());
      for (Feature isotopeFeature : iso.getIsotopeFeatures()) {
        final PlotXYDataProvider data = new IonTimeSeriesToXYProvider(
            isotopeFeature.getFeatureData(),
            "%.4f +- %.5f".formatted(isotopeFeature.getMZ(), iso.getAbsTolerance()), color);
        chromatogramsChart.addDataset(data);
      }
    });
  }
}
