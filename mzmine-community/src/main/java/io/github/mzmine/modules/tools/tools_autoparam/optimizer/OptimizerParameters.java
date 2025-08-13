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

package io.github.mzmine.modules.tools.tools_autoparam.optimizer;

import io.github.mzmine.datamodel.features.types.numbers.MZType;
import io.github.mzmine.datamodel.features.types.numbers.MobilityType;
import io.github.mzmine.datamodel.features.types.numbers.RTType;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.BooleanParameter;
import io.github.mzmine.parameters.parametertypes.ComboParameter;
import io.github.mzmine.parameters.parametertypes.ImportType;
import io.github.mzmine.parameters.parametertypes.ImportTypeParameter;
import io.github.mzmine.parameters.parametertypes.IntegerParameter;
import io.github.mzmine.parameters.parametertypes.OptionalParameter;
import io.github.mzmine.parameters.parametertypes.filenames.FileNameParameter;
import io.github.mzmine.parameters.parametertypes.filenames.FileSelectionType;
import io.github.mzmine.parameters.parametertypes.submodules.ParameterSetParameter;
import io.github.mzmine.util.files.ExtensionFilters;
import java.util.Collection;
import java.util.List;

public class OptimizerParameters extends SimpleParameterSet {

  public static final BooleanParameter maximizeCv20 = new BooleanParameter(
      "Maximize rows with RSD < 20",
      "Attempts to maximize the number of rows that have an area RSD of < 20%", true);

  public static final BooleanParameter maximizeFeaturesWithIsotopes = new BooleanParameter(
      "Maximize features with isotopes",
      "Attempts to maximize the number of features that have an isotope pattern assigned to them.",
      true);

  public static final BooleanParameter minimizeDoublePeaks = new BooleanParameter(
      "Minimize number of double peaks",
      "Minimizes the number of features that are classified as being a double peak.", true);

  public static final BooleanParameter maximizeNumberOfBenchmarkFeatures = new BooleanParameter(
      "Maximize number of benchmark features", """
      Maximizes the number of benchmark features. Benchmark features are classified as two-fold:
      1. All data files are searched for their base peak m/z (=most intense signal in a scan). 
         A chromatogram is build for each base peak and cut at 5% relative height. The RT range is 
         then increased by 3*the FWHM of the peak. Additionally, the 13C isotopes are extracted in
         the same RT range and used, if they have a correlation factor > 90%.
      2. Additional benchmark features may be user defined by the file given below.""", true);

  public static final BooleanParameter maximizeRowFillRatio = new BooleanParameter(
      "Maximize fill ratio", """
      Calculates how "full" the feature table is.
      Multiplies the number of rows by the number of samples to calculate the theoretical maximum.
      Then checks how many features were actually detected and divides it by the maximum.
      """, true);

  public static final OptionalParameter<ImportTypeParameter> benchmarkFeatureTypes = new OptionalParameter<>(
      new ImportTypeParameter("Benchmark feature csv column names", "",
          List.of(new ImportType(true, "mz", new MZType()),
              new ImportType(true, "rt", new RTType()),
              new ImportType(false, "mobility", new MobilityType()))));
  public static final OptionalParameter<FileNameParameter> benchmarkFeaturesFile = new OptionalParameter<>(
      new FileNameParameter("Benchmark features file", "", ExtensionFilters.CSV_TSV_IMPORT,
          FileSelectionType.OPEN));

  public static final IntegerParameter iterations = new IntegerParameter("Iterations",
      "Number of iterations during optimization.", 100, 100, 10_000);

  public static final ParameterSetParameter<OptimizerParameterParameters> paramToOptimize = new ParameterSetParameter<>(
      "Parameters to optimize", "", new OptimizerParameterParameters());

  public OptimizerParameters() {
    super(maximizeCv20, maximizeFeaturesWithIsotopes, minimizeDoublePeaks, maximizeRowFillRatio,
        maximizeNumberOfBenchmarkFeatures, benchmarkFeatureTypes, benchmarkFeaturesFile, iterations,
        paramToOptimize);
  }

  public static ParameterSet create(boolean maxCv20, boolean maxFeaturesWithIsotopes,
      boolean minDoublePeaks, boolean maxNumberOfBenchmarkFeatures, boolean maxFillRatio,
      int numIterations) {
    final ParameterSet param = new OptimizerParameters().cloneParameterSet();

    param.setParameter(maximizeCv20, maxCv20);
    param.setParameter(maximizeFeaturesWithIsotopes, maxFeaturesWithIsotopes);
    param.setParameter(minimizeDoublePeaks, minDoublePeaks);
    param.setParameter(maximizeNumberOfBenchmarkFeatures, maxNumberOfBenchmarkFeatures);
    param.setParameter(maximizeRowFillRatio, maxFillRatio);
    param.setParameter(benchmarkFeatureTypes, false);
    param.setParameter(benchmarkFeaturesFile, false);
    param.setParameter(iterations, numIterations);

    final OptimizerParameterParameters optimizerParameterParameters = OptimizerParameterParameters.create(
        true, true, true, true, true, true, true, true);
    param.setParameter(paramToOptimize, optimizerParameterParameters);

    return param;
  }

  @Override
  public boolean checkParameterValues(Collection<String> errorMessages,
      boolean skipRawDataAndFeatureListParameters) {
    final boolean superCheck = super.checkParameterValues(errorMessages,
        skipRawDataAndFeatureListParameters);

    final boolean benchmarkFileSelected = getValue(benchmarkFeaturesFile);
    final boolean benchmarkColumnsSelected = getValue(benchmarkFeatureTypes);

    if (!((benchmarkFileSelected && benchmarkColumnsSelected) || (!benchmarkFileSelected
        && !benchmarkColumnsSelected))) {
      errorMessages.add("%s and %s must be both selected or both disabled.".formatted(
          benchmarkFeatureTypes.getName(), benchmarkFeaturesFile.getName()));
    }

    return superCheck && errorMessages.isEmpty();
  }
}
