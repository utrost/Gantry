package org.trostheide.watercolorprocessor.dto.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a request to refill the brush at a specific station.
 * @param id Sequential command ID.
 * @param stationId The logical ID of the station (mapped in Python config).
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