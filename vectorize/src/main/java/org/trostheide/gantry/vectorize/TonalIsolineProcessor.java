package org.trostheide.gantry.vectorize;

import georegression.struct.point.Point2D_I32;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Raster-to-polyline image style that traces brightness-level contours, similar
 * to topographic map lines. It uses marching squares so non-radial image tones
 * remain local contours instead of centroid-sorted artificial rings.
 */
public final class TonalIsolineProcessor {
    private TonalIsolineProcessor() {
    }

    public record Options(int levels, double smoothing, int minLength, double threshold) {
        public static Options defaults() {
            return new Options(6, 3.0, 8, 0.06);
        }
    }

    private record Segment(Point2D_I32 a, Point2D_I32 b) {
    }

    public static List<VectorGeometry> process(BufferedImage image, Options options) {
        if (image == null || image.getWidth() < 2 || image.getHeight() < 2) {
            return List.of();
        }
        Options safe = sanitize(options);
        double[][] gray = grayscale(image);
        gray = boxBlur(gray, (int) Math.round(safe.smoothing()));
        if (toneRange(gray) < safe.threshold()) {
            return List.of();
        }

        int width = image.getWidth();
        int height = image.getHeight();
        List<VectorGeometry> out = new ArrayList<>();
        for (int level = 1; level <= safe.levels(); level++) {
            double iso = level / (double) (safe.levels() + 1);
            List<Segment> segments = new ArrayList<>();
            for (int y = 0; y < height - 1; y++) {
                for (int x = 0; x < width - 1; x++) {
                    appendCellSegments(gray, x, y, iso, segments);
                }
            }
            out.addAll(chainSegments(segments, safe.minLength()));
        }
        return out;
    }

    private static Options sanitize(Options options) {
        Options source = options == null ? Options.defaults() : options;
        return new Options(
                Math.max(1, Math.min(24, source.levels())),
                Math.max(0.0, source.smoothing()),
                Math.max(2, source.minLength()),
                Math.max(0.0, Math.min(1.0, source.threshold())));
    }

    private static void appendCellSegments(double[][] gray, int x, int y, double iso, List<Segment> segments) {
        double tl = gray[y][x];
        double tr = gray[y][x + 1];
        double br = gray[y + 1][x + 1];
        double bl = gray[y + 1][x];

        int cellCase = 0;
        if (tl >= iso) cellCase |= 1;
        if (tr >= iso) cellCase |= 2;
        if (br >= iso) cellCase |= 4;
        if (bl >= iso) cellCase |= 8;

        if (cellCase == 0 || cellCase == 15) {
            return;
        }

        Point2D_I32 top = interpolate(x, y, x + 1, y, tl, tr, iso);
        Point2D_I32 right = interpolate(x + 1, y, x + 1, y + 1, tr, br, iso);
        Point2D_I32 bottom = interpolate(x, y + 1, x + 1, y + 1, bl, br, iso);
        Point2D_I32 left = interpolate(x, y, x, y + 1, tl, bl, iso);

        switch (cellCase) {
            case 1 -> addSegment(segments, left, top);
            case 2 -> addSegment(segments, top, right);
            case 3 -> addSegment(segments, left, right);
            case 4 -> addSegment(segments, right, bottom);
            case 5 -> {
                addSegment(segments, left, bottom);
                addSegment(segments, top, right);
            }
            case 6 -> addSegment(segments, top, bottom);
            case 7 -> addSegment(segments, left, bottom);
            case 8 -> addSegment(segments, bottom, left);
            case 9 -> addSegment(segments, top, bottom);
            case 10 -> {
                addSegment(segments, top, left);
                addSegment(segments, right, bottom);
            }
            case 11 -> addSegment(segments, right, bottom);
            case 12 -> addSegment(segments, left, right);
            case 13 -> addSegment(segments, top, right);
            case 14 -> addSegment(segments, left, top);
            default -> { }
        }
    }

