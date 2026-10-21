package org.trostheide.gantry.app.plot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class MachineSettingsIOTest {
    @TempDir Path directory;
    @Test void portableRoundTripPreservesLocalPreferencesAndDetachesMachineData() throws Exception {
        GantryConfig source=new GantryConfig();source.gcode.machineWidth=594;source.gcode.machineHeight=841;
        source.gcode.serialPort="COM7";source.gcode.penMode="zaxis";source.gcode.zDown=-2;
        source.invertX=true;source.hasLimitSwitches=true;source.softLimits=false;
        source.stations.put("blue",new StationConfig(20,30,-2,"dip_swirl","#0000ff",700,3));
        source.lastDirectory="private path";source.preflightBeforeStart=false;
        File file=directory.resolve("machine.json").toFile();MachineSettingsIO.exportSettings(source,file);
        String json=Files.readString(file.toPath());
        for(String field:new String[]{"uiTheme","lastDirectory","preflightBeforeStart","mock","diagnosticsOpen"})assertFalse(json.contains('"'+field+'"'),field);
        GantryConfig local=new GantryConfig();local.uiTheme="Dark";local.mock=true;local.inspectorWidth=444;local.lastDirectory="local";
        GantryConfig imported=MachineSettingsIO.importSettings(file,local);
        assertEquals(594,imported.gcode.machineWidth);assertEquals("COM7",imported.gcode.serialPort);
        assertEquals(-2,imported.gcode.zDown);assertEquals(source.stations,imported.stations);
        assertTrue(imported.invertX);assertFalse(imported.softLimits);assertTrue(imported.hasLimitSwitches);
        assertEquals("Dark",imported.uiTheme);assertTrue(imported.mock);assertTrue(imported.preflightBeforeStart);
        assertEquals(444,imported.inspectorWidth);assertEquals("local",imported.lastDirectory);
        imported.gcode.machineWidth=1;assertEquals(300,local.gcode.machineWidth);assertEquals(594,source.gcode.machineWidth);
    }
    @Test void existingConfigFilesCanBeImportedWithoutCopyingTheirApplicationPolicy() throws Exception {
        GantryConfig source=new GantryConfig();source.gcode.machineWidth=600;source.preflightBeforeStart=false;
        File file=directory.resolve("config.json").toFile();ConfigStore.save(source,file);
        GantryConfig imported=MachineSettingsIO.importSettings(file,new GantryConfig());
        assertEquals(600,imported.gcode.machineWidth);assertTrue(imported.preflightBeforeStart);
    }
    @Test void rejectsMalformedUnsupportedAndInvalidFilesWithoutMutatingCurrentSettings() throws Exception {
        String[] invalid={"broken", "null", "[]", "{}", "{\"gcode\":{}}",
                "{\"format\":\"gantry-machine-settings\",\"version\":2,\"settings\":{}}",
                "{\"gcode\":{\"machineWidth\":0,\"machineHeight\":200}}",
                "{\"gcode\":{\"machineWidth\":null,\"machineHeight\":200}}",
                "{\"gcode\":{\"machineWidth\":\"300\",\"machineHeight\":200}}",
                "{\"gcode\":{\"machineWidth\":300,\"machineHeight\":200,\"penMode\":\"unknown\"}}",
                "{\"gcode\":{\"machineWidth\":300,\"machineHeight\":200},\"stations\":null}"};
        GantryConfig current=new GantryConfig();current.gcode.serialPort="COM9";
        for(String text:invalid){File file=directory.resolve("invalid.json").toFile();Files.writeString(file.toPath(),text);
            assertThrows(IOException.class,()->MachineSettingsIO.importSettings(file,current),text);
            assertEquals("COM9",current.gcode.serialPort);assertEquals(300,current.gcode.machineWidth);}
    }
    @Test void legacyCamelCaseStationsRetainTheirDepthAndTiming() throws Exception {
        File file=directory.resolve("legacy.json").toFile();
        Files.writeString(file.toPath(),"""
                {"gcode":{"machineWidth":300,"machineHeight":200},
                 "stations":{"blue":{"x":20,"y":30,"zDown":-2,"behavior":"dip_swirl","dwellMs":700,"swirlRadius":3}}}
                """);
        var imported=MachineSettingsIO.importSettings(file,new GantryConfig());
        assertEquals(-2,imported.stations.get("blue").zDown());
        assertEquals(700,imported.stations.get("blue").dwellMs());
        ConfigStore.save(imported,file);
        assertEquals(imported.stations,ConfigStore.load(file).stations);
    }

    @Test void failedExportDoesNotReplaceAnExistingBackup() throws Exception {
        File file=directory.resolve("backup.json").toFile();Files.writeString(file.toPath(),"previous backup");
        GantryConfig invalid=new GantryConfig();invalid.gcode.machineWidth=Double.NaN;
        assertThrows(IOException.class,()->MachineSettingsIO.exportSettings(invalid,file));
        assertEquals("previous backup",Files.readString(file.toPath()));
    }
}
