package org.trostheide.gantry.app.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Live View wrapper that offers obvious artwork entry points while the canvas is empty. */
final class ArtworkWorkspacePanel extends JLayeredPane {
    record Actions(Runnable addSvg, Runnable addImage, Runnable openProject) { }

    private final JComponent canvas;
    private final JPanel emptyState = new JPanel(new GridBagLayout());

    ArtworkWorkspacePanel(JComponent canvas, Actions actions) {
        this.canvas = canvas;
        emptyState.setOpaque(false);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        Color borderColor = UIManager.getColor("Component.borderColor");
        if (borderColor == null) borderColor = UIManager.getColor("Separator.foreground");
        if (borderColor == null) borderColor = Color.GRAY;
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor),
                new EmptyBorder(18, 22, 18, 22)));

        JLabel title = new JLabel("Add artwork");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel explanation = new JLabel("Prepare a drawing first — nothing will move yet.");
        explanation.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton svg = action("Add SVG or vector drawing", actions.addSvg(),
                "Choose an SVG vector drawing and fit it safely to the machine bed");
        JButton image = action("Add image or photo", actions.addImage(),
                "Choose a raster image and convert it to plottable lines");
        JButton project = action("Open Gantry project", actions.openProject(),
                "Continue an existing editable Gantry project");

        card.add(title);
        card.add(Box.createVerticalStrut(5));
        card.add(explanation);
        card.add(Box.createVerticalStrut(14));
        for (JButton button : new JButton[] {svg, image, project}) {
            button.setAlignmentX(Component.CENTER_ALIGNMENT);
            button.setMaximumSize(new Dimension(280, button.getPreferredSize().height));
            card.add(button);
            card.add(Box.createVerticalStrut(7));
        }
        emptyState.add(card);

        add(canvas, JLayeredPane.DEFAULT_LAYER);
        add(emptyState, JLayeredPane.PALETTE_LAYER);
        setHasArtwork(false);
    }

    void setHasArtwork(boolean hasArtwork) {
        emptyState.setVisible(!hasArtwork);
        emptyState.setEnabled(!hasArtwork);
        repaint();
    }

    boolean isShowingEmptyState() {
        return emptyState.isVisible();
    }

    @Override
    public void doLayout() {
        canvas.setBounds(0, 0, getWidth(), getHeight());
        emptyState.setBounds(0, 0, getWidth(), getHeight());
    }

    private static JButton action(String label, Runnable action, String accessibleDescription) {
        JButton button = new JButton(label);
        button.addActionListener(e -> action.run());
        button.getAccessibleContext().setAccessibleDescription(accessibleDescription);
        return button;
    }
}
