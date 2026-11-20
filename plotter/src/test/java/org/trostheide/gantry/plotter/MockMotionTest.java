package org.trostheide.gantry.plotter;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class MockMotionTest {
    /** First 50ms tick completes immediately; the second is held until interrupted/released. */
    private static class GatedMock extends MockPlotterBackend {
        final CountDownLatch moving = new CountDownLatch(1), release = new CountDownLatch(1);
        int ticks;
        GatedMock() { super(options(), null); }
        @Override protected void awaitStep(long millis) throws InterruptedException {
            if (++ticks > 1) { moving.countDown(); release.await(); }
        }
    }
    private static GcodeOptions options() {
        GcodeOptions o = new GcodeOptions(); o.feedRateDraw = 6000; o.feedRateTravel = 6000;
        o.penDownDelayMillis = 0; return o;
    }
    @Test void haltAndInterruptFreezeTheLastReachedPositionAndPermitASeparateLaterMove() throws Exception {
        for (boolean useHalt : List.of(false, true)) {
            GatedMock mock = new GatedMock(); mock.connect();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicBoolean interrupted = new AtomicBoolean();
            Thread worker = new Thread(() -> {
                try { mock.lineto(100, 0); }
                catch (Throwable e) { failure.set(e); interrupted.set(Thread.currentThread().isInterrupted()); }
            });
            worker.start();
            try {
                assertTrue(mock.moving.await(2, TimeUnit.SECONDS));
                assertArrayEquals(new double[] {5, 0}, mock.queryPosition());
                assertTrue(mock.isPenDown());
                assertTrue(mock.sendRaw("?").get(0).startsWith("<Run|"));
                assertThrows(IllegalStateException.class, () -> mock.moveto(1, 1));
                if (useHalt) { mock.haltMotion(); mock.haltMotion(); } else worker.interrupt();
                worker.join(2000);
                assertFalse(worker.isAlive());
                assertInstanceOf(CancellationException.class, failure.get());
                assertTrue(interrupted.get());
                assertArrayEquals(new double[] {5, 0}, mock.queryPosition());
                mock.penup(); assertFalse(mock.isPenDown());
                assertTrue(mock.sendRaw("?").get(0).startsWith("<Idle|"));
                mock.release.countDown(); mock.moveto(6, 0);
                assertArrayEquals(new double[] {6, 0}, mock.queryPosition());
            } finally { worker.interrupt(); mock.release.countDown(); worker.join(2000); mock.disconnect(); }
        }
    }
    @Test void connectionPenStateAndRawValidationAreExplicit() {
        MockPlotterBackend mock = new MockPlotterBackend(options(), null) {
            @Override protected void awaitStep(long millis) { }
        };
        assertNull(mock.queryPosition());
        assertThrows(IllegalStateException.class, () -> mock.moveto(1, 1));
        assertNotEquals(List.of("ok"), mock.sendRaw("G1 X1 Y1 F100"));
        mock.connect(); mock.lineto(1, 2); assertTrue(mock.isPenDown());
        mock.move(1, 1); assertTrue(mock.isPenDown());
        mock.moveto(4, 5); assertFalse(mock.isPenDown());
        for (String command : List.of("THIS IS NOT GCODE", "G20", "G2 X1 Y1", "G1 X1 X2", "G1 X1 F0", "$100=NaN", "$22=2")) {
            double[] before = mock.queryPosition();
            assertTrue(mock.sendRaw(command).get(0).startsWith("error:"), command);
            assertArrayEquals(before, mock.queryPosition());
        }
        assertEquals(List.of("ok"), mock.sendRaw("G91"));
        assertEquals(List.of("ok"), mock.sendRaw("G1 X2 Y3 F6000"));
        assertArrayEquals(new double[] {6, 8}, mock.queryPosition());
        mock.move(1, 1); // high-level relative movement restores G90
        mock.sendRaw("G1 X2 Y3");
        assertArrayEquals(new double[] {2, 3}, mock.queryPosition());
        mock.disconnect(); assertNull(mock.queryPosition());
        assertThrows(IllegalStateException.class, () -> mock.lineto(9, 9));
        mock.connect(); mock.moveto(1, 2); assertArrayEquals(new double[] {1, 2}, mock.queryPosition());
    }
    @Test void rawMovesInheritTheFeedEstablishedByHighLevelDrawingAndTravel() {
        GcodeOptions options = options(); options.feedRateDraw = 600; options.feedRateTravel = 1200;
        java.util.concurrent.atomic.AtomicLong waited = new java.util.concurrent.atomic.AtomicLong();
        MockPlotterBackend mock = new MockPlotterBackend(options, null) {
            @Override protected void awaitStep(long millis) { waited.addAndGet(millis); }
        };
        mock.connect(); mock.lineto(100, 0);
        waited.set(0); assertEquals(List.of("ok"), mock.sendRaw("G1 X200 Y0"));
        assertEquals(10_000, waited.get(), "raw G1 inherits the previous draw F600");
        mock.moveto(100, 0);
        waited.set(0); assertEquals(List.of("ok"), mock.sendRaw("G1 X0 Y0"));
        assertEquals(5_000, waited.get(), "raw G1 inherits the previous travel F1200");
        mock.disconnect();
    }

    @Test void rawPenCommandsAndDwellSupportEachConfiguredPenMode() {
        for (String mode : List.of("servo", "zaxis", "m3m5")) {
            GcodeOptions options = options(); options.penMode = mode;
            MockPlotterBackend mock = new MockPlotterBackend(options, null) {
                @Override protected void awaitStep(long millis) { }
            };
            mock.connect();
            assertEquals(List.of("ok"), mock.sendRaw(GcodeFormatter.penDown(options)));
            assertTrue(mock.isPenDown());
            assertEquals(List.of("ok"), mock.sendRaw("G4 P0.1"));
            assertEquals(List.of("ok"), mock.sendRaw("G0 X1 Y2"));
            assertTrue(mock.isPenDown(), "raw rapid must not silently lift the pen");
            assertEquals(List.of("ok"), mock.sendRaw(GcodeFormatter.penUp(options)));
            assertFalse(mock.isPenDown());
            assertTrue(mock.sendRaw("G4 P61").get(0).startsWith("error:"));
            mock.disconnect();
        }
    }

    @Test void rawReplayExecutesItsMovesAndConfiguredPenCommands() throws Exception {
        var file = java.nio.file.Files.createTempFile("mock-replay", ".gcode");
        try {
            GcodeOptions options = options();
            java.nio.file.Files.writeString(file, "G21\nG90\nM280 P0 S30\nG1 X10 Y20 F6000\nM280 P0 S60\n");
            MockPlotterBackend mock = new MockPlotterBackend(options, null) {
                @Override protected void awaitStep(long millis) { }
            };
            mock.connect();
            GcodeFileReplay.replay(GcodeFileReplay.preflight(file.toFile(), options), mock, line -> { });
            assertArrayEquals(new double[] {10, 20}, mock.queryPosition());
            assertFalse(mock.isPenDown());
        } finally { java.nio.file.Files.delete(file); }
    }
}
