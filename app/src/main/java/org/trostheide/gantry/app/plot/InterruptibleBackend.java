package org.trostheide.gantry.app.plot;

import org.trostheide.gantry.plotter.PlotterBackend;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Prevents a cancelled manual sequence from issuing another command after a caught interruption. */
final class InterruptibleBackend implements PlotterBackend {
    private final PlotterBackend backend;
    private final BooleanSupplier cancelled;
    InterruptibleBackend(PlotterBackend backend, BooleanSupplier cancelled) {
        this.backend = backend; this.cancelled = cancelled;
    }
    private void check() {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())
            throw new CancellationException("Machine operation stopped");
    }
    public boolean connect(){check();return backend.connect();}
    public void disconnect(){check();backend.disconnect();}
    public void moveto(double x,double y){check();backend.moveto(x,y);}
    public void lineto(double x,double y){check();backend.lineto(x,y);}
    public void move(double x,double y){check();backend.move(x,y);}
    public void penup(){check();backend.penup();}
    public void pendown(){check();backend.pendown();}
    public void pendown(double z){check();backend.pendown(z);}
    public void dwell(long millis){check();backend.dwell(millis);check();}
    public double[] queryPosition(){check();return backend.queryPosition();}
    public List<String> sendRaw(String command){check();return backend.sendRaw(command);}
    public void adjustSpeed(String direction){check();backend.adjustSpeed(direction);}
    public void home(){check();backend.home();}
    public void haltMotion(){check();backend.haltMotion();}
}
