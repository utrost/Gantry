package org.trostheide.gantry.model;

import org.trostheide.gantry.model.command.Command;

import java.util.List;

/**
 * Represents a specific layer/color pass in the plotting job.
 * Maps directly to an Inkscape Layer.
 *
 * @param id The layer identifier (from Inkscape layer name).
 * @param stationId The refill station ID this layer uses.
 * @param color The layer's source colour as a {@code #rrggbb} hex string (from the SVG
 *              stroke/fill), or {@code null} if unknown. Drives paint-station assignment
 *              for watercolor jobs.
 * @param maxDrawDistance maximum cumulative drawing distance in mm between refills;
 *                        {@code 0} disables automatic refills.
 * @param dipBehavior per-layer refill pattern, or {@code null} to inherit the station behavior.
 * @param commands The sequence of drawing/refill commands for this layer.
 */
public record Layer(
        String id,
        String stationId,
        String color,
        double maxDrawDistance,
        String dipBehavior,
        List<Command> commands
) {
    public Layer {
        commands = List.copyOf(commands);
    }

    /** Backward-compatible constructor for layers with no known source colour. */
    public Layer(String id, String stationId, List<Command> commands) {
        this(id, stationId, null, 0, null, commands);
    }

    /** Backward-compatible constructor for callers that do not persist refill distance yet. */
    public Layer(String id, String stationId, String color, List<Command> commands) {
        this(id, stationId, color, 0, null, commands);
    }

    public Layer(String id, String stationId, String color, double maxDrawDistance, List<Command> commands) {
        this(id, stationId, color, maxDrawDistance, null, commands);
    }

    /** Returns a copy of this layer reassigned to a different refill station. */
    public Layer withStationId(String newStationId) {
        return new Layer(id, newStationId, color, maxDrawDistance, dipBehavior, commands);
    }

    public Layer withCommands(List<Command> newCommands) {
        return new Layer(id, stationId, color, maxDrawDistance, dipBehavior, newCommands);
    }
}
