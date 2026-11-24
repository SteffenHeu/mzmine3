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

/**
 * Results of the homologue series refinement. Flags never delete annotations; users decide.
 */
public enum PfasRefinementFlag {
  /**
   * Retention time fits the homologue series of the class.
   */
  SERIES_RT_CONSISTENT,
  /**
   * Retention time does not fit the homologue series, likely false positive.
   */
  RT_OUTLIER,
  /**
   * Elutes earlier than the linear homologue of the same formula, likely a branched isomer.
   */
  BRANCHED_ISOMER,
  /**
   * Retention times of the series do not increase with the chain length.
   */
  SERIES_RT_NOT_INCREASING,
  /**
   * MS1-only feature at the mass and retention time predicted for a missing homologue.
   */
  SERIES_GAP_CANDIDATE,
  /**
   * The MS1 isotope pattern shows the chlorine M+2 signal of the formula.
   */
  CL_ISOTOPE_SUPPORTED,
  /**
   * The MS1 isotope pattern contradicts the chlorine count of the formula.
   */
  CL_ISOTOPE_CONTRADICTED;

  @Override
  public String toString() {
    return switch (this) {
      case SERIES_RT_CONSISTENT -> "RT fits homologue series";
      case RT_OUTLIER -> "RT outlier of homologue series";
      case BRANCHED_ISOMER -> "branched isomer";
      case SERIES_RT_NOT_INCREASING -> "series RT not increasing";
      case SERIES_GAP_CANDIDATE -> "MS1 homologue series candidate";
      case CL_ISOTOPE_SUPPORTED -> "Cl isotope pattern supported";
      case CL_ISOTOPE_CONTRADICTED -> "Cl isotope pattern contradicted";
    };
  }
}
