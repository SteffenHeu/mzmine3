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

package io.github.mzmine.modules.dataanalysis.qcdashboard;

import static java.util.Objects.requireNonNullElse;

import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.modules.visualization.projectmetadata.SampleType;
import io.github.mzmine.modules.visualization.projectmetadata.table.columns.MetadataColumn;
import java.util.List;
import java.util.Map;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shared model of the {@link QcDashboardController}. Holds all dashboard-wide state. Subplot models
 * bind to the relevant properties here (see {@link QcDashboardController} for the binding strategy).
 * <p>
 * Inputs are set by the controls / table; derived properties ({@link #orderedFiles},
 * {@link #fileColors}, {@link #qcFiles}) are computed by {@link QcDashboardInteractor} and are
 * read-only to the subplots.
 */
public class QcDashboardModel {

  // --- inputs / selection ---------------------------------------------------

  /**
   * The selected (aligned) feature lists. A list to allow binding via
   * {@link io.github.mzmine.gui.framework.fx.SelectedFeatureListsBinding}; the dashboard uses the
   * first aligned list.
   */
  private final ObjectProperty<List<FeatureList>> flists = new SimpleObjectProperty<>(List.of());

  /**
   * Selected rows. A list to allow binding via
   * {@link io.github.mzmine.gui.framework.fx.SelectedRowsBinding}; the single-row plots use the
   * first element.
   */
  private final ObjectProperty<List<FeatureListRow>> selectedRows = new SimpleObjectProperty<>(
      List.of());

  private final ObjectProperty<AbundanceMeasure> abundance = new SimpleObjectProperty<>(
      AbundanceMeasure.Height);

  /**
   * Optional metadata column used to group files into acquisition batches (for coloring). Doubles
   * as the {@link io.github.mzmine.gui.framework.fx.SelectedMetadataColumnBinding} grouping column.
   * Null = all files in one group.
   */
  private final ObjectProperty<@Nullable MetadataColumn<?>> batchColumn = new SimpleObjectProperty<>();

  /**
   * Sample types (as metadata strings, see {@link SampleType#toString()}) to display. Default: QC
   * only.
   */
  private final ObjectProperty<List<String>> sampleTypesToShow = new SimpleObjectProperty<>(
      List.of(SampleType.QC.toString()));

  // --- derived state (computed by the interactor, read-only to subplots) -----

  /**
   * The first aligned feature list of {@link #flists} (or null). Provided so subplots can iterate
   * rows without re-filtering the list.
   */
  private final ObjectProperty<@Nullable ModularFeatureList> featureList = new SimpleObjectProperty<>();

  /**
   * Files of the first aligned feature list, filtered to {@link #sampleTypesToShow} and sorted by
   * acquisition date (nulls last, then by name). The index in this list is the x-axis value of the
   * per-file plots.
   */
  private final ObjectProperty<List<RawDataFile>> orderedFiles = new SimpleObjectProperty<>(
      List.of());

  /**
   * Subset of {@link #orderedFiles} whose sample type is QC.
   */
  private final ObjectProperty<List<RawDataFile>> qcFiles = new SimpleObjectProperty<>(List.of());

  /**
   * File -> color mapping for this dashboard (batch shading or {@link RawDataFile#getColor()}).
   */
  private final ObjectProperty<Map<RawDataFile, Color>> fileColors = new SimpleObjectProperty<>(
      Map.of());

  // --- detection-count thresholds (shared with Plot 4 + filtering) ----------

  /** ">50% of MS1 features detected in all QCs" quality threshold (fraction of QC files). */
  private final DoubleProperty goodQualityFraction = new SimpleDoubleProperty(0.5);

  /** Dunn/Warwick recommendation: keep features detected in >70% of QCs (fraction of QC files). */
  private final DoubleProperty warwickFraction = new SimpleDoubleProperty(0.7);

  /** Global toggle for the mean ± SD overlay drawn on each per-file plot. */
  private final BooleanProperty showMeanSdInterval = new SimpleBooleanProperty(true);

  public boolean isShowMeanSdInterval() {
    return showMeanSdInterval.get();
  }

  public BooleanProperty showMeanSdIntervalProperty() {
    return showMeanSdInterval;
  }

  public @NotNull List<FeatureList> getFlists() {
    return requireNonNullElse(flists.get(), List.of());
  }

  public void setFlists(List<FeatureList> flists) {
    this.flists.set(flists);
  }

  public ObjectProperty<List<FeatureList>> flistsProperty() {
    return flists;
  }

  public @NotNull List<FeatureListRow> getSelectedRows() {
    return requireNonNullElse(selectedRows.get(), List.of());
  }

  public void setSelectedRows(List<FeatureListRow> selectedRows) {
    this.selectedRows.set(selectedRows);
  }

  public ObjectProperty<List<FeatureListRow>> selectedRowsProperty() {
    return selectedRows;
  }

  public AbundanceMeasure getAbundance() {
    return abundance.get();
  }

  public void setAbundance(AbundanceMeasure abundance) {
    this.abundance.set(abundance);
  }

  public ObjectProperty<AbundanceMeasure> abundanceProperty() {
    return abundance;
  }

  public @Nullable MetadataColumn<?> getBatchColumn() {
    return batchColumn.get();
  }

  public void setBatchColumn(@Nullable MetadataColumn<?> batchColumn) {
    this.batchColumn.set(batchColumn);
  }

  public ObjectProperty<@Nullable MetadataColumn<?>> batchColumnProperty() {
    return batchColumn;
  }

  public @NotNull List<String> getSampleTypesToShow() {
    return requireNonNullElse(sampleTypesToShow.get(), List.of());
  }

  public void setSampleTypesToShow(List<String> sampleTypesToShow) {
    this.sampleTypesToShow.set(sampleTypesToShow);
  }

  public ObjectProperty<List<String>> sampleTypesToShowProperty() {
    return sampleTypesToShow;
  }

  public @Nullable ModularFeatureList getFeatureList() {
    return featureList.get();
  }

  public void setFeatureList(@Nullable ModularFeatureList featureList) {
    this.featureList.set(featureList);
  }

  public ObjectProperty<@Nullable ModularFeatureList> featureListProperty() {
    return featureList;
  }

  public @NotNull List<RawDataFile> getOrderedFiles() {
    return requireNonNullElse(orderedFiles.get(), List.of());
  }

  public void setOrderedFiles(List<RawDataFile> orderedFiles) {
    this.orderedFiles.set(orderedFiles);
  }

  public ObjectProperty<List<RawDataFile>> orderedFilesProperty() {
    return orderedFiles;
  }

  public @NotNull List<RawDataFile> getQcFiles() {
    return requireNonNullElse(qcFiles.get(), List.of());
  }

  public void setQcFiles(List<RawDataFile> qcFiles) {
    this.qcFiles.set(qcFiles);
  }

  public ObjectProperty<List<RawDataFile>> qcFilesProperty() {
    return qcFiles;
  }

  public @NotNull Map<RawDataFile, Color> getFileColors() {
    return requireNonNullElse(fileColors.get(), Map.of());
  }

  public void setFileColors(Map<RawDataFile, Color> fileColors) {
    this.fileColors.set(fileColors);
  }

  public ObjectProperty<Map<RawDataFile, Color>> fileColorsProperty() {
    return fileColors;
  }

  public double getGoodQualityFraction() {
    return goodQualityFraction.get();
  }

  public void setGoodQualityFraction(double goodQualityFraction) {
    this.goodQualityFraction.set(goodQualityFraction);
  }

  public DoubleProperty goodQualityFractionProperty() {
    return goodQualityFraction;
  }

  public double getWarwickFraction() {
    return warwickFraction.get();
  }

  public void setWarwickFraction(double warwickFraction) {
    this.warwickFraction.set(warwickFraction);
  }

  public DoubleProperty warwickFractionProperty() {
    return warwickFraction;
  }
}
