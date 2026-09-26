package org.trostheide.gantry.app.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class InstallationCheckTest {
    @TempDir Path root;

    @Test void persistedProfileSurvivesAnotherAcceptanceRun() throws Exception {
        Path profile = root.resolve("profile with spaces");
        var seeded = InstallationCheck.run(profile, true);
        assertTrue(seeded.contains("raster-vectorize-import"));
        assertTrue(seeded.contains("mock-cancel-pen-up"));
        byte[] config = Files.readAllBytes(profile.resolve("config.json"));
        byte[] recovery = Files.readAllBytes(profile.resolve(".gantry-recovery"));
        assertEquals(seeded, InstallationCheck.run(profile, false));
        assertArrayEquals(config, Files.readAllBytes(profile.resolve("config.json")));
        assertArrayEquals(recovery, Files.readAllBytes(profile.resolve(".gantry-recovery")));
    }

    @Test void refusesToSeedAnExistingProfile() throws Exception {
        Files.writeString(root.resolve("config.json"), "do not replace");
        assertThrows(IOException.class, () -> InstallationCheck.run(root, true));
        assertEquals("do not replace", Files.readString(root.resolve("config.json")));
    }

    @Test void failsWhenPersistedSettingsAreCorrupt() throws Exception {
        InstallationCheck.run(root, true);
        Files.writeString(root.resolve("config.json"), "broken");
        assertThrows(IOException.class, () -> InstallationCheck.run(root, false));
    }
}
