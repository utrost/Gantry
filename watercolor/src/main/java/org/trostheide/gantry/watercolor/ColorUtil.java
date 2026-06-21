package org.trostheide.gantry.watercolor;

/** Hex-colour parsing and a cheap perceptual colour-distance for paint-station matching. */
public final class ColorUtil {

    private ColorUtil() {
    }

    /**
     * Parses a {@code #rgb} or {@code #rrggbb} hex string (with or without the leading {@code #})
     * into {@code {r, g, b}} 0-255, or {@code null} if it can't be parsed.
     */
    public static int[] parseHex(String hex) {
        if (hex == null) {
            return null;
        }
        String h = hex.trim();
        if (h.startsWith("#")) {
            h = h.substring(1);
        }
        try {
            if (h.length() == 3) {
                int r = Integer.parseInt(h.substring(0, 1), 16);
                int g = Integer.parseInt(h.substring(1, 2), 16);
                int b = Integer.parseInt(h.substring(2, 3), 16);
                return new int[] {r * 17, g * 17, b * 17};
            }
            if (h.length() == 6) {
                int r = Integer.parseInt(h.substring(0, 2), 16);
                int g = Integer.parseInt(h.substring(2, 4), 16);
                int b = Integer.parseInt(h.substring(4, 6), 16);
                return new int[] {r, g, b};
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return null;
    }

    /**
     * Perceptual distance between two RGB colours using the "redmean" weighting
     * (a cheap, surprisingly good approximation to CIELAB ΔE without a full colour-space
     * conversion). Smaller is closer. Returns {@link Double#MAX_VALUE} if either is unparseable.
     */
    public static double distance(String hexA, String hexB) {
        int[] a = parseHex(hexA);
        int[] b = parseHex(hexB);
        if (a == null || b == null) {
            return Double.MAX_VALUE;
        }
        double rMean = (a[0] + b[0]) / 2.0;
        double dr = a[0] - b[0];
        double dg = a[1] - b[1];
        double db = a[2] - b[2];
        double weightR = 2 + rMean / 256.0;
        double weightG = 4.0;
        double weightB = 2 + (255 - rMean) / 256.0;
        return Math.sqrt(weightR * dr * dr + weightG * dg * dg + weightB * db * db);
    }
}
