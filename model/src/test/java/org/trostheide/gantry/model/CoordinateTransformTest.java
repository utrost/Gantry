package org.trostheide.gantry.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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
                false, false, false, 0, false, 0, 0);

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
}
