package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.app.plot.GantryConfig;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportDiagnosticsTest {

    @Test
    void reportIncludesEnvironmentBackendAndSafeConfigSummary() {
        GantryConfig config = new GantryConfig();
        config.mock = false;
        config.gcode.serialPort = "/dev/ttyUSB1";
        config.gcode.baudRate = 115200;
        config.gcode.machineWidth = 594.0;
        config.gcode.machineHeight = 841.0;
        config.machineOrigin = "Top-Left";
        config.orientation = "Portrait";
        config.gcode.penMode = "servo";
        config.gcode.feedRateDraw = 900;
        config.gcode.feedRateTravel = 2500;

        String report = SupportDiagnostics.build(new SupportDiagnostics.Snapshot(
                "1.2.3-test", config, true, "Serial timeout", "Connected.\nERROR: Serial timeout\n"));

        assertTrue(report.contains("Gantry Support Diagnostics"));
        assertTrue(report.contains("Gantry version: 1.2.3-test"));
        assertTrue(report.contains("Java version:"));
        assertTrue(report.contains("Operating system:"));
        assertTrue(report.contains("Backend: Serial GRBL"));
        assertTrue(report.contains("Connected: yes"));
        assertTrue(report.contains("Serial port: /dev/ttyUSB1"));
        assertTrue(report.contains("Baud rate: 115200"));
        assertTrue(report.contains("Machine size: 594.0 x 841.0 mm"));
        assertTrue(report.contains("Origin: Top-Left"));
        assertTrue(report.contains("Orientation: Portrait"));
        assertTrue(report.contains("Pen mode: servo"));
        assertTrue(report.contains("Feed rates: draw 900 mm/min, travel 2500 mm/min"));
        assertTrue(report.contains("Last error: Serial timeout"));
    }

    @Test
    void reportTailsRecentConsoleLinesOnly() {
        GantryConfig config = new GantryConfig();
        String console = IntStream.rangeClosed(1, 50)
                .mapToObj(i -> "line " + i)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        String report = SupportDiagnostics.build(new SupportDiagnostics.Snapshot(
                "1.0.0", config, false, "", console));

        assertFalse(report.contains("line 1\n"));
        assertFalse(report.contains("line 10\n"));
        assertTrue(report.contains("line 11"));
        assertTrue(report.contains("line 50"));
    }
}
