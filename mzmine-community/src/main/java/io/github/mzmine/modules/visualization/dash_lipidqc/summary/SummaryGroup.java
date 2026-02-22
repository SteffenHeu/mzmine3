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

package io.github.mzmine.modules.visualization.dash_lipidqc.summary;

import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import org.jetbrains.annotations.NotNull;

enum SummaryGroup {
  LIPID_SUBCLASS("Lipid subclass") {
    @Override
    @NotNull
    String extractGroupLabel(final @NotNull MatchedLipid lipid, final @NotNull String subclassToken) {
      return subclassToken;
    }

    @Override
    @NotNull
    String extractTooltip(final @NotNull MatchedLipid lipid, final @NotNull String subclassToken) {
      return lipid.getLipidAnnotation().getLipidClass().getName();
    }
  }, LIPID_MAIN_CLASS("Lipid main class") {
    @Override
    @NotNull
    String extractGroupLabel(final @NotNull MatchedLipid lipid, final @NotNull String subclassToken) {
      return lipid.getLipidAnnotation().getLipidClass().getMainClass().getName();
    }
  }, LIPID_CATEGORY("Lipid category") {
    @Override
    @NotNull
    String extractGroupLabel(final @NotNull MatchedLipid lipid, final @NotNull String subclassToken) {
      return lipid.getLipidAnnotation().getLipidClass().getMainClass().getLipidCategory()
          .getName();
    }
  };

  private final @NotNull String axisLabel;

  SummaryGroup(final @NotNull String axisLabel) {
    this.axisLabel = axisLabel;
  }

  @NotNull String getAxisLabel() {
    return axisLabel;
  }

  abstract @NotNull String extractGroupLabel(@NotNull MatchedLipid lipid,
      @NotNull String subclassToken);

  @NotNull String extractTooltip(final @NotNull MatchedLipid lipid,
      final @NotNull String subclassToken) {
    return extractGroupLabel(lipid, subclassToken);
  }

  @Override
  public @NotNull String toString() {
    return axisLabel;
  }
}

