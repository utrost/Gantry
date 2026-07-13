package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtworkWorkspacePanelTest {
    @Test
    void emptyStateOffersAllThreeBeginnerEntryPoints() throws Exception {
        AtomicInteger svg = new AtomicInteger();
        AtomicInteger image = new AtomicInteger();
        AtomicInteger project = new AtomicInteger();
        ArtworkWorkspacePanel[] holder = new ArtworkWorkspacePanel[1];

        SwingUtilities.invokeAndWait(() -> holder[0] = new ArtworkWorkspacePanel(new JPanel(),
                new ArtworkWorkspacePanel.Actions(svg::incrementAndGet,
                        image::incrementAndGet, project::incrementAndGet)));

        ArtworkWorkspacePanel panel = holder[0];
        assertTrue(panel.isShowingEmptyState());
        click(panel, "Add SVG or vector drawing");
        click(panel, "Add image or photo");
        click(panel, "Open Gantry project");
        assertTrue(svg.get() == 1 && image.get() == 1 && project.get() == 1);

        SwingUtilities.invokeAndWait(() -> panel.setHasArtwork(true));
        assertFalse(panel.isShowingEmptyState());
    }

    private static void click(Container root, String text) throws Exception {
        JButton button = find(root, text);
        assertNotNull(button, "missing button " + text);
        SwingUtilities.invokeAndWait(button::doClick);
    }

    private static JButton find(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) return button;
            if (child instanceof Container nested) {
                JButton found = find(nested, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
