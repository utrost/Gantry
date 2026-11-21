package org.trostheide.gantry.app.plot;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.*;
import org.trostheide.gantry.model.command.MoveCommand;
import org.trostheide.gantry.plotter.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class SerialCompletionControllerTest {
    @Test void stopDuringSerialCompletionDoesNotReportAPlotFailureOrOfferTheNextPen() throws Exception {
        var options=new GcodeOptions();options.bootDelayMillis=0;options.penDownDelayMillis=0;
        options.positionPollIntervalSeconds=.01;options.responseTimeoutSeconds=2;
        var transport=new SerialSimulatorTransport(options);
        var backend=new GcodeBackend(options,(port,baud)->transport);var controller=new PlotJobController();
        assertTrue(controller.connect(backend));transport.pauseExecution();
        var service=new PlotService(backend,new PlotSettings());var gates=new AtomicInteger();service.setLayerGate(layer->gates.incrementAndGet());
        var output=new ProcessorOutput(new Metadata("test",Instant.EPOCH,"s","mm",2,new Bounds(0,0,20,20)),
                List.of(new Layer("one","s",List.of(new MoveCommand(1,10,10))),new Layer("two","s",List.of(new MoveCommand(2,20,20)))));
        AtomicReference<Throwable> failure=new AtomicReference<>();AtomicBoolean completed=new AtomicBoolean(true);
        CountDownLatch done=new CountDownLatch(1);
        try {
            controller.startPlot(service,output,(success,error)->{completed.set(success);failure.set(error);done.countDown();});
            assertTrue(transport.awaitCommand("G4 P0",2,TimeUnit.SECONDS));
            assertTrue(controller.isMachineBusy());assertEquals(1,gates.get());
            var pendingCommands = transport.commands();
            assertFalse(controller.togglePause(), "Pause cannot issue a concurrent pen command during the barrier");
            assertEquals(pendingCommands, transport.commands());
            controller.cancelPlot();assertTrue(done.await(4,TimeUnit.SECONDS));
            assertFalse(completed.get());assertNull(failure.get());assertEquals(1,gates.get());
            assertFalse(controller.canReplot());assertFalse(transport.commands().contains("G1 X20.000 Y20.000 F3000"));
        } finally {controller.cancelAndDisconnect(5000);transport.close();}
    }

    @Test void manualActionOwnsItsWorkerUntilTheSerialPlannerFinishes() throws Exception {
        var options=new GcodeOptions();options.bootDelayMillis=0;options.responseTimeoutSeconds=2;
        var transport=new SerialSimulatorTransport(options);var backend=new GcodeBackend(options,(p,b)->transport);
        var controller=new PlotJobController();assertTrue(controller.connect(backend));transport.pauseExecution();
        AtomicBoolean complete=new AtomicBoolean();AtomicReference<Throwable> failure=new AtomicReference<>();var done=new CountDownLatch(1);
        try {
            controller.startOperation("Station visit",b->b.moveto(10,10),(ok,error)->{complete.set(ok);failure.set(error);done.countDown();});
            assertTrue(transport.awaitCommand("G4 P0",2,TimeUnit.SECONDS));assertTrue(controller.isMachineBusy());
            assertFalse(complete.get());transport.resumeExecution();assertTrue(done.await(2,TimeUnit.SECONDS));
            assertTrue(complete.get());assertNull(failure.get());assertFalse(controller.isMachineBusy());
        } finally {controller.cancelAndDisconnect(5000);transport.close();}
    }
    @Test void replayCannotReleaseOwnershipBeforeItsFinalSerialBarrier(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var options = new GcodeOptions(); options.bootDelayMillis=0; options.responseTimeoutSeconds=2;
        var transport = new SerialSimulatorTransport(options);
        var backend = new GcodeBackend(options,(p,b)->transport);
        var controller = new PlotJobController(); assertTrue(controller.connect(backend));
        var file=directory.resolve("replay.gcode");
        java.nio.file.Files.writeString(file,"G21\nG90\nG1 X10 Y10 F1000\n");
        var plan=GcodeFileReplay.preflight(file.toFile(),options);
        var done=new CountDownLatch(1); var complete=new AtomicBoolean(); var failure=new AtomicReference<Throwable>();
        transport.pauseExecution();
        try {
            controller.startReplay(plan,line->{},(ok,error)->{complete.set(ok);failure.set(error);done.countDown();});
            assertTrue(transport.awaitCommand("G4 P0",2,TimeUnit.SECONDS));
            assertTrue(controller.isMachineBusy()); assertFalse(complete.get());
            transport.resumeExecution(); assertTrue(done.await(2,TimeUnit.SECONDS));
            assertTrue(complete.get()); assertNull(failure.get());
            assertEquals(10,transport.snapshot().x(),.001); assertFalse(transport.snapshot().penDown());
        } finally {controller.cancelAndDisconnect(5000);transport.close();}
    }

}
