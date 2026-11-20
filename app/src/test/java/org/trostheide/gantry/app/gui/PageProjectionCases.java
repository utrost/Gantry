package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.*;
import org.trostheide.gantry.app.session.*;
import org.trostheide.gantry.plotter.GcodeFileBackend;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Shared asymmetric cases; main captures executable baselines from the unmodified RC12 JAR. */
final class PageProjectionCases {
    record Case(String id, GantryConfig config) { }

    static List<Case> all() {
        List<Case> result = new ArrayList<>();
        for (String origin : List.of("Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right"))
            for (boolean portrait : List.of(false, true))
                for (int mask = 0; mask < 8; mask++)
                    for (boolean flip : List.of(false, true))
                        for (int rotation : List.of(0, 90)) {
                            GantryConfig c = new GantryConfig();
                            c.mock = true; c.machineOrigin = origin;
                            c.orientation = portrait ? "Portrait" : "Landscape";
                            c.swapXY = (mask & 1) != 0;
                            c.invertX = (mask & 2) != 0; c.invertY = (mask & 4) != 0;
                            c.flipY = flip; c.dataRotation = rotation;
                            c.canvasAlignment = "Center";
                            c.gcode.machineWidth = 20; c.gcode.machineHeight = 12;
                            c.gcode.penDownDelayMillis = 0;
                            String id = origin + "-" + c.orientation + "-" + mask + "-" + flip + "-" + rotation;
                            result.add(new Case(id, c));
                        }
        return result;
    }

    static String gcodeHash(GantryConfig config, Path output) throws Exception {
        GcodeFileBackend backend = new GcodeFileBackend(config.gcode, output.toFile());
        if (!backend.connect()) throw new IllegalStateException("Cannot open test G-code");
        try {
            new PlotService(backend, config.toPlotSettings()).plot(
                    QuickArtworkGenerator.generate(new QuickArtwork(QuickArtwork.Kind.TEXT, "F", 0, 7)));
        } finally { backend.disconnect(); }
        byte[] bytes = Files.readString(output).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempFile("gantry-projection-motion-", ".gcode");
        try {
            for (Case sample : all()) System.out.println(gcodeHash(sample.config(), temporary) + "  " + sample.id());
        } finally { Files.deleteIfExists(temporary); }
    }
}