    private static Point2D_I32 interpolate(int x1, int y1, int x2, int y2, double v1, double v2, double iso) {
        double denom = v2 - v1;
        double t = Math.abs(denom) < 1e-9 ? 0.5 : (iso - v1) / denom;
        t = Math.max(0.0, Math.min(1.0, t));
        int x = (int) Math.round(x1 + (x2 - x1) * t);
        int y = (int) Math.round(y1 + (y2 - y1) * t);
        return new Point2D_I32(x, y);
    }

    private static void addSegment(List<Segment> segments, Point2D_I32 a, Point2D_I32 b) {
        if (!samePoint(a, b)) {
            segments.add(new Segment(a, b));
        }
    }

    private static List<VectorGeometry> chainSegments(List<Segment> segments, int minLength) {
        List<VectorGeometry> out = new ArrayList<>();
        boolean[] used = new boolean[segments.size()];
        for (int i = 0; i < segments.size(); i++) {
            if (used[i]) {
                continue;
            }
            used[i] = true;
            List<Point2D_I32> chain = new ArrayList<>();
            chain.add(copy(segments.get(i).a()));
            chain.add(copy(segments.get(i).b()));

            boolean extended;
            do {
                extended = false;
                for (int j = 0; j < segments.size(); j++) {
                    if (used[j]) {
                        continue;
                    }
                    Segment candidate = segments.get(j);
                    Point2D_I32 first = chain.get(0);
                    Point2D_I32 last = chain.get(chain.size() - 1);
                    if (samePoint(candidate.a(), last)) {
                        appendIfDifferent(chain, candidate.b());
                        used[j] = true;
                        extended = true;
                    } else if (samePoint(candidate.b(), last)) {
                        appendIfDifferent(chain, candidate.a());
                        used[j] = true;
                        extended = true;
                    } else if (samePoint(candidate.b(), first)) {
                        prependIfDifferent(chain, candidate.a());
                        used[j] = true;
                        extended = true;
                    } else if (samePoint(candidate.a(), first)) {
                        prependIfDifferent(chain, candidate.b());
                        used[j] = true;
                        extended = true;
                    }
                }
            } while (extended);

            if (chain.size() >= minLength) {
                out.add(new PolylineGeometry(chain));
            }
        }
        return out;
    }

    private static void appendIfDifferent(List<Point2D_I32> chain, Point2D_I32 point) {
        if (!samePoint(chain.get(chain.size() - 1), point)) {
            chain.add(copy(point));
        }
    }

    private static void prependIfDifferent(List<Point2D_I32> chain, Point2D_I32 point) {
        if (!samePoint(chain.get(0), point)) {
            chain.add(0, copy(point));
        }
    }

    private static boolean samePoint(Point2D_I32 a, Point2D_I32 b) {
        return a.x == b.x && a.y == b.y;
    }

    private static Point2D_I32 copy(Point2D_I32 p) {
        return new Point2D_I32(p.x, p.y);
    }

    private static double[][] grayscale(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        double[][] gray = new double[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color c = new Color(image.getRGB(x, y), true);
                double alpha = c.getAlpha() / 255.0;
                double luma = (0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue()) / 255.0;
                gray[y][x] = 1.0 - alpha + alpha * luma;
            }
        }
        return gray;
    }

    private static double[][] boxBlur(double[][] source, int radius) {
        if (radius <= 0) {
            return source;
        }
        int height = source.length;
        int width = source[0].length;
        double[][] blurred = new double[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double sum = 0.0;
                int count = 0;
                for (int yy = Math.max(0, y - radius); yy <= Math.min(height - 1, y + radius); yy++) {
                    for (int xx = Math.max(0, x - radius); xx <= Math.min(width - 1, x + radius); xx++) {
                        sum += source[yy][xx];
                        count++;
                    }
                }
                blurred[y][x] = sum / count;
            }
        }
        return blurred;
    }

    private static double toneRange(double[][] gray) {
        double min = 1.0;
        double max = 0.0;
        for (double[] row : gray) {
            for (double value : row) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        return max - min;
    }
}
