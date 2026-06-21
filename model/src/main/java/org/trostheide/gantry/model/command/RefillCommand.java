package org.trostheide.gantry.model.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a request to refill the brush at a specific station.
 * @param id Sequential command ID.
 * @param stationId The logical ID of the refill station (resolved against the configured
 *                  stations, or reassigned by colour via the watercolor StationMapper).
 */
public final class RefillCommand extends Command {
    public final int id;
    public final String stationId;

    @JsonCreator
    public RefillCommand(@JsonProperty("id") int id, @JsonProperty("stationId") String stationId) {
        this.id = id;
        this.stationId = stationId;
    }

    @Override
    public int getId() {
        return id;
    }
}
