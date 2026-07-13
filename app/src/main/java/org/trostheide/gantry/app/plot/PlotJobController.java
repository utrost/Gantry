package org.trostheide.gantry.app.plot;

import org.trostheide.gantry.plotter.PlotterBackend;
import org.trostheide.gantry.model.ProcessorOutput;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Owns the backend used by the current application session.
 *
 * <p>This first extraction deliberately covers connection ownership only. Plot execution remains
 * in the existing caller until its cancellation and pen-safety behavior has dedicated controller
 * tests.</p>
 */
public final class PlotJobController {

    @FunctionalInterface
    public interface CompletionListener {
        /** Called on the plot worker thread after lifecycle state has been cleaned up. */
        void onComplete(boolean completed, Throwable failure);
    }

    private PlotterBackend backend;
    private PlotService activeService;
    private boolean paused;
    private boolean canReplot;

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
        activeService = service;
        paused = false;
        canReplot = false;
    }

    /** Starts the existing service on its dedicated worker and guarantees lifecycle cleanup. */
    public Thread startPlot(PlotService service, ProcessorOutput output, CompletionListener listener) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(listener, "listener");
        beginPlot(service);
        Thread worker = new Thread(() -> {
            boolean completed = false;
            Throwable failure = null;
            try {
                service.plot(output);
                completed = true;
            } catch (Throwable thrown) {
                failure = thrown;
            } finally {
                finishPlot(completed);
                listener.onComplete(completed, failure);
            }
        }, "plot-thread");
        worker.start();
        return worker;
    }

    public synchronized void finishPlot(boolean successful) {
        activeService = null;
        paused = false;
        canReplot = successful;
    }

    public synchronized boolean isPlotting() { return activeService != null; }
    public synchronized boolean isPaused() { return paused; }
    public synchronized boolean canReplot() { return canReplot; }
    public synchronized void resetReplot() { canReplot = false; }

    public void cancelPlot() {
        PlotService service;
        synchronized (this) { service = activeService; }
        if (service != null) service.cancel();
    }

    /** Toggles pause and returns the new paused state; false when no plot is active. */
    public synchronized boolean togglePause() {
        if (activeService == null) return false;
        if (paused) activeService.resume(); else activeService.pause();
        paused = !paused;
        return paused;
    }
}
