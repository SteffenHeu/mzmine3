package io.github.mzmine.modules.io.import_rawdata_agilent_d;

import org.jetbrains.annotations.NotNull;

/**
 * One non-MS detector signal (DAD/UV chromatogram, pump or column oven curve) of an Agilent .d
 * file.
 *
 * @param deviceType  device type as named by MassHunter, e.g. "BinaryPump", "DiodeArrayDetector"
 * @param description signal description, e.g. "Pressure" or "Sig=254,4  Ref=350,20"
 * @param rt          retention times (min)
 * @param values      signal values
 */
public record AgilentAnalogTrace(@NotNull String deviceType, @NotNull String description,
                                 float @NotNull [] rt, double @NotNull [] values) {

}
