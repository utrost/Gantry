package org.trostheide.gantry.app.plot;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.*;
import org.trostheide.gantry.model.command.MoveCommand;
import org.trostheide.gantry.plotter.PlotterBackend.MotionCompletion;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class MotionHandoffTest {
    @Test void nextPenGateAndFinalCompletionWaitForParking() throws Exception {
        var waiting = new LinkedBlockingQueue<CountDownLatch>();
        var backend = new FakePlotterBackend() {
            public MotionCompletion awaitMotionComplete() {
                var release = new CountDownLatch(1); waiting.add(release);
                try { release.await(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new CancellationException(); }
                return MotionCompletion.SIMULATED_IDLE;
            }
        };
        PlotService service = new PlotService(backend, new PlotSettings());
        AtomicInteger gates = new AtomicInteger(); service.setLayerGate(layer -> gates.incrementAndGet());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> { try { service.plot(job()); } catch(Throwable e) { failure.set(e); } });
        try {
            worker.start();
            CountDownLatch first = waiting.poll(2, TimeUnit.SECONDS);
            assertNotNull(first, "First park must wait for completion");
            assertEquals(1, gates.get(), "Next pen must not be offered while parking");
            assertTrue(worker.isAlive()); first.countDown();
            CountDownLatch last = waiting.poll(2, TimeUnit.SECONDS);
            assertNotNull(last); assertEquals(2, gates.get());
            assertTrue(worker.isAlive(), "Final completion must wait for final parking");
            last.countDown(); worker.join(2000);
            assertFalse(worker.isAlive()); assertNull(failure.get());
        } finally { worker.interrupt(); worker.join(2000); }
    }

    @Test void cancelledParkingCannotOfferNextPenOrContinueMotion() throws Exception {
        CountDownLatch waiting = new CountDownLatch(1);
        var backend = new FakePlotterBackend() {
            public MotionCompletion awaitMotionComplete() {
                waiting.countDown();
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new CancellationException(); }
                throw new AssertionError();
            }
        };
        var service = new PlotService(backend, new PlotSettings());
        AtomicInteger gates = new AtomicInteger(); service.setLayerGate(layer -> gates.incrementAndGet());
        Thread worker = new Thread(() -> { try { service.plot(job()); } catch(CancellationException expected) { } });
        try {
            worker.start(); assertTrue(waiting.await(2, TimeUnit.SECONDS));
            service.cancel(); worker.interrupt(); worker.join(2000);
            assertFalse(worker.isAlive()); assertEquals(1, gates.get());
            assertFalse(backend.calls.contains("MOVETO 20.000 20.000"));
        } finally { worker.interrupt(); worker.join(2000); }
    }

    private ProcessorOutput job() {
        return new ProcessorOutput(new Metadata("barrier", Instant.EPOCH, "s", "mm", 2, new Bounds(0,0,20,20)),
                List.of(new Layer("one","s",List.of(new MoveCommand(1,10,10))),
                        new Layer("two","s",List.of(new MoveCommand(2,20,20)))));
    }
}
