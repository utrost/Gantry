package org.trostheide.gantry.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.pipeline.io.ProcessorOutputIO;
import org.trostheide.gantry.pipeline.optimize.MultipassStage;
import org.trostheide.gantry.pipeline.optimize.OptimizeStage;
import org.trostheide.gantry.pipeline.svgimport.PaperFormat;
import org.trostheide.gantry.pipeline.svgimport.SvgImportOptions;
import org.trostheide.gantry.pipeline.svgimport.SvgImportStage;
import org.trostheide.gantry.svgtoolbox.Config;
import org.trostheide.gantry.svgtoolbox.HatchStyle;
import org.trostheide.gantry.watercolor.PaintStation;
import org.trostheide.gantry.watercolor.StationMapper;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Headless SVG-to-commands converter: the Gantry replacement for the legacy
 * {@code WatercolorProcessor} CLI. Converts an SVG file to a {@link ProcessorOutput} command
 * JSON file, ready to be loaded and plotted by the GUI or {@code PlotService}.
 */
public final class SvgImportCli {

    private SvgImportCli() {
    }

    public static void main(String[] args) {
        Options options = new Options();
        options.addOption(Option.builder("i").longOpt("input").hasArg().required(true)
                .desc("Input SVG file.").build());
        options.addOption(Option.builder("o").longOpt("output").hasArg().required(true)
                .desc("Output commands JSON file.").build());
        options.addOption(Option.builder("d").longOpt("max-dist").hasArg()
                .desc("Max draw distance (mm) before a REFILL is inserted. <= 0 disables refill (default 0).").build());
        options.addOption(Option.builder("s").longOpt("station").hasArg()
                .desc("Default refill station ID for layers with no explicit station (default 'default_station').").build());
        options.addOption(Option.builder("c").longOpt("curve-step").hasArg()
                .desc("Curve linearization step (mm, default 0.1).").build());
        options.addOption(Option.builder("f").longOpt("fit-to").hasArg()
                .desc("Fit to paper format: A5, A4, A3, XL, or WxH (mm).").build());
        options.addOption(Option.builder("p").longOpt("padding").hasArg()
                .desc("Padding for --fit-to (mm, default 10.0).").build());
        options.addOption(Option.builder("m").longOpt("mirror")
                .desc("Mirror the drawing horizontally.").build());
        options.addOption(Option.builder().longOpt("passes").hasArg()
                .desc("Repeat every stroke N times for multipass plotting (default 1, minimum 1).").build());
        options.addOption(Option.builder().longOpt("optimize-tolerance").hasArg()
                .desc("Post-import RDP simplify tolerance in mm.").build());
        options.addOption(Option.builder().longOpt("optimize-reorder")
                .desc("Post-import nearest-neighbour stroke reorder.").build());
        options.addOption(Option.builder().longOpt("optimize-merge").hasArg()
                .desc("Post-import stroke weld tolerance in mm.").build());
        options.addOption(Option.builder().longOpt("config").hasArg()
                .desc("Batch machine/station JSON config.").build());
        options.addOption(Option.builder().longOpt("map-stations")
                .desc("Map layer colours to stations from --config.").build());
        options.addOption(Option.builder().longOpt("gcode").hasArg()
                .desc("Also write plot-ready G-code using --config.").build());
        options.addOption(Option.builder("h").longOpt("help").desc("Show help.").build());

        // SVGToolBox pre-processing options
        options.addOption(Option.builder().longOpt("toolbox")
                .desc("Run the SVGToolBox processor pipeline before import.").build());
        options.addOption(Option.builder().longOpt("stroke-width").hasArg()
                .desc("Toolbox: stroke width override (px).").build());
        options.addOption(Option.builder().longOpt("palette").hasArg()
                .desc("Toolbox: quantize to hex colors (e.g. #000000,#FF0000).").build());
        options.addOption(Option.builder().longOpt("hatch")
                .desc("Toolbox: enable hatching.").build());
        options.addOption(Option.builder().longOpt("hatch-angle").hasArg()
                .desc("Toolbox: global hatch angle (default 45).").build());
        options.addOption(Option.builder().longOpt("hatch-gap").hasArg()
                .desc("Toolbox: global hatch gap (default 5).").build());
        options.addOption(Option.builder().longOpt("hatch-amplitude").hasArg()
                .desc("Toolbox: wave/zigzag amplitude (0 = auto, derived from gap).").build());
        options.addOption(Option.builder().longOpt("hatch-wavelength").hasArg()
                .desc("Toolbox: wave/zigzag wavelength (0 = auto, derived from gap).").build());
        options.addOption(Option.builder().longOpt("dot-radius").hasArg()
                .desc("Toolbox: dot pattern radius (0 = auto, uses stroke width).").build());
        options.addOption(Option.builder().longOpt("style").hasArg()
                .desc("Toolbox: per-color hatch overrides HEX:ANGLE:GAP:PATTERN;...").build());
        options.addOption(Option.builder().longOpt("no-hatch").hasArg()
                .desc("Toolbox: hex colors to skip hatching for.").build());
        options.addOption(Option.builder().longOpt("min-area").hasArg()
                .desc("Toolbox: minimum hatch area in px^2 (default 100).").build());
        options.addOption(Option.builder().longOpt("hidden-layers").hasArg()
                .desc("Toolbox: comma-separated hex colors to remove.").build());
        options.addOption(Option.builder().longOpt("layer-width").hasArg()
                .desc("Toolbox: per-color stroke width overrides HEX:WIDTH;...").build());
        options.addOption(Option.builder().longOpt("toolbox-simplify").hasArg()
                .desc("Toolbox: RDP simplify tolerance.").build());
        options.addOption(Option.builder().longOpt("pattern").hasArg()
                .desc("Toolbox: hatch pattern (none, empty, linear, cross, zigzag, wave, dot).").build());
        options.addOption(Option.builder().longOpt("rotate").hasArg()
                .desc("Toolbox: rotate degrees (90, 180...).").build());
        options.addOption(Option.builder().longOpt("toolbox-crop").hasArg()
                .desc("Toolbox: crop bounds (WxH, A4, Letter).").build());
        options.addOption(Option.builder().longOpt("optimize")
                .desc("Toolbox: optimize path order for plotting.").build());
        options.addOption(Option.builder().longOpt("linesimplify")
                .desc("Toolbox: simplify path lines (RDP).").build());
        options.addOption(Option.builder().longOpt("linesimplify-tolerance").hasArg()
                .desc("Toolbox: linesimplify tolerance (default 0.378).").build());
        options.addOption(Option.builder().longOpt("linemerge")
                .desc("Toolbox: merge adjacent open paths.").build());
        options.addOption(Option.builder().longOpt("linemerge-tolerance").hasArg()
                .desc("Toolbox: linemerge tolerance (default 1.89).").build());
        options.addOption(Option.builder().longOpt("linesort")
                .desc("Toolbox: sort paths for minimum pen travel.").build());
        options.addOption(Option.builder().longOpt("linesort-twoopt")
                .desc("Toolbox: enable 2-opt improvement for linesort.").build());
        options.addOption(Option.builder().longOpt("reloop")
                .desc("Toolbox: rotate closed-path start points for minimum pen travel.").build());
        options.addOption(Option.builder().longOpt("handdrawn")
                .desc("Toolbox: give lines a hand-drawn waver (jitter along the normal).").build());
        options.addOption(Option.builder().longOpt("handdrawn-magnitude").hasArg()
                .desc("Toolbox: hand-drawn jitter amplitude in px (default 2.0).").build());
        options.addOption(Option.builder().longOpt("handdrawn-segment").hasArg()
                .desc("Toolbox: hand-drawn resample spacing in px (default 4.0).").build());
        options.addOption(Option.builder().longOpt("handdrawn-wavelength").hasArg()
                .desc("Toolbox: hand-drawn wobble wavelength in px (default 30.0).").build());
        options.addOption(Option.builder().longOpt("handdrawn-seed").hasArg()
                .desc("Toolbox: hand-drawn random seed for reproducible output (default 1337).").build());
        options.addOption(Option.builder().longOpt("toolbox-stats")
                .desc("Toolbox: print element/length statistics.").build());

        if (Arrays.asList(args).contains("--help") || Arrays.asList(args).contains("-h")) {
            printHelp(options);
            return;
        }

        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);

