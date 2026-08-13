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

import io.github.mzmine.datamodel.utils.UniqueIdSupplier;
import io.github.mzmine.parameters.parametertypes.combowithinput.ComboWithFileInputParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Source of a calibrant list: either one of the bundled (shipped) lists or a {@link #CUSTOM_FILE}
 * that the user browses to. Used as the combo options of a {@link ComboWithFileInputParameter}.
 * <p>
 * Bundled lists are packaged CSV resources with an {@code mz} column (and optional {@code rt}); add
 * new presets by shipping a resource under {@code mzcalibration/} and adding an enum constant.
 */
public enum CalibrantListSource implements UniqueIdSupplier {

  /**
   * Browse to a custom calibrant CSV/TSV file (the combo's input trigger).
   */
  CUSTOM_FILE, // ---- calibration-segment lists (tab-separated tables with an "mz" (and optionally rt) column) ----
  NaFormPos, NaFormNeg, NaFormPosNeg, AgilentTuneMix, PolymerFactoryPrototype, // ---- internal-standard / contaminant lists shipped by the old module (universal calibrants) ----
  UNIVERSAL_1_POSITIVE, UNIVERSAL_1_NEGATIVE, UNIVERSAL_2_POSITIVE, UNIVERSAL_2_NEGATIVE, UNIVERSAL_MERGED_POSITIVE, UNIVERSAL_MERGED_NEGATIVE;

  /**
   * The file format of a bundled list, which determines how it is read.
   */
  public enum Format {
    /**
     * A tabular file with an {@code mz} column (and optional {@code rt}), read via
     * {@code CSVParsingUtils.getAnnotationsFromCsvFile}. Also used for custom user files.
     */
    MZ_TABLE,
    /**
     * The old module's universal-calibrant CSVs, whose first column is the monoisotopic m/z.
     */
    UNIVERSAL_CALIBRANTS
  }

  /**
   * Options offered by the calibration-segment method (its specific lists + custom file).
   */
  public static CalibrantListSource[] segmentOptions() {
    return new CalibrantListSource[]{CUSTOM_FILE, NaFormPos, NaFormNeg, NaFormPosNeg,
        AgilentTuneMix, PolymerFactoryPrototype};
  }

  /**
   * Options offered by the internal-standards / contaminant method (the old module's universal
   * calibrant lists + custom file).
   */
  public static CalibrantListSource[] internalStandardOptions() {
    return new CalibrantListSource[]{CUSTOM_FILE, UNIVERSAL_1_POSITIVE, UNIVERSAL_1_NEGATIVE,
        UNIVERSAL_2_POSITIVE, UNIVERSAL_2_NEGATIVE, UNIVERSAL_MERGED_POSITIVE,
        UNIVERSAL_MERGED_NEGATIVE, PolymerFactoryPrototype};
  }

  /**
   * @return how this list should be parsed (irrelevant for {@link #CUSTOM_FILE}, which uses the
   * user file with {@link Format#MZ_TABLE}).
   */
  public Format format() {
    return switch (this) {
      case CUSTOM_FILE, NaFormPos, NaFormNeg, NaFormPosNeg, AgilentTuneMix,
           PolymerFactoryPrototype -> Format.MZ_TABLE;
      case UNIVERSAL_1_POSITIVE, UNIVERSAL_1_NEGATIVE, UNIVERSAL_2_POSITIVE, UNIVERSAL_2_NEGATIVE,
           UNIVERSAL_MERGED_POSITIVE, UNIVERSAL_MERGED_NEGATIVE -> Format.UNIVERSAL_CALIBRANTS;
    };
  }

  /**
   * @return the classpath resource of the bundled list, or {@code null} for {@link #CUSTOM_FILE}.
   */
  public @Nullable String resourcePath() {
    return switch (this) {
      case CUSTOM_FILE -> null;
      case NaFormPos -> "mzcalibration_segments/NaFormPos.txt";
      case NaFormNeg -> "mzcalibration_segments/NaFormNeg.txt";
      case NaFormPosNeg -> "mzcalibration_segments/NaFormCombined.txt";
      case AgilentTuneMix -> "mzcalibration_segments/AgilentTuneMix.txt";
      case UNIVERSAL_1_POSITIVE -> "universal_calibrants_1_positive_mode.csv";
      case UNIVERSAL_1_NEGATIVE -> "universal_calibrants_1_negative_mode.csv";
      case UNIVERSAL_2_POSITIVE -> "universal_calibrants_2_positive_mode.csv";
      case UNIVERSAL_2_NEGATIVE -> "universal_calibrants_2_negative_mode.csv";
      case UNIVERSAL_MERGED_POSITIVE -> "universal_calibrants_merged_positive_mode.csv";
      case UNIVERSAL_MERGED_NEGATIVE -> "universal_calibrants_merged_negative_mode.csv";
      case PolymerFactoryPrototype -> "mzcalibration_segments/polymer_factory_lc.txt";
    };
  }

  @Override
  public @NotNull String getUniqueID() {
    return switch (this) {
      case CUSTOM_FILE -> "custom_file";
      case NaFormPos -> "na_form_pos";
      case NaFormNeg -> "na_form_neg";
      case NaFormPosNeg -> "na_form_pos_neg";
      case AgilentTuneMix -> "agilent_tune_mix";
      case UNIVERSAL_1_POSITIVE -> "universal_1_positive";
      case UNIVERSAL_1_NEGATIVE -> "universal_1_negative";
      case UNIVERSAL_2_POSITIVE -> "universal_2_positive";
      case UNIVERSAL_2_NEGATIVE -> "universal_2_negative";
      case UNIVERSAL_MERGED_POSITIVE -> "universal_merged_positive";
      case UNIVERSAL_MERGED_NEGATIVE -> "universal_merged_negative";
      case PolymerFactoryPrototype -> "polymer_factory_prototype";
    };
  }

  @Override
  public String toString() {
    return switch (this) {
      case CUSTOM_FILE -> "Custom file";
      case NaFormPos -> "Sodium Formate (+)";
      case NaFormNeg -> "Sodium Formate (-)";
      case NaFormPosNeg -> "Sodium formate (+/-)";
      case AgilentTuneMix -> "Agilent Tune Mix (Stow et al.) (+/-)";
      case PolymerFactoryPrototype -> "Polymer factory (prototype)";
      case UNIVERSAL_1_POSITIVE -> "Universal calibrants (Keller) (+)";
      case UNIVERSAL_1_NEGATIVE -> "Universal calibrants (Keller) (-)";
      case UNIVERSAL_2_POSITIVE -> "Universal calibrants (Hawkes) (+)";
      case UNIVERSAL_2_NEGATIVE -> "Universal calibrants (Hawkes) (-)";
      case UNIVERSAL_MERGED_POSITIVE -> "Universal calibrants merged (+)";
      case UNIVERSAL_MERGED_NEGATIVE -> "Universal calibrants merged (-)";
    };
  }
}
