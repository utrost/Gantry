package org.trostheide.gantry.vectorize;

import boofcv.alg.feature.detect.edge.CannyEdge;
import boofcv.factory.feature.detect.edge.FactoryEdgeDetectors;
import boofcv.core.image.GConvertImage;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.struct.ConnectRule;
import georegression.struct.point.Point2D_I32;

import jankovicsandras.imagetracer.SVGUtils;
import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.trostheide.gantry.vectorize.strategies.StraightLineStrategy;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Element;
import org.w3c.dom.svg.SVGDocument;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.json.JSONObject;


import net.plantabyte.drptrace.geometry.*;

import jankovicsandras.imagetracer.ImageTracer;


import java.util.HashMap;


/**
 * Main application class. Orchestrates the workflow:
 * Main -> CLI -> Image Processing (BoofCV) -> Vectorization (Strategy) -> SVG Output (Batik).
 */
public class BoofcvBatikVector {

    // Defaults for the old main() method, kept for the overloaded extractContours
    private static final float DEFAULT_CANNY_BLUR = 1.5f;
    private static final float DEFAULT_CANNY_LOW = 0.02f;
    private static final float DEFAULT_CANNY_HIGH = 0.1f;

    /**
     * Overloaded method for backward compatibility.
     * Calls extractContours with default Canny parameters.
     */
    public static List<List<Point2D_I32>> extractContours(BufferedImage image) {
        return extractContours(image, DEFAULT_CANNY_BLUR, DEFAULT_CANNY_LOW, DEFAULT_CANNY_HIGH);
    }

    /**
     * Extract raw pixel contours from an image using BoofCV Canny + BinaryImageOps
     * with specified Canny parameters.
     */
    public static List<List<Point2D_I32>> extractContours(BufferedImage image,
                                                          float blurSigma,
                                                          float threshLow,
                                                          float threshHigh) {
        return extractContours(image, blurSigma, threshLow, threshHigh, false);
    }

    /**
     * Extract contours with optional color-aware edge detection.
     * When colorAware is true, Canny is run on R, G, B channels independently
     * and the edge maps are merged (union), catching edges between regions of
     * different hue but similar brightness.
     */
    public static List<List<Point2D_I32>> extractContours(BufferedImage image,
                                                          float blurSigma,
                                                          float threshLow,
                                                          float threshHigh,
                                                          boolean colorAware) {
        GrayU8 edgeImage;

        if (colorAware && image.getType() != BufferedImage.TYPE_BYTE_GRAY) {
            edgeImage = extractColorAwareEdges(image, blurSigma, threshLow, threshHigh);
        } else {
            edgeImage = extractGrayscaleEdges(image, blurSigma, threshLow, threshHigh);
        }

        // --- Debug output ---
        try {
            GrayU8 visibleEdges = new GrayU8(edgeImage.width, edgeImage.height);
            for (int y = 0; y < edgeImage.height; y++) {
                for (int x = 0; x < edgeImage.width; x++) {
                    visibleEdges.set(x, y, edgeImage.get(x, y) * 255);
                }
            }
            BufferedImage debugImg = ConvertBufferedImage.convertTo(visibleEdges, null, true);
            ImageIO.write(debugImg, "png", new File("edges_debug.png"));
            System.out.println("Saved debug edge map: edges_debug.png");
        } catch (IOException e) {
            System.err.println("Could not save debug edge map: " + e.getMessage());
        }

        // --- Contour Tracing ---
        List<Contour> boofContours = BinaryImageOps.contour(edgeImage, ConnectRule.EIGHT, null);
        List<List<Point2D_I32>> allContours = new ArrayList<>();
        int emptyCount = 0;

        for (Contour c : boofContours) {
            if (c.external != null && !c.external.isEmpty()) {
                allContours.add(c.external);
            } else if (c.internal != null && !c.internal.isEmpty()) {
                allContours.addAll(c.internal);
            } else {
                emptyCount++;
            }
        }

        System.out.printf("Contours found: %d (empty skipped: %d)%s%n",
                allContours.size(), emptyCount, colorAware ? " [color-aware]" : "");
        return allContours;
    }

