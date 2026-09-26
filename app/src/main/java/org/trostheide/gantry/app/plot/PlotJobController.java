package org.trostheide.gantry.app.plot;

import org.trostheide.gantry.plotter.PlotterBackend;
import org.trostheide.gantry.model.ProcessorOutput;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Swing-free owner of the application session's backend and plot lifecycle.
 *
 * <p>Connection adoption, plot worker execution, pause/resume/cancel, lifecycle cleanup, and
 * re-plot eligibility live here. Presentation and EDT dispatch remain the caller's concern.</p>
 */
public final class PlotJobController {

    @FunctionalInterface
    public interface CompletionListener {
        /** Called on the plot worker thread after lifecycle state has been cleaned up. */
        void onComplete(boolean completed, Throwable failure);
    }

    private PlotterBackend backend;
    private PlotService activeService;
    private Thread activeWorker;
    private boolean paused;
    private boolean canReplot;
    private String exclusiveOperation;

    /** Connects and adopts {@code candidate} only when its connection succeeds. */
    public boolean connect(PlotterBackend candidate) {
        Objects.requireNonNull(candidate, "candidate");
        synchronized (this) {
            if (backend != null) {
                throw new IllegalStateException("A plotter backend is already connected");
            }
        }
        boolean connected = candidate.connect();
        if (!connected) {
            return false;
        }
        synchronized (this) {
            if (backend != null) {
                candidate.disconnect();
                throw new IllegalStateException("A plotter backend connected concurrently");
            }
            backend = candidate;
        }
        return true;
    }

    /** Clears ownership before performing the potentially blocking disconnect. */
    public void disconnect() {
        PlotterBackend previous;
        synchronized (this) {
            previous = backend;
            backend = null;
        }
        if (previous != null) {
            previous.disconnect();
        }
    }

    public synchronized boolean isConnected() {
        return backend != null;
    }

    /** Returns the connected backend for the existing plot pipeline during staged migration. */
    public synchronized PlotterBackend backend() {
        return backend;
    }

    /** Runs an action against a stable backend snapshot; returns false when disconnected. */
    public boolean withBackend(Consumer<PlotterBackend> action) {
        Objects.requireNonNull(action, "action");
        PlotterBackend current = backend();
        if (current == null) {
            return false;
        }
        action.accept(current);
        return true;
    }

    public synchronized void beginPlot(PlotService service) {
        Objects.requireNonNull(service, "service");
        if (activeService != null) {
            throw new IllegalStateException("A plot is already active");
        }
        if (exclusiveOperation != null) {
            throw new IllegalStateException("Machine is busy with " + exclusiveOperation);
        }
        activeService = service;
        paused = false;
        canReplot = false;
    }

    /** Starts the existing service on its dedicated worker and guarantees lifecycle cleanup. */
    public Thread startPlot(PlotService service, ProcessorOutput output, CompletionListener listener) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(listener, "listener");
        Thread worker = new Thread(() -> {
            boolean completed = false;
            Throwable failure = null;
            try {
                service.plot(output);
                completed = !service.isCancelled() && !Thread.currentThread().isInterrupted();
            } catch (Throwable thrown) {
                failure = thrown;
            } finally {
                finishPlot(completed);
                listener.onComplete(completed, failure);
            }
        }, "plot-thread");
        synchronized (this) {
            beginPlot(service);
            activeWorker = worker;
        }
        worker.start();
        return worker;
    }

    public synchronized void finishPlot(boolean successful) {
        activeService = null;
        activeWorker = null;
        paused = false;
        canReplot = successful;
    }

    public synchronized boolean isPlotting() { return activeService != null; }
    public synchronized boolean isPaused() { return paused; }
    public synchronized boolean canReplot() { return canReplot; }
    public synchronized void resetReplot() { canReplot = false; }

    /** Reserves the connected machine for an operation that runs outside PlotService. */
    public synchronized boolean tryBeginExclusiveOperation(String description) {
        if (activeService != null || exclusiveOperation != null) return false;
        exclusiveOperation = Objects.requireNonNull(description, "description");
        return true;
    }

    public synchronized void finishExclusiveOperation() {
        exclusiveOperation = null;
    }

    public synchronized boolean isMachineBusy() {
        return activeService != null || exclusiveOperation != null;
    }

    public void cancelPlot() {
        PlotService service;
        synchronized (this) { service = activeService; }
        if (service != null) service.cancel();
    }

    /**
     * Cancels an active plot and waits for its safety cleanup before disconnecting. This method
     * may block and must not be called on the Swing event thread.
     *
     * @return {@code false} when the plot did not stop within the timeout; in that case the
     *         backend deliberately remains connected so cleanup can still finish safely
     */
    public boolean cancelAndDisconnect(long timeoutMillis) {
        PlotService service;
        Thread worker;
        PlotterBackend current;
        synchronized (this) {
            service = activeService;
            worker = activeWorker;
            current = backend;
        }
        if (service != null) service.cancel();
        if (current != null && service != null) current.haltMotion();
        if (worker != null && worker != Thread.currentThread()) {
            try {
                worker.join(Math.max(0, timeoutMillis));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (worker.isAlive()) return false;
        }
        disconnect();
        return true;
    }

    /** Toggles pause and returns the new paused state; false when no plot is active. */
    public synchronized boolean togglePause() {
        if (activeService == null) return false;
        if (paused) activeService.resume(); else activeService.pause();
        paused = !paused;
        return paused;
    }
}
