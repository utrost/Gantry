package org.trostheide.gantry.plotter;

import java.util.List;

/**
 * Read-only firmware limits intersected with an operator's existing positive-work-space profile.
 */
public record FirmwareEnvelope(
        double minX,
        double maxX,
        double minY,
        double maxY,
        double offsetX,
        double offsetY,
        double maxFeed,
        double profileWidth,
        double profileHeight,
        int drawFeed,
        int travelFeed,
        String penMode) {
    public enum Space {
        NEGATIVE_X_NEGATIVE_Y("X −travel…0; Y −travel…0", false, false),
        POSITIVE_X_POSITIVE_Y("X 0…travel; Y 0…travel", true, true),
        NEGATIVE_X_POSITIVE_Y("X −travel…0; Y 0…travel", false, true),
        POSITIVE_X_NEGATIVE_Y("X 0…travel; Y −travel…0", true, false);
        final boolean positiveX, positiveY;
        private final String label;

        Space(String label, boolean x, boolean y) {
            this.label = label;
            positiveX = x;
            positiveY = y;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record Limits(double travelX, double travelY, double feedX, double feedY, Double feedZ) {
        public Limits {
            for (double value : new double[] {travelX, travelY, feedX, feedY})
                if (!Double.isFinite(value) || value <= 0)
                    throw new IllegalArgumentException("Invalid firmware travel/feed limits");
        }

        public static Limits parse(List<String> lines) {
            if (lines == null || lines.stream().noneMatch(s -> "ok".equalsIgnoreCase(s.trim())))
                throw new IllegalArgumentException(
                        "Firmware settings were not acknowledged; no limits assumed.");
            Double reportInches = GrblSettings.findSetting(lines, 13);
            if (reportInches == null || !Double.isFinite(reportInches) || reportInches != 0) {
                throw new IllegalArgumentException(
                        "Firmware $13=0 millimetre status reporting is required;"
                            + " missing/unsupported units cannot establish WCO. No firmware setting"
                            + " was changed.");
            }
            return new Limits(
                    required(lines, 130),
                    required(lines, 131),
                    required(lines, 110),
                    required(lines, 111),
                    GrblSettings.findSetting(lines, 112));
        }

        private static double required(List<String> lines, int number) {
            Double value = GrblSettings.findSetting(lines, number);
            if (value == null || !Double.isFinite(value) || value <= 0)
                throw new IllegalArgumentException(
                        "Missing or invalid firmware $"
                                + number
                                + "; review cannot assume a limit.");
            return value;
        }

        public FirmwareEnvelope review(GcodeOptions options, double[] wco, Space space) {
            if (wco == null
                    || wco.length < 2
                    || !Double.isFinite(wco[0])
                    || !Double.isFinite(wco[1]))
                throw new IllegalArgumentException(
                        "Fresh machine/work offset unavailable; coordinate assumptions are"
                                + " unsupported.");
            if (space == null)
                throw new IllegalArgumentException(
                        "Choose the machine-coordinate convention explicitly.");
            if (options.feedRateDraw <= 0 || options.feedRateTravel <= 0) {
                throw new IllegalArgumentException(
                        "Configured draw and travel feeds must be positive.");
            }
            double feed = Math.min(feedX, feedY);
            if (options.feedRateDraw > feed || options.feedRateTravel > feed)
                throw new IllegalArgumentException(
                        "Configured feed exceeds conservative XY firmware cap "
                                + feed
                                + " mm/min. Review Pen & Speeds manually; no feed was changed.");
            if ("zaxis".equals(options.penMode)
                    && (feedZ == null
                            || !Double.isFinite(feedZ)
                            || feedZ <= 0
                            || options.feedRateDraw > feedZ))
                throw new IllegalArgumentException(
                        "Configured Z lift feed exceeds or lacks a valid $112 limit. Review speeds"
                                + " manually.");
            if (!Double.isFinite(options.machineWidth)
                    || !Double.isFinite(options.machineHeight)
                    || options.machineWidth <= 0
                    || options.machineHeight <= 0)
                throw new IllegalArgumentException("Invalid configured machine dimensions.");
            var envelope =
                    new FirmwareEnvelope(
                            Math.max(0, (space.positiveX ? 0 : -travelX) - wco[0]),
                            Math.min(
                                    options.machineWidth, (space.positiveX ? travelX : 0) - wco[0]),
                            Math.max(0, (space.positiveY ? 0 : -travelY) - wco[1]),
                            Math.min(
                                    options.machineHeight,
                                    (space.positiveY ? travelY : 0) - wco[1]),
                            wco[0],
                            wco[1],
                            feed,
                            options.machineWidth,
                            options.machineHeight,
                            options.feedRateDraw,
                            options.feedRateTravel,
                            options.penMode);
            envelope.check(0, 0); // every normal job parks here
            return envelope;
        }
    }

    public void check(double x, double y) {
        if (!Double.isFinite(x)
                || !Double.isFinite(y)
                || x < minX - 1e-6
                || x > maxX + 1e-6
                || y < minY - 1e-6
                || y > maxY + 1e-6)
            throw new GcodeBackendException(
                    "Work target ("
                            + x
                            + ", "
                            + y
                            + ") exceeds reviewed firmware/profile envelope ["
                            + minX
                            + "…"
                            + maxX
                            + ", "
                            + minY
                            + "…"
                            + maxY
                            + "]. Recheck work origin, profile and station clearance.");
    }

    public boolean matches(GcodeOptions options, double[] wco) {
        return wco != null
                && Math.abs(wco[0] - offsetX) < 1e-6
                && Math.abs(wco[1] - offsetY) < 1e-6
                && java.util.Objects.equals(options.penMode, penMode)
                && options.machineWidth == profileWidth
                && options.machineHeight == profileHeight
                && options.feedRateDraw == drawFeed
                && options.feedRateTravel == travelFeed;
    }
}
