package org.trostheide.gantry.watercolor;

import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.RefillCommand;

import java.util.ArrayList;
import java.util.List;

/**
 * Assigns each drawing layer to the physical paint pot whose colour best matches the layer's
 * source colour. This is the watercolor module's reason to exist: it replaces the fragile
 * positional {@code "Layer1" -> "station1"} naming with a colour-driven mapping, so the operator
 * fills pots by colour and Gantry routes each layer (and its refill commands) to the right one.
 */
public final class StationMapper {

    private StationMapper() {
    }

    /**
     * Returns the id of the {@code stations} entry whose colour is perceptually closest to
     * {@code layerColorHex}, or {@code null} if no station has a usable colour (or the layer
     * colour is unknown).
     */
    public static String nearestStation(String layerColorHex, List<PaintStation> stations) {
        if (layerColorHex == null || stations == null || stations.isEmpty()) {
            return null;
        }
        String best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PaintStation station : stations) {
            double d = ColorUtil.distance(layerColorHex, station.colorHex());
            if (d < bestDistance) {
                bestDistance = d;
                best = station.id();
            }
        }
        return best;
    }

    /**
     * Returns a copy of {@code output} with every layer (and the {@link RefillCommand}s inside it)
     * reassigned to the nearest-colour station. Layers whose colour can't be matched to any
     * station keep their original station id, so a partial palette never drops geometry.
     */
    public static ProcessorOutput assignByColor(ProcessorOutput output, List<PaintStation> stations) {
        List<Layer> remapped = new ArrayList<>(output.layers().size());
        for (Layer layer : output.layers()) {
            String target = nearestStation(layer.color(), stations);
            if (target == null || target.equals(layer.stationId())) {
                remapped.add(layer);
                continue;
            }
            remapped.add(reassign(layer, target));
        }
        return new ProcessorOutput(output.metadata(), remapped);
    }

    private static Layer reassign(Layer layer, String stationId) {
        List<Command> commands = new ArrayList<>(layer.commands().size());
        for (Command cmd : layer.commands()) {
            if (cmd instanceof RefillCommand refill) {
                commands.add(new RefillCommand(refill.id, stationId));
            } else {
                commands.add(cmd);
            }
        }
        return new Layer(layer.id(), stationId, layer.color(), commands);
    }
}
