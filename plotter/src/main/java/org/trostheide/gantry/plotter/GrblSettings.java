package org.trostheide.gantry.plotter;

import java.util.List;
import java.util.Locale;

/**
 * Parses GRBL's {@code $$} settings dump. GRBL replies to {@code $$} with one {@code $N=value} line
 * per setting (e.g. {@code $100=80.000}) followed by {@code ok}; this pulls out the steps/mm values
 * the axis-calibration wizard reads and rewrites ({@code $100} = X steps/mm, {@code $101} = Y).
 */
public final class GrblSettings {

    public static final int X_STEPS_PER_MM = 100;
    public static final int Y_STEPS_PER_MM = 101;

    private GrblSettings() {
    }

    /**
     * Returns the value of {@code $<number>} from a {@code $$} response, or {@code null} if no such
     * line is present (or it can't be parsed as a number). Lines are matched leniently — leading
     * whitespace and a trailing comment after the value are both tolerated.
     */
    public static Double findSetting(List<String> dollarDollarOutput, int number) {
        if (dollarDollarOutput == null) {
            return null;
        }
        String prefix = "$" + number + "=";
        for (String raw : dollarDollarOutput) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (line.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                String rest = line.substring(prefix.length()).trim();
                // Strip any trailing comment GRBL appends, e.g. "$100=80.000 (x, step/mm)".
                int cut = rest.indexOf(' ');
                if (cut >= 0) {
                    rest = rest.substring(0, cut);
                }
                cut = rest.indexOf('(');
                if (cut >= 0) {
                    rest = rest.substring(0, cut);
                }
                try {
                    return Double.parseDouble(rest.trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** The {@code $N=value} write command for a setting, value formatted to 3 decimals (GRBL's form). */
    public static String writeCommand(int number, double value) {
        return String.format(Locale.ROOT, "$%d=%.3f", number, value);
    }

    /**
     * Computes corrected steps/mm from a calibration measurement: if {@code commandedMm} was sent
     * but the axis actually moved {@code measuredMm}, the true steps/mm is the old value scaled by
     * commanded/measured. Returns {@code null} if the measurement is non-positive (unusable).
     */
    public static Double correctedStepsPerMm(double currentStepsPerMm, double commandedMm, double measuredMm) {
        if (measuredMm <= 0 || commandedMm <= 0 || currentStepsPerMm <= 0) {
            return null;
        }
        return currentStepsPerMm * (commandedMm / measuredMm);
    }
}
