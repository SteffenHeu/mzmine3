/*
 * Copyright (c) 2004-2026 The mzmine Development Team
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

package io.github.mzmine.modules.dataprocessing.norm_intensity;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.modules.visualization.projectmetadata.SampleTypeFilter;
import io.github.mzmine.modules.visualization.projectmetadata.table.MetadataTable;
import io.github.mzmine.modules.visualization.projectmetadata.table.MetadataTableUtils;
import io.github.mzmine.modules.visualization.projectmetadata.table.MetadataTableUtils.InterpolationWeights;
import io.github.mzmine.modules.visualization.projectmetadata.table.columns.MetadataColumn;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.OptionalParameter;
import io.github.mzmine.parameters.parametertypes.metadata.MetadataGroupingParameter;
import io.github.mzmine.util.MathUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Basic implementation for normalization functions that have constant factor and do not rely on mz
 * or rt of the feature.
 */
public abstract class AbstractFactorNormalizationTypeModule implements NormalizationTypeModule {

  private static final Logger logger = Logger.getLogger(
      AbstractFactorNormalizationTypeModule.class.getName());

  @NotNull
  public List<RawDataFile> getReferenceSamples(@NotNull final FeatureList flist,
      @NotNull final ParameterSet normalizationModuleParameters) {
    // all parametersets need to use the same FeatureIntensityNormalizationParameters.samplesTypes parameter
    final var sampleTypeFilter = new SampleTypeFilter(
        normalizationModuleParameters.getParameter(FeatureIntensityNormalizationParameters.sampleTypes)
            .getValue());
    return sampleTypeFilter.filterFiles(flist.getRawDataFiles());
  }

  @Override
  public @NotNull Map<@NotNull RawDataFile, @NotNull NormalizationFunction> createReferenceFunctions(
      @NotNull final List<@NotNull RawDataFile> referenceFiles,
      @NotNull final ModularFeatureList featureList, @NotNull final MetadataTable metadata,
      @NotNull final ParameterSet mainParameters,
      @NotNull final ParameterSet normalizerSpecificParam) {

    // Check if batch-aware normalization is requested via the optional batchIdColumn parameter.
    final String batchColumnName = getBatchIdColumnName(mainParameters);
    if (batchColumnName != null) {
      return createBatchAwareFunctions(referenceFiles, featureList, metadata, mainParameters,
          normalizerSpecificParam, batchColumnName);
    }

    // Non-batch path: original behaviour — factors relative to global max QC metric.
    final Map<@NotNull RawDataFile, @NotNull Double> referenceToNormalizationMetric = referenceFiles.stream()
        .collect(Collectors.toMap(Function.identity(),
            file -> getNormalizationMetricForFile(file, featureList, mainParameters,
                normalizerSpecificParam)));
    final double maxNormalizationMetric = referenceToNormalizationMetric.values().stream()
        .max(Double::compare).orElse(1d);

    final Map<@NotNull RawDataFile, @NotNull NormalizationFunction> functions = new HashMap<>();
    for (final Entry<@NotNull RawDataFile, @NotNull Double> entry : referenceToNormalizationMetric.entrySet()) {
      final RawDataFile file = entry.getKey();
      final LocalDateTime runDate = MetadataTableUtils.getRunDate(metadata, file);
      final double normalizationFactor = maxNormalizationMetric / entry.getValue();
      functions.put(file, new FactorNormalizationFunction(file, runDate, normalizationFactor));
    }
    return functions;
  }

