package org.trostheide.gantry.app.plot;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.plotter.PlotterBackend;
import org.trostheide.gantry.model.ProcessorOutput;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotJobControllerTest {

    @Test
    void successfulConnectionIsOwnedUntilDisconnect() {
        RecordingBackend backend = new RecordingBackend(true);
        PlotJobController controller = new PlotJobController();

        assertTrue(controller.connect(backend));
        assertTrue(controller.isConnected());
        assertSame(backend, controller.backend());

        controller.disconnect();
        assertFalse(controller.isConnected());
        assertTrue(backend.disconnected);
    }

    @Test
    void failedConnectionIsNotAdopted() {
        PlotJobController controller = new PlotJobController();

        assertFalse(controller.connect(new RecordingBackend(false)));
        assertFalse(controller.isConnected());
    }

    @Test
    void secondConnectionIsRejectedWithoutTouchingCandidate() {
        PlotJobController controller = new PlotJobController();
        RecordingBackend first = new RecordingBackend(true);
        RecordingBackend second = new RecordingBackend(true);
        controller.connect(first);

        assertThrows(IllegalStateException.class, () -> controller.connect(second));
        assertFalse(second.connectCalled);
        assertSame(first, controller.backend());
    }

    @Test
    void backendActionUsesConnectedSnapshotAndReportsDisconnectedState() {
        PlotJobController controller = new PlotJobController();
        AtomicBoolean invoked = new AtomicBoolean();

        assertFalse(controller.withBackend(backend -> invoked.set(true)));
        controller.connect(new RecordingBackend(true));
        assertTrue(controller.withBackend(backend -> invoked.set(true)));
        assertTrue(invoked.get());
    }

    @Test
    void plotLifecycleOwnsActiveServicePauseCancelAndReplotState() {
        PlotJobController controller = new PlotJobController();
        PlotService service = new PlotService(new RecordingBackend(true), new PlotSettings());

        controller.beginPlot(service);
        assertTrue(controller.isPlotting());
        assertFalse(controller.canReplot());
        assertTrue(controller.togglePause());
        assertFalse(controller.togglePause());
        controller.cancelPlot();

        controller.finishPlot(true);
        assertFalse(controller.isPlotting());
        assertTrue(controller.canReplot());
        controller.resetReplot();
        assertFalse(controller.canReplot());
    }

    @Test
    void duplicatePlotStartIsRejectedWithoutReplacingTheActiveService() {
        PlotJobController controller = new PlotJobController();
        AtomicBoolean firstCancelled = new AtomicBoolean();
        AtomicBoolean secondCancelled = new AtomicBoolean();
        PlotService first = new PlotService(new RecordingBackend(true), new PlotSettings()) {
            @Override public void cancel() { firstCancelled.set(true); }
        };
        PlotService second = new PlotService(new RecordingBackend(true), new PlotSettings()) {
            @Override public void cancel() { secondCancelled.set(true); }
        };

        controller.beginPlot(first);

        assertThrows(IllegalStateException.class, () -> controller.beginPlot(second));
        assertTrue(controller.isPlotting());
        controller.cancelPlot();
        assertTrue(firstCancelled.get());
        assertFalse(secondCancelled.get());
    }

    @Test
    void exclusiveOperationAndPlotCannotOverlap() {
        PlotJobController controller = new PlotJobController();
        PlotService service = serviceThatDoesNotFail();

        assertTrue(controller.tryBeginExclusiveOperation("replay"));
        assertTrue(controller.isMachineBusy());
        assertFalse(controller.tryBeginExclusiveOperation("another operation"));
        assertThrows(IllegalStateException.class, () -> controller.beginPlot(service));

        controller.finishExclusiveOperation();
        controller.beginPlot(service);
        assertFalse(controller.tryBeginExclusiveOperation("replay"));
        controller.finishPlot(false);
        assertFalse(controller.isMachineBusy());
    }

    @Test
    void asynchronousPlotCleansUpAndReportsSuccess() throws InterruptedException {
        PlotJobController controller = new PlotJobController();
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        PlotService service = serviceThatDoesNotFail();

        controller.startPlot(service, emptyOutput(), (ok, error) -> {
            completed.set(ok);
            failure.set(error);
            finished.countDown();
        });

        assertTrue(finished.await(2, TimeUnit.SECONDS));
        assertTrue(completed.get());
        assertNull(failure.get());
        assertFalse(controller.isPlotting());
        assertTrue(controller.canReplot());
    }

    @Test
    void cancelledLayerGateDoesNotBecomeSuccessfulOrReplottable() throws Exception {
        PlotJobController controller = new PlotJobController();
        RecordingBackend backend = new RecordingBackend(true);
        PlotService service = new PlotService(backend, new PlotSettings());
        CountDownLatch waiting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        service.setLayerGate(layer -> { waiting.countDown(); release.await(); });
        AtomicBoolean completed = new AtomicBoolean(true);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ProcessorOutput output = new ProcessorOutput(emptyOutput().metadata(), java.util.List.of(
                new org.trostheide.gantry.model.Layer("test", "", java.util.List.of())));
        Thread worker = controller.startPlot(service, output, (ok, error) -> {
            completed.set(ok); failure.set(error);
        });
        assertTrue(waiting.await(2, TimeUnit.SECONDS));
        controller.cancelPlot();
        release.countDown();
        worker.join(2000);
        assertFalse(worker.isAlive());
        assertFalse(completed.get());
        assertNull(failure.get());
        assertFalse(controller.canReplot());
        assertTrue(service.isCancelled());
        assertTrue(backend.penRaised);
    }

    @Test
    void cancellationBeforeWorkerStartsIsNotLost() throws Exception {
        PlotJobController controller = new PlotJobController();
        PlotService service = new PlotService(new RecordingBackend(true), new PlotSettings());
        service.cancel();
        AtomicBoolean completed = new AtomicBoolean(true);
        Thread worker = controller.startPlot(service, emptyOutput(), (ok, error) -> completed.set(ok));
        worker.join(2000);
        assertFalse(worker.isAlive());
        assertTrue(service.isCancelled());
        assertFalse(completed.get());
        assertFalse(controller.canReplot());
    }

    @Test
    void asynchronousPlotCleansUpAndReportsFailure() throws InterruptedException {
        PlotJobController controller = new PlotJobController();
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        PlotService service = new PlotService(new RecordingBackend(true), new PlotSettings()) {
            @Override public void plot(ProcessorOutput output) { throw new IllegalStateException("boom"); }
        };

        controller.startPlot(service, emptyOutput(), (ok, error) -> {
            assertFalse(ok);
            failure.set(error);
            finished.countDown();
        });

        assertTrue(finished.await(2, TimeUnit.SECONDS));
        assertTrue(failure.get() instanceof IllegalStateException);
        assertFalse(controller.isPlotting());
        assertFalse(controller.canReplot());
    }

    @Test
    void cancelAndDisconnectWaitsForPlotCleanupBeforeClosingBackend() throws InterruptedException {
        PlotJobController controller = new PlotJobController();
        RecordingBackend backend = new RecordingBackend(true);
        controller.connect(backend);
        CountDownLatch plotting = new CountDownLatch(1);
        CountDownLatch releaseCleanup = new CountDownLatch(1);
        PlotService service = new PlotService(backend, new PlotSettings()) {
            @Override public void plot(ProcessorOutput output) {
                plotting.countDown();
                try {
                    releaseCleanup.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        controller.startPlot(service, emptyOutput(), (ok, error) -> { });
        assertTrue(plotting.await(1, TimeUnit.SECONDS));

        Thread release = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            assertFalse(backend.disconnected);
            releaseCleanup.countDown();
        });
        release.start();

        assertTrue(controller.cancelAndDisconnect(1000));
        release.join();
        assertTrue(backend.disconnected);
        assertFalse(controller.isConnected());
    }

    @Test
    void timedOutPlotRemainsConnected() throws InterruptedException {
        PlotJobController controller = new PlotJobController();
        RecordingBackend backend = new RecordingBackend(true);
        controller.connect(backend);
        CountDownLatch plotting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PlotService service = new PlotService(backend, new PlotSettings()) {
            @Override public void plot(ProcessorOutput output) {
                plotting.countDown();
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        Thread worker = controller.startPlot(service, emptyOutput(), (ok, error) -> { });
        assertTrue(plotting.await(1, TimeUnit.SECONDS));

        assertFalse(controller.cancelAndDisconnect(10));
        assertTrue(controller.isConnected());
        assertFalse(backend.disconnected);

        release.countDown();
        worker.join(1000);
        controller.disconnect();
    }

    private static PlotService serviceThatDoesNotFail() {
        return new PlotService(new RecordingBackend(true), new PlotSettings()) {
            @Override public void plot(ProcessorOutput output) { }
        };
    }

    private static ProcessorOutput emptyOutput() {
        return new ProcessorOutput(null, List.of());
    }

    private static final class RecordingBackend implements PlotterBackend {
        private final boolean connectResult;
        private boolean connectCalled;
        private boolean disconnected;
        private boolean penRaised;

        private RecordingBackend(boolean connectResult) {
            this.connectResult = connectResult;
        }

        @Override public boolean connect() {
            connectCalled = true;
            return connectResult;
        }

        @Override public void disconnect() {
            disconnected = true;
        }

        @Override public void moveto(double x, double y) { }
        @Override public void lineto(double x, double y) { }
        @Override public void move(double dx, double dy) { }
        @Override public void penup() { penRaised = true; }
        @Override public void pendown() { }
    }
}
