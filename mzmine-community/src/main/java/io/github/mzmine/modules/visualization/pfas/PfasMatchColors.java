package io.github.mzmine.modules.visualization.pfas;

import io.github.mzmine.javafx.util.FxColorUtil;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.util.color.SimpleColorPalette;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
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
    final Color neutral = FxColorUtil.fxColorToAWT(palette.getNeutralColor());
    // decision: saturated colors only, black or gray would hide the structure highlight
    final List<Color> others = new ArrayList<>(
        palette.getColorsAWT().stream().filter(c -> !c.equals(rule) && !c.equals(neutral))
            .filter(c -> Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null)[1] >= 0.4f)
            .limit(2).toList());
    while (others.size() < 2) {
      others.add(Color.GRAY);
    }
    return new PfasMatchColors(palette.getNeutralColor(), rule, others.getFirst(), others.get(1));
  }
}
