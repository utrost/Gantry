package org.trostheide.gantry.watercolor;

/**
 * A physical paint pot available for colour-to-station mapping: its logical id and the paint
 * colour loaded in it as a {@code #rrggbb} hex string. Decoupled from the app's full
 * {@code StationConfig} so the watercolor module need not depend on the app.
 */
public record PaintStation(String id, String colorHex) {
}
