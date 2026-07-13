package org.trostheide.gantry.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoordinateTransformTest {

    private static final double DELTA = 1e-9;

    @Test
    void transformPointIdentityWhenNoFlagsSet() {
        double[] result = CoordinateTransform.transformPoint(
                10, 20, false, false, false, 100, 100, 0, null);

        assertArrayEquals(new double[] { 10, 20 }, result, DELTA);
    }

    @Test
    void transformPointInvertsXAndY() {
        double[] result = CoordinateTransform.transformPoint(
                10, 20, false, true, true, 100, 200, 0, null);

        assertArrayEquals(new double[] { 90, 180 }, result, DELTA);
    }

    @Test
    void transformPointSwapsAxes() {
        double[] result = CoordinateTransform.transformPoint(
                10, 20, true, false, false, 100, 100, 0, null);

        assertArrayEquals(new double[] { 20, 10 }, result, DELTA);
    }

    @Test
    void transformAndInverseTransformRoundTrip() {
        double[] contentBounds = { 0, 100, 0, 100 };

        double[] forward = CoordinateTransform.transformPoint(
                30, 40, true, true, false, 100, 100, 90, contentBounds);

        double[] back = CoordinateTransform.inverseTransformPoint(
                forward[0], forward[1], true, true, false, 100, 100, 90, contentBounds);

        assertArrayEquals(new double[] { 30, 40 }, back, DELTA);
    }

    @Test
    void applyOverlayRawMirrorsAboutCenter() {
        double[] result = CoordinateTransform.applyOverlayRaw(
                60, 50, 50, 50, 1.0, 0, 0, 0, true);

        assertArrayEquals(new double[] { 40, 50 }, result, DELTA);
    }

    @Test
    void applyOverlayRawRotates90Degrees() {
        double[] result = CoordinateTransform.applyOverlayRaw(
                60, 50, 50, 50, 1.0, 0, 0, 90, false);

        assertArrayEquals(new double[] { 50, 60 }, result, DELTA);
    }

    @Test
    void calculateAlignmentOffsetCentersContent() {
        double[] contentBounds = { 0, 10, 0, 10 };

        double[] offset = CoordinateTransform.calculateAlignmentOffset(
                "center", contentBounds, 100, 100,
                false, false, false, 0, false, false, false, 0, 0);

        assertArrayEquals(new double[] { 45, 45 }, offset, DELTA);
    }

    @Test
    void physicalToScreenWithoutSwapOrInversion() {
        double[] screen = CoordinateTransform.physicalToScreen(
                30, 40, false, false, false, 100, 100);

        assertArrayEquals(new double[] { 30, 40 }, screen, DELTA);
    }

    @Test
    void physicalToScreenWithSwapAndOriginRight() {
        double[] screen = CoordinateTransform.physicalToScreen(
                30, 40, true, true, false, 100, 200);

        assertArrayEquals(new double[] { 200 - 40, 30 }, screen, DELTA);
    }

    @Test
    void physicalAndScreenCoordinatesRoundTripForEveryOriginAndSwap() {
        for (boolean swap : new boolean[] {false, true}) {
            for (boolean originRight : new boolean[] {false, true}) {
                for (boolean originBottom : new boolean[] {false, true}) {
                    double[] screen = CoordinateTransform.physicalToScreen(
                            30, 40, swap, originRight, originBottom, 100, 200);
                    double[] motor = CoordinateTransform.screenToPhysical(
                            screen[0], screen[1], swap, originRight, originBottom, 100, 200);
                    assertArrayEquals(new double[] {30, 40}, motor, DELTA,
                            "swap=" + swap + ", right=" + originRight + ", bottom=" + originBottom);
                }
            }
        }
    }

    @Test
    void everyFittingAlignmentStaysInsideCanvasAcrossOriginsAxisFlagsAndFlipY() {
        double[] bounds = {10, 30, 20, 50};
        double machineW = 100;
        double machineH = 80;
        double paddingX = 5;
        double paddingY = 7;
        String[] alignments = {"top-left", "top-right", "bottom-left", "bottom-right", "center"};

        for (boolean swap : new boolean[] {false, true}) {
            for (boolean invertX : new boolean[] {false, true}) {
                for (boolean invertY : new boolean[] {false, true}) {
                    for (boolean originRight : new boolean[] {false, true}) {
                        for (boolean originBottom : new boolean[] {false, true}) {
                            for (boolean flipY : new boolean[] {false, true}) {
                                for (int rotation : new int[] {0, 90, 180, 270}) {
                                    for (String alignment : alignments) {
                                        double[] offset = CoordinateTransform.calculateAlignmentOffset(
                                                alignment, bounds, machineW, machineH,
                                                swap, invertX, invertY, rotation,
                                                originRight, originBottom, flipY, paddingX, paddingY);
                                        double[] projected = projectedBounds(bounds, offset, machineW, machineH,
                                                swap, invertX, invertY, rotation,
                                                originRight, originBottom, flipY);
                                        double displayW = swap ? machineH : machineW;
                                        double displayH = swap ? machineW : machineH;
                                        String combination = alignment + ", swap=" + swap + ", invX=" + invertX
                                                + ", invY=" + invertY + ", rotation=" + rotation
                                                + ", right=" + originRight + ", bottom=" + originBottom
                                                + ", flipY=" + flipY;

                                        assertTrue(projected[0] >= -DELTA && projected[1] <= displayW + DELTA,
                                                "X outside for " + combination);
                                        assertTrue(projected[2] >= -DELTA && projected[3] <= displayH + DELTA,
                                                "Y outside for " + combination);
                                        if (alignment.contains("left")) assertEquals(paddingX, projected[0], DELTA, combination);
                                        if (alignment.contains("right")) assertEquals(displayW - paddingX, projected[1], DELTA, combination);
                                        if (alignment.contains("top")) assertEquals(paddingY, projected[2], DELTA, combination);
                                        if (alignment.contains("bottom")) assertEquals(displayH - paddingY, projected[3], DELTA, combination);
                                        if (alignment.equals("center")) {
                                            assertEquals(displayW / 2, (projected[0] + projected[1]) / 2, DELTA, combination);
                                            assertEquals(displayH / 2, (projected[2] + projected[3]) / 2, DELTA, combination);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static double[] projectedBounds(double[] bounds, double[] offset,
            double machineW, double machineH,
            boolean swap, boolean invertX, boolean invertY, int rotation,
            boolean originRight, boolean originBottom, boolean flipY) {
        double[][] corners = {
                {bounds[0], bounds[2]}, {bounds[1], bounds[2]},
                {bounds[0], bounds[3]}, {bounds[1], bounds[3]}
        };
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (double[] corner : corners) {
            double[] motor = CoordinateTransform.transformPoint(corner[0], corner[1],
                    swap, invertX, invertY, machineW, machineH, rotation, bounds);
            motor[0] += offset[0];
            motor[1] += offset[1];
            if (flipY) motor[1] = machineH - motor[1];
            double[] screen = CoordinateTransform.physicalToScreen(motor[0], motor[1],
                    swap, originRight, originBottom, machineW, machineH);
            minX = Math.min(minX, screen[0]);
            maxX = Math.max(maxX, screen[0]);
            minY = Math.min(minY, screen[1]);
            maxY = Math.max(maxY, screen[1]);
        }
        return new double[] {minX, maxX, minY, maxY};
    }
}
