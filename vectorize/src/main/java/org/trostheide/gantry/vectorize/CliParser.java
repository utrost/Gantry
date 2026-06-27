package org.trostheide.gantry.vectorize;

import org.apache.commons.cli.*;
import org.trostheide.gantry.vectorize.strategies.*;

import java.util.Map;
import java.util.HashMap;

/**
 * Parses command line arguments using Apache Commons CLI and validates inputs.
 * Provides safe defaults for all optional parameters.
 */
public class CliParser {

    // --- Defaults ---
    private static final double DEFAULT_TOLERANCE = 2.0;
    private static final double DEFAULT_DETAIL = 1.0;
    private static final double DEFAULT_MIN_LEN = 5.0;
    private static final double DEFAULT_MAX_LEN = 2000.0;
    private static final double DEFAULT_CANNY_BLUR = 1.5;
    private static final double DEFAULT_CANNY_LOW = 0.02;
    private static final double DEFAULT_CANNY_HIGH = 0.1;

    // --- Bezier Default ---
    private static final int DEFAULT_BEZIER_DETAIL = 10;
    private static final int DEFAULT_BEZIER_COLORS = 16;

    // --- Bezier2 (ImageTracer) Defaults ---
    private static final int DEFAULT_B2_COLORS = 16;
    private static final double DEFAULT_B2_LTRES = 1.0;
    private static final double DEFAULT_B2_QTRES = 1.0;
    private static final int DEFAULT_B2_PATHOMIT = 8;

    // --- Stroke Defaults ---
    private static final String DEFAULT_STROKE_COLOR = "black";
    private static final double DEFAULT_STROKE_WIDTH = 1.0;

    // --- Centerline Defaults ---
    private static final int DEFAULT_CL_THRESHOLD = 128;

    // --- Paint by Numbers Defaults ---
    private static final int DEFAULT_PBN_MIN_AREA = 100;
    private static final int DEFAULT_PBN_FONT_SIZE = 14;

    private final Options options = new Options();
    private final Map<String, VectorizationStrategy> strategyMap = new HashMap<>();

