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

package io.github.mzmine.modules.visualization.dash_lipidqc.panes;

import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.types.annotations.LipidMatchListType;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.RunOption;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.modules.visualization.spectra.matchedlipid.LipidSpectrumPlot;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MatchedSignalsPane extends BorderPane {

  private final @NotNull Label placeholder = new Label("Select a row with matched lipid signals.");
  private @Nullable FeatureListRow row;
  private @Nullable LipidSpectrumPlot spectrumPlot;

  public MatchedSignalsPane() {
    setCenter(placeholder);
    BorderPane.setAlignment(placeholder, Pos.CENTER);
  }

  public void setFeatureList(final @Nullable ModularFeatureList featureList) {
  }

  public void setRow(final @Nullable FeatureListRow row) {
    this.row = row;
    update();
  }

  private void update() {
    if (row == null) {
      showPlaceholder("Select a row with matched lipid signals.");
      return;
    }

    final List<MatchedLipid> matches = row.get(LipidMatchListType.class);
    if (matches == null || matches.isEmpty()) {
      showPlaceholder("No matched lipid signals available for selected row.");
      return;
    }

    final MatchedLipid match = matches.getFirst();
    if (spectrumPlot == null) {
      spectrumPlot = new LipidSpectrumPlot(match, true, RunOption.THIS_THREAD);
    } else {
      spectrumPlot.updateLipidSpectrum(match, true, RunOption.THIS_THREAD);
    }
    setCenter(spectrumPlot);
  }

  private void showPlaceholder(final @NotNull String text) {
    placeholder.setText(text);
    setCenter(placeholder);
  }
}
