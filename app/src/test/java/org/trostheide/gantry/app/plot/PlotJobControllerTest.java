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
        @Override public void penup() { }
        @Override public void pendown() { }
    }
}
