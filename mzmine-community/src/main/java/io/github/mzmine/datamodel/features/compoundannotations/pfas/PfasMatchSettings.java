package io.github.mzmine.datamodel.features.compoundannotations.pfas;

import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import org.jetbrains.annotations.NotNull;

/**
 * Spectrum matching settings of a PFAS annotation run, read from the applied method of the feature
 * list so explained signals can be rebuilt on demand.
 *
 * @param fragmentTolerance    tolerance between observed peaks and predicted ions
 * @param precursorTolerance   window around the precursor m/z that is excluded from matching
 * @param minRelativeIntensity peaks below this fraction of the base peak are not matched but count
 *                             as unexplained intensity
 */
public record PfasMatchSettings(@NotNull MZTolerance fragmentTolerance,
                                @NotNull MZTolerance precursorTolerance,
                                double minRelativeIntensity) {

}
