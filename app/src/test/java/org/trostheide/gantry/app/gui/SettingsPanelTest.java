package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.app.plot.GantryConfig;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsPanelTest {

    @Test
    void preservesUnexposedCalibrationConnectionAndWorkspaceValues() {
        SettingsPanel panel = new SettingsPanel();
        GantryConfig config = new GantryConfig();
        config.hasLimitSwitches = true;
        config.gcode.bootDelayMillis = 4100;
        config.gcode.positionPollIntervalSeconds = .25;
        config.lastDirectory = "/drawings";
        config.inspectorWidth = 420;
        config.machinePanelPinned = true;
        config.diagnosticsOpen = true;
        panel.loadConfig(config);
        config.hasLimitSwitches = false;
        GantryConfig edited = panel.toConfig();
        assertTrue(edited.hasLimitSwitches, "editor owns a snapshot rather than the caller's mutable config");
        assertEquals(4100, edited.gcode.bootDelayMillis);
        assertEquals(.25, edited.gcode.positionPollIntervalSeconds);
        assertEquals("/drawings", edited.lastDirectory);
        assertEquals(420, edited.inspectorWidth);
        assertTrue(edited.machinePanelPinned);
        assertTrue(edited.diagnosticsOpen);
        edited.gcode.bootDelayMillis = 0;
        assertEquals(4100, panel.toConfig().gcode.bootDelayMillis, "returned configurations are independent");
    }

    @Test
    void penModesShowRelevantFieldsWithoutDiscardingOtherModeValues() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try {
                SettingsPanel panel = new SettingsPanel();
                GantryConfig config = new GantryConfig();config.gcode.penServoUp=75;config.gcode.zDown=-3;
                panel.loadConfig(config);
                var modeField=SettingsPanel.class.getDeclaredField("penModeCombo");modeField.setAccessible(true);
                var servoField=SettingsPanel.class.getDeclaredField("penUpSpinner");servoField.setAccessible(true);
                var zField=SettingsPanel.class.getDeclaredField("zDownSpinner");zField.setAccessible(true);
                var mode=(javax.swing.JComboBox<?>)modeField.get(panel);
                var servo=(javax.swing.JComponent)servoField.get(panel);
                var z=(javax.swing.JComponent)zField.get(panel);
                assertTrue(servo.isVisible());assertFalse(z.isVisible());
                mode.setSelectedItem("zaxis");assertFalse(servo.isVisible());assertTrue(z.isVisible());
                mode.setSelectedItem("m3m5");assertFalse(servo.isVisible());assertFalse(z.isVisible());
                assertEquals(75,panel.toConfig().gcode.penServoUp);assertEquals(-3,panel.toConfig().gcode.zDown);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
    }

    @Test
    void connectedSettingsStayVisibleButDisableConnectionChanges() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try {
                SettingsPanel panel = new SettingsPanel();
                panel.loadConfig(new GantryConfig());
                panel.setConnectionEditable(false);
                for (String name : new String[]{"serialPortCombo", "baudRateSpinner", "mockCheckBox", "refreshPortsButton"}) {
                    var field = SettingsPanel.class.getDeclaredField(name);field.setAccessible(true);
                    var control = (javax.swing.JComponent)field.get(panel);
                    assertTrue(control.isVisible());assertFalse(control.isEnabled());
                    assertTrue(control.getToolTipText().contains("Disconnect"));
                    panel.setConnectionEditable(true);assertTrue(control.isEnabled());
                    panel.setConnectionEditable(false);
                }
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
    }

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
