package org.trostheide.gantry.app.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseAdapter;

/**
 * A translucent modal "busy" overlay for blocking background work. Installed as a window's glass
 * pane, it dims the window, swallows input, and shows a centred card with the operation's name and
 * an <em>animated</em> indeterminate progress bar — so a slow step (e.g. image→SVG vectorization)
 * is unmistakably in progress rather than a silent wait-cursor the user can overlook.
 *
 * <p>Use {@link #show(Component, String)} to display it over a component's window and {@link #hide()}
 * when the work completes.
 */
final class BusyOverlay extends JPanel {

    private final JLabel label = new JLabel();

    private BusyOverlay(Runnable cancelAction) {
        setOpaque(false);
        setLayout(new GridBagLayout());
        // Swallow clicks/keys so the dimmed UI underneath can't be operated while we're busy.
        addMouseListener(new MouseAdapter() { });
        addKeyListener(new KeyAdapter() { });

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(170, 170, 170)),
                BorderFactory.createEmptyBorder(20, 32, 20, 32)));

        label.setAlignmentX(CENTER_ALIGNMENT);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 15f));

        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setAlignmentX(CENTER_ALIGNMENT);
        Dimension barSize = new Dimension(240, 18);
        bar.setPreferredSize(barSize);
        bar.setMaximumSize(barSize);

        card.add(label);
        card.add(Box.createVerticalStrut(14));
        card.add(bar);
        if (cancelAction != null) {
            card.add(Box.createVerticalStrut(12));
            JButton cancel = new JButton("Cancel");
            cancel.setAlignmentX(CENTER_ALIGNMENT);
            cancel.getAccessibleContext().setAccessibleDescription(
                    "Cancel the current background operation without changing the artwork");
            cancel.addActionListener(e -> {
                cancel.setEnabled(false);
                cancel.setText("Cancelling…");
                label.setText("Cancelling…");
                cancelAction.run();
            });
            card.add(cancel);
        }
        add(card);
    }

    private void setMessage(String message) {
        label.setText(message);
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(new Color(0, 0, 0, 90));
        g.fillRect(0, 0, getWidth(), getHeight());
        super.paintComponent(g);
    }

    /**
     * Shows a busy overlay over the window hosting {@code anchor}. Returns the overlay so the caller
     * can {@link #dismiss()} it, or {@code null} if {@code anchor} has no usable window (nothing to do).
     */
    static BusyOverlay show(Component anchor, String message) {
        return show(anchor, message, null);
    }

    /** Shows a busy overlay whose Cancel button invokes {@code cancelAction}, when supplied. */
    static BusyOverlay show(Component anchor, String message, Runnable cancelAction) {
        Window w = SwingUtilities.getWindowAncestor(anchor);
        if (!(w instanceof RootPaneContainer rpc)) {
            return null;
        }
        BusyOverlay overlay = create(message, cancelAction);
        overlay.previousGlass = rpc.getGlassPane();
        overlay.host = rpc;
        rpc.setGlassPane(overlay);
        overlay.setVisible(true);
        return overlay;
    }

    /** Creates an unattached overlay for component-level tests. */
    static BusyOverlay create(String message, Runnable cancelAction) {
        BusyOverlay overlay = new BusyOverlay(cancelAction);
        overlay.setMessage(message);
        return overlay;
    }

    private RootPaneContainer host;
    private Component previousGlass;

    /** Removes the overlay and restores the window's previous glass pane. */
    void dismiss() {
        setVisible(false);
        if (host != null && previousGlass != null) {
            host.setGlassPane(previousGlass);
        }
    }
}
