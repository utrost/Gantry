package org.trostheide.gantry.app.plot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Keeps GUI history and recovery beside the per-user configuration. */
public final class UserStateFiles {
    private UserStateFiles() { }

    public static File resolve(File configFile, String name) {
        String override = System.getProperty("gantry.config.file");
        // Explicit profiles must never import state from the developer's working directory.
        File legacy = override == null || override.isBlank() ? new File(name) : null;
        return migrate(legacy, new File(configFile.getAbsoluteFile().getParentFile(), name));
    }

    static File migrate(File legacy, File target) {
        File stable = target.getAbsoluteFile();
        try {
            Files.createDirectories(stable.getParentFile().toPath());
            var marker = stable.toPath().resolveSibling(stable.getName() + ".migrated");
            if (Files.exists(marker)) return stable;
            if (!stable.exists() && legacy != null && legacy.isFile()
                    && !stable.equals(legacy.getAbsoluteFile())) {
                // Consume the old file so a dismissed recovery cannot reappear next launch.
                // Never replace a newer per-user file.
                Files.move(legacy.toPath(), stable.toPath());
            }
            if (stable.exists()) Files.writeString(marker, "Legacy migration considered.\n");
        } catch (IOException ex) {
            System.err.println("WARNING: Could not prepare Gantry state " + stable + ": " + ex.getMessage());
        }
        return stable;
    }
}
