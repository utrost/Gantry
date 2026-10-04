package org.trostheide.gantry.model.io;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;

/** Same-directory staged writes. Unsupported atomic moves fall back to a same-filesystem rename. */
public final class AtomicFiles {
    private AtomicFiles() { }
    @FunctionalInterface public interface Writer { void write(File temporary) throws IOException; }
    @FunctionalInterface interface Mover { void move(Path source, Path target, boolean atomic) throws IOException; }

    public static void write(File destination, Writer writer) throws IOException {
        write(destination, writer, (source, target, atomic) -> {
            if (atomic) Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        });
    }

    static void write(File destination, Writer writer, Mover mover) throws IOException {
        Path target = destination.toPath().toAbsolutePath();
        Path temporary = Files.createTempFile(target.getParent(), ".gantry-", ".tmp");
        try {
            writer.write(temporary.toFile());
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            try { mover.move(temporary, target, true); }
            catch (AtomicMoveNotSupportedException unsupported) { mover.move(temporary, target, false); }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
