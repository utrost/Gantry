package org.trostheide.gantry.cli;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleGalleryDocsTest {

    @Test
    void sampleGalleryDocumentsCommittedParseableTesterSamples() throws Exception {
        Path root = findRepoRoot();
        Path gallery = root.resolve("docs/SAMPLE_GALLERY.md");

        assertTrue(Files.exists(gallery), "docs/SAMPLE_GALLERY.md should exist");
        String markdown = Files.readString(gallery, StandardCharsets.UTF_8);

        List<String> requiredSamples = List.of(
                "simple-line.svg",
                "hatch-fill.svg",
                "multi-colour-layers.svg",
                "text-outline.svg"
        );

        for (String sample : requiredSamples) {
            assertTrue(markdown.contains("docs/samples/" + sample), sample + " should be linked from the gallery");
            Path samplePath = root.resolve("docs/samples").resolve(sample);
            assertTrue(Files.exists(samplePath), sample + " should be committed under docs/samples");
            assertParseableSvg(samplePath);
        }

        assertTrue(markdown.contains("Mock practice"), "gallery should tell testers which samples are safe for mock practice");
        assertTrue(markdown.contains("G-code export"), "gallery should tell testers which samples are suitable for export");
        assertTrue(markdown.contains("Real pen plot"), "gallery should state real-plot suitability");
        assertTrue(markdown.contains("Expected success"), "gallery should document success evidence for testers");
        assertTrue(markdown.contains("Recommended settings"), "gallery should include settings rather than only file names");

        assertLocalMarkdownLinksResolve(root, gallery, markdown);
        assertLocalMarkdownLinksResolve(root, root.resolve("README.md"), Files.readString(root.resolve("README.md"), StandardCharsets.UTF_8));
        assertLocalMarkdownLinksResolve(root, root.resolve("docs/FIRST_PLOT.md"), Files.readString(root.resolve("docs/FIRST_PLOT.md"), StandardCharsets.UTF_8));
    }

    private static void assertParseableSvg(Path svg) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().parse(svg.toFile());
        assertEquals("svg", document.getDocumentElement().getLocalName(), svg + " should have an <svg> root");
        assertFalse(document.getDocumentElement().getAttribute("viewBox").isBlank(), svg + " should declare a viewBox");
    }

    private static void assertLocalMarkdownLinksResolve(Path root, Path markdownPath, String markdown) {
        Matcher matcher = Pattern.compile("\\[[^]]+]\\(([^)]+)\\)").matcher(markdown);
        while (matcher.find()) {
            String target = matcher.group(1);
            if (target.contains("://") || target.startsWith("#") || target.startsWith("mailto:")) {
                continue;
            }
            String pathOnly = target.split("#", 2)[0];
            if (pathOnly.isBlank()) {
                continue;
            }
            Path resolved = markdownPath.getParent().resolve(pathOnly).normalize();
            assertTrue(Files.exists(resolved), markdownPath + " links to missing local target " + target);
            assertTrue(resolved.startsWith(root), markdownPath + " should not link outside the repository: " + target);
        }
    }

    private static Path findRepoRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("README.md"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not find Gantry repository root from user.dir");
    }
}
