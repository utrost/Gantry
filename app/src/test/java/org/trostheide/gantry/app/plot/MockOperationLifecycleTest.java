package org.trostheide.gantry.app.plot;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.plotter.*;
import org.trostheide.gantry.model.*;
import org.trostheide.gantry.model.command.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class MockOperationLifecycleTest {
    @Test void actualMockStopsThroughBothManualAndPlotOwnersThenStartsANewOperation() throws Exception {
        for (boolean manual : List.of(true, false)) {
            CountDownLatch moving = new CountDownLatch(1), release = new CountDownLatch(1);
            GcodeOptions options = new GcodeOptions(); options.feedRateDraw = 6000;
            options.feedRateTravel = 6000; options.penDownDelayMillis = 0;
            MockPlotterBackend mock = new MockPlotterBackend(options, null) {
                int ticks;
                @Override protected void awaitStep(long millis) throws InterruptedException {
                    if (++ticks > 1) { moving.countDown(); release.await(); }
                }
            };
            PlotJobController controller = new PlotJobController(); controller.connect(mock);
            AtomicBoolean completed = new AtomicBoolean(true);
            PlotJobController.CompletionListener done = (ok, error) -> completed.set(ok);
            ProcessorOutput job = new ProcessorOutput(new Metadata("test", Instant.EPOCH, "s", "mm", 2,
                    new Bounds(0, 0, 200, 0)), List.of(new Layer("pen", "s", List.of(
                    new DrawCommand(1, List.of(new Point(100, 0))),
                    new DrawCommand(2, List.of(new Point(200, 0)))))));
            Thread worker = manual
                    ? controller.startOperation("Frame", b -> { b.lineto(100, 0); b.lineto(200, 0); }, done)
                    : controller.startPlot(new PlotService(mock, new PlotSettings()), job, done);
            try {
                assertTrue(moving.await(2, TimeUnit.SECONDS));
                assertTrue(mock.isPenDown());
                controller.cancelPlot(); controller.cancelPlot(); worker.join(2000);
                assertFalse(worker.isAlive()); assertFalse(completed.get());
                assertFalse(controller.isMachineBusy()); assertFalse(mock.isPenDown());
                assertArrayEquals(new double[] {5, 0}, mock.queryPosition());
                release.countDown();
                Thread next = controller.startOperation("Later move", b -> b.moveto(6, 0), done);
                next.join(2000); assertFalse(next.isAlive()); assertTrue(completed.get());
                assertArrayEquals(new double[] {6, 0}, mock.queryPosition());
            } finally { worker.interrupt(); release.countDown(); worker.join(2000); controller.disconnect(); }
        }
    }
}
