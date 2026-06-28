package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoftLimitsTest {

    private static final double W = 300;
    private static final double H = 215;

    private static void assertClamp(double[] got, double x, double y) {
        assertEquals(x, got[0], 1e-9);
        assertEquals(y, got[1], 1e-9);
    }

    // --- Default machine: origin bottom-left, no inverted/swapped axes ---

    @Test
    void withinBedIsUnchanged() {
        assertClamp(SoftLimits.clampMotorToBed(100, 80, false, false, false, W, H, false, false), 100, 80);
    }

    @Test
    void clampsAtWidthAndHeight() {
        assertClamp(SoftLimits.clampMotorToBed(350, 250, false, false, false, W, H, false, false), 300, 215);
    }

    @Test
    void clampsAtZeroOrigin() {
        assertClamp(SoftLimits.clampMotorToBed(-10, -5, false, false, false, W, H, false, false), 0, 0);
    }

    // --- Inverted X: the bed extends toward negative motor-X; the clamp must follow ---

    @Test
    void invertedXClampsToNegativeBed() {
        // -310 is just past the far edge in the inverted direction → -300, not 0.
        assertClamp(SoftLimits.clampMotorToBed(-310, 100, true, false, false, W, H, false, false), -300, 100);
        // within the bed is untouched
        assertClamp(SoftLimits.clampMotorToBed(-150, 100, true, false, false, W, H, false, false), -150, 100);
    }

    // --- Origin at the opposite (top-right) corner: bed extends negative on both axes ---

    @Test
    void topRightOriginExtendsNegative() {
        assertClamp(SoftLimits.clampMotorToBed(-310, -250, false, false, false, W, H, true, true), -300, -215);
        // moving past the origin corner (wrong side) is clamped back to 0/0
        assertClamp(SoftLimits.clampMotorToBed(10, 10, false, false, false, W, H, true, true), 0, 0);
    }

    // --- Swapped axes: width bounds motor-Y and height bounds motor-X ---

    @Test
    void swappedAxesSwapTheBounds() {
        assertClamp(SoftLimits.clampMotorToBed(250, 320, false, false, true, W, H, false, false), 215, 300);
    }

    @Test
    void atWallReturnsSamePositionSoContinuousJogStops() {
        // Already at the far corner: clamping yields the same point ⇒ zero delta ⇒ jog stops.
        double[] r = SoftLimits.clampMotorToBed(300, 215, false, false, false, W, H, false, false);
        assertClamp(r, 300, 215);
    }
}
