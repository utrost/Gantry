package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.Bounds;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Metadata;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StudioMetricsTest {

    private static ProcessorOutput output(Layer... layers) {
        Metadata meta = new Metadata("test", Instant.EPOCH, "station", "mm", 0, Bounds.empty());
        return new ProcessorOutput(meta, List.of(layers));
    }

    @Test
    void countsStrokesPointsAndSeparatesDrawFromTravel() {
        // Move(0,0) → Draw 10mm → Move +10mm (travel) → Draw 5mm
        Layer layer = new Layer("L1", "default_station", List.of(
                new MoveCommand(1, 0, 0),
                new DrawCommand(2, List.of(new Point(0, 0), new Point(10, 0))),
                new MoveCommand(3, 20, 0),
                new DrawCommand(4, List.of(new Point(20, 0), new Point(20, 5)))));

        StudioMetrics m = StudioMetrics.of(output(layer));

        assertEquals(1, m.layers());
        assertEquals(2, m.strokes());
        assertEquals(4, m.points());
        assertEquals(15.0, m.drawDistance(), 1e-9, "drawing = 10 + 5");
        assertEquals(10.0, m.travelDistance(), 1e-9, "pen-up hop between the two strokes");
        assertEquals(0.4, m.travelRatio(), 1e-9, "10 / (15 + 10)");
    }

    @Test
    void approachToStrokeStartIsNotDoubleCounted() {
        // A Move that already lands on the stroke's first point adds no extra travel.
        Layer layer = new Layer("L1", "default_station", List.of(
                new MoveCommand(1, 5, 5),
                new DrawCommand(2, List.of(new Point(5, 5), new Point(5, 15)))));

        StudioMetrics m = StudioMetrics.of(output(layer));

        assertEquals(10.0, m.drawDistance(), 1e-9);
        assertEquals(0.0, m.travelDistance(), 1e-9);
        assertEquals(0.0, m.travelRatio(), 1e-9);
    }

    @Test
    void emptyOutputIsZeroNotNaN() {
        StudioMetrics m = StudioMetrics.of(output(new Layer("L1", "s", List.of())));
        assertEquals(0, m.strokes());
        assertEquals(0.0, m.travelRatio(), 1e-9);
    }

    @Test
    void summaryIsCompactAndHumanReadable() {
        Layer layer = new Layer("L1", "s", List.of(
                new DrawCommand(1, List.of(new Point(0, 0), new Point(1, 0)))));
        assertTrue(StudioMetrics.of(output(layer)).summary().contains("1 strokes"));
    }
}
