package org.trostheide.gantry.plotter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The mock backend's GRBL emulation that the limit-switch wizard step is exercised against. */
class MockGrblEmulationTest {

    @Test
    void dollarDollarReportsHomingAndStepsSettings() {
        MockPlotterBackend mock = new MockPlotterBackend();
        List<String> dump = mock.sendRaw("$$");
        assertNotNull(GrblSettings.findSetting(dump, GrblSettings.HOMING_ENABLE), "$22 present");
        assertNotNull(GrblSettings.findSetting(dump, GrblSettings.X_STEPS_PER_MM), "$100 present");
    }

    @Test
    void writingHomingEnablePersists() {
        MockPlotterBackend mock = new MockPlotterBackend();
        assertEquals(0, GrblSettings.findSetting(mock.sendRaw("$$"), GrblSettings.HOMING_ENABLE));
        mock.sendRaw("$22=1");
        assertEquals(1, GrblSettings.findSetting(mock.sendRaw("$$"), GrblSettings.HOMING_ENABLE));
    }

    @Test
    void statusReportSurfacesSimulatedPins() {
        MockPlotterBackend mock = new MockPlotterBackend();
        assertEquals("", GrblSettings.parsePins(mock.sendRaw("?")), "no pins by default");
        mock.setSimulatedPins("XY");
        assertEquals("XY", GrblSettings.parsePins(mock.sendRaw("?")), "pressed switches show up");
    }
}
