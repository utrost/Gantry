package org.trostheide.gantry.plotter;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SerialSimulatorTest {
    private record Rig(GcodeBackend backend,SerialSimulatorTransport transport) implements AutoCloseable {
        public void close(){backend.disconnect();transport.close();}
    }
    private Rig rig(String penMode) {
        var options=new GcodeOptions();options.bootDelayMillis=0;options.penDownDelayMillis=0;
        options.positionPollIntervalSeconds=.01;options.responseTimeoutSeconds=1;options.penMode=penMode;
        var transport=new SerialSimulatorTransport(options);
        var backend=new GcodeBackend(options,(port,baud)->transport);
        assertTrue(backend.connect());assertTrue(backend.isSimulation());return new Rig(backend,transport);
    }
    @Test void productionBackendAcknowledgesMotionBeforeExecutingItAndWaitsAtTheBarrier() throws Exception {
        try(var rig=rig("servo")) {
            rig.transport.pauseExecution();rig.backend.moveto(10,20);
            assertEquals("Run",rig.transport.snapshot().state());assertEquals(0,rig.transport.snapshot().x());
            AtomicReference<Object> result=new AtomicReference<>();
            Thread worker=new Thread(()->{try{result.set(rig.backend.awaitMotionComplete());}catch(Throwable e){result.set(e);}});
            try {
                worker.start();assertTrue(rig.transport.awaitCommand("G4 P0",2,TimeUnit.SECONDS));
                assertNull(result.get());rig.transport.resumeExecution();worker.join(2000);
                assertFalse(worker.isAlive());assertEquals(PlotterBackend.MotionCompletion.SIMULATED_IDLE,result.get());
                assertEquals(10,rig.transport.snapshot().x(),.001);assertEquals(20,rig.transport.snapshot().y(),.001);
            } finally {worker.interrupt();worker.join(2000);rig.transport.resumeExecution();}
        }
    }
    @Test void staleIdleReportsCannotBypassThePlannerBarrier() throws Exception {
        try(var rig=rig("servo")) {
            rig.transport.freezeStatusReports();rig.transport.pauseExecution();rig.backend.moveto(10,0);
            AtomicReference<Object> result=new AtomicReference<>();
            Thread worker=new Thread(()->{try{result.set(rig.backend.awaitMotionComplete());}catch(Throwable e){result.set(e);}});
            try {
                worker.start();assertTrue(rig.transport.awaitCommand("G4 P0",2,TimeUnit.SECONDS));assertNull(result.get());
                rig.transport.resumeExecution();worker.join(2000);
                assertEquals(PlotterBackend.MotionCompletion.SIMULATED_IDLE,result.get());
                assertEquals(10,rig.transport.snapshot().x(),.001);
            } finally {worker.interrupt();worker.join(2000);rig.transport.resumeStatusReports();rig.transport.resumeExecution();}
        }
    }
    @Test void freshStatusIsRequiredAfterTheBarrierAcknowledgement() {
        try(var rig=rig("servo")) {
            rig.transport.suppressStatusReports();
            assertTimeoutPreemptively(Duration.ofSeconds(2),()->assertThrows(GcodeBackendException.class,rig.backend::awaitMotionComplete));
            rig.transport.resumeStatusReports();
        }
    }
    @Test void delayedAckIsIndependentOfExecution() throws Exception {
        try(var rig=rig("servo")) {
            rig.transport.armFault(SerialSimulatorTransport.Fault.DELAY_ACK);
            AtomicReference<Throwable> failure=new AtomicReference<>();
            Thread worker=new Thread(()->{try{rig.backend.moveto(1,2);}catch(Throwable e){failure.set(e);}});
            try {
                worker.start();assertTrue(rig.transport.awaitCommand("G1 X1.000 Y2.000 F3000",2,TimeUnit.SECONDS));
                assertTrue(worker.isAlive());rig.transport.releaseDelayedAcknowledgements();worker.join(2000);
                assertFalse(worker.isAlive());assertNull(failure.get());rig.backend.awaitMotionComplete();
                assertEquals(1,rig.transport.snapshot().x(),.001);
            } finally {worker.interrupt();worker.join(2000);rig.transport.releaseDelayedAcknowledgements();}
        }
    }
    @Test void faultsFailTheProductionBackendWithoutPretendingSuccessfulExecution() {
        for(var fault:new SerialSimulatorTransport.Fault[]{SerialSimulatorTransport.Fault.DROP_ACK,
                SerialSimulatorTransport.Fault.REJECT_COMMAND,SerialSimulatorTransport.Fault.ALARM,
                SerialSimulatorTransport.Fault.DISCONNECT,SerialSimulatorTransport.Fault.RESET}) {
            try(var rig=rig("servo")) {
                rig.transport.armFault(fault);
                assertTimeoutPreemptively(Duration.ofSeconds(2),()->assertThrows(GcodeBackendException.class,()->rig.backend.moveto(1,2)),fault.name());
                if(fault!=SerialSimulatorTransport.Fault.DROP_ACK)assertEquals(0,rig.transport.snapshot().x(),.001);
                if(fault==SerialSimulatorTransport.Fault.RESET) {
                    assertThrows(GcodeBackendException.class,()->rig.backend.moveto(2,3));
                }
            }
        }
    }
    @Test void relativeModeWorkOffsetsAndAllLiftModesUseTheSameSerialPipeline() {
        for(String mode:new String[]{"servo","zaxis","m3m5"})try(var rig=rig(mode)) {
            rig.backend.moveto(10,20);rig.backend.awaitMotionComplete();
            assertEquals(java.util.List.of("ok"),rig.backend.sendRaw("G92 X0 Y0"));
            rig.backend.moveto(2,3);rig.backend.pendown();rig.backend.move(1,2);rig.backend.awaitMotionComplete();
            var point=rig.transport.snapshot();assertEquals(3,point.x(),.001);assertEquals(5,point.y(),.001);assertTrue(point.penDown(),mode);
            if(mode.equals("zaxis")) {
                rig.backend.pendown(-2);rig.backend.awaitMotionComplete();
                assertTrue(rig.transport.snapshot().penDown(), "custom dip depth remains lowered");
                assertEquals(-2,rig.transport.snapshot().z(),.001);
            }
            rig.backend.moveto(4,6);rig.backend.awaitMotionComplete();assertFalse(rig.transport.snapshot().penDown(),mode);
            rig.backend.home();rig.backend.awaitMotionComplete();
            assertEquals(0,rig.transport.snapshot().x(),.001);assertEquals(0,rig.transport.snapshot().y(),.001);assertFalse(rig.transport.snapshot().penDown(),mode);
        }
    }
    @Test void stopDiscardsQueuedMotionAndReconnectStartsAFreshSimulator() throws Exception {
        var options=new GcodeOptions();options.serialPort="THIS_IS_NOT_A_REAL_PORT";options.penDownDelayMillis=0;
        var backend=GcodeBackend.serialSimulator(options);
        try {
            assertTrue(backend.isSimulation());assertTrue(backend.connect());
            backend.moveto(2,3);backend.awaitMotionComplete();backend.haltMotion();backend.awaitMotionComplete();
            backend.disconnect();assertTrue(backend.connect());backend.awaitMotionComplete();
            assertArrayEquals(new double[]{0,0},backend.queryPosition(),.001);
        } finally {backend.disconnect();}
        try(var rig=rig("servo")) {
            rig.transport.pauseExecution();rig.backend.moveto(100,100);rig.backend.moveto(200,200);
            rig.backend.haltMotion();rig.backend.awaitMotionComplete();
            assertEquals(0,rig.transport.snapshot().x(),.001);assertFalse(rig.transport.snapshot().penDown());
            rig.backend.moveto(3,4);rig.backend.awaitMotionComplete();assertEquals(3,rig.transport.snapshot().x(),.001);
        }
    }
    @Test void realtimeHoldAndResumeStopThePlannerWithoutLosingTheQueue() throws Exception {
        try(var rig=rig("servo")) {
            rig.transport.pauseExecution();rig.backend.moveto(5,5);
            rig.transport.writeBytes(new byte[]{'!'});
            assertEquals("Hold:0",rig.transport.snapshot().state());assertEquals(0,rig.transport.snapshot().x());
            rig.transport.resumeExecution();rig.transport.writeBytes(new byte[]{'~'});
            rig.backend.awaitMotionComplete();
            assertEquals(5,rig.transport.snapshot().x(),.001);
        }
    }
    @Test void unknownCommandsAreRejectedAndSettingsAreReadWithoutHardware() {
        try(var rig=rig("servo")) {
            assertEquals(java.util.List.of("error:20"),rig.backend.sendRaw("G999 X10"));
            assertEquals(0,rig.transport.snapshot().x());
            assertEquals(java.util.List.of("error:20"),rig.backend.sendRaw("G4"));
            assertEquals(java.util.List.of("error:20"),rig.backend.sendRaw("M280 P0 S999"));
            var settings=rig.backend.sendRaw("$$");assertTrue(settings.contains("$130=300.0"));assertTrue(settings.contains("ok"));
            assertEquals(java.util.List.of("ok"),rig.backend.sendRaw("$100=81"));
            assertTrue(rig.backend.sendRaw("$$").contains("$100=81.0"));
        }
    }
    @Test void malformedLinesDiscardTheirTailAndClosedSnapshotsAreExplicit() throws Exception {
        var options = new GcodeOptions();
        try (var transport = new SerialSimulatorTransport(options)) {
            transport.readLine(); // boot banner
            transport.writeBytes(("X".repeat(513) + "G1 X20\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            assertEquals("error:24", transport.readLine());
            assertTrue(transport.commands().isEmpty(), "An invalid line must not execute a valid-looking suffix");
            transport.writeBytes("$I\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            assertTrue(transport.readLine().startsWith("[VER:"));
            assertEquals("ok", transport.readLine());
            transport.close();
            assertEquals("Disconnected", transport.snapshot().state());
        }
    }
    @Test void disabledHomingIsRejectedThroughTheProductionBackend() {
        try (var rig = rig("servo")) {
            assertEquals(java.util.List.of("ok"), rig.backend.sendRaw("$22=0"));
            assertThrows(GcodeBackendException.class, rig.backend::home);
            assertEquals(0, rig.transport.snapshot().x());
        }
    }

    @Test void staleIdleCannotStartHomingBeforeTheQueuedPenLiftCompletes() throws Exception {
        try (var rig=rig("zaxis")) {
            rig.transport.freezeStatusReports(); rig.transport.pauseExecution();
            var barrierOrHome=new CountDownLatch(1);
            rig.backend.setSentCommandCallback(command->{
                if(command.equals("G4 P0") || command.equals("$H"))barrierOrHome.countDown();
            });
            var failure=new AtomicReference<Throwable>();
            var home=new Thread(()->{try{rig.backend.home();}catch(Throwable error){failure.set(error);}});
            try {
                home.start();assertTrue(barrierOrHome.await(2,TimeUnit.SECONDS));
                assertFalse(rig.transport.commands().contains("$H"),"Frozen Idle must not release Home while the pen lift remains queued");
                rig.transport.resumeExecution();home.join(2000);
                assertFalse(home.isAlive());assertNull(failure.get());
                assertTrue(rig.transport.commands().contains("$H"));
                assertFalse(rig.transport.snapshot().penDown());
                assertEquals(rig.backend.getOptions().zUp,rig.transport.snapshot().z(),.001);
            } finally {home.interrupt();rig.transport.resumeExecution();rig.transport.resumeStatusReports();home.join(2000);}
        }
    }

}