            File inputFile = new File(cmd.getOptionValue("input"));
            File outputFile = new File(cmd.getOptionValue("output"));
            if (!inputFile.exists()) {
                throw new IllegalArgumentException("Input file missing: " + inputFile);
            }

            double maxDrawDistance = Double.parseDouble(cmd.getOptionValue("max-dist", "0"));
            double curveStep = Double.parseDouble(cmd.getOptionValue("curve-step", "0.1"));
            if (curveStep <= 0.01) {
                throw new IllegalArgumentException("Curve step too small.");
            }
            String defaultStationId = cmd.getOptionValue("station", "default_station");
            double padding = Double.parseDouble(cmd.getOptionValue("padding", "10.0"));
            boolean mirror = cmd.hasOption("mirror");
            int passes = Integer.parseInt(cmd.getOptionValue("passes", "1"));
            if (passes < 1) {
                throw new IllegalArgumentException("Passes must be at least 1.");
            }
            PaperFormat fitTo = PaperFormat.fromString(cmd.getOptionValue("fit-to"));

            SvgImportOptions importOptions = fitTo != null
                    ? SvgImportOptions.fitToFormat(maxDrawDistance, defaultStationId, curveStep, fitTo, padding, mirror)
                    : new SvgImportOptions(maxDrawDistance, defaultStationId, curveStep, 0, 0, true, 0, 0, mirror);

