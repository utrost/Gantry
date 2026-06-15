package org.trostheide.svgtoolbox;

import org.apache.commons.cli.*;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.util.XMLResourceDescriptor;
import org.trostheide.svgtoolbox.processors.*;
import org.w3c.dom.Document;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.Color;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class SvgToolboxRunner {

    public static void main(String[] args) {
        Options options = new Options();
        options.addOption(Option.builder("i").longOpt("input").hasArg().required().desc("Input SVG path").build());
        options.addOption(Option.builder("o").longOpt("output").hasArg().required().desc("Output SVG path").build());
        options.addOption(Option.builder("w").longOpt("stroke-width").hasArg().type(Number.class)
                .desc("Stroke width (px)").build());
        options.addOption(
                Option.builder("p").longOpt("palette").hasArg().desc("Hex colors (e.g. #000000,#FF0000)").build());

        // Hatching Flags
        options.addOption(Option.builder("h").longOpt("hatch").desc("Enable hatching").build());
        options.addOption(
                Option.builder().longOpt("hatch-angle").hasArg().type(Number.class).desc("Global angle").build());
        options.addOption(Option.builder().longOpt("hatch-gap").hasArg().type(Number.class).desc("Global gap").build());
        options.addOption(
                Option.builder("S").longOpt("style").hasArg().desc("Overrides: HEX:ANGLE:GAP:PATTERN;...").build());

        options.addOption(Option.builder().longOpt("no-hatch").hasArg().desc("Colors to skip").build());
        options.addOption(
                Option.builder().longOpt("min-area").hasArg().type(Number.class).desc("Min area (px^2)").build());

        options.addOption(Option.builder().longOpt("hidden-layers").hasArg().desc("Comma-separated list of colors to hide").build());
        options.addOption(Option.builder().longOpt("layer-width").hasArg().desc("Overrides: HEX:WIDTH;...").build());

        // Optimization Flags
        options.addOption(Option.builder().longOpt("simplify").hasArg().type(Number.class)
                .desc("Simplify tolerance (e.g., 0.5)").build());
        options.addOption(Option.builder().longOpt("pattern").hasArg()
                .desc("Hatch pattern (none, empty, linear, cross, zigzag, wave, dot)").build());
        options.addOption(Option.builder().longOpt("rotate").hasArg().type(Number.class)
                .desc("Rotate degrees (90, 180...)").build());
        options.addOption(Option.builder().longOpt("stats").desc("Print statistics").build());
        options.addOption(Option.builder().longOpt("crop").hasArg().desc("Crop (WxH, A4, Letter)").build());
        options.addOption(Option.builder().longOpt("optimize").desc("Optimize path order for plotting").build());
        options.addOption(Option.builder().longOpt("linesimplify").desc("Simplify path lines (RDP)").build());
        options.addOption(Option.builder().longOpt("linesimplify-tolerance").hasArg().type(Number.class)
                .desc("Linesimplify tolerance (default 0.378)").build());
        options.addOption(Option.builder().longOpt("linemerge").desc("Merge adjacent open paths").build());
        options.addOption(Option.builder().longOpt("linemerge-tolerance").hasArg().type(Number.class)
                .desc("Linemerge tolerance (default 1.89)").build());
        options.addOption(Option.builder().longOpt("linesort").desc("Sort paths for minimum pen travel").build());
        options.addOption(Option.builder().longOpt("linesort-twoopt").desc("Enable 2-opt improvement for linesort").build());
        options.addOption(Option.builder().longOpt("reloop").desc("Rotate closed-path start points for minimum pen travel").build());

        CommandLineParser parser = new DefaultParser();

        try {
            CommandLine cmd = parser.parse(options, args);
            Config config = buildConfig(cmd);
            processPipeline(config);
        } catch (ParseException e) {
            System.err.println("CLI Error: " + e.getMessage());
            new HelpFormatter().printHelp("svgtoolbox", options);
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
    }

    private static Config buildConfig(CommandLine cmd) {
        List<Color> palette = parseColors(cmd.getOptionValue("p"));
        List<Color> noHatch = parseColors(cmd.getOptionValue("no-hatch"));

        // 1. Build Global Defaults
        double defAngle = cmd.hasOption("hatch-angle") ? Double.parseDouble(cmd.getOptionValue("hatch-angle")) : 45.0;
        double defGap = cmd.hasOption("hatch-gap") ? Double.parseDouble(cmd.getOptionValue("hatch-gap")) : 5.0;
        HatchStyle globalStyle = new HatchStyle(defAngle, defGap, "linear");

        // 2. Parse Overrides (Format: #HEX:ANGLE:GAP:PATTERN;...)
        Map<String, HatchStyle> overrides = new HashMap<>();
        if (cmd.hasOption("style")) {
            String[] entries = cmd.getOptionValue("style").split(";");
            for (String entry : entries) {
                String[] parts = entry.split(":");
                if (parts.length >= 3) {
                    String hex = parts[0].trim().toLowerCase();
                    double angle = Double.parseDouble(parts[1]);
                    double gap = Double.parseDouble(parts[2]);
                    String pat = parts.length > 3 ? parts[3].trim() : "linear";
                    // Maintain backward compatibility for boolean crosshatch 'true'
                    if ("true".equalsIgnoreCase(pat)) pat = "cross";
                    else if ("false".equalsIgnoreCase(pat)) pat = "linear";
                    overrides.put(hex, new HatchStyle(angle, gap, pat));
                }
            }
        }

        // 3. Parse Stroke Width Overrides (Format: #HEX:WIDTH;...)
        Map<String, Float> strokeWidthOverrides = new HashMap<>();
        if (cmd.hasOption("layer-width")) {
            String[] entries = cmd.getOptionValue("layer-width").split(";");
            for (String entry : entries) {
                String[] parts = entry.split(":");
                if (parts.length >= 2) {
                    try {
                        String hex = parts[0].trim().toLowerCase();
                        float width = Float.parseFloat(parts[1]);
                        strokeWidthOverrides.put(hex, width);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // 4. Parse Hidden Layers
        List<String> hiddenLayers = new ArrayList<>();
        if (cmd.hasOption("hidden-layers")) {
            String[] split = cmd.getOptionValue("hidden-layers").split(",");
            for (String color : split) {
                hiddenLayers.add(color.trim().toLowerCase());
            }
        }

        return new Config.Builder()
                .inputPath(cmd.getOptionValue("i"))
                .outputPath(cmd.getOptionValue("o"))
                .strokeWidth((float) Double.parseDouble(cmd.getOptionValue("stroke-width", "0")))
                .palette(palette)
                .enableHatching(cmd.hasOption("h"))
                .globalStyle(globalStyle)
                .overrides(overrides)
                .strokeWidthOverrides(strokeWidthOverrides)
                .hiddenLayers(hiddenLayers)
                .noHatchColors(noHatch)
                .minHatchArea(Double.parseDouble(cmd.getOptionValue("min-area", "100.0")))
                .simplifyTolerance(Double.parseDouble(cmd.getOptionValue("simplify", "0.0")))
                .hatchPattern(cmd.getOptionValue("pattern", "linear"))
                .rotationDegrees(Double.parseDouble(cmd.getOptionValue("rotate", "0.0")))
                .printStats(cmd.hasOption("stats"))
                .cropBounds(parseCrop(cmd.getOptionValue("crop")))
                .optimizePaths(cmd.hasOption("optimize"))
                .linesimplify(cmd.hasOption("linesimplify"))
                .linesimplifyTolerance(Double.parseDouble(cmd.getOptionValue("linesimplify-tolerance", "0.378")))
                .linemerge(cmd.hasOption("linemerge"))
                .linemergeTolerance(Double.parseDouble(cmd.getOptionValue("linemerge-tolerance", "1.89")))
                .linesort(cmd.hasOption("linesort"))
                .linesortTwoOpt(cmd.hasOption("linesort-twoopt"))
                .reloop(cmd.hasOption("reloop"))
                .build();
    }

    public static java.awt.geom.Rectangle2D parseCrop(String arg) {
        if (arg == null || arg.isEmpty())
            return null;
        // Format: WxH or "A4"
        double w = 0, h = 0;
        switch (arg.toUpperCase()) {
            case "A4":
                w = 793.7;  // 210mm at 96 DPI (SVG/Inkscape standard)
                h = 1122.5; // 297mm at 96 DPI
                break;
            case "LETTER":
                w = 816.0;  // 8.5in at 96 DPI
                h = 1056.0; // 11in at 96 DPI
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
        if (arg == null || arg.isEmpty())
            return new ArrayList<>();
        return Arrays.stream(arg.split(","))
                .map(String::trim)
                .map(Color::decode)
                .collect(Collectors.toList());
    }

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int step, int total, String processorName);
    }

    public static void processPipeline(Config config) throws IOException {
        processPipeline(config, null);
    }

    public static void processPipeline(Config config, ProgressCallback progress) throws IOException {
        String parser = XMLResourceDescriptor.getXMLParserClassName();
        SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
        Document doc = factory.createDocument(new File(config.inputPath()).toURI().toString());

        List<Processor> pipeline = new ArrayList<>();
        pipeline.add(new VisibilityProcessor());
        pipeline.add(new StyleNormalizerProcessor());
        pipeline.add(new RotateProcessor());
        pipeline.add(new StrokeWidthProcessor());
        pipeline.add(new PaletteProcessor());

        // 1. Simplify paths to remove noise
        pipeline.add(new SimplifyProcessor());

        // 2. Generate hatched lines (inside groups with transforms)
        pipeline.add(new HatchProcessor());

        // 3. Plotter path optimization
        pipeline.add(new LinesimplifyProcessor());
        pipeline.add(new LinemergeProcessor());
        pipeline.add(new LinesortProcessor());
        pipeline.add(new ReloopProcessor());

        // 4. Organize into layers
        pipeline.add(new LayerProcessor());

        // 5. Crop
        pipeline.add(new CropProcessor());

        if (config.optimizePaths()) {
            pipeline.add(new PathOptimizeProcessor());
        }

        int total = pipeline.size();
        int step = 0;
        for (Processor p : pipeline) {
            step++;
            if (progress != null) {
                progress.onProgress(step, total, p.getClass().getSimpleName().replace("Processor", ""));
            }
            p.process(doc, config);
        }

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        try {
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(config.outputPath()));
            transformer.transform(source, result);
            System.out.println("Success: " + config.outputPath());

            if (config.printStats()) {
                org.trostheide.svgtoolbox.core.SvgStatistics.Stats stats = org.trostheide.svgtoolbox.core.SvgStatistics
                        .analyze(doc);
                System.out.println("--- Statistics ---");
                System.out.println("Elements: " + stats.elementCount());
                System.out.println(String.format("Total Length: %.2f meters", stats.totalLengthMeters()));
            }
        } catch (TransformerException e) {
            throw new IOException("Error saving SVG", e);
        }
    }
}