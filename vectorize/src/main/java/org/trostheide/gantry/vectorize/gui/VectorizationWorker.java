package org.trostheide.gantry.vectorize.gui;

import org.trostheide.gantry.vectorize.VectorizationStrategy;
import org.trostheide.gantry.vectorize.AutoCannyThresholds;
import org.trostheide.gantry.vectorize.BoofcvBatikVector;
import org.trostheide.gantry.vectorize.SvgOptimizer;
import org.trostheide.gantry.vectorize.strategies.*;
import org.trostheide.gantry.vectorize.PaintByNumbersProcessor;
import org.trostheide.gantry.vectorize.VectorGeometry;
import georegression.struct.point.Point2D_I32;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

public class VectorizationWorker extends SwingWorker<File, Void> {

    public interface Callback {
        void onVectorizationComplete(File svgFile, BufferedImage edgeImage);

        void onError(Exception e);

        default void onProgress(String stage) {}

        default void onAutoCannyComputed(float low, float high) {}
    }

    private final BufferedImage sourceImage;
    private final ControlsPanel controls;
    private final Callback callback;
    private BufferedImage edgeImage;
    private float autoCannyLow = -1;
    private float autoCannyHigh = -1;

    public VectorizationWorker(BufferedImage sourceImage, ControlsPanel controls, Callback callback) {
        this.sourceImage = sourceImage;
        this.controls = controls;
        this.callback = callback;
    }

    private void reportProgress(String stage) {
        if (callback != null) {
            callback.onProgress(stage);
        }
    }

