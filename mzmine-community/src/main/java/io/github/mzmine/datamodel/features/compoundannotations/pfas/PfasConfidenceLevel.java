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

package io.github.mzmine.datamodel.features.compoundannotations.pfas;

import io.github.mzmine.datamodel.utils.UniqueIdSupplier;
import org.jetbrains.annotations.NotNull;

/**
 * PFAS identification confidence levels of Charbonnet et al. 2022
 * (doi:10.1021/acs.estlett.2c00206), ordered from most to least confident. The annotation module
 * assigns 2b, 3a, 3b and 5a from MS2 evidence. MS2-only substructure
 * annotations have no precursor candidate and get {@link #MS2_SUBSTRUCTURE} without a number.
 * <p>
 * decision: levels the modules do not assign are commented out, so the exhaustive switches of the
 * annotation ranking and level mappings only cover used levels. Adding one back needs those
 * switches updated, e.g. in AnnotationSummary.
 */
public enum PfasConfidenceLevel implements UniqueIdSupplier {
  // LEVEL_1A("1a", "Confirmed by reference standard"), //
  // LEVEL_1B("1b", "Indistinguishable from reference standard"), //
  // LEVEL_2A("2a", "Probable by library spectrum match"), //
  LEVEL_2B("2b", "Probable by diagnostic fragmentation evidence"), //
  // LEVEL_2C("2c", "Probable by diagnostic homologue evidence"), //
  LEVEL_3A("3a", "Positional isomer candidates"), //
  LEVEL_3B("3b", "Fragmentation-based candidates"), //
  // LEVEL_3C("3c", "Circumstantial candidates by fragmentation evidence"), //
  // LEVEL_3D("3d", "Circumstantial candidates by homologue evidence"), //
  // LEVEL_4("4", "Unequivocal molecular formula"), //
  LEVEL_5A("5a", "PFAS suspect screening exact mass match"), //
  // LEVEL_5B("5b", "Nontarget PFAS exact mass of interest"), //
  /**
   * Building blocks from MS2 fragments without a precursor candidate, no Charbonnet level
   */
  MS2_SUBSTRUCTURE("ms2_substructure", "MS2 substructure fragments");

  /**
   * The publication of the confidence levels, cited wherever a level is shown. Same source id as
   * in the PFAS catalogue so both share one reference number.
   */
  public static final @NotNull PfasCitation CITATION = new PfasCitation("CH22",
      "Charbonnet et al., Communicating Confidence of Per- and Polyfluoroalkyl Substance "
          + "Identification via High-Resolution Mass Spectrometry, Environ. Sci. Technol. Lett. "
          + "2022, 9, 473−481", "https://doi.org/10.1021/acs.estlett.2c00206", null,
      "PFAS confidence levels");

  private final @NotNull String code;
  private final @NotNull String description;

  PfasConfidenceLevel(@NotNull String code, @NotNull String description) {
    this.code = code;
    this.description = description;
  }

  /**
   * @param other other level
   * @return true if this level is at least as confident as other
   */
  public boolean isAtLeast(@NotNull PfasConfidenceLevel other) {
    return ordinal() <= other.ordinal();
  }

  /**
   * @return the short code, e.g. 2b
   */
  public @NotNull String code() {
    return code;
  }

  /**
   * @return the level description
   */
  public @NotNull String description() {
    return description;
  }

  @Override
  public @NotNull String getUniqueID() {
    return code;
  }

  @Override
  public String toString() {
    return switch (this) {
      case MS2_SUBSTRUCTURE -> description;
      case LEVEL_2B, LEVEL_3A, LEVEL_3B, LEVEL_5A ->
          "Level %s: %s".formatted(code, description);
    };
  }
}
