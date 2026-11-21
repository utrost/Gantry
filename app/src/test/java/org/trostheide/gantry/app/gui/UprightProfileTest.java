package org.trostheide.gantry.app.gui;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.trostheide.gantry.app.plot.*;
import org.trostheide.gantry.app.session.*;
import org.trostheide.gantry.model.Point;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UprightProfileTest {
    @TempDir Path directory;
    private final ObjectMapper json = new ObjectMapper();

    @Test void freshAndExistingFilesChooseTheirOwnConventionWithoutRewriting() throws Exception {
        Path file = directory.resolve("config.json");
        assertEquals(2, ConfigStore.load(file.toFile()).coordinateVersion);
        Files.writeString(file, "{}");
        assertEquals(1, ConfigStore.load(file.toFile()).coordinateVersion);
        assertEquals("{}", Files.readString(file), "Loading cannot migrate the source file");
        var legacy = ConfigStore.load(file.toFile());
        legacy.invertY = true;
        ConfigStore.save(legacy, file.toFile());
        assertEquals(1, ConfigStore.load(file.toFile()).coordinateVersion);
        assertTrue(ConfigStore.load(file.toFile()).invertY);
        ConfigStore.save(new GantryConfig(), file.toFile());
        assertEquals(2, ConfigStore.load(file.toFile()).coordinateVersion);
        for (String bad : List.of("broken", "{\"coordinateVersion\":99}", "{\"coordinateVersion\":2.5}")) {
            Files.writeString(file, bad);
            assertEquals(1, ConfigStore.load(file.toFile()).coordinateVersion,
                    "Unreadable existing profiles must not silently adopt new axis defaults");
        }
    }

    @Test void transferRetainsOldCalibratedSettingsAndVersionsNewProfilesExplicitly() throws Exception {
        for (int version : List.of(1, 2)) {
            var source = new GantryConfig(); source.coordinateVersion = version;
            source.invertY = true; source.swapXY = true; source.orientation = "Portrait";
            Path file = directory.resolve("machine.json");
            MachineSettingsIO.exportSettings(source, file.toFile());
            assertEquals(version, json.readTree(file.toFile()).path("version").asInt());
            var imported = MachineSettingsIO.importSettings(file.toFile(), new GantryConfig());
            assertEquals(version, imported.coordinateVersion);
            assertEquals(PageProjectionCases.gcodeHash(source, directory.resolve("before.gcode")),
                    PageProjectionCases.gcodeHash(imported, directory.resolve("after.gcode")));
        }
        Path old = directory.resolve("rc12.json");
        Files.writeString(old, "{\"format\":\"gantry-machine-settings\",\"version\":1,\"settings\":{\"gcode\":{\"machineWidth\":300,\"machineHeight\":200},\"invertY\":true}}");
        var imported = MachineSettingsIO.importSettings(old.toFile(), new GantryConfig());
        assertEquals(1, imported.coordinateVersion); assertTrue(imported.invertY);
        var comparison = MachineSettingsIO.comparison(imported, new GantryConfig());
        assertTrue(comparison.stream().anyMatch(c -> c.property().equals("Artwork orientation")
                && c.before().contains("Legacy") && c.after().equals("Upright artwork") && c.changed()));
    }

    @Test void freshTextIsUprightForEveryOriginAndOrientationAfterProjectReload() throws Exception {
        var session = new DocumentSession();
        session.appendQuickArtwork(new QuickArtwork(QuickArtwork.Kind.TEXT, "F", 0, 7));
        var project = new GantryProject(1, session.currentOutput(), List.of(0), GantryProject.Placement.identity(),
                1, GantryProject.Source.empty(), session.artworks());
        Path file = directory.resolve("label.gantry");
        GantryProjectIO.save(project, file.toFile());
        var loaded = GantryProjectIO.load(file.toFile());
        assertEquals(json.valueToTree(project.output().layers()), json.valueToTree(loaded.output().layers()));
        assertEquals(project.output().metadata(), loaded.output().metadata());
        for (String origin : List.of("Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right"))
            for (String orientation : List.of("Landscape", "Portrait")) {
                var c = new GantryConfig(); c.machineOrigin = origin; c.orientation = orientation;
                c.gcode.machineWidth = 20; c.gcode.machineHeight = 12; c.canvasAlignment = "Center";
                var settings = c.toPlotSettings();
                var projected = new PlotService(null, settings).projectArtwork(loaded.output());
                var stroke = projected.layers().get(0).strokes().get(0);
                double[] bottom = projected.page().point(stroke.get(0).x(), stroke.get(0).y());
                double[] top = projected.page().point(stroke.get(1).x(), stroke.get(1).y());
                double[] right = projected.page().point(stroke.get(2).x(), stroke.get(2).y());
                assertEquals(-7, top[1] - bottom[1], 1e-9, origin + orientation + " F points up");
                assertEquals(5, right[0] - top[0], 1e-9, origin + orientation + " F points right");
                // A physical Up jog must agree with the readable artwork and page.
                double dx = settings.swapXY ? 1 : 0, dy = settings.swapXY ? 0 : 1;
                if (settings.invertX) dx = -dx; if (settings.invertY) dy = -dy;
                double[] a = projected.page().point(10, 6), b = projected.page().point(10 + dx, 6 + dy);
                assertEquals(-1, b[1] - a[1], 1e-9);
                assertEquals(0, b[0] - a[0], 1e-9);
            }
    }

    @Test void canvasRetainsEveryQuickTextStrokeIncludingMoveToFirstEndpoint() throws Exception {
        var output = QuickArtworkGenerator.generate(new QuickArtwork(QuickArtwork.Kind.TEXT, "MAX", 0, 10));
        var panel = new VisualizationPanel();
        panel.setMachineSize(60, 30); panel.setMachineOrigin("Top-Left");
        panel.setEffectiveAxes(false, false, true); panel.setCanvasAlignment("Center");
        panel.loadFromOutput(output);
        assertEquals(new VisualizationPanel.Point2D(0, 0), panel.allPaths.get(0).get(0), "M's first vertical stroke");
        assertEquals(2, panel.allPaths.get(2).size(), "A's one-endpoint crossbar is still a line");
        assertArrayEquals(PlotService.computeContentBounds(output.layers()), panel.contentBoundsArray(),
                "Displaying MOVE starts cannot change alignment or executable offsets");
        panel.editingEnabled = false; panel.setSize(640, 340);
        var image = new java.awt.image.BufferedImage(640, 340, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics(); panel.paint(g); g.dispose();
        for (var path : panel.allPaths) for (int i = 1; i < path.size(); i++) {
            double[] a = panel.transformPoint(path.get(i - 1)), b = panel.transformPoint(path.get(i));
            int x = (int) Math.round(panel.paintTx + (a[0] + b[0]) * .5 * panel.paintScale);
            int y = (int) Math.round(panel.paintTy + (a[1] + b[1]) * .5 * panel.paintScale);
            var pixel = new java.awt.Color(image.getRGB(x, y));
            assertTrue(pixel.getBlue() > pixel.getRed() + 20, "Actual canvas paints every stroke midpoint");
        }
        // A MOVE outside the DRAW-only box must not change the legacy alignment bounds.
        var layer = new org.trostheide.gantry.model.Layer("x", "s", java.util.List.of(
                new org.trostheide.gantry.model.command.MoveCommand(1, -100, -100),
                new org.trostheide.gantry.model.command.DrawCommand(2, java.util.List.of(new Point(1, 2), new Point(3, 4)))));
        var unusual = new org.trostheide.gantry.model.ProcessorOutput(output.metadata(), java.util.List.of(layer));
        panel.loadFromOutput(unusual);
        assertArrayEquals(PlotService.computeContentBounds(unusual.layers()), panel.contentBoundsArray());
        var consecutive = new org.trostheide.gantry.model.Layer("x", "s", java.util.List.of(
                new org.trostheide.gantry.model.command.MoveCommand(1, 0, 0),
                new org.trostheide.gantry.model.command.DrawCommand(2, java.util.List.of(new Point(1, 2))),
                new org.trostheide.gantry.model.command.DrawCommand(3, java.util.List.of(new Point(3, 4)))));
        panel.loadFromOutput(new org.trostheide.gantry.model.ProcessorOutput(output.metadata(), java.util.List.of(consecutive)));
        assertEquals(java.util.List.of(new VisualizationPanel.Point2D(1, 2), new VisualizationPanel.Point2D(3, 4)), panel.allPaths.get(1));
    }

    @Test void historicalReplayRequiresTheRecordedMachineMapping() throws Exception {
        var legacy = json.readValue("{}", GantryConfig.class);
        var recorded = legacy.toPlotSettings();
        var reloaded = json.readValue(json.writeValueAsString(recorded), PlotSettings.class);
        assertTrue(HistoricalJobPreview.compatible(reloaded, legacy.toPlotSettings()));
        assertFalse(HistoricalJobPreview.compatible(reloaded, new GantryConfig().toPlotSettings()),
                "A newly opted-in profile must not silently replay a differently calibrated historical job");
        assertEquals(recorded.invertY, reloaded.invertY);
    }

    @Test void calibrationReversesBothConventionsAndPortraitWithoutChangingOtherSettings() {
        for (int version : List.of(1, 2))
            for (String origin : List.of("Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right"))
                for (String orientation : List.of("Landscape", "Portrait"))
                    for (var x : AxisDirectionSolver.Dir.values()) for (var y : AxisDirectionSolver.Dir.values()) {
                        var desired = AxisDirectionSolver.solveEffective(x, y);
                        if (desired.isEmpty()) continue;
                        var c = new GantryConfig(); c.coordinateVersion = version;
                        c.machineOrigin = origin; c.orientation = orientation;
                        var stored = AxisDirectionSolver.toStoredExtra(desired.get(), c);
                        c.swapXY = stored.swapXY(); c.invertX = stored.invertX(); c.invertY = stored.invertY();
                        var actual = c.toPlotSettings();
                        assertEquals(desired.get(), new AxisDirectionSolver.AxisConfig(actual.swapXY, actual.invertX, actual.invertY));
                        assertEquals(version, c.coordinateVersion);
                    }
    }
}
