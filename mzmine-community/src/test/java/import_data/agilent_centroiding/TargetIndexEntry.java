package import_data.agilent_centroiding;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One target in one raw file: where its signal is most intense (frame and mobility scan) and
 * whether its isotope ratio indicates detector saturation. Intensities are centroid heights of the
 * centroided import used for indexing.
 *
 * @param rawFile                path of the .d folder relative to the dataset folder
 * @param exactMz                calculated m/z of the target ion
 * @param expectedRatio          predicted M+1 / M ratio
 * @param vendorSaturationFlag   saturation flag of the vendor software, null if unknown
 * @param frameId                1-based frame id as used by the Agilent reader
 * @param frameRt                retention time of the frame in min
 * @param frameRatio             observed M+1 / M in the total frame spectrum, NaN if M+1 missing
 * @param mobilityScanIndex      0-based index of the most intense mobility scan in the frame
 * @param mobility               drift time of that mobility scan in ms
 * @param mobilityRatio          observed M+1 / M in the mobility scan, NaN if M+1 missing
 * @param frameRatioDeviation    frameRatio / expectedRatio - 1
 * @param mobilityRatioDeviation mobilityRatio / expectedRatio - 1
 * @param potentiallySaturated   M+1/M ratio higher than expected by more than the tolerance in
 *                               the frame or in the mobility scan (clipped monoisotopic peak)
 */
public record TargetIndexEntry(@NotNull String dataset, @NotNull String rawFile,
                               @NotNull String targetName, @NotNull String neutralFormula,
                               @NotNull String adduct, @NotNull String ionFormula, double exactMz,
                               double expectedRatio, @Nullable Boolean vendorSaturationFlag,
                               int frameId, double frameRt, double frameMIntensity,
                               double frameM1Intensity, double frameRatio, int mobilityScanIndex,
                               double mobility, double mobilityMIntensity,
                               double mobilityM1Intensity, double mobilityRatio,
                               double frameRatioDeviation, double mobilityRatioDeviation,
                               boolean potentiallySaturated) {

}
