package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.app.plot.GantryConfig;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
