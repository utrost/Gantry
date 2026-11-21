package org.trostheide.gantry.plotter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class MotionCompletionTest {
    private final FakeGrblTransport fake = new FakeGrblTransport();
    private GcodeBackend connect() {
        var options = new GcodeOptions(); options.bootDelayMillis=0; options.penDownDelayMillis=0;
        options.responseTimeoutSeconds=1; options.positionPollIntervalSeconds=60;
        var backend = new GcodeBackend(options, (port,baud)->fake);
        assertTrue(backend.connect()); return backend;
    }
    @Test void cachedIdleAndAnAckDoNotCompleteMotionStillReportingRun() throws Exception {
        var backend=connect();
        var moving=new CountDownLatch(1);
        backend.setStateCallback(state->{if(state==GcodeBackend.MachineState.RUN)moving.countDown();});
        fake.setMachineState("Run");
        AtomicReference<Object> result=new AtomicReference<>();
        Thread worker=new Thread(()->{try{result.set(backend.awaitMotionComplete());}catch(Throwable e){result.set(e);}});
        try {
            worker.start(); assertTrue(moving.await(2,TimeUnit.SECONDS));
            assertTrue(fake.sentCommands().contains("G4 P0"));
            assertNull(result.get());
            fake.setMachineState("Idle"); worker.join(2000);
            assertFalse(worker.isAlive()); assertEquals(PlotterBackend.MotionCompletion.CONTROLLER_IDLE,result.get());
        } finally { worker.interrupt();worker.join(2000);backend.disconnect(); }
    }
    @Test void missingBarrierAckCannotBeReplacedByIdle() {
        var backend=connect();fake.scriptNext("G4 P0");
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(2),()-> {
                var failure=assertThrows(GcodeBackendException.class,backend::awaitMotionComplete);
                assertTrue(failure.getMessage().contains("acknowledgement"));
            });
        } finally { backend.disconnect(); }
    }
    @Test void rejectedBarrierCannotEnableAHandoff() {
        var backend=connect();fake.scriptNext("G4 P0","error:20");
        try {assertThrows(GcodeBackendException.class,backend::awaitMotionComplete);}
        finally {backend.disconnect();}
    }
    @Test void continuousRunAndHoldCannotExtendTheDeadline() {
        for(String state:new String[]{"Run","Hold:0"}) {
            var transport=new FakeGrblTransport();var options=new GcodeOptions();
            options.bootDelayMillis=0;options.responseTimeoutSeconds=1;options.positionPollIntervalSeconds=.01;
            var backend=new GcodeBackend(options,(p,b)->transport);assertTrue(backend.connect());
            transport.setMachineState(state);
            try { assertTimeoutPreemptively(Duration.ofSeconds(3),()->assertThrows(GcodeBackendException.class,backend::awaitMotionComplete)); }
            finally {transport.setMachineState("Idle");backend.disconnect();}
        }
    }
    @Test void alarmDisconnectStopAndInterruptionAbortTheWait() throws Exception {
        for(String fault:new String[]{"alarm","disconnect","stop","interrupt"}) {
            var transport=new FakeGrblTransport();var options=new GcodeOptions();options.bootDelayMillis=0;
            options.responseTimeoutSeconds=5;options.positionPollIntervalSeconds=60;
            var backend=new GcodeBackend(options,(p,b)->transport);assertTrue(backend.connect());
            var moving=new CountDownLatch(1);backend.setStateCallback(s->{if(s==GcodeBackend.MachineState.RUN)moving.countDown();});
            transport.setMachineState("Run");AtomicReference<Throwable> failure=new AtomicReference<>();
            Thread worker=new Thread(()->{try{backend.awaitMotionComplete();}catch(Throwable e){failure.set(e);}});
            try {
                worker.start();assertTrue(moving.await(2,TimeUnit.SECONDS));
                switch(fault) {
                    case "alarm"->transport.injectLine("ALARM:9");
                    case "disconnect"->transport.failReads(new java.io.IOException("adapter disappeared"));
                    case "stop"->backend.haltMotion();
                    case "interrupt"->worker.interrupt();
                }
                worker.join(2000);assertFalse(worker.isAlive(),fault);
                assertInstanceOf(GcodeBackendException.class,failure.get(),fault);
                if(fault.equals("interrupt"))assertTrue(worker.isInterrupted());
            } finally {worker.interrupt();worker.join(2000);transport.setMachineState("Idle");backend.disconnect();}
        }
    }
    @Test void fileCompletionFlushesOutputWithoutClaimingIdle(@TempDir Path directory) throws Exception {
        Path file=directory.resolve("plot.gcode");var backend=new GcodeFileBackend(new GcodeOptions(),file.toFile());
        assertTrue(backend.connect());
        try {backend.moveto(3,4);assertEquals(PlotterBackend.MotionCompletion.OUTPUT_WRITTEN,backend.awaitMotionComplete());
            assertTrue(Files.readString(file).contains("X3.000 Y4.000"));
            assertFalse(Files.readString(file).contains("G4 P0"));
        } finally {backend.disconnect();}
    }
}
