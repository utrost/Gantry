package org.trostheide.gantry.vectorize;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import java.awt.image.BufferedImage;
import java.io.File;
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
            // --- CONFIG RERUN MODE ---
            String configPath = cli.getConfigPath(cmd);
            if (configPath != null) {
                System.out.println("Loading parameters from config: " + configPath);
                CommandLine configCmd = cli.loadConfigFile(configPath);
                // Merge: CLI args override config, but config provides defaults.
                // If user also specified -o, use that; otherwise auto-generate.
                if (cmd.hasOption("o")) {
                    // Build a merged command by re-parsing with output override
                    List<String> mergedArgs = new ArrayList<>();
                    for (String arg : rebuildArgs(configCmd)) {
                        mergedArgs.add(arg);
                    }
                    mergedArgs.add("-o");
                    mergedArgs.add(cmd.getOptionValue("o"));
                    cmd = cli.parse(mergedArgs.toArray(new String[0]));
                } else {
                    cmd = configCmd;
                }
            }

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
            System.err.println("Not a directory: " + inputDir);
            System.exit(1);
        }

        File outDir = new File(outputDir);
        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        File[] imageFiles = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".bmp");
        });

        if (imageFiles == null || imageFiles.length == 0) {
            System.out.println("No image files found in: " + inputDir);
            return;
        }

        System.out.printf("Batch processing %d images from %s → %s%n", imageFiles.length, inputDir, outputDir);

        int success = 0;
        int failed = 0;
        for (File imageFile : imageFiles) {
            try {
                String baseName = getBaseFileName(imageFile.getName());
                String svgPath = Paths.get(outputDir, baseName + ".svg").toString();
                String jsonPath = Paths.get(outputDir, baseName + ".json").toString();

                System.out.printf("%n--- Processing: %s ---%n", imageFile.getName());

                // Build a per-file command with input and output overridden
                List<String> fileArgs = rebuildArgs(cmd);
                // Remove any existing -i and -o
                fileArgs = removeOption(fileArgs, "-i");
                fileArgs = removeOption(fileArgs, "-o");
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
            }
        }

        System.out.printf("%nBatch complete: %d succeeded, %d failed%n", success, failed);
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
        runSingleFile(cli, cli.parse(args));
    }

    /**
     * Processes a single image file (the core workflow).
     */
    public static void runSingleFile(CliParser cli, CommandLine cmd) throws Exception {
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

    /**
     * Rebuilds CLI args from a parsed CommandLine for re-parsing with modifications.
     */
    private static List<String> rebuildArgs(CommandLine cmd) {
        List<String> args = new ArrayList<>();
        for (var opt : cmd.getOptions()) {
            if (opt.getLongOpt() != null) {
                args.add("--" + opt.getLongOpt());
            } else {
                args.add("-" + opt.getOpt());
            }
            if (opt.hasArg() && opt.getValue() != null) {
                args.add(opt.getValue());
            }
        }
        return args;
    }

    /**
     * Removes an option (and its value) from an arg list.
     */
    private static List<String> removeOption(List<String> args, String optName) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).equals(optName) || args.get(i).equals("--" + optName.replaceFirst("^-+", ""))) {
                // Skip this arg and its value if it has one
                if (i + 1 < args.size() && !args.get(i + 1).startsWith("-")) {
                    i++; // skip value too
                }
            } else {
                result.add(args.get(i));
            }
        }
        return result;
    }
}
