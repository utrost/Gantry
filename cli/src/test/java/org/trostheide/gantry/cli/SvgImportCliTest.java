package org.trostheide.gantry.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.pipeline.io.ProcessorOutputIO;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SvgImportCliTest {

    @TempDir
    Path tmp;

    @Test
    void passesRepeatsImportedStrokesAndUpdatesMetadata() throws Exception {
        Path input = writeSvg("""
                <path d="M10,10 L90,10" fill="none" stroke="#000000"/>
                <path d="M10,30 L90,30" fill="none" stroke="#000000"/>
                """);
        Path singleFile = tmp.resolve("single.json");
        Path tripleFile = tmp.resolve("triple.json");

        SvgImportCli.main(new String[] {"-i", input.toString(), "-o", singleFile.toString()});
        SvgImportCli.main(new String[] {
                "-i", input.toString(), "-o", tripleFile.toString(), "--passes", "3"});

        ProcessorOutput single = ProcessorOutputIO.load(singleFile.toFile());
        ProcessorOutput triple = ProcessorOutputIO.load(tripleFile.toFile());
        int singleDraws = drawCount(single);
        int tripleDraws = drawCount(triple);

        assertTrue(singleDraws > 0, "fixture should import at least one stroke");
        assertEquals(singleDraws * 3, tripleDraws);
        assertEquals(commandCount(triple), triple.metadata().totalCommands());
    }

    @Test
    void styleAppliesPerColorHatchOverride() throws Exception {
        Path input = writeSvg("""
                <rect x="10" y="10" width="80" height="80" fill="#ff0000"/>
                """);
        Path linearFile = tmp.resolve("linear.json");
        Path overrideFile = tmp.resolve("override.json");

        SvgImportCli.main(new String[] {
                "-i", input.toString(), "-o", linearFile.toString(),
                "--toolbox", "--hatch", "--pattern", "linear", "--hatch-gap", "20"});
        SvgImportCli.main(new String[] {
                "-i", input.toString(), "-o", overrideFile.toString(),
                "--toolbox", "--hatch", "--pattern", "linear", "--hatch-gap", "20",
                "--style", "#ff0000:0:20:cross"});

        int linearDraws = drawCount(ProcessorOutputIO.load(linearFile.toFile()));
        int overrideDraws = drawCount(ProcessorOutputIO.load(overrideFile.toFile()));

        assertTrue(linearDraws > 0, "global linear hatch should produce strokes");
        assertTrue(overrideDraws > linearDraws,
                "red cross-hatch override should produce more strokes than global linear hatch");
    }

    private Path writeSvg(String body) throws Exception {
        Path input = tmp.resolve("input-" + System.nanoTime() + ".svg");
        Files.writeString(input, """
                <svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100">
                """ + body + "</svg>\n");
        return input;
    }

    private static int drawCount(ProcessorOutput output) {
        return (int) output.layers().stream()
                .flatMap(layer -> layer.commands().stream())
                .filter(DrawCommand.class::isInstance)
                .count();
    }

    private static int commandCount(ProcessorOutput output) {
        return output.layers().stream().mapToInt(layer -> layer.commands().size()).sum();
    }
}
