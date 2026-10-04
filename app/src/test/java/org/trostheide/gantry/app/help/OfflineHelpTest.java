package org.trostheide.gantry.app.help;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class OfflineHelpTest {
    @TempDir Path temporary;

    @Test void findsGuideBesideJarInAnInstallationPathWithSpaces() throws Exception {
        Path app = temporary.resolve("A chosen installation directory/app");
        Files.createDirectories(app.resolve("docs"));
        Path jar = Files.writeString(app.resolve("Gantry.jar"), "fixture");
        Path html = Files.writeString(app.resolve("docs/index.html"), "offline guide");
        assertEquals(html.toAbsolutePath(), OfflineHelp.guideAt(jar));
    }

    @Test void missingDocumentationGivesAnActionableErrorInsteadOfAnOnlineFallback() throws Exception {
        Path jar = Files.writeString(temporary.resolve("Gantry.jar"), "fixture");
        IOException error = assertThrows(IOException.class, () -> OfflineHelp.guideAt(jar));
        assertTrue(error.getMessage().contains("documentation ZIP"));
    }
}
