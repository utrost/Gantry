package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LocalFeedbackPanelTest {
    @Test
    void actionFeedbackIsVisibleAccessibleAndInvokesTheExistingAction() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        LocalFeedbackPanel[] holder = new LocalFeedbackPanel[1];

        SwingUtilities.invokeAndWait(() -> {
            LocalFeedbackPanel panel = new LocalFeedbackPanel(60_000);
            panel.showAction("Line added.", LocalFeedbackPanel.Tone.SUCCESS, "Undo",
                    () -> invoked.set(true));
            holder[0] = panel;
        });

        LocalFeedbackPanel panel = holder[0];
        assertTrue(panel.isVisible());
        assertEquals("Line added.", panel.messageText());
        assertEquals("Line added.", panel.getComponent(0).getAccessibleContext().getAccessibleDescription());
        assertEquals("Undo", panel.actionButton().getText());
        assertTrue(panel.actionButton().getAccessibleContext().getAccessibleName().contains("Line added"));

        SwingUtilities.invokeAndWait(panel.actionButton()::doClick);
        assertTrue(invoked.get());
        assertFalse(panel.isVisible());
    }

    @Test
    void messageWithoutActionDoesNotLeaveAStaleButton() throws Exception {
        LocalFeedbackPanel[] holder = new LocalFeedbackPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            LocalFeedbackPanel panel = new LocalFeedbackPanel(60_000);
            panel.showAction("Editable", LocalFeedbackPanel.Tone.SUCCESS, "Undo", () -> { });
            panel.showMessage("Plotter disconnected.", LocalFeedbackPanel.Tone.INFO);
            holder[0] = panel;
        });
        assertFalse(holder[0].actionButton().isVisible());
        assertEquals("Plotter disconnected.", holder[0].messageText());
    }
}
