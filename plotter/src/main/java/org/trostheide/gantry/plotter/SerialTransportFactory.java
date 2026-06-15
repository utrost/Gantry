package org.trostheide.gantry.plotter;

import java.io.IOException;

/** Opens a {@link SerialTransport} for the given port name and baud rate. */
@FunctionalInterface
public interface SerialTransportFactory {
    SerialTransport open(String portName, int baudRate) throws IOException;
}
