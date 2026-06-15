package org.trostheide.gantry.pipeline.optimize;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.Bounds;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Metadata;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;
import org.trostheide.gantry.model.command.RefillCommand;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptimizeStageTest {

    private static ProcessorOutput output(Layer... layers) {
        Metadata meta = new Metadata("test", Instant.EPOCH, "station", "mm", 0, Bounds.empty());
        return new ProcessorOutput(meta, List.of(layers));
    }

    @Test
    void reorderReducesTravelDistance() {
        // Three strokes laid out so the natural (file) order zig-zags across the bed, but
        // visiting them near-to-far from the origin is much shorter.
        Layer layer = new Layer("L1", "default_station", List.of(
                new MoveCommand(1, 100, 0),
                new DrawCommand(2, List.of(new Point(100, 0), new Point(100, 1))),
                new MoveCommand(3, 0, 0),
                new DrawCommand(4, List.of(new Point(0, 0), new Point(0, 1))),
                new MoveCommand(5, 50, 0),
                new DrawCommand(6, List.of(new Point(50, 0), new Point(50, 1)))));

        ProcessorOutput input = output(layer);
        OptimizeStage.Stats before = OptimizeStage.computeStats(input);

        ProcessorOutput optimized = OptimizeStage.optimize(input, 0, true);
        OptimizeStage.Stats after = OptimizeStage.computeStats(optimized);

        assertTrue(after.travelDistanceMm() < before.travelDistanceMm(),
                "expected reduced travel: before=" + before.travelDistanceMm() + " after=" + after.travelDistanceMm());
    }

    @Test
    void simplifyDropsRedundantCollinearPoints() {
        Layer layer = new Layer("L1", "default_station", List.of(
                new MoveCommand(1, 0, 0),
                new DrawCommand(2, List.of(
                        new Point(0, 0), new Point(1, 0), new Point(2, 0), new Point(3, 0)))));

        ProcessorOutput optimized = OptimizeStage.optimize(output(layer), 0.01, false);

        DrawCommand draw = (DrawCommand) optimized.layers().get(0).commands().get(1);
        assertEquals(List.of(new Point(0, 0), new Point(3, 0)), draw.points);
    }

    @Test
    void preservesStationIdAndRefillPositions() {
        Layer layer = new Layer("L1", "station-a", List.of(
                new RefillCommand(1, "station-a"),
                new MoveCommand(2, 10, 0),
                new DrawCommand(3, List.of(new Point(10, 0), new Point(10, 1))),
                new MoveCommand(4, 0, 0),
                new DrawCommand(5, List.of(new Point(0, 0), new Point(0, 1)))));

        ProcessorOutput optimized = OptimizeStage.optimize(output(layer), 0, true);

        Layer optLayer = optimized.layers().get(0);
        assertEquals("station-a", optLayer.stationId());
        List<Command> commands = optLayer.commands();
        assertTrue(commands.get(0) instanceof RefillCommand, "refill command should stay first");
    }
}
