package org.trostheide.gantry.app.plot;
import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.*;
import org.trostheide.gantry.model.command.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PlotFailureRecoveryTest {
    private ProcessorOutput drawing() {
        return new ProcessorOutput(new Metadata("fixture", Instant.EPOCH, "default", "mm", 2, new Bounds(0,10,0,10)),
                List.of(new Layer("ink", "default", List.of(new MoveCommand(1,1,1),new DrawCommand(2,List.of(new Point(1,1),new Point(2,2)))))));
    }
    @Test void refillFailureAndCancellationStopBeforeFurtherDips() {
        for (boolean cancel : List.of(false, true)) {
            List<String> events = new ArrayList<>();
            java.util.concurrent.atomic.AtomicReference<PlotService> ref = new java.util.concurrent.atomic.AtomicReference<>();
            RuntimeException original = new IllegalStateException("refill failure");
            var backend = new FakePlotterBackend() {
                public void pendown() { events.add("down"); }
                public void dwell(long millis) { if (cancel) ref.get().cancel(); else throw original; }
                public void haltMotion() { events.add("halt"); }
                public void penup() { events.add("up"); }
            };
            PlotSettings settings = new PlotSettings();
            settings.stations.put("paint", new StationConfig(10,10,0,"dip_swirl"));
            var service = new PlotService(backend, settings); ref.set(service);
            var output = new ProcessorOutput(drawing().metadata(), List.of(new Layer("paint", "paint", List.of(new RefillCommand(1,"paint")))));
            var failure = assertThrows(RuntimeException.class, () -> service.plot(output));
            if (!cancel) assertSame(original, failure);
            assertEquals(1, events.stream().filter("down"::equals).count());
            assertEquals(List.of("halt", "up"), events.subList(events.size()-2, events.size()));
        }
    }

    @Test void moveDrawAndCallbackFailuresHaltThenLiftWithoutMaskingOriginal() {
        for (String stage : List.of("move", "draw", "callback")) {
            RuntimeException original = new IllegalStateException(stage);
            List<String> cleanup = new ArrayList<>();
            var backend = new FakePlotterBackend() {
                public void moveto(double x,double y) { if(stage.equals("move")) throw original; }
                public void lineto(double x,double y) { if(stage.equals("draw")) throw original; }
                public void haltMotion(){cleanup.add("halt");throw new IllegalStateException("halt failed");}
                public void penup(){cleanup.add("up");throw new IllegalStateException("lift failed");}
            };
            var service = new PlotService(backend,new PlotSettings());
            if(stage.equals("callback")) service.setLayerStartedCallback(l -> {throw original;});
            assertSame(original, assertThrows(RuntimeException.class, () -> service.plot(drawing())));
            assertEquals(List.of("halt","up"), cleanup);
            assertEquals(2,original.getSuppressed().length);
        }
    }
    @Test void interruptedLayerWaitRecoversAndPreservesInterrupt() {
        var backend = new FakePlotterBackend();
        var service = new PlotService(backend,new PlotSettings());
        service.setLayerGate(l -> {throw new InterruptedException();});
        try {service.plot(drawing()); assertTrue(Thread.currentThread().isInterrupted()); assertEquals(List.of("PENUP"),backend.calls);}
        finally {Thread.interrupted();}
    }
}
