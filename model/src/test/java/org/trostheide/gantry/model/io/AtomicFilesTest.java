package org.trostheide.gantry.model.io;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
class AtomicFilesTest {
    @TempDir Path directory;
    @Test void failedSerializationPreservesOriginalAndCleansTemporary() throws Exception {
        Path file = Files.writeString(directory.resolve("project.gantry"), "original");
        assertThrows(IOException.class, () -> AtomicFiles.write(file.toFile(), temp -> {
            Files.writeString(temp.toPath(), "partial"); throw new IOException("disk full");
        }));
        assertEquals("original", Files.readString(file));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
    @Test void failedMovePreservesOriginal() throws Exception {
        Path file = Files.writeString(directory.resolve("project.gantry"), "original");
        assertThrows(IOException.class, () -> AtomicFiles.write(file.toFile(), t -> Files.writeString(t.toPath(), "new"),
                (s, d, atomic) -> { throw new IOException("permission denied"); }));
        assertEquals("original", Files.readString(file));
    }
    @Test void newAndExistingDestinationsAndFallback() throws Exception {
        Path file = directory.resolve("project.gantry");
        AtomicFiles.write(file.toFile(), t -> Files.writeString(t.toPath(), "first"));
        assertEquals("first", Files.readString(file));
        AtomicFiles.write(file.toFile(), t -> Files.writeString(t.toPath(), "second"), (s, d, atomic) -> {
            if (atomic) throw new AtomicMoveNotSupportedException(s.toString(), d.toString(), "fixture");
            Files.move(s, d, StandardCopyOption.REPLACE_EXISTING);
        });
        assertEquals("second", Files.readString(file));
    }
}
