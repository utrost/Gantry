package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.app.plot.GantryConfig;
import org.trostheide.gantry.app.plot.PlotSettings;
import org.trostheide.gantry.model.Bounds;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Metadata;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.DrawCommand;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exhaustive regression matrix for the Live View placement bug: artwork must stay inside the
 * visible bed for every origin, extra axis override, orientation, data rotation and alignment.
 * These tests deliberately use the production {@link GantryConfig#toPlotSettings()} composition
 * and {@link VisualizationPanel} transform pipeline instead of duplicating their coordinate math.
 */
class VisualizationTransformMatrixTest {
    private static final double MACHINE_W = 300;
    private static final double MACHINE_H = 200;
    private static final double EPS = 1e-7;

    private static final String[] ORIGINS = {
            "Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right"
    };
    private static final String[] ORIENTATIONS = {"Landscape", "Portrait"};
    private static final String[] ALIGNMENTS = {
            "Top Left", "Top Right", "Bottom Left", "Bottom Right", "Center"
    };
    private static final int[] ROTATIONS = {0, 90, 180, 270};

    @Test
    void alignedArtworkAlwaysStaysInsideVisibleBedForCompleteTransformMatrix() {
        int combinations = 0;
        for (String origin : ORIGINS) {
            for (String orientation : ORIENTATIONS) {
                for (int flags = 0; flags < 16; flags++) {
                    for (int rotation : ROTATIONS) {
                        for (String alignment : ALIGNMENTS) {
                            VisualizationPanel panel = configuredPanel(origin, orientation, flags,
                                    rotation, alignment);
                            assertInsideVisibleBed(panel, description(origin, orientation, flags,
                                    rotation, alignment));
                            combinations++;
                        }
                    }
                }
            }
        }
        assertTrue(combinations == 2_560, "matrix unexpectedly changed");
    }

    @Test
    void numericPositioningClampsArtworkInsideBedForCompleteAxisMatrix() {
        double[][] requestedPositions = {
                {0, 0}, {MACHINE_W, 0}, {0, MACHINE_H},
                {MACHINE_W, MACHINE_H}, {MACHINE_W / 2, MACHINE_H / 2}
        };
        int combinations = 0;
        for (String origin : ORIGINS) {
            for (String orientation : ORIENTATIONS) {
                for (int flags = 0; flags < 16; flags++) {
                    for (int rotation : ROTATIONS) {
                        for (double[] position : requestedPositions) {
                            VisualizationPanel panel = configuredPanel(origin, orientation, flags,
                                    rotation, "Center");
                            panel.setContentMotorMin(position[0], position[1]);
                            assertInsideVisibleBed(panel, description(origin, orientation, flags,
                                    rotation, "position=" + position[0] + "," + position[1]));
                            combinations++;
                        }
                    }
                }
            }
        }
        assertTrue(combinations == 2_560, "matrix unexpectedly changed");
    }

    private static VisualizationPanel configuredPanel(String origin, String orientation, int flags,
                                                       int rotation, String alignment) {
        GantryConfig config = new GantryConfig();
        config.gcode.machineWidth = MACHINE_W;
        config.gcode.machineHeight = MACHINE_H;
        config.machineOrigin = origin;
        config.orientation = orientation;
        config.invertX = (flags & 1) != 0;
        config.invertY = (flags & 2) != 0;
        config.swapXY = (flags & 4) != 0;
        config.flipY = (flags & 8) != 0;
        config.dataRotation = rotation;
        config.canvasAlignment = alignment;
        PlotSettings settings = config.toPlotSettings();

        VisualizationPanel panel = new VisualizationPanel();
        panel.setMachineSize(MACHINE_W, MACHINE_H);
        panel.setMachineOrigin(origin);
        panel.setOrientation(orientation);
        panel.setEffectiveAxes(settings.swapXY, settings.invertX, settings.invertY);
        panel.setFlipY(config.flipY);
        panel.setDataRotation(rotation);
        panel.setCanvasAlignment(alignment);
        panel.loadFromOutput(artwork());
        return panel;
    }

    private static ProcessorOutput artwork() {
        DrawCommand rectangle = new DrawCommand(1, List.of(
                new Point(10, 20), new Point(90, 20), new Point(90, 70),
                new Point(10, 70), new Point(10, 20)));
        return new ProcessorOutput(
                new Metadata("matrix.svg", Instant.EPOCH, "test", "mm", 1, Bounds.empty()),
                List.of(new Layer("artwork", "default", "#ffffff", List.of(rectangle))));
    }

    private static void assertInsideVisibleBed(VisualizationPanel panel, String description) {
        double displayW = panel.displayWidth();
        double displayH = panel.displayHeight();
        VisualizationPanel.Point2D[] corners = {
                new VisualizationPanel.Point2D(panel.rawMinX, panel.rawMinY),
                new VisualizationPanel.Point2D(panel.rawMaxX, panel.rawMinY),
                new VisualizationPanel.Point2D(panel.rawMinX, panel.rawMaxY),
                new VisualizationPanel.Point2D(panel.rawMaxX, panel.rawMaxY)
        };
        for (VisualizationPanel.Point2D corner : corners) {
            double[] screen = panel.transformPoint(corner);
            assertTrue(screen[0] >= -EPS && screen[0] <= displayW + EPS,
                    () -> description + " has X=" + screen[0] + " outside [0," + displayW + "]");
            assertTrue(screen[1] >= -EPS && screen[1] <= displayH + EPS,
                    () -> description + " has Y=" + screen[1] + " outside [0," + displayH + "]");
        }
    }

    private static String description(String origin, String orientation, int flags,
                                      int rotation, String placement) {
        return "origin=" + origin + ", orientation=" + orientation
                + ", invertX=" + ((flags & 1) != 0)
                + ", invertY=" + ((flags & 2) != 0)
                + ", swapXY=" + ((flags & 4) != 0)
                + ", flipY=" + ((flags & 8) != 0)
                + ", rotation=" + rotation + ", " + placement;
    }
}
