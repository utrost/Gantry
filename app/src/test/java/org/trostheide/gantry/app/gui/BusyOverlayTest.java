package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusyOverlayTest {
    @Test
    void cancellableOverlayShowsProgressAndInvokesCancelOnce() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        BusyOverlay[] overlay = new BusyOverlay[1];
        SwingUtilities.invokeAndWait(() -> overlay[0] =
                BusyOverlay.create("Optimizing artwork…", () -> cancelled.set(true)));

        JProgressBar progress = descendants(overlay[0], JProgressBar.class).get(0);
        JButton cancel = descendants(overlay[0], JButton.class).stream()
                .filter(button -> "Cancel".equals(button.getText()))
                .findFirst().orElseThrow();

        assertTrue(progress.isIndeterminate());
        assertFalse(cancelled.get());
        SwingUtilities.invokeAndWait(cancel::doClick);
        assertTrue(cancelled.get());
        assertFalse(cancel.isEnabled());
        assertEquals("Cancelling…", cancel.getText());
    }

    private static <T extends Component> List<T> descendants(Container root, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component component : root.getComponents()) {
            if (type.isInstance(component)) result.add(type.cast(component));
            if (component instanceof Container child) result.addAll(descendants(child, type));
        }
        return result;
    }
}
