package org.trostheide.gantry.app.session;

/** Editable source parameters for a simple plotter-native artwork group. */
public record QuickArtwork(Kind kind, String text, double widthMm, double heightMm) {
    public enum Kind { LINE, RECTANGLE, ELLIPSE, TEXT }

    public QuickArtwork {
        if (kind == null) throw new IllegalArgumentException("Choose a quick-artwork type");
        text = text == null ? "" : text;
        if (kind == Kind.TEXT) {
            if (text.isBlank()) throw new IllegalArgumentException("Text cannot be empty");
            if (!Double.isFinite(heightMm) || heightMm <= 0)
                throw new IllegalArgumentException("Text height must be positive");
            widthMm = 0; // Letter spacing follows the bundled stroke font.
        } else {
            if (!Double.isFinite(widthMm) || widthMm <= 0)
                throw new IllegalArgumentException("Width must be positive");
            if (!Double.isFinite(heightMm) || (kind != Kind.LINE && heightMm <= 0) || heightMm < 0)
                throw new IllegalArgumentException("Height must be zero or positive for a line, positive for a shape");
        }
    }
}
