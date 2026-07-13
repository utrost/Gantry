package org.trostheide.gantry.app.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/** Compact, non-blocking feedback shown beside the workflow instead of only in the Console. */
final class LocalFeedbackPanel extends JPanel {
    enum Tone { INFO, SUCCESS, ERROR }

    private static final Color INFO = new Color(21, 101, 192);
    private static final Color SUCCESS = new Color(46, 125, 50);
    private static final Color ERROR = new Color(198, 40, 40);

    private final JLabel message = new JLabel();
    private final JButton action = new JButton();
    private final Timer dismissTimer;

    LocalFeedbackPanel() { this(15_000); }

    LocalFeedbackPanel(int dismissDelayMs) {
        super(new BorderLayout(8, 0));
        setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 4));
        message.getAccessibleContext().setAccessibleName("Latest Gantry status");
        add(message, BorderLayout.CENTER);

        JButton close = new JButton("×");
        close.setToolTipText("Dismiss this message");
        close.setMargin(new Insets(0, 4, 0, 4));
        close.addActionListener(e -> dismiss());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.setOpaque(false);
        buttons.add(action);
        buttons.add(close);
        add(buttons, BorderLayout.EAST);

        dismissTimer = new Timer(dismissDelayMs, e -> dismiss());
        dismissTimer.setRepeats(false);
        setVisible(false);
    }

    void showMessage(String text, Tone tone) { showAction(text, tone, null, null); }

    void showAction(String text, Tone tone, String actionLabel, Runnable callback) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> showAction(text, tone, actionLabel, callback));
            return;
        }
        message.setText(text);
        message.getAccessibleContext().setAccessibleDescription(text);
        message.setForeground(color(tone));
        for (ActionListener listener : action.getActionListeners()) action.removeActionListener(listener);
        boolean hasAction = actionLabel != null && callback != null;
        action.setVisible(hasAction);
        if (hasAction) {
            action.setText(actionLabel);
            action.getAccessibleContext().setAccessibleName(actionLabel + ": " + text);
            action.addActionListener(e -> { dismiss(); callback.run(); });
        }
        setVisible(true);
        dismissTimer.restart();
    }

    String messageText() { return message.getText(); }
    JButton actionButton() { return action; }

    void dismiss() {
        dismissTimer.stop();
        setVisible(false);
    }

    private static Color color(Tone tone) {
        return switch (tone) { case INFO -> INFO; case SUCCESS -> SUCCESS; case ERROR -> ERROR; };
    }
}
