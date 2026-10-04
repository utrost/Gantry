package org.trostheide.gantry.app.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DocumentReplacementGuardTest {
    @Test void cleanDoesNotPromptOrSave() {
        assertTrue(DocumentReplacementGuard.allow(false, () -> {throw new AssertionError();}, () -> {throw new AssertionError();}));
    }
    @Test void cancellationAndDiscardDoNotSave() {
        assertFalse(DocumentReplacementGuard.allow(true, () -> DocumentReplacementGuard.Choice.CANCEL, () -> {throw new AssertionError();}));
        assertTrue(DocumentReplacementGuard.allow(true, () -> DocumentReplacementGuard.Choice.DISCARD, () -> {throw new AssertionError();}));
    }
    @Test void saveMustSucceed() {
        assertFalse(DocumentReplacementGuard.allow(true, () -> DocumentReplacementGuard.Choice.SAVE, () -> false));
        assertTrue(DocumentReplacementGuard.allow(true, () -> DocumentReplacementGuard.Choice.SAVE, () -> true));
    }
}