  /**
   * Batch-aware normalization combining intra-batch QC drift correction with inter-batch median QC
   * scaling (pooled QC normalization).
   * <p>
   * Algorithm:
   * <ol>
   *   <li>Group QC reference files by their batch ID value from the metadata column.</li>
   *   <li>Compute the normalization metric (e.g. median intensity) for every QC file.</li>
   *   <li>Compute the median QC metric per batch ({@code batchMedian}).</li>
   *   <li>Compute the global median of per-batch medians ({@code globalMedian}).</li>
   *   <li>Inter-batch scale for each batch: {@code globalMedian / batchMedian}.</li>
   *   <li>Adjusted metric per file: {@code fileMetric * interBatchScale}.</li>
   *   <li>Within each batch, compute factor = {@code batchAdjustedMax / adjustedMetric}.</li>
   *   <li>Interpolate non-reference (non-QC) files within their batch from adjacent QC
   *       functions.</li>
   * </ol>
   * Returns functions for <em>all</em> files so the caller's generic interpolation loop is a no-op.
   */
  private @NotNull Map<@NotNull RawDataFile, @NotNull NormalizationFunction> createBatchAwareFunctions(
      @NotNull final List<@NotNull RawDataFile> referenceFiles,
      @NotNull final ModularFeatureList featureList, @NotNull final MetadataTable metadata,
      @NotNull final ParameterSet mainParameters,
      @NotNull final ParameterSet normalizerSpecificParam,
      @NotNull final String batchColumnName) {

    // Resolve the batch ID column from metadata.
    final MetadataColumn<?> batchCol = metadata.getColumnByName(batchColumnName);
    if (batchCol == null) {
      throw new IllegalStateException(
          "Batch ID column '%s' not found in metadata.".formatted(batchColumnName));
    }

    // Compute metric for every reference (QC) file.
    final Map<RawDataFile, Double> qcMetrics = referenceFiles.stream().collect(
        Collectors.toMap(Function.identity(),
            f -> getNormalizationMetricForFile(f, featureList, mainParameters,
                normalizerSpecificParam)));

    // Group QC files by batch ID.
    final Map<String, List<RawDataFile>> batchToQCFiles = groupFilesByBatchId(referenceFiles,
        metadata, batchCol);

    // Per-batch median metric.
    final Map<String, Double> batchMedians = new HashMap<>();
    for (final Entry<String, List<RawDataFile>> batchEntry : batchToQCFiles.entrySet()) {
      final double[] metrics = batchEntry.getValue().stream()
          .mapToDouble(f -> qcMetrics.getOrDefault(f, 1.0)).toArray();
      batchMedians.put(batchEntry.getKey(), MathUtils.calcMedian(metrics));
    }

    // Global median of per-batch medians (inter-batch anchor).
    final double globalMedian = MathUtils.calcMedian(
        batchMedians.values().stream().mapToDouble(Double::doubleValue).toArray());

    // Compute inter-batch scale and adjusted metrics per QC file.
    final Map<RawDataFile, Double> adjustedMetrics = new HashMap<>();
    final Map<String, Double> interBatchScales = new HashMap<>();
    for (final Entry<String, Double> batchMedianEntry : batchMedians.entrySet()) {
      final double interBatchScale = globalMedian / batchMedianEntry.getValue();
      interBatchScales.put(batchMedianEntry.getKey(), interBatchScale);
    }
    for (final RawDataFile qcFile : referenceFiles) {
      final String batchId = getBatchId(qcFile, metadata, batchCol);
      adjustedMetrics.put(qcFile, qcMetrics.get(qcFile) * interBatchScales.getOrDefault(batchId, 1.0));
    }

    // Create normalization functions for all QC files (intra-batch factor × inter-batch scale).
    final Map<RawDataFile, NormalizationFunction> functions = new HashMap<>();
    for (final Entry<String, List<RawDataFile>> batchEntry : batchToQCFiles.entrySet()) {
      final List<RawDataFile> batchQCFiles = batchEntry.getValue();
      final double batchAdjustedMax = batchQCFiles.stream()
          .mapToDouble(f -> adjustedMetrics.getOrDefault(f, 1.0)).max().orElse(1.0);

      for (final RawDataFile qcFile : batchQCFiles) {
        final double factor = batchAdjustedMax / adjustedMetrics.getOrDefault(qcFile, 1.0);
        final LocalDateTime runDate = MetadataTableUtils.getRunDate(metadata, qcFile);
        functions.put(qcFile, new FactorNormalizationFunction(qcFile, runDate, factor));
      }
    }

    // Interpolate non-reference files within their own batch.
    final List<RawDataFile> allFiles = featureList.getRawDataFiles();
    for (final RawDataFile nonRefFile : allFiles) {
      if (functions.containsKey(nonRefFile)) {
        continue; // already a reference file
      }
      final String fileBatchId = getBatchId(nonRefFile, metadata, batchCol);
      final List<RawDataFile> batchQCFiles = batchToQCFiles.getOrDefault(fileBatchId,
          List.of());

      if (batchQCFiles.isEmpty()) {
        logger.warning(
            "No QC reference files found in batch '%s' for file '%s'. Using global references.".formatted(
                fileBatchId, nonRefFile.getName()));
        // Fall back to all reference files for interpolation.
        final InterpolationWeights weights = MetadataTableUtils.extractAcquisitionDateInterpolationWeights(
            nonRefFile, referenceFiles, metadata);
        functions.put(nonRefFile, createInterpolatedFunction(nonRefFile,
            functions.get(weights.previousRun()), functions.get(weights.nextRun()), weights,
            metadata, mainParameters, normalizerSpecificParam));
      } else {
        final InterpolationWeights weights = MetadataTableUtils.extractAcquisitionDateInterpolationWeights(
            nonRefFile, batchQCFiles, metadata);
        functions.put(nonRefFile, createInterpolatedFunction(nonRefFile,
            functions.get(weights.previousRun()), functions.get(weights.nextRun()), weights,
            metadata, mainParameters, normalizerSpecificParam));
      }
    }

    return functions;
  }

