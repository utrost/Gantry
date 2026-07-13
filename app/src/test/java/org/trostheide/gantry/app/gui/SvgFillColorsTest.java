package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SvgFillColorsTest {
    @TempDir
    Path tmp;

    @Test
    void findsAttributeAndInlineStyleHexFills() throws Exception {
        Path svg = tmp.resolve("colors.svg");
        Files.writeString(svg, """
                <svg xmlns="http://www.w3.org/2000/svg">
                  <rect fill="#FF0000"/>
                  <circle style="stroke:#000; fill: #00ff00; opacity:1"/>
                  <path fill="none"/>
                  <g fill="rgb(0, 0, 255)"><path d="M0 0L1 1"/></g>
                  <g fill="#123456"><path fill="none" d="M0 0L1 1"/></g>
                  <path fill="#abc"/>
                  <path fill="orange"/>
                </svg>
                """);

        assertEquals(Set.of("#ff0000", "#00ff00", "#0000ff", "#123456", "#aabbcc", "#ffa500"),
                SvgFillColors.read(svg.toFile()));
    }
}
