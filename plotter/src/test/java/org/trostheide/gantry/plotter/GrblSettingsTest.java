package org.trostheide.gantry.plotter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GrblSettingsTest {

    @Test
    void parsePinsReadsTriggeredSwitches() {
        assertEquals("XY", GrblSettings.parsePins(List.of("<Idle|MPos:0.000,0.000,0.000|FS:0,0|Pn:XY>")));
        assertEquals("Z", GrblSettings.parsePins(List.of("<Run|MPos:1,2,3|Pn:Z>")));
    }

    @Test
    void parsePinsEmptyWhenStatusHasNoPinField() {
        // A status line with no Pn: field means "no pins active" — distinct from "no status at all".
        assertEquals("", GrblSettings.parsePins(List.of("<Idle|MPos:0.000,0.000,0.000|FS:0,0>")));
    }

    @Test
    void parsePinsNullWhenNoStatusLine() {
        assertNull(GrblSettings.parsePins(List.of("ok")));
        assertNull(GrblSettings.parsePins(List.of()));
        assertNull(GrblSettings.parsePins(null));
    }

    @Test
    void findSettingReadsHomingFlags() {
        List<String> dump = List.of("$20=0", "$21=1", "$22=1", "$23=3", "$100=80.000", "ok");
        assertEquals(0, GrblSettings.findSetting(dump, GrblSettings.SOFT_LIMITS));
        assertEquals(1, GrblSettings.findSetting(dump, GrblSettings.HARD_LIMITS));
        assertEquals(1, GrblSettings.findSetting(dump, GrblSettings.HOMING_ENABLE));
        assertEquals(3, GrblSettings.findSetting(dump, GrblSettings.HOMING_DIR_MASK));
    }
}
