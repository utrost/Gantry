package org.trostheide.gantry.plotter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A line-oriented serial connection. {@link #readLine()} mirrors pyserial's
 * timeout-based {@code readline()}: it blocks for at most the configured read
 * timeout and returns {@code null} if no complete line was available.
 */
public interface SerialTransport extends AutoCloseable {

    boolean isOpen();

    /** Reads one line (without the trailing newline), or null on read timeout / EOF. */
    String readLine() throws IOException;

    void writeBytes(byte[] data) throws IOException;

    default void writeLine(String command) throws IOException {
        writeBytes((command + "\n").getBytes(StandardCharsets.UTF_8));
    }

    @Override
    void close();
}
