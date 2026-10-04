package org.trostheide.gantry.app.plot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class UserStateFilesTest {
    @TempDir Path temp;

    @Test void migratesWithoutResurrectingDismissedRecovery() throws Exception {
        Path legacy = temp.resolve(".gantry-recovery");
        Path target = temp.resolve("profile/.gantry-recovery");
        Files.writeString(legacy, "recovery");
        assertEquals(target.toFile(), UserStateFiles.migrate(legacy.toFile(), target.toFile()));
        assertEquals("recovery", Files.readString(target));
        assertFalse(Files.exists(legacy));
        Files.delete(target);
        UserStateFiles.migrate(legacy.toFile(), target.toFile());
        assertFalse(Files.exists(target));
    }

    @Test void neverOverwritesExistingUserState() throws Exception {
        Path legacy = temp.resolve("old.json");
        Path target = temp.resolve("new.json");
        Files.writeString(legacy, "old");
        Files.writeString(target, "new");
        UserStateFiles.migrate(legacy.toFile(), target.toFile());
        assertEquals("new", Files.readString(target));
        assertEquals("old", Files.readString(legacy));
        Files.delete(target);
        UserStateFiles.migrate(legacy.toFile(), target.toFile());
        assertFalse(Files.exists(target), "old recovery must not return after dismissing newer state");
    }

    @Test void explicitProfileIsIndependentOfLaunchDirectory() {
        String previous = System.getProperty("gantry.config.file");
        try {
            Path profile = temp.resolve("isolated/config.json");
            System.setProperty("gantry.config.file", profile.toString());
            assertEquals(profile.toFile(), ConfigStore.guiConfigFile(temp.resolve("legacy.json").toFile()));
            assertEquals(profile.resolveSibling("plot-history.json").toFile(),
                    UserStateFiles.resolve(profile.toFile(), "plot-history.json"));
            assertTrue(Files.isDirectory(profile.getParent()));
        } finally {
            if (previous == null) System.clearProperty("gantry.config.file");
            else System.setProperty("gantry.config.file", previous);
        }
    }
}
