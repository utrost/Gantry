package org.trostheide.svgtoolbox;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigBuilderTest {

    @Test
    void testBuilderDefaults() {
        Config config = new Config.Builder()
                .inputPath("in.svg")
                .outputPath("out.svg")
                .build();

        assertEquals("in.svg", config.inputPath());
        assertEquals("out.svg", config.outputPath());
        assertEquals(0f, config.strokeWidth());
        assertFalse(config.enableHatching());
        assertEquals("linear", config.hatchPattern());
        assertEquals(45.0, config.hatchAngle());
        assertEquals(5.0, config.hatchGap());
        assertEquals(0.0, config.rotationDegrees());
        assertFalse(config.optimizePaths());
        assertFalse(config.printStats());
        assertNull(config.cropBounds());
        assertTrue(config.hiddenLayers().isEmpty());
        assertTrue(config.overrides().isEmpty());
        assertTrue(config.strokeWidthOverrides().isEmpty());
        assertTrue(config.noHatchColors().isEmpty());
        assertTrue(config.palette().isEmpty());
        assertEquals(100.0, config.minHatchArea());
        assertEquals(0.0, config.simplifyTolerance());

        HatchStyle gs = config.globalStyle();
        assertEquals(45.0, gs.angle());
        assertEquals(5.0, gs.gap());
        assertEquals("linear", gs.patternName());
    }

    @Test
    void testBuilderWithPerLayerOverrides() {
        HatchStyle crossStyle = new HatchStyle(90.0, 8.0, "cross");
        HatchStyle zigzagStyle = new HatchStyle(30.0, 3.0, "zigzag");

        Config config = new Config.Builder()
                .inputPath("in.svg")
                .outputPath("out.svg")
                .strokeWidth(1.0f)
                .enableHatching(true)
                .overrides(Map.of(
                        "#ff0000", crossStyle,
                        "#00ff00", zigzagStyle))
                .strokeWidthOverrides(Map.of(
                        "#ff0000", 2.0f,
                        "#00ff00", 0.5f))
                .globalStyle(new HatchStyle(45.0, 5.0, "linear"))
                .build();

        assertTrue(config.enableHatching());
        assertEquals(1.0f, config.strokeWidth());

        // Per-layer overrides
        assertEquals(crossStyle, config.overrides().get("#ff0000"));
        assertEquals(zigzagStyle, config.overrides().get("#00ff00"));
        assertEquals(2.0f, config.strokeWidthOverrides().get("#ff0000"));
        assertEquals(0.5f, config.strokeWidthOverrides().get("#00ff00"));

        // Global fallback
        assertEquals("linear", config.globalStyle().patternName());
    }

    @Test
    void testBuilderNullSafety() {
        Config config = new Config.Builder()
                .inputPath("in.svg")
                .outputPath("out.svg")
                .globalStyle(null)
                .hatchPattern(null)
                .palette(null)
                .overrides(null)
                .strokeWidthOverrides(null)
                .hiddenLayers(null)
                .noHatchColors(null)
                .build();

        assertNotNull(config.globalStyle());
        assertEquals("linear", config.globalStyle().patternName());
        assertEquals("linear", config.hatchPattern());
        assertNotNull(config.palette());
        assertNotNull(config.overrides());
        assertNotNull(config.strokeWidthOverrides());
        assertNotNull(config.hiddenLayers());
        assertNotNull(config.noHatchColors());
    }
}
