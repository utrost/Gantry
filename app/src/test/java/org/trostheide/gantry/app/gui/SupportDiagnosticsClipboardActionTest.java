package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupportDiagnosticsClipboardActionTest {
    @Test
    void copiesFreshDiagnosticsTextAndNotifiesUser() throws Exception {
        Clipboard clipboard = new Clipboard("test");
        AtomicReference<String> notice = new AtomicReference<>();
        Supplier<String> report = () -> "diagnostics text";

        new SupportDiagnosticsClipboardAction(clipboard, report, notice::set).copy();

        assertEquals("diagnostics text", clipboard.getData(DataFlavor.stringFlavor));
        assertEquals("Support diagnostics copied to clipboard.", notice.get());
    }
}