    public CliParser() {
        // --- Register vectorization strategies ---
        strategyMap.put("raw", new RawContourStrategy());
        strategyMap.put("dp", new DouglasPeuckerStrategy());
        strategyMap.put("line", new StraightLineStrategy());
        strategyMap.put("convexhull", new ConvexHullStrategy());
        strategyMap.put("bezier", new BezierStrategy());
        strategyMap.put("bezier2", new ImageTracerStrategy());
        strategyMap.put("centerline", new SkeletonStrategy());
        strategyMap.put("pbn", new PaintByNumbersStrategy());

        StringBuilder sb = new StringBuilder("Vectorization strategy. Available: ");
        strategyMap.keySet().stream().sorted().forEach(key -> sb.append(key).append(" | "));
        String strategyHelp = sb.substring(0, sb.length() - 3); // remove last " | "

        // --- CLI options ---
        options.addOption(Option.builder("i")
                .longOpt("input")
                .hasArg()
                .desc("Input image path (JPG, PNG, etc.). Required unless --config or --input-dir is used.")
                .build());

        options.addOption(Option.builder("o")
                .longOpt("output")
                .hasArg()
                .desc("Output SVG file path. (Optional: defaults to input_timestamp.svg)")
                .build());

        options.addOption(Option.builder("h")
                .longOpt("help")
                .desc("Print this help message")
                .build());

        // --- Vectorization Options (Polyline strategies) ---
        options.addOption(Option.builder("s")
                .longOpt("strategy")
                .hasArg()
                .desc(strategyHelp + " (default: dp)")
                .build());

        options.addOption(Option.builder("t")
                .longOpt("tolerance")
                .hasArg()
                .type(Number.class)
                .desc("Simplification tolerance (pixels). Default: " + DEFAULT_TOLERANCE)
                .build());

        options.addOption(Option.builder("d")
                .longOpt("detail")
                .hasArg()
                .type(Number.class)
                .desc("Detail level 0.0 (coarse) → 1.0 (full). Default: " + DEFAULT_DETAIL)
                .build());

        options.addOption(Option.builder()
                .longOpt("minlen")
                .hasArg()
                .type(Number.class)
                .desc("Minimum line length (pixels). Default: " + DEFAULT_MIN_LEN)
                .build());

        options.addOption(Option.builder()
                .longOpt("maxlen")
                .hasArg()
                .type(Number.class)
                .desc("Maximum line length (pixels). Default: " + DEFAULT_MAX_LEN)
                .build());

        options.addOption(Option.builder()
                .longOpt("canny-blur")
                .hasArg()
                .type(Number.class)
                .desc("Canny: Gaussian blur sigma. Default: " + DEFAULT_CANNY_BLUR)
                .build());

        options.addOption(Option.builder()
                .longOpt("canny-low")
                .hasArg()
                .type(Number.class)
                .desc("Canny: low threshold (0.0-1.0). Default: " + DEFAULT_CANNY_LOW)
                .build());

        options.addOption(Option.builder()
                .longOpt("canny-high")
                .hasArg()
                .type(Number.class)
                .desc("Canny: high threshold (0.0-1.0). Default: " + DEFAULT_CANNY_HIGH)
                .build());

        // --- Bezier Strategy Options ---
        options.addOption(Option.builder()
                .longOpt("bezier-detail")
                .hasArg()
                .type(Number.class)
                .desc("Bezier: Detail (pixels per node). Smaller = more detail. Default: " + DEFAULT_BEZIER_DETAIL)
                .build());

        options.addOption(Option.builder()
                .longOpt("bezier-colors")
                .hasArg()
                .type(Number.class)
                .desc("Bezier: Number of colors to quantize. Fewer colors = simpler image. Default: "
                        + DEFAULT_BEZIER_COLORS)
                .build());

        // --- Bezier2 (ImageTracer) Options ---
        options.addOption(Option.builder()
                .longOpt("b2-colors") // <-- NEW
                .hasArg()
                .type(Number.class)
                .desc("bezier2: Number of colors to quantize. Default: " + DEFAULT_B2_COLORS)
                .build());

        options.addOption(Option.builder()
                .longOpt("b2-ltres") // <-- NEW
                .hasArg()
                .type(Number.class)
                .desc("bezier2: Line threshold. Default: " + DEFAULT_B2_LTRES)
                .build());

        options.addOption(Option.builder()
                .longOpt("b2-qtres") // <-- NEW
                .hasArg()
                .type(Number.class)
                .desc("bezier2: Quadratic curve threshold. Default: " + DEFAULT_B2_QTRES)
                .build());

        options.addOption(Option.builder()
                .longOpt("b2-pathomit") // <-- NEW
                .hasArg()
                .type(Number.class)
                .desc("bezier2: Omit paths shorter than this (pixels). Default: " + DEFAULT_B2_PATHOMIT)
                .build());

        options.addOption(Option.builder() // <-- NEW
                .longOpt("b2-outline")
                .desc("bezier2: Output outlines (strokes) instead of filled shapes.")
                .build());

        // --- Stroke / Output Style Options ---
        options.addOption(Option.builder()
                .longOpt("stroke-color")
                .hasArg()
                .desc("Stroke color (black, white, red, blue, green, gray). Default: " + DEFAULT_STROKE_COLOR)
                .build());

        options.addOption(Option.builder()
                .longOpt("stroke-width")
                .hasArg()
                .type(Number.class)
                .desc("Stroke width (0.1-5.0). Default: " + DEFAULT_STROKE_WIDTH)
                .build());

        options.addOption(Option.builder()
                .longOpt("smooth-curves")
                .desc("Convert polylines to smooth cubic Bezier curves (Canny strategies).")
                .build());

        // --- Centerline Options ---
        options.addOption(Option.builder()
                .longOpt("cl-threshold")
                .hasArg()
                .type(Number.class)
                .desc("Centerline: Binary threshold (0-255). Pixels darker than this are traced. Default: "
                        + DEFAULT_CL_THRESHOLD)
                .build());

        // --- Config Rerun Option ---
        options.addOption(Option.builder()
                .longOpt("config")
                .hasArg()
                .desc("Path to a JSON sidecar file to replay parameters from a previous run.")
                .build());

        // --- Batch Processing Options ---
        options.addOption(Option.builder()
                .longOpt("input-dir")
                .hasArg()
                .desc("Process all images (PNG, JPG, JPEG, BMP) in this directory.")
                .build());

        options.addOption(Option.builder()
                .longOpt("output-dir")
                .hasArg()
                .desc("Output directory for batch processing. Default: same as input-dir.")
                .build());

        // --- Export Format Option ---
        options.addOption(Option.builder()
                .longOpt("format")
                .hasArg()
                .desc("Output format: svg, png, pdf. Default: svg")
                .build());

        // --- Auto Canny Option ---
        options.addOption(Option.builder()
                .longOpt("canny-auto")
                .desc("Automatically compute optimal Canny thresholds from image histogram.")
                .build());

        // --- Color-Aware Canny Option ---
        options.addOption(Option.builder()
                .longOpt("color-edges")
                .desc("Run Canny on R/G/B channels and merge edges. Catches hue-boundary edges that grayscale misses.")
                .build());

        // --- Paint by Numbers Options ---
        // --- Crop Option ---
        options.addOption(Option.builder()
                .longOpt("crop")
                .hasArg()
                .desc("Crop region as x,y,w,h (pixels). Only the specified sub-region will be vectorized.")
                .build());

        options.addOption(Option.builder()
                .longOpt("pbn-colors")
                .hasArg()
                .desc("PBN: Comma-separated hex colors (e.g. FF0000,00FF00,0000FF). If omitted, colors are auto-extracted from the image.")
                .build());

        options.addOption(Option.builder()
                .longOpt("pbn-num-colors")
                .hasArg()
                .type(Number.class)
                .desc("PBN: Number of colors to auto-extract from image (used when --pbn-colors is omitted). Default: 6")
                .build());

        options.addOption(Option.builder()
                .longOpt("pbn-min-area")
                .hasArg()
                .type(Number.class)
                .desc("PBN: Minimum region area in pixels. Smaller regions are merged. Default: " + DEFAULT_PBN_MIN_AREA)
                .build());

        options.addOption(Option.builder()
                .longOpt("pbn-font-size")
                .hasArg()
                .type(Number.class)
                .desc("PBN: Font size for region number labels. Default: " + DEFAULT_PBN_FONT_SIZE)
                .build());

        options.addOption(Option.builder()
                .longOpt("pbn-no-numbers")
                .desc("PBN: Omit region number labels from output.")
                .build());

        options.addOption(Option.builder()
                .longOpt("pbn-no-legend")
                .desc("PBN: Omit color legend from output.")
                .build());

    }

