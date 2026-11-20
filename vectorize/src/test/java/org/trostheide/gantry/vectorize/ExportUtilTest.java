package org.trostheide.gantry.vectorize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class ExportUtilTest {
    @TempDir Path directory;
    @Test void pngContainsDrawingAndFailedOverwritePreservesDestination() throws Exception {
        Path svg = directory.resolve("drawing.svg"), png = directory.resolve("drawing.png");
        Files.writeString(svg, "<svg xmlns='http://www.w3.org/2000/svg' width='40' height='30'><path stroke='black' d='M5 5 L35 25'/></svg>");
        ExportUtil.exportToPng(svg.toString(), png.toString(), 0);
        var image = ImageIO.read(png.toFile());
        assertEquals(40, image.getWidth()); assertEquals(30, image.getHeight());
        assertTrue((image.getRGB(20, 15) >>> 24) > 0);
        byte[] original = Files.readAllBytes(png);
        Files.writeString(svg, "not SVG");
        assertThrows(Exception.class, () -> ExportUtil.exportToPng(svg.toString(), png.toString(), 0));
        assertArrayEquals(original, Files.readAllBytes(png));
        try (var files = Files.list(directory)) { assertEquals(2, files.count()); }
    }
}
