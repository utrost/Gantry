package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.trostheide.gantry.app.plot.*;
import org.trostheide.gantry.plotter.*;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SerialSimulationSelectionTest {
    @Test void selectedSerialSimulatorNeverOpensTheConfiguredPhysicalPort(@TempDir Path directory) throws Exception {
        var config=new GantryConfig();config.mock=true;config.serialSimulator=true;
        config.gcode.serialPort="THIS_PORT_MUST_NEVER_BE_OPENED";
        var file=directory.resolve("config.json").toFile();ConfigStore.save(config,file);
        var restored=ConfigStore.load(file);assertTrue(restored.mock);assertTrue(restored.serialSimulator);
        var backend=BackendConnectionCoordinator.createBackend(restored,line->{});
        assertInstanceOf(GcodeBackend.class,backend);assertTrue(backend.isSimulation());
        try {assertTrue(backend.connect());backend.moveto(2,3);
            assertEquals(PlotterBackend.MotionCompletion.SIMULATED_IDLE,backend.awaitMotionComplete());
        } finally {backend.disconnect();}
        String diagnostics=SupportDiagnostics.build(new SupportDiagnostics.Snapshot("test",restored,false,"",""));
        assertTrue(diagnostics.contains("Serial GRBL simulator (no physical hardware)"));
        restored.serialSimulator=false;
        assertInstanceOf(MockPlotterBackend.class,BackendConnectionCoordinator.createBackend(restored,line->{}));
        restored.mock=false;restored.serialSimulator=true;
        var hardware=BackendConnectionCoordinator.createBackend(restored,line->{});
        assertInstanceOf(GcodeBackend.class,hardware);assertFalse(hardware.isSimulation()); // never connect hardware
    }
}
