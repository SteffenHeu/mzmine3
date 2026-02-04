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

package io.github.mzmine.modules.dataprocessing.norm_rtcalibration2.methods;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.compoundannotations.CompoundDBAnnotation;
import io.github.mzmine.modules.dataprocessing.norm_rtcalibration2.RTCorrectionParameters;
import io.github.mzmine.modules.dataprocessing.norm_rtcalibration2.RTMeasure;
import io.github.mzmine.modules.dataprocessing.norm_rtcalibration2.RtStandard;
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
import java.util.List;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

public final class MultilinearStandardRtCorrectionModule implements RawFileRtCorrectionModule {

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

    File standardsFile = correctionModuleParameters.getValue(
        MultilinearStandardRtCalibrationParameters.standardsList);
    List<ImportType<?>> types = correctionModuleParameters.getValue(
        MultilinearStandardRtCalibrationParameters.importTypes);
    final Character separator = CSVParsingUtils.autoDetermineSeparatorDefaultFallback(
        standardsFile);
    CompoundDbLoadResult standardsResult = CSVParsingUtils.getAnnotationsFromCsvFile(standardsFile,
        separator.toString(), types, null);

    final MZTolerance mzTol = mainParameters.getValue(RTCorrectionParameters.MZTolerance);
    final RTTolerance rtTol = mainParameters.getValue(RTCorrectionParameters.RTTolerance);

    if (standardsResult.status() != TaskStatus.FINISHED) {
      throw new IllegalStateException(standardsResult.errorMessage());
    }

    final List<RtStandard> internalStandards = new ArrayList<>();

    final List<CompoundDBAnnotation> annotations = standardsResult.annotations().stream()
        .filter(a -> a.getPrecursorMZ() != null && a.getRT() != null)
        .sorted(Comparator.comparingDouble(CompoundDBAnnotation::getPrecursorMZ)).toList();

    final List<RtStandard> mzSortedStandards = rtSortedStandards.stream()
        .sorted(Comparator.comparingDouble(RtStandard::getAverageMz)).toList();

    for (CompoundDBAnnotation annotation : annotations) {
      final IndexRange matchingRts = BinarySearch.indexRange(
          rtTol.getToleranceRange(annotation.getRT()), rtSortedStandards, s -> s.getRt(rtMeasure));

      final double annotationMz = annotation.getPrecursorMZ();
      final RtStandard bestStandard = matchingRts.sublist(rtSortedStandards).stream()
          .min(Comparator.comparingDouble(std -> {
            final double stdMz = std.getAverageMz();
            return Math.abs(stdMz - annotationMz);
          })).orElse(null);
      if (bestStandard == null) {
        continue;
      }
      internalStandards.add(new InternalRtStandard(bestStandard.standards(), annotation));
    }

    return new MultiLinearRtCorrectionFunction(flist,
        internalStandards.stream().sorted(Comparator.comparingDouble(s -> s.getRt(rtMeasure)))
            .toList(), correctionModuleParameters.getValue(
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
}
