package org.trostheide.gantry.vectorize;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import org.apache.commons.cli.Option;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.imageio.ImageIO;

import georegression.struct.point.Point2D_I32;

import org.trostheide.gantry.vectorize.strategies.*;

public class Main {

    public static void main(String[] args) {
        CliParser cli = new CliParser();
        CommandLine cmd;

        if (args.length == 0) {
            // Launch GUI mode
            org.trostheide.gantry.vectorize.gui.VectorizerGUI.launch();
            return;
        }

        try {
            cmd = cli.parse(args);
            if (cmd.hasOption("h"))
                return;
        } catch (ParseException e) {
            System.err.println("Error parsing command line arguments: " + e.getMessage());
            System.exit(1);
            return;
        }

        try {
            cmd = resolveConfig(cli, cmd);

            // --- BATCH PROCESSING MODE ---
            String inputDir = cli.getInputDir(cmd);
            if (inputDir != null) {
                runBatchProcessing(cli, cmd, inputDir);
                return;
            }

            // --- SINGLE FILE MODE ---
            runSingleFile(cli, cmd);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Processes all image files in a directory with the same parameters.
     */
    private static void runBatchProcessing(CliParser cli, CommandLine cmd, String inputDir) throws Exception {
        String outputDir = cli.getOutputDir(cmd);
        if (outputDir == null) {
            outputDir = inputDir;
        }

        File dir = new File(inputDir);
        if (!dir.isDirectory()) {
            throw new IOException("Not a directory: " + inputDir);
        }

        File outDir = new File(outputDir);

        File[] imageFiles = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".bmp");
        });

        if (imageFiles == null) throw new IOException("Could not list input directory: " + inputDir);
        if (imageFiles.length == 0) {
            System.out.println("No image files found in: " + inputDir);
            return;
        }

        Arrays.sort(imageFiles, Comparator.comparing((File f) -> f.getName().toLowerCase(Locale.ROOT))
                .thenComparing(File::getName));
        preflightBatchOutputs(imageFiles, outDir, cli.getFormat(cmd));
        Files.createDirectories(outDir.toPath());
        System.out.printf("Batch processing %d images from %s → %s%n", imageFiles.length, inputDir, outputDir);

        int success = 0;
        int failed = 0;
        List<String> failedNames = new ArrayList<>();
        for (File imageFile : imageFiles) {
            try {
                String baseName = getBaseFileName(imageFile.getName());
                String svgPath = Paths.get(outputDir, baseName + ".svg").toString();

                System.out.printf("%n--- Processing: %s ---%n", imageFile.getName());

                List<String> fileArgs = rebuildArgs(cmd,
                        Set.of("input", "output", "input-dir", "output-dir", "config"));
                fileArgs.add("-i");
                fileArgs.add(imageFile.getAbsolutePath());
                fileArgs.add("-o");
                fileArgs.add(svgPath);

                CommandLine fileCmd = cli.parse(fileArgs.toArray(new String[0]));
                runSingleFile(cli, fileCmd);
                success++;
            } catch (Exception e) {
                System.err.printf("Failed to process %s: %s%n", imageFile.getName(), e.getMessage());
                failed++;
                failedNames.add(imageFile.getName());
            }
        }

