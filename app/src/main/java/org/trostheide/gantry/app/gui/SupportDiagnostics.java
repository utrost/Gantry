package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.GantryConfig;

import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Builds the plain-text support bundle copied from Help > Copy Diagnostics. */
final class SupportDiagnostics {
    private static final int CONSOLE_TAIL_LINES = 40;

    private SupportDiagnostics() {
    }

    record Snapshot(String gantryVersion, GantryConfig config, boolean connected, String lastError, String consoleText) {
    }

    static String build(Snapshot snapshot) {
        GantryConfig config = snapshot.config() == null ? new GantryConfig() : snapshot.config();
        StringBuilder report = new StringBuilder();
        report.append("Gantry Support Diagnostics\n");
        report.append("Generated: ").append(Instant.now()).append('\n');
        report.append("Gantry version: ").append(value(snapshot.gantryVersion(), "unknown")).append('\n');
        report.append("Java version: ").append(System.getProperty("java.version", "unknown")).append('\n');
        report.append("Operating system: ").append(System.getProperty("os.name", "unknown"))
                .append(' ').append(System.getProperty("os.version", "unknown"))
                .append(' ').append(System.getProperty("os.arch", "unknown")).append('\n');
        report.append('\n');
        report.append("Backend: ").append(config.mock ? "Mock backend" : "Serial GRBL").append('\n');
        report.append("Connected: ").append(snapshot.connected() ? "yes" : "no").append('\n');
        report.append("Serial port: ").append(value(config.gcode.serialPort, "not configured")).append('\n');
        report.append("Baud rate: ").append(config.gcode.baudRate).append('\n');
        report.append("Machine size: ").append(config.gcode.machineWidth).append(" x ")
                .append(config.gcode.machineHeight).append(" mm\n");
        report.append("Origin: ").append(value(config.machineOrigin, "unknown")).append('\n');
        report.append("Orientation: ").append(value(config.orientation, "unknown")).append('\n');
        report.append("Canvas alignment: ").append(value(config.canvasAlignment, "none")).append('\n');
        report.append("Axis corrections: invertX=").append(config.invertX)
                .append(", invertY=").append(config.invertY)
                .append(", swapXY=").append(config.swapXY)
                .append(", flipY=").append(config.flipY).append('\n');
        report.append("Pen mode: ").append(value(config.gcode.penMode, "unknown")).append('\n');
        report.append("Feed rates: draw ").append(config.gcode.feedRateDraw)
                .append(" mm/min, travel ").append(config.gcode.feedRateTravel).append(" mm/min\n");
        report.append("Soft limits: ").append(config.softLimits ? "enabled" : "disabled").append('\n');
        report.append("Preflight before start: ").append(config.preflightBeforeStart ? "enabled" : "disabled").append('\n');
        report.append("Stations configured: ").append(config.stations == null ? 0 : config.stations.size()).append('\n');
        report.append('\n');
        report.append("Last error: ").append(value(snapshot.lastError(), "none recorded")).append('\n');
        report.append('\n');
        report.append("Recent console log (last ").append(CONSOLE_TAIL_LINES).append(" lines):\n");
        String tail = tail(snapshot.consoleText());
        report.append(tail.isBlank() ? "(empty)\n" : tail);
        if (!report.toString().endsWith("\n")) {
            report.append('\n');
        }
        return report.toString();
    }

    static String lastErrorFromConsole(String consoleText) {
        if (consoleText == null || consoleText.isBlank()) {
            return "";
        }
        String[] lines = consoleText.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.startsWith("ERROR:")) {
                return line.substring("ERROR:".length()).trim();
            }
        }
        return "";
    }

    private static String tail(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] lines = text.stripTrailing().split("\\R");
        int start = Math.max(0, lines.length - CONSOLE_TAIL_LINES);
        return Arrays.stream(lines, start, lines.length).collect(Collectors.joining("\n", "", "\n"));
    }

    private static String value(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text;
    }
}
