package org.trostheide.gantry.plotter;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortTimeoutException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** {@link SerialTransport} backed by jSerialComm. */
public final class JSerialCommTransport implements SerialTransport {

    private final SerialPort port;
    private final InputStream in;
    private final OutputStream out;

    private JSerialCommTransport(SerialPort port) {
        this.port = port;
        this.in = port.getInputStream();
        this.out = port.getOutputStream();
    }

    /**
     * Opens the named serial port at the given baud rate. {@code readTimeoutMs} is the
     * semi-blocking read timeout used by {@link #readLine()}, analogous to pyserial's
     * {@code timeout} constructor argument.
     */
    public static JSerialCommTransport open(String portName, int baudRate, int readTimeoutMs) throws IOException {
        SerialPort port = SerialPort.getCommPort(portName);
        port.setBaudRate(baudRate);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, readTimeoutMs, 0);
        if (!port.openPort()) {
            throw new IOException("Failed to open serial port " + portName);
        }
        return new JSerialCommTransport(port);
    }

    @Override
    public boolean isOpen() {
        return port.isOpen();
    }

    @Override
    public String readLine() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while (true) {
            int b;
            try {
                b = in.read();
            } catch (SerialPortTimeoutException e) {
                // On Windows, a semi-blocking read with zero bytes available throws
                // SerialPortTimeoutException instead of returning -1 (unlike Linux/macOS).
                // Treat it the same as a timeout with nothing buffered.
                b = -1;
            }
            if (b == -1) {
                // Read timeout or port closed with nothing buffered: behave like
                // pyserial's readline() returning b"" so the caller can poll again.
                return buffer.size() == 0 ? null : buffer.toString(StandardCharsets.UTF_8).strip();
            }
            if (b == '\n') {
                return buffer.toString(StandardCharsets.UTF_8).strip();
            }
            buffer.write(b);
        }
    }

    @Override
    public void writeBytes(byte[] data) throws IOException {
        out.write(data);
        out.flush();
    }

    @Override
    public void close() {
        port.closePort();
    }
}
