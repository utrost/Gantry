package org.trostheide.gantry.app.plot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GantryConfigTest {

    private static GantryConfig landscape(String origin) {
        GantryConfig c = new GantryConfig();
        c.gcode.machineWidth = 841.0;
        c.gcode.machineHeight = 594.0;
        c.orientation = "Landscape";
        c.machineOrigin = origin;
        return c;
    }

    @Test
    void extraInvertCancelsOriginDerivedInversion() {
        // A bottom origin gives a baseline inverted Y; Extra Invert Y must be able to cancel it
        // (the bug: with OR it could not, so a bottom-origin machine's reversed jog was unfixable).
        GantryConfig c = landscape("Bottom-Left");
        assertTrue(c.toPlotSettings().invertY, "bottom origin inverts Y by default");

        c.invertY = true;
        assertFalse(c.toPlotSettings().invertY, "Extra Invert Y cancels the bottom-origin inversion");
    }

    @Test
    void rightOriginInvertsXAndIsCancelable() {
        GantryConfig c = landscape("Top-Right");
        assertTrue(c.toPlotSettings().invertX, "right origin inverts X");

        c.invertX = true;
        assertFalse(c.toPlotSettings().invertX, "Extra Invert X cancels the right-origin inversion");
    }

    @Test
    void extraInvertAddsInversionWhenOriginDoesNot() {
        GantryConfig c = landscape("Top-Left"); // neither right nor bottom
        assertFalse(c.toPlotSettings().invertX);
        assertFalse(c.toPlotSettings().invertY);

        c.invertX = true;
        c.invertY = true;
        assertTrue(c.toPlotSettings().invertX, "Extra Invert X adds inversion a top-left origin lacks");
        assertTrue(c.toPlotSettings().invertY, "Extra Invert Y adds inversion a top-left origin lacks");
    }

    @Test
    void defaultExtraInvertOffMatchesOriginBaseline() {
        // Regression guard: with Extra Invert off, behaviour is unchanged from before (origin-only).
        assertFalse(landscape("Top-Left").toPlotSettings().invertX);
        assertFalse(landscape("Top-Left").toPlotSettings().invertY);
        assertTrue(landscape("Bottom-Right").toPlotSettings().invertX);
        assertTrue(landscape("Bottom-Right").toPlotSettings().invertY);
    }

    @Test
    void exposesBothPhysicalOriginComponentsToAlignmentPipeline() {
        PlotSettings topRight = landscape("Top-Right").toPlotSettings();
        assertTrue(topRight.originRight);
        assertFalse(topRight.originBottom);

        PlotSettings bottomLeft = landscape("Bottom-Left").toPlotSettings();
        assertFalse(bottomLeft.originRight);
        assertTrue(bottomLeft.originBottom);
    }

    @Test
    void portraitKeepsUserFacingAlignmentLabel() {
        GantryConfig c = landscape("Top-Right");
        c.orientation = "Portrait";
        c.canvasAlignment = "Bottom Left";

        assertEquals("Bottom Left", c.toPlotSettings().canvasAlign);
    }

    @Test
    void persistsStartupWelcomePreference(@TempDir Path tempDir) throws Exception {
        GantryConfig config = new GantryConfig();
        config.showWelcomeOnStartup = true;
        Path file = tempDir.resolve("config.json");

        ConfigStore.save(config, file.toFile());

        assertTrue(ConfigStore.load(file.toFile()).showWelcomeOnStartup);
    }

    @Test
    void migratesLegacyWorkingDirectoryConfigOnce(@TempDir Path tempDir) throws Exception {
        Path legacy = tempDir.resolve("old/config.json");
        Path stable = tempDir.resolve("user/Gantry/config.json");
        GantryConfig configured = new GantryConfig();
        configured.gcode.penMode = "zaxis";
        ConfigStore.save(configured, legacy.toFile());

        assertEquals(stable.toFile().getAbsoluteFile(),
                ConfigStore.migrateLegacy(legacy.toFile(), stable.toFile()));
        assertEquals("zaxis", ConfigStore.load(stable.toFile()).gcode.penMode);

        configured.gcode.penMode = "servo";
        ConfigStore.save(configured, legacy.toFile());
        ConfigStore.migrateLegacy(legacy.toFile(), stable.toFile());
        assertEquals("zaxis", ConfigStore.load(stable.toFile()).gcode.penMode,
                "an existing user config must never be overwritten by the legacy file");
    }

    @Test
    void saveCreatesUserConfigDirectories(@TempDir Path tempDir) throws Exception {
        Path nested = tempDir.resolve("a/b/c/config.json");
        ConfigStore.save(new GantryConfig(), nested.toFile());
        assertTrue(nested.toFile().isFile());
    }
}
