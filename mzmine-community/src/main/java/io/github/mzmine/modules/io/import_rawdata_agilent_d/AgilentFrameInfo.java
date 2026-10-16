package io.github.mzmine.modules.io.import_rawdata_agilent_d;

import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.msms.IonMobilityMsMsInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Metadata of one Agilent IMS frame, independent of the reader.
 *
 * @param rt                     retention time (min)
 * @param polarity               polarity
 * @param msLevel                1 = low energy, 2 = high energy (fragmentation)
 * @param mobilityScanAbundLimit max possible abundance of a mobility scan point (detector
 *                               saturation level), NaN if not stored
 * @param msMsInfo               fragmentation info of MS2 frames, null for MS1
 */
public record AgilentFrameInfo(float rt, @NotNull PolarityType polarity, int msLevel,
                               double mobilityScanAbundLimit,
                               @Nullable IonMobilityMsMsInfo msMsInfo) {

}
