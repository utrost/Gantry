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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void mergeWeldsStrokesThatTouchEndToEndIntoOnePolyline() {
        // Three segments authored separately but forming one continuous line:
        // (0,0)->(1,0)->(2,0)->(3,0). Each is its own MOVE+DRAW (a redundant pen-up between them).
        Layer layer = new Layer("L1", "default_station", List.of(
                new MoveCommand(1, 0, 0),
                new DrawCommand(2, List.of(new Point(0, 0), new Point(1, 0))),
                new MoveCommand(3, 1, 0),
                new DrawCommand(4, List.of(new Point(1, 0), new Point(2, 0))),
                new MoveCommand(5, 2, 0),
                new DrawCommand(6, List.of(new Point(2, 0), new Point(3, 0)))));

        ProcessorOutput optimized = OptimizeStage.optimize(output(layer), 0, false, 0.01);

        List<Command> commands = optimized.layers().get(0).commands();
        assertEquals(2, commands.size(), "three touching strokes should weld to one MOVE + one DRAW");
        assertTrue(commands.get(0) instanceof MoveCommand);
        DrawCommand draw = (DrawCommand) commands.get(1);
        assertEquals(List.of(new Point(0, 0), new Point(1, 0), new Point(2, 0), new Point(3, 0)), draw.points);
    }

    @Test
    void mergeReversesASegmentWhenOnlyItsEndTouches() {
        // Second segment is authored end-first: (2,0)->(1,0). It still continues the first
        // segment and must be reversed so the welded polyline runs (0,0)->(1,0)->(2,0).
        Layer layer = new Layer("L1", "default_station", List.of(
                new MoveCommand(1, 0, 0),
                new DrawCommand(2, List.of(new Point(0, 0), new Point(1, 0))),
                new MoveCommand(3, 2, 0),
                new DrawCommand(4, List.of(new Point(2, 0), new Point(1, 0)))));

        ProcessorOutput optimized = OptimizeStage.optimize(output(layer), 0, false, 0.01);

        List<Command> commands = optimized.layers().get(0).commands();
        assertEquals(2, commands.size());
        DrawCommand draw = (DrawCommand) commands.get(1);
        assertEquals(List.of(new Point(0, 0), new Point(1, 0), new Point(2, 0)), draw.points);
    }

    @Test
    void mergeLeavesDisjointStrokesSeparate() {
        // Two strokes that don't touch must remain two strokes (no spurious welding).
        Layer layer = new Layer("L1", "default_station", List.of(
                new MoveCommand(1, 0, 0),
                new DrawCommand(2, List.of(new Point(0, 0), new Point(1, 0))),
                new MoveCommand(3, 50, 0),
                new DrawCommand(4, List.of(new Point(50, 0), new Point(51, 0)))));

        ProcessorOutput optimized = OptimizeStage.optimize(output(layer), 0, false, 0.01);

        OptimizeStage.Stats stats = OptimizeStage.computeStats(optimized);
        assertEquals(2, stats.strokeCount());
    }

    @Test
    void zeroMergeToleranceLeavesStrokesUntouched() {
        Layer layer = new Layer("L1", "default_station", List.of(
                new MoveCommand(1, 0, 0),
                new DrawCommand(2, List.of(new Point(0, 0), new Point(1, 0))),
                new MoveCommand(3, 1, 0),
                new DrawCommand(4, List.of(new Point(1, 0), new Point(2, 0)))));

        ProcessorOutput optimized = OptimizeStage.optimize(output(layer), 0, false, 0.0);

        OptimizeStage.Stats stats = OptimizeStage.computeStats(optimized);
        assertEquals(2, stats.strokeCount(), "merge disabled: touching strokes stay separate");
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

    @Test
    void cancellationStopsExpensiveReorderingWithoutMutatingTheInput() {
        List<Command> commands = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            commands.add(new MoveCommand(i * 2 + 1, 40 - i, i % 7));
            commands.add(new DrawCommand(i * 2 + 2,
                    List.of(new Point(40 - i, i % 7), new Point(40 - i, i % 7 + 1))));
        }
        ProcessorOutput input = output(new Layer("large", "default_station", commands));
        List<Command> originalCommands = List.copyOf(input.layers().get(0).commands());
        AtomicInteger checks = new AtomicInteger();

        assertThrows(CancellationException.class, () -> OptimizeStage.optimize(
                input, 0, true, 0, () -> checks.incrementAndGet() > 50));

        assertTrue(checks.get() > 50, "cancellation should be checked during the optimization loops");
        assertEquals(originalCommands, input.layers().get(0).commands());
    }
}
