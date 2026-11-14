package org.trostheide.gantry.app.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/** Non-blocking feedback; transient results never erase an unresolved error. */
final class LocalFeedbackPanel extends JPanel {
    enum Tone { INFO, SUCCESS, ERROR }
    private record Notice(String text, Tone tone, String actionLabel, Runnable callback) { }

    private static final Color INFO = new Color(21, 101, 192);
    private static final Color SUCCESS = new Color(46, 125, 50);
    private static final Color ERROR = new Color(198, 40, 40);
    private final JLabel message = new JLabel();
    private final JButton action = new JButton();
    private final JButton previousError = new JButton("Unresolved error");
    private final Timer dismissTimer;
    private Notice current;
    private Notice unresolvedError;

    LocalFeedbackPanel() { this(15_000); }

    LocalFeedbackPanel(int dismissDelayMs) {
        super(new BorderLayout(8, 0));
        setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 4));
        message.setMinimumSize(new Dimension(0, 0));
        message.getAccessibleContext().setAccessibleName("Latest Gantry status");
        add(message, BorderLayout.CENTER);
        JButton close = new JButton("×");
        close.setToolTipText("Dismiss this message");
        close.getAccessibleContext().setAccessibleName("Dismiss this message");
        close.setMargin(new Insets(0, 4, 0, 4));
        close.addActionListener(e -> dismiss());
        previousError.addActionListener(e -> { if (unresolvedError != null) render(unresolvedError); });
        previousError.setVisible(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.setOpaque(false);
        buttons.add(previousError); buttons.add(action); buttons.add(close);
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
        Notice notice = new Notice(text, tone, actionLabel, callback);
        if (tone == Tone.ERROR) unresolvedError = notice;
        render(notice);
    }

    private void render(Notice notice) {
        current = notice;
        message.setText(notice.text());
        message.setToolTipText(notice.text());
        message.getAccessibleContext().setAccessibleDescription(notice.text());
        message.setForeground(color(notice.tone()));
        previousError.setVisible(unresolvedError != null && current != unresolvedError);
        previousError.setForeground(color(Tone.ERROR));
        previousError.setToolTipText(unresolvedError == null ? null : unresolvedError.text());
        previousError.getAccessibleContext().setAccessibleDescription(unresolvedError == null ? null : unresolvedError.text());
        for (ActionListener listener : action.getActionListeners()) action.removeActionListener(listener);
        boolean hasAction = notice.actionLabel() != null && notice.callback() != null;
        boolean details = !hasAction && (notice.tone() == Tone.ERROR || notice.text().length() > 100);
        action.setVisible(hasAction || details);
        if (hasAction || details) {
            String label = hasAction ? notice.actionLabel() : "Details";
            action.setText(label);
            action.getAccessibleContext().setAccessibleName(label + ": " + notice.text());
            action.addActionListener(e -> {
                // Inspecting an error is not the same as resolving/dismissing it.
                if (notice.tone() != Tone.ERROR) dismiss();
                if (hasAction) notice.callback().run();
                else showDetails(notice.text());
            });
        }
        setVisible(true);
        dismissTimer.stop();
        if (notice.tone() != Tone.ERROR) dismissTimer.restart();
        revalidate(); repaint();
    }

    @Override public void updateUI() {
        super.updateUI();
        // Preserve message lifetime and ownership while adapting retained feedback to a new theme.
        if (current != null) message.setForeground(color(current.tone()));
        if (previousError != null) previousError.setForeground(color(Tone.ERROR));
    }

    private void showDetails(String text) {
        JTextArea detail = new JTextArea(text, 8, 48);
        detail.setEditable(false); detail.setLineWrap(true); detail.setWrapStyleWord(true);
        detail.setCaretPosition(0); detail.getAccessibleContext().setAccessibleName("Message details");
        JOptionPane.showMessageDialog(this, new JScrollPane(detail), "Gantry message", JOptionPane.PLAIN_MESSAGE);
    }

    String messageText() { return message.getText(); }
    JButton actionButton() { return action; }
    JButton unresolvedErrorButton() { return previousError; }

    void dismiss() {
        dismissTimer.stop();
        if (current == unresolvedError) unresolvedError = null;
        if (unresolvedError != null) render(unresolvedError);
        else { current = null; previousError.setVisible(false); setVisible(false); }
    }

    private static Color color(Tone tone) {
        if (com.formdev.flatlaf.FlatLaf.isLafDark()) return switch (tone) {
            case INFO -> new Color(130,185,255); case SUCCESS -> new Color(130,215,150); case ERROR -> new Color(255,150,145);
        };
        return switch (tone) { case INFO -> INFO; case SUCCESS -> SUCCESS; case ERROR -> ERROR; };
    }
}
