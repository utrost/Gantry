package org.trostheide.gantry.plotter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GcodeBackendTest {

    private final FakeGrblTransport fake = new FakeGrblTransport();
    private GcodeBackend backend;

    private GcodeBackend newBackend(GcodeOptions options) {
        options.bootDelayMillis = 0;
        backend = new GcodeBackend(options, (port, baud) -> fake);
        return backend;
    }

    private GcodeBackend connected() {
        GcodeOptions options = new GcodeOptions();
        GcodeBackend b = newBackend(options);
        assertTrue(b.connect());
        return b;
    }

    @AfterEach
    void cleanUp() {
        if (backend != null) {
            backend.disconnect();
        }
    }

    @Test
    void connectRunsGrblInitializationSequence() {
        GcodeBackend b = connected();

        List<String> sent = fake.sentCommands();
        assertEquals(List.of("$X", "G21", "G90", "G92 X0 Y0"), sent.subList(0, 4));
    }

    @Test
    void movetoSendsRapidMoveWithTravelFeedAndThreeDecimals() {
        GcodeBackend b = connected();

        b.moveto(12.3456, 78.9);

        String last = lastNonStatus();
        assertEquals("G0 X12.346 Y78.900 F3000", last);
    }

    @Test
    void linetoLowersPenThenSendsDrawMoveWithDrawFeed() {
        GcodeBackend b = connected();

        b.lineto(1.0, 2.0);

        List<String> sent = fake.sentCommands();
        // Default servo mode: pendown sends M280 with the "down" angle before the G1.
        assertTrue(sent.contains("M280 P0 S30"));
        assertEquals("G1 X1.000 Y2.000 F1000", sent.get(sent.size() - 1));
    }

    @Test
    void moveSendsRelativeMoveBracketedByG91AndG90() {
        GcodeBackend b = connected();
        int before = fake.sentCommands().size();

        b.move(5.0, -2.5);

        List<String> sent = fake.sentCommands().subList(before, fake.sentCommands().size());
        assertEquals(List.of("G91", "G1 X5.000 Y-2.500 F3000", "G90"), sent);
    }

    @Test
    void penupAndPendownInZaxisModeUseZMoves() {
        GcodeOptions options = new GcodeOptions();
        options.penMode = "zaxis";
        GcodeBackend b = newBackend(options);
        b.connect();

        b.penup();
        assertEquals("G0 Z5.00", lastNonStatus());

        b.pendown();
        assertEquals("G1 Z0.00 F1000", lastNonStatus());
    }

    @Test
    void pendownWithDepthLowersToStationZInZaxisMode() {
        GcodeOptions options = new GcodeOptions();
        options.penMode = "zaxis";
        GcodeBackend b = newBackend(options);
        b.connect();

        b.pendown(-3.5); // a per-station dip depth
        assertEquals("G1 Z-3.50 F1000", lastNonStatus());
    }

    @Test
    void pendownWithDepthFallsBackToServoCommandWhenNoZAxis() {
        GcodeBackend b = connected(); // default servo mode

        b.pendown(-3.5);
        assertEquals("M280 P0 S30", lastNonStatus()); // depth ignored; normal servo pen-down
    }

    @Test
    void penupAndPendownInM3M5ModeUseSpindleSpeed() {
        GcodeOptions options = new GcodeOptions();
        options.penMode = "m3m5";
        GcodeBackend b = newBackend(options);
        b.connect();

        b.penup();
        assertEquals("M3 S60", lastNonStatus());

        b.pendown();
        assertEquals("M3 S30", lastNonStatus());
    }

    @Test
    void statusReportsUpdateQueryPositionAndCallback() throws InterruptedException {
        GcodeBackend b = connected();
        AtomicReference<double[]> received = new AtomicReference<>();
        b.setPositionCallback((x, y) -> received.set(new double[] { x, y }));

        b.moveto(11.0, 22.0);

        double[] pos = awaitPosition(b, 11.0, 22.0);
        assertEquals(11.0, pos[0], 1e-6);
        assertEquals(22.0, pos[1], 1e-6);
        assertEquals(11.0, received.get()[0], 1e-6);
        assertEquals(22.0, received.get()[1], 1e-6);
    }

    /** Polls until the poller thread reports the given work position, or fails after 2s. */
    private double[] awaitPosition(GcodeBackend b, double x, double y) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        double[] pos;
        do {
            pos = b.queryPosition();
            if (pos != null && Math.abs(pos[0] - x) < 1e-6 && Math.abs(pos[1] - y) < 1e-6) {
                return pos;
            }
            Thread.sleep(20);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError("timed out waiting for position " + x + "," + y
                + ", last=" + java.util.Arrays.toString(pos));
    }

    @Test
    void adjustSpeedChangesReportedFeedOverride() throws InterruptedException {
        GcodeBackend b = connected();
        AtomicReference<Integer> percent = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        b.setSpeedCallback(p -> {
            percent.set(p);
            latch.countDown();
        });

        b.adjustSpeed("up");

        assertTrue(latch.await(2, TimeUnit.SECONDS), "expected a speed callback from the poller");
        assertEquals(110, percent.get());
    }

    @Test
    void sendRawCollectsResponsesUntilOk() {
        GcodeBackend b = connected();

        List<String> responses = b.sendRaw("$$");

        assertEquals(List.of("ok"), responses);
        assertTrue(fake.sentCommands().contains("$$"));
    }

    @Test
    void disconnectRaisesPenAndReturnsToOrigin() {
        GcodeBackend b = connected();
        b.lineto(5.0, 5.0); // pen down

        b.disconnect();
        backend = null; // already disconnected

        List<String> sent = fake.sentCommands();
        assertEquals("G0 X0 Y0", sent.get(sent.size() - 1));
        assertTrue(sent.contains("M280 P0 S60")); // penup before returning home
    }

    @Test
    void haltMotionClearsAlarmAndLiftsPen() {
        GcodeBackend b = connected();
        b.lineto(5.0, 5.0); // pen down

        int beforeHalt = fake.sentCommands().size();
        b.haltMotion();

        List<String> afterHalt = List.copyOf(
                fake.sentCommands().subList(beforeHalt, fake.sentCommands().size()));
        // Recovery clears the post-soft-reset alarm, then lifts the pen as the final action.
        assertTrue(afterHalt.contains("$X"), "expected $X alarm-clear, got " + afterHalt);
        assertEquals("M280 P0 S60", afterHalt.get(afterHalt.size() - 1)); // penup is last
    }

    @Test
    void holdStatusBlocksCommandsUntilGrblResumes() throws Exception {
        GcodeOptions options = new GcodeOptions();
        options.positionPollIntervalSeconds = 0.02;
        GcodeBackend b = newBackend(options);
        assertTrue(b.connect());
        fake.setMachineState("Hold:0");
        awaitState(b, GcodeBackend.MachineState.HOLD);

        Thread move = new Thread(() -> b.moveto(9, 7), "held-move-test");
        move.start();
        Thread.sleep(100);
        assertTrue(move.isAlive(), "move should wait while GRBL reports Hold");
        assertFalse(fake.sentCommands().contains("G0 X9.000 Y7.000 F3000"));

        fake.setMachineState("Run");
        awaitState(b, GcodeBackend.MachineState.RUN);
        move.join(2000);
        assertFalse(move.isAlive(), "move did not resume after GRBL left Hold");
        assertTrue(fake.sentCommands().contains("G0 X9.000 Y7.000 F3000"));
    }

    @Test
    void alarmUnblocksAndAbortsAnInFlightCommand() throws Exception {
        GcodeBackend b = connected();
        fake.setAutoAck(false);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread move = new Thread(() -> {
            try {
                b.moveto(4, 5);
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        }, "alarm-move-test");
        move.start();
        awaitCommand("G0 X4.000 Y5.000 F3000");

        fake.injectLine("ALARM:1");
        move.join(2000);

        assertFalse(move.isAlive(), "alarm did not unblock the waiting plot command");
        assertTrue(failure.get() instanceof GcodeBackendException, "expected fatal alarm failure");
        assertTrue(failure.get().getMessage().contains("ALARM:1"));
        assertEquals(GcodeBackend.MachineState.ALARM, b.getMachineState());
    }

    @Test
    void alarmStatusReportAlsoAbortsPlotCommands() throws Exception {
        GcodeOptions options = new GcodeOptions();
        options.positionPollIntervalSeconds = 0.02;
        GcodeBackend b = newBackend(options);
        assertTrue(b.connect());

        fake.setMachineState("Alarm");
        awaitState(b, GcodeBackend.MachineState.ALARM);

        GcodeBackendException failure = assertThrows(GcodeBackendException.class,
                () -> b.moveto(3, 4));
        assertTrue(failure.getMessage().contains("GRBL status: Alarm"));
    }

    @Test
    void serialWriteFailureIsThrownAndMarksBackendDisconnected() {
        GcodeOptions options = new GcodeOptions();
        options.positionPollIntervalSeconds = 60;
        GcodeBackend b = newBackend(options);
        assertTrue(b.connect());
        fake.failNextWrite(new IOException("cable unplugged"));

        GcodeBackendException failure = assertThrows(GcodeBackendException.class,
                () -> b.moveto(1, 2));

        assertTrue(failure.getMessage().contains("cable unplugged"));
        assertEquals(GcodeBackend.MachineState.DISCONNECTED, b.getMachineState());
    }

    @Test
    void serialReadFailureAbortsTheNextPlotCommand() throws Exception {
        GcodeBackend b = connected();
        fake.failReads(new IOException("adapter vanished"));
        awaitState(b, GcodeBackend.MachineState.DISCONNECTED);

        GcodeBackendException failure = assertThrows(GcodeBackendException.class,
                () -> b.moveto(1, 2));

        assertTrue(failure.getMessage().contains("adapter vanished"));
    }

    private void awaitState(GcodeBackend b, GcodeBackend.MachineState expected) throws Exception {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (b.getMachineState() == expected) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("timed out waiting for GRBL state " + expected
                + ", last=" + b.getMachineState());
    }

    private void awaitCommand(String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (fake.sentCommands().contains(expected)) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("timed out waiting for command " + expected);
    }

    /** Last command sent that isn't a "?" status poll response handler artifact. */
    private String lastNonStatus() {
        List<String> sent = fake.sentCommands();
        return sent.get(sent.size() - 1);
    }
}
