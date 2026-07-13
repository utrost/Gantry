package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedControlsDisclosureTest {
    @Test
    void expertSectionsAreHiddenByDefaultAndShareOneDisclosure() throws Exception {
        JPanel manual = new JPanel();
        JPanel diagnostics = new JPanel();
        AdvancedControlsDisclosure[] disclosure = new AdvancedControlsDisclosure[1];

        SwingUtilities.invokeAndWait(() -> disclosure[0] =
                new AdvancedControlsDisclosure(List.of(manual, diagnostics)));
        assertFalse(disclosure[0].isExpanded());
        assertFalse(manual.isVisible());
        assertFalse(diagnostics.isVisible());

        SwingUtilities.invokeAndWait(() -> disclosure[0].setExpanded(true));
        assertTrue(disclosure[0].isExpanded());
        assertTrue(manual.isVisible());
        assertTrue(diagnostics.isVisible());
    }
}
