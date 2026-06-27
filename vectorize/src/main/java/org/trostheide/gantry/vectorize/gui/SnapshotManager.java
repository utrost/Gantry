package org.trostheide.gantry.vectorize.gui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

class SnapshotManager {

    private final Deque<Map<String, Object>> undoStack = new ArrayDeque<>();
    private final Deque<Map<String, Object>> redoStack = new ArrayDeque<>();
    private static final int MAX_UNDO = 50;

    private final Supplier<Map<String, Object>> snapshotSupplier;
    private final Consumer<Map<String, Object>> snapshotApplier;

    SnapshotManager(Supplier<Map<String, Object>> snapshotSupplier,
                    Consumer<Map<String, Object>> snapshotApplier) {
        this.snapshotSupplier = snapshotSupplier;
        this.snapshotApplier = snapshotApplier;
    }

    void pushUndoState() {
        undoStack.push(snapshotSupplier.get());
        if (undoStack.size() > MAX_UNDO) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    boolean undo() {
        if (undoStack.isEmpty()) return false;
        redoStack.push(snapshotSupplier.get());
        snapshotApplier.accept(undoStack.pop());
        return true;
    }

    boolean redo() {
        if (redoStack.isEmpty()) return false;
        undoStack.push(snapshotSupplier.get());
        snapshotApplier.accept(redoStack.pop());
        return true;
    }

    boolean canUndo() {
        return !undoStack.isEmpty();
    }

    boolean canRedo() {
        return !redoStack.isEmpty();
    }
}
