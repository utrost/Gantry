package org.trostheide.gantry.plotter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;

class FirmwareEnvelopeTest {
    @Test
    void shiftedOriginIntersectsFirmwareAndSmallerProfileWithoutChangingOptions() {
        var options = new GcodeOptions();
        options.machineWidth = 80;
        options.machineHeight = 60;
        var limits = new FirmwareEnvelope.Limits(100, 100, 4000, 5000, 3000.0);
        var envelope =
                limits.review(
                        options,
                        new double[] {-90, -70},
                        FirmwareEnvelope.Space.NEGATIVE_X_NEGATIVE_Y);
        envelope.check(80, 60);
        assertThrows(GcodeBackendException.class, () -> envelope.check(81, 20));
        assertEquals(80, options.machineWidth);
        assertEquals(60, options.machineHeight);
        var shifted =
                limits.review(
                        options,
                        new double[] {-50, -50},
                        FirmwareEnvelope.Space.NEGATIVE_X_NEGATIVE_Y);
        assertThrows(GcodeBackendException.class, () -> shifted.check(51, 20));
        assertThrows(
                GcodeBackendException.class,
                () ->
                        limits.review(
                                options,
                                new double[] {20, 0},
                                FirmwareEnvelope.Space.NEGATIVE_X_NEGATIVE_Y));
    }

