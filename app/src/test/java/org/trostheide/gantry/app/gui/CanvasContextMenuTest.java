package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.app.session.CompositionArtwork;
import org.trostheide.gantry.model.Bounds;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CanvasContextMenuTest {
    @Test
    void addArtworkRoutesToConfiguredAction() throws Exception {
        VisualizationPanel panel = new VisualizationPanel();
        AtomicInteger calls = new AtomicInteger();
        panel.setAddArtworkAction(calls::incrementAndGet);

        SwingUtilities.invokeAndWait(() -> {
            JMenuItem addArtwork = item(panel, "Add artwork...");
            assertNotNull(addArtwork);
            addArtwork.doClick();
        });

        assertEquals(1, calls.get());
    }

    @Test
    void selectedArtworkRoutesToIndependentTransformAction() throws Exception {
        VisualizationPanel panel = new VisualizationPanel();
        CompositionArtwork artwork = new CompositionArtwork("mark", "Registration mark", null,
                List.of(1), new Bounds(0, 0, 10, 10), new Bounds(30, 5, 40, 15),
                new CompositionArtwork.Transform(30, 5, 1, false), null, null);
        panel.setArtworks(List.of(artwork));
        panel.selectedArtworkId = artwork.id();
        AtomicReference<String> transformed = new AtomicReference<>();
        panel.setArtworkInteractionListener(new VisualizationPanel.ArtworkInteractionListener() {
            public void onArtworkMoved(String artworkId, double dx, double dy) { }
            public void onTransformArtwork(String artworkId) { transformed.set(artworkId); }
        });

        SwingUtilities.invokeAndWait(() -> {
            JMenuItem transform = item(panel, "Transform selected artwork...");
            assertNotNull(transform);
            transform.doClick();
        });

        assertEquals("mark", transformed.get());
    }

    private static JMenuItem item(VisualizationPanel panel, String label) {
        JPopupMenu menu = CanvasContextMenu.build(panel);
        for (java.awt.Component component : menu.getComponents()) {
            if (component instanceof JMenuItem item && label.equals(item.getText())) return item;
        }
        return null;
    }
}
