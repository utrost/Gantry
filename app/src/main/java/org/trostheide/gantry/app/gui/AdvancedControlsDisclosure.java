package org.trostheide.gantry.app.gui;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/** One disclosure controlling the visibility of expert-only control-column sections. */
final class AdvancedControlsDisclosure extends JPanel {
    private final JToggleButton toggle = new JToggleButton("Advanced controls ▸");
    private final List<? extends JComponent> advancedSections;

    AdvancedControlsDisclosure(List<? extends JComponent> advancedSections) {
        this.advancedSections = List.copyOf(advancedSections);
        setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        toggle.setToolTipText("Show manual movement, raw G-code, and the diagnostic Console");
        toggle.getAccessibleContext().setAccessibleDescription(
                "Show or hide manual movement, raw G-code, and diagnostic controls");
        toggle.addActionListener(e -> setExpanded(toggle.isSelected()));
        add(toggle);
        setExpanded(false);
    }

    void setExpanded(boolean expanded) {
        toggle.setSelected(expanded);
        toggle.setText(expanded ? "Advanced controls ▾" : "Advanced controls ▸");
        for (JComponent section : advancedSections) section.setVisible(expanded);
        Container parent = getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    boolean isExpanded() { return toggle.isSelected(); }
}
