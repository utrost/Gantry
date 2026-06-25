package org.trostheide.gantry.app.gui;

import javax.swing.JComponent;

/**
 * One step in a {@link WizardDialog}. The shell knows nothing about plotting or hardware — it
 * just shows {@link #panel()}, gates {@code Next} on {@link #canAdvance()}, and calls the
 * enter/leave hooks so a step can kick off an action (e.g. "send $H and wait") when it's shown.
 */
public interface WizardStep {

    /** Short label shown in the progress trail, e.g. "Connect" or "Frame the job". */
    String title();

    /** The step's UI, built once and reused for the life of the wizard. */
    JComponent panel();

    /** Whether {@code Next}/{@code Finish} should be enabled right now. */
    default boolean canAdvance() {
        return true;
    }

    /** True if this step may be bypassed via a {@code Skip} button instead of completing it. */
    default boolean isOptional() {
        return false;
    }

    /** Called right before the step becomes visible. */
    default void onEnter() {
    }

    /** Called right before navigating away from this step (Next, Back, or Skip). */
    default void onLeave() {
    }
}
