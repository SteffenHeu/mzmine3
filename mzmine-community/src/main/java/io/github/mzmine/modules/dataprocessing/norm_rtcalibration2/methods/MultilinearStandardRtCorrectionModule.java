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

package io.github.mzmine.modules.dataprocessing.norm_rtcalibration2.methods;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.compoundannotations.CompoundDBAnnotation;
import io.github.mzmine.modules.dataprocessing.norm_rtcalibration2.RTCorrectionParameters;
import io.github.mzmine.modules.dataprocessing.norm_rtcalibration2.RTMeasure;
import io.github.mzmine.modules.dataprocessing.norm_rtcalibration2.RtStandard;
import io.github.mzmine.modules.dataprocessing.norm_rtcalibration2.ScanRtCorrectionTask;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.ImportType;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilePlaceholder;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.CSVParsingUtils;
import io.github.mzmine.util.CSVParsingUtils.CompoundDbLoadResult;
import io.github.mzmine.util.ParsingUtils;
import io.github.mzmine.util.collections.BinarySearch;
import io.github.mzmine.util.collections.IndexRange;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

public final class MultilinearStandardRtCorrectionModule implements RawFileRtCorrectionModule {

  private static final Logger logger = Logger.getLogger(
      MultilinearStandardRtCorrectionModule.class.getName());

  @Override
  public AbstractRtCorrectionFunction createInterpolated(@NotNull RawDataFile file,
      @NotNull List<RtStandard> rtSortedStandards,
      @NotNull AbstractRtCorrectionFunction previousRunCalibration, double previousRunWeight,
      @NotNull AbstractRtCorrectionFunction nextRunCalibration, double nextRunWeight,
      @NotNull final RTMeasure rtMeasure, @NotNull ParameterSet parameters) {

    return new MultiLinearRtCorrectionFunction(file, rtSortedStandards,
        (MultiLinearRtCorrectionFunction) previousRunCalibration, previousRunWeight,
        (MultiLinearRtCorrectionFunction) nextRunCalibration, nextRunWeight,
        parameters.getValue(MultilinearRawFileRtCalibrationParameters.correctionBandwidth),
        rtMeasure);
  }

  @Override
  public AbstractRtCorrectionFunction createFromStandards(@NotNull FeatureList flist,
      @NotNull List<@NotNull RtStandard> rtSortedStandards, @NotNull final RTMeasure rtMeasure,
      @NotNull ParameterSet correctionModuleParameters, @NotNull ParameterSet mainParameters) {

    return new MultiLinearRtCorrectionFunction(flist, rtSortedStandards,
        correctionModuleParameters.getValue(
            MultilinearRawFileRtCalibrationParameters.correctionBandwidth), rtMeasure);
  }

  @Override
  public AbstractRtCorrectionFunction loadFromXML(@NotNull Element element,
      final @NotNull RawDataFilePlaceholder file) {

    final PolynomialSplineFunction polynomialSplineFunction = ParsingUtils.loadSplineFunctionFromParentXmlElement(
        element);
    return new MultiLinearRtCorrectionFunction(file, polynomialSplineFunction);
  }

  @Override
  public @NotNull String getUniqueID() {
    return "multi_linear_standards_raw_file_rt_calibration";
  }

  @Override
  public @NotNull String getName() {
    return "Internal standard-based multi linear RT calibration";
  }

  @Override
  public @Nullable Class<? extends ParameterSet> getParameterSetClass() {
    return MultilinearStandardRtCalibrationParameters.class;
  }

