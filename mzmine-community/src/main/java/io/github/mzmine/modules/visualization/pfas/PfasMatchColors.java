package io.github.mzmine.modules.visualization.pfas;

import io.github.mzmine.javafx.util.FxColorUtil;
import io.github.mzmine.javafx.util.color.ColorsFX;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.util.color.SimpleColorPalette;
import java.awt.Color;
import org.jetbrains.annotations.NotNull;

/**
 * Colors of the PFAS match spectrum datasets, shared with the structure highlights so a highlighted
 * substructure has the color of the selected peak.
 *
 * @param spectrum     the full MS/MS spectrum, an FX color as the spectrum provider takes it
 * @param rule         rule-based signals
 * @param substructure sub-formulas that are substructures of the structure
 * @param subFormula   other sub-formulas of the precursor ion
 */
record PfasMatchColors(@NotNull javafx.scene.paint.Color spectrum, @NotNull Color rule,
                       @NotNull Color substructure, @NotNull Color subFormula) {

  /**
   * @return colors of the default color palette
   */
  static @NotNull PfasMatchColors ofDefaultPalette() {
    final SimpleColorPalette palette = ConfigService.getConfiguration().getDefaultColorPalette();
    final Color rule = FxColorUtil.fxColorToAWT(palette.getPositiveColor());
    // decision: fixed saturated colors, palette colors can be black or gray and hide the highlight
    final Color substructure = FxColorUtil.fxColorToAWT(ColorsFX.NEGATIVE_MARKER_COLORBLIND);
    final Color subFormula = FxColorUtil.fxColorToAWT(ColorsFX.YELLOW_WARN);
    return new PfasMatchColors(palette.getNeutralColor(), rule, substructure, subFormula);
  }
}
