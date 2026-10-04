package org.trostheide.gantry.app.gui;

import javax.swing.*;
import java.awt.*;

/** Keeps controls reachable when a small window or larger fonts need scrolling. */
final class ControlSidebar extends JPanel implements Scrollable {
    ControlSidebar() { setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(16, getFontMetrics(getFont()).getHeight());
    }
    @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(16, (orientation == SwingConstants.VERTICAL ? visible.height : visible.width) - 24);
    }
    @Override public boolean getScrollableTracksViewportWidth() {
        return getParent() instanceof JViewport viewport && viewport.getWidth() >= getMinimumSize().width;
    }
    @Override public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport viewport && viewport.getHeight() >= getPreferredSize().height;
    }
}
