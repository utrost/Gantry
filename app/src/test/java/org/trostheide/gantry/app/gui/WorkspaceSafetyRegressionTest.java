package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.app.plot.GantryConfig;
import org.trostheide.gantry.app.plot.MachineSettingsIO;
import javax.swing.UIManager;
import java.awt.Color;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceSafetyRegressionTest {
    @Test void enabledDefaultButtonsHaveReadableLabelsInBothThemes() {
        for (String theme : new String[]{"Light", "Dark"}) {
            GantryConfig config = new GantryConfig(); config.uiTheme = theme;
            WorkspaceStyle.install(config);
            Color foreground = UIManager.getColor("Button.default.foreground");
            for (String state : new String[]{"background", "focusedBackground", "hoverBackground", "pressedBackground"}) {
                Color background = UIManager.getColor("Button.default." + state);
                double a = luminance(foreground), b = luminance(background);
                assertTrue((Math.max(a,b)+.05)/(Math.min(a,b)+.05)>=4.5, theme + " " + state);
            }
        }
    }
    @Test void settingsComparisonIncludesChangesButExcludesApplicationPreferences() {
        GantryConfig before = new GantryConfig(), after = new GantryConfig();
        after.gcode.machineWidth = before.gcode.machineWidth + 1;
        after.uiTheme = "Dark"; after.mock = !before.mock; after.lastDirectory = "private";
        var changes = MachineSettingsIO.comparison(before, after);
        assertEquals(1, changes.stream().filter(c -> !c.before().equals(c.after())).count());
        assertTrue(changes.stream().anyMatch(c -> c.property().equals("machine Width")));
        assertFalse(changes.stream().anyMatch(c -> c.property().contains("mock") || c.property().contains("lastDirectory")));
    }
    @Test void historicalReviewRejectsChangedMachineGeometryAndAxes() {
        GantryConfig before = new GantryConfig(), after = new GantryConfig();
        assertTrue(HistoricalJobPreview.compatible(before.toPlotSettings(), after.toPlotSettings()));
        after.gcode.machineWidth++;
        assertFalse(HistoricalJobPreview.compatible(before.toPlotSettings(), after.toPlotSettings()));
        after.gcode.machineWidth = before.gcode.machineWidth; after.invertX = !before.invertX;
        assertFalse(HistoricalJobPreview.compatible(before.toPlotSettings(), after.toPlotSettings()));
    }
    private static double luminance(Color c) {
        return .2126*linear(c.getRed()) + .7152*linear(c.getGreen()) + .0722*linear(c.getBlue());
    }
    private static double linear(int v) { double s=v/255.; return s<=.04045?s/12.92:Math.pow((s+.055)/1.055,2.4); }
}
