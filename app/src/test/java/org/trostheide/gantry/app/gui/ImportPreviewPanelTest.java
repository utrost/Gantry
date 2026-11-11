package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.trostheide.gantry.pipeline.svgimport.SvgImportOptions;
import javax.swing.SwingUtilities;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class ImportPreviewPanelTest {
    @TempDir Path directory;
    @Test void changedAndFailedSourcesImmediatelyInvalidateTheAcceptedPreview() throws Exception {
        Path svg = directory.resolve("valid.svg");
        Files.writeString(svg, "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"20mm\" height=\"20mm\" viewBox=\"0 0 20 20\"><path d=\"M1 1 L19 19\" stroke=\"black\" fill=\"none\"/></svg>");
        ImportPreviewPanel[] preview = new ImportPreviewPanel[1];
        AtomicBoolean invalidOptions = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> {
            preview[0] = new ImportPreviewPanel(100,100, () -> invalidOptions.get()?null:
                    new SvgImportOptionsPanel.Result(SvgImportOptions.defaults(),null));
            preview[0].setSource(svg.toFile());
        });
        try {
            boolean[] ready = {false};
            for (int i=0;i<100&&!ready[0];i++) {
                Thread.sleep(50); SwingUtilities.invokeAndWait(()->ready[0]=preview[0].ready());
            }
            assertTrue(ready[0], "valid preview becomes ready");
            SwingUtilities.invokeAndWait(() -> {
                invalidOptions.set(true); preview[0].schedule();
                assertFalse(preview[0].ready(), "parameter edit invalidates before debounce");
                preview[0].setSource(directory.resolve("missing.svg").toFile());
                invalidOptions.set(false);
            });
            Thread.sleep(700);
            SwingUtilities.invokeAndWait(() -> assertFalse(preview[0].ready(), "failure cannot reuse previous success"));
        } finally { SwingUtilities.invokeAndWait(()->preview[0].close()); }
    }
}
