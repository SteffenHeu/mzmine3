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

package io.github.mzmine.modules.visualization.dash_lipidqc.isotope;

import io.github.mzmine.datamodel.IsotopePattern;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.identities.iontype.IonType;
import io.github.mzmine.datamodel.identities.iontype.IonTypeParser;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYDataset;
import io.github.mzmine.gui.chartbasics.simplechart.providers.impl.spectra.MassSpectrumProvider;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYBarRenderer;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.modules.tools.isotopeprediction.IsotopePatternCalculator;
import io.github.mzmine.modules.visualization.spectra.simplespectra.SpectraPlot;
import io.github.mzmine.modules.visualization.spectra.simplespectra.renderers.SpectraItemLabelGenerator;
import java.awt.Color;
import java.awt.geom.Ellipse2D;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.openscience.cdk.interfaces.IMolecularFormula;

public class IsotopePane extends BorderPane {

  private static final double APPROX_INSTRUMENT_RESOLUTION = 100_000d;
  private static final double MIN_THEORETICAL_ABUNDANCE = 0.005d;
  private static final double MIN_MERGE_WIDTH = 0.00005d;

  private final @NotNull SpectraPlot plot = new SpectraPlot();
  private final @NotNull Label placeholder = new Label("Select a row with an isotope pattern.");

  public IsotopePane() {
    setCenter(placeholder);
    BorderPane.setAlignment(placeholder, Pos.CENTER);

    plot.getXYPlot().getDomainAxis().setLabel("m/z");
    ((NumberAxis) plot.getXYPlot().getDomainAxis()).setNumberFormatOverride(
        ConfigService.getGuiFormats().mzFormat());
    plot.getXYPlot().getRangeAxis().setLabel("Intensity");
    ((NumberAxis) plot.getXYPlot().getRangeAxis()).setNumberFormatOverride(
        ConfigService.getGuiFormats().intensityFormat());
    plot.setMinSize(250, 200);
  }

  public void setFeatureList(final @Nullable ModularFeatureList featureList) {
  }

  public void setRow(final @Nullable FeatureListRow row) {
    plot.applyWithNotifyChanges(false, plot::removeAllDataSets);
    if (row == null) {
      showPlaceholder("Select a row with an isotope pattern.");
      return;
    }

    final IsotopePattern pattern = row.getBestIsotopePattern();
    if (pattern == null) {
      showPlaceholder("No isotope pattern available for selected row.");
      return;
    }

    final ColoredXYBarRenderer measuredRenderer = new ColoredXYBarRenderer(false);
    measuredRenderer.setDefaultItemLabelGenerator(new SpectraItemLabelGenerator(plot));
    plot.addDataSet(new ColoredXYDataset(new MassSpectrumProvider(pattern, "Isotope pattern",
            ConfigService.getDefaultColorPalette().getAWT(0))), Color.black, false,
        measuredRenderer,
        false, false);

    final MatchedLipid selectedMatch =
        row.getLipidMatches().isEmpty() ? null : row.getLipidMatches().getFirst();
    final IsotopePattern theoreticalPattern = resolveTheoreticalPattern(row, selectedMatch);
    if (theoreticalPattern != null) {
      final XYLineAndShapeRenderer theoreticalRenderer = new XYLineAndShapeRenderer(false, true);
      theoreticalRenderer.setSeriesPaint(0, new Color(220, 40, 40));
      theoreticalRenderer.setSeriesShape(0, new Ellipse2D.Double(-4, -4, 8, 8));
      theoreticalRenderer.setSeriesStroke(0, new java.awt.BasicStroke(1.8f));
      theoreticalRenderer.setSeriesVisibleInLegend(0, true);
      plot.addDataSet(new ColoredXYDataset(
          new MassSpectrumProvider(theoreticalPattern, "Theoretical isotope pattern",
              new Color(220, 40, 40))), Color.RED, false, theoreticalRenderer, false, false);
    }
    setCenter(plot);
  }

  private static @Nullable IsotopePattern resolveTheoreticalPattern(final @NotNull FeatureListRow row,
      final @Nullable MatchedLipid selectedMatch) {
    final List<MatchedLipid> matches = row.getLipidMatches();
    final MatchedLipid target =
        selectedMatch != null ? selectedMatch : (matches.isEmpty() ? null : matches.getFirst());
    if (target == null) {
      return null;
    }
    IonType adductType = target.getAdductType();
    if (adductType == null) {
      adductType = IonTypeParser.parse(target.getIonizationType().getAdductName());
    }
    IsotopePattern pattern = null;
    final IMolecularFormula neutralFormula = target.getLipidAnnotation().getMolecularFormula();
    if (neutralFormula != null && adductType != null) {
      try {
        final IMolecularFormula ionFormula = adductType.addToFormula(neutralFormula);
        final double referenceMz =
            target.getAccurateMz() != null ? target.getAccurateMz() : row.getAverageMZ();
        final double mergeWidth = Math.max(MIN_MERGE_WIDTH,
            referenceMz > 0d ? referenceMz / APPROX_INSTRUMENT_RESOLUTION : MIN_MERGE_WIDTH);
        pattern = IsotopePatternCalculator.calculateIsotopePattern(ionFormula,
            MIN_THEORETICAL_ABUNDANCE, mergeWidth, adductType.getAbsCharge(),
            adductType.getPolarity(), false);
      } catch (CloneNotSupportedException ignored) {
        pattern = null;
      }
    }
    final IsotopePattern measured = row.getBestIsotopePattern();
    if (pattern != null && measured != null && measured.getBasePeakIntensity() != null
        && measured.getBasePeakIntensity() > 0d) {
      pattern = IsotopePatternCalculator.normalizeIsotopePattern(pattern,
          measured.getBasePeakIntensity());
    }
    return pattern;
  }

  private void showPlaceholder(final @NotNull String text) {
    placeholder.setText(text);
    setCenter(placeholder);
  }
}
