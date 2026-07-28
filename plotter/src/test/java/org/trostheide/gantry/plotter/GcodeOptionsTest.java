package org.trostheide.gantry.plotter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class GcodeOptionsTest {
    @Test
    void copyFromUpdatesTheInstanceRetainedByALiveBackend() {
        GcodeOptions live = new GcodeOptions();
        GcodeBackend backend = new GcodeBackend(live);
        GcodeOptions changed = new GcodeOptions();
        changed.penMode = "zaxis";
        changed.zUp = 0.0;
        changed.zDown = -0.01;
        changed.penServoUp = 0;
        changed.penServoDown = 0;

        live.copyFrom(changed);

        assertSame(live, backend.getOptions());
        assertEquals("zaxis", backend.getOptions().penMode);
        assertEquals(0.0, backend.getOptions().zUp);
        assertEquals(-0.01, backend.getOptions().zDown);
        assertEquals(0, backend.getOptions().penServoUp);
        assertEquals(0, backend.getOptions().penServoDown);
    }
}
