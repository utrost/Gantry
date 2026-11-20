package org.trostheide.gantry.plotter;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class GrblFaultContractTest {
    private final FakeGrblTransport fake = new FakeGrblTransport();
    private GcodeBackend connect() {
        GcodeOptions o = new GcodeOptions(); o.bootDelayMillis = 0; o.penDownDelayMillis = 0;
        o.responseTimeoutSeconds = 1; o.positionPollIntervalSeconds = 60;
        GcodeBackend backend = new GcodeBackend(o, (port, baud) -> fake);
        assertTrue(backend.connect()); return backend;
    }
    @Test void rejectedMotionDoesNotChangeFakeCoordinatesAndFailureIsVisible() {
        GcodeBackend b = connect();
        try {
            fake.scriptNext("G1 X7.000 Y8.000 F3000", "error:20");
            assertThrows(GcodeBackendException.class, () -> b.moveto(7, 8));
            assertArrayEquals(new double[] {0, 0}, fake.position());
            fake.scriptNext("G2 X9 Y10", "error:20");
            assertEquals(List.of("error:20"), b.sendRaw("G2 X9 Y10"));
            assertArrayEquals(new double[] {0, 0}, fake.position());
        } finally { b.disconnect(); }
    }
    @Test void delayedRawAckAndUnsolicitedStatusAreKeptSeparate() throws Exception {
        GcodeBackend b = connect();
        String cmd = "G1 X7 Y8 F100";
        fake.scriptNext(cmd); // release the acknowledgement explicitly, without wall-clock delays
        AtomicReference<List<String>> reply = new AtomicReference<>();
        Thread worker = new Thread(() -> reply.set(b.sendRaw(cmd)));
        try {
            worker.start(); assertTrue(fake.awaitCommand(cmd, 2, TimeUnit.SECONDS));
            fake.injectLine("<Run|MPos:3.000,4.000,0.000|FS:100,0>");
            fake.injectLine("[MSG:controller note]");
            fake.injectLine("ok");
            worker.join(2000); assertFalse(worker.isAlive());
            assertEquals(List.of("[MSG:controller note]", "ok"), reply.get());
            assertArrayEquals(new double[] {3, 4}, b.queryPosition());
        } finally { worker.interrupt(); worker.join(2000); b.disconnect(); }
    }
    @Test void missingAckCanBeCancelledBeforeTheResponseDeadline() throws Exception {
        GcodeBackend b = connect();
        String cmd = "G1 X7.000 Y8.000 F3000";
        fake.scriptNext(cmd);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> { try { b.moveto(7, 8); } catch (Throwable e) { failure.set(e); } });
        try {
            worker.start(); assertTrue(fake.awaitCommand(cmd, 2, TimeUnit.SECONDS));
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> { worker.interrupt(); worker.join(1000); });
            assertFalse(worker.isAlive()); assertInstanceOf(GcodeBackendException.class, failure.get());
            assertTrue(worker.isInterrupted());
            assertInstanceOf(InterruptedException.class, failure.get().getCause());
        } finally { worker.interrupt(); worker.join(2000); b.disconnect(); }
    }
    @Test void disconnectWhileAcknowledgementIsPendingWakesTheWorker() throws Exception {
        GcodeBackend b = connect(); String cmd = "G1 X7.000 Y8.000 F3000";
        fake.scriptNext(cmd);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> { try { b.moveto(7, 8); } catch (Throwable e) { failure.set(e); } });
        try {
            worker.start(); assertTrue(fake.awaitCommand(cmd, 2, TimeUnit.SECONDS));
            fake.failReads(new java.io.IOException("adapter disappeared during ack wait"));
            worker.join(2000);
            assertFalse(worker.isAlive()); assertInstanceOf(GcodeBackendException.class, failure.get());
            assertEquals(GcodeBackend.MachineState.DISCONNECTED, b.getMachineState());
        } finally { worker.interrupt(); worker.join(2000); b.disconnect(); }
    }

    @Test void alarmDuringDelayedAckAbortsThePendingCommand() throws Exception {
        GcodeBackend b = connect(); String cmd = "G1 X7.000 Y8.000 F3000";
        fake.scriptNext(cmd);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> { try { b.moveto(7, 8); } catch (Throwable e) { failure.set(e); } });
        try {
            worker.start(); assertTrue(fake.awaitCommand(cmd, 2, TimeUnit.SECONDS));
            fake.injectLine("ALARM:1"); worker.join(2000);
            assertFalse(worker.isAlive()); assertInstanceOf(GcodeBackendException.class, failure.get());
            assertEquals(GcodeBackend.MachineState.ALARM, b.getMachineState());
        } finally { worker.interrupt(); worker.join(2000); b.disconnect(); }
    }
}