    private static GrayU8 extractGrayscaleEdges(BufferedImage image,
                                                 float blurSigma,
                                                 float threshLow,
                                                 float threshHigh) {
        GrayU8 inputGray = ConvertBufferedImage.convertFrom(image, (GrayU8) null);
        GrayF32 processed = new GrayF32(inputGray.width, inputGray.height);
        GConvertImage.convert(inputGray, processed);

        int blurRadius = (int) Math.ceil(3 * blurSigma);
        CannyEdge<GrayF32, GrayF32> canny = FactoryEdgeDetectors.canny(
                blurRadius, true, true, GrayF32.class, GrayF32.class);
        GrayU8 edgeImage = new GrayU8(inputGray.width, inputGray.height);
        canny.process(processed, threshLow, threshHigh, edgeImage);
        return edgeImage;
    }

    /**
     * Run Canny on each color channel (R, G, B) and merge edge maps.
     * An edge pixel in ANY channel is kept, catching hue boundaries
     * that grayscale Canny misses.
     */
    private static GrayU8 extractColorAwareEdges(BufferedImage image,
                                                  float blurSigma,
                                                  float threshLow,
                                                  float threshHigh) {
        int w = image.getWidth();
        int h = image.getHeight();
        int blurRadius = (int) Math.ceil(3 * blurSigma);

        GrayU8 merged = new GrayU8(w, h);

        for (int ch = 0; ch < 3; ch++) {
            GrayF32 channel = new GrayF32(w, h);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgb = image.getRGB(x, y);
                    int value = switch (ch) {
                        case 0 -> (rgb >> 16) & 0xFF;
                        case 1 -> (rgb >> 8) & 0xFF;
                        default -> rgb & 0xFF;
                    };
                    channel.set(x, y, value);
                }
            }

            CannyEdge<GrayF32, GrayF32> canny = FactoryEdgeDetectors.canny(
                    blurRadius, true, true, GrayF32.class, GrayF32.class);
            GrayU8 edges = new GrayU8(w, h);
            canny.process(channel, threshLow, threshHigh, edges);

