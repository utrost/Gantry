package org.trostheide.gantry.app.plot;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A refill station's position, dip depth, refill behavior and (for watercolor) paint colour.
 *
 * @param x station X in mm.
 * @param y station Y in mm.
 * @param zDown dip depth (Z-axis machines only; informational on servo setups).
 * @param behavior "simple_dip" (dip and lift), "dip_swirl" (dip, then swirl the brush in a
 *                 circle of {@code swirlRadius} before lifting), or "rinse" (a water/clean pot
 *                 visited between colours to clean the brush).
 * @param color the paint colour at this station as a {@code #rrggbb} hex string, or {@code null}.
 *              Used to map a drawing layer's colour to the nearest physical pot.
 * @param dwellMs how long to hold the brush down in the pot, in milliseconds.
 * @param swirlRadius radius of the circular swirl, in mm, for "dip_swirl"/"rinse" behaviours.
 */
public record StationConfig(double x, double y, int zDown, String behavior,
        String color, int dwellMs, double swirlRadius) {

    /** Default dwell when none is configured (matches the legacy hard-coded value). */
    public static final int DEFAULT_DWELL_MS = 500;
    /** Default swirl radius when none is configured (matches the legacy hard-coded value). */
    public static final double DEFAULT_SWIRL_RADIUS = 2.0;

    @JsonCreator
    public StationConfig(
            @JsonProperty("x") double x,
            @JsonProperty("y") double y,
            @JsonProperty("z_down") int zDown,
            @JsonProperty("behavior") String behavior,
            @JsonProperty("color") String color,
            @JsonProperty("dwell_ms") int dwellMs,
            @JsonProperty("swirl_radius") double swirlRadius) {
        this.x = x;
        this.y = y;
        this.zDown = zDown;
        this.behavior = behavior;
        this.color = color;
        // Normalise so old config.json entries (missing these fields -> 0) get sensible defaults.
        this.dwellMs = dwellMs > 0 ? dwellMs : DEFAULT_DWELL_MS;
        this.swirlRadius = swirlRadius > 0 ? swirlRadius : DEFAULT_SWIRL_RADIUS;
    }

    /** Backward-compatible constructor: no colour, default dwell and swirl radius. */
    public StationConfig(double x, double y, int zDown, String behavior) {
        this(x, y, zDown, behavior, null, DEFAULT_DWELL_MS, DEFAULT_SWIRL_RADIUS);
    }
}
