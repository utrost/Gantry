package org.trostheide.gantry.plotter;

import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Same behavioral assertions for the GUI simulator and the production serial implementation. */
class BackendContractTest {
    private record Rig(PlotterBackend backend, BooleanSupplier penDown, Runnable refresh) implements AutoCloseable {
        @Override public void close() { backend.disconnect(); }
        void at(double x, double y) throws Exception {
            long end = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            do {
                refresh.run();
                double[] point = backend.queryPosition();
                if (point != null && Math.abs(point[0] - x) < .001 && Math.abs(point[1] - y) < .001) return;
                Thread.sleep(5); // The production backend reader receives asynchronous position reports.
            } while (System.nanoTime() < end);
            assertArrayEquals(new double[] {x, y}, backend.queryPosition(), .001);
        }
    }
    private Rig rig(boolean serial) {
        GcodeOptions o = new GcodeOptions(); o.bootDelayMillis = 0; o.penDownDelayMillis = 0;
        o.positionPollIntervalSeconds = .01; o.feedRateTravel = 6000; o.feedRateDraw = 6000;
        if (serial) {
            FakeGrblTransport transport = new FakeGrblTransport();
            GcodeBackend backend = new GcodeBackend(o, (port, baud) -> transport);
            assertTrue(backend.connect());
            return new Rig(backend, transport::isPenDown, () -> { });
        }
        MockPlotterBackend mock = new MockPlotterBackend(o, null) {
            @Override protected void awaitStep(long millis) { }
        };
        assertTrue(mock.connect());
        return new Rig(mock, mock::isPenDown, () -> { });
    }
    @Test void productionBackendCanReconnectUsingAFreshTransport() {
        GcodeOptions o = new GcodeOptions(); o.bootDelayMillis = 0; o.penDownDelayMillis = 0;
        java.util.concurrent.atomic.AtomicReference<FakeGrblTransport> current = new java.util.concurrent.atomic.AtomicReference<>();
        GcodeBackend backend = new GcodeBackend(o, (port, baud) -> {
            FakeGrblTransport fresh = new FakeGrblTransport(); current.set(fresh); return fresh;
        });
        try {
            assertTrue(backend.connect()); backend.moveto(1, 2); backend.disconnect();
            assertThrows(GcodeBackendException.class, () -> backend.lineto(2, 3));
            assertTrue(backend.connect()); backend.lineto(3, 4);
            assertTrue(current.get().isPenDown(), "first draw on reconnect must actually send pen-down");
        } finally { backend.disconnect(); }
    }

    @TestFactory Stream<DynamicTest> sharedMotionAndPenContract() {
        return Stream.of(false, true).map(serial -> DynamicTest.dynamicTest(
                serial ? "production GRBL with fake serial" : "GUI mock", () -> {
            try (Rig r = rig(serial)) {
                r.backend.moveto(10, 20); r.at(10, 20); assertFalse(r.penDown.getAsBoolean());
                r.backend.lineto(12, 24); r.at(12, 24); assertTrue(r.penDown.getAsBoolean());
                r.backend.move(3, -2); r.at(15, 22); assertTrue(r.penDown.getAsBoolean());
                r.backend.moveto(1, 2); r.at(1, 2); assertFalse(r.penDown.getAsBoolean());
                assertEquals(List.of("ok"), r.backend.sendRaw("G1 X4 Y5 F6000"));
                r.at(4, 5); // move() restored G90; this raw command is absolute, not relative.
                r.backend.pendown(); assertTrue(r.penDown.getAsBoolean());
                r.backend.haltMotion(); r.backend.penup(); assertFalse(r.penDown.getAsBoolean());
                r.backend.moveto(2, 3); r.at(2, 3);
                r.backend.disconnect(); assertNull(r.backend.queryPosition());
                assertThrows(RuntimeException.class, () -> r.backend.moveto(4, 5));
                assertNotEquals(List.of("ok"), r.backend.sendRaw("G1 X4 Y5 F6000"));
            }
            // Both implementations can establish a separate fresh connection after a completed session.
            try (Rig next = rig(serial)) { next.backend.moveto(3, 4); next.at(3, 4); }
        }));
    }
}