    // --- Centerline Getters ---
    public int getClThreshold(CommandLine cmd) {
        return Math.max(0, Math.min(255, (int) Math.round(getDouble(cmd, "cl-threshold", DEFAULT_CL_THRESHOLD))));
    }

    // --- Parse CLI ---
    public CommandLine parse(String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
            if (cmd.hasOption("h")) {
                printHelp();
                return cmd; // Return immediately, Main.java will exit
            }

            // Validate that input is provided unless config or input-dir is used
            if (!cmd.hasOption("i") && !cmd.hasOption("config") && !cmd.hasOption("input-dir")) {
                throw new ParseException("Missing required option: -i/--input (or use --config/--input-dir)");
            }

            // Cross-validate Canny thresholds (only if not using bezier)
            if (!"bezier".equals(cmd.getOptionValue("s"))) {
                double low = getCannyLow(cmd);
                double high = getCannyHigh(cmd);
                if (low >= high) {
                    throw new ParseException(String.format(
                            "Invalid Canny thresholds: --canny-low (%.3f) must be less than --canny-high (%.3f)",
                            low, high));
                }
            }

        } catch (ParseException e) {
            System.err.println("Error parsing arguments: " + e.getMessage());
            printHelp();
            throw e;
        }
        return cmd;
    }

    public void printHelp() {
        final String HEADER = """
                --- BoofCV-Batik Vectorizer CLI ---
                Converts JPG/PNG to SVG using edge detection and vectorization.
                """;
        final String FOOTER = """

                Examples:
                  java -jar vectorizer.jar -i in.jpg -o out.svg -s dp -t 1.5 -d 0.8
                  java -jar vectorizer.jar -i in.png -o out.svg -s line --minlen 10 --maxlen 800
                  java -jar vectorizer.jar -i noisy.png -o clean.svg --canny-blur 2.5 --canny-low 0.05 --canny-high 0.15
                  java -jar vectorizer.jar -i in.png -s bezier --bezier-detail 5
                  java -jar vectorizer.jar -i in.png -s bezier2 --b2-colors 8 --b2-ltres 2.0 --b2-pathomit 10
                """;
        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(120);
        formatter.printHelp("java -jar vectorizer.jar", HEADER, options, FOOTER, true);
    }

    // --- Strategy selection ---
    public VectorizationStrategy getStrategy(String strategyName) {
        if (strategyName == null || strategyName.isEmpty())
            return strategyMap.get("dp");
        VectorizationStrategy strategy = strategyMap.get(strategyName.toLowerCase());
        if (strategy == null)
            throw new IllegalArgumentException("Unknown strategy: " + strategyName);
        return strategy;
    }

    // --- Getters with validation and defaults ---

    private double getDouble(CommandLine cmd, String opt, double defaultValue) {
        if (cmd.hasOption(opt)) {
            try {
                return ((Number) cmd.getParsedOptionValue(opt)).doubleValue();
            } catch (ParseException e) {
                // This shouldn't happen with type(Number.class) but good to guard.
                throw new RuntimeException("Invalid number format for " + opt, e);
            }
        }
        return defaultValue;
    }

    public double getTolerance(CommandLine cmd) {
        return Math.max(0.0, getDouble(cmd, "t", DEFAULT_TOLERANCE));
    }

    public double getDetailLevel(CommandLine cmd) {
        double detail = getDouble(cmd, "d", DEFAULT_DETAIL);
        return Math.max(0.0, Math.min(1.0, detail)); // clamp 0–1
    }

    public double getMinLength(CommandLine cmd) {
        return Math.max(0.0, getDouble(cmd, "minlen", DEFAULT_MIN_LEN));
    }

    public double getMaxLength(CommandLine cmd) {
        return Math.max(0.0, getDouble(cmd, "maxlen", DEFAULT_MAX_LEN));
    }

    public double getCannyBlur(CommandLine cmd) {
        // Blur sigma must be > 0
        return Math.max(0.1, getDouble(cmd, "canny-blur", DEFAULT_CANNY_BLUR));
    }

    public double getCannyLow(CommandLine cmd) {
        double low = getDouble(cmd, "canny-low", DEFAULT_CANNY_LOW);
        // Clamp to (0.0, 1.0)
        return Math.max(0.001, Math.min(0.999, low));
    }

    public double getCannyHigh(CommandLine cmd) {
        double high = getDouble(cmd, "canny-high", DEFAULT_CANNY_HIGH);
        // Clamp to (0.0, 1.0)
        return Math.max(0.001, Math.min(0.999, high));
    }

    public int getBezierDetail(CommandLine cmd) {
        // This is 'pixelsPerNode'. Must be at least 1.
        return Math.max(1, (int) Math.round(getDouble(cmd, "bezier-detail", DEFAULT_BEZIER_DETAIL)));
    }

    public int getBezierColors(CommandLine cmd) {
        // This is 'maxColors'. Must be at least 2.
        return Math.max(2, (int) Math.round(getDouble(cmd, "bezier-colors", DEFAULT_BEZIER_COLORS)));
    }

    // --- Bezier2 (ImageTracer) Getters ---
    public int getB2Colors(CommandLine cmd) { // <-- NEW
        return Math.max(2, (int) Math.round(getDouble(cmd, "b2-colors", DEFAULT_B2_COLORS)));
    }

    public double getB2Ltres(CommandLine cmd) { // <-- NEW
        return Math.max(0.0, getDouble(cmd, "b2-ltres", DEFAULT_B2_LTRES));
    }

    public double getB2Qtres(CommandLine cmd) { // <-- NEW
        return Math.max(0.0, getDouble(cmd, "b2-qtres", DEFAULT_B2_QTRES));
    }

    public int getB2PathOmit(CommandLine cmd) { // <-- NEW
        return Math.max(0, (int) Math.round(getDouble(cmd, "b2-pathomit", DEFAULT_B2_PATHOMIT)));
    }

    public boolean getB2Outline(CommandLine cmd) {
        return cmd.hasOption("b2-outline");
    }

    // --- Stroke / Output Style Getters ---
    public String getStrokeColor(CommandLine cmd) {
        return cmd.getOptionValue("stroke-color", DEFAULT_STROKE_COLOR);
    }

    public double getStrokeWidth(CommandLine cmd) {
        return Math.max(0.1, Math.min(5.0, getDouble(cmd, "stroke-width", DEFAULT_STROKE_WIDTH)));
    }

    public boolean getSmoothCurves(CommandLine cmd) {
        return cmd.hasOption("smooth-curves");
    }

    public boolean getCannyAuto(CommandLine cmd) {
        return cmd.hasOption("canny-auto");
    }

    public boolean getColorEdges(CommandLine cmd) {
        return cmd.hasOption("color-edges");
    }

    // --- Paint by Numbers Getters ---
    public java.awt.Color[] getPbnColors(CommandLine cmd) {
        String val = cmd.getOptionValue("pbn-colors");
        if (val == null || val.isEmpty()) {
            return null;
        }
        String[] parts = val.split(",");
        java.awt.Color[] colors = new java.awt.Color[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String hex = parts[i].trim();
            if (hex.startsWith("#")) hex = hex.substring(1);
            colors[i] = new java.awt.Color(Integer.parseInt(hex, 16));
        }
        return colors;
    }

    public int getPbnNumColors(CommandLine cmd) {
        return Math.max(2, Math.min(20, (int) Math.round(getDouble(cmd, "pbn-num-colors", 6))));
    }

    public int getPbnMinArea(CommandLine cmd) {
        return Math.max(1, (int) Math.round(getDouble(cmd, "pbn-min-area", DEFAULT_PBN_MIN_AREA)));
    }

    public int getPbnFontSize(CommandLine cmd) {
        return Math.max(4, (int) Math.round(getDouble(cmd, "pbn-font-size", DEFAULT_PBN_FONT_SIZE)));
    }

    public boolean isPbnNoNumbers(CommandLine cmd) {
        return cmd.hasOption("pbn-no-numbers");
    }

    public boolean isPbnNoLegend(CommandLine cmd) {
        return cmd.hasOption("pbn-no-legend");
    }

    private static java.awt.Color[] getDefaultPbnPalette() {
        java.awt.Color[] palette = new java.awt.Color[6];
        for (int i = 0; i < 6; i++) {
            palette[i] = java.awt.Color.getHSBColor(i / 6f, 0.8f, 0.9f);
        }
        return palette;
    }

    public java.awt.Rectangle getCrop(CommandLine cmd) {
        String val = cmd.getOptionValue("crop");
        if (val == null || val.isEmpty()) return null;
        String[] parts = val.split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("--crop requires exactly 4 comma-separated values: x,y,w,h");
        }
        int x = Integer.parseInt(parts[0].trim());
        int y = Integer.parseInt(parts[1].trim());
        int w = Integer.parseInt(parts[2].trim());
        int h = Integer.parseInt(parts[3].trim());
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("--crop width and height must be positive");
        }
        return new java.awt.Rectangle(x, y, w, h);
    }

    public String getFormat(CommandLine cmd) {
        return cmd.getOptionValue("format", "svg").toLowerCase();
    }

    public String getConfigPath(CommandLine cmd) {
        return cmd.getOptionValue("config");
    }

    public String getInputDir(CommandLine cmd) {
        return cmd.getOptionValue("input-dir");
    }

    public String getOutputDir(CommandLine cmd) {
        return cmd.getOptionValue("output-dir");
    }

    /**
     * Loads a JSON sidecar config file and creates a synthetic CommandLine
     * with the parameters from the previous run.
     */
    public CommandLine loadConfigFile(String configPath) throws ParseException {
        try {
            String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(configPath)));
            org.json.JSONObject json = new org.json.JSONObject(content);

            java.util.List<String> argList = new java.util.ArrayList<>();

            // Input path
            if (json.has("sourceImage")) {
                argList.add("-i");
                argList.add(json.getString("sourceImage"));
            }

            // Strategy
            if (json.has("strategy")) {
                argList.add("-s");
                argList.add(json.getString("strategy"));
            }

            // Canny/Polyline params
            if (json.has("tolerance")) {
                argList.add("-t");
                argList.add(String.valueOf(json.getDouble("tolerance")));
            }
            if (json.has("detail")) {
                argList.add("-d");
                argList.add(String.valueOf(json.getDouble("detail")));
            }
            if (json.has("minLength")) {
                argList.add("--minlen");
                argList.add(String.valueOf(json.getDouble("minLength")));
            }
            if (json.has("maxLength")) {
                argList.add("--maxlen");
                argList.add(String.valueOf(json.getDouble("maxLength")));
            }
            if (json.has("cannyBlur")) {
                argList.add("--canny-blur");
                argList.add(String.valueOf(json.getDouble("cannyBlur")));
            }
            if (json.has("cannyLow")) {
                argList.add("--canny-low");
                argList.add(String.valueOf(json.getDouble("cannyLow")));
            }
            if (json.has("cannyHigh")) {
                argList.add("--canny-high");
                argList.add(String.valueOf(json.getDouble("cannyHigh")));
            }
            if (json.has("strokeColor")) {
                argList.add("--stroke-color");
                argList.add(json.getString("strokeColor"));
            }
            if (json.has("strokeWidth")) {
                argList.add("--stroke-width");
                argList.add(String.valueOf(json.getDouble("strokeWidth")));
            }
            if (json.has("smoothCurves") && json.getBoolean("smoothCurves")) {
                argList.add("--smooth-curves");
            }

            // Bezier params
            if (json.has("bezier")) {
                org.json.JSONObject bz = json.getJSONObject("bezier");
                if (bz.has("detail")) {
                    argList.add("--bezier-detail");
                    argList.add(String.valueOf(bz.getInt("detail")));
                }
                if (bz.has("colors")) {
                    argList.add("--bezier-colors");
                    argList.add(String.valueOf(bz.getInt("colors")));
                }
            }

            // Bezier2 params
            if (json.has("bezier2")) {
                org.json.JSONObject b2 = json.getJSONObject("bezier2");
                if (b2.has("colors")) {
                    argList.add("--b2-colors");
                    argList.add(String.valueOf(b2.getInt("colors")));
                }
                if (b2.has("ltres")) {
                    argList.add("--b2-ltres");
                    argList.add(String.valueOf(b2.getDouble("ltres")));
                }
                if (b2.has("qtres")) {
                    argList.add("--b2-qtres");
                    argList.add(String.valueOf(b2.getDouble("qtres")));
                }
                if (b2.has("pathomit")) {
                    argList.add("--b2-pathomit");
                    argList.add(String.valueOf(b2.getInt("pathomit")));
                }
                if (b2.has("outline") && b2.getBoolean("outline")) {
                    argList.add("--b2-outline");
                }
            }

            // Centerline params
            if (json.has("clThreshold")) {
                argList.add("--cl-threshold");
                argList.add(String.valueOf(json.getInt("clThreshold")));
            }

            // Paint by Numbers params
            if (json.has("pbn")) {
                org.json.JSONObject pbn = json.getJSONObject("pbn");
                if (pbn.has("colors")) { argList.add("--pbn-colors"); argList.add(pbn.getString("colors")); }
                if (pbn.has("minArea")) { argList.add("--pbn-min-area"); argList.add(String.valueOf(pbn.getInt("minArea"))); }
                if (pbn.has("fontSize")) { argList.add("--pbn-font-size"); argList.add(String.valueOf(pbn.getInt("fontSize"))); }
                if (pbn.has("noNumbers") && pbn.getBoolean("noNumbers")) { argList.add("--pbn-no-numbers"); }
                if (pbn.has("noLegend") && pbn.getBoolean("noLegend")) { argList.add("--pbn-no-legend"); }
            }

            String[] args = argList.toArray(new String[0]);
            CommandLineParser parser = new DefaultParser();
            return parser.parse(options, args);

        } catch (java.io.IOException e) {
            throw new ParseException("Could not read config file: " + e.getMessage());
        } catch (org.json.JSONException e) {
            throw new ParseException("Invalid JSON in config file: " + e.getMessage());
        }
    }

    /**
     * Returns the Options object for external use (e.g., making input optional for config mode).
     */
    public Options getOptions() {
        return options;
    }

}