    @Test
    void rejectsUnsupportedMissingNonfiniteAndOverSpeedWithoutCapping() {
        var options = new GcodeOptions();
        for (var lines :
                List.of(
                        List.of("ok"),
                        List.of("$13=0", "$130=NaN", "$131=100", "$110=5000", "$111=5000", "ok"),
                        List.of("error:20")))
            assertThrows(
                    IllegalArgumentException.class, () -> FirmwareEnvelope.Limits.parse(lines));
        var limits = new FirmwareEnvelope.Limits(100, 100, 1000, 2000, 500.0);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        limits.review(
                                options,
                                new double[] {0, 0},
                                FirmwareEnvelope.Space.POSITIVE_X_POSITIVE_Y));
        assertEquals(3000, options.feedRateTravel);
        options.feedRateTravel = 500;
        options.feedRateDraw = 500;
        options.penMode = "zaxis";
        assertDoesNotThrow(
                () ->
                        limits.review(
                                options,
                                new double[] {0, 0},
                                FirmwareEnvelope.Space.POSITIVE_X_POSITIVE_Y));
        options.feedRateDraw = 600;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        limits.review(
                                options,
                                new double[] {0, 0},
                                FirmwareEnvelope.Space.POSITIVE_X_POSITIVE_Y));
    }

    @Test
    void productionSimulatorRequiresExplicitReviewAndInvalidatesOnStopOriginAndSettings() {
        var options = new GcodeOptions();
        options.penDownDelayMillis = 0;
        var backend = GcodeBackend.serialSimulator(options);
        backend.requireSafetyReview(true);
        try {
            assertTrue(backend.connect());
            assertThrows(GcodeBackendException.class, () -> backend.moveto(1, 1));
            var snapshot = backend.readSafetySnapshot();
            assertThrows(
                    GcodeBackendException.class,
                    () ->
                            backend.approveSafetyReview(
                                    snapshot, FirmwareEnvelope.Space.POSITIVE_X_POSITIVE_Y, false));
            backend.approveSafetyReview(
                    snapshot, FirmwareEnvelope.Space.POSITIVE_X_POSITIVE_Y, true);
            backend.moveto(1, 1);
            backend.awaitMotionComplete();
            var refreshed = backend.readSafetySnapshot();
            assertTrue(
                    backend.needsSafetyReview(),
                    "Refreshing limits invalidates the earlier receipt even before approval");
            assertThrows(GcodeBackendException.class, () -> backend.moveto(2, 2));
            backend.approveSafetyReview(
                    refreshed, FirmwareEnvelope.Space.POSITIVE_X_POSITIVE_Y, true);
            backend.sendRaw("G92 X0 Y0");
            assertTrue(backend.needsSafetyReview());
            backend.awaitMotionComplete();
            backend.approveSafetyReview(
                    backend.readSafetySnapshot(),
                    FirmwareEnvelope.Space.POSITIVE_X_POSITIVE_Y,
                    true);
            backend.haltMotion();
            assertTrue(backend.needsSafetyReview());
            backend.approveSafetyReview(
                    backend.readSafetySnapshot(),
                    FirmwareEnvelope.Space.POSITIVE_X_POSITIVE_Y,
                    true);
            options.feedRateTravel = 500;
            assertTrue(backend.needsSafetyReview());
        } finally {
            backend.disconnect();
        }
    }

    @Test
    void rejectsUnreviewedRawModalChangesStaleReviewsAndDisconnectReturn() {
        var options = new GcodeOptions();
        options.bootDelayMillis = 0;
        options.penDownDelayMillis = 0;
        options.positionPollIntervalSeconds = .01;
        var transport = new SerialSimulatorTransport(options);
        var backend = new GcodeBackend(options, (port, baud) -> transport);
        backend.requireSafetyReview(true);
        try {
            assertTrue(backend.connect());
            var review = backend.readSafetySnapshot();
            backend.approveSafetyReview(review, FirmwareEnvelope.Space.POSITIVE_X_POSITIVE_Y, true);
            for (String line :
                    List.of("G91", "G1X1X2", "G1XNaN", "G1X1F99999", "G2X1Y2", "$J=G91X1F100"))
                assertThrows(GcodeBackendException.class, () -> backend.sendRaw(line), line);
            assertFalse(backend.needsSafetyReview());
            backend.moveto(10, 10);
            backend.awaitMotionComplete();
            backend.invalidateSafetyReview();
            assertThrows(
                    GcodeBackendException.class,
                    () ->
                            backend.approveSafetyReview(
                                    review, FirmwareEnvelope.Space.POSITIVE_X_POSITIVE_Y, true));
            int before = transport.commands().size();
            backend.disconnect();
            assertTrue(backend.needsSafetyReview());
            assertFalse(
                    transport.commands().subList(before, transport.commands().size()).stream()
                            .anyMatch(s -> s.startsWith("G0 X") || s.startsWith("G1 X")),
                    "Uncertain disconnect must not return XY to origin");
        } finally {
            backend.disconnect();
        }
    }

    @Test
    void rejectsMissingOrInchStatusUnitsBeforeTrustingWorkOffsets() {
        for (String units : List.of("", "$13=1", "$13=NaN", "$13=-1")) {
            var lines =
                    new java.util.ArrayList<>(
                            List.of("$130=100", "$131=100", "$110=4000", "$111=4000", "ok"));
            if (!units.isBlank()) lines.add(units);
            assertThrows(
                    IllegalArgumentException.class, () -> FirmwareEnvelope.Limits.parse(lines));
        }
        assertDoesNotThrow(
                () ->
                        FirmwareEnvelope.Limits.parse(
                                List.of(
                                        "$13=0",
                                        "$130=100",
                                        "$131=100",
                                        "$110=4000",
                                        "$111=4000",
                                        "ok")));
    }

    @Test
    void serialInchReportsAreModeledButNeverApprovedOrSilentlyReconfigured() {
        var options = new GcodeOptions();
        options.bootDelayMillis = 0;
        options.penDownDelayMillis = 0;
        options.positionPollIntervalSeconds = .01;
        var transport = new SerialSimulatorTransport(options);
        var backend = new GcodeBackend(options, (port, baud) -> transport);
        try {
            assertTrue(backend.connect());
            backend.moveto(25.4, 0);
            backend.awaitMotionComplete();
            backend.requireSafetyReview(true);
            backend.sendRaw("$13=1");
            assertThrows(IllegalArgumentException.class, backend::readSafetySnapshot);
            assertEquals(
                    1,
                    backend.queryPosition()[0],
                    .001,
                    "Simulator reports actual inch status units");
            assertThrows(GcodeBackendException.class, () -> backend.moveto(1, 0));
            assertFalse(transport.commands().contains("$13=0"), "Review is read-only");
            backend.sendRaw("$13=0");
            backend.approveSafetyReview(
                    backend.readSafetySnapshot(),
                    FirmwareEnvelope.Space.POSITIVE_X_POSITIVE_Y,
                    true);
            assertFalse(backend.needsSafetyReview());
        } finally {
            backend.disconnect();
        }
    }
}