            // Union: OR the edges into merged
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (edges.get(x, y) != 0) {
                        merged.set(x, y, 1);
                    }
                }
            }
        }

        return merged;
    }

    /**
     * Create an SVG using Batik from simplified contours (Canny-based workflow).
     * This method processes contours one-by-one.
     */
    /**
     * Backward-compatible overload using default stroke style.
     */
    public static void createSvgFileBatik(List<List<Point2D_I32>> contours,
                                          int width, int height, String outputPath,
                                          VectorizationStrategy strategy,
                                          double tolerance, double detailFactor,
                                          double minLength, double maxLength) throws IOException {
        createSvgFileBatik(contours, width, height, outputPath, strategy,
                tolerance, detailFactor, minLength, maxLength, "black", 1.0);
    }

    public static void createSvgFileBatik(List<List<Point2D_I32>> contours,
                                          int width, int height, String outputPath,
                                          VectorizationStrategy strategy,
                                          double tolerance, double detailFactor,
                                          double minLength, double maxLength,
                                          String strokeColor, double strokeWidth) throws IOException {
        createSvgFileBatik(contours, width, height, outputPath, strategy,
                tolerance, detailFactor, minLength, maxLength, strokeColor, strokeWidth, false);
    }

    public static void createSvgFileBatik(List<List<Point2D_I32>> contours,
                                          int width, int height, String outputPath,
                                          VectorizationStrategy strategy,
                                          double tolerance, double detailFactor,
                                          double minLength, double maxLength,
                                          String strokeColor, double strokeWidth,
                                          boolean smoothCurves) throws IOException {

        DOMImplementation domImpl = SVGDOMImplementation.getDOMImplementation();
        String svgNS = SVGDOMImplementation.SVG_NAMESPACE_URI;
        SVGDocument document = (SVGDocument) domImpl.createDocument(svgNS, "svg", null);

        Element root = document.getDocumentElement();
        root.setAttributeNS(null, "width", String.valueOf(width));
        root.setAttributeNS(null, "height", String.valueOf(height));
        root.setAttributeNS(null, "viewBox", "0 0 " + width + " " + height);

        int drawn = 0;
        int skipped = 0;

        for (List<Point2D_I32> rawPoints : contours) {
            // 1. Simplify using the chosen strategy. This now returns VectorGeometry.
            VectorGeometry geometry = strategy.processContour(
                    rawPoints, tolerance, detailFactor, minLength, maxLength
            );

            // 2. Check what *kind* of geometry we got.
            if (geometry instanceof PolylineGeometry) {
                // It's a polyline! Cast it and get the points.
                List<Point2D_I32> simplified = ((PolylineGeometry) geometry).points;

                if (simplified == null || simplified.size() < 2) {
                    skipped++;
                    continue;
                }

                // 3. Apply global length filtering *only* if the strategy is not StraightLineStrategy
                if (!(strategy instanceof StraightLineStrategy)) {
                    double len = contourLength(simplified);
                    if (len < minLength) {
                        skipped++;
                        continue;
                    }
                    if (maxLength > 0 && len > maxLength) {
                        skipped++;
                        continue;
                    }
                }

                // 4. Render the polyline (or smooth Bezier path)
                if (smoothCurves && simplified.size() >= 3) {
                    String pathData = polylineToCubicBezierPath(simplified);
                    renderPath(root, document, pathData, strokeColor, strokeWidth);
                } else {
                    renderPolyline(root, document, simplified, strokeColor, strokeWidth);
                }
                drawn++;
            }
            else if (geometry instanceof PathGeometry) {
                // It's a path! Cast it and get the data string.
                // We assume paths have already been filtered by their strategy.
                renderPath(root, document, ((PathGeometry) geometry).pathData, strokeColor, strokeWidth);
                drawn++;
            }
            else {
                // Empty or unhandled geometry
                skipped++;
            }
        }

        try (FileWriter writer = new FileWriter(outputPath)) {
            org.apache.batik.dom.util.DOMUtilities.writeDocument(document, writer);
        }

        System.out.printf("SVG saved: %s (%d drawn, %d skipped, minLen=%.1f, maxLen=%.1f)%n",
                outputPath, drawn, skipped, minLength, maxLength);
    }

    /**
     * Creates an SVG file from a pre-computed list of VectorGeometry objects.
     * This is used by the Bezier (DrPTrace) workflow, which returns all paths at once.
     *
     * @param allGeometry  A list of PolylineGeometry or PathGeometry
     * @param width        Image width
     * @param height       Image height
     * @param outputPath   File path for the generated SVG
     * @throws IOException
     */
    public static void createSvgFileFromGeometry(List<VectorGeometry> allGeometry,
                                                 int width, int height, String outputPath) throws IOException {
        createSvgFileFromGeometry(allGeometry, width, height, outputPath, "black", 1.0);
    }

    public static void createSvgFileFromGeometry(List<VectorGeometry> allGeometry,
                                                 int width, int height, String outputPath,
                                                 String strokeColor, double strokeWidth) throws IOException {

        DOMImplementation domImpl = SVGDOMImplementation.getDOMImplementation();
        String svgNS = SVGDOMImplementation.SVG_NAMESPACE_URI;
        SVGDocument document = (SVGDocument) domImpl.createDocument(svgNS, "svg", null);

        Element root = document.getDocumentElement();
        root.setAttributeNS(null, "width", String.valueOf(width));
        root.setAttributeNS(null, "height", String.valueOf(height));
        root.setAttributeNS(null, "viewBox", "0 0 " + width + " " + height);

        int drawn = 0;
        int skipped = 0;

        for (VectorGeometry geometry : allGeometry) {
            if (geometry instanceof PolylineGeometry) {
                List<Point2D_I32> points = ((PolylineGeometry) geometry).points;
                if (points != null && points.size() >= 2) {
                    renderPolyline(root, document, points, strokeColor, strokeWidth);
                    drawn++;
                } else {
                    skipped++;
                }
            } else if (geometry instanceof PathGeometry) {
                String pathData = ((PathGeometry) geometry).pathData;
                if (pathData != null && !pathData.isEmpty()) {
                    renderPath(root, document, pathData, strokeColor, strokeWidth);
                    drawn++;
                } else {
                    skipped++;
                }
            } else {
                skipped++;
            }
        }

        try (FileWriter writer = new FileWriter(outputPath)) {
            org.apache.batik.dom.util.DOMUtilities.writeDocument(document, writer);
        }

        System.out.printf("SVG saved: %s (%d drawn, %d skipped)%n",
                outputPath, drawn, skipped);
    }

    /**
     * Helper method to create and append an SVG <polyline> element.
     */
    private static final double CLOSE_THRESHOLD = 3.0;

    private static void renderPolyline(Element parent, SVGDocument document, List<Point2D_I32> points,
                                        String strokeColor, double strokeWidth) {
        // Detect closed shapes: if first and last points are within threshold, use <polygon>
        Point2D_I32 first = points.get(0);
        Point2D_I32 last = points.get(points.size() - 1);
        boolean isClosed = Math.hypot(last.x - first.x, last.y - first.y) < CLOSE_THRESHOLD;

        // Build the 'points' string, e.g., "10,10 20,20 30,10"
        StringBuilder sb = new StringBuilder();
        int limit = isClosed ? points.size() - 1 : points.size(); // skip duplicate closing point
        for (int i = 0; i < limit; i++) {
            Point2D_I32 p = points.get(i);
            sb.append(p.x).append(",").append(p.y).append(" ");
        }

        String svgNS = SVGDOMImplementation.SVG_NAMESPACE_URI;
        String elementName = isClosed ? "polygon" : "polyline";
        Element element = document.createElementNS(svgNS, elementName);
        element.setAttributeNS(null, "points", sb.toString().trim());

        String style = String.format("fill:none;stroke:%s;stroke-width:%.1f", strokeColor, strokeWidth);
        element.setAttributeNS(null, "style", style);

        parent.appendChild(element);
    }

    /**
     * Helper method to create and append an SVG <path> element.
     */
    private static void renderPath(Element parent, SVGDocument document, String pathData,
                                    String strokeColor, double strokeWidth) {
        String svgNS = SVGDOMImplementation.SVG_NAMESPACE_URI;
        Element path = document.createElementNS(svgNS, "path");
        path.setAttributeNS(null, "d", pathData);

        String style = String.format("fill:none;stroke:%s;stroke-width:%.1f", strokeColor, strokeWidth);
        path.setAttributeNS(null, "style", style);

        parent.appendChild(path);
    }

    /**
     * Runs the DrPTrace (potrace-like) algorithm on the entire image.
     * This uses the high-level ImageTracer.traceBufferedImage() method.
     *
     * @param image The source BufferedImage
     * @param bezierDetail Smoothness (pixels per node)
     * @param bezierColors Max colors to quantize
     * @return A list of VectorGeometry (specifically PathGeometry) objects
     */
    public static List<VectorGeometry> runBezierTrace(BufferedImage image, int bezierDetail, int bezierColors)
            throws IOException { // <-- Added IOException

        System.out.println("Tracing image with ImageTracer...");

        // 1. Run the trace using the static ImageTracer.traceBufferedImage method
        //    This handles quantization and tracing in one step.
        List<BezierShape> shapes = net.plantabyte.drptrace.utils.ImageTracer.traceBufferedImage(
                image,
                bezierDetail, // smoothness
                bezierColors  // maxColors
        );

        System.out.printf("Trace complete, found %d paths.%n", shapes.size());

        // 2. Convert the List<BezierShape> to our List<VectorGeometry>
        return shapes.stream()
                .map(BezierShape::toSVGPathString)
                .map(PathGeometry::new)
                .collect(Collectors.toList());
    }



    /**
     * Runs the ImageTracer (bezier2) algorithm and writes the resulting
     * SVG file directly. This strategy generates a complete SVG file,
     * not individual path data.
     *
     * @param image      The source BufferedImage
     * @param b2Colors   Number of colors to quantize
     * @param b2Ltres    Line threshold
     * @param b2Qtres    Quadratic curve threshold
     * @param b2PathOmit Omit paths shorter than this
     * @param b2Outline  If true, convert fills to strokes
     * @param outputPath The full path to write the .svg file to
     * @throws Exception
     */
    public static void runAndWriteImageTracer(BufferedImage image,
                                              int b2Colors,
                                              double b2Ltres,
                                              double b2Qtres,
                                              int b2PathOmit,
                                              boolean b2Outline, // <-- NEW
                                              String outputPath) throws Exception {

        System.out.println("Tracing image with ImageTracer (bezier2)...");

        // 1. Set up the options map
        HashMap<String, Float> options = new HashMap<>();
        options.put("ltres", (float) b2Ltres);
        options.put("qtres", (float) b2Qtres);
        options.put("pathomit", (float) b2PathOmit);
        options.put("numberofcolors", (float) b2Colors);
        options.put("colorsampling", 1f);
        options.put("mincolorratio", 0.02f);
        options.put("colorquantcycles", 3f);
        options.put("roundcoords", 1.0f);
        options.put("blurradius", 0.0f);
        options.put("viewbox", 0f);
        options.put("desc", 0f);
        options.put("scale", 1.0f);
        options.put("lcpr", 0f);
        options.put("qcpr", 0f);

        // 2. Run the trace
        jankovicsandras.imagetracer.ImageTracer.ImageData imgd =
                jankovicsandras.imagetracer.ImageTracer.loadImageData(image);
        byte[][] palette = jankovicsandras.imagetracer.ImageTracer.getPalette(image, options);
        jankovicsandras.imagetracer.ImageTracer.IndexedImage ii =
                jankovicsandras.imagetracer.ImageTracer.imagedataToTracedata(imgd, options, palette);
        String svgContent = jankovicsandras.imagetracer.SVGUtils.getsvgstring(ii, options);

        // 3. --- THIS IS THE FIX ---
        // If --b2-outline is true, we modify the SVG string
        if (b2Outline) {
            System.out.println("Converting fills to strokes...");

            // This regex finds: fill="rgb(r,g,b)" stroke="rgb(r,g,b)"
            // And replaces it with: fill="none" stroke="rgb(r,g,b)"
            // The ($1) captures the "rgb(...)" part.
            svgContent = svgContent.replaceAll(
                    "fill=\"(rgb\\([\\d,]+\\))\" stroke=\"(rgb\\([\\d,]+\\))\"",
                    "fill=\"none\" stroke=\"$1\""
            );
        }

        // 4. Write the string directly to the output file
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write(svgContent);
        }

        System.out.printf("SVG saved: %s%n", outputPath);
    }


    /**
     * Writes a JSON sidecar file containing all vectorization parameters.
     */
    public static void writeMetadataSidecar(CommandLine cmd, String jsonOutputPath, String absoluteInputPath) throws IOException {
        CliParser tempParser = new CliParser();
        JSONObject params = new JSONObject();
        params.put("sourceImage", absoluteInputPath);
        String strategyName = cmd.getOptionValue("s", "dp");
        params.put("strategy", strategyName);

        if (strategyName.equals("bezier")) {
            JSONObject bezier = new JSONObject();
            bezier.put("detail", tempParser.getBezierDetail(cmd));
            bezier.put("colors", tempParser.getBezierColors(cmd));
            params.put("bezier", bezier);
        } else if (strategyName.equals("bezier2")) {
            // Bezier2 (ImageTracer) parameters
            JSONObject bezier2 = new JSONObject();
            bezier2.put("colors", tempParser.getB2Colors(cmd));
            bezier2.put("ltres", tempParser.getB2Ltres(cmd));
            bezier2.put("qtres", tempParser.getB2Qtres(cmd));
            bezier2.put("pathomit", tempParser.getB2PathOmit(cmd));
            bezier2.put("outline", tempParser.getB2Outline(cmd)); // <-- NEW
            params.put("bezier2", bezier2);

        } else if (strategyName.equals("pbn")) {
            JSONObject pbn = new JSONObject();
            pbn.put("colors", cmd.getOptionValue("pbn-colors", ""));
            pbn.put("minArea", tempParser.getPbnMinArea(cmd));
            pbn.put("fontSize", tempParser.getPbnFontSize(cmd));
            pbn.put("noNumbers", tempParser.isPbnNoNumbers(cmd));
            pbn.put("noLegend", tempParser.isPbnNoLegend(cmd));
            params.put("pbn", pbn);

        } else {
            JSONObject contour = new JSONObject();
            contour.put("tolerance", tempParser.getTolerance(cmd));
            contour.put("detail", tempParser.getDetailLevel(cmd));
            contour.put("minLength", tempParser.getMinLength(cmd));
            contour.put("maxLength", tempParser.getMaxLength(cmd));
            contour.put("cannyBlur", tempParser.getCannyBlur(cmd));
            contour.put("cannyLow", tempParser.getCannyLow(cmd));
            contour.put("cannyHigh", tempParser.getCannyHigh(cmd));
            contour.put("cannyAuto", tempParser.getCannyAuto(cmd));
            contour.put("colorEdges", tempParser.getColorEdges(cmd));
            contour.put("strokeColor", tempParser.getStrokeColor(cmd));
            contour.put("strokeWidth", tempParser.getStrokeWidth(cmd));
            contour.put("smoothCurves", tempParser.getSmoothCurves(cmd));
            params.put("contour", contour);
        }

        try (FileWriter file = new FileWriter(jsonOutputPath)) {
            file.write(params.toString(4));
        }
    }

    /**
     * Render simplified contours to a debug PNG.
     * This method must also be updated to handle VectorGeometry.
     */
    public static BufferedImage renderSimplifiedContours(List<List<Point2D_I32>> contours,
                                                         VectorizationStrategy strategy,
                                                         double tolerance,
                                                         double detailFactor,
                                                         int width,
                                                         int height,
                                                         double minLength,
                                                         double maxLength) {
        BufferedImage debugImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = debugImage.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);

        for (List<Point2D_I32> rawPoints : contours) {
            // 1. Simplify using the chosen strategy
            VectorGeometry geometry = strategy.processContour(
                    rawPoints, tolerance, detailFactor, minLength, maxLength
            );

            // 2. Check what *kind* of geometry we got.
            // We can only draw polylines to the debug image.
            if (geometry instanceof PolylineGeometry) {
                List<Point2D_I32> simplified = ((PolylineGeometry) geometry).points;

                if (simplified == null || simplified.size() < 2) continue;

                // 3. Apply global length filtering *only* if the strategy is not StraightLineStrategy
                if (!(strategy instanceof StraightLineStrategy)) {
                    double len = contourLength(simplified);
                    if (len < minLength) {
                        continue;
                    }
                    if (maxLength > 0 && len > maxLength) {
                        continue;
                    }
                }

                // 4. Draw the polyline to the Graphics2D context
                for (int i = 0; i < simplified.size() - 1; i++) {
                    Point2D_I32 p1 = simplified.get(i);
                    Point2D_I32 p2 = simplified.get(i + 1);
                    g.drawLine(p1.x, p1.y, p2.x, p2.y);
                }
            }
        }

        g.dispose();
        return debugImage;
    }

    /**
     * Convert a polyline to a smooth SVG path using Catmull-Rom to cubic Bezier conversion.
     * This produces smooth curves through all the original points.
     */
    public static String polylineToCubicBezierPath(List<Point2D_I32> points) {
        if (points.size() < 2) return "";
        if (points.size() == 2) {
            return String.format("M %d %d L %d %d",
                    points.get(0).x, points.get(0).y,
                    points.get(1).x, points.get(1).y);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("M %d %d", points.get(0).x, points.get(0).y));

        // For each segment, compute Catmull-Rom control points and convert to cubic Bezier
        for (int i = 0; i < points.size() - 1; i++) {
            Point2D_I32 p0 = points.get(Math.max(0, i - 1));
            Point2D_I32 p1 = points.get(i);
            Point2D_I32 p2 = points.get(i + 1);
            Point2D_I32 p3 = points.get(Math.min(points.size() - 1, i + 2));

            // Catmull-Rom to cubic Bezier control points (alpha = 0.5 / uniform)
            double cp1x = p1.x + (p2.x - p0.x) / 6.0;
            double cp1y = p1.y + (p2.y - p0.y) / 6.0;
            double cp2x = p2.x - (p3.x - p1.x) / 6.0;
            double cp2y = p2.y - (p3.y - p1.y) / 6.0;

            sb.append(String.format(" C %.1f %.1f, %.1f %.1f, %d %d",
                    cp1x, cp1y, cp2x, cp2y, p2.x, p2.y));
        }

        return sb.toString();
    }

    /** Compute Euclidean contour length */
    private static double contourLength(List<Point2D_I32> points) {
        double len = 0;
        for (int i = 1; i < points.size(); i++) {
            Point2D_I32 p1 = points.get(i - 1);
            Point2D_I32 p2 = points.get(i);
            len += Math.hypot(p2.x - p1.x, p2.y - p1.y);
        }
        return len;
    }

}