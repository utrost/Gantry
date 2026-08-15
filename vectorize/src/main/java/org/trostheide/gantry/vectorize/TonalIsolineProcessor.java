package org.trostheide.gantry.vectorize;

import georegression.struct.point.Point2D_I32;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Raster-to-polyline image style that traces brightness-level contours, similar
 * to topographic map rings. It is deterministic and emits simple polylines so
 * the output can enter the normal plotter pipeline.
 */
public final class TonalIsolineProcessor {
    private TonalIsolineProcessor() {
    }

    public record Options(int levels, double smoothing, int minLength, double threshold) {
        public static Options defaults() {
            return new Options(6, 3.0, 8, 0.06);
        }
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
            boolean[][] active = new boolean[height][width];
            for (int y = 1; y < height - 1; y++) {
                for (int x = 1; x < width - 1; x++) {
                    double center = gray[y][x];
                    if (center > iso) {
                        continue;
                    }
                    double maxNeighbour = Math.max(
                            Math.max(gray[y - 1][x], gray[y + 1][x]),
                            Math.max(gray[y][x - 1], gray[y][x + 1]));
                    active[y][x] = maxNeighbour >= iso;
                }
            }
            traceLevel(active, safe.minLength(), out);
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

    private static void traceLevel(boolean[][] active, int minLength, List<VectorGeometry> out) {
        int height = active.length;
        int width = active[0].length;
        boolean[][] visited = new boolean[height][width];
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (!active[y][x] || visited[y][x]) {
                    continue;
                }
                List<Point2D_I32> component = new ArrayList<>();
                collect(active, visited, x, y, component);
                if (component.size() >= minLength) {
                    List<Point2D_I32> ordered = orderByAngle(component);
                    if (ordered.size() >= minLength) {
                        Point2D_I32 first = ordered.get(0);
                        Point2D_I32 last = ordered.get(ordered.size() - 1);
                        if (Math.hypot(first.x - last.x, first.y - last.y) > 0.0) {
                            ordered = new ArrayList<>(ordered);
                            ordered.add(new Point2D_I32(first.x, first.y));
                        }
                        out.add(new PolylineGeometry(ordered));
                    }
                }
            }
        }
    }

    private static void collect(boolean[][] active, boolean[][] visited, int startX, int startY,
                                List<Point2D_I32> component) {
        int height = active.length;
        int width = active[0].length;
        int[] stackX = new int[width * height];
        int[] stackY = new int[width * height];
        int top = 0;
        stackX[top] = startX;
        stackY[top] = startY;
        top++;
        visited[startY][startX] = true;
        while (top > 0) {
            top--;
            int x = stackX[top];
            int y = stackY[top];
            component.add(new Point2D_I32(x, y));
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    int nx = x + dx;
                    int ny = y + dy;
                    if (nx <= 0 || nx >= width - 1 || ny <= 0 || ny >= height - 1) {
                        continue;
                    }
                    if (active[ny][nx] && !visited[ny][nx]) {
                        visited[ny][nx] = true;
                        stackX[top] = nx;
                        stackY[top] = ny;
                        top++;
                    }
                }
            }
        }
    }

    private static List<Point2D_I32> orderByAngle(List<Point2D_I32> points) {
        double cx = 0.0;
        double cy = 0.0;
        for (Point2D_I32 p : points) {
            cx += p.x;
            cy += p.y;
        }
        cx /= points.size();
        cy /= points.size();
        final double centerX = cx;
        final double centerY = cy;
        List<Point2D_I32> ordered = new ArrayList<>(points);
        ordered.sort((a, b) -> {
            double aa = Math.atan2(a.y - centerY, a.x - centerX);
            double bb = Math.atan2(b.y - centerY, b.x - centerX);
            int cmp = Double.compare(aa, bb);
            if (cmp != 0) {
                return cmp;
            }
            double da = Math.hypot(a.x - centerX, a.y - centerY);
            double db = Math.hypot(b.x - centerX, b.y - centerY);
            return Double.compare(da, db);
        });
        return ordered;
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
