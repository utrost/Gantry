package org.trostheide.gantry.app.gui;

import java.awt.Color;

/** Deterministic colour policy for the dark plot-preview canvas. */
final class CanvasPalette {
    static final Color BACKGROUND = new Color(35, 35, 40);
    static final Color DEFAULT_PATH = new Color(130, 160, 255);
    private static final Color[] FALLBACK = {
            new Color(130, 160, 255), new Color(255, 140, 120), new Color(120, 220, 150),
            new Color(230, 200, 110), new Color(210, 130, 230), new Color(120, 220, 220),
            new Color(240, 150, 190), new Color(170, 200, 120)
    };

    private CanvasPalette() { }

    static Color displayColor(String hex, int index) {
        Color parsed = parseHex(hex);
        if (parsed == null) return fallback(index);
        double brightness = 0.299 * parsed.getRed() + 0.587 * parsed.getGreen() + 0.114 * parsed.getBlue();
        if (brightness >= 70) return parsed;
        int max = Math.max(parsed.getRed(), Math.max(parsed.getGreen(), parsed.getBlue()));
        int min = Math.min(parsed.getRed(), Math.min(parsed.getGreen(), parsed.getBlue()));
        if (max - min < 24) return fallback(index);
        double lift = 0.55;
        return new Color(lift(parsed.getRed(), lift), lift(parsed.getGreen(), lift), lift(parsed.getBlue(), lift));
    }

    static Color ghost(Color color) {
        double keep = 0.30;
        return new Color(blend(color.getRed(), BACKGROUND.getRed(), keep),
                blend(color.getGreen(), BACKGROUND.getGreen(), keep),
                blend(color.getBlue(), BACKGROUND.getBlue(), keep));
    }

    static Color parseHex(String hex) {
        if (hex == null) return null;
        String value = hex.trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (value.length() == 3) {
            value = "" + value.charAt(0) + value.charAt(0) + value.charAt(1) + value.charAt(1)
                    + value.charAt(2) + value.charAt(2);
        }
        if (value.length() != 6) return null;
        try {
            return new Color(Integer.parseInt(value.substring(0, 2), 16),
                    Integer.parseInt(value.substring(2, 4), 16), Integer.parseInt(value.substring(4, 6), 16));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Color fallback(int index) { return FALLBACK[Math.floorMod(index, FALLBACK.length)]; }
    private static int lift(int channel, double amount) {
        return (int) Math.round(channel + (255 - channel) * amount);
    }
    private static int blend(int foreground, int background, double keep) {
        return (int) Math.round(foreground * keep + background * (1 - keep));
    }
}
