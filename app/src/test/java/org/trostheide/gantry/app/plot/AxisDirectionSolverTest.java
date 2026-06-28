package org.trostheide.gantry.app.plot;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.app.plot.AxisDirectionSolver.AxisConfig;
import org.trostheide.gantry.app.plot.AxisDirectionSolver.Dir;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AxisDirectionSolverTest {

    /** Replica of PlotterPanel.transformDelta (swap → invertX → invertY) for a screen unit input. */
    private static int[] transformDelta(int sx, int sy, AxisConfig c) {
        int dx = sx, dy = sy;
        if (c.swapXY()) {
            int t = dx; dx = dy; dy = t;
        }
        if (c.invertX()) dx = -dx;
        if (c.invertY()) dy = -dy;
        return new int[]{dx, dy};
    }

    /** Physical direction the pen moves for a signed motor unit, given the two raw observations. */
    private static Dir physical(int[] motor, Dir rawX, Dir rawY) {
        if (motor[0] == 1) return rawX;
        if (motor[0] == -1) return rawX.opposite();
        if (motor[1] == 1) return rawY;
        return rawY.opposite();
    }

    @Test
    void solvedConfigMakesScreenMatchPhysicalForEveryValidObservation() {
        Dir[] all = Dir.values();
        int checked = 0;
        for (Dir rawX : all) {
            for (Dir rawY : all) {
                if (rawX.horizontal() == rawY.horizontal()) {
                    continue; // invalid (non-perpendicular) — covered separately
                }
                Optional<AxisConfig> solved = AxisDirectionSolver.solveEffective(rawX, rawY);
                assertTrue(solved.isPresent(), "valid observation should solve: " + rawX + "," + rawY);
                AxisConfig c = solved.get();

                // Screen +X must end up physically RIGHT; screen +Y physically UP.
                assertEquals(Dir.RIGHT, physical(transformDelta(1, 0, c), rawX, rawY),
                        "screen +X should go right for " + rawX + "," + rawY);
                assertEquals(Dir.UP, physical(transformDelta(0, 1, c), rawX, rawY),
                        "screen +Y should go up for " + rawX + "," + rawY);
                checked++;
            }
        }
        assertEquals(8, checked, "there are 8 valid perpendicular observation pairs");
    }

    @Test
    void identityWiringNeedsNoTransform() {
        AxisConfig c = AxisDirectionSolver.solveEffective(Dir.RIGHT, Dir.UP).orElseThrow();
        assertEquals(new AxisConfig(false, false, false), c);
    }

    @Test
    void swappedAxesAreDetected() {
        // Motor X moves the pen up, motor Y moves it right ⇒ axes swapped.
        AxisConfig c = AxisDirectionSolver.solveEffective(Dir.UP, Dir.RIGHT).orElseThrow();
        assertTrue(c.swapXY(), "press-X-moves-vertically must be caught as a swap");
    }

    @Test
    void inconsistentObservationsReturnEmpty() {
        assertTrue(AxisDirectionSolver.solveEffective(Dir.RIGHT, Dir.LEFT).isEmpty(),
                "two horizontal observations can't come from orthogonal axes");
        assertTrue(AxisDirectionSolver.solveEffective(Dir.UP, Dir.DOWN).isEmpty());
    }

    @Test
    void storedExtraXorsOutTheOriginBaseline() {
        // A bottom-right origin already inverts both axes; to reach an identity effective transform
        // the stored extra flags must cancel that baseline (XOR), matching toPlotSettings.
        AxisConfig effective = new AxisConfig(false, false, false);
        AxisConfig stored = AxisDirectionSolver.toStoredExtra(effective, true, true);
        assertTrue(stored.invertX(), "right origin baseline cancelled by stored invertX");
        assertTrue(stored.invertY(), "bottom origin baseline cancelled by stored invertY");

        // Top-left origin has no baseline, so stored == effective.
        assertEquals(effective, AxisDirectionSolver.toStoredExtra(effective, false, false));
    }
}
