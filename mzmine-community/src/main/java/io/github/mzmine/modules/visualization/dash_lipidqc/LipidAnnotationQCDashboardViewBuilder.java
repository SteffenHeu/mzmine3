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

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.IsotopePattern;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.types.annotations.LipidMatchListType;
import io.github.mzmine.datamodel.identities.iontype.IonType;
import io.github.mzmine.datamodel.identities.iontype.IonTypeParser;
import io.github.mzmine.gui.chartbasics.chartutils.ColoredBubbleDatasetRenderer;
import io.github.mzmine.gui.chartbasics.chartutils.paintscales.PaintScaleTransform;
import io.github.mzmine.gui.chartbasics.gui.javafx.EChartViewer;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYDataset;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.RunOption;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.XYZBubbleDataset;
import io.github.mzmine.gui.chartbasics.simplechart.providers.ToolTipTextProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.XYItemObjectProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.impl.spectra.MassSpectrumProvider;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYBarRenderer;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.javafx.util.FxColorUtil;
import io.github.mzmine.javafx.util.color.ColorScaleUtil;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.MSMSLipidTools;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.molecular_species.MolecularSpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.species_level.SpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.ILipidAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.ILipidClass;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.LipidAnnotationLevel;
import io.github.mzmine.modules.tools.isotopeprediction.IsotopePatternCalculator;
import io.github.mzmine.modules.visualization.equivalentcarbonnumberplot.EquivalentCarbonNumberChart;
import io.github.mzmine.modules.visualization.equivalentcarbonnumberplot.EquivalentCarbonNumberDataset;
import io.github.mzmine.modules.visualization.kendrickmassplot.KendrickMassPlotChart;
import io.github.mzmine.modules.visualization.kendrickmassplot.KendrickMassPlotParameters;
import io.github.mzmine.modules.visualization.kendrickmassplot.KendrickMassPlotXYZDataset;
import io.github.mzmine.modules.visualization.kendrickmassplot.KendrickPlotDataTypes;
import io.github.mzmine.modules.visualization.spectra.matchedlipid.LipidSpectrumPlot;
import io.github.mzmine.modules.visualization.spectra.simplespectra.SpectraPlot;
import io.github.mzmine.modules.visualization.spectra.simplespectra.renderers.SpectraItemLabelGenerator;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsSelection;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.FeatureTableFXUtil;
import io.github.mzmine.util.color.SimpleColorPalette;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Accordion;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.CategoryTextAnnotation;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.CategoryAnchor;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.entity.CategoryItemEntity;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.event.AxisChangeEvent;
import org.jfree.chart.event.AxisChangeListener;
import org.jfree.chart.fx.interaction.ChartMouseEventFX;
import org.jfree.chart.fx.interaction.ChartMouseListenerFX;
import org.jfree.chart.labels.CategoryToolTipGenerator;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CrosshairState;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.LookupPaintScale;
import org.jfree.chart.renderer.PaintScale;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.renderer.xy.XYItemRendererState;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.title.Title;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.openscience.cdk.interfaces.IMolecularFormula;

