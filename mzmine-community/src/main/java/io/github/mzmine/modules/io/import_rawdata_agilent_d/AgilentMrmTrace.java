package io.github.mzmine.modules.io.import_rawdata_agilent_d;

import org.jetbrains.annotations.NotNull;

/**
 * One MRM/SRM transition of an Agilent .d file.
 *
 * @param q1        precursor m/z
 * @param q3        product m/z
 * @param rt        retention times (min)
 * @param intensity intensities
 */
public record AgilentMrmTrace(double q1, double q3, float @NotNull [] rt,
                              double @NotNull [] intensity) {

}
