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

package io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.methods.lockmass;

import io.github.mzmine.datamodel.utils.UniqueIdSupplier;
import org.jetbrains.annotations.NotNull;

/**
 * Predefined lockmass m/z presets plus a {@link #CUSTOM} option that enables free-text entry of one
 * or more comma-separated m/z values. Used by the {@link ComboWithStringInputParameter} in
 * {@link LockmassCalibrationParameters}.
 */
public enum LockmassPreset implements UniqueIdSupplier {

  /**
   * Free-text entry of one or more comma-separated m/z values (the combo's input trigger).
   */
  CUSTOM,
  /**
   * Leucine enkephalin [M+H]+ — the classic Waters positive-mode lockmass.
   */
  LEUCINE_ENKEPHALIN_POS,
  /**
   * Leucine enkephalin [M-H]- — negative-mode lockmass.
   */
  LEUCINE_ENKEPHALIN_NEG,
  /**
   *
   */
  AGILENT_TUNE_POS_322, AGILENT_TUNE_POS_622, AGILENT_TUNE_POS_922, AGILENT_TUNE_NEG_301, AGILENT_TUNE_NEG_601, AGILENT_TUNE_NEG_1033;

  /**
   * @return the lockmass m/z values of this preset, or an empty array for {@link #CUSTOM}.
   */
  public double[] mzValues() {
    return switch (this) {
      case CUSTOM -> new double[0];
      case LEUCINE_ENKEPHALIN_POS -> new double[]{556.276575};
      case LEUCINE_ENKEPHALIN_NEG -> new double[]{554.262022};
      case AGILENT_TUNE_POS_322 -> new double[]{322.048123};
      case AGILENT_TUNE_POS_622 -> new double[]{622.028961};
      case AGILENT_TUNE_POS_922 -> new double[]{922.009799};
      case AGILENT_TUNE_NEG_301 -> new double[]{301.998139};
      case AGILENT_TUNE_NEG_601 -> new double[]{601.978977};
      case AGILENT_TUNE_NEG_1033 -> new double[]{1033.98811};
    };
  }

  @Override
  public @NotNull String getUniqueID() {
    return switch (this) {
      case CUSTOM -> "custom";
      case LEUCINE_ENKEPHALIN_POS -> "leucine_enkephalin_pos";
      case LEUCINE_ENKEPHALIN_NEG -> "leucine_enkephalin_neg";
      case AGILENT_TUNE_POS_322 -> "agilent_pos_322";
      case AGILENT_TUNE_POS_622 -> "agilent_pos_622";
      case AGILENT_TUNE_POS_922 -> "agilent_pos_922";
      case AGILENT_TUNE_NEG_301 -> "agilent_neg_301";
      case AGILENT_TUNE_NEG_601 -> "agilent_neg_601";
      case AGILENT_TUNE_NEG_1033 -> "agilent_neg_1033";
    };
  }

  @Override
  public String toString() {
    return switch (this) {
      case CUSTOM -> "Custom m/z";
      case LEUCINE_ENKEPHALIN_POS -> "Leucine enkephalin [M+H]+ (556.2766)";
      case LEUCINE_ENKEPHALIN_NEG -> "Leucine enkephalin [M-H]- (554.2620)";
      case AGILENT_TUNE_POS_322 -> "Agilent Tune [M+H]+ (322.0481)";
      case AGILENT_TUNE_POS_622 -> "Agilent Tune [M+H]+ (622.0290)";
      case AGILENT_TUNE_POS_922 -> "Agilent Tune [M+H]+ (922.0098)";
      case AGILENT_TUNE_NEG_301 -> "Agilent Tune [M-H]- (301.9981)";
      case AGILENT_TUNE_NEG_601 -> "Agilent Tune [M-H]- (601.9790)";
      case AGILENT_TUNE_NEG_1033 -> "Agilent Tune [M-H]- (1033.9881)";
    };
  }
}
