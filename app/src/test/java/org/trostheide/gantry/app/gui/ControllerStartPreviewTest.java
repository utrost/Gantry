package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.app.plot.*;
import org.trostheide.gantry.model.*;
import org.trostheide.gantry.model.command.*;
import java.time.Instant;
import java.util.List;
import java.awt.Color;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class ControllerStartPreviewTest {
    @Test void initialSingleEndpointDrawIsVisibleFromMachineOrigin() {
        var settings = new PlotSettings(); settings.machineWidth = 40.; settings.machineHeight = 30.;
        settings.canvasAlign = "Center";
        var output = new ProcessorOutput(new Metadata("preview", Instant.EPOCH, "s", "mm", 1, Bounds.empty()),
                List.of(new Layer("ink", "s", List.of(new DrawCommand(1, List.of(new Point(12, 8)))))));
        var panel = new VisualizationPanel(); panel.setMachineSize(40, 30); panel.setMachineOrigin("Top-Left");
        panel.setCanvasAlignment("Center"); panel.loadFromOutput(output); panel.editingEnabled = false;
        panel.setSize(840, 640);
        var image = new BufferedImage(840, 640, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics(); panel.paint(g); g.dispose();
        var projected = new PlotService(null, settings).projectArtwork(output);
        var stroke = projected.layers().get(0).strokes().get(0);
        var a = stroke.get(0); var b = stroke.get(1);
        var p = panel.physicalToScreen((a.x() + b.x()) / 2, (a.y() + b.y()) / 2);
        int x = (int)Math.round(panel.paintTx + p[0] * panel.paintScale);
        int y = (int)Math.round(panel.paintTy + p[1] * panel.paintScale);
        var pixel = new Color(image.getRGB(x, y));
        assertTrue(pixel.getBlue() > pixel.getRed() + 20, "Canvas must paint the actual origin-to-DRAW segment");
    }
    @Test void initialRefillFallbackRinseAndUnknownStationStartsMatchExecutedGeometry() {
        var metadata = new Metadata("controller starts", Instant.EPOCH, "s", "mm", 7, Bounds.empty());
        var output = new ProcessorOutput(metadata, List.of(
                new Layer("one", "s", List.of(new DrawCommand(1, List.of(new Point(8, 6))),
                        new RefillCommand(2, "missing"), new DrawCommand(3, List.of(new Point(12, 8))))),
                new Layer("two", "s", List.of(new DrawCommand(4, List.of(new Point(8, 6))),
                        new RefillCommand(5, "s"), new DrawCommand(6, List.of(new Point(12, 8)))))));
        for (String origin : List.of("Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right"))
            for (String orientation : List.of("Landscape", "Portrait")) for (int flags = 0; flags < 8; flags++) {
                var config = new GantryConfig(); config.gcode.machineWidth = 40; config.gcode.machineHeight = 30;
                config.machineOrigin = origin; config.orientation = orientation; config.canvasAlignment = "Center";
                config.invertX = (flags & 1) != 0; config.invertY = (flags & 2) != 0; config.swapXY = (flags & 4) != 0;
                config.stations.put("s", new StationConfig(35, 20, 0, "simple_dip"));
                config.stations.put("water", new StationConfig(30, 25, 0, "simple_dip"));
                assertPreview(output, config);
                config.stations.put("default_station", new StationConfig(25, 20, 0, "simple_dip"));
                assertPreview(output, config);
            }
    }

    @Test void historicalStationStartsAreVisibleAndHitTestableWithoutChangingInclusion() {
        var settings = new PlotSettings(); settings.machineWidth = 40.; settings.machineHeight = 30.;
        settings.alignmentOffsetOverride = new double[]{2, 3};
        settings.stations.put("s", new StationConfig(30, 20, 0, "simple_dip"));
        var output = new ProcessorOutput(new Metadata("stored", Instant.EPOCH, "s", "mm", 2, Bounds.empty()),
                List.of(new Layer("ink", "s", List.of(new RefillCommand(1, "s"),
                        new DrawCommand(2, List.of(new Point(12, 8)))))));
        var history = new HistoricalJobPreview(new PlotJobHistory.Job(Instant.EPOCH, output, settings), () -> { });
        var canvas = findCanvas(history); assertNotNull(canvas);
        assertArrayEquals(new double[]{30, 20}, canvas.transformPointToMotor(canvas.displayPath(0).get(0)), 1e-9);
        canvas.setSize(840, 640);
        var image = new BufferedImage(840, 640, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics(); canvas.paint(g); g.dispose();
        var p = canvas.physicalToScreen(22, 15.5); // midpoint from station to placed endpoint (14,11)
        int x = (int)Math.round(canvas.paintTx + p[0] * canvas.paintScale);
        int y = (int)Math.round(canvas.paintTy + p[1] * canvas.paintScale);
        assertEquals(0, canvas.interaction.nearestStrokeIndex(x, y));
        canvas.setSelectedLayers(List.of());
        canvas.setRefillStations(java.util.Map.of("s", new StationConfig(35, 22, 0, "simple_dip")));
        assertTrue(canvas.selectedLayers.isEmpty(), "Station refresh cannot re-include excluded layers");
        assertArrayEquals(new double[]{35, 22}, canvas.transformPointToMotor(canvas.displayPath(0).get(0)), 1e-9);
        assertEquals(2, output.layers().get(0).commands().size(), "No synthetic start is saved into commands");
    }

    private static VisualizationPanel findCanvas(java.awt.Container root) {
        for (var child : root.getComponents()) {
            if (child instanceof VisualizationPanel panel) return panel;
            if (child instanceof java.awt.Container container) {
                var result = findCanvas(container); if (result != null) return result;
            }
        }
        return null;
    }

    private static void assertPreview(ProcessorOutput output, GantryConfig config) {
        var settings = config.toPlotSettings();
        var panel = new VisualizationPanel(); panel.setMachineSize(40, 30); panel.setMachineOrigin(config.machineOrigin);
        panel.setEffectiveAxes(settings.swapXY, settings.invertX, settings.invertY);
        panel.setCanvasAlignment("Center"); panel.setRefillStations(settings.stations); panel.loadFromOutput(output);
        panel.editingEnabled = false; panel.setSize(840, 640);
        var image = new BufferedImage(840, 640, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics(); panel.paint(g); g.dispose();
        var projected = new PlotService(null, settings).projectArtwork(output);
        int pathIndex = 0;
        for (int layerIndex = 0; layerIndex < projected.layers().size(); layerIndex++) {
            for (var stroke : projected.layers().get(layerIndex).strokes()) {
                var displayed = panel.displayPath(pathIndex++);
                var start = panel.transformPointToMotor(displayed.get(0));
                assertArrayEquals(new double[]{stroke.get(0).x(), stroke.get(0).y()}, start, 1e-8);
                for (int i = 1; i < stroke.size(); i++) {
                    var a = stroke.get(i - 1); var b = stroke.get(i);
                    var p = panel.physicalToScreen((a.x() + b.x()) / 2, (a.y() + b.y()) / 2);
                    int x = (int)Math.round(panel.paintTx + p[0] * panel.paintScale);
                    int y = (int)Math.round(panel.paintTy + p[1] * panel.paintScale);
                    Color ink = panel.colorForLayer(layerIndex);
                    boolean found = false;
                    for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) {
                        Color actual = new Color(image.getRGB(x + dx, y + dy));
                        if (Math.abs(actual.getRed() - ink.getRed()) + Math.abs(actual.getGreen() - ink.getGreen())
                                + Math.abs(actual.getBlue() - ink.getBlue()) < 80) found = true;
                    }
                    assertTrue(found, "Actual canvas must paint the executed segment " + config.machineOrigin + config.orientation);
                }
            }
        }
        assertArrayEquals(PlotService.computeContentBounds(output.layers()), panel.contentBoundsArray(), 1e-9);
        panel.setSelectedLayers(List.of(1));
        assertArrayEquals(new double[]{0, 0}, panel.transformPointToMotor(panel.displayPath(2).get(0)), 1e-8,
                "The first included layer starts at origin even when earlier document layers are excluded");
        panel.setSelectedLayers(List.of(0, 1));
        assertArrayEquals(new double[]{30, 25}, panel.transformPointToMotor(panel.displayPath(2).get(0)), 1e-8);
        // Moving/scaling the document cannot move a controller-space initial point.
        panel.overlayScale = 1.25; panel.overlayOffsetX = 2; panel.overlayOffsetY = -1;
        assertArrayEquals(new double[]{0, 0}, panel.transformPointToMotor(panel.displayPath(0).get(0)), 1e-8);
    }

}
