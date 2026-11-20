import com.fasterxml.jackson.databind.ObjectMapper;
import org.trostheide.gantry.cli.CliBatchConfig;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;
import org.trostheide.gantry.pipeline.io.ProcessorOutputIO;
import org.trostheide.gantry.plotter.GcodeFileReplay;
import java.io.File;

/** Offline typed command reload and production replay preflight. Never opens a backend. */
public class ImageArtArtifactCheck {
    public static void main(String[] args) throws Exception {
        var options = CliBatchConfig.load(new File(args[0])).gcode;
        var output = ProcessorOutputIO.load(new File(args[1]));
        int points = 0;
        for (var layer : output.layers()) {
            for (var command : layer.commands()) {
                if (command instanceof MoveCommand move) check(move.x, move.y, options.machineWidth, options.machineHeight);
                else if (command instanceof DrawCommand draw) {
                    for (var point : draw.points) {
                        check(point.x(), point.y(), options.machineWidth, options.machineHeight);
                        points++;
                    }
                } else throw new IllegalStateException("Unexpected non-drawing command: " + command);
            }
        }
        if (points == 0) throw new IllegalStateException("No drawing points");
        var plan = GcodeFileReplay.preflight(new File(args[2]), options);
        var result = new ObjectMapper().createObjectNode();
        result.put("commandReload", "passed");
        result.put("finiteInBedPoints", points);
        result.put("gcodePreflight", "passed");
        result.put("gcodeCommands", plan.commands().size());
        System.out.println(result);
    }
    private static void check(double x, double y, double width, double height) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > width || y < 0 || y > height)
            throw new IllegalStateException("Invalid/out-of-bed point: " + x + ", " + y);
    }
}
