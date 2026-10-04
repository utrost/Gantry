package org.trostheide.gantry.app.gui;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** One Save/Discard/Cancel policy for every document replacement and close action. */
final class DocumentReplacementGuard {
    enum Choice { SAVE, DISCARD, CANCEL }
    private DocumentReplacementGuard() { }
    static boolean allow(boolean dirty, Supplier<Choice> choice, BooleanSupplier save) {
        if (!dirty) return true;
        return switch (choice.get()) {
            case SAVE -> save.getAsBoolean();
            case DISCARD -> true;
            case CANCEL -> false;
        };
    }
}