    @Override
    protected File doInBackground() throws Exception {
        if (sourceImage == null)
            return null;

        // Create a temp file for output
        File tempFile = File.createTempFile("vector_preview_", ".svg");
        tempFile.deleteOnExit();
        String outputPath = tempFile.getAbsolutePath();

        String strategyName = controls.getStrategy();

        // Map string to Strategy object
        VectorizationStrategy strategy = switch (strategyName) {
            case "dp" -> new DouglasPeuckerStrategy();
            case "line" -> new StraightLineStrategy();
            case "raw" -> new RawContourStrategy();
            case "convexhull" -> new ConvexHullStrategy();
            case "bezier" -> new BezierStrategy();
            case "bezier2" -> new ImageTracerStrategy();
            case "centerline" -> new SkeletonStrategy();
            case "pbn" -> new PaintByNumbersStrategy();
            default -> new DouglasPeuckerStrategy();
        };

        // --- Workflow Logic (Mirroring Main.java) ---

        switch (strategy.getWorkflowType()) {
            case WHOLE_IMAGE_BEZIER -> {
                if (isCancelled()) return null;
                reportProgress("Tracing with DrPTrace...");
                int smoothness = controls.getDetailRaw();
                String strokeColor = controls.getStrokeColor();
                double strokeWidth = controls.getStrokeWidth();
                List<VectorGeometry> allPaths = BoofcvBatikVector.runBezierTrace(
                        sourceImage, smoothness, controls.getColors());
                if (isCancelled()) return null;
                reportProgress("Writing SVG...");
                BoofcvBatikVector.createSvgFileFromGeometry(
                        allPaths, sourceImage.getWidth(), sourceImage.getHeight(), outputPath,
                        strokeColor, strokeWidth);
            }
            case WHOLE_IMAGE_IMAGETRACER -> {
                if (isCancelled()) return null;
                reportProgress("Quantizing colors...");
                int pathOmit = controls.getSpeckle();
                double ltres = controls.getB2Ltres();
                double qtres = controls.getB2Qtres();
                boolean outline = controls.isB2Outline();

                if (isCancelled()) return null;
                reportProgress("Tracing with ImageTracer...");
                BoofcvBatikVector.runAndWriteImageTracer(
                        sourceImage, controls.getColors(), ltres, qtres, pathOmit, outline, outputPath);
            }
            case SKELETON -> {
                if (isCancelled()) return null;
                reportProgress("Binarizing & skeletonizing...");
                int threshold = controls.getClThreshold();
                double tolerance = controls.getTolerance();

                List<VectorGeometry> allPaths = SkeletonStrategy.vectoriseImage(
                        sourceImage, threshold, tolerance);

                if (isCancelled()) return null;
                reportProgress("Writing SVG...");
                BoofcvBatikVector.createSvgFileFromGeometry(
                        allPaths, sourceImage.getWidth(), sourceImage.getHeight(), outputPath);
            }
            case PAINT_BY_NUMBERS -> {
                if (isCancelled()) return null;
                reportProgress("Quantizing to palette...");
                java.awt.Color[] palette = controls.getPbnPalette();
                int minArea = controls.getPbnMinArea();
                double tolerance = controls.getTolerance();

                if (isCancelled()) return null;
                reportProgress("Finding regions...");
                PaintByNumbersProcessor.PbnResult result = PaintByNumbersProcessor.process(
                        sourceImage, palette, minArea, tolerance);

                if (isCancelled()) return null;
                reportProgress("Writing SVG...");
                int fontSize = controls.getPbnFontSize();
                boolean showNumbers = controls.isPbnShowNumbers();
                boolean showLegend = controls.isPbnShowLegend();
                PaintByNumbersProcessor.writeSvg(result, outputPath, fontSize, showNumbers, showLegend);
            }
            case CANNY_CONTOUR -> {
                double tolerance = controls.getTolerance();
                double detail = controls.getDetailFactor();
                float blur = Math.max(0.1f, (float) controls.getBlur());
                double minLen = (double) controls.getSpeckle();
                double maxLen = 2000.0;
                float low = controls.getCannyLow();
                float high = controls.getCannyHigh();

                if (controls.isCannyAuto()) {
                    if (isCancelled()) return null;
                    reportProgress("Computing auto thresholds...");
                    AutoCannyThresholds auto = AutoCannyThresholds.compute(sourceImage);
                    low = (float) auto.getLow();
                    high = (float) auto.getHigh();
                    autoCannyLow = low;
                    autoCannyHigh = high;
                }

                if (low >= high) {
                    high = Math.min(low + 0.01f, 0.999f);
                }

                String strokeColor = controls.getStrokeColor();
                double strokeWidth = controls.getStrokeWidth();
                boolean smoothCurves = controls.isSmoothCurves();
                boolean colorEdges = controls.isColorEdges();

                if (isCancelled()) return null;
                reportProgress("Detecting edges...");
                // The edge overlay reads the edge map back, so enable debug output for the
                // GUI; it is written to the temp dir rather than the working directory.
                BoofcvBatikVector.DEBUG = true;
                List<List<Point2D_I32>> contours = BoofcvBatikVector.extractContours(
                        sourceImage, blur, low, high, colorEdges);

                try {
                    File edgeDebugFile = BoofcvBatikVector.debugFile("edges_debug.png");
                    if (edgeDebugFile.exists()) {
                        edgeImage = ImageIO.read(edgeDebugFile);
                    }
                } catch (Exception ignored) {}

                if (isCancelled()) return null;
                reportProgress("Writing SVG (" + contours.size() + " contours)...");
                BoofcvBatikVector.createSvgFileBatik(
                        contours, sourceImage.getWidth(), sourceImage.getHeight(), outputPath,
                        strategy, tolerance, detail, minLen, maxLen,
                        strokeColor, strokeWidth, smoothCurves);
            }
        }

        if (isCancelled()) return null;
        reportProgress("Optimizing SVG...");
        SvgOptimizer.optimize(outputPath);

        return tempFile;
    }

    @Override
    protected void done() {
        try {
            File result = get();
            if (result != null) {
                if (autoCannyLow >= 0 && autoCannyHigh >= 0) {
                    callback.onAutoCannyComputed(autoCannyLow, autoCannyHigh);
                }
                callback.onVectorizationComplete(result, edgeImage);
            }
        } catch (Exception e) {
            callback.onError(e);
        }
    }
}