        System.out.printf("%nBatch complete: %d succeeded, %d failed%n", success, failed);
        if (failed > 0) throw new IOException("Batch failures (" + success + " succeeded, " + failed
                + " failed): " + String.join(", ", failedNames));
    }

    /**
     * Programmatic entry point for embedders (e.g. the Gantry GUI): runs the single-image
     * workflow from a CLI-style argument array, reusing the exact option parsing and
     * strategy dispatch as the command line. Unlike {@link #main(String[])} it never calls
     * {@code System.exit}; failures surface as exceptions for the caller to handle.
     *
     * @param args the same options accepted on the command line (must include {@code -i};
     *             {@code -o} is recommended so the SVG path is deterministic).
     */
    public static void runSingleFile(String[] args) throws Exception {
        CliParser cli = new CliParser();
        runSingleFile(cli, resolveConfig(cli, cli.parse(args)));
    }

    /**
     * Processes a single image file (the core workflow).
     */
    public static void runSingleFile(CliParser cli, CommandLine cmd) throws Exception {
        cmd = resolveConfig(cli, cmd);
        // Diagnostic edge maps are written only with --debug (to the temp dir, not the CWD).
        BoofcvBatikVector.DEBUG = cmd.hasOption("debug");

        // --- Get all parameters from CLI ---
        String inputPath = cmd.getOptionValue("i");
        VectorizationStrategy strategy = cli.getStrategy(cmd.getOptionValue("s"));

        // Polyline/Canny parameters
        double tolerance = cli.getTolerance(cmd);
        double detailFactor = cli.getDetailLevel(cmd);
        double minLength = cli.getMinLength(cmd);
        double maxLength = cli.getMaxLength(cmd);
        double cannyBlur = cli.getCannyBlur(cmd);
        double cannyLow = cli.getCannyLow(cmd);
        double cannyHigh = cli.getCannyHigh(cmd);

        // Bezier (DrPTrace) parameters
        int bezierDetail = cli.getBezierDetail(cmd);
        int bezierColors = cli.getBezierColors(cmd);

        // Bezier2 (ImageTracer) parameters
        int b2Colors = cli.getB2Colors(cmd);
        double b2Ltres = cli.getB2Ltres(cmd);
        double b2Qtres = cli.getB2Qtres(cmd);
        int b2PathOmit = cli.getB2PathOmit(cmd);
        boolean b2Outline = cli.getB2Outline(cmd);

        // Stroke / output style parameters
        String strokeColor = cli.getStrokeColor(cmd);
        double strokeWidth = cli.getStrokeWidth(cmd);
        boolean smoothCurves = cli.getSmoothCurves(cmd);

        // Centerline parameters
        int clThreshold = cli.getClThreshold(cmd);
        double clTolerance = cli.getTolerance(cmd);

        // Export format
        String format = cli.getFormat(cmd);
        ExportUtil.requireFormatSupport(format);

        // Auto Canny
        boolean cannyAuto = cli.getCannyAuto(cmd);
        boolean colorEdges = cli.getColorEdges(cmd);

        // Paint by Numbers parameters
        java.awt.Color[] pbnColors = null;
        int pbnMinArea = 100;
        int pbnFontSize = 14;
        boolean pbnNoNumbers = false;
        boolean pbnNoLegend = false;
        if (strategy.getWorkflowType() == VectorizationStrategy.WorkflowType.PAINT_BY_NUMBERS) {
            pbnColors = cli.getPbnColors(cmd);
            pbnMinArea = cli.getPbnMinArea(cmd);
            pbnFontSize = cli.getPbnFontSize(cmd);
            pbnNoNumbers = cli.isPbnNoNumbers(cmd);
            pbnNoLegend = cli.isPbnNoLegend(cmd);
        }

        double squiggleDensity = cli.getSquiggleDensity(cmd);
        double squiggleAmplitude = cli.getSquiggleAmplitude(cmd);
        double squiggleTone = cli.getSquiggleTone(cmd);
        double squiggleBackground = cli.getSquiggleBackgroundSuppression(cmd);

        double needleSpacing = cli.getNeedleSpacing(cmd);
        double needleLength = cli.getNeedleLength(cmd);
        double needleThreshold = cli.getNeedleThreshold(cmd);
        double needleGradient = cli.getNeedleGradientThreshold(cmd);
        double needleTone = cli.getNeedleTone(cmd);

        int isolineLevels = cli.getIsolineLevels(cmd);
        double isolineSmoothing = cli.getIsolineSmoothing(cmd);
        int isolineMinLength = cli.getIsolineMinLength(cmd);
        double isolineThreshold = cli.getIsolineThreshold(cmd);

        int sketchWindow = cli.getSketchWindow(cmd);
        double sketchOffset = cli.getSketchOffset(cmd);
        int sketchMinLength = cli.getSketchMinLength(cmd);
        boolean sketchSkeleton = cli.getSketchSkeleton(cmd);

        // --- Determine Output Paths ---
        String svgOutputPath;
        String jsonOutputPath;

        if (cmd.hasOption("o")) {
            String userPath = cmd.getOptionValue("o");
            if (userPath.toLowerCase().endsWith(".svg")) {
                svgOutputPath = userPath;
                jsonOutputPath = userPath + ".json";
            } else {
                svgOutputPath = userPath + ".svg";
                jsonOutputPath = userPath + ".json";
            }
        } else {
            String baseName = getBaseFileName(inputPath);
            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            String generatedName = baseName + "_" + timestamp;
            File inputFile = new File(inputPath);
            String parentDir = inputFile.getParent();
            if (parentDir == null) {
                parentDir = ".";
            }
            svgOutputPath = Paths.get(parentDir, generatedName + ".svg").toString();
            jsonOutputPath = Paths.get(parentDir, generatedName + ".json").toString();
        }

        // --- Load image ---
        BufferedImage image = ImageIO.read(new File(inputPath));
        if (image == null) {
            throw new java.io.IOException("Could not read input image: " + inputPath);
        }

        // --- Apply crop ---
        java.awt.Rectangle crop = cli.getCrop(cmd);
        if (crop != null) {
            System.out.printf("Cropping to region: x=%d y=%d w=%d h=%d%n", crop.x, crop.y, crop.width, crop.height);
            image = image.getSubimage(crop.x, crop.y, crop.width, crop.height);
        }

        // --- Auto palette extraction for PBN ---
        if (pbnColors == null && strategy.getWorkflowType() == VectorizationStrategy.WorkflowType.PAINT_BY_NUMBERS) {
            int numColors = cli.getPbnNumColors(cmd);
            System.out.println("Auto-extracting " + numColors + " colors from image...");
            pbnColors = PaintByNumbersProcessor.extractPalette(image, numColors);
        }

        // --- Auto Canny threshold computation ---
        if (cannyAuto && strategy.getWorkflowType() == VectorizationStrategy.WorkflowType.CANNY_CONTOUR) {
            AutoCannyThresholds auto = AutoCannyThresholds.compute(image);
            cannyLow = auto.getLow();
            cannyHigh = auto.getHigh();
        }

        // --- Print execution plan ---
        System.out.printf("""
                Vectorization Parameters:
                  Input:     %s
                  Strategy:  %s
                """, inputPath, strategy.getName());

        switch (strategy.getWorkflowType()) {
        case WHOLE_IMAGE_BEZIER -> System.out.printf("""
                      Bezier Detail: %d px
                      Bezier Colors: %d
                    """, bezierDetail, bezierColors);
        case WHOLE_IMAGE_IMAGETRACER -> System.out.printf("""
                      B2 Colors:   %d
                      B2 Ltres:    %.2f
                      B2 Qtres:    %.2f
                      B2 PathOmit: %d
                      B2 Outline:  %b
                    """, b2Colors, b2Ltres, b2Qtres, b2PathOmit, b2Outline);
        case SKELETON -> System.out.printf("""
                      CL Threshold: %d
                    """, clThreshold);
        case PAINT_BY_NUMBERS -> System.out.printf("""
                      PBN Colors:    %d
                      PBN Min Area:  %d
                      PBN Font Size: %d
                      Show Numbers:  %b
                      Show Legend:   %b
                    """, pbnColors.length, pbnMinArea, pbnFontSize, !pbnNoNumbers, !pbnNoLegend);
        case TONAL_SQUIGGLE -> System.out.printf("""
                      Row Spacing: %.1f px
                      Amplitude:   %.1f px
                      Density:     %.2f
                      Detail:      %.2f
                      Tone:        %.2f
                      Background:  %.2f
                    """, Math.max(3.0, tolerance * 4.0), squiggleAmplitude, squiggleDensity,
                    detailFactor, squiggleTone, squiggleBackground);
        case ORIENTED_NEEDLES -> System.out.printf("""
                      Spacing:     %.1f px
                      Length:      %.1f px
                      Threshold:   %.2f
                      Gradient:    %.2f
                      Tone:        %.2f
                    """, needleSpacing, needleLength, needleThreshold, needleGradient, needleTone);
        case TONAL_ISOLINES -> System.out.printf("""
                      Levels:      %d
                      Smoothing:   %.1f px
                      Min Length:  %d pts
                      Threshold:   %.2f
                    """, isolineLevels, isolineSmoothing, isolineMinLength, isolineThreshold);
        case SKETCH_TRACE -> System.out.printf("""
                      Window:      %d px
                      Offset:      %.2f
                      Min Length:  %d pts
                      Skeleton:    %b
                    """, sketchWindow, sketchOffset, sketchMinLength, sketchSkeleton);
        case CANNY_CONTOUR ->
            System.out.printf("""
                      Tolerance: %.2f
                      Detail:    %.2f
                      Min Len:   %.1f px
                      Max Len:   %.1f px
                      Canny Blur:  %.2f
                      Canny Low:   %.3f%s
                      Canny High:  %.3f%s
                    """, tolerance, detailFactor, minLength, maxLength,
                    cannyBlur, cannyLow, cannyAuto ? " (auto)" : "",
                    cannyHigh, cannyAuto ? " (auto)" : "");
        }

        System.out.printf("""

                  Writing SVG: %s
                  Writing JSON: %s
                """, svgOutputPath, jsonOutputPath);

        // --- WORKFLOW SPLIT ---
        switch (strategy.getWorkflowType()) {
            case WHOLE_IMAGE_BEZIER -> {
                System.out.println("Running Bezier (DrPTrace) trace strategy...");
                List<VectorGeometry> allPaths = BoofcvBatikVector.runBezierTrace(image, bezierDetail, bezierColors);
                BoofcvBatikVector.createSvgFileFromGeometry(
                        allPaths, image.getWidth(), image.getHeight(), svgOutputPath,
                        strokeColor, strokeWidth);
            }
            case WHOLE_IMAGE_IMAGETRACER -> {
                System.out.println("Running Bezier2 (ImageTracer) trace strategy...");
                BoofcvBatikVector.runAndWriteImageTracer(
                        image, b2Colors, b2Ltres, b2Qtres, b2PathOmit, b2Outline, svgOutputPath);
            }
            case SKELETON -> {
                System.out.println("Running Centerline (Skeleton) strategy...");
                List<VectorGeometry> geometry = SkeletonStrategy.vectoriseImage(image, clThreshold, clTolerance);
                BoofcvBatikVector.createSvgFileFromGeometry(geometry, image.getWidth(), image.getHeight(), svgOutputPath);
            }
            case PAINT_BY_NUMBERS -> {
                System.out.println("Running Paint by Numbers strategy...");
                PaintByNumbersProcessor.PbnResult result = PaintByNumbersProcessor.process(image, pbnColors, pbnMinArea, tolerance);
                PaintByNumbersProcessor.writeSvg(result, svgOutputPath, pbnFontSize, !pbnNoNumbers, !pbnNoLegend);
            }
            case TONAL_SQUIGGLE -> {
                System.out.println("Running Tonal Squiggle strategy...");
                TonalSquiggleProcessor.Options squiggleOptions = new TonalSquiggleProcessor.Options(
                        Math.max(3.0, tolerance * 4.0),
                        squiggleAmplitude,
                        detailFactor,
                        squiggleTone,
                        squiggleBackground,
                        squiggleDensity);
                List<VectorGeometry> geometry = TonalSquiggleProcessor.process(image, squiggleOptions);
                BoofcvBatikVector.createSvgFileFromGeometry(
                        geometry, image.getWidth(), image.getHeight(), svgOutputPath,
                        strokeColor, strokeWidth);
            }
            case ORIENTED_NEEDLES -> {
                System.out.println("Running Oriented Needles strategy...");
                OrientedNeedleProcessor.Options needleOptions = new OrientedNeedleProcessor.Options(
                        needleSpacing,
                        needleLength,
                        needleThreshold,
                        needleGradient,
                        needleTone);
                List<VectorGeometry> geometry = OrientedNeedleProcessor.process(image, needleOptions);
                BoofcvBatikVector.createSvgFileFromGeometry(
                        geometry, image.getWidth(), image.getHeight(), svgOutputPath,
                        strokeColor, strokeWidth);
            }
            case TONAL_ISOLINES -> {
                System.out.println("Running Tonal Isolines strategy...");
                TonalIsolineProcessor.Options isolineOptions = new TonalIsolineProcessor.Options(
                        isolineLevels,
                        isolineSmoothing,
                        isolineMinLength,
                        isolineThreshold);
                List<VectorGeometry> geometry = TonalIsolineProcessor.process(image, isolineOptions);
                BoofcvBatikVector.createSvgFileFromGeometry(
                        geometry, image.getWidth(), image.getHeight(), svgOutputPath,
                        strokeColor, strokeWidth);
            }
            case SKETCH_TRACE -> {
                System.out.println("Running Sketch Trace strategy...");
                SketchTraceProcessor.Options sketchOptions = new SketchTraceProcessor.Options(
                        sketchWindow,
                        sketchOffset,
                        sketchMinLength,
                        sketchSkeleton);
                List<VectorGeometry> geometry = SketchTraceProcessor.process(image, sketchOptions);
                BoofcvBatikVector.createSvgFileFromGeometry(
                        geometry, image.getWidth(), image.getHeight(), svgOutputPath,
                        strokeColor, strokeWidth);
            }
            case CANNY_CONTOUR -> {
                List<List<Point2D_I32>> contours = BoofcvBatikVector.extractContours(
                        image, (float) cannyBlur, (float) cannyLow, (float) cannyHigh, colorEdges);
                if (BoofcvBatikVector.DEBUG) {
                    double effectiveTolerance = strategy.computeEffectiveTolerance(tolerance, detailFactor);
                    BufferedImage debugSimplified = BoofcvBatikVector.renderSimplifiedContours(
                            contours, strategy, effectiveTolerance, detailFactor,
                            image.getWidth(), image.getHeight(), minLength, maxLength);
                    File debugFile = BoofcvBatikVector.debugFile("edges_debug_simplified.png");
                    ImageIO.write(debugSimplified, "PNG", debugFile);
                    System.out.println("Saved simplified debug: " + debugFile.getAbsolutePath());
                }
                BoofcvBatikVector.createSvgFileBatik(
                        contours, image.getWidth(), image.getHeight(), svgOutputPath,
                        strategy, tolerance, detailFactor, minLength, maxLength,
                        strokeColor, strokeWidth, smoothCurves);
            }
        }

        // --- SVG Optimization ---
        SvgOptimizer.optimize(svgOutputPath);

        // --- Export to other formats ---
        if ("png".equals(format)) {
            String pngPath = ExportUtil.getOutputPath(svgOutputPath, "png");
            ExportUtil.exportToPng(svgOutputPath, pngPath, 0);
        } else if ("pdf".equals(format)) {
            String pdfPath = ExportUtil.getOutputPath(svgOutputPath, "pdf");
            ExportUtil.exportToPdf(svgOutputPath, pdfPath);
        }

        // Write metadata sidecar file
        BoofcvBatikVector.writeMetadataSidecar(cmd, jsonOutputPath, new File(inputPath).getAbsolutePath());
        System.out.println("Saved metadata: " + jsonOutputPath);

        System.out.println("Vectorization complete!");
    }

    /**
     * Gets the base name of a file without its extension.
     */
    private static String getBaseFileName(String fullPath) {
        String baseName = new File(fullPath).getName();
        int dotIndex = baseName.lastIndexOf('.');
        if (dotIndex > 0) {
            return baseName.substring(0, dotIndex);
        } else {
            return baseName;
        }
    }

    /** Saved settings are defaults; each explicit CLI option replaces its canonical alias once. */
    static CommandLine resolveConfig(CliParser cli, CommandLine explicit) throws ParseException {
        String path = cli.getConfigPath(explicit);
        if (path == null) return explicit;
        Map<String, Option> merged = options(cli.loadConfigFile(path), Set.of());
        merged.putAll(options(explicit, Set.of("config")));
        return cli.parse(arguments(merged).toArray(new String[0]));
    }

    private static Map<String, Option> options(CommandLine cmd, Set<String> excluded) {
        Map<String, Option> result = new LinkedHashMap<>();
        for (Option option : cmd.getOptions()) {
            String key = option.getLongOpt() == null ? option.getOpt() : option.getLongOpt();
            if (!excluded.contains(key)) result.put(key, option);
        }
        return result;
    }

    private static List<String> arguments(Map<String, Option> options) {
        List<String> result = new ArrayList<>();
        for (Option option : options.values()) {
            result.add(option.getLongOpt() == null ? "-" + option.getOpt() : "--" + option.getLongOpt());
            if (option.hasArg() && option.getValue() != null) result.add(option.getValue());
        }
        return result;
    }

    private static List<String> rebuildArgs(CommandLine cmd, Set<String> excluded) {
        return arguments(options(cmd, excluded));
    }

    /** Reject ambiguous destinations and source overwrites before writing any batch artifact. */
    private static void preflightBatchOutputs(File[] inputs, File output, String format) throws IOException {
        Set<String> sources = new HashSet<>();
        for (File input : inputs) sources.add(input.getCanonicalPath().toLowerCase(Locale.ROOT));
        Set<String> targets = new HashSet<>();
        for (File input : inputs) {
            String base = getBaseFileName(input.getName());
            List<String> names = new ArrayList<>(List.of(base + ".svg", base + ".svg.json"));
            if (!"svg".equals(format)) names.add(base + "." + format);
            for (String name : names) {
                File target = new File(output, name);
                String key = target.getCanonicalPath().toLowerCase(Locale.ROOT);
                if (sources.contains(key)) throw new IOException("Batch output would overwrite input: " + target
                        + ". Choose a separate output directory.");
                if (!targets.add(key)) throw new IOException("Batch output name collision: " + target
                        + ". Rename duplicate input basenames or run them separately.");
            }
        }
    }
}
