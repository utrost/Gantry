package org.trostheide.gantry.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.trostheide.gantry.plotter.GcodeOptions;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Headless machine/station settings used by station mapping and G-code export. */
public class CliBatchConfig {
    public GcodeOptions gcode = new GcodeOptions();
    public Map<String, Station> stations = new LinkedHashMap<>();

    public static class Station {
        public double x;
        public double y;
        public double zDown;
        public String color;
        public String behavior = "simple_dip";
        public long dwellMs = 500;
        public double swirlRadius = 2;
    }

    public static CliBatchConfig load(File file) throws IOException {
        return new ObjectMapper().readValue(file, CliBatchConfig.class);
    }
}
