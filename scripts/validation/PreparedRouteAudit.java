package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.*;
import org.trostheide.gantry.model.*;
import org.trostheide.gantry.model.command.*;
import org.trostheide.gantry.plotter.GcodeOptions;
import java.time.Instant;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.apache.batik.transcoder.*;
import org.apache.batik.transcoder.image.PNGTranscoder;

/** Compile/run against baseline and fixed JARs; actual canvas, SVG raster and estimate, no backend. */
public class PreparedRouteAudit {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]); Files.createDirectories(directory);
        var settings = new PlotSettings(); settings.machineWidth = 40.; settings.machineHeight = 30.;
        settings.stations = Map.of("s", new StationConfig(30, 22, 0, "simple_dip", null, 1500, 2));
        var output = new ProcessorOutput(new Metadata("controller starts", Instant.EPOCH, "s", "mm", 5, Bounds.empty()),
                List.of(new Layer("ink", "s", "#2463a8", 0, null, List.of(
                        new DrawCommand(1, List.of(new Point(8, 6))), new MoveCommand(2, 4, 4),
                        new DrawCommand(3, List.of(new Point(8, 4))), new RefillCommand(4, "s"),
                        new DrawCommand(5, List.of(new Point(18, 10)))))));
        SwingUtilities.invokeAndWait(() -> {
            try {
                var panel = new VisualizationPanel(); panel.setMachineSize(40, 30); panel.setMachineOrigin("Top-Left");
                panel.setCanvasAlignment(null); panel.setStations(List.of(new VisualizationPanel.Station("s", 30, 22)));
                try { panel.getClass().getMethod("setRefillStations", Map.class).invoke(panel, settings.stations); }
                catch (NoSuchMethodException baseline) { /* Baseline has marker-only station metadata. */ }
                panel.loadFromOutput(output); panel.editingEnabled = false; panel.setSize(840, 640);
                var image = new BufferedImage(840, 640, BufferedImage.TYPE_INT_RGB);
                var g = image.createGraphics(); panel.paint(g); g.dispose();
                ImageIO.write(image, "png", directory.resolve("workspace.png").toFile());
            } catch (Exception failure) { throw new RuntimeException(failure); }
        });
        String svg = SvgJobExporter.combined(new PlotService(null, settings).projectArtwork(output), List.of(.3));
        Files.writeString(directory.resolve("reviewed.svg"), svg);
        var renderer = new PNGTranscoder(); renderer.addTranscodingHint(PNGTranscoder.KEY_WIDTH, 840f);
        renderer.addTranscodingHint(PNGTranscoder.KEY_BACKGROUND_COLOR, java.awt.Color.WHITE);
        try (var stream = Files.newOutputStream(directory.resolve("reviewed.png"))) {
            renderer.transcode(new TranscoderInput(new java.io.StringReader(svg)), new TranscoderOutput(stream));
        }
        var options = new GcodeOptions(); options.feedRateDraw = 60; options.feedRateTravel = 6000; options.penDownDelayMillis = 100;
        TimeEstimator.PlotEstimate estimate;
        try { estimate = (TimeEstimator.PlotEstimate) TimeEstimator.class.getMethod("estimate", ProcessorOutput.class,
                GcodeOptions.class, PlotSettings.class).invoke(null, output, options, settings); }
        catch (NoSuchMethodException baseline) { estimate = TimeEstimator.estimate(output, options, settings.stations); }
        String report = "Draw mm: " + estimate.layers().get(0).drawDistanceMm() + "\nTravel mm: "
                + estimate.layers().get(0).travelDistanceMm() + "\nFeed/dwell seconds: " + estimate.totalSeconds() + "\n";
        Files.writeString(directory.resolve("estimate.txt"), report); System.out.print(report);
    }
}
