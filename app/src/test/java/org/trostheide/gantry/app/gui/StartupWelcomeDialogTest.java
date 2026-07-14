package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupWelcomeDialogTest {

    @Test
    void freshInstallShowsWelcomeEvenBeforePreferenceExists() {
        assertTrue(PlotterPanel.shouldShowStartupWelcome(true, false));
        assertTrue(PlotterPanel.shouldShowStartupWelcome(true, true));
    }

    @Test
    void savedPreferenceControlsLaterStarts() {
        assertFalse(PlotterPanel.shouldShowStartupWelcome(false, false));
        assertTrue(PlotterPanel.shouldShowStartupWelcome(false, true));
    }

    @Test
    void closeButtonAndWindowCloseAreBothDismissalsAndKeepCheckboxValue() {
        StartupWelcomeDialog.Result closeButton = StartupWelcomeDialog.resultForChoice(2, false);
        StartupWelcomeDialog.Result windowClose = StartupWelcomeDialog.resultForChoice(-1, true);

        assertEquals(StartupWelcomeDialog.Action.DISMISSED, closeButton.action());
        assertFalse(closeButton.showOnStartup());
        assertEquals(StartupWelcomeDialog.Action.DISMISSED, windowClose.action());
        assertTrue(windowClose.showOnStartup());
    }

    @Test
    void primaryChoicesMapToExpectedActions() {
        assertEquals(StartupWelcomeDialog.Action.GUIDED_PRACTICE,
                StartupWelcomeDialog.resultForChoice(0, true).action());
        assertEquals(StartupWelcomeDialog.Action.MACHINE_SETUP,
                StartupWelcomeDialog.resultForChoice(1, true).action());
    }
}
