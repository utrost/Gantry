package org.trostheide.gantry.model;

import org.trostheide.gantry.model.command.Command;

import java.util.List;

/**
 * Represents a specific layer/color pass in the plotting job.
 * Maps directly to an Inkscape Layer.
 *
 * @param id The layer identifier (from Inkscape layer name).
 * @param stationId The refill station ID this layer uses.
 * @param commands The sequence of drawing/refill commands for this layer.
 */
public record Layer(
        String id,
        String stationId,
        List<Command> commands
) {}
