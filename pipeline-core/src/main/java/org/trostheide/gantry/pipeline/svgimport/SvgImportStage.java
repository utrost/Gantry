package org.trostheide.gantry.pipeline.svgimport;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.parser.AWTPathProducer;
import org.apache.batik.parser.AWTTransformProducer;
import org.apache.batik.parser.PathParser;
import org.apache.batik.parser.TransformListParser;
import org.apache.batik.util.XMLResourceDescriptor;
import org.trostheide.gantry.model.Bounds;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Metadata;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;
import org.trostheide.gantry.model.command.RefillCommand;
import org.trostheide.gantry.svgtoolbox.SvgToolboxPipeline;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Converts an SVG file into a {@link ProcessorOutput} command model: the in-process port of
 * {@code legacy/SVG2WaterColor}'s {@code ProcessorService}.
 *
 * <p>Inkscape {@code <g inkscape:groupmode="layer">} groups become {@code Layer1}, {@code Layer2},
 * ... (each its own refill station by default); SVGs without explicit layers become a single
 * {@code "Default"} layer using {@link SvgImportOptions#defaultStationId()}. Curves are flattened
 * to polylines, primitives (rect/circle/ellipse/line/polyline/polygon) are normalized to paths,
 * and element/ancestor {@code transform} attributes are applied. When
 * {@link SvgImportOptions#maxDrawDistance()} is positive, a REFILL command is inserted at the
 * start of each layer and whenever the cumulative draw distance since the last refill would
 * exceed it (splitting the stroke at the boundary).
 */
public final class SvgImportStage {

    private SvgImportStage() {
    }

    /** Loads {@code inputFile} as an SVG and converts it to a command model. */
    public static ProcessorOutput importSvg(File inputFile, SvgImportOptions options) throws IOException {
        Document doc = loadDocument(inputFile);
        return importSvg(doc, inputFile.getName(), options);
    }

    /**
     * Loads {@code inputFile} as an SVG, runs the SVGToolBox processor pipeline against it using
     * {@code toolboxConfig}, and converts the result to a command model.
     */
    public static ProcessorOutput importSvg(File inputFile, org.trostheide.gantry.svgtoolbox.Config toolboxConfig,
            SvgImportOptions options) throws IOException {
        Document doc = loadDocument(inputFile);
        SvgToolboxPipeline.process(doc, toolboxConfig);
        return importSvg(doc, inputFile.getName(), options);
    }

    /** Converts an already-loaded SVG {@link Document} to a command model. */
    public static ProcessorOutput importSvg(Document doc, String sourceName, SvgImportOptions options) {
        boolean hasTargetSize = options.targetWidth() > 0 && options.targetHeight() > 0;
        boolean hasPosition = options.posX() != 0 || options.posY() != 0;

        List<LayerContext> layersToProcess = identifyLayers(doc, options.defaultStationId());

        AffineTransform globalTx = new AffineTransform();
        // Always scan the content bounds: the Y-flip below needs them on every import, and so do
        // the fit/position/mirror transforms.
        Bounds preScannedBounds = calculateGlobalBounds(layersToProcess);
        boolean haveBounds = preScannedBounds.minX() != Double.MAX_VALUE;

        if (hasTargetSize && haveBounds) {
            globalTx = calculateScaleTransform(preScannedBounds, options.targetWidth(), options.targetHeight(),
                    options.keepAspectRatio(), options.posX(), options.posY());
        } else if (hasPosition && haveBounds) {
            globalTx.translate(options.posX() - preScannedBounds.minX(), options.posY() - preScannedBounds.minY());
        }

        if (options.mirror()) {
            double pivotX;
            if (hasTargetSize) {
                pivotX = options.posX() + options.targetWidth() / 2.0;
            } else if (haveBounds) {
                pivotX = (preScannedBounds.minX() + preScannedBounds.maxX()) / 2.0;
            } else {
                pivotX = 0;
            }
            AffineTransform mirrorTx = new AffineTransform(-1, 0, 0, 1, 2 * pivotX, 0);
            globalTx.preConcatenate(mirrorTx);
        }

        // SVG uses a Y-down coordinate system (origin top-left), but the plotter/visualization treat
        // Y as growing upward from the machine origin — so raw SVG content imports upside down. Mirror
        // Y about the content's own vertical center so drawings come in upright without needing the
        // manual "Flip Y" override. Concatenated last so it applies first, in raw content space,
        // before the scale/position/mirror transforms (and it preserves the content's Y extents).
        if (haveBounds) {
            AffineTransform flipY = new AffineTransform(1, 0, 0, -1, 0,
                    preScannedBounds.minY() + preScannedBounds.maxY());
            globalTx.concatenate(flipY);
        }

        List<Layer> resultLayers = new ArrayList<>();
        BoundsBuilder globalBoundsBuilder = new BoundsBuilder();
        PathParser pathParser = new PathParser();
        AWTPathProducer pathProducer = new AWTPathProducer();
        pathParser.setPathHandler(pathProducer);

        int totalCommands = 0;
        int[] commandCounter = {1};

        for (LayerContext ctx : layersToProcess) {
            List<Command> layerCommands = generateCommandsForLayer(ctx.rootNode, ctx.stationId,
                    options.maxDrawDistance(), options.curveStep(), pathParser, pathProducer,
                    globalBoundsBuilder, globalTx, commandCounter, preScannedBounds);

            if (!layerCommands.isEmpty()) {
                resultLayers.add(new Layer(ctx.layerName, ctx.stationId, layerCommands));
                totalCommands += layerCommands.size();
            }
        }

        Metadata metadata = new Metadata(
                sourceName,
                Instant.now(),
                "MULTI_LAYER",
                "mm",
                totalCommands,
                globalBoundsBuilder.build());

        return new ProcessorOutput(metadata, resultLayers);
    }

    private static Document loadDocument(File inputFile) throws IOException {
        try {
            String parser = XMLResourceDescriptor.getXMLParserClassName();
            SAXSVGDocumentFactory f = new SAXSVGDocumentFactory(parser);
            return f.createDocument(inputFile.toURI().toString());
        } catch (org.w3c.dom.DOMException e) {
            try {
                javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true);
                return dbf.newDocumentBuilder().parse(inputFile);
            } catch (Exception inner) {
                throw new IOException("Failed to parse SVG: " + inner.getMessage(), inner);
            }
        }
    }

    // --- Layer identification ---

    private record LayerContext(String layerName, String stationId, Element rootNode) {
    }

    private static List<LayerContext> identifyLayers(Document doc, String defaultStationId) {
        List<LayerContext> contexts = new ArrayList<>();
        Element root = doc.getDocumentElement();

        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals("g")) {
                Element g = (Element) n;
                if ("layer".equals(g.getAttribute("inkscape:groupmode"))
                        || "layer".equals(g.getAttributeNS("http://www.inkscape.org/namespaces/inkscape", "groupmode"))) {

                    String label = g.getAttribute("inkscape:label");
                    if (label.isEmpty()) {
                        label = g.getAttributeNS("http://www.inkscape.org/namespaces/inkscape", "label");
                    }

                    String genericId = "Layer" + (contexts.size() + 1);
                    String stationId = genericId;
                    String layerName = (label != null && !label.isEmpty()) ? label + " (" + genericId + ")" : genericId;

                    contexts.add(new LayerContext(layerName, stationId, g));
                }
            }
        }

        if (contexts.isEmpty()) {
            contexts.add(new LayerContext("Default", defaultStationId, root));
        }

        return contexts;
    }

    // --- Core generation ---

    private static List<Command> generateCommandsForLayer(Element layerRoot, String stationId, double maxDist,
            double curveStep, PathParser parser, AWTPathProducer producer, BoundsBuilder bounds,
            AffineTransform globalTx, int[] commandCounter, Bounds contentBounds) {
        List<Command> cmds = new ArrayList<>();
        List<Node> drawables = new ArrayList<>();
        collectDrawableElements(layerRoot, drawables);

        if (drawables.isEmpty()) {
            return cmds;
        }

        if (maxDist > 0) {
            cmds.add(new RefillCommand(commandCounter[0]++, stationId));
        }
        double currentPaintDist = 0.0;

        for (Node node : drawables) {
            String d = getRawPathData(node);
            if (d == null) {
                continue;
            }

            parser.parse(d);
            Shape shape = producer.getShape();

            shape = applyElementTransform(node, shape);

            // Skip a full-page background/border rectangle (e.g. an Inkscape page rect): it would
            // otherwise be plotted as the drawing's outer frame before the real content.
            if (isPageBorderRect(shape, contentBounds)) {
                continue;
            }

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
                    case PathIterator.SEG_MOVETO -> {
                        if (!currentStroke.isEmpty()) {
                            finishStroke(cmds, currentStroke, bounds, commandCounter);
                        }
                        double mx = coords[0];
                        double my = coords[1];
                        cmds.add(new MoveCommand(commandCounter[0]++, mx, my));
                        bounds.add(mx, my);
                        currentPos = new Point(mx, my);
                        startPoint = currentPos;
                        currentStroke.add(currentPos);
                    }
                    case PathIterator.SEG_LINETO -> {
                        Point target = new Point(coords[0], coords[1]);
                        double dist = distance(currentPos, target);

                        while (maxDist > 0 && currentPaintDist + dist > maxDist) {
                            double rem = maxDist - currentPaintDist;
                            Point split = interpolate(currentPos, target, rem, dist);

                            currentStroke.add(split);
                            finishStroke(cmds, currentStroke, bounds, commandCounter);

                            cmds.add(new RefillCommand(commandCounter[0]++, stationId));
                            cmds.add(new MoveCommand(commandCounter[0]++, split.x(), split.y()));
                            bounds.add(split.x(), split.y());

                            currentPaintDist = 0.0;
                            currentPos = split;
                            currentStroke.add(currentPos);
                            dist = distance(currentPos, target);
                        }
                        currentStroke.add(target);
                        currentPaintDist += dist;
                        currentPos = target;
                    }
                    case PathIterator.SEG_CLOSE -> {
                        if (startPoint != null && currentPos != null && !startPoint.equals(currentPos)) {
                            double cDist = distance(currentPos, startPoint);
                            while (maxDist > 0 && currentPaintDist + cDist > maxDist) {
                                double rem = maxDist - currentPaintDist;
                                Point split = interpolate(currentPos, startPoint, rem, cDist);
                                currentStroke.add(split);
                                finishStroke(cmds, currentStroke, bounds, commandCounter);

                                cmds.add(new RefillCommand(commandCounter[0]++, stationId));
                                cmds.add(new MoveCommand(commandCounter[0]++, split.x(), split.y()));
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
                    }
                    default -> {
                        // SEG_QUADTO/SEG_CUBICTO never appear: getPathIterator(null, curveStep) flattens them.
                    }
                }
                pi.next();
            }
            if (!currentStroke.isEmpty()) {
                finishStroke(cmds, currentStroke, bounds, commandCounter);
            }
        }
        return cmds;
    }

    private static void finishStroke(List<Command> cmds, List<Point> stroke, BoundsBuilder bounds, int[] commandCounter) {
        cmds.add(new DrawCommand(commandCounter[0]++, new ArrayList<>(stroke)));
        for (Point p : stroke) {
            bounds.add(p.x(), p.y());
        }
        stroke.clear();
    }

    // --- Helpers ---

    /**
     * Returns true if {@code shape} is an axis-aligned rectangle that spans (within tolerance) the
     * whole {@code contentBounds} — i.e. a full-page background/border rectangle that should not be
     * plotted as the drawing's outer frame.
     */
    private static boolean isPageBorderRect(Shape shape, Bounds contentBounds) {
        if (contentBounds == null || contentBounds.minX() == Double.MAX_VALUE) {
            return false;
        }
        double gw = contentBounds.maxX() - contentBounds.minX();
        double gh = contentBounds.maxY() - contentBounds.minY();
        if (gw <= 0 || gh <= 0) {
            return false;
        }
        double tol = 0.02 * Math.max(gw, gh) + 0.01;
        Rectangle2D b = shape.getBounds2D();
        boolean spansContent = Math.abs(b.getMinX() - contentBounds.minX()) <= tol
                && Math.abs(b.getMinY() - contentBounds.minY()) <= tol
                && Math.abs(b.getMaxX() - contentBounds.maxX()) <= tol
                && Math.abs(b.getMaxY() - contentBounds.maxY()) <= tol;
        return spansContent && isAxisAlignedRectangleOutline(shape, b, tol);
    }

    /** True if every vertex of {@code shape} sits on a corner of {@code box} and it has no curves. */
    private static boolean isAxisAlignedRectangleOutline(Shape shape, Rectangle2D box, double tol) {
        PathIterator pi = shape.getPathIterator(null);
        double[] c = new double[6];
        int vertices = 0;
        while (!pi.isDone()) {
            int type = pi.currentSegment(c);
            if (type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO) {
                boolean onX = Math.abs(c[0] - box.getMinX()) <= tol || Math.abs(c[0] - box.getMaxX()) <= tol;
                boolean onY = Math.abs(c[1] - box.getMinY()) <= tol || Math.abs(c[1] - box.getMaxY()) <= tol;
                if (!(onX && onY)) {
                    return false;
                }
                vertices++;
            } else if (type == PathIterator.SEG_QUADTO || type == PathIterator.SEG_CUBICTO) {
                return false;
            }
            pi.next();
        }
        return vertices >= 4;
    }

    private static void collectDrawableElements(Node node, List<Node> result) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            String tagName = ((Element) node).getTagName();
            if (tagName.equals("path") || tagName.equals("rect") || tagName.equals("circle")
                    || tagName.equals("ellipse") || tagName.equals("line") || tagName.equals("polyline")
                    || tagName.equals("polygon")) {
                result.add(node);
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            collectDrawableElements(children.item(i), result);
        }
    }

    private static String getRawPathData(Node node) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return null;
        }
        Element el = (Element) node;
        String tagName = el.getTagName();

        try {
            switch (tagName) {
                case "path":
                    return el.getAttribute("d");
                case "rect": {
                    double x = attr(el, "x");
                    double y = attr(el, "y");
                    double w = attr(el, "width");
                    double h = attr(el, "height");
                    return String.format(Locale.US, "M%f %f L%f %f L%f %f L%f %f Z",
                            x, y, x + w, y, x + w, y + h, x, y + h);
                }
                case "circle": {
                    double cx = attr(el, "cx");
                    double cy = attr(el, "cy");
                    double r = attr(el, "r");
                    return String.format(Locale.US, "M %f %f A %f %f 0 1 0 %f %f A %f %f 0 1 0 %f %f Z",
                            cx - r, cy, r, r, cx + r, cy, r, r, cx - r, cy);
                }
                case "ellipse": {
                    double cx = attr(el, "cx");
                    double cy = attr(el, "cy");
                    double rx = attr(el, "rx");
                    double ry = attr(el, "ry");
                    return String.format(Locale.US, "M %f %f A %f %f 0 1 0 %f %f A %f %f 0 1 0 %f %f Z",
                            cx - rx, cy, rx, ry, cx + rx, cy, rx, ry, cx - rx, cy);
                }
                case "line":
                    return String.format(Locale.US, "M %f %f L %f %f",
                            attr(el, "x1"), attr(el, "y1"), attr(el, "x2"), attr(el, "y2"));
                case "polyline":
                case "polygon": {
                    String points = el.getAttribute("points").trim();
                    if (points.isEmpty()) {
                        return null;
                    }
                    String[] pts = points.split("[\\s,]+");
                    if (pts.length < 2) {
                        return null;
                    }
                    StringBuilder sb = new StringBuilder("M ").append(pts[0]).append(" ").append(pts[1]);
                    for (int i = 2; i < pts.length - 1; i += 2) {
                        sb.append(" L ").append(pts[i]).append(" ").append(pts[i + 1]);
                    }
                    if ("polygon".equals(tagName)) {
                        sb.append(" Z");
                    }
                    return sb.toString();
                }
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static double attr(Element e, String attr) {
        String v = e.getAttribute(attr);
        return v.isEmpty() ? 0.0 : Double.parseDouble(v);
    }

    static double distance(Point p1, Point p2) {
        return Math.sqrt(Math.pow(p2.x() - p1.x(), 2) + Math.pow(p2.y() - p1.y(), 2));
    }

    static Point interpolate(Point start, Point end, double distToTravel, double totalDist) {
        if (totalDist == 0) {
            return start;
        }
        double ratio = distToTravel / totalDist;
        return new Point(start.x() + (end.x() - start.x()) * ratio, start.y() + (end.y() - start.y()) * ratio);
    }

    static final class BoundsBuilder {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        void add(double x, double y) {
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
        }

        Bounds build() {
            if (minX == Double.MAX_VALUE) {
                return Bounds.empty();
            }
            return new Bounds(minX, minY, maxX, maxY);
        }
    }

    // --- Auto-scaling ---

    private static Bounds calculateGlobalBounds(List<LayerContext> contexts) {
        BoundsBuilder builder = new BoundsBuilder();
        PathParser parser = new PathParser();
        AWTPathProducer producer = new AWTPathProducer();
        parser.setPathHandler(producer);

        for (LayerContext ctx : contexts) {
            List<Node> drawables = new ArrayList<>();
            collectDrawableElements(ctx.rootNode, drawables);
            for (Node node : drawables) {
                String d = getRawPathData(node);
                if (d == null) {
                    continue;
                }
                try {
                    parser.parse(d);
                    Shape shape = producer.getShape();
                    shape = applyElementTransform(node, shape);
                    Rectangle2D r2d = shape.getBounds2D();
                    builder.add(r2d.getMinX(), r2d.getMinY());
                    builder.add(r2d.getMaxX(), r2d.getMaxY());
                } catch (Exception ignored) {
                    // skip elements whose path data can't be parsed
                }
            }
        }
        return builder.build();
    }

    static AffineTransform calculateScaleTransform(Bounds contentBounds, double targetWidth, double targetHeight,
            boolean keepAspectRatio, double posX, double posY) {
        if (contentBounds.minX() == Double.MAX_VALUE) {
            return new AffineTransform();
        }

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

        AffineTransform tx = new AffineTransform();
        tx.translate(posX + centerOffsetX, posY + centerOffsetY);
        tx.scale(scaleX, scaleY);
        tx.translate(-contentBounds.minX(), -contentBounds.minY());

        return tx;
    }

    /** Computes the scale+translate transform to fit {@code contentBounds} into {@code format} with {@code padding}mm margins. */
    public static AffineTransform calculateFitToPageTransform(Bounds contentBounds, PaperFormat format, double padding) {
        if (contentBounds.minX() == Double.MAX_VALUE) {
            return new AffineTransform();
        }

        double contentWidth = contentBounds.maxX() - contentBounds.minX();
        double contentHeight = contentBounds.maxY() - contentBounds.minY();

        double targetWidth = format.width() - (padding * 2);
        double targetHeight = format.height() - (padding * 2);

        if (targetWidth <= 0 || targetHeight <= 0) {
            return new AffineTransform();
        }

        double scaleX = targetWidth / contentWidth;
        double scaleY = targetHeight / contentHeight;
        double scale = Math.min(scaleX, scaleY);

        double offsetX = padding + (targetWidth - (contentWidth * scale)) / 2.0;
        double offsetY = padding + (targetHeight - (contentHeight * scale)) / 2.0;

        AffineTransform tx = new AffineTransform();
        tx.translate(offsetX, offsetY);
        tx.scale(scale, scale);
        tx.translate(-contentBounds.minX(), -contentBounds.minY());

        return tx;
    }

    private static Shape applyElementTransform(Node node, Shape shape) {
        AffineTransform accumulated = getAccumulatedTransform(node);
        if (!accumulated.isIdentity()) {
            return accumulated.createTransformedShape(shape);
        }
        return shape;
    }

    /**
     * Walks from {@code node} up to the document root, collecting every ancestor
     * {@code transform} attribute, and returns their concatenation (outermost first).
     */
    private static AffineTransform getAccumulatedTransform(Node node) {
        Deque<AffineTransform> stack = new ArrayDeque<>();

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
                } catch (Exception ignored) {
                    // skip unparsable transform attributes
                }
            }
            current = current.getParentNode();
        }

        AffineTransform accumulated = new AffineTransform();
        while (!stack.isEmpty()) {
            accumulated.concatenate(stack.pop());
        }
        return accumulated;
    }
}
