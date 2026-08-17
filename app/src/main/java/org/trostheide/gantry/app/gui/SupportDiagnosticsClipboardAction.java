package org.trostheide.gantry.app.gui;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Copies a freshly generated support diagnostics report to the system clipboard. */
final class SupportDiagnosticsClipboardAction {
    private final Clipboard clipboard;
    private final Supplier<String> reportSupplier;
    private final Consumer<String> notifyUser;

    SupportDiagnosticsClipboardAction(Clipboard clipboard, Supplier<String> reportSupplier, Consumer<String> notifyUser) {
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
        this.reportSupplier = Objects.requireNonNull(reportSupplier, "reportSupplier");
        this.notifyUser = Objects.requireNonNull(notifyUser, "notifyUser");
    }

    void copy() {
        String report = reportSupplier.get();
        clipboard.setContents(new StringSelection(report), null);
        notifyUser.accept("Support diagnostics copied to clipboard.");
    }
}
