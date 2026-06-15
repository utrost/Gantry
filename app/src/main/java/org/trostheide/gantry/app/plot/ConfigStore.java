package org.trostheide.gantry.app.plot;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

/** Loads and saves {@link GantryConfig} as {@code config.json}. */
public class ConfigStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ConfigStore() {
    }

    /** Returns a default config if {@code file} doesn't exist or fails to parse. */
    public static GantryConfig load(File file) {
        if (file == null || !file.exists()) {
            return new GantryConfig();
        }
        try {
            return MAPPER.readValue(file, GantryConfig.class);
        } catch (IOException e) {
            System.out.println("WARNING: Failed to load config " + file + ": " + e.getMessage());
            return new GantryConfig();
        }
    }

    public static void save(GantryConfig config, File file) throws IOException {
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, config);
    }
}
