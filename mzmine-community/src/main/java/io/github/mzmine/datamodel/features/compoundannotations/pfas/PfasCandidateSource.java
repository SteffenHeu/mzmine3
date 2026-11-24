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
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/**
 * Where a PFAS candidate comes from. Reported with each annotation so literature compounds,
 * extrapolated homologues and theoretical combinations stay distinguishable.
 */
public enum PfasCandidateSource implements UniqueIdSupplier {
  /**
   * A literature-reported compound of the catalogue.
   */
  KNOWN,
  /**
   * A homologue of a catalogue class at a chain length without a curated compound entry.
   */
  HOMOLOGUE,
  /**
   * A theoretical combination of building blocks that is not a catalogue class.
   */
  COMBINATORIAL,
  /**
   * A user-defined class of a custom library.
   */
  CUSTOM,
  /**
   * Building blocks detected in an MS2 spectrum without a precursor candidate.
   */
  SUBSTRUCTURE;

  @Override
  public @NotNull String getUniqueID() {
    // never change, used in project files
    return name().toLowerCase(Locale.ROOT);
  }

  @Override
  public String toString() {
    return switch (this) {
      case KNOWN -> "Known compound";
      case HOMOLOGUE -> "Homologue";
      case COMBINATORIAL -> "Theoretical combination";
      case CUSTOM -> "Custom";
      case SUBSTRUCTURE -> "MS2 substructures";
    };
  }
}
