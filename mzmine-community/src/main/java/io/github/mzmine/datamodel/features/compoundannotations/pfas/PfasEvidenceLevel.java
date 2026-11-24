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
 * Evidence strength of a matched fragment following Charbonnet et al. 2022
 * (doi:10.1021/acs.estlett.2c00206).
 */
public enum PfasEvidenceLevel {
  /**
   * Attributable to a specific substructure, counts towards level 2b/2c.
   */
  DIAGNOSTIC,
  /**
   * Associated with substructures of a subclass but shared by several head groups, e.g. C3H8N+.
   * Supports level 3b/3c.
   */
  SUBCLASS_ALIGNED,
  /**
   * Generic fluorocarbon or common ion, e.g. CF3-. Never sufficient alone.
   */
  GENERIC
}
