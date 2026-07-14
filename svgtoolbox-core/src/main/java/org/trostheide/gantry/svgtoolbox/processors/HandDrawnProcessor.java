package org.trostheide.gantry.svgtoolbox.processors;

import org.trostheide.gantry.svgtoolbox.Config;
import org.trostheide.gantry.svgtoolbox.Processor;
import org.trostheide.gantry.svgtoolbox.core.ShapeParser;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Gives paths a hand-drawn look by resampling each subpath into dense, even samples
 * and nudging every interior sample sideways (along the line normal) by a smooth,
 * low-frequency random amount. The wobble is applied in the line's own frame of
 * reference, so it reads as a natural "side to side" waver regardless of the line's
 * orientation.
 *
 * <p>Technique after Scott Turner, "This Is Where I Draw the Line" (Here Dragons
 * Abound, 2016), itself after the XKCD-plot jitter algorithm. Three moves:
 * <ol>
 *   <li><b>Resample</b> each subpath to ~{@code handdrawnSegment} spacing.</li>
 *   <li><b>Jitter</b> each sample along the normal by smooth noise whose wavelength
 *       is {@code handdrawnWavelength} and whose amplitude is {@code handdrawnMagnitude}.</li>
 *   <li><b>Pin the endpoints</b> (offset tapers to zero at both ends) so separate
 *       strokes still meet exactly and closed paths stay closed — this also avoids the
 *       end "hook" the article warns about.</li>
 * </ol>
 *
 * <p>Operates on paths and the standard SVG geometry primitives in local user units.
 * Primitives are replaced by equivalent path elements while retaining their non-geometry
 * attributes. Curves are flattened to line samples before resampling, so this must run
 * <em>before</em> {@code LinesimplifyProcessor}/{@code LinemergeProcessor} — RDP would
 * otherwise straighten the wobble right back out.
 *
 * <p>The random stream is seeded from {@code handdrawnSeed} so a given input plots the
 * same way every time.
 */
public class HandDrawnProcessor implements Processor {

