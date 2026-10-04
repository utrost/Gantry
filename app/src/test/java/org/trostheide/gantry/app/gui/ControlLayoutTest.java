package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.ProcessorOutput;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ControlLayoutTest {
    @Test void longLayerNamesDoNotHideEditButtonsOrPlotActions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (float scale : new float[]{1f, 1.5f, 2f}) {
                PlotControlsPanel panel = new PlotControlsPanel(new PlotControlsPanel.Actions(
                        ()->{}, ()->{}, ()->{}, ()->{}, ()->{}, ()->{}, ()->{}, value->{}, index->{}));
                panel.rebuild(new ProcessorOutput(null, List.of(new Layer(
                        "A very long imported SVG layer name ".repeat(5), "", "#ff0000", List.of()))),
                        new VisualizationPanel(), false);
                scaleFonts(panel, scale);
                panel.setSize(Math.max(300, panel.getMinimumSize().width), panel.getPreferredSize().height);
                layout(panel);
                for (String label : List.of("Edit…", "Start plotting", "Check before plotting...", "Stop")) {
                    JButton button = findButton(panel, label);
                    assertNotNull(button, label);
                    Rectangle bounds = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), panel);
                    assertTrue(new Rectangle(panel.getSize()).contains(bounds), label + " clipped at " + scale + ": " + bounds);
                    assertTrue(button.getWidth() >= button.getPreferredSize().width, label + " truncated");
                }
            }
        });
    }

    @Test void smallViewportScrollsInsteadOfCompressingControls() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ControlSidebar sidebar = new ControlSidebar();
            JButton button = new JButton("Controls requiring room");
            button.setMinimumSize(new Dimension(350, 80));
            button.setPreferredSize(new Dimension(350, 900));
            sidebar.add(button);
            JViewport viewport = new JViewport();
            viewport.setView(sidebar);
            viewport.setSize(250, 400);
            assertFalse(sidebar.getScrollableTracksViewportWidth());
            assertFalse(sidebar.getScrollableTracksViewportHeight());
            viewport.setSize(500, 1000);
            assertTrue(sidebar.getScrollableTracksViewportWidth());
            assertTrue(sidebar.getScrollableTracksViewportHeight());
        });
    }

    private static void scaleFonts(Component component, float scale) {
        if (component.getFont() != null) component.setFont(component.getFont().deriveFont(component.getFont().getSize2D() * scale));
        if (component instanceof Container container) for (Component child : container.getComponents()) scaleFonts(child, scale);
    }
    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container nested) layout(nested);
    }
    private static JButton findButton(Container container, String text) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) return button;
            if (child instanceof Container nested) { JButton result = findButton(nested, text); if (result != null) return result; }
        }
        return null;
    }
}
