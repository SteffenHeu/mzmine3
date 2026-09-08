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

package io.github.mzmine.modules.tools.tools_autoparam.estimation;

import io.github.mzmine.modules.tools.batchwizard.WizardPart;
import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassDetectorWizardOptions;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassSpectrometerWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.WizardStepParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.custom_parameters.WizardMassDetectorNoiseLevels;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.MassSpectrometerWizardParameterFactory;
import io.github.mzmine.modules.tools.tools_autoparam.RawDataParameterEstimation;
import io.github.mzmine.parameters.UserParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wizard context for interpreting measurements; construction performs no processing.
 */
public final class ParameterEstimationContext {

  private final @NotNull RawDataAnalysis analysis;
  private final @NotNull WizardSequence sequence;
  private final @Nullable MZTolerance sampleMzTolerance;

  /**
   *
   */
  private ParameterEstimationContext(@NotNull RawDataAnalysis analysis,
      @NotNull WizardSequence sequence, @Nullable MZTolerance sampleMzTolerance) {
    this.analysis = analysis;
    this.sequence = sequence;
    this.sampleMzTolerance = sampleMzTolerance;
  }

  public ParameterEstimationContext(@NotNull RawDataAnalysis analysis,
      @NotNull WizardSequence sequence) {
    this(analysis, sequence,
        ParameterEstimators.estimateSampleToSampleMzTolerance(analysis.sampleMzToleranceCounts(),
            0.8f));
  }

  public @NotNull MassDetectorWizardOptions massDetectorType() {
    return sequence.get(WizardPart.MS)
        .map(step -> step.getValue(MassSpectrometerWizardParameters.massDetectorOption))
        .map(WizardMassDetectorNoiseLevels::getValueType)
        .orElseGet(() -> RawDataParameterEstimation.inferMassDetectorType(analysis.files()));
  }

  public boolean lowResolution() {
    return sequence.get(WizardPart.MS).map(WizardStepParameters::getFactory)
        .map(MassSpectrometerWizardParameterFactory.LOW_RES::equals).orElse(false);
  }

  public <T> @NotNull T preset(@NotNull WizardPart part, @NotNull UserParameter<T, ?> parameter) {
    return sequence.get(part).map(WizardStepParameters::createDefaultParameterPreset)
        .map(step -> step.getValue(parameter)).orElseThrow(() -> new IllegalArgumentException(
            "No preset default for " + part + ": " + parameter.getName()));
  }

  public @NotNull RawDataAnalysis analysis() {
    return analysis;
  }

  public @NotNull WizardSequence sequence() {
    return sequence;
  }

  public @Nullable MZTolerance sampleMzTolerance() {
    return sampleMzTolerance;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    var that = (ParameterEstimationContext) obj;
    return Objects.equals(this.analysis, that.analysis) && Objects.equals(this.sequence,
        that.sequence) && Objects.equals(this.sampleMzTolerance, that.sampleMzTolerance);
  }

  @Override
  public int hashCode() {
    return Objects.hash(analysis, sequence, sampleMzTolerance);
  }

  @Override
  public String toString() {
    return "ParameterEstimationContext[" + "analysis=" + analysis + ", " + "sequence=" + sequence
        + ", " + "sampleMzTolerance=" + sampleMzTolerance + ']';
  }

}
