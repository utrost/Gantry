package org.trostheide.gantry.app.plot;

import org.trostheide.gantry.plotter.PlotterBackend;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Records every call made on it, for asserting the exact sequence/coordinates a test produced. */
class FakePlotterBackend implements PlotterBackend {

    final List<String> calls = new ArrayList<>();

    @Override
    public boolean connect() {
        return true;
    }

    @Override
    public void disconnect() {
        calls.add("DISCONNECT");
    }

    @Override
    public void moveto(double x, double y) {
        calls.add(String.format(Locale.ROOT, "MOVETO %.3f %.3f", x, y));
    }

    @Override
    public void lineto(double x, double y) {
        calls.add(String.format(Locale.ROOT, "LINETO %.3f %.3f", x, y));
    }

    @Override
    public void move(double dx, double dy) {
        calls.add(String.format(Locale.ROOT, "MOVE %.3f %.3f", dx, dy));
    }

    @Override
    public void penup() {
        calls.add("PENUP");
    }

    @Override
    public void pendown() {
        calls.add("PENDOWN");
    }
}
