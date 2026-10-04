package org.trostheide.gantry.app.help;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.trostheide.gantry.app.GantryApp;

/** Locates the help shipped beside the GUI JAR, independent of the working directory. */
public final class OfflineHelp {
    private OfflineHelp() { }

    public static Path guide() throws IOException {
        try {
            Path location = Path.of(GantryApp.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return guideAt(location);
        } catch (URISyntaxException | SecurityException ex) {
            throw new IOException("Could not locate the installed offline guide", ex);
        }
    }

    static Path guideAt(Path codeLocation) throws IOException {
        Path directory = Files.isDirectory(codeLocation) ? codeLocation : codeLocation.getParent();
        Path packaged = directory.resolve("docs/index.html");
        if (Files.isRegularFile(packaged)) return packaged.toAbsolutePath();
        throw new IOException("Offline guide is missing. Install Gantry again, or extract the matching "
                + "documentation ZIP beside the GUI JAR.");
    }
}
