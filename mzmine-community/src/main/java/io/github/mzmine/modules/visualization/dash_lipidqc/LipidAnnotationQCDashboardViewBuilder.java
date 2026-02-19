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

package io.github.mzmine.modules.visualization.dash_lipidqc;

import io.github.mzmine.datamodel.IsotopePattern;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.types.annotations.LipidMatchListType;
import io.github.mzmine.datamodel.identities.iontype.IonType;
import io.github.mzmine.datamodel.identities.iontype.IonTypeParser;
import io.github.mzmine.gui.chartbasics.chartutils.ColoredBubbleDatasetRenderer;
import io.github.mzmine.gui.chartbasics.gui.javafx.EChartViewer;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYDataset;
import io.github.mzmine.gui.chartbasics.simplechart.providers.XYItemObjectProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.impl.spectra.MassSpectrumProvider;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYBarRenderer;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.MSMSLipidTools;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.molecular_species.MolecularSpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.species_level.SpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.ILipidAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.ILipidClass;
import io.github.mzmine.modules.tools.isotopeprediction.IsotopePatternCalculator;
import io.github.mzmine.modules.visualization.equivalentcarbonnumberplot.EquivalentCarbonNumberChart;
import io.github.mzmine.modules.visualization.equivalentcarbonnumberplot.EquivalentCarbonNumberChartPane;
import io.github.mzmine.modules.visualization.equivalentcarbonnumberplot.EquivalentCarbonNumberDataset;
import io.github.mzmine.modules.visualization.kendrickmassplot.KendrickMassPlotChart;
import io.github.mzmine.modules.visualization.kendrickmassplot.KendrickMassPlotParameters;
import io.github.mzmine.modules.visualization.kendrickmassplot.KendrickMassPlotXYZDataset;
import io.github.mzmine.modules.visualization.spectra.matchedlipid.LipidAnnotationMatchPane;
import io.github.mzmine.modules.visualization.spectra.simplespectra.SpectraPlot;
import io.github.mzmine.modules.visualization.spectra.simplespectra.renderers.SpectraItemLabelGenerator;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsSelection;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.FeatureTableFXUtil;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.entity.CategoryItemEntity;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.fx.interaction.ChartMouseEventFX;
import org.jfree.chart.fx.interaction.ChartMouseListenerFX;
import org.jfree.chart.labels.CategoryToolTipGenerator;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CrosshairState;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.renderer.xy.XYItemRendererState;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.openscience.cdk.interfaces.IMolecularFormula;

