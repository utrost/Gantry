package org.trostheide.gantry.pipeline.svgimport;

/**
 * Options for {@link SvgImportStage#importSvg}, mirroring the legacy
 * {@code ProcessorService.process(...)} parameters.
 *
 * @param maxDrawDistance max cumulative draw distance (mm) before a REFILL is inserted;
 *                         {@code <= 0} disables refill splitting (pure pen-plot / "Draw SVG" mode)
 * @param defaultStationId station id used for layers with no explicit station (and for the
 *                          single fallback layer when the SVG has no Inkscape layers)
 * @param curveStep curve linearization step in mm (Batik {@code PathIterator} flatness)
 * @param targetWidth target width in mm for "fit to size", or {@code <= 0} to disable scaling
 * @param targetHeight target height in mm for "fit to size", or {@code <= 0} to disable scaling
 * @param keepAspectRatio if true, scale uniformly (min of X/Y scale) instead of stretching
 * @param posX position offset in mm applied after scaling
 * @param posY position offset in mm applied after scaling
 * @param mirror mirror the drawing horizontally (about the target/content center)
 */
public record SvgImportOptions(
        double maxDrawDistance,
        String defaultStationId,
        double curveStep,
        double targetWidth,
        double targetHeight,
        boolean keepAspectRatio,
        double posX,
        double posY,
        boolean mirror) {

    /** No refill, 0.1mm curve step, no scaling/positioning/mirroring. */
    public static SvgImportOptions defaults() {
        return new SvgImportOptions(0, "default_station", 0.1, 0, 0, true, 0, 0, false);
    }

    /**
     * Builds options that fit the drawing to {@code format} with {@code padding}mm margins on
     * all sides, as used by the legacy CLI's {@code --fit-to}/{@code --padding} flags.
     */
    public static SvgImportOptions fitToFormat(double maxDrawDistance, String defaultStationId,
            double curveStep, PaperFormat format, double padding, boolean mirror) {
        return fitToFormat(maxDrawDistance, defaultStationId, curveStep, format, padding, mirror, true);
    }

    public static SvgImportOptions fitToFormat(double maxDrawDistance, String defaultStationId,
            double curveStep, PaperFormat format, double padding, boolean mirror, boolean keepAspectRatio) {
        double targetW = 0;
        double targetH = 0;
        if (format != null) {
            targetW = format.width() - padding * 2;
            targetH = format.height() - padding * 2;
        }
        return new SvgImportOptions(maxDrawDistance, defaultStationId, curveStep,
                targetW, targetH, keepAspectRatio, 0, 0, mirror);
    }
}
