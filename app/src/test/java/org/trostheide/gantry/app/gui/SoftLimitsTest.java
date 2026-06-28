package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoftLimitsTest {

    @Test
    void withinBoundsIsUnchanged() {
        double[] d = SoftLimits.clampDelta(100, 100, 20, -30, 300, 215);
        assertEquals(20, d[0], 1e-9);
        assertEquals(-30, d[1], 1e-9);
    }

    @Test
    void clampsAtUpperWall() {
        // At x=290 a +20 jog would reach 310 on a 300mm axis → only 10 allowed.
        double[] d = SoftLimits.clampDelta(290, 0, 20, 0, 300, 215);
        assertEquals(10, d[0], 1e-9);
    }

    @Test
    void clampsAtZero() {
        double[] d = SoftLimits.clampDelta(5, 5, -20, -20, 300, 215);
        assertEquals(-5, d[0], 1e-9);
        assertEquals(-5, d[1], 1e-9);
    }

    @Test
    void atWallGivesZeroSoContinuousJogStops() {
        double[] d = SoftLimits.clampDelta(300, 215, 5, 5, 300, 215);
        assertEquals(0, d[0], 1e-9);
        assertEquals(0, d[1], 1e-9);
    }

    @Test
    void axesClampedIndependently() {
        // Into the top-right corner: X already maxed (slides to 0), Y still has room.
        double[] d = SoftLimits.clampDelta(300, 100, 10, 10, 300, 215);
        assertEquals(0, d[0], 1e-9, "X is at the wall");
        assertEquals(10, d[1], 1e-9, "Y still moves");
    }
}