  private @NotNull Map<String, List<RawDataFile>> groupFilesByBatchId(
      @NotNull final List<RawDataFile> files, @NotNull final MetadataTable metadata,
      @NotNull final MetadataColumn<?> batchCol) {
    final Map<String, List<RawDataFile>> grouped = new HashMap<>();
    for (final RawDataFile file : files) {
      final String batchId = getBatchId(file, metadata, batchCol);
      grouped.computeIfAbsent(batchId, k -> new ArrayList<>()).add(file);
    }
    return grouped;
  }

  private @NotNull String getBatchId(@NotNull final RawDataFile file,
      @NotNull final MetadataTable metadata, @NotNull final MetadataColumn<?> batchCol) {
    final Object value = metadata.getValue(batchCol, file);
    return value != null ? value.toString() : "unknown";
  }

  /**
   * Returns the batch ID column name from mainParameters if the {@code batchIdColumn} optional
   * parameter is present and enabled. Returns {@code null} if batch-aware processing is not
   * requested.
   */
  private static @Nullable String getBatchIdColumnName(@NotNull final ParameterSet mainParameters) {
    try {
      final OptionalParameter<MetadataGroupingParameter> batchIdOpt = mainParameters.getParameter(
          IntensityNormalizerParameters.batchIdColumn);
      if (Boolean.TRUE.equals(batchIdOpt.getValue())) {
        final String colName = batchIdOpt.getEmbeddedParameter().getValue();
        if (colName != null && !colName.isBlank()) {
          return colName;
        }
      }
    } catch (final Exception ignored) {
      // batchIdColumn not present in these parameters — non-batch call path
    }
    return null;
  }

  @Override
  public @NotNull NormalizationFunction createInterpolatedFunction(
      @NotNull final RawDataFile fileToInterpolate,
      @NotNull final NormalizationFunction previousRunCalibration,
      @NotNull final NormalizationFunction nextRunCalibration,
      @NotNull final InterpolationWeights interpolationWeights,
      @NotNull final MetadataTable metadata, @NotNull final ParameterSet mainParameters,
      @NotNull final ParameterSet normalizerParameters) {
    if (!(previousRunCalibration instanceof FactorNormalizationFunction prev)
        || !(nextRunCalibration instanceof FactorNormalizationFunction next)) {
      throw new IllegalStateException("Input calibrations are no factor-based calibrations.");
    }
    final LocalDateTime runDate = MetadataTableUtils.getRunDate(metadata, fileToInterpolate);
    if (runDate == null) {
      throw new IllegalStateException(
          "No acquisition timestamp found for file: " + fileToInterpolate.getName());
    }

    final double factor = next.factor() * interpolationWeights.nextRunWeight()
        + prev.factor() * interpolationWeights.previousWeight();

    return new FactorNormalizationFunction(fileToInterpolate, runDate, factor);
  }

  protected abstract double getNormalizationMetricForFile(@NotNull RawDataFile file,
      @NotNull ModularFeatureList featureList, @NotNull ParameterSet linearNormalizerParameters,
      @NotNull ParameterSet moduleSpecificParameters);
}
