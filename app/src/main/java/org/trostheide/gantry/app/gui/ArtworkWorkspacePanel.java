package org.trostheide.gantry.app.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/** A session-dismissible hint; hiding it never changes the document. */
final class ArtworkWorkspacePanel extends JLayeredPane {
    private final JComponent canvas;
    private final JPanel emptyState = new JPanel(new BorderLayout(0, 12));
    private boolean dismissed;

    ArtworkWorkspacePanel(JComponent canvas, Consumer<Component> browse) {
        this.canvas = canvas;
        emptyState.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JLabel title = new JLabel("Your next drawing starts here");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        emptyState.add(title, BorderLayout.NORTH);
        emptyState.add(new JLabel("Browse for artwork or open a Gantry project."), BorderLayout.CENTER);
        JButton open = WorkspaceStyle.button("Browse…", () -> { });
        open.addActionListener(e -> browse.accept(open));
        JButton close = WorkspaceStyle.button("Close", this::dismiss);
        close.getAccessibleContext().setAccessibleDescription("Hide this hint and return to the empty canvas");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        actions.add(open); actions.add(close); emptyState.add(actions, BorderLayout.SOUTH);
        emptyState.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "dismiss");
        emptyState.getActionMap().put("dismiss", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { dismiss(); }
        });
        add(canvas, DEFAULT_LAYER); add(emptyState, PALETTE_LAYER);
    }
    void dismiss() { dismissed = true; emptyState.setVisible(false); canvas.requestFocusInWindow(); repaint(); }
    void setHasArtwork(boolean hasArtwork) { emptyState.setVisible(!hasArtwork && !dismissed); repaint(); }
    boolean isShowingEmptyState() { return emptyState.isVisible(); }
    @Override public void doLayout() {
        canvas.setBounds(0, 0, getWidth(), getHeight());
        Dimension size = emptyState.getPreferredSize();
        int width = Math.min(size.width, Math.max(0, getWidth() - 24));
        emptyState.setBounds((getWidth() - width) / 2, Math.max(12, (getHeight() - size.height) / 2), width, size.height);
    }
}
