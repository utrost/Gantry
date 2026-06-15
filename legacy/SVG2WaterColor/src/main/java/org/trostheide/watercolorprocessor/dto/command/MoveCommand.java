package org.trostheide.watercolorprocessor.dto.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a non-drawing travel move (Pen Up).
 * @param id Sequential command ID.
 * @param x Target X coordinate (mm).
 * @param y Target Y coordinate (mm).
 */
public final class MoveCommand extends Command {
    public final int id;
    public final double x;
    public final double y;

    @JsonCreator
    public MoveCommand(@JsonProperty("id") int id, @JsonProperty("x") double x, @JsonProperty("y") double y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }

    @Override
    public int getId() {
        return id;
    }
}