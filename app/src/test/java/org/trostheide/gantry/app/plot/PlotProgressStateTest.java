package org.trostheide.gantry.app.plot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotProgressStateTest {
    @Test
    void formatsLayerElapsedAndPaceBasedRemainingTime() {
        PlotProgressState state = new PlotProgressState();
        state.start(3, 1_000);
        state.layerStarted();
        state.update(25, 100);

        assertEquals(25, state.percent());
        assertEquals("Layer 1/3  ·  Elapsed: 0:10  ·  ~0:30 remaining",
                state.liveText(11_000, 100));
    }

    @Test
    void emptyProgressNeverProducesInvalidPercentage() {
        PlotProgressState state = new PlotProgressState();
        state.start(0, 5_000);
        state.update(0, 0);

        assertEquals(0, state.percent());
        assertTrue(state.liveText(4_000, 100).startsWith("Elapsed: 0:00"));
    }
}
