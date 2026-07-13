package org.trostheide.gantry.cli;

import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;
import org.trostheide.gantry.model.command.RefillCommand;
import org.trostheide.gantry.plotter.GcodeFileBackend;

import java.io.File;
import java.util.Locale;

final class CliGcodeExporter {
    private static final int SWIRL_SEGMENTS = 16;

    private CliGcodeExporter() { }

    static void export(ProcessorOutput output, CliBatchConfig config, File file) {
        GcodeFileBackend backend = new GcodeFileBackend(config.gcode, file);
        if (!backend.connect()) {
            throw new IllegalStateException("Could not create G-code file " + file);
        }
        try {
            for (Layer layer : output.layers()) {
                for (Command command : layer.commands()) {
                    if (command instanceof MoveCommand move) {
                        backend.moveto(move.x, move.y);
                    } else if (command instanceof DrawCommand draw) {
                        for (Point point : draw.points) {
                            backend.lineto(point.x(), point.y());
                        }
                    } else if (command instanceof RefillCommand refill) {
                        refill(backend, config.stations.get(refill.stationId));
                    }
                }
            }
        } finally {
            backend.disconnect();
        }
    }

    private static void refill(GcodeFileBackend backend, CliBatchConfig.Station station) {
        if (station == null) {
            return;
        }
        backend.penup();
        backend.moveto(station.x, station.y);
        backend.pendown(station.zDown);
        if (station.dwellMs > 0) {
            backend.sendRaw(String.format(Locale.ROOT, "G4 P%.3f", station.dwellMs / 1000.0));
        }
        if (("dip_swirl".equals(station.behavior) || "rinse".equals(station.behavior))
                && station.swirlRadius > 0) {
            for (int i = 0; i <= SWIRL_SEGMENTS; i++) {
                double angle = 2 * Math.PI * i / SWIRL_SEGMENTS;
                backend.lineto(station.x + station.swirlRadius * Math.cos(angle),
                        station.y + station.swirlRadius * Math.sin(angle));
            }
        }
        backend.penup();
    }
}
