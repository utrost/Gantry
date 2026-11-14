package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceNoteLayoutTest {
    @Test void changedWarningsCanGrowToShowTheirFullWrappedText() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextArea note = WorkspaceStyle.note("");
            note.setSize(280, 100);
            int initial = note.getMaximumSize().height;
            note.setText("Prepared bounds: X -90.0–210.0, Y -12.0–148.0 mm. Some paths will be clipped to the bed. Return to artwork to resize or reposition before plotting.");
            assertTrue(note.getPreferredSize().height > initial);
            assertEquals(note.getPreferredSize().height, note.getMaximumSize().height,
                    "BoxLayout must not keep the two-line height measured before a warning arrives");
            note.setMaximumSize(new Dimension(500, 70));
            assertEquals(70, note.getMaximumSize().height, "explicit size limits still work");
        });
    }
}
