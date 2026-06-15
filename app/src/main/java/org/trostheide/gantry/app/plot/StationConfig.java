package org.trostheide.gantry.app.plot;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A refill station's position, dip depth and refill behavior.
 *
 * @param behavior "simple_dip" (dip and lift) or "dip_swirl" (dip, swirl +-2mm, lift).
 */
public record StationConfig(double x, double y, int zDown, String behavior) {

    @JsonCreator
    public StationConfig(
            @JsonProperty("x") double x,
            @JsonProperty("y") double y,
            @JsonProperty("z_down") int zDown,
            @JsonProperty("behavior") String behavior) {
        this.x = x;
        this.y = y;
        this.zDown = zDown;
        this.behavior = behavior;
    }
}
