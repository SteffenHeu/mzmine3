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

package io.github.mzmine.gui.preferences;

import io.github.mzmine.datamodel.utils.UniqueIdSupplier;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max.LocalMaxMassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.local_max.LocalMaxSmoothingOptions;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * Selects how Agilent {@code .d} files are imported: via the native AgilentReader subprocess or by
 * conversion through MSConvert.
 */
public enum AgilentImportOptions implements UniqueIdSupplier {
  AGILENT_READER, AGILENT_READER_AUTO_CENTROID, MSCONVERT;

  @Override
  public @NotNull String getUniqueID() {
    return switch (this) {
      case AGILENT_READER -> "agilent_reader";
      case AGILENT_READER_AUTO_CENTROID -> "agilent_reader_auto_centroid_ims";
      case MSCONVERT -> "msconvert";
    };
  }

  @Override
  public String toString() {
    return switch (this) {
      case AGILENT_READER -> "Native (AgilentReader)";
      case AGILENT_READER_AUTO_CENTROID -> "Native (AgilentReader, auto-centroid IMS)";
      case MSCONVERT -> "MSConvert";
    };
  }

  public boolean isNative() {
    return this == AGILENT_READER || this == AGILENT_READER_AUTO_CENTROID;
  }

  public String getDescriptions() {
    return switch (this) {
      case AGILENT_READER ->
          "Import Agilent .d files using the native Agilent reader (Windows only)";
      case AGILENT_READER_AUTO_CENTROID -> """
          Import Agilent .d files using the native Agilent reader (Windows only) and automatically
          centroid IMS datasets during import using an mzmine algorithm (%s with %s smoothing)
          (Agilent reader does not support centroiding for IMS data). The "vendor centroiding" option
          must be enabled for the mzmine-centroiding to be applied. If advanced parameters are used
          during data import, they will be applied after centroiding.""".formatted(
          LocalMaxMassDetector.NAME, LocalMaxSmoothingOptions.GAUSSIAN);
      case MSCONVERT ->
          "Import Agilent .d files by converting them to mzML using MSConvert. (Windows only)";
    };
  }

  public static String getTooltip() {
    return Arrays.stream(values()).map(opt -> opt.toString() + ": " + opt.getDescriptions())
        .collect(Collectors.joining("\n"));
  }
}