            ProcessorOutput output = cmd.hasOption("toolbox")
                    ? SvgImportStage.importSvg(inputFile, buildToolboxConfig(cmd, inputFile, outputFile), importOptions)
                    : SvgImportStage.importSvg(inputFile, importOptions);
            double optimizeTolerance = Double.parseDouble(
                    cmd.getOptionValue("optimize-tolerance", "0"));
            double optimizeMerge = Double.parseDouble(cmd.getOptionValue("optimize-merge", "0"));
            if (optimizeTolerance > 0 || optimizeMerge > 0 || cmd.hasOption("optimize-reorder")) {
                output = OptimizeStage.optimize(output, optimizeTolerance,
                        cmd.hasOption("optimize-reorder"), optimizeMerge);
            }
            CliBatchConfig batch = null;
            if (cmd.hasOption("config")) {
                batch = CliBatchConfig.load(new File(cmd.getOptionValue("config")));
            }
            if (cmd.hasOption("map-stations")) {
                if (batch == null) {
                    throw new IllegalArgumentException("--map-stations requires --config");
                }
                List<PaintStation> stations = new ArrayList<>();
                for (Map.Entry<String, CliBatchConfig.Station> entry : batch.stations.entrySet()) {
                    if (entry.getValue().color != null) {
                        stations.add(new PaintStation(entry.getKey(), entry.getValue().color));
                    }
                }
                output = StationMapper.assignByColor(output, stations);
            }
            output = MultipassStage.apply(output, passes);
            ProcessorOutputIO.save(output, outputFile);
            if (cmd.hasOption("gcode")) {
                if (batch == null) {
                    throw new IllegalArgumentException("--gcode requires --config");
                }
                CliGcodeExporter.export(output, batch, new File(cmd.getOptionValue("gcode")));
            }

