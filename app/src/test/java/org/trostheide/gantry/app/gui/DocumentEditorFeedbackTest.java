package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.app.session.DocumentSession;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentEditorFeedbackTest {
    @Test
    void editOffersContextualUndoAndUndoConfirmsLocally() {
        DocumentSession session = new DocumentSession();
        List<String> edits = new ArrayList<>();
        List<String> notices = new ArrayList<>();
        DocumentEditor editor = new DocumentEditor(session, new JPanel(), () -> { }, ignored -> { },
                ignored -> { }, ignored -> { }, new DocumentEditor.Feedback() {
                    public void edited(String message) { edits.add(message); }
                    public void notice(String message) { notices.add(message); }
                });
        editor.replace(PracticeArtwork.create());

        editor.delete(1);

        assertEquals(List.of("Line deleted."), edits);
        assertTrue(session.canUndo());
        editor.undo();
        assertEquals(List.of("Last edit undone."), notices);
        assertEquals(5, session.currentOutput().layers().get(0).commands().size());
    }
}
