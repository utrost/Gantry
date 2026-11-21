package org.trostheide.gantry.app.plot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.trostheide.gantry.model.*;
import org.trostheide.gantry.model.command.*;
import org.trostheide.gantry.plotter.GcodeFileBackend;
import org.trostheide.gantry.plotter.GcodeOptions;
import org.trostheide.gantry.watercolor.LayerRefillPlanner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class WatercolorRouteRegressionTest {
    @TempDir Path temporary;

    @Test void singleEndpointAndDrawOnlyRoutesEmitOnlyTheReviewedInk() throws Exception {
        for (List<Command> commands : List.of(
                List.<Command>of(new MoveCommand(1, 0, 0), new DrawCommand(2, List.of(new Point(25, 0)))),
                List.<Command>of(new DrawCommand(1, List.of(new Point(25, 0)))))) {
            assertExportedInk(commands, 25);
        }
    }

    @Test void exactRefillBoundaryDoesNotRetraceArtworkInExportedGcode() throws Exception {
        assertExportedInk(List.of(new MoveCommand(1, 0, 0),
                new DrawCommand(2, List.of(new Point(0, 0), new Point(8, 0))),
                new DrawCommand(3, List.of(new Point(8, 0), new Point(10, 0), new Point(15, 0)))), 15);
    }

    private void assertExportedInk(List<Command> source, double expectedInk) throws Exception {
        Layer layer = LayerRefillPlanner.configure(new Layer("ink", "paint", source), "paint", 10);
        GcodeOptions options = new GcodeOptions();
        options.penMode = "zaxis"; options.zDown = -1; options.zUp = 5;
        Path file = temporary.resolve("route.gcode");
        GcodeFileBackend backend = new GcodeFileBackend(options, file.toFile());
        PlotSettings settings = new PlotSettings();
        settings.stations.put("paint", new StationConfig(50, 50, 0, "simple_dip"));
        assertTrue(backend.connect());
        try {
            new PlotService(backend, settings).plot(new ProcessorOutput(
                    new Metadata("audit", Instant.EPOCH, "paint", "mm", layer.commands().size(),
                            new Bounds(0, 0, expectedInk, 0)), List.of(layer)));
        } finally { backend.disconnect(); }

        Pattern xy = Pattern.compile("G[01] X(-?[0-9.]+) Y(-?[0-9.]+).*");
        double x = 0, y = 0, ink = 0, sinceRefill = 0;
        boolean down = false;
        for (String line : Files.readAllLines(file)) {
            if (line.startsWith("G0 Z")) down = false;
            else if (line.startsWith("G1 Z")) {
                down = true;
                if (x == 50 && y == 50) sinceRefill = 0;
            }
            var match = xy.matcher(line);
            if (!match.matches()) continue;
            double targetX = Double.parseDouble(match.group(1)), targetY = Double.parseDouble(match.group(2));
            if (down) {
                assertEquals(0, y, 1e-8); assertEquals(0, targetY, 1e-8);
                assertTrue(targetX >= x && targetX <= expectedInk, "Unexpected paint or backwards retrace");
                double length = Math.hypot(targetX-x, targetY-y);
                ink += length; sinceRefill += length;
                assertTrue(sinceRefill <= 10.001, "Refill budget exceeded in actual G-code");
            }
            x=targetX; y=targetY;
        }
        assertEquals(expectedInk, ink, 1e-6);
        assertFalse(down);
    }
}
