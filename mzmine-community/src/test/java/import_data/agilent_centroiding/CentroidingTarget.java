package import_data.agilent_centroiding;

import io.github.mzmine.datamodel.IonizationType;
import io.github.mzmine.datamodel.IsotopePattern;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.modules.tools.isotopeprediction.IsotopePatternCalculator;
import io.github.mzmine.util.FormulaUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openscience.cdk.interfaces.IMolecularFormula;

/**
 * A benchmark target. The m/z is always the exact (calculated) m/z of the ion formula, never a
 * measured value.
 *
 * @param name                 compound name
 * @param neutralFormula       neutral molecular formula
 * @param ionType              adduct
 * @param ionFormula           ionized formula string (with charge)
 * @param exactMz              calculated m/z of the monoisotopic ion
 * @param expectedM1Ratio      predicted intensity ratio M+1 / M (unresolved M+1 cluster summed)
 * @param vendorSaturationFlag saturation flag reported by vendor software for this target, null
 *                             if not available
 */
public record CentroidingTarget(@NotNull String name, @NotNull String neutralFormula,
                                @NotNull IonizationType ionType, @NotNull String ionFormula,
                                double exactMz, double expectedM1Ratio,
                                @Nullable Boolean vendorSaturationFlag) {

  /**
   * Distance between M and M+1, the 13C isotope dominates the unresolved M+1 cluster.
   */
  public static final double M1_DISTANCE = IsotopePatternCalculator.THIRTHEEN_C_DISTANCE;

  public @NotNull PolarityType polarity() {
    return ionType.getPolarity();
  }

  public int absCharge() {
    return Math.abs(ionType.getCharge());
  }

  public double m1Mz() {
    return exactMz + M1_DISTANCE / absCharge();
  }

  /**
   * @return the target or null if the formula cannot be ionized with this adduct
   */
  public static @Nullable CentroidingTarget create(@NotNull final String name,
      @NotNull final String neutralFormula, @NotNull final IonizationType ionType,
      @Nullable final Boolean vendorSaturationFlag) {
    final IMolecularFormula ion = ionType.ionizeFormula(neutralFormula).orElse(null);
    if (ion == null) {
      return null;
    }
    final double mz = FormulaUtils.calculateMzRatio(ion);
    final double ratio = expectedM1Ratio(ion, mz, Math.abs(ionType.getCharge()),
        ionType.getPolarity());
    return new CentroidingTarget(name, neutralFormula, ionType,
        FormulaUtils.getFormulaString(ion, true), mz, ratio, vendorSaturationFlag);
  }

  private static double expectedM1Ratio(@NotNull final IMolecularFormula ion, final double mz,
      final int charge, @NotNull final PolarityType polarity) {
    final IsotopePattern pattern = IsotopePatternCalculator.calculateIsotopePattern(ion, 0.0001,
        AgilentCentroidingTestConfig.ISOTOPE_MERGE_WIDTH, charge, polarity, false);
    final double m = intensityNear(pattern, mz);
    final double m1 = intensityNear(pattern, mz + M1_DISTANCE / charge);
    return m > 0 ? m1 / m : Double.NaN;
  }

  private static double intensityNear(@NotNull final IsotopePattern pattern, final double mz) {
    // the merged pattern peaks are 1 Da apart, half the distance is a safe window
    final double window = 0.3;
    double intensity = 0;
    for (int i = 0; i < pattern.getNumberOfDataPoints(); i++) {
      if (Math.abs(pattern.getMzValue(i) - mz) <= window) {
        intensity = Math.max(intensity, pattern.getIntensityValue(i));
      }
    }
    return intensity;
  }
}
