package org.trostheide.gantry.app.gui;

import java.awt.geom.Rectangle2D;

/** Shared paper-size presets (in px, at 96dpi) for the SVGToolBox "crop" option, used by both
 * {@link SvgImportDialog} and {@link EditProcessDialog}. */
final class PaperSizes {

    static final double A4_WIDTH = 793.7;
    static final double A4_HEIGHT = 1122.5;
    static final double LETTER_WIDTH = 816.0;
    static final double LETTER_HEIGHT = 1056.0;

    private PaperSizes() {
    }

    /** Resolves a "None"/"A4"/"Letter"/"Custom" crop selection, parsing {@code customField} for "Custom". */
    static Rectangle2D resolve(String selection, String customField) {
        switch (selection) {
            case "A4":
                return new Rectangle2D.Double(0, 0, A4_WIDTH, A4_HEIGHT);
            case "Letter":
                return new Rectangle2D.Double(0, 0, LETTER_WIDTH, LETTER_HEIGHT);
            case "Custom":
                try {
                    String[] parts = customField.trim().split("x");
                    double w = Double.parseDouble(parts[0]);
                    double h = Double.parseDouble(parts[1]);
                    return new Rectangle2D.Double(0, 0, w, h);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Custom crop must be 'WxH' in px, e.g. 793.7x1122.5.");
                }
            default:
                return null;
        }
    }
}
