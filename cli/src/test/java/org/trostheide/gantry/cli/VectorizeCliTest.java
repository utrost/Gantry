package org.trostheide.gantry.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the {@link VectorizeCli} front controller end to end: image → SVG, and the
 * {@code --}-separated chain image → SVG → command JSON. Verifies the wiring (argument
 * split, SVG-path derivation, {@code -i} injection) and that the produced SVG flows through
 * the existing {@code SvgImportCli} import.
 */
class VectorizeCliTest {

    @TempDir
    Path tmp;
    private Path image;

    @BeforeEach
    void makeImage() throws Exception {
        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 200);
        g.setColor(Color.BLACK);
        g.fillOval(40, 40, 120, 120);
        g.fillRect(20, 20, 40, 40);
        g.dispose();
        image = tmp.resolve("in.png");
        ImageIO.write(img, "png", image.toFile());
    }

    @AfterEach
    void cleanupDebugArtifacts() throws Exception {
        // Canny strategies write diagnostic edge maps to the working directory; keep the tree clean.
        Files.deleteIfExists(Path.of("edges_debug.png"));
        Files.deleteIfExists(Path.of("edges_debug_simplified.png"));
    }

    @Test
    void imageToSvgOnly() throws Exception {
        Path svg = tmp.resolve("out.svg");

        VectorizeCli.main(new String[] {
                "-i", image.toString(), "-o", svg.toString(), "-s", "dp", "--canny-auto"});

        assertTrue(Files.exists(svg), "VectorizeCli should write the SVG");
        assertTrue(Files.readString(svg).contains("<svg"), "output should be an SVG document");
    }

    @Test
    void imageToCommandsChain() throws Exception {
        Path svg = tmp.resolve("chain.svg");
        Path json = tmp.resolve("chain.json");

        // Everything after `--` is the SVG-import side; the produced SVG is injected as -i.
        VectorizeCli.main(new String[] {
                "-i", image.toString(), "-o", svg.toString(), "-s", "centerline", "--cl-threshold", "128",
                "--", "-o", json.toString(), "--fit-to", "A4"});

        assertTrue(Files.exists(svg), "intermediate SVG should be written");
        assertTrue(Files.exists(json), "command JSON should be written");
        String j = Files.readString(json);
        assertTrue(j.contains("\"metadata\""), "JSON should be a Gantry command model");
        assertTrue(j.contains("\"layers\""), "command model should contain layers");
    }
}
