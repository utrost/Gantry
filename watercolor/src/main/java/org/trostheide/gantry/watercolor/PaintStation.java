package org.trostheide.gantry.watercolor;

/** Paint palette entry with enough metadata to exclude water/cleaning pots. */
public record PaintStation(String id, String colorHex, String behavior) {
    public PaintStation(String id, String colorHex) { this(id, colorHex, null); }

    /** Shared legacy-name and explicit-behavior rule for mapping and between-layer rinsing. */
    public static boolean isRinse(String id, String behavior) {
        return "rinse".equals(behavior) || "rinse".equalsIgnoreCase(id) || "water".equalsIgnoreCase(id);
    }

    public boolean isRinse() { return isRinse(id, behavior); }
}
