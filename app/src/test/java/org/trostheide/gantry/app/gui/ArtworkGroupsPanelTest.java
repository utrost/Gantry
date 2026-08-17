package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.app.session.CompositionArtwork;
import org.trostheide.gantry.model.Bounds;
import org.trostheide.gantry.pipeline.svgimport.SvgImportOptions;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtworkGroupsPanelTest {
    @Test
    void showsArtworkSummaryAndRoutesSelectedActions() throws Exception {
        CompositionArtwork base = artwork("a", "Base", null, false);
        CompositionArtwork mark = artwork("b", "Registration", "/tmp/reg.svg", true);
        AtomicReference<String> transformed = new AtomicReference<>();
        AtomicReference<String> reprocessed = new AtomicReference<>();
        AtomicReference<String> renamed = new AtomicReference<>();
        ArtworkGroupsPanel[] holder = new ArtworkGroupsPanel[1];

        SwingUtilities.invokeAndWait(() -> holder[0] = new ArtworkGroupsPanel(List.of(base, mark),
                new ArtworkGroupsPanel.Actions(
                        artwork -> transformed.set(artwork.id()),
                        artwork -> reprocessed.set(artwork.id()),
                        artwork -> renamed.set(artwork.id()))));

        ArtworkGroupsPanel panel = holder[0];
        assertTrue(text(panel).contains("Base"));
        assertTrue(text(panel).contains("Registration"));
        assertTrue(text(panel).contains("10.0 × 15.0 mm"));
        assertTrue(text(panel).contains("No saved SVG source"));

        SwingUtilities.invokeAndWait(() -> panel.selectArtwork("b"));
        assertTrue(text(panel).contains("Can re-process"));
        click(panel, "Transform...");
        click(panel, "Re-process...");
        click(panel, "Rename...");

        assertEquals("b", transformed.get());
        assertEquals("b", reprocessed.get());
        assertEquals("b", renamed.get());

        SwingUtilities.invokeAndWait(() -> panel.selectArtwork("a"));
        assertFalse(button(panel, "Re-process...").isEnabled(), "reprocess should be disabled without SVG provenance");
    }

    private static CompositionArtwork artwork(String id, String label, String sourcePath, boolean provenance) {
        SvgImportOptions options = provenance ? SvgImportOptions.defaults() : null;
        return new CompositionArtwork(id, label, sourcePath, List.of(0), new Bounds(0, 0, 10, 15),
                new Bounds(5, 6, 15, 21), new CompositionArtwork.Transform(5, 6, 1.0, false), options, null);
    }

    private static void click(Container root, String text) throws Exception {
        JButton button = button(root, text);
        assertNotNull(button, "missing button " + text);
        SwingUtilities.invokeAndWait(button::doClick);
    }

    private static JButton button(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) return button;
            if (child instanceof Container nested) {
                JButton found = button(nested, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String text(Container root) {
        StringBuilder result = new StringBuilder();
        collectText(root, result);
        return result.toString();
    }

    private static void collectText(Container root, StringBuilder result) {
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) result.append(label.getText()).append('\n');
            if (child instanceof Container nested) collectText(nested, result);
        }
    }
}
