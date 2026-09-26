package org.trostheide.gantry.app.plot;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Loads and saves {@link GantryConfig} as {@code config.json}. */
public class ConfigStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ConfigStore() {
    }

    /**
     * Returns the GUI's stable per-user settings file, migrating the legacy working-directory
     * {@code config.json} on first use. Set {@code -Dgantry.config.file=...} to override it.
     */
    public static File guiConfigFile(File legacyFile) {
        String override = System.getProperty("gantry.config.file");
        File target;
        if (override != null && !override.isBlank()) {
            return new File(override).getAbsoluteFile();
        } else {
            String os = System.getProperty("os.name", "").toLowerCase();
            String home = System.getProperty("user.home", ".");
            if (os.contains("win")) {
                String appData = System.getenv("APPDATA");
                target = new File(appData == null || appData.isBlank() ? home : appData,
                        "Gantry/config.json");
            } else if (os.contains("mac")) {
                target = new File(home, "Library/Application Support/Gantry/config.json");
            } else {
                String xdg = System.getenv("XDG_CONFIG_HOME");
                target = new File(xdg == null || xdg.isBlank() ? new File(home, ".config") : new File(xdg),
                        "gantry/config.json");
            }
        }
        return migrateLegacy(legacyFile, target);
    }

    static File migrateLegacy(File legacyFile, File target) {
        File stable = target.getAbsoluteFile();
        if (stable.exists() || legacyFile == null || !legacyFile.exists()
                || stable.equals(legacyFile.getAbsoluteFile())) return stable;
        try {
            File parent = stable.getParentFile();
            if (parent != null) Files.createDirectories(parent.toPath());
            Files.copy(legacyFile.toPath(), stable.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            System.out.println("Migrated Gantry settings to " + stable);
            return stable;
        } catch (IOException e) {
            System.out.println("WARNING: Could not migrate Gantry settings to " + stable + ": " + e.getMessage());
            return legacyFile.getAbsoluteFile();
        }
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
        File parent = file == null ? null : file.getAbsoluteFile().getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, config);
    }
}
