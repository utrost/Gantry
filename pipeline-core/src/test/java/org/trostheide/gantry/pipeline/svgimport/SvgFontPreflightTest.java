package org.trostheide.gantry.pipeline.svgimport;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SvgFontPreflightTest {

    @Test
    void logicalFontAndAbsentFontDeclarationNeedNoWarning() throws Exception {
        SvgFontPreflight.Result result = inspect("<text x='0' y='10'>default</text>"
                + "<text x='0' y='20' font-family='monospace'>logical</text>");
        assertFalse(result.hasMissingFonts());
    }

    @Test
    void installedFallbackPreventsWarningWhenPrimaryIsMissing() throws Exception {
        SvgFontPreflight.Result result = inspect(
                "<text font-family=\"'Definitely Missing Gantry Font', sans-serif\">fallback</text>");
        assertFalse(result.hasMissingFonts());
    }

    @Test
    void unresolvedInheritedStyleIsAggregatedAndNamesSubstitute() throws Exception {
        SvgFontPreflight.Result result = inspect("<g style=\"font-family: 'Definitely Missing Gantry Font'\">"
                + "<text>one</text><text>two</text></g>");

        assertTrue(result.hasMissingFonts());
        assertEquals(1, result.missingFonts().size());
        SvgFontPreflight.MissingFont issue = result.missingFonts().get(0);
        assertEquals(java.util.List.of("Definitely Missing Gantry Font"), issue.requestedFamilies());
        assertEquals("SansSerif", issue.substituteFamily());
        assertEquals(2, issue.textElements());
    }

    private static SvgFontPreflight.Result inspect(String content) throws Exception {
        File svg = File.createTempFile("font-preflight", ".svg");
        svg.deleteOnExit();
        Files.writeString(svg.toPath(), "<svg xmlns='http://www.w3.org/2000/svg'>" + content + "</svg>");
        return SvgFontPreflight.inspect(svg);
    }
}
