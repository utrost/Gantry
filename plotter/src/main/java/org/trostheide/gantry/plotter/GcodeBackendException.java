package org.trostheide.gantry.plotter;

/** A fatal GRBL command, controller-state, or serial-transport failure. */
public final class GcodeBackendException extends IllegalStateException {
    public GcodeBackendException(String message) {
        super(message);
    }

    public GcodeBackendException(String message, Throwable cause) {
        super(message, cause);
    }
}