  @Override
  public @NotNull List<RtStandard> prefilterStandards(@NotNull List<RtStandard> rtSortedStandards,
      @NotNull List<FeatureList> referenceFlists, @NotNull ParameterSet mainParameters,
      @NotNull ParameterSet correctionModuleParameters) {

    final RTMeasure rtMeasure = mainParameters.getValue(RTCorrectionParameters.rtMeasure);

    final File standardsFile = correctionModuleParameters.getValue(
        MultilinearStandardRtCalibrationParameters.standardsList);
    final List<ImportType<?>> types = correctionModuleParameters.getValue(
        MultilinearStandardRtCalibrationParameters.importTypes);
    final Character separator = CSVParsingUtils.autoDetermineSeparatorDefaultFallback(
        standardsFile);
    CompoundDbLoadResult standardsResult = CSVParsingUtils.getAnnotationsFromCsvFile(standardsFile,
        separator.toString(), types, null);

    final RTTolerance rtTol = correctionModuleParameters.getValue(
        MultilinearStandardRtCalibrationParameters.standardTolerance);
    final MZTolerance mzTol = mainParameters.getValue(RTCorrectionParameters.MZTolerance);

    if (standardsResult.status() != TaskStatus.FINISHED) {
      throw new IllegalStateException(standardsResult.errorMessage());
    }

    final List<RtStandard> internalStandards = new ArrayList<>();
    // kept only for the summary log
    final Map<CompoundDBAnnotation, RtStandard> matchedStandards = new HashMap<>();

    final List<CompoundDBAnnotation> annotations = standardsResult.annotations().stream()
        .filter(a -> a.getPrecursorMZ() != null && a.getRT() != null)
        .sorted(Comparator.comparingDouble(CompoundDBAnnotation::getPrecursorMZ)).toList();

    for (CompoundDBAnnotation annotation : annotations) {
      if (annotation.getPrecursorMZ() == null || annotation.getRT() == null) {
        continue;
      }

      final IndexRange matchingRts = BinarySearch.indexRange(
          rtTol.getToleranceRange(annotation.getRT()), rtSortedStandards, s -> s.getRt(rtMeasure));

      final double annotationMz = annotation.getPrecursorMZ();
      final List<RtStandard> inMzRange = matchingRts.sublist(rtSortedStandards).stream()
          .filter(standard -> mzTol.checkWithinTolerance(standard.getAverageMz(), annotationMz))
          .toList();
      final RtStandard bestStandard = inMzRange.stream()
          .min(Comparator.comparingDouble(std -> Math.abs(std.getAverageMz() - annotationMz)))
          .orElse(null);

      if (bestStandard == null) {
        continue;
      }

      final InternalRtStandard internalStandard = new InternalRtStandard(bestStandard.standards(),
          annotation);
      internalStandards.add(internalStandard);
      matchedStandards.put(annotation, internalStandard);
    }

    final List<RtStandard> rtSortedInternalStandards = internalStandards.stream()
        .sorted(Comparator.comparingDouble(s -> s.getRt(rtMeasure))).toList();
    // resetting the RTs to the list RTs may change the monotonicity. The caller filters again,
    // which is a no-op on this result, but filtering here tells which standards were rejected.
    final List<RtStandard> monotonousStandards = ScanRtCorrectionTask.removeNonMonotonousStandards(
        rtSortedInternalStandards, referenceFlists, rtMeasure);

    logStandardsSummary(standardsResult.annotations(), matchedStandards, monotonousStandards,
        rtMeasure);
    return monotonousStandards;
  }

  /**
   * Logs a table with the status of every entry of the internal standards list: used, not found
   * among the detected standard candidates, or rejected because its list RT contradicts the
   * observed elution order.
   *
   * @param listEntries      all entries of the internal standards list
   * @param matchedStandards the detected standard matched to each list entry
   * @param usedStandards    the standards that passed the monotonicity check
   * @param rtMeasure        the RT measure for observed RTs
   */
  private static void logStandardsSummary(@NotNull List<CompoundDBAnnotation> listEntries,
      @NotNull Map<CompoundDBAnnotation, RtStandard> matchedStandards,
      @NotNull List<RtStandard> usedStandards, @NotNull RTMeasure rtMeasure) {
    final String rowFormat = "%-45s %10s %8s  %-15s %10s %8s%n";
    final StringBuilder table = new StringBuilder(
        "Internal RT standards: %d of %d used%n".formatted(usedStandards.size(),
            listEntries.size()));
    table.append(
        rowFormat.formatted("Name", "List m/z", "List RT", "Status", "Found m/z", "Found RT"));

    // assumption: entries without RT sort last, they are reported as invalid
    listEntries.stream()
        .sorted(Comparator.comparingDouble(a -> a.getRT() != null ? a.getRT() : Float.MAX_VALUE))
        .forEach(entry -> {
          final RtStandard matched = matchedStandards.get(entry);
          final String status;
          if (entry.getPrecursorMZ() == null || entry.getRT() == null) {
            status = "NO M/Z OR RT";
          } else if (matched == null) {
            status = "NOT FOUND";
          } else if (usedStandards.contains(matched)) {
            status = "USED";
          } else {
            status = "REJECTED";
          }

          // InternalRtStandard reports the list RT, recalculate the observed one from the features
          final String foundMz = matched != null ? "%.4f".formatted(matched.getAverageMz()) : "-";
          final String foundRt = matched != null ? "%.3f".formatted(
              new RtStandard(matched.standards()).getRt(rtMeasure)) : "-";
          table.append(
              rowFormat.formatted(entry.getCompoundName() != null ? entry.getCompoundName() : "-",
                  entry.getPrecursorMZ() != null ? "%.4f".formatted(entry.getPrecursorMZ()) : "-",
                  entry.getRT() != null ? "%.3f".formatted(entry.getRT()) : "-", status, foundMz,
                  foundRt));
        });

    table.append("""
        NOT FOUND: no standard candidate within the m/z and standard RT tolerance. Candidates must \
        be detected in ALL reference files, be unique within the m/z and 3x RT tolerance and reach \
        the minimum height.
        REJECTED: the list RT order contradicts the observed elution order in at least one file.""");
    logger.info(table.toString());
  }
}