    private static final Set<String> SUPPORTED_ELEMENTS = new HashSet<>(Arrays.asList(
            "path", "line", "rect", "circle", "ellipse", "polyline", "polygon"));
    private static final Set<String> GEOMETRY_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "d", "x", "y", "width", "height", "rx", "ry", "cx", "cy", "r",
            "x1", "y1", "x2", "y2", "points"));

    @Override
    public void process(Document doc, Config config) {
        if (!config.handdrawn()) return;

        double magnitude = config.handdrawnMagnitude();
        double segment = config.handdrawnSegment();
        double wavelength = config.handdrawnWavelength();
        if (magnitude <= 0 || segment <= 0 || wavelength <= 0) return;

        Random rng = new Random(config.handdrawnSeed());

        List<Element> elements = drawableElements(doc);
        int count = 0;
        for (Element el : elements) {
            if (handDrawElement(el, magnitude, segment, wavelength, rng)) {
                count++;
            }
        }
        System.out.println("HandDrawn: roughened " + count + " shapes (magnitude: " + magnitude
                + ", segment: " + segment + ", wavelength: " + wavelength + ")");
    }

    private boolean handDrawElement(Element el, double magnitude, double segment, double wavelength, Random rng) {
        Shape shape = ShapeParser.parse(el);
        if (shape == null) return false;

        // A tighter flatness than the requested sample spacing preserves the actual curve
        // before the regular resampling pass. Keep an upper bound for large segment settings.
        double flatness = Math.max(0.01, Math.min(0.5, segment / 4.0));
        List<Subpath> subpaths = parseSubpaths(shape, flatness);
        if (subpaths.isEmpty()) return false;

        boolean any = false;
        for (Subpath sp : subpaths) {
            if (sp.points.size() < 2) continue;
            List<double[]> resampled = resample(sp.points, segment);
            double[] offsets = smoothOffsets(resampled, wavelength, magnitude, rng);
            sp.points = applyJitter(resampled, offsets);
            any = true;
        }

        if (any) {
            Element path = asPathElement(el);
            path.setAttribute("d", toPathString(subpaths));
        }
        return any;
    }

    private List<Element> drawableElements(Document doc) {
        NodeList nodes = doc.getElementsByTagName("*");
        List<Element> elements = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            if (SUPPORTED_ELEMENTS.contains(elementName(el))) {
                elements.add(el);
            }
        }
        return elements;
    }

    private String elementName(Element el) {
        String localName = el.getLocalName();
        return localName == null ? el.getTagName() : localName;
    }

    /** Replaces an SVG primitive with a path while preserving styling, identity and transforms. */
    private Element asPathElement(Element el) {
        if ("path".equals(elementName(el))) return el;

        Document doc = el.getOwnerDocument();
        String namespace = el.getNamespaceURI();
        String prefix = el.getPrefix();
        String qualifiedName = prefix == null || prefix.isBlank() ? "path" : prefix + ":path";
        Element path = namespace == null
                ? doc.createElement(qualifiedName)
                : doc.createElementNS(namespace, qualifiedName);

        NamedNodeMap attributes = el.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attribute = (Attr) attributes.item(i);
            String localName = attribute.getLocalName();
            String name = localName == null ? attribute.getName() : localName;
            if (!GEOMETRY_ATTRIBUTES.contains(name)) {
                path.setAttributeNodeNS((Attr) attribute.cloneNode(true));
            }
        }

        Node parent = el.getParentNode();
        if (parent != null) {
            parent.replaceChild(path, el);
        }
        return path;
    }

    /** Resamples a polyline into samples spaced roughly {@code step} apart. */
    private List<double[]> resample(List<double[]> points, double step) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < points.size() - 1; i++) {
            double[] a = points.get(i);
            double[] b = points.get(i + 1);
            double dx = b[0] - a[0];
            double dy = b[1] - a[1];
            double dist = Math.hypot(dx, dy);
            int n = Math.max(1, (int) Math.round(dist / step));
            for (int j = 0; j < n; j++) {
                double t = (double) j / n;
                out.add(new double[]{a[0] + dx * t, a[1] + dy * t});
            }
        }
        double[] last = points.get(points.size() - 1);
        out.add(new double[]{last[0], last[1]});
        return out;
    }

    /** Cumulative arc length at each sample. */
    private double[] arcLengths(List<double[]> pts) {
        double[] l = new double[pts.size()];
        for (int i = 1; i < pts.size(); i++) {
            double[] a = pts.get(i - 1);
            double[] b = pts.get(i);
            l[i] = l[i - 1] + Math.hypot(b[0] - a[0], b[1] - a[1]);
        }
        return l;
    }

    /**
     * Low-frequency offsets via cosine-interpolated random control values, forced to zero
     * at both ends (and tapered over the first/last few samples to fully suppress end hooks).
     */
    private double[] smoothOffsets(List<double[]> pts, double wavelength, double magnitude, Random rng) {
        double[] l = arcLengths(pts);
        double total = l[l.length - 1];
        if (total <= 0) return new double[pts.size()];

        int nCtrl = Math.max(2, (int) Math.round(total / wavelength) + 1);
        double[] ctrl = new double[nCtrl];
        for (int k = 0; k < nCtrl; k++) {
            ctrl[k] = (rng.nextDouble() * 2 - 1) * magnitude;
        }
        ctrl[0] = 0;
        ctrl[nCtrl - 1] = 0;

        double[] offs = new double[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            double u = (l[i] / total) * (nCtrl - 1);
            int k = Math.min(nCtrl - 2, (int) Math.floor(u));
            double f = u - k;
            double w = (1 - Math.cos(f * Math.PI)) / 2;
            offs[i] = ctrl[k] * (1 - w) + ctrl[k + 1] * w;
        }

        int taperN = Math.min(3, pts.size() / 2);
        for (int i = 0; i < taperN; i++) {
            double s = (double) i / taperN;
            offs[i] *= s;
            offs[pts.size() - 1 - i] *= s;
        }
        return offs;
    }

    /** Offsets each sample along its local normal (perpendicular to the tangent). */
    private List<double[]> applyJitter(List<double[]> pts, double[] offsets) {
        List<double[]> out = new ArrayList<>(pts.size());
        int n = pts.size();
        for (int i = 0; i < n; i++) {
            double[] a = pts.get(Math.max(0, i - 1));
            double[] b = pts.get(Math.min(n - 1, i + 1));
            double tx = b[0] - a[0];
            double ty = b[1] - a[1];
            double m = Math.hypot(tx, ty);
            if (m == 0) {
                out.add(new double[]{pts.get(i)[0], pts.get(i)[1]});
                continue;
            }
            tx /= m;
            ty /= m;
            double[] p = pts.get(i);
            // normal = (-ty, tx)
            out.add(new double[]{p[0] - ty * offsets[i], p[1] + tx * offsets[i]});
        }
        return out;
    }

    private List<Subpath> parseSubpaths(Shape shape, double flatness) {
        List<Subpath> subpaths = new ArrayList<>();
        try {
            PathIterator pi = shape.getPathIterator(null, flatness);
            double[] coords = new double[6];
            Subpath current = null;

            while (!pi.isDone()) {
                int type = pi.currentSegment(coords);
                switch (type) {
                    case PathIterator.SEG_MOVETO:
                        current = new Subpath();
                        current.points.add(new double[]{coords[0], coords[1]});
                        subpaths.add(current);
                        break;
                    case PathIterator.SEG_LINETO:
                        if (current != null) {
                            current.points.add(new double[]{coords[0], coords[1]});
                        }
                        break;
                    case PathIterator.SEG_CLOSE:
                        if (current != null) {
                            current.closed = true;
                            double[] first = current.points.get(0);
                            double[] last = current.points.get(current.points.size() - 1);
                            if (first[0] != last[0] || first[1] != last[1]) {
                                current.points.add(new double[]{first[0], first[1]});
                            }
                        }
                        break;
                    default:
                        break;
                }
                pi.next();
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }
        return subpaths;
    }

    private String toPathString(List<Subpath> subpaths) {
        StringBuilder sb = new StringBuilder();
        for (Subpath sp : subpaths) {
            if (sp.points.isEmpty()) continue;
            double[] first = sp.points.get(0);
            sb.append(String.format(Locale.US, "M%.4f,%.4f ", first[0], first[1]));
            for (int i = 1; i < sp.points.size(); i++) {
                double[] pt = sp.points.get(i);
                sb.append(String.format(Locale.US, "L%.4f,%.4f ", pt[0], pt[1]));
            }
            if (sp.closed) {
                sb.append("Z ");
            }
        }
        return sb.toString().trim();
    }

    static class Subpath {
        List<double[]> points = new ArrayList<>();
        boolean closed = false;
    }
}
