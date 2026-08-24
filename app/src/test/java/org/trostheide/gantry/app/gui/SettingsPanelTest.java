package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.app.plot.GantryConfig;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsPanelTest {

    @Test
    void roundTripsStartupWelcomePreference() {
        SettingsPanel panel = new SettingsPanel();
        GantryConfig enabled = new GantryConfig();
        enabled.showWelcomeOnStartup = true;
        panel.loadConfig(enabled);
        assertTrue(panel.toConfig().showWelcomeOnStartup);

        GantryConfig disabled = new GantryConfig();
        disabled.showWelcomeOnStartup = false;
        panel.loadConfig(disabled);
        assertFalse(panel.toConfig().showWelcomeOnStartup);
    }

    @Test
    void roundTripsLongRunningMoveResponseTimeout() {
        SettingsPanel panel = new SettingsPanel();
        GantryConfig config = new GantryConfig();
        config.gcode.responseTimeoutSeconds = 1200;

        panel.loadConfig(config);

        assertEquals(1200, panel.toConfig().gcode.responseTimeoutSeconds);
    }
}
