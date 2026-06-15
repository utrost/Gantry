package org.trostheide.watercolorprocessor.dto.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.trostheide.watercolorprocessor.dto.Point;
import java.util.List;

/**
 * Represents a drawing action (Pen Down) consisting of multiple connected segments.
 * @param id Sequential command ID.
 * @param points Ordered list of vertices for this polyline.
 */
public final class DrawCommand extends Command {
    public final int id;
    public final List<Point> points;

    @JsonCreator
    public DrawCommand(@JsonProperty("id") int id, @JsonProperty("points") List<Point> points) {
        this.id = id;
        this.points = points;
    }

    @Override
    public int getId() {
        return id;
    }
}