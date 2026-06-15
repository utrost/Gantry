package org.trostheide.watercolorprocessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.parser.AWTPathProducer;
import org.apache.batik.parser.PathParser;
import org.apache.batik.util.XMLResourceDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.trostheide.watercolorprocessor.dto.Bounds;
import org.trostheide.watercolorprocessor.dto.Layer;
import org.trostheide.watercolorprocessor.dto.Point;
import org.trostheide.watercolorprocessor.dto.ProcessorOutput;
import org.trostheide.watercolorprocessor.dto.Metadata;
import org.trostheide.watercolorprocessor.dto.command.Command;
import org.trostheide.watercolorprocessor.dto.command.DrawCommand;
import org.trostheide.watercolorprocessor.dto.command.MoveCommand;
import org.trostheide.watercolorprocessor.dto.command.RefillCommand;
import org.w3c.dom.*;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import org.apache.batik.parser.AWTTransformProducer;
import org.apache.batik.parser.TransformListParser;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Core logic service for the Watercolor Processor.
 * Handles Multi-Layer SVG processing for watercolor plotting.
 */
public class ProcessorService {

    private static final Logger logger = LoggerFactory.getLogger(ProcessorService.class);
    private final ObjectMapper objectMapper;

    // Global state for the processing run — reset at the start of each process() call
    private int commandCounter = 1;

    public ProcessorService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void process(File inputFile, File outputFile, double maxDrawDistance, String defaultStationId,
            double curveApproximation, String fitToFormatStr, double padding, boolean mirror) {
        PaperFormat fmt = PaperFormat.fromString(fitToFormatStr);
        double targetW = 0, targetH = 0;
        if (fmt != null) {
            targetW = fmt.width() - padding * 2;
            targetH = fmt.height() - padding * 2;
        }
        process(inputFile, outputFile, maxDrawDistance, defaultStationId,
                curveApproximation, targetW, targetH, true, 0, 0, mirror);
    }

