package org.trostheide.gantry.watercolor;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;
import org.trostheide.gantry.model.command.RefillCommand;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LayerRefillPlannerTest {
    @Test void assignsStationAndSplitsAtConfiguredDistance() {
        Layer input = new Layer("ink", "old", "#123456", 100, List.of(
                new RefillCommand(1, "old"), new MoveCommand(2, 0, 0),
                new DrawCommand(3, List.of(new Point(0, 0), new Point(25, 0)))));

        Layer result = LayerRefillPlanner.configure(input, "blue-pot", 10);

        assertEquals("blue-pot", result.stationId());
        assertEquals(10, result.maxDrawDistance());
        assertEquals(3, result.commands().stream().filter(RefillCommand.class::isInstance).count());
        assertTrue(result.commands().stream().filter(RefillCommand.class::isInstance)
                .map(RefillCommand.class::cast).allMatch(r -> r.stationId.equals("blue-pot")));
    }

    @Test void zeroDistanceRemovesAutomaticRefills() {
        Layer input = new Layer("ink", "old", null, 10, List.of(
                new RefillCommand(1, "old"), new DrawCommand(2,
                List.of(new Point(0, 0), new Point(5, 0)))));
        Layer result = LayerRefillPlanner.configure(input, "new", 0);
        assertEquals(0, result.commands().stream().filter(RefillCommand.class::isInstance).count());
    }
}
