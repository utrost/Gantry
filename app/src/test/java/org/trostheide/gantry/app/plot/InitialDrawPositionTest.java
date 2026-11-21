package org.trostheide.gantry.app.plot;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.*;
import org.trostheide.gantry.model.command.*;
import org.trostheide.gantry.plotter.PlotterBackend;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InitialDrawPositionTest {
    private static ProcessorOutput output(Command... commands) {
        return new ProcessorOutput(new Metadata("initial draw", Instant.EPOCH, "s", "mm", commands.length, Bounds.empty()),
                List.of(new Layer("one", "s", List.of(commands))));
    }
    @Test void knownNonOriginInitialDrawIsRejectedWithoutAutomaticPositioning() {
        var backend = new PositionBackend(5, 5);
        var service = new PlotService(backend, new PlotSettings());
        var failure = assertThrows(IllegalStateException.class, () -> service.plot(output(
                new DrawCommand(1, List.of(new Point(12, 8))))));
        assertTrue(failure.getMessage().contains("initial MOVE"));
        assertFalse(backend.drew); assertEquals(0, backend.moves);
        assertTrue(backend.halted); assertTrue(backend.lifted);
    }
    @Test void explicitMoveEstablishesTheStartFromAnyReportedPosition() {
        var backend = new PositionBackend(5, 5);
        new PlotService(backend, new PlotSettings()).plot(output(new MoveCommand(1, 2, 3),
                new DrawCommand(2, List.of(new Point(12, 8)))));
        assertTrue(backend.drew); assertFalse(backend.halted);
    }
    @Test void originInitialDrawRemainsSupported() {
        var backend = new PositionBackend(0, 0);
        new PlotService(backend, new PlotSettings()).plot(output(new DrawCommand(1, List.of(new Point(12, 8)))));
        assertTrue(backend.drew);
    }
    @Test void knownRefillEstablishesTheStartWithoutRequiringOrigin() {
        var backend = new PositionBackend(5, 5);
        var settings = new PlotSettings(); settings.stations.put("s", new StationConfig(30, 20, 0, "simple_dip"));
        new PlotService(backend, settings).plot(output(new RefillCommand(1, "s"),
                new DrawCommand(2, List.of(new Point(12, 8)))));
        assertTrue(backend.drew); assertFalse(backend.halted);
    }
    private static class PositionBackend implements PlotterBackend {
        public MotionCompletion awaitMotionComplete() { return MotionCompletion.SIMULATED_IDLE; }
        double x, y; int moves; boolean drew, halted, lifted;
        PositionBackend(double x, double y) { this.x = x; this.y = y; }
        public boolean connect() { return true; }
        public void disconnect() { }
        public void moveto(double x, double y) { this.x=x; this.y=y; moves++; }
        public void lineto(double x, double y) { this.x=x; this.y=y; drew=true; }
        public void move(double dx, double dy) { x+=dx; y+=dy; }
        public void dwell(long millis) { }
        public void penup() { lifted=true; }
        public void pendown() { }
        public void haltMotion() { halted=true; }
        public double[] queryPosition() { return new double[]{x,y}; }
    }
}
