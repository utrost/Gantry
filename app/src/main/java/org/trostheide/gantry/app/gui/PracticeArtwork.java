package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.model.Bounds;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Metadata;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;

import java.time.Instant;
import java.util.List;

/** Small bundled drawing for the no-hardware guided first-plot route. */
final class PracticeArtwork {
    private PracticeArtwork() { }

    static ProcessorOutput create() {
        List<Command> commands = List.of(
                line(1, 0, 0, 80, 0), line(2, 80, 0, 80, 60),
                line(3, 80, 60, 0, 60), line(4, 0, 60, 0, 0),
                new DrawCommand(5, List.of(
                        new Point(12, 42), new Point(27, 18), new Point(40, 42),
                        new Point(53, 18), new Point(68, 42))));
        Metadata metadata = new Metadata("Gantry practice drawing", Instant.now(),
                "default_station", "mm", commands.size(), new Bounds(0, 0, 80, 60));
        return new ProcessorOutput(metadata,
                List.of(new Layer("Practice pen", "default_station", "#2563eb", commands)));
    }

    private static DrawCommand line(int id, double x1, double y1, double x2, double y2) {
        return new DrawCommand(id, List.of(new Point(x1, y1), new Point(x2, y2)));
    }
}
