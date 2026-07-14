package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.svgtoolbox.Config;
import org.trostheide.gantry.svgtoolbox.HatchStyle;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolboxOptionsPanelTest {

    @Test
    void buildConfigIncludesPerColorHatchOverrideFromGuiTable() {
        ToolboxOptionsPanel panel = new ToolboxOptionsPanel(List.of("#ff0000", "#00ff00"));
        panel.setHatchOverride("#ff0000", "cross", 15.0, 8.0);

        Config config = panel.buildConfig();

        assertEquals(new HatchStyle(15.0, 8.0, "cross"), config.overrides().get("#ff0000"));
        assertEquals(1, config.overrides().size());
    }

    @Test
    void buildConfigIncludesHanddrawnSettings() {
        ToolboxOptionsPanel panel = new ToolboxOptionsPanel(List.of());
        panel.setHanddrawnOptions(true, 2.5, 3.5, 24.0, 99);

        Config config = panel.buildConfig();

        assertEquals(true, config.handdrawn());
        assertEquals(2.5, config.handdrawnMagnitude());
        assertEquals(3.5, config.handdrawnSegment());
        assertEquals(24.0, config.handdrawnWavelength());
        assertEquals(99L, config.handdrawnSeed());
    }
}
