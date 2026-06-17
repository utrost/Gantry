package org.trostheide.gantry.plotter;

import java.util.List;
import java.util.Locale;

/**
 * Stateless G-code line formatting shared by {@link GcodeBackend} (live serial streaming) and
 * {@link GcodeFileBackend} (export to a {@code .gcode} file for later replay).
 */
public final class GcodeFormatter {

    private GcodeFormatter() {
    }

    /** GRBL setup sequence sent right after connecting: unlock alarm, set units/mode, zero the origin. */
    public static List<String> setupSequence() {
        return List.of("$X", "G21", "G90", "G92 X0 Y0");
    }

    /** Returns to the work origin with the pen up, used when disconnecting / ending a job. */
    public static String home() {
        return "G0 X0 Y0";
    }

    /** GRBL homing cycle: seeks the limit switches and sets machine position at the switches. */
    public static String homingCycle() {
        return "$H";
    }

    /** Zeroes the work coordinate origin at the current position (used right after homing). */
    public static String zeroWorkOrigin() {
        return "G92 X0 Y0";
    }

    /** Absolute rapid move (pen up) at the travel feed rate. */
    public static String moveto(double x, double y, int feedRateTravel) {
        return String.format(Locale.ROOT, "G0 X%.3f Y%.3f F%d", x, y, feedRateTravel);
    }

    /** Absolute linear move (pen down) at the draw feed rate. */
    public static String lineto(double x, double y, int feedRateDraw) {
        return String.format(Locale.ROOT, "G1 X%.3f Y%.3f F%d", x, y, feedRateDraw);
    }

    /** Relative move bracketed by G91/G90, at the travel feed rate. */
    public static List<String> relativeMove(double dx, double dy, int feedRateTravel) {
        return List.of("G91", String.format(Locale.ROOT, "G1 X%.3f Y%.3f F%d", dx, dy, feedRateTravel), "G90");
    }

    /** The command that lifts the pen for {@code options.penMode}, or null if the mode has none. */
    public static String penUp(GcodeOptions options) {
        return switch (options.penMode) {
            case "servo" -> String.format(Locale.ROOT, "M280 P%d S%d", options.servoPin, options.penServoUp);
            case "zaxis" -> String.format(Locale.ROOT, "G0 Z%.2f", options.zUp);
            case "m3m5" -> String.format(Locale.ROOT, "M3 S%d", options.penServoUp);
            default -> null;
        };
    }

    /** The command that lowers the pen for {@code options.penMode}, or null if the mode has none. */
    public static String penDown(GcodeOptions options) {
        return switch (options.penMode) {
            case "servo" -> String.format(Locale.ROOT, "M280 P%d S%d", options.servoPin, options.penServoDown);
            case "zaxis" -> String.format(Locale.ROOT, "G1 Z%.2f F%d", options.zDown, options.feedRateDraw);
            case "m3m5" -> String.format(Locale.ROOT, "M3 S%d", options.penServoDown);
            default -> null;
        };
    }
}
