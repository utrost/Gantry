package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.pipeline.svgimport.PaperFormat;

/** Safe beginner defaults for fitting new artwork to the configured machine bed. */
final class ArtworkImportPolicy {
    static final double DEFAULT_PADDING_MM = 10.0;
    private static final double FALLBACK_WIDTH_MM = 300.0;
    private static final double FALLBACK_HEIGHT_MM = 200.0;

    private ArtworkImportPolicy() { }

    static PaperFormat machineBed(double width, double height) {
        double safeWidth = Double.isFinite(width) && width > 0 ? width : FALLBACK_WIDTH_MM;
        double safeHeight = Double.isFinite(height) && height > 0 ? height : FALLBACK_HEIGHT_MM;
        return new PaperFormat(safeWidth, safeHeight);
    }

    static String summary(PaperFormat bed, double padding) {
        return String.format("Fit safely inside the %.0f × %.0f mm machine bed with a %.0f mm margin.",
                bed.width(), bed.height(), padding);
    }
}
