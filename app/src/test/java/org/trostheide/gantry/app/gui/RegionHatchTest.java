package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.Bounds;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Metadata;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;

import java.awt.geom.Path2D;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Click-to-hatch geometry/model splicing (Phase 10 Tier 3). */
class RegionHatchTest {

    private static Path2D square(double x, double y, double size) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, y);
        p.lineTo(x + size, y);
        p.lineTo(x + size, y + size);
        p.lineTo(x, y + size);
        p.closePath();
        return p;
    }

    @Test
    void fillsAClosedRegionWithStrokesInsideItsBounds() {
        List<DrawCommand> strokes = RegionHatch.hatchCommands(square(10, 10, 100), "linear", 0.0, 10.0, 1);

        assertFalse(strokes.isEmpty(), "a 100mm square should hold several 10mm-spaced lines");
        for (DrawCommand d : strokes) {
            assertEquals(2, d.points.size(), "each linear hatch stroke is a single line segment");
            for (var pt : d.points) {
                assertTrue(pt.x() >= 9.5 && pt.x() <= 110.5, "x within the region: " + pt.x());
                assertTrue(pt.y() >= 9.5 && pt.y() <= 110.5, "y within the region: " + pt.y());
            }
        }
    }

    @Test
    void everyPatternProducesStrokesAndStaysInBounds() {
        for (String pattern : RegionHatch.PATTERNS) {
            List<DrawCommand> strokes = RegionHatch.hatchCommands(square(0, 0, 100), pattern, 45.0, 6.0, 1);
            assertFalse(strokes.isEmpty(), pattern + " should produce strokes");
            for (DrawCommand d : strokes) {
                assertTrue(d.points.size() >= 2, pattern + " strokes are polylines");
                for (var pt : d.points) {
                    // Wave/zigzag can bow slightly past the edge; allow a small margin.
                    assertTrue(pt.x() >= -10 && pt.x() <= 110, pattern + " x in range: " + pt.x());
                    assertTrue(pt.y() >= -10 && pt.y() <= 110, pattern + " y in range: " + pt.y());
                }
            }
        }
    }

    @Test
    void idsContinueFromStart() {
        List<DrawCommand> strokes = RegionHatch.hatchCommands(square(0, 0, 100), "linear", 45.0, 8.0, 50);
        assertEquals(50, strokes.get(0).id);
        for (int i = 1; i < strokes.size(); i++) {
            assertEquals(strokes.get(i - 1).id + 1, strokes.get(i).id, "ids are sequential");
        }
    }

    @Test
    void tooSmallRegionYieldsNoStrokes() {
        // A 1mm square can hold no 10mm-spaced scanline.
        assertTrue(RegionHatch.hatchCommands(square(0, 0, 1), "linear", 0.0, 10.0, 1).isEmpty());
    }

    @Test
    void appendAddsToTargetLayerAndBumpsMetadata() {
        ProcessorOutput out = oneLayerOutput();
        assertEquals(7, RegionHatch.maxCommandId(out), "highest existing id");

        List<DrawCommand> extra = RegionHatch.hatchCommands(square(0, 0, 100), "linear", 0.0, 10.0, 8);
        ProcessorOutput after = RegionHatch.appendToLayer(out, 0, extra);

        assertEquals(1 + extra.size(), after.layers().get(0).commands().size(),
                "the new strokes land in the target layer");
        assertEquals(1 + extra.size(), after.metadata().totalCommands(),
                "metadata command count is bumped");
        assertEquals(out.metadata().source(), after.metadata().source(), "other metadata preserved");
    }

    @Test
    void appendIsANoOpForEmptyOrBadLayer() {
        ProcessorOutput out = oneLayerOutput();
        assertSame(out, RegionHatch.appendToLayer(out, 0, List.of()), "empty extra → unchanged");
        assertSame(out, RegionHatch.appendToLayer(out, 5,
                RegionHatch.hatchCommands(square(0, 0, 100), "linear", 0.0, 10.0, 8)),
                "out-of-range layer → unchanged");
    }

    @Test
    void clearRemovesOnlyTrackedHatchStrokesInsideTheRegion() {
        // Boundary stroke (id 1, not a hatch) + hatch fill (ids 8…) inside a 100mm square, plus one
        // tracked stroke OUTSIDE the region that must survive.
        Path2D square = square(0, 0, 100);
        List<DrawCommand> fill = RegionHatch.hatchCommands(square, "linear", 0.0, 10.0, 8);
        DrawCommand boundary = new DrawCommand(1, List.of(new org.trostheide.gantry.model.Point(0, 0),
                new org.trostheide.gantry.model.Point(100, 0)));
        DrawCommand far = new DrawCommand(999, List.of(new org.trostheide.gantry.model.Point(500, 500),
                new org.trostheide.gantry.model.Point(510, 510)));
        List<Command> cmds = new ArrayList<>();
        cmds.add(boundary);
        cmds.addAll(fill);
        cmds.add(far);
        Layer layer = new Layer("l", null, "#000", cmds);
        Metadata md = new Metadata("t", Instant.EPOCH, null, "mm", cmds.size(), Bounds.empty());
        ProcessorOutput out = new ProcessorOutput(md, new ArrayList<>(List.of(layer)));

        java.util.Set<Integer> hatchIds = new java.util.HashSet<>();
        for (DrawCommand d : fill) {
            hatchIds.add(d.id);
        }
        hatchIds.add(999); // tracked, but its centroid is outside the region

        RegionHatch.RemoveResult r = RegionHatch.removeHatchInRegion(out, hatchIds, square);
        assertEquals(fill.size(), r.removed(), "only the in-region hatch strokes are removed");
        List<Command> after = r.output().layers().get(0).commands();
        assertTrue(after.contains(boundary), "the boundary (not a hatch id) survives");
        assertTrue(after.contains(far), "a tracked stroke outside the region survives");
        assertEquals(2, after.size());
        assertEquals(2, r.output().metadata().totalCommands(), "metadata count decremented");
    }

    @Test
    void removeCommandByIdDropsExactlyThatCommand() {
        ProcessorOutput out = oneLayerOutput(); // single command, id 7
        RegionHatch.RemoveResult miss = RegionHatch.removeCommandById(out, 123);
        assertEquals(0, miss.removed(), "unknown id removes nothing");
        assertSame(out, miss.output());

        RegionHatch.RemoveResult hit = RegionHatch.removeCommandById(out, 7);
        assertEquals(1, hit.removed());
        assertTrue(hit.output().layers().get(0).commands().isEmpty(), "the command is gone");
        assertEquals(0, hit.output().metadata().totalCommands());
    }

    @Test
    void clearIsANoOpWhenNothingMatches() {
        ProcessorOutput out = oneLayerOutput();
        RegionHatch.RemoveResult r = RegionHatch.removeHatchInRegion(out, java.util.Set.of(), square(0, 0, 100));
        assertEquals(0, r.removed());
        assertSame(out, r.output());
    }

    private static ProcessorOutput oneLayerOutput() {
        List<Command> cmds = new ArrayList<>();
        cmds.add(new DrawCommand(7, List.of(new org.trostheide.gantry.model.Point(0, 0),
                new org.trostheide.gantry.model.Point(1, 1))));
        Layer layer = new Layer("layer1", null, "#000000", cmds);
        Metadata md = new Metadata("test", Instant.EPOCH, null, "mm", 1, Bounds.empty());
        return new ProcessorOutput(md, new ArrayList<>(List.of(layer)));
    }
}
