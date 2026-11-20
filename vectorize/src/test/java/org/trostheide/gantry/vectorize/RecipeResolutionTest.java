package org.trostheide.gantry.vectorize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.commons.cli.ParseException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class RecipeResolutionTest {
    @TempDir Path directory;
    @Test void legacyNestedSettingsAndFlagsRemainReadable() throws Exception {
        Path recipe = directory.resolve("legacy.json");
        Files.writeString(recipe, """
                {"sourceImage":"in.png","strategy":"dp","contour":{"tolerance":1.3,"detail":0.8,
                "cannyAuto":true,"colorEdges":true,"strokeColor":"#123456","strokeWidth":0.7,"smoothCurves":true}}
                """);
        var cli = new CliParser(); var cmd = cli.loadConfigFile(recipe.toString());
        assertEquals(1.3, cli.getTolerance(cmd)); assertEquals(.8, cli.getDetailLevel(cmd));
        assertEquals("#123456", cli.getStrokeColor(cmd)); assertEquals(.7, cli.getStrokeWidth(cmd));
        assertTrue(cli.getCannyAuto(cmd)); assertTrue(cli.getColorEdges(cmd)); assertTrue(cli.getSmoothCurves(cmd));
        Files.writeString(recipe, "{\"sourceImage\":\"in.png\",\"strategy\":\"sketch\",\"sketch\":{\"minLength\":19,\"strokeColor\":\"#abcdef\"}}");
        var sketch = cli.loadConfigFile(recipe.toString());
        assertEquals(19, cli.getSketchMinLength(sketch));
        assertEquals(5, cli.getMinLength(sketch), "traced-point count is not generic pixel-length filtering");
        assertEquals("#abcdef", cli.getStrokeColor(sketch));
    }
    @Test void thresholdsAreValidatedAfterConfigAndExplicitOptionsAreMerged() throws Exception {
        Path recipe = directory.resolve("thresholds.json");
        Files.writeString(recipe, "{\"sourceImage\":\"in.png\",\"strategy\":\"dp\",\"cannyLow\":0.3,\"cannyHigh\":0.8}");
        var cli = new CliParser();
        var merged = Main.resolveConfig(cli, cli.parse(new String[] {"--config", recipe.toString(), "--canny-low", "0.4"}));
        assertEquals(.4, cli.getCannyLow(merged)); assertEquals(.8, cli.getCannyHigh(merged));
        assertThrows(ParseException.class, () -> Main.resolveConfig(cli,
                cli.parse(new String[] {"--config", recipe.toString(), "--canny-low", "0.9"})));
    }
    @Test void embedderResolvesSavedDefaultsAndSourceOverridesBeforeRendering() throws Exception {
        Path input = directory.resolve("input.png"), recipe = directory.resolve("recipe.json"), output = directory.resolve("output.svg");
        var image = new java.awt.image.BufferedImage(80, 80, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(java.awt.Color.WHITE); g.fillRect(0, 0, 80, 80);
        g.setColor(java.awt.Color.BLACK); g.fillRect(10, 10, 40, 40); g.dispose();
        javax.imageio.ImageIO.write(image, "png", input.toFile());
        Files.writeString(recipe, "{\"sourceImage\":\"missing.png\",\"strategy\":\"needles\",\"strokeColor\":\"#ff0000\"}");
        Main.runSingleFile(new String[] {"--config", recipe.toString(), "--input", input.toString(),
                "--output", output.toString(), "--strategy", "sketch", "--crop", "0,0,60,60"});
        String svg = Files.readString(output);
        assertTrue(svg.contains("viewBox=\"0 0 60 60\""));
        assertTrue(svg.contains("stroke:#ff0000"));
    }
}
