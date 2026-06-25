package org.trostheide.gantry.app.gui;

import javax.swing.JComponent;

/**
 * A {@link WizardStep} that just shows a supplied component under a given title, with no gating —
 * used to host existing panels (e.g. the {@link SettingsPanel} sections re-parented into the Setup
 * Wizard) without writing a one-off step class for each.
 */
final class PanelStep implements WizardStep {

    private final String title;
    private final JComponent panel;
    private final boolean optional;
    private final Runnable onEnter;

    PanelStep(String title, JComponent panel) {
        this(title, panel, false, null);
    }

    PanelStep(String title, JComponent panel, boolean optional, Runnable onEnter) {
        this.title = title;
        this.panel = panel;
        this.optional = optional;
        this.onEnter = onEnter;
    }

    @Override public String title() { return title; }
    @Override public JComponent panel() { return panel; }
    @Override public boolean isOptional() { return optional; }

    @Override public void onEnter() {
        if (onEnter != null) {
            onEnter.run();
        }
    }
}