    public void process(File inputFile, File outputFile, double maxDrawDistance, String defaultStationId,
            double curveApproximation, double targetWidth, double targetHeight, boolean keepAspectRatio,
            double posX, double posY, boolean mirror) {
        logger.info("Starting processing for: {}", inputFile.getName());
        this.commandCounter = 1;

        boolean hasTargetSize = targetWidth > 0 && targetHeight > 0;
        boolean hasPosition = posX != 0 || posY != 0;

        try {
            // 1. Load SVG (try strict SVG DOM first, fall back to generic XML for non-standard elements)
            Document doc;
            try {
                String parser = XMLResourceDescriptor.getXMLParserClassName();
                SAXSVGDocumentFactory f = new SAXSVGDocumentFactory(parser);
                doc = f.createDocument(inputFile.toURI().toString());
            } catch (org.w3c.dom.DOMException e) {
                logger.warn("Strict SVG parser failed ({}), retrying with generic XML parser.", e.getMessage());
                javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true);
                doc = dbf.newDocumentBuilder().parse(inputFile);
            }

            // 2. Identify Layers
            List<LayerProcessingContext> layersToProcess = identifyLayers(doc, defaultStationId);

            // 3. Pre-scan for bounds (if scaling, positioning, or mirroring)
            AffineTransform globalTx = new AffineTransform();
            Bounds preScannedBounds = null;

            if (hasTargetSize || hasPosition || mirror) {
                preScannedBounds = calculateGlobalBounds(layersToProcess);
            }

            if (hasTargetSize && preScannedBounds != null) {
                globalTx = calculateScaleTransform(preScannedBounds, targetWidth, targetHeight,
                        keepAspectRatio, posX, posY);
            } else if (hasPosition && preScannedBounds != null) {
                globalTx.translate(posX - preScannedBounds.minX(), posY - preScannedBounds.minY());
            }

            // Mirror Logic
            if (mirror) {
                double pivotX;
                if (hasTargetSize) {
                    pivotX = posX + targetWidth / 2.0;
                } else if (preScannedBounds != null) {
                    pivotX = (preScannedBounds.minX() + preScannedBounds.maxX()) / 2.0;
                } else {
                    pivotX = 0;
                }
                AffineTransform mirrorTx = new AffineTransform(-1, 0, 0, 1, 2 * pivotX, 0);
                globalTx.preConcatenate(mirrorTx);
            }

            // 4. Process Each Layer
            List<Layer> resultLayers = new ArrayList<>();
            // We use globalBounds to track the FINAL bounds
            BoundsBuilder globalBoundsBuilder = new BoundsBuilder();
            PathParser pathParser = new PathParser();
            AWTPathProducer pathProducer = new AWTPathProducer();
            pathParser.setPathHandler(pathProducer);

            int totalCommands = 0;

            for (LayerProcessingContext ctx : layersToProcess) {
                logger.info("Processing Layer: '{}' mapped to Station: '{}'", ctx.layerName, ctx.stationId);

                List<Command> layerCommands = generateCommandsForLayer(
                        ctx.rootNode,
                        ctx.stationId,
                        maxDrawDistance,
                        curveApproximation,
                        pathParser,
                        pathProducer,
                        globalBoundsBuilder,
                        globalTx); // Pass the transform!

                if (!layerCommands.isEmpty()) {
                    resultLayers.add(new Layer(ctx.layerName, ctx.stationId, layerCommands));
                    totalCommands += layerCommands.size();
                } else {
                    logger.warn("Layer '{}' contained no drawable paths.", ctx.layerName);
                }
            }

            // 4. Build Output
            Metadata metadata = new Metadata(
                    inputFile.getName(),
                    Instant.now(),
                    "MULTI_LAYER", // Station ID is now per-layer
                    "mm",
                    totalCommands,
                    globalBoundsBuilder.build());

            ProcessorOutput output = new ProcessorOutput(metadata, resultLayers);

            // 5. Write JSON
            objectMapper.writeValue(outputFile, output);
            logger.info("Successfully wrote {} layers with {} total commands to {}",
                    resultLayers.size(), totalCommands, outputFile.getAbsolutePath());

        } catch (Exception e) {
            logger.error("Fatal Error during SVG processing", e);
            throw new RuntimeException(e);
        }
    }

    // --- Layer Identification ---

    public record PaperFormat(double width, double height) {
        public static final PaperFormat A5 = new PaperFormat(148, 210);
        public static final PaperFormat A4 = new PaperFormat(210, 297);
        public static final PaperFormat A3 = new PaperFormat(297, 420);
        public static final PaperFormat XL = new PaperFormat(430, 600); // Rough XL size

        public static PaperFormat fromString(String s) {
            if (s == null)
                return null;
            switch (s.toUpperCase()) {
                case "A5":
                    return A5;
                case "A4":
                    return A4;
                case "A3":
                    return A3;
                case "XL":
                    return XL;
                default:
                    // Support custom "WxH" format (e.g. "300x400")
                    if (s.contains("x")) {
                        String[] parts = s.split("x");
                        if (parts.length == 2) {
                            try {
                                return new PaperFormat(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                    return null;
            }
        }
    }

    private record LayerProcessingContext(String layerName, String stationId, Element rootNode) {
    }

    private List<LayerProcessingContext> identifyLayers(Document doc, String defaultStationId) {
        List<LayerProcessingContext> contexts = new ArrayList<>();
        Element root = doc.getDocumentElement();

        // Scan direct children for Inkscape Layers
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals("g")) {
                Element g = (Element) n;
                // Check for inkscape:groupmode="layer"
                if ("layer".equals(g.getAttribute("inkscape:groupmode")) ||
                        "layer".equals(g.getAttributeNS("http://www.inkscape.org/namespaces/inkscape", "groupmode"))) {

                    String label = g.getAttribute("inkscape:label");
                    if (label.isEmpty()) {
                        label = g.getAttributeNS("http://www.inkscape.org/namespaces/inkscape", "label");
                    }

                    // Renaming Strategy: User wants generic Layer1, Layer2... regardless of
                    // Inkscape label.
                    // The label is still useful for logging/debugging.
                    String genericId = "Layer" + (contexts.size() + 1);
                    String stationId = genericId;
                    String layerName = (label != null && !label.isEmpty()) ? label + " (" + genericId + ")" : genericId;

                    contexts.add(new LayerProcessingContext(layerName, stationId, g));
                }
            }
        }

        // Fallback: If no explicit layers found, treat the whole document root as one
        // layer
        if (contexts.isEmpty()) {
            logger.info("No explicit Inkscape layers found. Processing entire document as default layer.");
            contexts.add(new LayerProcessingContext("Default", defaultStationId, root));
        }

        return contexts;
    }

    // --- Core Generation Logic ---

    private List<Command> generateCommandsForLayer(Element layerRoot, String stationId, double maxDist,
            double curveStep,
            PathParser parser, AWTPathProducer producer, BoundsBuilder bounds, AffineTransform globalTx) {
        List<Command> cmds = new ArrayList<>();
        List<Node> drawables = new ArrayList<>();
        collectDrawableElements(layerRoot, drawables);

        if (drawables.isEmpty())
            return cmds;

        // Initial Refill for this layer (skip when maxDist <= 0, i.e. no-refill mode)
        if (maxDist > 0) {
            cmds.add(new RefillCommand(commandCounter++, stationId));
        }
        double currentPaintDist = 0.0;

        for (Node node : drawables) {
            String d = getRawPathData(node);
            if (d == null)
                continue;

            parser.parse(d);
            Shape shape = producer.getShape();

            // Apply SVG Transform if present
            shape = applyElementTransform(node, shape);

            // Apply Auto-Scale Transform
            if (globalTx != null && !globalTx.isIdentity()) {
                shape = globalTx.createTransformedShape(shape);
            }

            PathIterator pi = shape.getPathIterator(null, curveStep);

            double[] coords = new double[6];
            Point currentPos = null;
            Point startPoint = null;
            List<Point> currentStroke = new ArrayList<>();

            while (!pi.isDone()) {
                int type = pi.currentSegment(coords);
                switch (type) {
                    case PathIterator.SEG_MOVETO:
                        if (!currentStroke.isEmpty()) {
                            finishStroke(cmds, currentStroke, bounds);
                        }
                        double mx = coords[0];
                        double my = coords[1];
                        MoveCommand mc = new MoveCommand(commandCounter++, mx, my);
                        cmds.add(mc);
                        bounds.add(mx, my);
                        currentPos = new Point(mx, my);
                        startPoint = currentPos;
                        currentStroke.add(currentPos);
                        break;

                    case PathIterator.SEG_LINETO:
                        Point target = new Point(coords[0], coords[1]);
                        double dist = distance(currentPos, target);

                        while (maxDist > 0 && currentPaintDist + dist > maxDist) {
                            double rem = maxDist - currentPaintDist;
                            Point split = interpolate(currentPos, target, rem, dist);

                            currentStroke.add(split);
                            finishStroke(cmds, currentStroke, bounds);

                            // Refill
                            cmds.add(new RefillCommand(commandCounter++, stationId));
                            MoveCommand ret = new MoveCommand(commandCounter++, split.x(), split.y());
                            cmds.add(ret);
                            bounds.add(split.x(), split.y());

                            currentPaintDist = 0.0;
                            currentPos = split;
                            currentStroke.add(currentPos);
                            dist = distance(currentPos, target);
                        }
                        currentStroke.add(target);
                        currentPaintDist += dist;
                        currentPos = target;
                        break;

                    case PathIterator.SEG_CLOSE:
                        if (startPoint != null && currentPos != null && !startPoint.equals(currentPos)) {
                            double cDist = distance(currentPos, startPoint);
                            while (maxDist > 0 && currentPaintDist + cDist > maxDist) {
                                double rem = maxDist - currentPaintDist;
                                Point split = interpolate(currentPos, startPoint, rem, cDist);
                                currentStroke.add(split);
                                finishStroke(cmds, currentStroke, bounds);

                                cmds.add(new RefillCommand(commandCounter++, stationId));
                                MoveCommand ret = new MoveCommand(commandCounter++, split.x(), split.y());
                                cmds.add(ret);
                                bounds.add(split.x(), split.y());

                                currentPaintDist = 0.0;
                                currentPos = split;
                                currentStroke.add(currentPos);
                                cDist = distance(currentPos, startPoint);
                            }
                            currentStroke.add(startPoint);
                            currentPaintDist += cDist;
                            currentPos = startPoint;
                        }
                        break;
                }
                pi.next();
            }
            if (!currentStroke.isEmpty()) {
                finishStroke(cmds, currentStroke, bounds);
            }
        }
        return cmds;
    }

    private void finishStroke(List<Command> cmds, List<Point> stroke, BoundsBuilder bounds) {
        DrawCommand dc = new DrawCommand(commandCounter++, new ArrayList<>(stroke));
        cmds.add(dc);
        for (Point p : stroke)
            bounds.add(p.x(), p.y());
        stroke.clear();
    }

    // --- Helpers ---

    private void collectDrawableElements(Node node, List<Node> result) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            String tagName = ((Element) node).getTagName();
            // Added basic primitives: circle, ellipse, line, polyline, polygon
            if (tagName.equals("path") || tagName.equals("rect") ||
                    tagName.equals("circle") || tagName.equals("ellipse") ||
                    tagName.equals("line") || tagName.equals("polyline") || tagName.equals("polygon")) {
                result.add(node);
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
            collectDrawableElements(children.item(i), result);
    }

    private String getRawPathData(Node node) {
        if (node.getNodeType() != Node.ELEMENT_NODE)
            return null;
        Element el = (Element) node;
        String tagName = el.getTagName();

        // Transform check removed to support transformed elements

        try {
            if ("path".equals(tagName))
                return el.getAttribute("d");

            if ("rect".equals(tagName)) {
                double x = d(el, "x");
                double y = d(el, "y");
                double w = d(el, "width");
                double h = d(el, "height");
                return String.format(java.util.Locale.US, "M%f %f L%f %f L%f %f L%f %f Z", x, y, x + w, y, x + w, y + h,
                        x, y + h);
            }

            if ("circle".equals(tagName)) {
                double cx = d(el, "cx");
                double cy = d(el, "cy");
                double r = d(el, "r");
                // Two arcs to make a circle
                return String.format(java.util.Locale.US, "M %f %f A %f %f 0 1 0 %f %f A %f %f 0 1 0 %f %f Z",
                        cx - r, cy, r, r, cx + r, cy, r, r, cx - r, cy);
            }

            if ("ellipse".equals(tagName)) {
                double cx = d(el, "cx");
                double cy = d(el, "cy");
                double rx = d(el, "rx");
                double ry = d(el, "ry");
                return String.format(java.util.Locale.US, "M %f %f A %f %f 0 1 0 %f %f A %f %f 0 1 0 %f %f Z",
                        cx - rx, cy, rx, ry, cx + rx, cy, rx, ry, cx - rx, cy);
            }

            if ("line".equals(tagName)) {
                return String.format(java.util.Locale.US, "M %f %f L %f %f", d(el, "x1"), d(el, "y1"), d(el, "x2"),
                        d(el, "y2"));
            }

            if ("polyline".equals(tagName) || "polygon".equals(tagName)) {
                String points = el.getAttribute("points").trim();
                if (points.isEmpty())
                    return null;
                // Simple conversion: points="x1,y1 x2,y2" -> M x1 y1 L x2 y2 ...
                // Note: This is a naive regex replacement for demonstration.
                // Ideally parse numbers, but Batik PathParser expects standard path commands.
                // We'll assume space/comma separation.
                String[] pts = points.split("[\\s,]+");
                if (pts.length < 2)
                    return null;

                StringBuilder sb = new StringBuilder("M ").append(pts[0]).append(" ").append(pts[1]);
                for (int i = 2; i < pts.length - 1; i += 2) {
                    sb.append(" L ").append(pts[i]).append(" ").append(pts[i + 1]);
                }
                if ("polygon".equals(tagName))
                    sb.append(" Z");
                return sb.toString();
            }

        } catch (Exception e) {
            logger.warn("Failed to convert primitive '{}' to path: {}", tagName, e.getMessage());
            return null;
        }
        return null;
    }

    private double d(Element e, String attr) {
        String v = e.getAttribute(attr);
        return v.isEmpty() ? 0.0 : Double.parseDouble(v);
    }

    // Package-private for testability
    double distance(Point p1, Point p2) {
        return Math.sqrt(Math.pow(p2.x() - p1.x(), 2) + Math.pow(p2.y() - p1.y(), 2));
    }

    // Package-private for testability
    Point interpolate(Point start, Point end, double distToTravel, double totalDist) {
        if (totalDist == 0)
            return start;
        double ratio = distToTravel / totalDist;
        return new Point(start.x() + (end.x() - start.x()) * ratio, start.y() + (end.y() - start.y()) * ratio);
    }

    // Package-private for testability
    static class BoundsBuilder {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        void reset() {
            minX = Double.MAX_VALUE;
            minY = Double.MAX_VALUE;
            maxX = -Double.MAX_VALUE;
            maxY = -Double.MAX_VALUE;
        }

        void add(double x, double y) {
            if (x < minX)
                minX = x;
            if (y < minY)
                minY = y;
            if (x > maxX)
                maxX = x;
            if (y > maxY)
                maxY = y;
        }

        Bounds build() {
            if (minX == Double.MAX_VALUE)
                return Bounds.empty();
            return new Bounds(minX, minY, maxX, maxY);
        }
    }

    // --- Auto-Scaling Logic ---

    private Bounds calculateGlobalBounds(List<LayerProcessingContext> contexts) {
        BoundsBuilder builder = new BoundsBuilder();
        PathParser parser = new PathParser();
        AWTPathProducer producer = new AWTPathProducer();
        parser.setPathHandler(producer);

        for (LayerProcessingContext ctx : contexts) {
            List<Node> drawables = new ArrayList<>();
            collectDrawableElements(ctx.rootNode, drawables);
            for (Node node : drawables) {
                String d = getRawPathData(node);
                if (d == null)
                    continue;
                try {
                    parser.parse(d);
                    Shape shape = producer.getShape();
                    // Apply element transform
                    shape = applyElementTransform(node, shape);
                    // Add current shape bounds to builder
                    java.awt.Rectangle r = shape.getBounds();
                    // getBounds is int, getBounds2D is double. Let's strictly iterate path to be
                    // 100% precise or use Bounds2D
                    java.awt.geom.Rectangle2D r2d = shape.getBounds2D();
                    builder.add(r2d.getMinX(), r2d.getMinY());
                    builder.add(r2d.getMaxX(), r2d.getMaxY());
                } catch (Exception e) {
                    logger.warn("Skipping element during bounds calculation: {}", e.getMessage());
                }
            }
        }
        return builder.build();
    }

    AffineTransform calculateScaleTransform(Bounds contentBounds,
            double targetWidth, double targetHeight, boolean keepAspectRatio,
            double posX, double posY) {
        if (contentBounds.minX() == Double.MAX_VALUE)
            return new AffineTransform();

        double contentWidth = contentBounds.maxX() - contentBounds.minX();
        double contentHeight = contentBounds.maxY() - contentBounds.minY();

        double scaleX = targetWidth / contentWidth;
        double scaleY = targetHeight / contentHeight;

        if (keepAspectRatio) {
            double scale = Math.min(scaleX, scaleY);
            scaleX = scale;
            scaleY = scale;
        }

        double scaledWidth = contentWidth * scaleX;
        double scaledHeight = contentHeight * scaleY;
        double centerOffsetX = (targetWidth - scaledWidth) / 2.0;
        double centerOffsetY = (targetHeight - scaledHeight) / 2.0;

        logger.info("Scale Transform: Content={}x{}, Target={}x{}, Scale=({}, {}), Pos=({}, {})",
                contentWidth, contentHeight, targetWidth, targetHeight, scaleX, scaleY, posX, posY);

        AffineTransform tx = new AffineTransform();
        tx.translate(posX + centerOffsetX, posY + centerOffsetY);
        tx.scale(scaleX, scaleY);
        tx.translate(-contentBounds.minX(), -contentBounds.minY());

        return tx;
    }

    // Package-private for testability
    AffineTransform calculateFitToPageTransform(Bounds contentBounds, PaperFormat format, double padding) {
        if (contentBounds.minX() == Double.MAX_VALUE)
            return new AffineTransform();

        double contentWidth = contentBounds.maxX() - contentBounds.minX();
        double contentHeight = contentBounds.maxY() - contentBounds.minY();

        double targetWidth = format.width() - (padding * 2);
        double targetHeight = format.height() - (padding * 2);

        if (targetWidth <= 0 || targetHeight <= 0) {
            logger.warn("Padding is too large for the selected format!");
            return new AffineTransform();
        }

        double scaleX = targetWidth / contentWidth;
        double scaleY = targetHeight / contentHeight;
        double scale = Math.min(scaleX, scaleY);

        logger.info("Auto-Scale Calculation: Content={}x{}, Target={}x{}, Scale={}",
                contentWidth, contentHeight, targetWidth, targetHeight, scale);

        double offsetX = padding + (targetWidth - (contentWidth * scale)) / 2.0;
        double offsetY = padding + (targetHeight - (contentHeight * scale)) / 2.0;

        AffineTransform tx = new AffineTransform();
        // 3. Translate to Final Position
        tx.translate(offsetX, offsetY);
        // 2. Scale
        tx.scale(scale, scale);
        // 1. Translate to Origin (so 0,0 is logical top-left of content)
        tx.translate(-contentBounds.minX(), -contentBounds.minY());

        return tx;
    }

    private Shape applyElementTransform(Node node, Shape shape) {
        // Accumulate transforms from the element up through all parent <g> elements
        AffineTransform accumulated = getAccumulatedTransform(node);
        if (!accumulated.isIdentity()) {
            return accumulated.createTransformedShape(shape);
        }
        return shape;
    }

    /**
     * Walk from the given node up to the document root, collecting all
     * transform attributes from parent elements. Returns a single
     * AffineTransform that is the concatenation of all ancestor transforms
     * (outermost first) followed by the element's own transform.
     */
    private AffineTransform getAccumulatedTransform(Node node) {
        // Collect transforms bottom-up, then apply top-down
        java.util.Deque<AffineTransform> stack = new java.util.ArrayDeque<>();

        Node current = node;
        while (current != null && current.getNodeType() == Node.ELEMENT_NODE) {
            Element el = (Element) current;
            if (el.hasAttribute("transform")) {
                try {
                    TransformListParser tParser = new TransformListParser();
                    AWTTransformProducer tProducer = new AWTTransformProducer();
                    tParser.setTransformListHandler(tProducer);
                    tParser.parse(el.getAttribute("transform"));
                    stack.push(tProducer.getAffineTransform());
                } catch (Exception ex) {
                    logger.warn("Failed to parse transform '{}': {}", el.getAttribute("transform"), ex.getMessage());
                }
            }
            current = current.getParentNode();
        }

        AffineTransform accumulated = new AffineTransform();
        // Pop applies outermost (root) transform first, then inner transforms
        while (!stack.isEmpty()) {
            accumulated.concatenate(stack.pop());
        }
        return accumulated;
    }
}