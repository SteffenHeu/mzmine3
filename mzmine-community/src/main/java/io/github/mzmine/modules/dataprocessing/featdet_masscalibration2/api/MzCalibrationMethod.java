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

package io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.api;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.gui.chartbasics.simplechart.providers.PlotXYDataProvider;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.util.MemoryMapStorage;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A mass-calibration strategy (sub-module of the unified mass calibration module). Each option of
 * {@link io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.MzCalibrationMethods} is backed by
 * an implementation of this interface.
 * <p>
 * Modeled on {@link io.github.mzmine.modules.dataprocessing.featdet_baselinecorrection.BaselineCorrector}:
 * each method is itself an {@link MZmineModule} (so it can carry a parameter set), is instantiated
 * per-run via {@link #newInstance(ParameterSet, MemoryMapStorage)}, and can contribute diagnostic
 * data to the preview.
 * <p>
 * The strategies differ only in <i>how</i> the calibration is built; the unified
 * {@link io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.MassCalibrationTask} applies the
 * resulting {@link MzCalibrationFunction} to all selected mass lists in the same way.
 */
public interface MzCalibrationMethod extends MZmineModule {

  /**
   * Build the file-specific calibration from the already-detected mass lists of a raw data file.
   * Implementations create their own scan access over the relevant scans (e.g. MS1-only for
   * lockmass, an RT window for the calibration segment).
   *
   * @param file the raw data file being calibrated
   * @return the fitted calibration function, or {@code null} (after logging) if calibration could
   * not be established (e.g. no lockmass / no calibrant matches found)
   */
  @Nullable
  MzCalibrationFunction buildCalibration(@NotNull RawDataFile file);

  /**
   * @param parameters the embedded parameters selected for this option
   * @param storage    storage for any generated data (may be {@code null}, e.g. in preview)
   * @return a configured instance of this method for a single run
   */
  MzCalibrationMethod newInstance(@NotNull ParameterSet parameters, @Nullable MemoryMapStorage storage);

  /**
   * Diagnostic curves/points for the preview chart (e.g. correction-vs-RT for lockmass, error-vs-m/z
   * fit for the segment/standards methods). Populated by {@link #buildCalibration} when run in
   * preview. Empty by default.
   *
   * @return the additional preview datasets
   */
  default List<PlotXYDataProvider> getAdditionalPreviewData() {
    return List.of();
  }
}