public class LipidAnnotationQCDashboardViewBuilder extends
    FxViewBuilder<LipidAnnotationQCDashboardModel> {

  private static final double RT_DELTA_NO_PENALTY_NORMALIZED = 0.006d;
  private static final double RT_DELTA_FULL_PENALTY_NORMALIZED = 0.07d;
  private static final double RT_DELTA_PENALTY_EXPONENT = 1.35d;

  protected LipidAnnotationQCDashboardViewBuilder(LipidAnnotationQCDashboardModel model) {
    super(model);
  }

  @Override
  public Region build() {
    final DashboardFilterState filterState = new DashboardFilterState();
    final BorderPane isotopePane = new IsotopePane();
    final EquivalentCarbonNumberPane ecnPane = new EquivalentCarbonNumberPane(model);
    final KendrickPane kendrickPane = new KendrickPane(model, filterState);
    final AnnotationQualityPane qualityPane = new AnnotationQualityPane(model);
    final BorderPane matchedSignalsPane = new MatchedSignalsPane();
    final ComboBox<PreferredLipidLevelOption> preferredLevelCombo = new ComboBox<>(
        FXCollections.observableArrayList(PreferredLipidLevelOption.values()));
    preferredLevelCombo.getSelectionModel().select(PreferredLipidLevelOption.MOLECULAR_SPECIES);
    final LipidSummaryPane summaryPane = new LipidSummaryPane(model, filterState,
        preferredLevelCombo);
    final Runnable refreshAllDashboardPlots = () -> {
      final @Nullable ModularFeatureList currentFeatureList = model.getFeatureList();
      final @Nullable FeatureListRow selectedRow = model.getRow();
      if (currentFeatureList != null) {
        summaryPane.setFeatureList(currentFeatureList);
        qualityPane.setFeatureList(currentFeatureList);
        kendrickPane.setFeatureList(currentFeatureList);
        ecnPane.setFeatureList(currentFeatureList);
        ((IsotopePane) isotopePane).setFeatureList(currentFeatureList);
        ((MatchedSignalsPane) matchedSignalsPane).setFeatureList(currentFeatureList);
      }
      qualityPane.setRow(selectedRow);
      kendrickPane.setRow(selectedRow);
      ecnPane.setRow(selectedRow);
      ((IsotopePane) isotopePane).setRow(selectedRow);
      ((MatchedSignalsPane) matchedSignalsPane).setRow(selectedRow);
    };
    qualityPane.setOnAnnotationsChanged(refreshAllDashboardPlots);

    model.featureTableFxProperty().get().getSelectionModel().selectedItemProperty()
        .addListener((_, _, row) -> model.setRow(row == null ? null : row.getValue()));
    model.featureTableFxProperty().get().getFilteredRowItems().addListener(
        (javafx.collections.ListChangeListener<javafx.scene.control.TreeItem<io.github.mzmine.datamodel.features.ModularFeatureListRow>>) _ -> Platform.runLater(
            () -> {
              refreshAllDashboardPlots.run();
              selectFirstVisibleRow(model);
            }));
    model.rowProperty().addListener((_, _, row) -> {
      if (row != null && model.getFeatureTableFx().getSelectedRow() != model.getRow()) {
        FeatureTableFXUtil.selectAndScrollTo(row, model.getFeatureTableFx());
      }
      ((IsotopePane) isotopePane).setRow(row);
      ecnPane.setRow(row);
      ((MatchedSignalsPane) matchedSignalsPane).setRow(row);
      kendrickPane.setRow(row);
      qualityPane.setRow(row);
    });

    model.featureListProperty().subscribe(flist -> {
      ecnPane.setFeatureList(flist);
      ((IsotopePane) isotopePane).setFeatureList(flist);
      ((MatchedSignalsPane) matchedSignalsPane).setFeatureList(flist);
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

    final BorderPane retentionSection = wrapInSubsection("Retention time analysis", ecnPane);
    if (retentionSection.getTop() instanceof Label titleLabel) {
      titleLabel.textProperty().bind(ecnPane.paneTitleProperty());
    }

    final Region dashboardContent = createSixPaneLayout(
        wrapInSubsection("Lipid annotation summary", summaryPane),
        wrapInSubsection("Kendrick mass plot", kendrickPane),
        wrapInSubsection("Lipid annotation quality", qualityPane), retentionSection,
        wrapInSubsection("Matched lipid signals", matchedSignalsPane),
        wrapInSubsection("Isotope pattern", isotopePane));

    preferredLevelCombo.setOnAction(_ -> {
      final PreferredLipidLevelOption option = preferredLevelCombo.getValue();
      if (option == null) {
        return;
      }
      applyPreferredLipidLevel(model.getFeatureList(), option.level);
      model.getFeatureTableFx().refresh();
      refreshAllDashboardPlots.run();
    });
    final SplitPane mainSplit = new SplitPane(dashboardContent,
        model.getFeatureTableController().buildView());
    mainSplit.setOrientation(Orientation.VERTICAL);
    mainSplit.setDividerPositions(0.68);

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

  private static BorderPane wrapInSubsection(final @NotNull String title,
      final @NotNull Region content) {
    final BorderPane pane = new BorderPane(content);
    final Label titleLabel = new Label(title);
    titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 4 6 4 6;");
    pane.setTop(titleLabel);
    pane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    VBox.setVgrow(pane, javafx.scene.layout.Priority.ALWAYS);
    return pane;
  }

  private static Region createSixPaneLayout(final @NotNull Region summaryPane,
      final @NotNull Region kendrickPane, final @NotNull Region qualityPane,
      final @NotNull Region ecnPane, final @NotNull Region matchedSignalsPane,
      final @NotNull Region isotopePane) {
    final GridPane grid = new GridPane();
    grid.setHgap(8);
    grid.setVgap(8);

    for (int col = 0; col < 3; col++) {
      final ColumnConstraints constraints = new ColumnConstraints();
      constraints.setPercentWidth(100d / 3d);
      constraints.setHgrow(javafx.scene.layout.Priority.ALWAYS);
      grid.getColumnConstraints().add(constraints);
    }
    for (int row = 0; row < 2; row++) {
      final RowConstraints constraints = new RowConstraints();
      constraints.setPercentHeight(50d);
      constraints.setVgrow(javafx.scene.layout.Priority.ALWAYS);
      grid.getRowConstraints().add(constraints);
    }

    final List<Region> panes = List.of(summaryPane, kendrickPane, qualityPane, ecnPane,
        matchedSignalsPane, isotopePane);
    for (final Region pane : panes) {
      pane.setMinSize(260, 180);
      pane.setPrefSize(360, 250);
      pane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    }
    ecnPane.setMinHeight(120);

    grid.add(summaryPane, 0, 0);
    grid.add(kendrickPane, 0, 1);
    grid.add(qualityPane, 1, 0);
    grid.add(ecnPane, 1, 1);
    grid.add(matchedSignalsPane, 2, 0);
    grid.add(isotopePane, 2, 1);
    GridPane.setHgrow(summaryPane, javafx.scene.layout.Priority.ALWAYS);
    GridPane.setVgrow(summaryPane, javafx.scene.layout.Priority.ALWAYS);
    GridPane.setHgrow(kendrickPane, javafx.scene.layout.Priority.ALWAYS);
    GridPane.setVgrow(kendrickPane, javafx.scene.layout.Priority.ALWAYS);
    GridPane.setHgrow(qualityPane, javafx.scene.layout.Priority.ALWAYS);
    GridPane.setVgrow(qualityPane, javafx.scene.layout.Priority.ALWAYS);
    GridPane.setHgrow(ecnPane, javafx.scene.layout.Priority.ALWAYS);
    GridPane.setVgrow(ecnPane, javafx.scene.layout.Priority.ALWAYS);
    GridPane.setHgrow(matchedSignalsPane, javafx.scene.layout.Priority.ALWAYS);
    GridPane.setVgrow(matchedSignalsPane, javafx.scene.layout.Priority.ALWAYS);
    GridPane.setHgrow(isotopePane, javafx.scene.layout.Priority.ALWAYS);
    GridPane.setVgrow(isotopePane, javafx.scene.layout.Priority.ALWAYS);
    return grid;
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

  private static class MatchedSignalsPane extends BorderPane {

    private final Label placeholder = new Label("Select a row with matched lipid signals.");
    private @Nullable FeatureListRow row;
    private @Nullable LipidSpectrumPlot spectrumPlot;

    private MatchedSignalsPane() {
      setCenter(placeholder);
      BorderPane.setAlignment(placeholder, Pos.CENTER);
    }

    void setFeatureList(@Nullable ModularFeatureList featureList) {
    }

    void setRow(@Nullable FeatureListRow row) {
      this.row = row;
      update();
    }

    private void update() {
      if (row == null) {
        showPlaceholder("Select a row with matched lipid signals.");
        return;
      }

      final List<MatchedLipid> matches = row.get(LipidMatchListType.class);
      if (matches == null || matches.isEmpty()) {
        showPlaceholder("No matched lipid signals available for selected row.");
        return;
      }

      final MatchedLipid match = matches.getFirst();
      if (spectrumPlot == null) {
        spectrumPlot = new LipidSpectrumPlot(match, true, RunOption.THIS_THREAD);
      } else {
        spectrumPlot.updateLipidSpectrum(match, true, RunOption.THIS_THREAD);
      }
      setCenter(spectrumPlot);
    }

    private void showPlaceholder(final String text) {
      placeholder.setText(text);
      setCenter(placeholder);
    }
  }

  private static class EquivalentCarbonNumberPane extends BorderPane {

    private final LipidAnnotationQCDashboardModel model;
    private final Label placeholder = new Label("Select a row with lipid annotations.");
    private final StringProperty paneTitle = new SimpleStringProperty("Retention time analysis");
    private final ComboBox<RetentionTrendMode> trendModeCombo = new ComboBox<>(
        FXCollections.observableArrayList(RetentionTrendMode.values()));
    private List<FeatureListRow> rowsWithLipidIds = List.of();
    private @Nullable FeatureListRow selectedRow;

    private EquivalentCarbonNumberPane(LipidAnnotationQCDashboardModel model) {
      this.model = model;
      trendModeCombo.getSelectionModel().select(RetentionTrendMode.COMBINED_CARBON_DBE_TRENDS);
      trendModeCombo.valueProperty().addListener((_, _, _) -> update());
      final HBox trendRow = new HBox(6, new Label("Trend:"), trendModeCombo);
      trendRow.setAlignment(Pos.CENTER_LEFT);
      final VBox controlBox = new VBox(6, trendRow);
      controlBox.setAlignment(Pos.TOP_LEFT);
      final TitledPane controls = new TitledPane("Retention time analysis options", controlBox);
      controls.setCollapsible(true);
      final Accordion accordion = new Accordion(controls);
      accordion.setExpandedPane(null);
      setBottom(accordion);
      setCenter(placeholder);
      BorderPane.setAlignment(placeholder, Pos.CENTER);
    }

    StringProperty paneTitleProperty() {
      return paneTitle;
    }

    void setFeatureList(@NotNull ModularFeatureList featureList) {
      rowsWithLipidIds = featureList.getRows().stream()
          .filter(LipidAnnotationQCDashboardViewBuilder::rowHasMatchedLipidSignals)
          .map(r -> (FeatureListRow) r).toList();
      update();
    }

    void setRow(@Nullable FeatureListRow row) {
      selectedRow = row;
      update();
    }

    private void update() {
      updatePaneTitle("Retention time analysis");
      if (selectedRow == null) {
        showPlaceholder("Select a row with lipid annotations.");
        return;
      }

      final List<MatchedLipid> selectedMatches = selectedRow.get(LipidMatchListType.class);
      if (selectedMatches == null || selectedMatches.isEmpty()) {
        showPlaceholder("No lipid annotations available for selected row.");
        return;
      }

      final List<FeatureListRow> currentRowsWithLipidIds = getCurrentRowsWithLipidIds();
      if (currentRowsWithLipidIds.isEmpty()) {
        showPlaceholder("No lipid annotations available in this feature list.");
        return;
      }

      final MatchedLipid selectedMatch = selectedMatches.getFirst();
      final RetentionTrendMode mode = trendModeCombo.getValue();
      if (mode == null) {
        showPlaceholder("Select a retention time trend.");
        return;
      }
      updatePaneTitle("Retention time analysis: " + mode);
      switch (mode) {
        case ECN_CARBON_TREND -> showEcnTrend(selectedMatch, currentRowsWithLipidIds);
        case DBE_TREND -> showDbeTrend(selectedMatch, currentRowsWithLipidIds);
        case COMBINED_CARBON_DBE_TRENDS ->
            showCombinedTrend(selectedMatch, currentRowsWithLipidIds);
      }
    }

    private void showEcnTrend(final @NotNull MatchedLipid selectedMatch,
        final @NotNull List<FeatureListRow> currentRowsWithLipidIds) {
      final ILipidClass selectedClass = selectedMatch.getLipidAnnotation().getLipidClass();
      final int dbe = extractDbe(selectedMatch.getLipidAnnotation());
      if (dbe < 0) {
        showPlaceholder("Cannot determine DBE for selected lipid annotation.");
        return;
      }

      final List<MatchedLipid> classAndDbeMatches = currentRowsWithLipidIds.stream()
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
          currentRowsWithLipidIds, currentRowsWithLipidIds.toArray(new FeatureListRow[0]),
          selectedClass, dbe);
      dataset.run();
      final EquivalentCarbonNumberChart chart = new EquivalentCarbonNumberChart("",
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
      if (selectedRow != null) {
        highlightSelectedLipid(chart, selectedRow, selectedMatch);
      }
      chart.setMinSize(250, 120);
      updatePaneTitle(
          "Retention time analysis: " + selectedClass.getAbbr() + " ECN DBE=" + dbe + " (R2 "
              + MZmineCore.getConfiguration().getScoreFormat().format(chart.getR2()) + ", n="
              + classAndDbeMatches.size() + ")");
      setCenter(chart);
    }

    private void showDbeTrend(final @NotNull MatchedLipid selectedMatch,
        final @NotNull List<FeatureListRow> currentRowsWithLipidIds) {
      final ILipidClass selectedClass = selectedMatch.getLipidAnnotation().getLipidClass();
      final int carbons = extractCarbons(selectedMatch.getLipidAnnotation());
      final int selectedDbe = extractDbe(selectedMatch.getLipidAnnotation());
      if (carbons < 0 || selectedDbe < 0) {
        showPlaceholder("Cannot determine carbon/DBE values for selected lipid annotation.");
        return;
      }

      final RetentionTrendDataset trendDataset = new RetentionTrendDataset(currentRowsWithLipidIds,
          match -> match.getLipidAnnotation().getLipidClass().equals(selectedClass)
              && extractCarbons(match.getLipidAnnotation()) == carbons,
          match -> extractDbe(match.getLipidAnnotation()));
      if (trendDataset.getItemCount(0) < 3) {
        showPlaceholder(
            "Not enough lipids for DBE trend model (need at least 3 rows in same class/carbon group).");
        return;
      }

      final TrendChartResult chartResult = createTrendChart(trendDataset, "Number of DBEs");
      final EChartViewer chart = chartResult.chart();
      chart.addChartMouseListener(new ChartMouseListenerFX() {
        @Override
        public void chartMouseClicked(final ChartMouseEventFX event) {
          if (!(event.getEntity() instanceof XYItemEntity entity)
              || !(entity.getDataset() instanceof RetentionTrendDataset clickedDataset)) {
            return;
          }
          final int item = entity.getItem();
          if (item < 0 || item >= clickedDataset.getItemCount(0)) {
            return;
          }
          final MatchedLipid clickedLipid = clickedDataset.getMatchedLipid(item);
          final FeatureListRow clickedRow = findRowForLipid(clickedLipid);
          if (clickedRow != null) {
            model.setRow(clickedRow);
            FeatureTableFXUtil.selectAndScrollTo(clickedRow, model.getFeatureTableFx());
          }
        }

        @Override
        public void chartMouseMoved(final ChartMouseEventFX event) {
        }
      });

      if (selectedRow != null) {
        highlightSelectedTrendPoint(chart, selectedRow, selectedDbe);
      }
      updatePaneTitle(
          "Retention time analysis: " + selectedClass.getAbbr() + " DBE C=" + carbons + " (R2 "
              + MZmineCore.getConfiguration().getScoreFormat().format(chartResult.r2()) + ", n="
              + trendDataset.getItemCount(0) + ")");
      setCenter(chart);
    }

    private void showCombinedTrend(final @NotNull MatchedLipid selectedMatch,
        final @NotNull List<FeatureListRow> currentRowsWithLipidIds) {
      final ILipidClass selectedClass = selectedMatch.getLipidAnnotation().getLipidClass();
      final int selectedCarbons = extractCarbons(selectedMatch.getLipidAnnotation());
      final int selectedDbe = extractDbe(selectedMatch.getLipidAnnotation());
      if (selectedCarbons < 0 || selectedDbe < 0) {
        showPlaceholder("Cannot determine carbon/DBE values for selected lipid annotation.");
        return;
      }

      final RetentionTrendDataset carbonTrendDataset = new RetentionTrendDataset(
          currentRowsWithLipidIds,
          match -> match.getLipidAnnotation().getLipidClass().equals(selectedClass)
              && extractDbe(match.getLipidAnnotation()) == selectedDbe,
          match -> extractCarbons(match.getLipidAnnotation()));
      final RetentionTrendDataset dbeTrendDataset = new RetentionTrendDataset(
          currentRowsWithLipidIds,
          match -> match.getLipidAnnotation().getLipidClass().equals(selectedClass)
              && extractCarbons(match.getLipidAnnotation()) == selectedCarbons,
          match -> extractDbe(match.getLipidAnnotation()));
      final boolean hasCarbonTrend = carbonTrendDataset.getItemCount(0) >= 3;
      final boolean hasDbeTrend = dbeTrendDataset.getItemCount(0) >= 3;
      if (!hasCarbonTrend && !hasDbeTrend) {
        showPlaceholder(
            "Not enough lipids for combined trend model (need at least 3 rows in trend group).");
        return;
      }

      final CombinedTrendChartResult chartResult = createCombinedTrendChart(
          hasCarbonTrend ? carbonTrendDataset : null, hasDbeTrend ? dbeTrendDataset : null);
      final EChartViewer chart = chartResult.chart();
      chart.addChartMouseListener(new ChartMouseListenerFX() {
        @Override
        public void chartMouseClicked(final ChartMouseEventFX event) {
          if (!(event.getEntity() instanceof XYItemEntity entity)
              || !(entity.getDataset() instanceof RetentionTrendDataset clickedDataset)) {
            return;
          }
          final int item = entity.getItem();
          if (item < 0 || item >= clickedDataset.getItemCount(0)) {
            return;
          }
          final MatchedLipid clickedLipid = clickedDataset.getMatchedLipid(item);
          final FeatureListRow clickedRow = findRowForLipid(clickedLipid);
          if (clickedRow != null) {
            model.setRow(clickedRow);
            FeatureTableFXUtil.selectAndScrollTo(clickedRow, model.getFeatureTableFx());
          }
        }

        @Override
        public void chartMouseMoved(final ChartMouseEventFX event) {
        }
      });

      if (selectedRow != null) {
        highlightSelectedCombinedTrendPoints(chart, selectedRow, selectedCarbons, selectedDbe,
            chartResult);
      }
      final double combinedR2 = combineR2(chartResult.carbonsR2(), chartResult.dbeR2());
      updatePaneTitle(
          "Retention time analysis: " + selectedClass.getAbbr() + " " + selectedCarbons + ":"
              + selectedDbe);
      setCenter(chart);
    }

    private static TrendChartResult createTrendChart(final @NotNull RetentionTrendDataset dataset,
        final @NotNull String yAxisLabel) {
      final JFreeChart chart = ChartFactory.createScatterPlot("", "Retention time", yAxisLabel,
          dataset, PlotOrientation.VERTICAL, false, true, true);
      final EChartViewer viewer = new EChartViewer(chart);
      ConfigService.getConfiguration().getDefaultChartTheme().apply(viewer);

      final XYPlot plot = chart.getXYPlot();
      final XYLineAndShapeRenderer pointRenderer = new XYLineAndShapeRenderer(false, true);
      pointRenderer.setSeriesShape(0, new Ellipse2D.Double(-3, -3, 6, 6));
      pointRenderer.setSeriesPaint(0, ConfigService.getDefaultColorPalette().getPositiveColorAWT());
      pointRenderer.setDefaultItemLabelGenerator(
          (xyDataset, series, item) -> dataset.getLabel(item));
      pointRenderer.setDefaultItemLabelsVisible(true);
      pointRenderer.setDefaultToolTipGenerator(
          (xyDataset, series, item) -> dataset.getTooltip(item));
      plot.setRenderer(0, pointRenderer);

      final double[] regression = calculateLinearRegression(dataset);
      if (dataset.getItemCount(0) > 1 && Double.isFinite(regression[0]) && Double.isFinite(
          regression[1])) {
        plot.setDataset(1, createRegressionDataset(regression[0], regression[1], dataset));
        final XYLineAndShapeRenderer regressionRenderer = new XYLineAndShapeRenderer(true, false);
        regressionRenderer.setSeriesPaint(0,
            ConfigService.getDefaultColorPalette().getNeutralColorAWT());
        plot.setRenderer(1, regressionRenderer);
      }
      return new TrendChartResult(viewer, regression[2]);
    }

    private static double[] calculateLinearRegression(
        final @NotNull RetentionTrendDataset dataset) {
      final int itemCount = dataset.getItemCount(0);
      if (itemCount < 2) {
        return new double[]{Double.NaN, Double.NaN, Double.NaN};
      }
      double sumX = 0d;
      double sumY = 0d;
      double sumXX = 0d;
      double sumXY = 0d;
      for (int i = 0; i < itemCount; i++) {
        final double x = dataset.getXValue(0, i);
        final double y = dataset.getYValue(0, i);
        sumX += x;
        sumY += y;
        sumXX += x * x;
        sumXY += x * y;
      }
      final double denominator = itemCount * sumXX - sumX * sumX;
      if (Math.abs(denominator) < 1e-10d) {
        return new double[]{Double.NaN, Double.NaN, Double.NaN};
      }
      final double slope = (itemCount * sumXY - sumX * sumY) / denominator;
      final double intercept = (sumY - slope * sumX) / itemCount;

      final double meanY = sumY / itemCount;
      double ssr = 0d;
      double sse = 0d;
      for (int i = 0; i < itemCount; i++) {
        final double x = dataset.getXValue(0, i);
        final double y = dataset.getYValue(0, i);
        final double predicted = slope * x + intercept;
        ssr += (predicted - meanY) * (predicted - meanY);
        sse += (y - predicted) * (y - predicted);
      }
      final double r2 = (ssr + sse) > 0d ? ssr / (ssr + sse) : Double.NaN;
      return new double[]{slope, intercept, r2};
    }

    private static @NotNull XYSeriesCollection createRegressionDataset(final double slope,
        final double intercept, final @NotNull RetentionTrendDataset dataset) {
      final double minX = java.util.Arrays.stream(dataset.getXValues()).min().orElse(0d);
      final double maxX = java.util.Arrays.stream(dataset.getXValues()).max().orElse(minX);
      final XYSeries series = new XYSeries("Regression");
      series.add(minX, slope * minX + intercept);
      series.add(maxX, slope * maxX + intercept);
      final XYSeriesCollection regressionDataset = new XYSeriesCollection();
      regressionDataset.addSeries(series);
      return regressionDataset;
    }

    private static @NotNull CombinedTrendChartResult createCombinedTrendChart(
        final @Nullable RetentionTrendDataset carbonDataset,
        final @Nullable RetentionTrendDataset dbeDataset) {
      final XYPlot plot = new XYPlot();
      final NumberAxis domainAxis = new NumberAxis("Retention time");
      plot.setDomainAxis(domainAxis);

      int carbonAxisIndex = -1;
      int dbeAxisIndex = -1;
      double carbonR2 = Double.NaN;
      double dbeR2 = Double.NaN;
      final boolean hasCarbon = carbonDataset != null;
      final boolean hasDbe = dbeDataset != null;
      final int carbonDatasetIndex = hasCarbon ? 0 : -1;
      final int dbeDatasetIndex = hasDbe ? (hasCarbon ? 1 : 0) : -1;
      final int carbonRegressionIndex = hasCarbon ? (hasDbe ? 2 : 1) : -1;
      final int dbeRegressionIndex = hasDbe ? (hasCarbon ? 3 : 1) : -1;
      if (carbonDataset != null) {
        carbonAxisIndex = 0;
        final NumberAxis carbonAxis = new NumberAxis("Number of carbons");
        plot.setRangeAxis(carbonAxisIndex, carbonAxis);
        plot.setDataset(carbonDatasetIndex, carbonDataset);
        plot.mapDatasetToRangeAxis(carbonDatasetIndex, carbonAxisIndex);
        plot.setRenderer(carbonDatasetIndex, createTrendPointRenderer(carbonDataset,
            ConfigService.getDefaultColorPalette().getPositiveColorAWT(),
            new Ellipse2D.Double(-3.2d, -3.2d, 6.4d, 6.4d)));
        final double[] regression = calculateLinearRegression(carbonDataset);
        carbonR2 = regression[2];
        appendRegressionDatasetIfValid(plot, carbonRegressionIndex, regression, carbonDataset,
            carbonAxisIndex, ConfigService.getDefaultColorPalette().getPositiveColorAWT());
      }
      if (dbeDataset != null) {
        dbeAxisIndex = carbonDataset != null ? 1 : 0;
        final NumberAxis dbeAxis = new NumberAxis("Number of DBEs");
        plot.setRangeAxis(dbeAxisIndex, dbeAxis);
        plot.setDataset(dbeDatasetIndex, dbeDataset);
        plot.mapDatasetToRangeAxis(dbeDatasetIndex, dbeAxisIndex);
        plot.setRenderer(dbeDatasetIndex, createTrendPointRenderer(dbeDataset,
            ConfigService.getDefaultColorPalette().getNeutralColorAWT(),
            new Rectangle2D.Double(-3d, -3d, 6d, 6d)));
        final double[] regression = calculateLinearRegression(dbeDataset);
        dbeR2 = regression[2];
        appendRegressionDatasetIfValid(plot, dbeRegressionIndex, regression, dbeDataset,
            dbeAxisIndex, ConfigService.getDefaultColorPalette().getNeutralColorAWT());
      }

      final JFreeChart chart = new JFreeChart("", JFreeChart.DEFAULT_TITLE_FONT, plot, false);
      chart.setBackgroundPaint(new Color(0, 0, 0, 0));
      final EChartViewer viewer = new EChartViewer(chart);
      ConfigService.getConfiguration().getDefaultChartTheme().apply(viewer);
      configureCombinedAxisRanges(plot, carbonDataset, dbeDataset, carbonAxisIndex, dbeAxisIndex);
      return new CombinedTrendChartResult(viewer, carbonR2, dbeR2, carbonAxisIndex, dbeAxisIndex);
    }

    private static void configureCombinedAxisRanges(final @NotNull XYPlot plot,
        final @Nullable RetentionTrendDataset carbonDataset,
        final @Nullable RetentionTrendDataset dbeDataset, final int carbonAxisIndex,
        final int dbeAxisIndex) {
      if (plot.getDomainAxis() instanceof NumberAxis domainAxis) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        if (carbonDataset != null) {
          for (int i = 0; i < carbonDataset.getItemCount(0); i++) {
            final double value = carbonDataset.getXValue(0, i);
            if (Double.isFinite(value)) {
              minX = Math.min(minX, value);
              maxX = Math.max(maxX, value);
            }
          }
        }
        if (dbeDataset != null) {
          for (int i = 0; i < dbeDataset.getItemCount(0); i++) {
            final double value = dbeDataset.getXValue(0, i);
            if (Double.isFinite(value)) {
              minX = Math.min(minX, value);
              maxX = Math.max(maxX, value);
            }
          }
        }
        setAxisRangeToData(domainAxis, minX, maxX, true);
      }

      if (carbonAxisIndex >= 0 && carbonDataset != null && plot.getRangeAxis(
          carbonAxisIndex) instanceof NumberAxis carbonAxis) {
        setAxisRangeToData(carbonAxis, minDatasetY(carbonDataset), maxDatasetY(carbonDataset),
            false);
      }
      if (dbeAxisIndex >= 0 && dbeDataset != null && plot.getRangeAxis(
          dbeAxisIndex) instanceof NumberAxis dbeAxis) {
        setAxisRangeToData(dbeAxis, minDatasetY(dbeDataset), maxDatasetY(dbeDataset), false);
      }
    }

    private static double minDatasetY(final @NotNull RetentionTrendDataset dataset) {
      double min = Double.POSITIVE_INFINITY;
      for (int i = 0; i < dataset.getItemCount(0); i++) {
        final double value = dataset.getYValue(0, i);
        if (Double.isFinite(value)) {
          min = Math.min(min, value);
        }
      }
      return min;
    }

    private static double maxDatasetY(final @NotNull RetentionTrendDataset dataset) {
      double max = Double.NEGATIVE_INFINITY;
      for (int i = 0; i < dataset.getItemCount(0); i++) {
        final double value = dataset.getYValue(0, i);
        if (Double.isFinite(value)) {
          max = Math.max(max, value);
        }
      }
      return max;
    }

    private static void setAxisRangeToData(final @NotNull NumberAxis axis, final double min,
        final double max, final boolean lockLowerToFirstPoint) {
      if (!Double.isFinite(min) || !Double.isFinite(max)) {
        return;
      }
      axis.setAutoRange(false);
      axis.setAutoRangeIncludesZero(false);
      axis.setAutoRangeStickyZero(false);

      if (max <= min) {
        final double delta = Math.max(Math.abs(min) * 0.05d, 0.2d);
        final double lower = lockLowerToFirstPoint ? min : min - delta;
        axis.setRange(lower, min + delta);
        return;
      }

      final double span = max - min;
      final double lowerPadding = lockLowerToFirstPoint ? 0d : span * 0.03d;
      final double upperPadding = span * 0.03d;
      axis.setRange(min - lowerPadding, max + upperPadding);
    }

    private static void appendRegressionDatasetIfValid(final @NotNull XYPlot plot,
        final int datasetIndex, final @NotNull double[] regression,
        final @NotNull RetentionTrendDataset dataset, final int rangeAxisIndex,
        final @NotNull Paint linePaint) {
      if (datasetIndex < 0 || dataset.getItemCount(0) < 2 || !Double.isFinite(regression[0])
          || !Double.isFinite(regression[1])) {
        return;
      }
      plot.setDataset(datasetIndex, createRegressionDataset(regression[0], regression[1], dataset));
      plot.mapDatasetToRangeAxis(datasetIndex, rangeAxisIndex);
      final XYLineAndShapeRenderer regressionRenderer = new XYLineAndShapeRenderer(true, false);
      regressionRenderer.setSeriesPaint(0, linePaint);
      regressionRenderer.setSeriesStroke(0, new java.awt.BasicStroke(1.6f));
      regressionRenderer.setSeriesVisibleInLegend(0, false);
      plot.setRenderer(datasetIndex, regressionRenderer);
    }

    private static @NotNull XYLineAndShapeRenderer createTrendPointRenderer(
        final @NotNull RetentionTrendDataset dataset, final @NotNull Paint seriesPaint,
        final @NotNull java.awt.Shape seriesShape) {
      final XYLineAndShapeRenderer pointRenderer = new XYLineAndShapeRenderer(false, true);
      pointRenderer.setSeriesShape(0, seriesShape);
      pointRenderer.setSeriesPaint(0, seriesPaint);
      pointRenderer.setDefaultItemLabelGenerator(
          (xyDataset, series, item) -> dataset.getLabel(item));
      pointRenderer.setDefaultItemLabelsVisible(true);
      pointRenderer.setDefaultToolTipGenerator(
          (xyDataset, series, item) -> dataset.getTooltip(item));
      pointRenderer.setSeriesVisibleInLegend(0, false);
      return pointRenderer;
    }

    private void updatePaneTitle(final @NotNull String title) {
      paneTitle.set(title);
    }

    private static double combineR2(final double carbonR2, final double dbeR2) {
      if (Double.isFinite(carbonR2) && Double.isFinite(dbeR2)) {
        return clampToUnit((carbonR2 + dbeR2) / 2d);
      }
      if (Double.isFinite(carbonR2)) {
        return clampToUnit(carbonR2);
      }
      if (Double.isFinite(dbeR2)) {
        return clampToUnit(dbeR2);
      }
      return Double.NaN;
    }

    private @Nullable FeatureListRow findRowForLipid(@NotNull MatchedLipid clickedLipid) {
      for (FeatureListRow candidate : getCurrentRowsWithLipidIds()) {
        final List<MatchedLipid> matches = candidate.getLipidMatches();
        if (!matches.isEmpty() && clickedLipid.equals(matches.getFirst())) {
          return candidate;
        }
      }
      return null;
    }

    private @NotNull List<FeatureListRow> getCurrentRowsWithLipidIds() {
      if (rowsWithLipidIds.isEmpty()) {
        return List.of();
      }
      final List<FeatureListRow> current = rowsWithLipidIds.stream()
          .filter(LipidAnnotationQCDashboardViewBuilder::rowHasMatchedLipidSignals).toList();
      rowsWithLipidIds = current;
      return current;
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

    private static void highlightSelectedTrendPoint(final @NotNull EChartViewer chart,
        final @NotNull FeatureListRow row, final double yValue) {
      if (row.getAverageRT() == null || !Double.isFinite(yValue)) {
        return;
      }
      final XYSeries highlightSeries = new XYSeries("Selected lipid");
      highlightSeries.add((double) row.getAverageRT(), yValue);
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

    private static void highlightSelectedCombinedTrendPoints(final @NotNull EChartViewer chart,
        final @NotNull FeatureListRow row, final double carbonValue, final double dbeValue,
        final @NotNull CombinedTrendChartResult chartResult) {
      if (row.getAverageRT() == null) {
        return;
      }
      final XYPlot plot = chart.getChart().getXYPlot();
      plot.clearDomainMarkers();
      plot.setDataset(10, null);
      plot.setRenderer(10, null);
      plot.setDataset(11, null);
      plot.setRenderer(11, null);

      final double rt = row.getAverageRT();
      final Paint selectedColor = combinedSelectionPaint();
      final boolean hasCarbonPoint =
          chartResult.carbonsAxisIndex() >= 0 && Double.isFinite(carbonValue);
      final boolean hasDbePoint = chartResult.dbeAxisIndex() >= 0 && Double.isFinite(dbeValue);
      if (!hasCarbonPoint || !hasDbePoint) {
        final ValueMarker selectionMarker = new ValueMarker(rt);
        selectionMarker.setPaint(selectedColor);
        selectionMarker.setStroke(new java.awt.BasicStroke(1.6f, java.awt.BasicStroke.CAP_BUTT,
            java.awt.BasicStroke.JOIN_BEVEL, 0f, new float[]{6f, 4f}, 0f));
        selectionMarker.setAlpha(0.75f);
        plot.addDomainMarker(selectionMarker);
      } else {
        synchronizeSelectedPointAcrossAxes(plot, chartResult.carbonsAxisIndex(),
            chartResult.dbeAxisIndex(), carbonValue, dbeValue);
      }

      if (hasCarbonPoint) {
        addSelectedOverlayPoint(plot, 10, chartResult.carbonsAxisIndex(), rt, carbonValue,
            selectedColor, new Ellipse2D.Double(-5d, -5d, 10d, 10d));
      }
      if (hasDbePoint) {
        addSelectedOverlayPoint(plot, 11, chartResult.dbeAxisIndex(), rt, dbeValue, selectedColor,
            new Ellipse2D.Double(-5d, -5d, 10d, 10d));
      }
    }

    private static @NotNull Paint combinedSelectionPaint() {
      return ColorScaleUtil.getColor(ConfigService.getDefaultColorPalette().getPositiveColorAWT(),
          ConfigService.getDefaultColorPalette().getNeutralColorAWT(), 0d, 1d, 0.5d);
    }

    private static void addSelectedOverlayPoint(final @NotNull XYPlot plot, final int datasetIndex,
        final int rangeAxisIndex, final double xValue, final double yValue,
        final @NotNull Paint strokePaint, final @NotNull java.awt.Shape marker) {
      final XYSeries overlaySeries = new XYSeries("Selected lipid");
      overlaySeries.add(xValue, yValue);
      final XYSeriesCollection overlayDataset = new XYSeriesCollection();
      overlayDataset.addSeries(overlaySeries);

      final XYLineAndShapeRenderer overlayRenderer = new XYLineAndShapeRenderer(false, true);
      overlayRenderer.setSeriesPaint(0, strokePaint);
      overlayRenderer.setSeriesStroke(0, new java.awt.BasicStroke(2f));
      overlayRenderer.setSeriesShape(0, marker);
      overlayRenderer.setDefaultShapesFilled(true);
      overlayRenderer.setUseOutlinePaint(true);
      overlayRenderer.setSeriesOutlinePaint(0,
          MZmineCore.getConfiguration().isDarkMode() ? Color.WHITE : Color.BLACK);
      overlayRenderer.setSeriesOutlineStroke(0, new java.awt.BasicStroke(1.1f));
      overlayRenderer.setSeriesVisibleInLegend(0, false);

      plot.setDataset(datasetIndex, overlayDataset);
      plot.mapDatasetToRangeAxis(datasetIndex, rangeAxisIndex);
      plot.setRenderer(datasetIndex, overlayRenderer);
    }

    private static void synchronizeSelectedPointAcrossAxes(final @NotNull XYPlot plot,
        final int primaryAxisIndex, final int secondaryAxisIndex, final double primaryValue,
        final double secondaryValue) {
      if (!(plot.getRangeAxis(primaryAxisIndex) instanceof NumberAxis primaryAxis)
          || !(plot.getRangeAxis(secondaryAxisIndex) instanceof NumberAxis secondaryAxis)) {
        return;
      }
      final SelectedPointAxisSynchronizer synchronizer = new SelectedPointAxisSynchronizer(
          primaryAxis, secondaryAxis, primaryValue, secondaryValue);
      synchronizer.install();
      synchronizer.syncSecondaryToPrimary();
    }

    private static final class SelectedPointAxisSynchronizer {

      private final @NotNull NumberAxis primaryAxis;
      private final @NotNull NumberAxis secondaryAxis;
      private final double primaryValue;
      private final double secondaryValue;
      private final @NotNull AxisChangeListener primaryListener;
      private final @NotNull AxisChangeListener secondaryListener;
      private boolean updating;

      private SelectedPointAxisSynchronizer(final @NotNull NumberAxis primaryAxis,
          final @NotNull NumberAxis secondaryAxis, final double primaryValue,
          final double secondaryValue) {
        this.primaryAxis = primaryAxis;
        this.secondaryAxis = secondaryAxis;
        this.primaryValue = primaryValue;
        this.secondaryValue = secondaryValue;
        primaryListener = this::onPrimaryAxisChanged;
        secondaryListener = this::onSecondaryAxisChanged;
      }

      private void install() {
        primaryAxis.addChangeListener(primaryListener);
        secondaryAxis.addChangeListener(secondaryListener);
      }

      private void onPrimaryAxisChanged(final @NotNull AxisChangeEvent event) {
        syncSecondaryToPrimary();
      }

      private void onSecondaryAxisChanged(final @NotNull AxisChangeEvent event) {
        syncPrimaryToSecondary();
      }

      private void syncSecondaryToPrimary() {
        syncAxis(primaryAxis, secondaryAxis, primaryValue, secondaryValue);
      }

      private void syncPrimaryToSecondary() {
        syncAxis(secondaryAxis, primaryAxis, secondaryValue, primaryValue);
      }

      private void syncAxis(final @NotNull NumberAxis sourceAxis,
          final @NotNull NumberAxis targetAxis, final double sourceValue,
          final double targetValue) {
        if (updating || !Double.isFinite(sourceValue) || !Double.isFinite(targetValue)) {
          return;
        }
        final org.jfree.data.Range sourceRange = sourceAxis.getRange();
        final org.jfree.data.Range targetRange = targetAxis.getRange();
        final double sourceSpan = sourceRange.getLength();
        final double targetSpan = targetRange.getLength();
        if (!Double.isFinite(sourceSpan) || !Double.isFinite(targetSpan) || sourceSpan <= 0d
            || targetSpan <= 0d) {
          return;
        }

        final double normalizedPosition = (sourceValue - sourceRange.getLowerBound()) / sourceSpan;
        if (!Double.isFinite(normalizedPosition)) {
          return;
        }
        final double clampedPosition = clampToUnit(normalizedPosition);
        final double targetLower = targetValue - clampedPosition * targetSpan;
        final double targetUpper = targetLower + targetSpan;
        updating = true;
        try {
          targetAxis.setRange(targetLower, targetUpper);
        } finally {
          updating = false;
        }
      }
    }

    private record TrendChartResult(@NotNull EChartViewer chart, double r2) {

    }

    private record CombinedTrendChartResult(@NotNull EChartViewer chart, double carbonsR2,
                                            double dbeR2, int carbonsAxisIndex, int dbeAxisIndex) {

    }

    private enum RetentionTrendMode {
      ECN_CARBON_TREND("ECN carbon trend"), DBE_TREND("DBE trend"), COMBINED_CARBON_DBE_TRENDS(
          "Combined C+DBE trends");

      private final @NotNull String label;

      RetentionTrendMode(final @NotNull String label) {
        this.label = label;
      }

      @Override
      public @NotNull String toString() {
        return label;
      }
    }

    private static final class RetentionTrendDataset extends org.jfree.data.xy.AbstractXYDataset {

      private final @NotNull double[] xValues;
      private final @NotNull double[] yValues;
      private final @NotNull MatchedLipid[] matchedLipids;

      private RetentionTrendDataset(final @NotNull List<FeatureListRow> rows,
          final @NotNull Predicate<MatchedLipid> predicate,
          final @NotNull java.util.function.ToDoubleFunction<MatchedLipid> yValueExtractor) {
        final List<Double> xList = new ArrayList<>();
        final List<Double> yList = new ArrayList<>();
        final List<MatchedLipid> lipidList = new ArrayList<>();
        for (final FeatureListRow row : rows) {
          final List<MatchedLipid> rowMatches = row.get(LipidMatchListType.class);
          if (rowMatches == null || rowMatches.isEmpty() || row.getAverageRT() == null) {
            continue;
          }
          final MatchedLipid match = rowMatches.getFirst();
          if (!predicate.test(match)) {
            continue;
          }
          final double y = yValueExtractor.applyAsDouble(match);
          if (!Double.isFinite(y)) {
            continue;
          }
          lipidList.add(match);
          xList.add((double) row.getAverageRT());
          yList.add(y);
        }
        xValues = xList.stream().mapToDouble(Double::doubleValue).toArray();
        yValues = yList.stream().mapToDouble(Double::doubleValue).toArray();
        matchedLipids = lipidList.toArray(new MatchedLipid[0]);
      }

      @Override
      public int getSeriesCount() {
        return 1;
      }

      @Override
      public Comparable<?> getSeriesKey(final int series) {
        return "Retention trend";
      }

      @Override
      public int getItemCount(final int series) {
        return xValues.length;
      }

      @Override
      public Number getX(final int series, final int item) {
        return xValues[item];
      }

      @Override
      public Number getY(final int series, final int item) {
        return yValues[item];
      }

      private @NotNull double[] getXValues() {
        return xValues;
      }

      private @Nullable MatchedLipid getMatchedLipid(final int item) {
        return item >= 0 && item < matchedLipids.length ? matchedLipids[item] : null;
      }

      private @Nullable String getTooltip(final int item) {
        final MatchedLipid lipid = getMatchedLipid(item);
        return lipid == null ? null : lipid.getLipidAnnotation().getAnnotation();
      }

      private @Nullable String getLabel(final int item) {
        final MatchedLipid lipid = getMatchedLipid(item);
        if (lipid == null) {
          return null;
        }
        final double yValue = yValues[item];
        for (int i = 0; i < yValues.length; i++) {
          if (i == item || yValues[i] != yValue) {
            continue;
          }
          final MatchedLipid other = getMatchedLipid(i);
          final double score = lipid.getMsMsScore() == null ? 0d : lipid.getMsMsScore();
          final double otherScore =
              other == null || other.getMsMsScore() == null ? 0d : other.getMsMsScore();
          if (score < otherScore) {
            return null;
          }
        }
        return lipid.getLipidAnnotation().getAnnotation();
      }
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

    private final @NotNull LipidAnnotationQCDashboardModel model;
    private final @NotNull DashboardFilterState filterState;
    private final @NotNull Label placeholder = new Label(
        "Select a feature list to build Kendrick plot.");
    private @Nullable ModularFeatureList featureList;
    private @Nullable KendrickMassPlotXYZDataset baseDataset;
    private @Nullable KendrickMassPlotChart chart;
    private @Nullable ColoredBubbleDatasetRenderer colorRenderer;
    private @Nullable ColoredBubbleDatasetRenderer filteredOutRenderer;
    private @Nullable FeatureListRow selectedRow;

    private KendrickPane(final @NotNull LipidAnnotationQCDashboardModel model,
        final @NotNull DashboardFilterState filterState) {
      this.model = model;
      this.filterState = filterState;
      setCenter(placeholder);
      BorderPane.setAlignment(placeholder, Pos.CENTER);
    }

    void setRow(final @Nullable FeatureListRow row) {
      selectedRow = row;
      updateSelectionOverlay();
    }

    void setFeatureList(final @NotNull ModularFeatureList featureList) {
      if (this.featureList == featureList && chart != null && baseDataset != null) {
        applyFilters();
        return;
      }

      if (baseDataset != null && baseDataset.getStatus() == TaskStatus.PROCESSING) {
        baseDataset.cancel();
      }
      discardChart();
      this.featureList = featureList;
      if (featureList.getNumberOfRows() == 0) {
        showPlaceholder("Feature list has no rows.");
        return;
      }

      showPlaceholder("Loading Kendrick mass plot...");
      final KendrickMassPlotParameters params = (KendrickMassPlotParameters) new KendrickMassPlotParameters().cloneParameterSet();
      params.setParameter(KendrickMassPlotParameters.featureList,
          new FeatureListsSelection(featureList));
      params.setParameter(KendrickMassPlotParameters.xAxisValues, KendrickPlotDataTypes.MZ);
      params.setParameter(KendrickMassPlotParameters.yAxisValues,
          KendrickPlotDataTypes.KENDRICK_MASS_DEFECT);
      params.setParameter(KendrickMassPlotParameters.yAxisCustomKendrickMassBase, "CH2");
      params.setParameter(KendrickMassPlotParameters.colorScaleValues,
          KendrickPlotDataTypes.RETENTION_TIME);
      params.setParameter(KendrickMassPlotParameters.bubbleSizeValues,
          KendrickPlotDataTypes.INTENSITY);

      final KendrickMassPlotXYZDataset dataset = new KendrickMassPlotXYZDataset(params, 1, 1);
      baseDataset = dataset;
      dataset.addTaskStatusListener((_, newStatus, _) -> {
        if (dataset != baseDataset) {
          return;
        }
        if (newStatus == TaskStatus.FINISHED) {
          Platform.runLater(() -> buildChart(dataset));
        } else if (newStatus == TaskStatus.ERROR || newStatus == TaskStatus.CANCELED) {
          Platform.runLater(() -> showPlaceholder("Kendrick mass plot could not be created."));
        }
      });
    }

    void applyFilters() {
      if (chart == null || baseDataset == null || colorRenderer == null
          || filteredOutRenderer == null) {
        return;
      }
      final XYPlot plot = chart.getChart().getXYPlot();
      final boolean filterActive = !filterState.barSelectedRowIds.isEmpty();
      final KendrickSubsetDataset inDataset = new KendrickSubsetDataset(baseDataset,
          this::rowVisible);
      final PaintScale filteredColorScale = createColorPaintScale(inDataset);
      colorRenderer.setPaintScale(filteredColorScale);
      updateColorScaleLegend(filteredColorScale);

      if (filterActive) {
        final KendrickSubsetDataset outDataset = new KendrickSubsetDataset(baseDataset,
            row -> !rowVisible(row));
        filteredOutRenderer.setPaintScale(createGrayPaintScale(outDataset));
        plot.setDataset(0, outDataset);
        plot.setRenderer(0, filteredOutRenderer);
        plot.setDataset(1, inDataset);
        plot.setRenderer(1, colorRenderer);
      } else {
        plot.setDataset(0, inDataset);
        plot.setRenderer(0, colorRenderer);
        plot.setDataset(1, null);
        plot.setRenderer(1, null);
      }

      updateOutlierOverlay(inDataset);
      updateSelectionOverlay();
      optimizeVisibleKendrickAxes(plot);
      chart.getChart().fireChartChanged();
    }

    private void updateColorScaleLegend(final @NotNull PaintScale scale) {
      if (chart == null) {
        return;
      }
      final JFreeChart jChart = chart.getChart();
      Title existingLegend = null;
      for (int i = 0; i < jChart.getSubtitleCount(); i++) {
        final Title subtitle = jChart.getSubtitle(i);
        if (subtitle instanceof PaintScaleLegend) {
          existingLegend = subtitle;
          break;
        }
      }
      if (existingLegend != null) {
        jChart.removeSubtitle(existingLegend);
      }

      final XYPlot xyPlot = jChart.getXYPlot();
      final NumberAxis scaleAxis = new NumberAxis(null);
      scaleAxis.setRange(scale.getLowerBound(),
          Math.max(scale.getUpperBound(), scale.getLowerBound()));
      final Paint axisPaint = xyPlot.getDomainAxis().getAxisLinePaint();
      scaleAxis.setAxisLinePaint(axisPaint);
      scaleAxis.setTickMarkPaint(axisPaint);
      scaleAxis.setNumberFormatOverride(new DecimalFormat("0.#"));
      scaleAxis.setLabelFont(xyPlot.getDomainAxis().getLabelFont());
      scaleAxis.setLabelPaint(axisPaint);
      scaleAxis.setTickLabelFont(xyPlot.getDomainAxis().getTickLabelFont());
      scaleAxis.setTickLabelPaint(axisPaint);
      scaleAxis.setLabel("Retention time");

      final PaintScaleLegend legend = new PaintScaleLegend(scale, scaleAxis);
      legend.setPadding(5, 0, 5, 0);
      legend.setStripOutlineVisible(false);
      legend.setAxisLocation(AxisLocation.BOTTOM_OR_LEFT);
      legend.setAxisOffset(5.0);
      legend.setSubdivisionCount(500);
      legend.setPosition(RectangleEdge.RIGHT);
      legend.setBackgroundPaint(new Color(0, 0, 0, 0));
      jChart.addSubtitle(legend);
    }

    private void buildChart(final @NotNull KendrickMassPlotXYZDataset dataset) {
      final KendrickMassPlotChart newChart = new KendrickMassPlotChart("", "m/z",
          "Kendrick mass defect (CH2)", "Retention time", dataset);
      newChart.getChart().setTitle((String) null);
      final XYPlot plot = newChart.getChart().getXYPlot();
      plot.setDomainCrosshairVisible(false);
      plot.setRangeCrosshairVisible(false);
      plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);
      if (plot.getDomainAxis() instanceof NumberAxis domainAxis) {
        domainAxis.setAutoRangeIncludesZero(false);
        domainAxis.setAutoRangeStickyZero(false);
      }
      if (plot.getRangeAxis() instanceof NumberAxis rangeAxis) {
        rangeAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setAutoRangeStickyZero(false);
      }

      final var baseRenderer = plot.getRenderer();
      final var tooltipGenerator =
          baseRenderer != null ? baseRenderer.getDefaultToolTipGenerator() : null;
      final PaintScale colorScale =
          baseRenderer instanceof ColoredBubbleDatasetRenderer colored ? colored.getPaintScale()
              : new LookupPaintScale(0d, 1d, Color.GRAY);

      colorRenderer = new AlphaBubbleRenderer(1f);
      colorRenderer.setPaintScale(colorScale);
      if (tooltipGenerator != null) {
        colorRenderer.setDefaultToolTipGenerator(tooltipGenerator);
      }

      filteredOutRenderer = new AlphaBubbleRenderer(0.35f);
      filteredOutRenderer.setPaintScale(new LookupPaintScale(0d, 1d, Color.GRAY));
      if (tooltipGenerator != null) {
        filteredOutRenderer.setDefaultToolTipGenerator(tooltipGenerator);
      }

      newChart.addChartMouseListener(new ChartMouseListenerFX() {
        @Override
        public void chartMouseClicked(final ChartMouseEventFX event) {
          if (event.getTrigger() == null || !event.getTrigger().isStillSincePress()
              || event.getTrigger().getButton() != MouseButton.PRIMARY) {
            return;
          }
          if (!(event.getEntity() instanceof XYItemEntity entity)) {
            return;
          }
          final FeatureListRow clickedRow = resolveClickedRow(entity.getDataset(),
              entity.getItem());
          if (clickedRow == null || Objects.equals(clickedRow, model.getRow())) {
            return;
          }
          model.setRow(clickedRow);
          FeatureTableFXUtil.selectAndScrollTo(clickedRow, model.getFeatureTableFx());
        }

        @Override
        public void chartMouseMoved(final ChartMouseEventFX event) {
        }
      });

      chart = newChart;
      setCenter(newChart);
      applyFilters();
    }

    private static void optimizeVisibleKendrickAxes(final @NotNull XYPlot plot) {
      final AxisExtrema xExtrema = collectVisibleExtrema(plot, true);
      final AxisExtrema yExtrema = collectVisibleExtrema(plot, false);
      if (plot.getDomainAxis() instanceof NumberAxis domainAxis && xExtrema.available()) {
        applyAxisExtrema(domainAxis, xExtrema);
      }
      if (plot.getRangeAxis() instanceof NumberAxis rangeAxis && yExtrema.available()) {
        applyAxisExtrema(rangeAxis, yExtrema);
      }
    }

    private static @NotNull AxisExtrema collectVisibleExtrema(final @NotNull XYPlot plot,
        final boolean xAxis) {
      double min = Double.POSITIVE_INFINITY;
      double max = Double.NEGATIVE_INFINITY;
      for (int datasetIndex = 0; datasetIndex <= 1; datasetIndex++) {
        final XYDataset dataset = plot.getDataset(datasetIndex);
        if (dataset == null || dataset.getSeriesCount() == 0) {
          continue;
        }
        for (int series = 0; series < dataset.getSeriesCount(); series++) {
          final int itemCount = dataset.getItemCount(series);
          for (int item = 0; item < itemCount; item++) {
            final double value =
                xAxis ? dataset.getXValue(series, item) : dataset.getYValue(series, item);
            if (!Double.isFinite(value)) {
              continue;
            }
            min = Math.min(min, value);
            max = Math.max(max, value);
          }
        }
      }
      return new AxisExtrema(min, max, Double.isFinite(min) && Double.isFinite(max));
    }

    private static void applyAxisExtrema(final @NotNull NumberAxis axis,
        final @NotNull AxisExtrema extrema) {
      if (!extrema.available()) {
        return;
      }
      axis.setAutoRange(false);
      axis.setAutoRangeIncludesZero(false);
      axis.setAutoRangeStickyZero(false);
      if (extrema.max() <= extrema.min()) {
        final double delta = Math.max(Math.abs(extrema.min()) * 0.02d, 0.05d);
        axis.setRange(extrema.min() - delta, extrema.max() + delta);
        return;
      }
      final double span = extrema.max() - extrema.min();
      final double padding = span * 0.02d;
      axis.setRange(extrema.min() - padding, extrema.max() + padding);
    }

    private record AxisExtrema(double min, double max, boolean available) {
    }

    private void updateSelectionOverlay() {
      if (chart == null || baseDataset == null) {
        return;
      }
      final XYPlot plot = chart.getChart().getXYPlot();
      plot.setDataset(4, null);
      plot.setRenderer(4, null);
      if (selectedRow == null) {
        return;
      }

      int selectedIndex = -1;
      for (int i = 0; i < baseDataset.getItemCount(0); i++) {
        final FeatureListRow row = baseDataset.getItemObject(i);
        if (row != null && Objects.equals(row.getID(), selectedRow.getID())) {
          selectedIndex = i;
          break;
        }
      }
      if (selectedIndex < 0) {
        return;
      }

      final double x = baseDataset.getXValue(0, selectedIndex);
      final double y = baseDataset.getYValue(0, selectedIndex);
      if (!Double.isFinite(x) || !Double.isFinite(y)) {
        return;
      }
      final XYSeries selectedSeries = new XYSeries("Selected lipid");
      selectedSeries.add(x, y);
      final XYSeriesCollection selectedDataset = new XYSeriesCollection();
      selectedDataset.addSeries(selectedSeries);
      plot.setDataset(4, selectedDataset);
      plot.setRenderer(4, new SelectedOverlayRenderer(getSelectedLabel(selectedRow)));
    }

    private void updateOutlierOverlay(final @NotNull KendrickSubsetDataset visibleDataset) {
      if (chart == null) {
        return;
      }
      final XYPlot plot = chart.getChart().getXYPlot();
      plot.setDataset(3, null);
      plot.setRenderer(3, null);
      if (featureList == null || baseDataset == null || visibleDataset.getItemCount(0) == 0) {
        return;
      }

      final KendrickSubsetDataset outlierDataset = new KendrickSubsetDataset(baseDataset,
          row -> rowVisible(row) && rowOutlier(row));
      if (outlierDataset.getItemCount(0) == 0) {
        return;
      }

      final XYLineAndShapeRenderer outlierRenderer = new XYLineAndShapeRenderer(false, true);
      outlierRenderer.setSeriesPaint(0,
          ConfigService.getDefaultColorPalette().getNegativeColorAWT());
      outlierRenderer.setSeriesStroke(0, new java.awt.BasicStroke(1.9f));
      outlierRenderer.setSeriesShape(0, new Ellipse2D.Double(-5.5, -5.5, 11, 11));
      outlierRenderer.setDefaultShapesFilled(false);
      outlierRenderer.setSeriesVisibleInLegend(0, false);
      plot.setDataset(3, outlierDataset);
      plot.setRenderer(3, outlierRenderer);
    }

    private boolean rowVisible(final @NotNull FeatureListRow row) {
      return filterState.barSelectedRowIds.isEmpty() || filterState.barSelectedRowIds.contains(
          row.getID());
    }

    private boolean rowOutlier(final @NotNull FeatureListRow row) {
      if (featureList == null) {
        return false;
      }
      final List<MatchedLipid> matches = row.getLipidMatches();
      if (matches.isEmpty()) {
        return false;
      }
      final MatchedLipid match = matches.getFirst();
      final ElutionOrderMetrics elutionMetrics = computeElutionOrderMetrics(featureList, row,
          match);
      final double overall = computeOverallQualityScore(row, match, elutionMetrics.combinedScore());
      final boolean poorCarbonTrend = elutionMetrics.carbonsTrend().available()
          && elutionMetrics.carbonsTrend().score() < 0.55d;
      final boolean poorDbeTrend =
          elutionMetrics.dbeTrend().available() && elutionMetrics.dbeTrend().score() < 0.55d;
      return poorCarbonTrend || poorDbeTrend || elutionMetrics.combinedScore() < 0.55d
          || overall < 0.5d;
    }

    private static double computeOverallQualityScore(final @NotNull FeatureListRow row,
        final @NotNull MatchedLipid match, final double elutionOrderScore) {
      final double ms1Score = computeMs1Score(row, match);
      final double ms2Score = computeMs2Score(match);
      final double adductScore = computeAdductScore(row, match);
      final double isotopeScore = computeIsotopeScore(row, match);
      final InterferenceMetrics interferenceMetrics = computeInterferenceMetrics(row);
      final double interference = computeInterferenceScore(interferenceMetrics.totalPenaltyCount());
      return (ms1Score + ms2Score + adductScore + isotopeScore + elutionOrderScore + interference)
          / 6d;
    }

    private static double computeMs1Score(final @NotNull FeatureListRow row,
        final @NotNull MatchedLipid match) {
      final double exactMz = MatchedLipid.getExactMass(match);
      final double observedMz =
          match.getAccurateMz() != null ? match.getAccurateMz() : row.getAverageMZ();
      final double ppm = (observedMz - exactMz) / exactMz * 1e6;
      final double absPpm = Math.abs(ppm);
      if (!Double.isFinite(absPpm)) {
        return 0d;
      }
      return clampToUnit(1d - Math.min(absPpm, 5d) / 5d);
    }

    private static double computeMs2Score(final @NotNull MatchedLipid match) {
      final double explainedIntensity = match.getMsMsScore() == null ? 0d : match.getMsMsScore();
      return clampToUnit(explainedIntensity);
    }

    private static double computeAdductScore(final @NotNull FeatureListRow row,
        final @NotNull MatchedLipid match) {
      if (row.getBestIonIdentity() == null) {
        return 0d;
      }
      final String featureAdduct = normalizeAdduct(row.getBestIonIdentity().getAdduct());
      final String lipidAdduct = normalizeAdduct(match.getIonizationType().getAdductName());
      return featureAdduct.equals(lipidAdduct) ? 1d : 0d;
    }

    private static double computeIsotopeScore(final @NotNull FeatureListRow row,
        final @NotNull MatchedLipid match) {
      if (row.getBestIsotopePattern() == null || match.getIsotopePattern() == null) {
        return 0.35d;
      }
      return io.github.mzmine.modules.tools.isotopepatternscore.IsotopePatternScoreCalculator.getSimilarityScore(
          row.getBestIsotopePattern(), match.getIsotopePattern(),
          new io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance(0.003, 10d), 0d);
    }

    private static String normalizeAdduct(final @Nullable String adduct) {
      return adduct == null ? "" : adduct.replaceAll("\\s+", "").toLowerCase();
    }

    private static double computeEcnOrderScore(final @NotNull ModularFeatureList featureList,
        final @NotNull FeatureListRow row, final @NotNull MatchedLipid match) {
      return computeElutionOrderMetrics(featureList, row, match).combinedScore();
    }

    private static int extractDbe(final @NotNull ILipidAnnotation lipidAnnotation) {
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

    private static int extractCarbons(final @NotNull ILipidAnnotation lipidAnnotation) {
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

    private static double predictRtByLinearFit(final @NotNull List<double[]> points,
        final double x) {
      double sumX = 0d;
      double sumY = 0d;
      double sumXY = 0d;
      double sumXX = 0d;
      for (final double[] p : points) {
        sumX += p[0];
        sumY += p[1];
        sumXY += p[0] * p[1];
        sumXX += p[0] * p[0];
      }
      final int n = points.size();
      final double denom = n * sumXX - sumX * sumX;
      if (Math.abs(denom) < 1e-8d) {
        return sumY / n;
      }
      final double slope = (n * sumXY - sumX * sumY) / denom;
      final double intercept = (sumY - slope * sumX) / n;
      return intercept + slope * x;
    }

    private static double estimateResidualStd(final @NotNull List<double[]> points) {
      if (points.size() < 3) {
        return 0.1d;
      }
      final double meanX = points.stream().mapToDouble(p -> p[0]).average().orElse(0d);
      final double meanY = points.stream().mapToDouble(p -> p[1]).average().orElse(0d);
      double numerator = 0d;
      double denominator = 0d;
      for (final double[] p : points) {
        numerator += (p[0] - meanX) * (p[1] - meanY);
        denominator += (p[0] - meanX) * (p[0] - meanX);
      }
      final double slope = denominator == 0d ? 0d : numerator / denominator;
      final double intercept = meanY - slope * meanX;
      double ss = 0d;
      for (final double[] p : points) {
        final double residual = p[1] - (intercept + slope * p[0]);
        ss += residual * residual;
      }
      return Math.sqrt(ss / Math.max(1d, points.size() - 2d));
    }

    private static @Nullable FeatureListRow resolveClickedRow(final @NotNull XYDataset dataset,
        final int item) {
      if (dataset instanceof XYItemObjectProvider<?> provider) {
        final Object obj = provider.getItemObject(item);
        if (obj instanceof FeatureListRow row) {
          return row;
        }
      }
      return null;
    }

    private static @NotNull LookupPaintScale createGrayPaintScale(
        final @NotNull KendrickSubsetDataset dataset) {
      final int count = dataset.getItemCount(0);
      if (count == 0) {
        final LookupPaintScale fallback = new LookupPaintScale(0d, 1d, new Color(160, 160, 160));
        fallback.add(0d, new Color(215, 215, 215));
        fallback.add(1d, new Color(105, 105, 105));
        return fallback;
      }

      double min = Double.POSITIVE_INFINITY;
      double max = Double.NEGATIVE_INFINITY;
      for (int i = 0; i < count; i++) {
        final double z = dataset.getZValue(0, i);
        if (Double.isFinite(z)) {
          min = Math.min(min, z);
          max = Math.max(max, z);
        }
      }
      if (!Double.isFinite(min) || !Double.isFinite(max) || min == max) {
        min = 0d;
        max = 1d;
      }
      final LookupPaintScale grayScale = new LookupPaintScale(min, max, new Color(160, 160, 160));
      final int steps = 8;
      for (int i = 0; i < steps; i++) {
        final double value = min + (max - min) * i / (steps - 1d);
        final int shade = 215 - (int) Math.round(120d * i / (steps - 1d));
        grayScale.add(value, new Color(shade, shade, shade, 95));
      }
      return grayScale;
    }

    private static @NotNull PaintScale createColorPaintScale(
        final @NotNull KendrickSubsetDataset dataset) {
      final int count = dataset.getItemCount(0);
      if (count == 0) {
        return new LookupPaintScale(0d, 1d, Color.GRAY);
      }
      double min = Double.POSITIVE_INFINITY;
      double max = Double.NEGATIVE_INFINITY;
      for (int i = 0; i < count; i++) {
        final double z = dataset.getZValue(0, i);
        if (Double.isFinite(z)) {
          min = Math.min(min, z);
          max = Math.max(max, z);
        }
      }
      if (!Double.isFinite(min) || !Double.isFinite(max) || min == max) {
        min = 0d;
        max = 1d;
      }
      return MZmineCore.getConfiguration().getDefaultPaintScalePalette()
          .toPaintScale(PaintScaleTransform.LINEAR, Range.closed(min, max));
    }

    private static @NotNull Paint selectedLabelTextPaint() {
      return MZmineCore.getConfiguration().isDarkMode() ? Color.WHITE : Color.BLACK;
    }

    private static @NotNull Paint selectedLabelBackgroundPaint() {
      return MZmineCore.getConfiguration().isDarkMode() ? new Color(0, 0, 0, 160)
          : new Color(255, 255, 255, 175);
    }

    private static @NotNull String getSelectedLabel(final @NotNull FeatureListRow row) {
      final List<MatchedLipid> matches = row.get(LipidMatchListType.class);
      if (matches == null || matches.isEmpty()) {
        return "Row " + row.getID();
      }
      final String annotation = matches.getFirst().getLipidAnnotation().getAnnotation();
      return annotation.length() > 52 ? annotation.substring(0, 49) + "..." : annotation;
    }

    private static final class AlphaBubbleRenderer extends ColoredBubbleDatasetRenderer {

      private final float alpha;

      private AlphaBubbleRenderer(final float alpha) {
        this.alpha = Math.max(0f, Math.min(1f, alpha));
      }

      @Override
      public void drawItem(final Graphics2D g2, final XYItemRendererState state,
          final Rectangle2D dataArea, final PlotRenderingInfo info, final XYPlot plot,
          final ValueAxis domainAxis, final ValueAxis rangeAxis, final XYDataset dataset,
          final int series, final int item, final CrosshairState crosshairState, final int pass) {
        final var oldComposite = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        try {
          super.drawItem(g2, state, dataArea, info, plot, domainAxis, rangeAxis, dataset, series,
              item, crosshairState, pass);
        } finally {
          g2.setComposite(oldComposite);
        }
      }
    }

    private static final class SelectedOverlayRenderer extends XYLineAndShapeRenderer {

      private final @NotNull String label;

      private SelectedOverlayRenderer(final @NotNull String label) {
        super(false, false);
        this.label = label;
      }

      @Override
      public void drawItem(final Graphics2D g2, final XYItemRendererState state,
          final Rectangle2D dataArea, final PlotRenderingInfo info, final XYPlot plot,
          final ValueAxis domainAxis, final ValueAxis rangeAxis, final XYDataset dataset,
          final int series, final int item, final CrosshairState crosshairState, final int pass) {
        final double x = dataset.getXValue(series, item);
        final double y = dataset.getYValue(series, item);
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
          return;
        }

        final double tx = domainAxis.valueToJava2D(x, dataArea, plot.getDomainAxisEdge());
        final double ty = rangeAxis.valueToJava2D(y, dataArea, plot.getRangeAxisEdge());
        final double radius = 9d;
        g2.setPaint(ConfigService.getDefaultColorPalette().getPositiveColorAWT());
        g2.setStroke(new java.awt.BasicStroke(2.2f));
        g2.draw(new Ellipse2D.Double(tx - radius, ty - radius, radius * 2d, radius * 2d));

        final Font labelFont = new Font("SansSerif", Font.BOLD, 11);
        g2.setFont(labelFont);
        final var fm = g2.getFontMetrics(labelFont);
        final int textW = fm.stringWidth(label);
        final int textH = fm.getHeight();
        final double xPad = 6d;
        final double yPad = 4d;
        double lx = tx + 10d;
        double ly = ty - 10d;
        if (lx + textW + 2 * xPad > dataArea.getMaxX()) {
          lx = tx - (textW + 2 * xPad + 10d);
        }
        if (lx < dataArea.getMinX()) {
          lx = dataArea.getMinX() + 2d;
        }
        if (ly - textH < dataArea.getMinY()) {
          ly = ty + textH + 6d;
        }
        if (ly > dataArea.getMaxY()) {
          ly = dataArea.getMaxY() - 2d;
        }

        final Rectangle2D.Double bg = new Rectangle2D.Double(lx, ly - textH, textW + 2 * xPad,
            textH + 2 * yPad - 2d);
        g2.setPaint(selectedLabelBackgroundPaint());
        g2.fill(bg);
        g2.setPaint(ConfigService.getDefaultColorPalette().getPositiveColorAWT());
        g2.setStroke(new java.awt.BasicStroke(1.2f));
        g2.draw(bg);
        g2.setPaint(selectedLabelTextPaint());
        g2.drawString(label, (float) (lx + xPad), (float) (ly + yPad - 2d));
      }
    }

    private void discardChart() {
      if (chart == null) {
        baseDataset = null;
        return;
      }
      final XYPlot plot = chart.getChart().getXYPlot();
      for (int i = 0; i < Math.max(5, plot.getDatasetCount()); i++) {
        plot.setDataset(i, null);
        plot.setRenderer(i, null);
      }
      setCenter(placeholder);
      chart = null;
      baseDataset = null;
    }

    private void showPlaceholder(final @NotNull String text) {
      placeholder.setText(text);
      setCenter(placeholder);
    }
  }

  private static class LipidSummaryPane extends BorderPane {

    private static final Pattern SUBCLASS_TOKEN_PATTERN = Pattern.compile(
        "^([A-Za-z][A-Za-z0-9-]*(?:\\s+[OP]-)?)");
    private final LipidAnnotationQCDashboardModel model;
    private final Label placeholder = new Label("Select a feature list with lipid annotations.");
    private final DashboardFilterState filterState;
    private final ComboBox<SummaryGroup> groupSelector = new ComboBox<>(
        FXCollections.observableArrayList(SummaryGroup.values()));
    private final ComboBox<SummaryCountMode> countModeSelector = new ComboBox<>(
        FXCollections.observableArrayList(SummaryCountMode.values()));
    private final Button clearFilterButton = new Button("Clear filter");
    private @Nullable ModularFeatureList featureList;
    private @Nullable String selectedGroup;
    private final Map<String, Set<Integer>> groupToRowIds = new TreeMap<>();

    private LipidSummaryPane(final @NotNull LipidAnnotationQCDashboardModel model,
        final @NotNull DashboardFilterState filterState,
        final @NotNull ComboBox<PreferredLipidLevelOption> preferredLevelCombo) {
      this.model = model;
      this.filterState = filterState;
      groupSelector.getSelectionModel().select(SummaryGroup.LIPID_SUBCLASS);
      countModeSelector.getSelectionModel().select(SummaryCountMode.ROW_COUNT);
      groupSelector.valueProperty().addListener((_, _, _) -> updateChart());
      countModeSelector.valueProperty().addListener((_, _, _) -> updateChart());
      clearFilterButton.setOnAction(_ -> {
        selectedGroup = null;
        filterState.barSelectedRowIds = Set.of();
        if (filterState.onChange != null) {
          filterState.onChange.run();
        }
        updateChart();
      });
      final HBox preferredLevelRow = new HBox(6, new Label("Preferred level:"),
          preferredLevelCombo);
      preferredLevelRow.setAlignment(Pos.CENTER_LEFT);
      final HBox groupByRow = new HBox(6, new Label("Group by:"), groupSelector);
      groupByRow.setAlignment(Pos.CENTER_LEFT);
      final HBox countModeRow = new HBox(6, new Label("Count mode:"), countModeSelector);
      countModeRow.setAlignment(Pos.CENTER_LEFT);
      final HBox actionRow = new HBox(6, clearFilterButton);
      actionRow.setAlignment(Pos.CENTER_LEFT);
      final VBox filterControls = new VBox(6, preferredLevelRow, groupByRow, countModeRow,
          actionRow);
      filterControls.setAlignment(Pos.TOP_LEFT);
      final TitledPane filterPane = new TitledPane("Summary filters", filterControls);
      filterPane.setCollapsible(true);
      final Accordion filterAccordion = new Accordion(filterPane);
      filterAccordion.setExpandedPane(null);
      setBottom(filterAccordion);
      model.getFeatureTableFx().sceneProperty().addListener((_, _, scene) -> {
        if (scene != null) {
          scene.getRoot().styleProperty().addListener((_, _, _) -> updateChart());
        }
      });
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
      final SummaryCountMode countMode = countModeSelector.getValue();
      final Map<String, Integer> groupToCount = new TreeMap<>();
      final Map<String, Set<String>> groupToUniqueAnnotations = new TreeMap<>();
      final Map<String, String> groupTooltip = new TreeMap<>();
      groupToRowIds.clear();
      int totalLipidRows = 0;
      for (FeatureListRow row : featureList.getRows()) {
        final List<MatchedLipid> matches = row.get(LipidMatchListType.class);
        if (matches == null || matches.isEmpty()) {
          continue;
        }
        totalLipidRows++;
        if (countMode == SummaryCountMode.ROW_COUNT) {
          final MatchedLipid lipid = matches.getFirst();
          final String groupName = grouping.extractGroupLabel(lipid, extractSubclassToken(lipid));
          groupTooltip.putIfAbsent(groupName,
              grouping.extractTooltip(lipid, extractSubclassToken(lipid)));
          groupToCount.merge(groupName, 1, Integer::sum);
          groupToRowIds.computeIfAbsent(groupName, _ -> new HashSet<>()).add(row.getID());
          continue;
        }

        final Set<String> rowMolecularAggregateKeys = new HashSet<>();
        for (final MatchedLipid lipid : matches) {
          if (lipid.getLipidAnnotation() instanceof MolecularSpeciesLevelAnnotation) {
            final String speciesKey = speciesAggregateKey(lipid);
            if (speciesKey != null) {
              rowMolecularAggregateKeys.add(speciesKey);
            }
          }
        }
        final Set<String> rowUniqueAnnotationKeys = new HashSet<>();
        for (final MatchedLipid lipid : matches) {
          final String groupName = grouping.extractGroupLabel(lipid, extractSubclassToken(lipid));
          groupTooltip.putIfAbsent(groupName,
              grouping.extractTooltip(lipid, extractSubclassToken(lipid)));
          groupToRowIds.computeIfAbsent(groupName, _ -> new HashSet<>()).add(row.getID());

          final @Nullable String uniqueAnnotationKey = uniqueAnnotationKey(lipid,
              rowMolecularAggregateKeys);
          if (uniqueAnnotationKey == null) {
            continue;
          }
          if (!rowUniqueAnnotationKeys.add(uniqueAnnotationKey)) {
            continue;
          }
          groupToUniqueAnnotations.computeIfAbsent(groupName, _ -> new HashSet<>())
              .add(uniqueAnnotationKey);
        }
      }
      if (countMode == SummaryCountMode.UNIQUE_ANNOTATIONS) {
        groupToUniqueAnnotations.forEach(
            (group, annotations) -> groupToCount.put(group, annotations.size()));
      }
      final DefaultCategoryDataset dataset = new DefaultCategoryDataset();
      groupToCount.forEach(
          (group, count) -> dataset.addValue(count, countMode.getSeriesLabel(), group));

      final JFreeChart chart = ChartFactory.createBarChart(null, grouping.getAxisLabel(),
          countMode.getRangeAxisLabel(), dataset, PlotOrientation.VERTICAL,
          false, true, false);
      chart.getCategoryPlot().getDomainAxis()
          .setCategoryLabelPositions(CategoryLabelPositions.UP_45);
      final EChartViewer viewer = new EChartViewer(chart);
      final SelectableBarRenderer selectable = new SelectableBarRenderer(selectedGroup);
      selectable.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator());
      selectable.setDefaultItemLabelsVisible(true);
      final Color textColor = summaryLabelColor();
      selectable.setDefaultItemLabelPaint(textColor);
      final double maxCount = Math.max(1d,
          groupToCount.values().stream().mapToInt(Integer::intValue).max().orElse(1));
      chart.getCategoryPlot().getRangeAxis().setUpperMargin(0.24d);
      if (dataset.getColumnCount() > 0) {
        final Comparable<?> rightCategory = dataset.getColumnKey(dataset.getColumnCount() - 1);
        final int totalCount =
            countMode == SummaryCountMode.UNIQUE_ANNOTATIONS ? groupToUniqueAnnotations.values()
                .stream().flatMap(Set::stream).collect(java.util.stream.Collectors.toSet()).size()
                : totalLipidRows;
        final CategoryTextAnnotation totalAnnotation = new CategoryTextAnnotation(
            countMode.getTotalLabelPrefix() + totalCount, rightCategory, maxCount * 1.12d);
        totalAnnotation.setCategoryAnchor(CategoryAnchor.END);
        totalAnnotation.setTextAnchor(TextAnchor.CENTER_RIGHT);
        totalAnnotation.setFont(new Font("SansSerif", Font.BOLD, 12));
        totalAnnotation.setPaint(textColor);
        chart.getCategoryPlot().addAnnotation(totalAnnotation);
      }
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
      ConfigService.getConfiguration().getDefaultChartTheme().apply(viewer);
      if (chart.getTitle() != null) {
        chart.getTitle().setPaint(textColor);
      }
      chart.getCategoryPlot().getDomainAxis().setLabelPaint(textColor);
      chart.getCategoryPlot().getDomainAxis().setTickLabelPaint(textColor);
      chart.getCategoryPlot().getRangeAxis().setLabelPaint(textColor);
      chart.getCategoryPlot().getRangeAxis().setTickLabelPaint(textColor);
      chart.getCategoryPlot().setRenderer(selectable);
      setCenter(viewer);
    }

    private static @NotNull Color summaryLabelColor() {
      return MZmineCore.getConfiguration().isDarkMode() ? new Color(230, 230, 230)
          : new Color(35, 35, 35);
    }

    private static String extractSubclassToken(@NotNull MatchedLipid lipid) {
      final String annotation = lipid.getLipidAnnotation().getAnnotation();
      if (annotation == null || annotation.isBlank()) {
        return lipid.getLipidAnnotation().getLipidClass().getAbbr();
      }
      final Matcher matcher = SUBCLASS_TOKEN_PATTERN.matcher(annotation);
      if (matcher.find()) {
        return matcher.group(1).trim();
      }
      final String[] parts = annotation.trim().split("\\s+", 2);
      if (parts.length > 0 && !parts[0].isBlank()) {
        return parts[0];
      }
      return lipid.getLipidAnnotation().getLipidClass().getAbbr();
    }

    private static @Nullable String uniqueAnnotationKey(final @NotNull MatchedLipid lipid,
        final @NotNull Set<String> rowMolecularAggregateKeys) {
      final String annotation = lipid.getLipidAnnotation().getAnnotation();
      final String speciesAggregateKey = speciesAggregateKey(lipid);
      if (lipid.getLipidAnnotation() instanceof SpeciesLevelAnnotation) {
        if (speciesAggregateKey != null && rowMolecularAggregateKeys.contains(
            speciesAggregateKey)) {
          return null;
        }
        return speciesAggregateKey == null ? "S|" + annotation : "S|" + speciesAggregateKey;
      }
      if (lipid.getLipidAnnotation() instanceof MolecularSpeciesLevelAnnotation) {
        return "M|" + annotation;
      }
      return "A|" + annotation;
    }

    private static @Nullable String speciesAggregateKey(final @NotNull MatchedLipid lipid) {
      final var carbonsDbe = MSMSLipidTools.getCarbonandDBEFromLipidAnnotaitonString(
          lipid.getLipidAnnotation().getAnnotation());
      final int carbons = carbonsDbe.getKey();
      final int dbe = carbonsDbe.getValue();
      if (carbons < 0 || dbe < 0) {
        return null;
      }
      return lipid.getLipidAnnotation().getLipidClass().getName() + "|" + carbons + ":" + dbe;
    }

    private void showPlaceholder(String text) {
      placeholder.setText(text);
      setCenter(placeholder);
    }

    private static final class SelectableBarRenderer extends BarRenderer {

      private final @Nullable String selectedGroup;
      private final Color selected = ConfigService.getDefaultColorPalette().getPositiveColorAWT();
      private final Color normal = ConfigService.getDefaultColorPalette().getMainColorAWT();

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

  private enum SummaryCountMode {
    ROW_COUNT("Rows", "Number of lipid annotations", "Lipid annotations",
        "Total lipids: "), UNIQUE_ANNOTATIONS("Unique annotations",
        "Number of unique lipid annotations", "Unique lipid annotations",
        "Total unique annotations: ");

    private final @NotNull String label;
    private final @NotNull String rangeAxisLabel;
    private final @NotNull String seriesLabel;
    private final @NotNull String totalLabelPrefix;

    SummaryCountMode(final @NotNull String label, final @NotNull String rangeAxisLabel,
        final @NotNull String seriesLabel, final @NotNull String totalLabelPrefix) {
      this.label = label;
      this.rangeAxisLabel = rangeAxisLabel;
      this.seriesLabel = seriesLabel;
      this.totalLabelPrefix = totalLabelPrefix;
    }

    private @NotNull String getRangeAxisLabel() {
      return rangeAxisLabel;
    }

    private @NotNull String getSeriesLabel() {
      return seriesLabel;
    }

    private @NotNull String getTotalLabelPrefix() {
      return totalLabelPrefix;
    }

    @Override
    public @NotNull String toString() {
      return label;
    }
  }

  private static class AnnotationQualityPane extends BorderPane {

    private final LipidAnnotationQCDashboardModel model;
    private final javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(8);
    private final javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane();
    private final Label placeholder = new Label("Select a row with lipid annotations.");
    private final Button removeMultiRowAnnotationsButton = new Button(
        "Remove multi-row annotations");
    private final Button setBestPerRowButton = new Button(
        "Set all rows to highest-score annotation");
    private @Nullable Runnable onAnnotationsChanged;
    private @Nullable FeatureListRow row;
    private @Nullable ModularFeatureList featureList;

    private AnnotationQualityPane(LipidAnnotationQCDashboardModel model) {
      this.model = model;
      scrollPane.setFitToWidth(true);
      scrollPane.setContent(content);
      removeMultiRowAnnotationsButton.setOnAction(_ -> removeMultiRowAnnotations());
      setBestPerRowButton.setOnAction(_ -> setHighestScoreAnnotationOnAllRows());
      final VBox actionBox = new VBox(6, removeMultiRowAnnotationsButton, setBestPerRowButton);
      actionBox.setAlignment(Pos.TOP_LEFT);
      final TitledPane actionsPane = new TitledPane("Annotation actions", actionBox);
      actionsPane.setCollapsible(true);
      final Accordion actionsAccordion = new Accordion(actionsPane);
      actionsAccordion.setExpandedPane(null);
      setBottom(actionsAccordion);
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

    void setOnAnnotationsChanged(final @Nullable Runnable onAnnotationsChanged) {
      this.onAnnotationsChanged = onAnnotationsChanged;
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
      content.setStyle("-fx-padding: 8;");
      final InterferenceMetrics interferenceMetrics = computeInterferenceMetrics(row);
      if (interferenceMetrics.totalPenaltyCount() > 0) {
        final Label warning = new Label(
            "Potential interference: " + interferenceDetail(interferenceMetrics));
        warning.setStyle(qualityWarningStyle());
        content.getChildren().add(warning);
      }
      final @Nullable Region duplicateRowsAlert = createDuplicateRowsAlert(matches);
      if (duplicateRowsAlert != null) {
        content.getChildren().add(duplicateRowsAlert);
      }

      for (MatchedLipid match : matches) {
        content.getChildren().add(createQualityCard(match, interferenceMetrics));
      }
      setCenter(scrollPane);
    }

    private Region createQualityCard(final @NotNull MatchedLipid match,
        final @NotNull InterferenceMetrics interferenceMetrics) {
      final javafx.scene.layout.VBox card = new javafx.scene.layout.VBox(6);
      card.setStyle(qualityCardStyle());

      final Label annotation = new Label(match.getLipidAnnotation().getAnnotation());
      annotation.setStyle("-fx-font-weight: bold;");

      final QualityMetric ms1 = evaluateMs1(match);
      final QualityMetric ms2 = evaluateMs2(match);
      final QualityMetric adduct = evaluateAdduct(match);
      final QualityMetric isotope = evaluateIsotope(match);
      final QualityMetric elutionOrder = evaluateElutionOrder(match);
      final QualityMetric interference = new QualityMetric(
          computeInterferenceScore(interferenceMetrics.totalPenaltyCount()),
          interferenceDetail(interferenceMetrics));
      final double overall =
          (ms1.score + ms2.score + adduct.score + isotope.score + elutionOrder.score
          + interference.score) / 6d;

      card.getChildren().add(annotation);
      card.getChildren().add(createMetricRow("Overall quality", overall,
          overall >= 0.75 ? "High confidence"
              : overall >= 0.5 ? "Moderate confidence" : "Low confidence"));
      card.getChildren().add(createMetricRow("MS1 mass accuracy", ms1.score, ms1.detail));
      card.getChildren().add(createMetricRow("MS2 diagnostics", ms2.score, ms2.detail));
      card.getChildren()
          .add(createMetricRow("Lipid Ion vs Ion Identity", adduct.score, adduct.detail));
      card.getChildren().add(createMetricRow("Isotope pattern", isotope.score, isotope.detail));
      card.getChildren()
          .add(createMetricRow("Elution order score", elutionOrder.score, elutionOrder.detail));
      card.getChildren()
          .add(createMetricRow("Interference risk", interference.score, interference.detail));
      return card;
    }

    private Region createMetricRow(String name, double score, String detail) {
      final double clipped = Math.max(0d, Math.min(1d, score));
      final javafx.scene.control.ProgressBar bar = new javafx.scene.control.ProgressBar(clipped);
      final javafx.scene.paint.Color scoreColor = qualityScoreColor(clipped);
      bar.setStyle("-fx-accent: " + toCssColor(scoreColor) + ";");
      bar.setMinWidth(230);
      bar.setPrefWidth(230);
      bar.setMaxWidth(230);
      final Label barLabel = new Label(name + "  " + String.format("%.0f%%", clipped * 100d));
      barLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
      barLabel.setMouseTransparent(true);
      final StackPane barPane = new StackPane(bar, barLabel);
      barPane.setAlignment(Pos.CENTER_LEFT);
      barPane.setMinWidth(230);
      barPane.setPrefWidth(230);
      barPane.setMaxWidth(230);

      final Label detailLabel = new Label(detail);
      detailLabel.setStyle("-fx-font-size: 11px;");
      detailLabel.setWrapText(true);
      return new VBox(3, barPane, detailLabel);
    }

    private @Nullable Region createDuplicateRowsAlert(final @NotNull List<MatchedLipid> matches) {
      if (matches.isEmpty()) {
        return null;
      }
      final MatchedLipid selectedAnnotation = matches.getFirst();
      final List<FeatureListRow> duplicateRows = getDuplicateRowsExcludingSelected(
          selectedAnnotation);
      if (duplicateRows.isEmpty()) {
        return null;
      }
      final javafx.scene.layout.FlowPane rowLinks = new javafx.scene.layout.FlowPane(4, 4);
      final Label title = new Label("Same annotation rows:");
      rowLinks.getChildren().add(title);
      for (final FeatureListRow duplicate : duplicateRows) {
        final javafx.scene.control.Hyperlink link = new javafx.scene.control.Hyperlink(
            "#" + duplicate.getID());
        link.setOnAction(_ -> {
          model.setRow(duplicate);
          FeatureTableFXUtil.selectAndScrollTo(duplicate, model.getFeatureTableFx());
        });
        rowLinks.getChildren().add(link);
      }
      final Button deleteSelectedRowButton = new Button("Delete selected");
      deleteSelectedRowButton.setOnAction(_ -> {
        if (row == null) {
          return;
        }
        removeAllAnnotationsFromRow(row);
        refreshAfterAnnotationDelete(row);
      });

      final Button deleteAllSameAnnotationRowsButton = new Button("Delete others");
      deleteAllSameAnnotationRowsButton.setOnAction(_ -> {
        final List<FeatureListRow> rowsToUpdate = getDuplicateRowsExcludingSelected(
            selectedAnnotation);
        for (final FeatureListRow sameAnnotationRow : rowsToUpdate) {
          removeAllAnnotationsFromRow(sameAnnotationRow);
        }
        refreshAfterAnnotationDelete(row);
      });

      final HBox actionButtons = new HBox(6, deleteSelectedRowButton,
          deleteAllSameAnnotationRowsButton);
      actionButtons.setAlignment(Pos.CENTER_LEFT);

      final VBox alertContainer = new VBox(6, rowLinks, actionButtons);
      alertContainer.setStyle(qualityWarningStyle());
      return alertContainer;
    }

    private @NotNull List<FeatureListRow> getDuplicateRowsExcludingSelected(
        final @NotNull MatchedLipid match) {
      if (featureList == null) {
        return List.of();
      }
      final String annotation = match.getLipidAnnotation().getAnnotation();
      final int selectedRowId = row != null ? row.getID() : -1;
      return featureList.getRows().stream().filter(r -> !r.getLipidMatches().isEmpty()).filter(
              r -> r.getLipidMatches().getFirst().getLipidAnnotation().getAnnotation()
                  .equals(annotation)).filter(r -> r.getID() != selectedRowId)
          .sorted((a, b) -> Integer.compare(a.getID(), b.getID()))
          .map(r -> (FeatureListRow) r).toList();
    }

    private static void removeAllAnnotationsFromRow(final @NotNull FeatureListRow targetRow) {
      targetRow.setLipidAnnotations(List.of());
    }

    private void removeMultiRowAnnotations() {
      if (featureList == null) {
        return;
      }
      final Map<FeatureListRow, Set<String>> removalPlan = buildMultiRowRemovalPlan(featureList);
      final int removals = countPlannedAnnotationRemovals(removalPlan);
      if (removals <= 0) {
        MZmineCore.getDesktop().displayMessage("Annotation actions",
            "No multi-row annotations would be removed.");
        return;
      }
      if (!confirmRemovalAction("Remove multi-row annotations", removals, removalPlan.size())) {
        return;
      }
      applyMultiRowRemovalPlan(removalPlan);
      refreshAfterAnnotationDelete(row);
    }

    private void setHighestScoreAnnotationOnAllRows() {
      if (featureList == null) {
        return;
      }
      final Map<FeatureListRow, MatchedLipid> bestMatchByRow = planBestAnnotationPerRow(featureList);
      final int changedRows = countPlannedSetBestUpdates(bestMatchByRow);
      if (changedRows <= 0) {
        MZmineCore.getDesktop().displayMessage("Annotation actions",
            "All rows already use the highest-score annotation as selected annotation.");
        return;
      }
      if (!confirmSetBestAction(changedRows)) {
        return;
      }
      for (final Map.Entry<FeatureListRow, MatchedLipid> entry : bestMatchByRow.entrySet()) {
        final FeatureListRow candidateRow = entry.getKey();
        final MatchedLipid bestMatch = entry.getValue();
        final List<MatchedLipid> reordered = new ArrayList<>(candidateRow.getLipidMatches());
        if (!reordered.remove(bestMatch)) {
          continue;
        }
        reordered.add(0, bestMatch);
        candidateRow.setLipidAnnotations(reordered);
      }
      refreshAfterAnnotationDelete(row);
    }

    private boolean confirmRemovalAction(final @NotNull String actionName, final int removals,
        final int affectedRows) {
      final String rowText = affectedRows == 1 ? "1 row" : affectedRows + " rows";
      final String message = actionName + " will remove " + removals
          + " lipid annotations across " + rowText + ". Continue?";
      final Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES,
          ButtonType.NO);
      alert.setTitle("Confirm annotation cleanup");
      alert.setHeaderText(actionName);
      final Optional<ButtonType> result = alert.showAndWait();
      return result.isPresent() && ButtonType.YES.equals(result.get());
    }

    private boolean confirmSetBestAction(final int changedRows) {
      final String rowText = changedRows == 1 ? "1 row" : changedRows + " rows";
      final String message = "Set highest-score annotation as selected annotation for " + rowText
          + ". No annotations will be removed. Continue?";
      final Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES,
          ButtonType.NO);
      alert.setTitle("Confirm selected-annotation update");
      alert.setHeaderText("Set all rows to highest-score annotation");
      final Optional<ButtonType> result = alert.showAndWait();
      return result.isPresent() && ButtonType.YES.equals(result.get());
    }

    private static @NotNull Map<FeatureListRow, Set<String>> buildMultiRowRemovalPlan(
        final @NotNull ModularFeatureList featureList) {
      final Map<String, List<RowAnnotationCandidate>> candidatesByAnnotation = new TreeMap<>();
      for (final FeatureListRow candidateRow : featureList.getRows()) {
        final List<MatchedLipid> rowMatches = candidateRow.getLipidMatches();
        if (rowMatches.isEmpty()) {
          continue;
        }
        for (final MatchedLipid match : rowMatches) {
          final String annotation = Objects.toString(match.getLipidAnnotation().getAnnotation(), "");
          if (annotation.isBlank()) {
            continue;
          }
          final double combinedScore = combinedAnnotationScore(featureList, candidateRow, match);
          candidatesByAnnotation.computeIfAbsent(annotation, _ -> new ArrayList<>())
              .add(new RowAnnotationCandidate(candidateRow, match, combinedScore));
        }
      }

      final Map<FeatureListRow, Set<String>> removalPlan = new LinkedHashMap<>();
      for (final Map.Entry<String, List<RowAnnotationCandidate>> entry : candidatesByAnnotation.entrySet()) {
        final List<RowAnnotationCandidate> candidates = entry.getValue();
        if (candidates.size() <= 1) {
          continue;
        }
        final RowAnnotationCandidate best = candidates.stream()
            .max((a, b) -> Double.compare(a.combinedScore(), b.combinedScore())).orElse(null);
        if (best == null) {
          continue;
        }
        final int bestRowId = best.row().getID();
        for (final RowAnnotationCandidate candidate : candidates) {
          if (candidate.row().getID() == bestRowId) {
            continue;
          }
          removalPlan.computeIfAbsent(candidate.row(), _ -> new HashSet<>()).add(entry.getKey());
        }
      }
      return removalPlan;
    }

    private static int countPlannedAnnotationRemovals(
        final @NotNull Map<FeatureListRow, Set<String>> removalPlan) {
      int removals = 0;
      for (final Map.Entry<FeatureListRow, Set<String>> entry : removalPlan.entrySet()) {
        final FeatureListRow targetRow = entry.getKey();
        final Set<String> annotations = entry.getValue();
        for (final String annotation : annotations) {
          removals += (int) targetRow.getLipidMatches().stream()
              .filter(m -> annotation.equals(m.getLipidAnnotation().getAnnotation())).count();
        }
      }
      return removals;
    }

    private static void applyMultiRowRemovalPlan(
        final @NotNull Map<FeatureListRow, Set<String>> removalPlan) {
      for (final Map.Entry<FeatureListRow, Set<String>> entry : removalPlan.entrySet()) {
        final Set<String> annotationsToRemove = entry.getValue();
        final List<MatchedLipid> remaining = entry.getKey().getLipidMatches().stream()
            .filter(m -> !annotationsToRemove.contains(m.getLipidAnnotation().getAnnotation()))
            .toList();
        entry.getKey().setLipidAnnotations(remaining);
      }
    }

    private static @NotNull Map<FeatureListRow, MatchedLipid> planBestAnnotationPerRow(
        final @NotNull ModularFeatureList featureList) {
      final Map<FeatureListRow, MatchedLipid> bestMatchByRow = new LinkedHashMap<>();
      for (final FeatureListRow candidateRow : featureList.getRows()) {
        final List<MatchedLipid> matches = candidateRow.getLipidMatches();
        if (matches.size() <= 1) {
          continue;
        }
        final MatchedLipid bestMatch = matches.stream()
            .max((a, b) -> Double.compare(combinedAnnotationScore(featureList, candidateRow, a),
                combinedAnnotationScore(featureList, candidateRow, b))).orElse(null);
        if (bestMatch != null) {
          bestMatchByRow.put(candidateRow, bestMatch);
        }
      }
      return bestMatchByRow;
    }

    private static int countPlannedSetBestUpdates(
        final @NotNull Map<FeatureListRow, MatchedLipid> bestMatchByRow) {
      int changes = 0;
      for (final Map.Entry<FeatureListRow, MatchedLipid> entry : bestMatchByRow.entrySet()) {
        final List<MatchedLipid> matches = entry.getKey().getLipidMatches();
        if (!matches.isEmpty() && !Objects.equals(matches.getFirst(), entry.getValue())) {
          changes++;
        }
      }
      return changes;
    }

    private record RowAnnotationCandidate(@NotNull FeatureListRow row, @NotNull MatchedLipid match,
                                          double combinedScore) {

    }

    private void refreshAfterAnnotationDelete(final @Nullable FeatureListRow preferredRow) {
      model.setFeatureList(model.getFeatureList());
      model.getFeatureTableFx().refresh();
      final FeatureListRow rowToSelect = preferredRow != null ? preferredRow : row;
      if (rowToSelect != null) {
        model.setRow(null);
        model.setRow(rowToSelect);
        FeatureTableFXUtil.selectAndScrollTo(rowToSelect, model.getFeatureTableFx());
      }
      if (onAnnotationsChanged != null) {
        onAnnotationsChanged.run();
      }
      update();
    }

    private QualityMetric evaluateMs1(MatchedLipid match) {
      final double exactMz = MatchedLipid.getExactMass(match);
      final double observedMz = match.getAccurateMz() != null ? match.getAccurateMz()
          : (row != null ? row.getAverageMZ() : exactMz);
      final double ppm = (observedMz - exactMz) / exactMz * 1e6;
      final double absPpm = Math.abs(ppm);
      final double score =
          Double.isFinite(absPpm) ? clampToUnit(1d - Math.min(absPpm, 5d) / 5d) : 0d;
      return new QualityMetric(score, String.format("%.2f ppm", ppm));
    }

    private QualityMetric evaluateMs2(MatchedLipid match) {
      final double explained = clampToUnit(
          match.getMsMsScore() == null ? 0d : match.getMsMsScore());
      return new QualityMetric(explained,
          String.format("%.1f", explained * 100d) + "% explained intensity");
    }

    private QualityMetric evaluateAdduct(MatchedLipid match) {
      if (row == null || row.getBestIonIdentity() == null) {
        return new QualityMetric(0d, "No ion identity available for cross-check");
      }
      final String featureAdduct = normalizeAdduct(row.getBestIonIdentity().getAdduct());
      final String lipidAdduct = normalizeAdduct(match.getIonizationType().getAdductName());
      final boolean matchFound = featureAdduct.equals(lipidAdduct);
      final String detail = "Feature: " + row.getBestIonIdentity().getAdduct() + " vs Lipid: "
          + match.getIonizationType().getAdductName();
      return new QualityMetric(matchFound ? 1d : 0d, detail);
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

    private QualityMetric evaluateElutionOrder(final @NotNull MatchedLipid match) {
      if (featureList == null || row == null || row.getAverageRT() == null) {
        return new QualityMetric(0.4, "Missing RT context");
      }
      final ElutionOrderMetrics metrics = computeElutionOrderMetrics(featureList, row, match);
      return new QualityMetric(metrics.combinedScore(), formatElutionOrderDetail(metrics));
    }

    private static @NotNull javafx.scene.paint.Color qualityScoreColor(final double score) {
      final SimpleColorPalette defaultPalette = ConfigService.getDefaultColorPalette();
      final io.github.mzmine.gui.chartbasics.chartutils.paintscales.PaintScale scoreScale = new SimpleColorPalette(
          defaultPalette.getNegativeColor(), defaultPalette.getNeutralColor(),
          defaultPalette.getPositiveColor()).toPaintScale(PaintScaleTransform.LINEAR,
          Range.closed(0d, 1d));
      final Paint awtPaint = scoreScale.getPaint(clampToUnit(score));
      if (awtPaint instanceof Color awtColor) {
        return FxColorUtil.awtColorToFX(awtColor);
      }
      return javafx.scene.paint.Color.GRAY;
    }

    private static @NotNull String toCssColor(final @NotNull javafx.scene.paint.Color color) {
      final int red = (int) Math.round(color.getRed() * 255d);
      final int green = (int) Math.round(color.getGreen() * 255d);
      final int blue = (int) Math.round(color.getBlue() * 255d);
      return "rgba(%d,%d,%d,%.3f)".formatted(red, green, blue, color.getOpacity());
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

    private static String qualityWarningStyle() {
      final Color negative = ConfigService.getDefaultColorPalette().getNegativeColorAWT();
      final String borderColor = "rgb(%d,%d,%d)".formatted(negative.getRed(), negative.getGreen(),
          negative.getBlue());
      return MZmineCore.getConfiguration().isDarkMode() ?
          "-fx-background-color: transparent; -fx-border-color: " + borderColor
              + "; -fx-padding: 6;"
          : "-fx-background-color: #fff3cd; -fx-border-color: " + borderColor + "; -fx-padding: 6;";
    }

    private static String qualityCardStyle() {
      return MZmineCore.getConfiguration().isDarkMode()
          ? "-fx-background-color: transparent; -fx-border-color: #5b5b5b; -fx-padding: 8;"
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

  private record TrendScore(double score, double residualRt, double normalizedDelta,
                            boolean available) {

  }

  private record InterferenceMetrics(int classPenaltyCount, int sameClassAdductPenaltyCount) {

    private int totalPenaltyCount() {
      return Math.max(0, classPenaltyCount) + Math.max(0, sameClassAdductPenaltyCount);
    }
  }

  private record ElutionOrderMetrics(double combinedScore, @NotNull TrendScore carbonsTrend,
                                     @NotNull TrendScore dbeTrend) {

  }

  private static @NotNull ElutionOrderMetrics computeElutionOrderMetrics(
      final @NotNull ModularFeatureList featureList, final @NotNull FeatureListRow row,
      final @NotNull MatchedLipid match) {
    if (row.getAverageRT() == null) {
      final TrendScore missing = new TrendScore(0d, Double.NaN, Double.NaN, false);
      return new ElutionOrderMetrics(0d, missing, missing);
    }
    final int selectedCarbons = extractTrendCarbons(match.getLipidAnnotation());
    final int selectedDbe = extractTrendDbe(match.getLipidAnnotation());
    if (selectedCarbons < 0 || selectedDbe < 0) {
      final TrendScore missing = new TrendScore(0d, Double.NaN, Double.NaN, false);
      return new ElutionOrderMetrics(0d, missing, missing);
    }

    final ILipidClass selectedClass = match.getLipidAnnotation().getLipidClass();
    final double observedRt = row.getAverageRT();
    final double methodLength = computeRtMethodLength(featureList);

    final List<double[]> carbonTrendPoints = new ArrayList<>();
    final List<double[]> dbeTrendPoints = new ArrayList<>();
    for (final FeatureListRow other : featureList.getRows()) {
      final List<MatchedLipid> otherMatches = other.getLipidMatches();
      if (otherMatches.isEmpty() || other.getAverageRT() == null) {
        continue;
      }
      final MatchedLipid otherMatch = otherMatches.getFirst();
      if (!otherMatch.getLipidAnnotation().getLipidClass().equals(selectedClass)) {
        continue;
      }
      final int otherCarbons = extractTrendCarbons(otherMatch.getLipidAnnotation());
      final int otherDbe = extractTrendDbe(otherMatch.getLipidAnnotation());
      if (otherCarbons < 0 || otherDbe < 0) {
        continue;
      }
      if (otherDbe == selectedDbe) {
        carbonTrendPoints.add(new double[]{otherCarbons, other.getAverageRT()});
      }
      if (otherCarbons == selectedCarbons) {
        dbeTrendPoints.add(new double[]{otherDbe, other.getAverageRT()});
      }
    }

    final TrendScore carbonsTrend = computeTrendScore(carbonTrendPoints, selectedCarbons,
        observedRt, methodLength);
    final TrendScore dbeTrend = computeTrendScore(dbeTrendPoints, selectedDbe, observedRt,
        methodLength);
    final double combined = combineTrendScores(carbonsTrend, dbeTrend);
    return new ElutionOrderMetrics(combined, carbonsTrend, dbeTrend);
  }

  private static @NotNull TrendScore computeTrendScore(final @NotNull List<double[]> points,
      final double selectedPredictor, final double observedRt, final double methodLength) {
    if (points.size() < 3) {
      return new TrendScore(0d, Double.NaN, Double.NaN, false);
    }
    final double expectedRt = predictRtByLinearFitGlobal(points, selectedPredictor);
    final double residual = Math.abs(observedRt - expectedRt);
    final double normalizedDelta = normalizeRtDeltaByMethodLength(residual, methodLength);
    final double score = computeEcnRtScoreFromDelta(residual, methodLength);
    return new TrendScore(score, residual, normalizedDelta, true);
  }

  private static double combineTrendScores(final @NotNull TrendScore carbonsTrend,
      final @NotNull TrendScore dbeTrend) {
    if (carbonsTrend.available() && dbeTrend.available()) {
      return clampToUnit((carbonsTrend.score() + dbeTrend.score()) / 2d);
    }
    if (carbonsTrend.available()) {
      return Math.max(0.05d, clampToUnit(carbonsTrend.score() * 0.8d));
    }
    if (dbeTrend.available()) {
      return Math.max(0.05d, clampToUnit(dbeTrend.score() * 0.8d));
    }
    return 0d;
  }

  private static @NotNull String formatElutionOrderDetail(
      final @NotNull ElutionOrderMetrics metrics) {
    return "C trend: " + formatTrendDetail(metrics.carbonsTrend()) + " | DBE trend: "
        + formatTrendDetail(metrics.dbeTrend());
  }

  private static @NotNull String formatTrendDetail(final @NotNull TrendScore trendScore) {
    if (!trendScore.available()) {
      return "n/a";
    }
    if (Double.isFinite(trendScore.normalizedDelta())) {
      return String.format("%.0f%% (Δ=%.2f, %.1f%% method)", trendScore.score() * 100d,
          trendScore.residualRt(), trendScore.normalizedDelta() * 100d);
    }
    return String.format("%.0f%% (Δ=%.2f)", trendScore.score() * 100d, trendScore.residualRt());
  }

  private static double predictRtByLinearFitGlobal(final @NotNull List<double[]> points,
      final double predictorValue) {
    double sumX = 0d;
    double sumY = 0d;
    double sumXY = 0d;
    double sumXX = 0d;
    for (final double[] p : points) {
      sumX += p[0];
      sumY += p[1];
      sumXY += p[0] * p[1];
      sumXX += p[0] * p[0];
    }
    final int n = points.size();
    final double denom = n * sumXX - sumX * sumX;
    if (Math.abs(denom) < 1e-8d) {
      return sumY / n;
    }
    final double slope = (n * sumXY - sumX * sumY) / denom;
    final double intercept = (sumY - slope * sumX) / n;
    return intercept + slope * predictorValue;
  }

  private static int extractTrendDbe(final @NotNull ILipidAnnotation lipidAnnotation) {
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

  private static int extractTrendCarbons(final @NotNull ILipidAnnotation lipidAnnotation) {
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

  private static double clampToUnit(final double value) {
    return Math.max(0d, Math.min(1d, value));
  }

  private static double normalizeRtDeltaByMethodLength(final double deltaRt,
      final double methodLength) {
    if (!Double.isFinite(deltaRt) || !Double.isFinite(methodLength) || methodLength <= 0d) {
      return Double.NaN;
    }
    return Math.abs(deltaRt) / methodLength;
  }

  private static double computeEcnRtScoreFromDelta(final double deltaRt,
      final double methodLength) {
    final double normalizedDelta = normalizeRtDeltaByMethodLength(deltaRt, methodLength);
    if (!Double.isFinite(normalizedDelta)) {
      return 0.4d;
    }
    if (normalizedDelta <= RT_DELTA_NO_PENALTY_NORMALIZED) {
      return 1d;
    }
    final double penaltyRange = RT_DELTA_FULL_PENALTY_NORMALIZED - RT_DELTA_NO_PENALTY_NORMALIZED;
    if (penaltyRange <= 0d) {
      return clampToUnit(1d - normalizedDelta / RT_DELTA_FULL_PENALTY_NORMALIZED);
    }
    final double scaledPenalty = (normalizedDelta - RT_DELTA_NO_PENALTY_NORMALIZED) / penaltyRange;
    final double nonLinearPenalty = Math.pow(clampToUnit(scaledPenalty), RT_DELTA_PENALTY_EXPONENT);
    return clampToUnit(1d - nonLinearPenalty);
  }

  private static double computeInterferenceScore(final int interferenceCount) {
    return switch (Math.max(0, interferenceCount)) {
      case 0 -> 1d;
      case 1 -> 0.5d;
      default -> 0d;
    };
  }

  private static double combinedAnnotationScore(final @NotNull ModularFeatureList featureList,
      final @NotNull FeatureListRow row, final @NotNull MatchedLipid match) {
    final ElutionOrderMetrics elutionMetrics = computeElutionOrderMetrics(featureList, row, match);
    return KendrickPane.computeOverallQualityScore(row, match, elutionMetrics.combinedScore());
  }

  private static @NotNull InterferenceMetrics computeInterferenceMetrics(
      final @NotNull FeatureListRow row) {
    final List<MatchedLipid> matches = row.getLipidMatches();
    if (matches.isEmpty()) {
      return new InterferenceMetrics(0, 0);
    }

    final long uniqueClasses = matches.stream()
        .map(m -> m.getLipidAnnotation().getLipidClass().getName()).distinct().count();
    final int classPenaltyCount = (int) Math.max(0L, uniqueClasses - 1L);

    final Map<String, List<MatchedLipid>> byClass = new TreeMap<>();
    for (final MatchedLipid match : matches) {
      final String className = match.getLipidAnnotation().getLipidClass().getName();
      byClass.computeIfAbsent(className, _ -> new ArrayList<>()).add(match);
    }
    int sameClassAdductPenaltyCount = 0;
    for (final List<MatchedLipid> sameClassMatches : byClass.values()) {
      final long uniqueAnnotations = sameClassMatches.stream()
          .map(m -> m.getLipidAnnotation().getAnnotation()).filter(Objects::nonNull).distinct()
          .count();
      final long uniqueAdducts = sameClassMatches.stream().map(m -> normalizeAdductForInterference(
              m.getIonizationType() != null ? m.getIonizationType().getAdductName() : null))
          .filter(adduct -> !adduct.isBlank()).distinct().count();
      if (uniqueAnnotations > 1 && uniqueAdducts > 1) {
        sameClassAdductPenaltyCount++;
      }
    }
    return new InterferenceMetrics(classPenaltyCount, sameClassAdductPenaltyCount);
  }

  private static @NotNull String interferenceDetail(final @NotNull InterferenceMetrics metrics) {
    if (metrics.totalPenaltyCount() == 0) {
      return "No competing lipid classes or adduct-conflicting annotations in selected row.";
    }
    if (metrics.classPenaltyCount() > 0 && metrics.sameClassAdductPenaltyCount() > 0) {
      return "Multiple lipid classes and same-class annotations with different adducts are present.";
    }
    if (metrics.classPenaltyCount() > 0) {
      return metrics.classPenaltyCount() == 1 ? "One competing lipid class present in selected row."
          : "Multiple competing lipid classes present in selected row.";
    }
    return metrics.sameClassAdductPenaltyCount() == 1
        ? "Same lipid class has multiple annotations supported by different adducts."
        : "Multiple lipid classes show annotation ambiguity across different adducts.";
  }

  private static @NotNull String normalizeAdductForInterference(final @Nullable String adduct) {
    return adduct == null ? "" : adduct.replaceAll("\\s+", "").toLowerCase();
  }

  private static double computeRtMethodLength(final @NotNull ModularFeatureList featureList) {
    double minRt = Double.POSITIVE_INFINITY;
    double maxRt = Double.NEGATIVE_INFINITY;
    for (final FeatureListRow row : featureList.getRows()) {
      final Float rt = row.getAverageRT();
      if (rt == null || !Float.isFinite(rt)) {
        continue;
      }
      minRt = Math.min(minRt, rt);
      maxRt = Math.max(maxRt, rt);
    }
    if (!Double.isFinite(minRt) || !Double.isFinite(maxRt) || maxRt <= minRt) {
      return 0d;
    }
    return maxRt - minRt;
  }

  private static void applyPreferredLipidLevel(final @NotNull ModularFeatureList featureList,
      final @NotNull LipidAnnotationLevel level) {
    for (final FeatureListRow row : featureList.getRows()) {
      final List<MatchedLipid> matches = row.getLipidMatches();
      if (matches.isEmpty()) {
        continue;
      }
      final List<MatchedLipid> preferredLevel = matches.stream()
          .filter(match -> isMatchingLevel(match, level)).toList();
      if (preferredLevel.isEmpty()) {
        continue;
      }
      final List<MatchedLipid> reordered = new ArrayList<>(preferredLevel);
      reordered.addAll(matches.stream().filter(match -> !isMatchingLevel(match, level)).toList());
      row.setLipidAnnotations(reordered);
    }
  }

  private static boolean isMatchingLevel(final @NotNull MatchedLipid match,
      final @NotNull LipidAnnotationLevel level) {
    return switch (level) {
      case SPECIES_LEVEL -> match.getLipidAnnotation() instanceof SpeciesLevelAnnotation;
      case MOLECULAR_SPECIES_LEVEL ->
          match.getLipidAnnotation() instanceof MolecularSpeciesLevelAnnotation;
    };
  }

  private enum PreferredLipidLevelOption {
    SPECIES("Species level", LipidAnnotationLevel.SPECIES_LEVEL), MOLECULAR_SPECIES(
        "Molecular species level", LipidAnnotationLevel.MOLECULAR_SPECIES_LEVEL);

    private final @NotNull String label;
    private final @NotNull LipidAnnotationLevel level;

    PreferredLipidLevelOption(final @NotNull String label,
        final @NotNull LipidAnnotationLevel level) {
      this.label = label;
      this.level = level;
    }

    @Override
    public @NotNull String toString() {
      return label;
    }
  }

  private static final class DashboardFilterState {

    private Set<Integer> barSelectedRowIds = Set.of();
    private @Nullable Runnable onChange;
  }

  private static final class KendrickSubsetDataset extends
      org.jfree.data.xy.AbstractXYZDataset implements XYZBubbleDataset,
      XYItemObjectProvider<FeatureListRow>, ToolTipTextProvider {

    private final @NotNull FeatureListRow[] rows;
    private final @NotNull double[] x;
    private final @NotNull double[] y;
    private final @NotNull double[] z;
    private final @NotNull double[] bubble;

    private KendrickSubsetDataset(final @NotNull KendrickMassPlotXYZDataset source,
        final @NotNull Predicate<FeatureListRow> predicate) {
      final List<FeatureListRow> rowList = new ArrayList<>();
      final List<Double> xList = new ArrayList<>();
      final List<Double> yList = new ArrayList<>();
      final List<Double> zList = new ArrayList<>();
      final List<Double> bubbleList = new ArrayList<>();
      for (int i = 0; i < source.getItemCount(0); i++) {
        final FeatureListRow row = source.getItemObject(i);
        if (row == null || !predicate.test(row)) {
          continue;
        }
        rowList.add(row);
        xList.add(source.getXValue(0, i));
        yList.add(source.getYValue(0, i));
        zList.add(source.getZValue(0, i));
        bubbleList.add(source.getBubbleSizeValue(0, i));
      }
      rows = rowList.toArray(new FeatureListRow[0]);
      x = xList.stream().mapToDouble(Double::doubleValue).toArray();
      y = yList.stream().mapToDouble(Double::doubleValue).toArray();
      z = zList.stream().mapToDouble(Double::doubleValue).toArray();
      bubble = bubbleList.stream().mapToDouble(Double::doubleValue).toArray();
    }

    @Override
    public int getSeriesCount() {
      return 1;
    }

    @Override
    public Comparable<?> getSeriesKey(final int series) {
      return "Kendrick subset";
    }

    @Override
    public int getItemCount(final int series) {
      return rows.length;
    }

    @Override
    public Number getX(final int series, final int item) {
      return x[item];
    }

    @Override
    public Number getY(final int series, final int item) {
      return y[item];
    }

    @Override
    public Number getZ(final int series, final int item) {
      return z[item];
    }

    @Override
    public double getBubbleSizeValue(final int series, final int item) {
      return bubble[item];
    }

    @Override
    public double[] getBubbleSizeValues() {
      return bubble;
    }

    @Override
    public @Nullable FeatureListRow getItemObject(final int item) {
      return item >= 0 && item < rows.length ? rows[item] : null;
    }

    @Override
    public @Nullable String getToolTipText(final int itemIndex) {
      final FeatureListRow row = getItemObject(itemIndex);
      if (row == null) {
        return null;
      }
      final String annotation = row.getPreferredAnnotationName();
      return annotation == null ? "Feature list ID: " + row.getID()
          : "Feature list ID: " + row.getID() + "\n" + annotation;
    }
  }

}