            System.out.printf("Wrote %d layer(s), %d command(s) to %s%n",
                    output.layers().size(), output.metadata().totalCommands(), outputFile);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            printHelp(options);
            System.exit(1);
        }
    }

    /** Builds the SVGToolBox {@link Config} from CLI options, mirroring legacy {@code SvgToolboxRunner.buildConfig}. */
    private static Config buildToolboxConfig(CommandLine cmd, File inputFile, File outputFile) {
        List<Color> palette = parseColors(cmd.getOptionValue("palette"));
        List<Color> noHatch = parseColors(cmd.getOptionValue("no-hatch"));

        double defAngle = cmd.hasOption("hatch-angle") ? Double.parseDouble(cmd.getOptionValue("hatch-angle")) : 45.0;
        double defGap = cmd.hasOption("hatch-gap") ? Double.parseDouble(cmd.getOptionValue("hatch-gap")) : 5.0;
        double defAmplitude = Double.parseDouble(cmd.getOptionValue("hatch-amplitude", "0"));
        double defWavelength = Double.parseDouble(cmd.getOptionValue("hatch-wavelength", "0"));
        double defDotRadius = Double.parseDouble(cmd.getOptionValue("dot-radius", "0"));
        HatchStyle globalStyle = new HatchStyle(defAngle, defGap, cmd.getOptionValue("pattern", "linear"),
                defAmplitude, defWavelength, defDotRadius);

        Map<String, HatchStyle> overrides = new HashMap<>();
        if (cmd.hasOption("style")) {
            for (String entry : cmd.getOptionValue("style").split(";")) {
                String[] parts = entry.split(":");
                if (parts.length >= 3) {
                    String hex = parts[0].trim().toLowerCase();
                    double angle = Double.parseDouble(parts[1]);
                    double gap = Double.parseDouble(parts[2]);
                    String pat = parts.length > 3 ? parts[3].trim() : "linear";
                    if ("true".equalsIgnoreCase(pat)) pat = "cross";
                    else if ("false".equalsIgnoreCase(pat)) pat = "linear";
                    overrides.put(hex, new HatchStyle(angle, gap, pat));
                }
            }
        }

        Map<String, Float> strokeWidthOverrides = new HashMap<>();
        if (cmd.hasOption("layer-width")) {
            for (String entry : cmd.getOptionValue("layer-width").split(";")) {
                String[] parts = entry.split(":");
                if (parts.length >= 2) {
                    try {
                        strokeWidthOverrides.put(parts[0].trim().toLowerCase(), Float.parseFloat(parts[1]));
                    } catch (NumberFormatException ignored) {
                        // skip malformed override
                    }
                }
            }
        }

        List<String> hiddenLayers = new ArrayList<>();
        if (cmd.hasOption("hidden-layers")) {
            for (String color : cmd.getOptionValue("hidden-layers").split(",")) {
                hiddenLayers.add(color.trim().toLowerCase());
            }
        }

        return new Config.Builder()
                .inputPath(inputFile.getPath())
                .outputPath(outputFile.getPath())
                .strokeWidth((float) Double.parseDouble(cmd.getOptionValue("stroke-width", "0")))
                .palette(palette)
                .enableHatching(cmd.hasOption("hatch"))
                .globalStyle(globalStyle)
                .overrides(overrides)
                .strokeWidthOverrides(strokeWidthOverrides)
                .hiddenLayers(hiddenLayers)
                .noHatchColors(noHatch)
                .minHatchArea(Double.parseDouble(cmd.getOptionValue("min-area", "100.0")))
                .simplifyTolerance(Double.parseDouble(cmd.getOptionValue("toolbox-simplify", "0.0")))
                .hatchPattern(cmd.getOptionValue("pattern", "linear"))
                .rotationDegrees(Double.parseDouble(cmd.getOptionValue("rotate", "0.0")))
                .printStats(cmd.hasOption("toolbox-stats"))
                .cropBounds(parseCrop(cmd.getOptionValue("toolbox-crop")))
                .optimizePaths(cmd.hasOption("optimize"))
                .linesimplify(cmd.hasOption("linesimplify"))
                .linesimplifyTolerance(Double.parseDouble(cmd.getOptionValue("linesimplify-tolerance", "0.378")))
                .linemerge(cmd.hasOption("linemerge"))
                .linemergeTolerance(Double.parseDouble(cmd.getOptionValue("linemerge-tolerance", "1.89")))
                .linesort(cmd.hasOption("linesort"))
                .linesortTwoOpt(cmd.hasOption("linesort-twoopt"))
                .reloop(cmd.hasOption("reloop"))
                .handdrawn(cmd.hasOption("handdrawn"))
                .handdrawnMagnitude(Double.parseDouble(cmd.getOptionValue("handdrawn-magnitude", "2.0")))
                .handdrawnSegment(Double.parseDouble(cmd.getOptionValue("handdrawn-segment", "4.0")))
                .handdrawnWavelength(Double.parseDouble(cmd.getOptionValue("handdrawn-wavelength", "30.0")))
                .handdrawnSeed(Long.parseLong(cmd.getOptionValue("handdrawn-seed", "1337")))
                .build();
    }

    /** Parses a crop spec: {@code WxH}, {@code A4}, or {@code Letter}. Mirrors legacy {@code SvgToolboxRunner.parseCrop}. */
    private static java.awt.geom.Rectangle2D parseCrop(String arg) {
        if (arg == null || arg.isEmpty()) {
            return null;
        }
        double w;
        double h;
        switch (arg.toUpperCase()) {
            case "A4":
                w = 793.7;
                h = 1122.5;
                break;
            case "LETTER":
                w = 816.0;
                h = 1056.0;
                break;
            default:
                try {
                    String[] parts = arg.split("x");
                    w = Double.parseDouble(parts[0]);
                    h = Double.parseDouble(parts[1]);
                } catch (Exception e) {
                    return null;
                }
        }
        return new java.awt.geom.Rectangle2D.Double(0, 0, w, h);
    }

    private static List<Color> parseColors(String arg) {
        if (arg == null || arg.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(arg.split(","))
                .map(String::trim)
                .map(Color::decode)
                .collect(Collectors.toList());
    }

    private static void printHelp(Options options) {
        new HelpFormatter().printHelp("gantry-cli", options);
    }
}
