package org.trostheide.gantry.pipeline.svgimport;

/**
 * Common paper sizes (in mm) for the "fit to page" option, plus a parser for
 * the {@code A5}/{@code A4}/{@code A3}/{@code XL}/{@code WxH} format strings used by the
 * legacy SVG2WaterColor CLI and GUI.
 */
public record PaperFormat(double width, double height) {
    public static final PaperFormat A6 = new PaperFormat(105, 148);
    public static final PaperFormat A5 = new PaperFormat(148, 210);
    public static final PaperFormat A4 = new PaperFormat(210, 297);
    public static final PaperFormat A3 = new PaperFormat(297, 420);
    public static final PaperFormat A2 = new PaperFormat(420, 594);
    public static final PaperFormat A1 = new PaperFormat(594, 841);
    public static final PaperFormat XL = new PaperFormat(430, 600);

    /**
     * Parses {@code "A6"}, {@code "A5"}, {@code "A4"}, {@code "A3"}, {@code "A2"}, {@code "A1"},
     * {@code "XL"} (case-insensitive), or a custom {@code "WxH"} size in mm (e.g. {@code "300x400"}).
     * Returns {@code null} for {@code null} input or anything unrecognized.
     */
    public static PaperFormat fromString(String s) {
        if (s == null) {
            return null;
        }
        switch (s.toUpperCase()) {
            case "A6":
                return A6;
            case "A5":
                return A5;
            case "A4":
                return A4;
            case "A3":
                return A3;
            case "A2":
                return A2;
            case "A1":
                return A1;
            case "XL":
                return XL;
            default:
                if (s.contains("x")) {
                    String[] parts = s.split("x");
                    if (parts.length == 2) {
                        try {
                            return new PaperFormat(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
                        } catch (NumberFormatException ignored) {
                            // fall through to null
                        }
                    }
                }
                return null;
        }
    }
}
