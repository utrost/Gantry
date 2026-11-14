package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LocalFeedbackPanelTest {
    @Test void retainedFeedbackAdaptsToRuntimeThemeChanges() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LookAndFeel previous = UIManager.getLookAndFeel();
            try {
                com.formdev.flatlaf.FlatDarkLaf.setup();
                LocalFeedbackPanel panel = new LocalFeedbackPanel(60_000);
                panel.showMessage("Machine failed", LocalFeedbackPanel.Tone.ERROR);
                panel.showMessage("Saved", LocalFeedbackPanel.Tone.SUCCESS);
                java.awt.Color dark = panel.getComponent(0).getForeground();
                com.formdev.flatlaf.FlatLightLaf.setup();
                SwingUtilities.updateComponentTreeUI(panel);
                assertNotEquals(dark, panel.getComponent(0).getForeground());
                assertEquals("Saved", panel.messageText());
                assertTrue(panel.unresolvedErrorButton().isVisible());
                panel.unresolvedErrorButton().doClick();
                assertEquals("Machine failed", panel.messageText());
                panel.dismiss();
            } finally {
                try { UIManager.setLookAndFeel(previous); }
                catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            }
        });
    }

    @Test void unresolvedErrorSurvivesSuccessAndTimeout() throws Exception {
        LocalFeedbackPanel[] panel=new LocalFeedbackPanel[1];
        SwingUtilities.invokeAndWait(()->{
            panel[0]=new LocalFeedbackPanel(10);
            panel[0].showMessage("Machine failed",LocalFeedbackPanel.Tone.ERROR);
            panel[0].showMessage("Saved",LocalFeedbackPanel.Tone.SUCCESS);
            assertEquals("Saved", panel[0].messageText(), "export/save results must remain visible even after an error");
            assertTrue(panel[0].unresolvedErrorButton().isVisible());
            assertEquals("Machine failed", panel[0].unresolvedErrorButton().getToolTipText());
        });
        Thread.sleep(60);
        SwingUtilities.invokeAndWait(()->{
            assertTrue(panel[0].isVisible());assertEquals("Machine failed",panel[0].messageText());
            panel[0].dismiss();panel[0].showMessage("Saved",LocalFeedbackPanel.Tone.SUCCESS);
            assertEquals("Saved",panel[0].messageText());panel[0].dismiss();
        });
    }
    @Test void inspectingErrorDetailsDoesNotDismissTheError() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LocalFeedbackPanel panel = new LocalFeedbackPanel(60_000);
            AtomicBoolean detailsOpened = new AtomicBoolean();
            panel.showAction("Machine failed", LocalFeedbackPanel.Tone.ERROR, "Details", () -> detailsOpened.set(true));
            panel.actionButton().doClick();
            assertTrue(detailsOpened.get());
            assertTrue(panel.isVisible());
            panel.showMessage("Export complete", LocalFeedbackPanel.Tone.SUCCESS);
            panel.unresolvedErrorButton().doClick();
            assertEquals("Machine failed", panel.messageText());
            assertFalse(panel.unresolvedErrorButton().isVisible());
            panel.dismiss();
            assertFalse(panel.isVisible());
        });
    }

    @Test void longMessagesHaveKeyboardAccessibleDetails() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LocalFeedbackPanel panel = new LocalFeedbackPanel(60_000);
            panel.showMessage("Exported: " + "long-folder-name/".repeat(20), LocalFeedbackPanel.Tone.SUCCESS);
            assertTrue(panel.actionButton().isVisible());
            assertEquals("Details", panel.actionButton().getText());
            panel.dismiss();
        });
    }

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