public class LipidAnnotationQCDashboardViewBuilder extends
    FxViewBuilder<LipidAnnotationQCDashboardModel> {

  protected LipidAnnotationQCDashboardViewBuilder(LipidAnnotationQCDashboardModel model) {
    super(model);
  }

  @Override
  public Region build() {
    final DashboardFilterState filterState = new DashboardFilterState();
    final BorderPane isotopePane = new IsotopePane();
    final BorderPane ecnPane = new EquivalentCarbonNumberPane(model);
    final KendrickPane kendrickPane = new KendrickPane(model, filterState);
    final LipidSummaryPane summaryPane = new LipidSummaryPane(filterState);
    final AnnotationQualityPane qualityPane = new AnnotationQualityPane(model);
    final LipidAnnotationMatchPane lipidMatchPane = new LipidAnnotationMatchPane(
        model.getPaneGroup());

    model.featureTableFxProperty().get().getSelectionModel().selectedItemProperty()
        .addListener((_, _, row) -> model.setRow(row == null ? null : row.getValue()));
    model.featureTableFxProperty().get().getFilteredRowItems().addListener(
        (javafx.collections.ListChangeListener<javafx.scene.control.TreeItem<io.github.mzmine.datamodel.features.ModularFeatureListRow>>) _ -> Platform.runLater(
            () -> selectFirstVisibleRow(model)));
    model.rowProperty().addListener((_, _, row) -> {
      if (row != null && model.getFeatureTableFx().getSelectedRow() != model.getRow()) {
        FeatureTableFXUtil.selectAndScrollTo(row, model.getFeatureTableFx());
      }
      ((IsotopePane) isotopePane).setRow(row);
      ((EquivalentCarbonNumberPane) ecnPane).setRow(row);
      kendrickPane.setRow(row);
      qualityPane.setRow(row);
    });

    model.featureListProperty().subscribe(flist -> {
      ((EquivalentCarbonNumberPane) ecnPane).setFeatureList(flist);
      ((IsotopePane) isotopePane).setFeatureList(flist);
      kendrickPane.setFeatureList(flist);
      summaryPane.setFeatureList(flist);
      qualityPane.setFeatureList(flist);
      Platform.runLater(() -> selectFirstVisibleRow(model));
    });

    filterState.onChange = () -> {
      final Set<Integer> rowIds = filterState.barSelectedRowIds;
      if (rowIds == null || rowIds.isEmpty()) {
        model.getFeatureTableController().getFilterModel().setIdFilter("");
      } else {
        model.getFeatureTableController().getFilterModel().setIdFilter(
            rowIds.stream().sorted().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(""));
      }
      kendrickPane.applyFilters();
      Platform.runLater(() -> selectFirstVisibleRow(model));
    };

    final GridPane dashboardGrid = new GridPane();
    dashboardGrid.setHgap(8);
    dashboardGrid.setVgap(8);
    for (int i = 0; i < 3; i++) {
      final ColumnConstraints col = new ColumnConstraints();
      col.setPercentWidth(33.333);
      col.setHgrow(Priority.ALWAYS);
      dashboardGrid.getColumnConstraints().add(col);
    }
    for (int i = 0; i < 2; i++) {
      final RowConstraints rowC = new RowConstraints();
      rowC.setPercentHeight(50);
      rowC.setVgrow(Priority.ALWAYS);
      dashboardGrid.getRowConstraints().add(rowC);
    }
    dashboardGrid.add(wrapInSection("Lipid annotation summary", summaryPane), 0, 0);
    dashboardGrid.add(wrapInSection("Lipid annotation quality", qualityPane), 1, 0);
    dashboardGrid.add(wrapInSection("Isotope pattern", isotopePane), 2, 0);
    dashboardGrid.add(wrapInSection("Lipid annotation matches", lipidMatchPane), 0, 1);
    dashboardGrid.add(wrapInSection("Equivalent carbon number", ecnPane), 1, 1);
    dashboardGrid.add(wrapInSection("Kendrick mass plot", kendrickPane), 2, 1);

    final SplitPane mainSplit = new SplitPane(dashboardGrid,
        model.getFeatureTableController().buildView());
    mainSplit.setOrientation(Orientation.VERTICAL);
    mainSplit.setDividerPositions(0.75);

    return mainSplit;
  }

  private static void selectFirstVisibleRow(@NotNull LipidAnnotationQCDashboardModel model) {
    final var table = model.getFeatureTableFx();
    final var filteredItems = table.getFilteredRowItems();
    if (filteredItems == null || filteredItems.isEmpty()) {
      model.setRow(null);
      return;
    }
    final FeatureListRow firstRow = filteredItems.get(0).getValue();
    if (firstRow != null && !Objects.equals(model.getRow(), firstRow)) {
      FeatureTableFXUtil.selectAndScrollTo(firstRow, table);
      model.setRow(firstRow);
    }
  }

  private static BorderPane wrapInSection(String title, Region content) {
    final BorderPane pane = new BorderPane(content);
    final Label titleLabel = new Label(title);
    titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 4 6 4 6;");
    pane.setTop(titleLabel);
    return pane;
  }

  private static boolean rowHasMatchedLipidSignals(@NotNull FeatureListRow row) {
    final List<MatchedLipid> matches = row.get(LipidMatchListType.class);
    return matches != null && !matches.isEmpty();
  }

  private static class IsotopePane extends BorderPane {

    private static final double APPROX_INSTRUMENT_RESOLUTION = 100_000d;
    private static final double MIN_THEORETICAL_ABUNDANCE = 0.005d;
    private static final double MIN_MERGE_WIDTH = 0.00005d;
    private final SpectraPlot plot = new SpectraPlot();
    private final Label placeholder = new Label("Select a row with an isotope pattern.");

    private IsotopePane() {
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

    void setFeatureList(@Nullable ModularFeatureList featureList) {
    }

    void setRow(@Nullable FeatureListRow row) {
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
              ConfigService.getDefaultColorPalette().getAWT(0))), Color.black, false, measuredRenderer,
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

    private static @Nullable IsotopePattern resolveTheoreticalPattern(@NotNull FeatureListRow row,
        @Nullable MatchedLipid selectedMatch) {
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

    private void showPlaceholder(String text) {
      placeholder.setText(text);
      setCenter(placeholder);
    }
  }

  private static class EquivalentCarbonNumberPane extends BorderPane {

    private final LipidAnnotationQCDashboardModel model;
    private final Label placeholder = new Label("Select a row with lipid annotations.");
    private List<FeatureListRow> rowsWithLipidIds = List.of();

    private EquivalentCarbonNumberPane(LipidAnnotationQCDashboardModel model) {
      this.model = model;
      setCenter(placeholder);
      BorderPane.setAlignment(placeholder, Pos.CENTER);
    }

    void setFeatureList(@NotNull ModularFeatureList featureList) {
      rowsWithLipidIds = featureList.getRows().stream()
          .filter(LipidAnnotationQCDashboardViewBuilder::rowHasMatchedLipidSignals)
          .map(r -> (FeatureListRow) r).toList();
    }

    void setRow(@Nullable FeatureListRow row) {
      if (row == null) {
        showPlaceholder("Select a row with lipid annotations.");
        return;
      }

      final List<MatchedLipid> selectedMatches = row.get(LipidMatchListType.class);
      if (selectedMatches == null || selectedMatches.isEmpty()) {
        showPlaceholder("No lipid annotations available for selected row.");
        return;
      }

      if (rowsWithLipidIds.isEmpty()) {
        showPlaceholder("No lipid annotations available in this feature list.");
        return;
      }

      final MatchedLipid selectedMatch = selectedMatches.getFirst();
      final ILipidClass selectedClass = selectedMatch.getLipidAnnotation().getLipidClass();
      final int dbe = extractDbe(selectedMatch.getLipidAnnotation());
      if (dbe < 0) {
        showPlaceholder("Cannot determine DBE for selected lipid annotation.");
        return;
      }

      final List<MatchedLipid> classAndDbeMatches = rowsWithLipidIds.stream()
          .map(r -> r.get(LipidMatchListType.class))
          .filter(matches -> matches != null && !matches.isEmpty()).map(List::getFirst)
          .filter(match -> match.getLipidAnnotation().getLipidClass().equals(selectedClass))
          .filter(match -> extractDbe(match.getLipidAnnotation()) == dbe).toList();

      if (classAndDbeMatches.size() < 3) {
        showPlaceholder(
            "Not enough lipids for ECN model (need at least 3 rows in same class/DBE group).");
        return;
      }

      final EquivalentCarbonNumberDataset dataset = new EquivalentCarbonNumberDataset(
          rowsWithLipidIds, rowsWithLipidIds.toArray(new FeatureListRow[0]), selectedClass, dbe);
      dataset.run();
      final EquivalentCarbonNumberChart chart = new EquivalentCarbonNumberChart("ECN model",
          "Retention time", "Number of carbons", dataset);
      chart.addChartMouseListener(new ChartMouseListenerFX() {
        @Override
        public void chartMouseClicked(ChartMouseEventFX event) {
          if (!(event.getEntity() instanceof XYItemEntity entity)
              || entity.getDataset() != dataset) {
            return;
          }
          final int item = entity.getItem();
          if (item < 0 || item >= dataset.getItemCount(0)) {
            return;
          }
          final MatchedLipid clickedLipid = dataset.getMatchedLipid(item);
          final FeatureListRow clickedRow = findRowForLipid(clickedLipid);
          if (clickedRow != null) {
            model.setRow(clickedRow);
            FeatureTableFXUtil.selectAndScrollTo(clickedRow, model.getFeatureTableFx());
          }
        }

        @Override
        public void chartMouseMoved(ChartMouseEventFX event) {
        }
      });
      highlightSelectedLipid(chart, row, selectedMatch);
      chart.setMinSize(250, 200);
      setCenter(new EquivalentCarbonNumberChartPane(chart, dbe, classAndDbeMatches));
    }

    private @Nullable FeatureListRow findRowForLipid(@NotNull MatchedLipid clickedLipid) {
      for (FeatureListRow candidate : rowsWithLipidIds) {
        final List<MatchedLipid> matches = candidate.getLipidMatches();
        if (!matches.isEmpty() && clickedLipid.equals(matches.getFirst())) {
          return candidate;
        }
      }
      return null;
    }

    private static void highlightSelectedLipid(EquivalentCarbonNumberChart chart,
        FeatureListRow row, MatchedLipid selectedMatch) {
      final int carbons = extractCarbons(selectedMatch.getLipidAnnotation());
      if (carbons < 0) {
        return;
      }

      final XYSeries highlightSeries = new XYSeries("Selected lipid");
      highlightSeries.add((double) row.getAverageRT(), (double) carbons);
      final XYSeriesCollection highlightDataset = new XYSeriesCollection();
      highlightDataset.addSeries(highlightSeries);

      final XYLineAndShapeRenderer highlightRenderer = new XYLineAndShapeRenderer(false, true);
      highlightRenderer.setSeriesPaint(0,
          ConfigService.getDefaultColorPalette().getPositiveColorAWT());
      highlightRenderer.setSeriesStroke(0, new java.awt.BasicStroke(2f));
      highlightRenderer.setSeriesShape(0, new Ellipse2D.Double(-5, -5, 10, 10));
      highlightRenderer.setSeriesVisibleInLegend(0, false);

      chart.getChart().getXYPlot().setDataset(2, highlightDataset);
      chart.getChart().getXYPlot().setRenderer(2, highlightRenderer);
    }

    private static int extractDbe(ILipidAnnotation lipidAnnotation) {
      if (lipidAnnotation instanceof MolecularSpeciesLevelAnnotation molecularAnnotation) {
        return MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
            molecularAnnotation.getAnnotation()).getValue();
      }
      if (lipidAnnotation instanceof SpeciesLevelAnnotation speciesAnnotation) {
        return MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
            speciesAnnotation.getAnnotation()).getValue();
      }
      return -1;
    }

    private static int extractCarbons(ILipidAnnotation lipidAnnotation) {
      if (lipidAnnotation instanceof MolecularSpeciesLevelAnnotation molecularAnnotation) {
        return MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
            molecularAnnotation.getAnnotation()).getKey();
      }
      if (lipidAnnotation instanceof SpeciesLevelAnnotation speciesAnnotation) {
        return MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
            speciesAnnotation.getAnnotation()).getKey();
      }
      return -1;
    }

    private void showPlaceholder(String text) {
      placeholder.setText(text);
      setCenter(placeholder);
    }
  }

  private static class KendrickPane extends BorderPane {

    private final LipidAnnotationQCDashboardModel model;
    private final Label placeholder = new Label("Select a feature list to build Kendrick plot.");
    private final DashboardFilterState filterState;
    private KendrickMassPlotXYZDataset currentDataset;
    private KendrickMassPlotChart currentChart;
    private @Nullable FeatureListRow selectedRow;
    private @Nullable XYTextAnnotation selectedAnnotation;
    private final FilteringBubbleRenderer renderer = new FilteringBubbleRenderer();

    private KendrickPane(LipidAnnotationQCDashboardModel model, DashboardFilterState filterState) {
      this.model = model;
      this.filterState = filterState;
      setCenter(placeholder);
      BorderPane.setAlignment(placeholder, Pos.CENTER);
    }

    void setRow(@Nullable FeatureListRow row) {
      selectedRow = row;
      updateSelectedHighlight();
    }

    void setFeatureList(@NotNull ModularFeatureList featureList) {
      currentChart = null;
      currentDataset = null;
      if (featureList.getNumberOfRows() == 0) {
        showPlaceholder("Feature list has no rows.");
        return;
      }

      showPlaceholder("Loading Kendrick mass plot...");

      final KendrickMassPlotParameters params = (KendrickMassPlotParameters) new KendrickMassPlotParameters().cloneParameterSet();
      params.setParameter(KendrickMassPlotParameters.featureList,
          new FeatureListsSelection(featureList));
      params.setParameter(KendrickMassPlotParameters.yAxisCustomKendrickMassBase, "CH2");

      final KendrickMassPlotXYZDataset dataset = new KendrickMassPlotXYZDataset(params, 1, 1);
      currentDataset = dataset;
      dataset.addTaskStatusListener((_, newStatus, _) -> {
        if (dataset != currentDataset) {
          return;
        }
        if (newStatus == TaskStatus.FINISHED) {
          Platform.runLater(() -> {
            final KendrickMassPlotChart chart = new KendrickMassPlotChart("Kendrick mass plot",
                "m/z", "Kendrick mass defect (CH2)", "Retention time", dataset);
            currentChart = chart;
            chart.addChartMouseListener(new ChartMouseListenerFX() {
              @Override
              public void chartMouseClicked(ChartMouseEventFX event) {
                if (!(event.getEntity() instanceof XYItemEntity entity)
                    || entity.getDataset() != dataset) {
                  return;
                }
                final int item = entity.getItem();
                final FeatureListRow clickedRow = dataset.getItemObject(item);
                if (clickedRow != null) {
                  model.setRow(clickedRow);
                  FeatureTableFXUtil.selectAndScrollTo(clickedRow, model.getFeatureTableFx());
                }
              }

              @Override
              public void chartMouseMoved(ChartMouseEventFX event) {
              }
            });
            installFilteringRenderer(chart);
            setCenter(chart);
            applyFilters();
            updateSelectedHighlight();
          });
        } else if (newStatus == TaskStatus.ERROR || newStatus == TaskStatus.CANCELED) {
          Platform.runLater(() -> showPlaceholder("Kendrick mass plot could not be created."));
        }
      });
    }

    private void updateSelectedHighlight() {
      if (currentChart == null || currentDataset == null) {
        return;
      }
      final XYPlot plot = currentChart.getChart().getXYPlot();
      if (selectedAnnotation != null) {
        plot.removeAnnotation(selectedAnnotation);
        selectedAnnotation = null;
      }
      plot.setDataset(1, null);
      plot.setRenderer(1, null);

      if (selectedRow == null) {
        return;
      }

      int selectedIndex = -1;
      for (int i = 0; i < currentDataset.getItemCount(0); i++) {
        if (selectedRow.equals(currentDataset.getItemObject(i))) {
          if (!rowVisible((FeatureListRow) currentDataset.getItemObject(i))) {
            break;
          }
          selectedIndex = i;
          break;
        }
      }
      if (selectedIndex < 0) {
        return;
      }

      final double x = currentDataset.getXValue(0, selectedIndex);
      final double y = currentDataset.getYValue(0, selectedIndex);

      final XYSeries selectedSeries = new XYSeries("Selected lipid");
      selectedSeries.add(x, y);
      final XYSeriesCollection selectedDataset = new XYSeriesCollection();
      selectedDataset.addSeries(selectedSeries);

      final XYLineAndShapeRenderer selectedRenderer = new XYLineAndShapeRenderer(false, true);
      final Color highlightColor = ConfigService.getDefaultColorPalette().getPositiveColorAWT();
      selectedRenderer.setSeriesPaint(0, highlightColor);
      selectedRenderer.setSeriesStroke(0, new java.awt.BasicStroke(2f));
      selectedRenderer.setSeriesShape(0, new Ellipse2D.Double(-6, -6, 12, 12));
      selectedRenderer.setSeriesVisibleInLegend(0, false);
      plot.setDataset(1, selectedDataset);
      plot.setRenderer(1, selectedRenderer);

      final String label = getSelectedLabel(selectedRow);
      selectedAnnotation = new XYTextAnnotation(label, x, y);
      selectedAnnotation.setPaint(
          ConfigService.getConfiguration().getDefaultChartTheme().getItemLabelPaint());
      selectedAnnotation.setFont(new Font("SansSerif", Font.BOLD, 11));
      selectedAnnotation.setTextAnchor(TextAnchor.BOTTOM_LEFT);
      plot.addAnnotation(selectedAnnotation);
      currentChart.fireChangeEvent();
    }

    void applyFilters() {
      if (currentChart == null) {
        return;
      }
      renderer.setRowFilter(this::rowVisible);
      if (currentChart.getChart().getXYPlot().getRenderer() != renderer) {
        currentChart.getChart().getXYPlot().setRenderer(renderer);
      }
      currentChart.getChart().fireChartChanged();
      updateSelectedHighlight();
    }

    private boolean rowVisible(@NotNull FeatureListRow row) {
      return filterState.barSelectedRowIds.isEmpty() || filterState.barSelectedRowIds.contains(
          row.getID());
    }

    private void installFilteringRenderer(@NotNull KendrickMassPlotChart chart) {
      if (!(chart.getChart().getXYPlot()
          .getRenderer() instanceof ColoredBubbleDatasetRenderer old)) {
        return;
      }
      renderer.setPaintScale(old.getPaintScale());
      renderer.setDefaultToolTipGenerator(old.getDefaultToolTipGenerator());
      renderer.setRowFilter(this::rowVisible);
      chart.getChart().getXYPlot().setRenderer(renderer);
    }

    private static String getSelectedLabel(FeatureListRow row) {
      final List<MatchedLipid> matches = row.get(LipidMatchListType.class);
      if (matches == null || matches.isEmpty()) {
        return "Row " + row.getID();
      }
      final String annotation = matches.getFirst().getLipidAnnotation().getAnnotation();
      return annotation.length() > 50 ? annotation.substring(0, 47) + "..." : annotation;
    }

    private void showPlaceholder(String text) {
      placeholder.setText(text);
      setCenter(placeholder);
    }
  }

  private static class LipidSummaryPane extends BorderPane {

    private static final Pattern SUBCLASS_TOKEN_PATTERN = Pattern.compile(
        "^([A-Za-z]+(?:\\s+[OP]-)?)");
    private final Label placeholder = new Label("Select a feature list with lipid annotations.");
    private final DashboardFilterState filterState;
    private final ComboBox<SummaryGroup> groupSelector = new ComboBox<>(
        FXCollections.observableArrayList(SummaryGroup.values()));
    private final Button clearFilterButton = new Button("Clear filter");
    private final Label totalCountLabel = new Label("0");
    private final Label totalCountTitle = new Label("Total lipids");
    private @Nullable ModularFeatureList featureList;
    private @Nullable String selectedGroup;
    private final Map<String, Set<Integer>> groupToRowIds = new TreeMap<>();

    private LipidSummaryPane(DashboardFilterState filterState) {
      this.filterState = filterState;
      groupSelector.getSelectionModel().select(SummaryGroup.LIPID_SUBCLASS);
      groupSelector.valueProperty().addListener((_, _, _) -> updateChart());
      clearFilterButton.setOnAction(_ -> {
        selectedGroup = null;
        filterState.barSelectedRowIds = Set.of();
        if (filterState.onChange != null) {
          filterState.onChange.run();
        }
        updateChart();
      });
      totalCountLabel.setStyle(
          "-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1f5fbf;");
      totalCountTitle.setStyle("-fx-font-size: 10px; -fx-text-fill: #5f6b7a;");
      final javafx.scene.layout.VBox totalBadge = new javafx.scene.layout.VBox(0, totalCountLabel,
          totalCountTitle);
      totalBadge.setAlignment(Pos.CENTER_RIGHT);
      totalBadge.setStyle("-fx-background-color: #e8f1ff; "
          + "-fx-padding: 6 10 6 10; -fx-border-color: #9fbef2; -fx-border-radius: 6; "
          + "-fx-background-radius: 6;");
      final Region spacer = new Region();
      HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
      final HBox controls = new HBox(6, new Label("Group by:"), groupSelector, clearFilterButton,
          spacer, totalBadge);
      controls.setAlignment(Pos.CENTER_LEFT);
      setTop(controls);
      showPlaceholder("Select a feature list with lipid annotations.");
    }

    void setFeatureList(@NotNull ModularFeatureList featureList) {
      this.featureList = featureList;
      updateChart();
    }

    private void updateChart() {
      if (featureList == null) {
        showPlaceholder("Select a feature list with lipid annotations.");
        return;
      }

      final List<MatchedLipid> bestMatches = featureList.getRows().stream()
          .map(FeatureListRow::getLipidMatches).filter(matches -> !matches.isEmpty())
          .map(List::getFirst).toList();
      if (bestMatches.isEmpty()) {
        showPlaceholder("No lipid annotations available in this feature list.");
        return;
      }

      final SummaryGroup grouping = groupSelector.getValue();
      final Map<String, Set<String>> groupToSpecies = new TreeMap<>();
      final Map<String, String> groupTooltip = new TreeMap<>();
      groupToRowIds.clear();
      int totalLipidRows = 0;
      for (FeatureListRow row : featureList.getRows()) {
        final List<MatchedLipid> matches = row.getLipidMatches();
        if (matches.isEmpty()) {
          continue;
        }
        totalLipidRows++;
        final MatchedLipid lipid = matches.getFirst();
        final String groupName = grouping.extractGroupLabel(lipid, extractSubclassToken(lipid));
        final String species = lipid.getLipidAnnotation().getAnnotation();
        groupTooltip.putIfAbsent(groupName,
            grouping.extractTooltip(lipid, extractSubclassToken(lipid)));
        groupToSpecies.computeIfAbsent(groupName, _ -> new java.util.HashSet<>()).add(species);
        groupToRowIds.computeIfAbsent(groupName, _ -> new HashSet<>()).add(row.getID());
      }
      totalCountLabel.setText(String.valueOf(totalLipidRows));

      final DefaultCategoryDataset dataset = new DefaultCategoryDataset();
      groupToSpecies.forEach(
          (group, species) -> dataset.addValue(species.size(), "Lipid species", group));

      final JFreeChart chart = ChartFactory.createBarChart("Lipid annotation summary",
          grouping.getAxisLabel(), "Number of lipid species", dataset, PlotOrientation.VERTICAL,
          false, true, false);
      chart.getCategoryPlot().getDomainAxis()
          .setCategoryLabelPositions(CategoryLabelPositions.UP_45);
      final EChartViewer viewer = new EChartViewer(chart);
      final SelectableBarRenderer selectable = new SelectableBarRenderer(selectedGroup);
      selectable.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator());
      selectable.setDefaultItemLabelsVisible(true);
      selectable.setDefaultToolTipGenerator(
          (CategoryToolTipGenerator) (tipDataset, row, column) -> groupTooltip.getOrDefault(
              Objects.toString(tipDataset.getColumnKey(column), ""),
              Objects.toString(tipDataset.getColumnKey(column), "")));
      viewer.addChartMouseListener(new ChartMouseListenerFX() {
        @Override
        public void chartMouseClicked(ChartMouseEventFX event) {
          if (event.getEntity() instanceof CategoryItemEntity categoryEntity) {
            final String key = Objects.toString(categoryEntity.getColumnKey(), null);
            if (key != null && groupToRowIds.containsKey(key)) {
              selectedGroup = key.equals(selectedGroup) ? null : key;
              filterState.barSelectedRowIds =
                  selectedGroup == null ? Set.of() : Set.copyOf(groupToRowIds.get(selectedGroup));
              if (filterState.onChange != null) {
                filterState.onChange.run();
              }
              updateChart();
            }
          }
        }

        @Override
        public void chartMouseMoved(ChartMouseEventFX event) {
        }
      });
      MZmineCore.getConfiguration().getDefaultChartTheme().apply(viewer);
      chart.getCategoryPlot().setRenderer(selectable);
      setCenter(viewer);
    }

    private static String extractSubclassToken(@NotNull MatchedLipid lipid) {
      final String annotation = lipid.getLipidAnnotation().getAnnotation();
      final Matcher matcher = SUBCLASS_TOKEN_PATTERN.matcher(annotation);
      if (matcher.find()) {
        return matcher.group(1).trim();
      }
      return lipid.getLipidAnnotation().getLipidClass().getAbbr();
    }

    private void showPlaceholder(String text) {
      placeholder.setText(text);
      setCenter(placeholder);
    }

    private static final class SelectableBarRenderer extends BarRenderer {

      private final @Nullable String selectedGroup;
      private final Color selected = ConfigService.getDefaultColorPalette().getPositiveColorAWT();
      private final Color normal = new Color(120, 150, 190);

      private SelectableBarRenderer(@Nullable String selectedGroup) {
        this.selectedGroup = selectedGroup;
        setBarPainter(new StandardBarPainter());
        setShadowVisible(false);
      }

      @Override
      public Paint getItemPaint(int row, int column) {
        final var plot = getPlot();
        if (plot != null && plot.getDataset() != null) {
          final String key = Objects.toString(plot.getDataset().getColumnKey(column), null);
          if (selectedGroup != null && selectedGroup.equals(key)) {
            return selected;
          }
        }
        return normal;
      }
    }
  }

  private static class AnnotationQualityPane extends BorderPane {

    private final LipidAnnotationQCDashboardModel model;
    private final javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(8);
    private final javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane();
    private final Label placeholder = new Label("Select a row with lipid annotations.");
    private @Nullable FeatureListRow row;
    private @Nullable ModularFeatureList featureList;

    private AnnotationQualityPane(LipidAnnotationQCDashboardModel model) {
      this.model = model;
      scrollPane.setFitToWidth(true);
      scrollPane.setContent(content);
      setCenter(placeholder);
      BorderPane.setAlignment(placeholder, Pos.CENTER);
    }

    void setFeatureList(@Nullable ModularFeatureList featureList) {
      this.featureList = featureList;
      update();
    }

    void setRow(@Nullable FeatureListRow row) {
      this.row = row;
      update();
    }

    private void update() {
      if (row == null) {
        showPlaceholder("Select a row with lipid annotations.");
        return;
      }
      final List<MatchedLipid> matches = row.getLipidMatches();
      if (matches.isEmpty()) {
        showPlaceholder("No lipid annotations available for selected row.");
        return;
      }

      content.getChildren().clear();
      content.setStyle("-fx-padding: 8; " + qualityBackgroundStyle());
      final boolean crossClassInterference =
          matches.stream().map(m -> m.getLipidAnnotation().getLipidClass().getName()).distinct()
              .count() > 1;
      if (crossClassInterference) {
        final Label warning = new Label(
            "Potential interference: selected row has annotations from multiple lipid classes.");
        warning.setStyle(qualityWarningStyle());
        content.getChildren().add(warning);
      }

      for (MatchedLipid match : matches) {
        content.getChildren().add(createQualityCard(match, crossClassInterference));
      }
      setCenter(scrollPane);
    }

    private Region createQualityCard(MatchedLipid match, boolean crossClassInterference) {
      final javafx.scene.layout.VBox card = new javafx.scene.layout.VBox(6);
      card.setStyle(qualityCardStyle());

      final Label annotation = new Label(match.getLipidAnnotation().getAnnotation());
      annotation.setStyle("-fx-font-weight: bold;");

      final QualityMetric ms1 = evaluateMs1(match);
      final QualityMetric ms2 = evaluateMs2(match);
      final QualityMetric adduct = evaluateAdduct(match);
      final QualityMetric isotope = evaluateIsotope(match);
      final QualityMetric ecnOrder = evaluateEcnOrder(match);
      final QualityMetric interference = new QualityMetric(crossClassInterference ? 0.2 : 1.0,
          crossClassInterference ? "Competing lipid classes present in this row."
              : "No cross-class conflict in selected row.");
      final double overall = (ms1.score + ms2.score + adduct.score + isotope.score + ecnOrder.score
          + interference.score) / 6d;

      card.getChildren().add(annotation);
      card.getChildren().add(createMetricRow("Overall quality", overall,
          overall >= 0.75 ? "High confidence"
              : overall >= 0.5 ? "Moderate confidence" : "Low confidence"));
      card.getChildren().add(createMetricRow("MS1 mass accuracy", ms1.score, ms1.detail));
      card.getChildren().add(createMetricRow("MS2 diagnostics", ms2.score, ms2.detail));
      card.getChildren()
          .add(createMetricRow("Adduct vs ion identity", adduct.score, adduct.detail));
      card.getChildren().add(createMetricRow("Isotope pattern", isotope.score, isotope.detail));
      card.getChildren().add(createMetricRow("ECN elution order", ecnOrder.score, ecnOrder.detail));
      card.getChildren()
          .add(createMetricRow("Interference risk", interference.score, interference.detail));
      card.getChildren().add(createDuplicateRowLinks(match));
      return card;
    }

    private Region createMetricRow(String name, double score, String detail) {
      final double clipped = Math.max(0d, Math.min(1d, score));
      final Label label = new Label(name);
      label.setMinWidth(170);
      final javafx.scene.control.ProgressBar bar = new javafx.scene.control.ProgressBar(clipped);
      bar.setPrefWidth(130);
      final Label value = new Label(String.format("%.0f%%", clipped * 100d));
      value.setMinWidth(45);
      value.setStyle("-fx-font-weight: bold;");
      final HBox rowBox = new HBox(8, label, bar, value, new Label(detail));
      rowBox.setAlignment(Pos.CENTER_LEFT);
      return rowBox;
    }

    private Region createDuplicateRowLinks(MatchedLipid match) {
      final javafx.scene.layout.FlowPane rowLinks = new javafx.scene.layout.FlowPane(4, 4);
      final Label title = new Label("Same annotation rows:");
      rowLinks.getChildren().add(title);
      final List<FeatureListRow> duplicateRows = getDuplicateRows(match);
      if (duplicateRows.isEmpty()) {
        rowLinks.getChildren().add(new Label("none"));
        return rowLinks;
      }
      for (FeatureListRow duplicate : duplicateRows) {
        final javafx.scene.control.Hyperlink link = new javafx.scene.control.Hyperlink(
            "#" + duplicate.getID());
        if (row != null && duplicate.getID() == row.getID()) {
          link.setStyle("-fx-font-weight: bold;");
        }
        link.setOnAction(_ -> {
          model.setRow(duplicate);
          FeatureTableFXUtil.selectAndScrollTo(duplicate, model.getFeatureTableFx());
        });
        rowLinks.getChildren().add(link);
      }
      return rowLinks;
    }

    private List<FeatureListRow> getDuplicateRows(MatchedLipid match) {
      if (featureList == null) {
        return List.of();
      }
      final String annotation = match.getLipidAnnotation().getAnnotation();
      return featureList.getRows().stream().filter(r -> !r.getLipidMatches().isEmpty()).filter(
              r -> r.getLipidMatches().getFirst().getLipidAnnotation().getAnnotation()
                  .equals(annotation)).sorted((a, b) -> Integer.compare(a.getID(), b.getID()))
          .map(r -> (FeatureListRow) r).toList();
    }

    private QualityMetric evaluateMs1(MatchedLipid match) {
      final double exactMz = MatchedLipid.getExactMass(match);
      final double observedMz = match.getAccurateMz() != null ? match.getAccurateMz()
          : (row != null ? row.getAverageMZ() : exactMz);
      final double ppm = (observedMz - exactMz) / exactMz * 1e6;
      final double absPpm = Math.abs(ppm);
      final double score = absPpm <= 2 ? 1d : absPpm <= 5 ? 0.8 : absPpm <= 10 ? 0.5 : 0.2;
      return new QualityMetric(score, String.format("%.2f ppm", ppm));
    }

    private QualityMetric evaluateMs2(MatchedLipid match) {
      final int fragmentCount = match.getMatchedFragments().size();
      final double scorePct = (match.getMsMsScore() == null ? 0d : match.getMsMsScore()) * 100d;
      final double fragmentScore = Math.min(1d, fragmentCount / 6d);
      final double explainedIntensityScore = Math.min(1d, scorePct / 75d);
      final double combined = (fragmentScore + explainedIntensityScore) / 2d;
      return new QualityMetric(combined,
          fragmentCount + " fragments, " + String.format("%.1f", scorePct) + "% explained");
    }

    private QualityMetric evaluateAdduct(MatchedLipid match) {
      if (row == null || row.getBestIonIdentity() == null) {
        return new QualityMetric(0.5, "No ion identity available for cross-check");
      }
      final String featureAdduct = normalizeAdduct(row.getBestIonIdentity().getAdduct());
      final String lipidAdduct = normalizeAdduct(match.getIonizationType().getAdductName());
      final boolean matchFound =
          featureAdduct.contains(lipidAdduct) || lipidAdduct.contains(featureAdduct);
      final String detail = "Feature: " + row.getBestIonIdentity().getAdduct() + " vs Lipid: "
          + match.getIonizationType().getAdductName();
      return new QualityMetric(matchFound ? 1d : 0.15, detail);
    }

    private QualityMetric evaluateIsotope(MatchedLipid match) {
      if (row == null || row.getBestIsotopePattern() == null || match.getIsotopePattern() == null) {
        return new QualityMetric(0.35, "Missing measured or theoretical isotope pattern");
      }
      final float score = io.github.mzmine.modules.tools.isotopepatternscore.IsotopePatternScoreCalculator.getSimilarityScore(
          row.getBestIsotopePattern(), match.getIsotopePattern(),
          new io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance(0.003, 10d), 0d);
      return new QualityMetric(score, "Similarity score " + String.format("%.2f", score));
    }

    private QualityMetric evaluateEcnOrder(MatchedLipid match) {
      if (featureList == null || row == null || row.getAverageRT() == null) {
        return new QualityMetric(0.4, "Missing RT context");
      }
      final int selectedCarbons = extractCarbons(match.getLipidAnnotation());
      final int selectedDbe = extractDbe(match.getLipidAnnotation());
      if (selectedCarbons < 0 || selectedDbe < 0) {
        return new QualityMetric(0.4, "Cannot parse lipid annotation for ECN check");
      }
      final List<double[]> points = new ArrayList<>();
      for (FeatureListRow other : featureList.getRows()) {
        final List<MatchedLipid> otherMatches = other.getLipidMatches();
        if (otherMatches.isEmpty() || other.getAverageRT() == null) {
          continue;
        }
        final MatchedLipid otherMatch = otherMatches.getFirst();
        if (!otherMatch.getLipidAnnotation().getLipidClass()
            .equals(match.getLipidAnnotation().getLipidClass())) {
          continue;
        }
        if (extractDbe(otherMatch.getLipidAnnotation()) != selectedDbe) {
          continue;
        }
        final int carbons = extractCarbons(otherMatch.getLipidAnnotation());
        if (carbons < 0) {
          continue;
        }
        points.add(new double[]{carbons, other.getAverageRT()});
      }
      if (points.size() < 3) {
        return new QualityMetric(0.45, "Not enough class+DBE rows for ECN order check");
      }

      final double expectedRt = predictRtByLinearFit(points, selectedCarbons);
      final double observedRt = row.getAverageRT();
      final double residual = Math.abs(observedRt - expectedRt);
      final double tolerance = Math.max(0.1d, estimateResidualStd(points) * 2d);
      final double score = residual <= tolerance ? 1d : residual <= tolerance * 1.7 ? 0.55 : 0.2;
      final String detail = String.format("RT %.2f vs expected %.2f (Δ=%.2f)", observedRt,
          expectedRt, residual);
      return new QualityMetric(score, detail);
    }

    private static String normalizeAdduct(String adduct) {
      return adduct == null ? "" : adduct.replaceAll("\\s+", "").toLowerCase();
    }

    private static int extractDbe(ILipidAnnotation lipidAnnotation) {
      if (lipidAnnotation instanceof MolecularSpeciesLevelAnnotation molecularAnnotation) {
        return MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
            molecularAnnotation.getAnnotation()).getValue();
      }
      if (lipidAnnotation instanceof SpeciesLevelAnnotation speciesAnnotation) {
        return MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
            speciesAnnotation.getAnnotation()).getValue();
      }
      return -1;
    }

    private static int extractCarbons(ILipidAnnotation lipidAnnotation) {
      if (lipidAnnotation instanceof MolecularSpeciesLevelAnnotation molecularAnnotation) {
        return MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
            molecularAnnotation.getAnnotation()).getKey();
      }
      if (lipidAnnotation instanceof SpeciesLevelAnnotation speciesAnnotation) {
        return MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
            speciesAnnotation.getAnnotation()).getKey();
      }
      return -1;
    }

    private static double predictRtByLinearFit(List<double[]> points, double x) {
      double sumX = 0;
      double sumY = 0;
      double sumXY = 0;
      double sumXX = 0;
      for (double[] p : points) {
        sumX += p[0];
        sumY += p[1];
        sumXY += p[0] * p[1];
        sumXX += p[0] * p[0];
      }
      final int n = points.size();
      final double denom = n * sumXX - sumX * sumX;
      if (Math.abs(denom) < 1e-8) {
        return sumY / n;
      }
      final double slope = (n * sumXY - sumX * sumY) / denom;
      final double intercept = (sumY - slope * sumX) / n;
      return intercept + slope * x;
    }

    private static double estimateResidualStd(List<double[]> points) {
      if (points.size() < 3) {
        return 0.1d;
      }
      double meanX = points.stream().mapToDouble(p -> p[0]).average().orElse(0d);
      double meanY = points.stream().mapToDouble(p -> p[1]).average().orElse(0d);
      double numerator = 0d;
      double denominator = 0d;
      for (double[] p : points) {
        numerator += (p[0] - meanX) * (p[1] - meanY);
        denominator += (p[0] - meanX) * (p[0] - meanX);
      }
      final double slope = denominator == 0d ? 0d : numerator / denominator;
      final double intercept = meanY - slope * meanX;
      double ss = 0d;
      for (double[] p : points) {
        final double residual = p[1] - (intercept + slope * p[0]);
        ss += residual * residual;
      }
      return Math.sqrt(ss / Math.max(1, points.size() - 2));
    }

    private void showPlaceholder(String text) {
      placeholder.setText(text);
      setCenter(placeholder);
    }

    private static String qualityBackgroundStyle() {
      return MZmineCore.getConfiguration().isDarkMode() ? "-fx-background-color: #2f2f2f;"
          : "-fx-background-color: #ffffff;";
    }

    private static String qualityWarningStyle() {
      return MZmineCore.getConfiguration().isDarkMode()
          ? "-fx-background-color: #66581f; -fx-border-color: #ac9440; -fx-padding: 6;"
          : "-fx-background-color: #fff3cd; -fx-border-color: #ffcc80; -fx-padding: 6;";
    }

    private static String qualityCardStyle() {
      return MZmineCore.getConfiguration().isDarkMode()
          ? "-fx-background-color: #3a3a3a; -fx-border-color: #5b5b5b; -fx-padding: 8;"
          : "-fx-background-color: #ffffff; -fx-border-color: #d0d0d0; -fx-padding: 8;";
    }

    private record QualityMetric(double score, String detail) {

    }
  }

  private enum SummaryGroup {
    LIPID_SUBCLASS("Lipid subclass") {
      @Override
      String extractGroupLabel(MatchedLipid lipid, String subclassToken) {
        return subclassToken;
      }

      @Override
      String extractTooltip(MatchedLipid lipid, String subclassToken) {
        return lipid.getLipidAnnotation().getLipidClass().getName();
      }
    }, LIPID_MAIN_CLASS("Lipid main class") {
      @Override
      String extractGroupLabel(MatchedLipid lipid, String subclassToken) {
        return lipid.getLipidAnnotation().getLipidClass().getMainClass().getName();
      }
    }, LIPID_CATEGORY("Lipid category") {
      @Override
      String extractGroupLabel(MatchedLipid lipid, String subclassToken) {
        return lipid.getLipidAnnotation().getLipidClass().getMainClass().getLipidCategory()
            .getName();
      }
    };

    private final String axisLabel;

    SummaryGroup(String axisLabel) {
      this.axisLabel = axisLabel;
    }

    String getAxisLabel() {
      return axisLabel;
    }

    abstract String extractGroupLabel(MatchedLipid lipid, String subclassToken);

    String extractTooltip(MatchedLipid lipid, String subclassToken) {
      return extractGroupLabel(lipid, subclassToken);
    }

    @Override
    public String toString() {
      return axisLabel;
    }
  }

  private static final class DashboardFilterState {

    private Set<Integer> barSelectedRowIds = Set.of();
    private @Nullable Runnable onChange;
  }

  private static final class FilteringBubbleRenderer extends ColoredBubbleDatasetRenderer {

    private Predicate<FeatureListRow> rowFilter = _ -> true;

    public void setRowFilter(Predicate<FeatureListRow> rowFilter) {
      this.rowFilter = rowFilter;
    }

    @Override
    public void drawItem(Graphics2D g2, XYItemRendererState state, Rectangle2D dataArea,
        PlotRenderingInfo info, XYPlot plot, ValueAxis domainAxis, ValueAxis rangeAxis,
        XYDataset dataset, int series, int item, CrosshairState crosshairState, int pass) {
      if (dataset instanceof XYItemObjectProvider<?> provider) {
        final Object obj = provider.getItemObject(item);
        if (obj instanceof FeatureListRow row && !rowFilter.test(row)) {
          return;
        }
      }
      super.drawItem(g2, state, dataArea, info, plot, domainAxis, rangeAxis, dataset, series, item,
          crosshairState, pass);
    }
  }
}
