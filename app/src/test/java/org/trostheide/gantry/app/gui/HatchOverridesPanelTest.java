package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.svgtoolbox.HatchStyle;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HatchOverridesPanelTest {

    @Test
    void selectedColorStyleBuildsConfigOverrideWhileGlobalRowsAreOmitted() {
        HatchOverridesPanel panel = new HatchOverridesPanel(List.of("#ff0000", "#00ff00"));
        panel.setOverride("#ff0000", "cross", 30.0, 7.5);

        Map<String, HatchStyle> overrides = panel.buildOverrides();

        assertEquals(new HatchStyle(30.0, 7.5, "cross"), overrides.get("#ff0000"));
        assertFalse(overrides.containsKey("#00ff00"));
    }

    @Test
    void invalidManualColorIsRejected() {
        HatchOverridesPanel panel = new HatchOverridesPanel(List.of());
        panel.setOverride("red", "linear", 45.0, 5.0);

        assertThrows(IllegalArgumentException.class, panel::buildOverrides);
    }
}
