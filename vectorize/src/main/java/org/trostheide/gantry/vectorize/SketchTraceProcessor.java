package org.trostheide.gantry.vectorize;

import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.struct.image.GrayU8;
import georegression.struct.point.Point2D_I32;
import org.trostheide.gantry.vectorize.algorithms.SkeletonTracer;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Adaptive threshold sketch/blueprint tracer for scanned pencil or ink drawings. */
public final class SketchTraceProcessor {
    private SketchTraceProcessor() {
    }

    public record Options(int window, double offset, int minLength, boolean skeleton) {
        public static Options defaults() {
            return new Options(15, 0.08, 8, true);
        }
    }

    public static List<VectorGeometry> process(BufferedImage image, Options options) {
        if (image == null || image.getWidth() < 2 || image.getHeight() < 2) {
            return List.of();
        }
        Options safe = sanitize(options);
        double[][] gray = grayscale(image);
        if (toneRange(gray) < safe.offset() * 0.5) {
            return List.of();
        }
        GrayU8 binary = adaptiveThreshold(gray, safe.window(), safe.offset());
        GrayU8 source = binary;
        if (safe.skeleton()) {
            GrayU8 skeleton = new GrayU8(binary.width, binary.height);
            BinaryImageOps.thin(binary, -1, skeleton);
            source = skeleton;
        }
        return traceBinary(source, safe.minLength());
    }

    private static Options sanitize(Options options) {
        Options source = options == null ? Options.defaults() : options;
        int window = Math.max(3, Math.min(99, source.window()));
        if (window % 2 == 0) {
            window++;
        }
        return new Options(
                window,
                Math.max(0.0, Math.min(0.5, source.offset())),
                Math.max(2, source.minLength()),
                source.skeleton());
    }

    private static GrayU8 adaptiveThreshold(double[][] gray, int window, double offset) {
        int height = gray.length;
        int width = gray[0].length;
        double[][] integral = integral(gray);
        GrayU8 binary = new GrayU8(width, height);
        int radius = window / 2;
        for (int y = 0; y < height; y++) {
            int y0 = Math.max(0, y - radius);
            int y1 = Math.min(height - 1, y + radius);
            for (int x = 0; x < width; x++) {
                int x0 = Math.max(0, x - radius);
                int x1 = Math.min(width - 1, x + radius);
                double localMean = sum(integral, x0, y0, x1, y1) / ((x1 - x0 + 1) * (y1 - y0 + 1));
                binary.set(x, y, gray[y][x] < localMean - offset ? 1 : 0);
            }
        }
        return binary;
    }

    private static List<VectorGeometry> traceBinary(GrayU8 binary, int minLength) {
        SkeletonTracer tracer = new SkeletonTracer();
        List<List<Point2D_I32>> rawPaths = tracer.trace(binary);
        List<VectorGeometry> result = new ArrayList<>();
        for (List<Point2D_I32> path : rawPaths) {
            if (path.size() >= minLength && !isBorderArtifact(path, binary.width, binary.height)) {
                result.add(new PolylineGeometry(copy(path)));
            }
        }
        return result;
    }

    private static boolean isBorderArtifact(List<Point2D_I32> path, int width, int height) {
        int border = Math.max(2, Math.round(Math.min(width, height) * 0.01f));
        int borderPoints = 0;
        for (Point2D_I32 p : path) {
            if (p.x <= border || p.y <= border || p.x >= width - 1 - border || p.y >= height - 1 - border) {
                borderPoints++;
            }
        }
        return borderPoints >= Math.max(2, path.size() * 0.80);
    }

    private static List<Point2D_I32> copy(List<Point2D_I32> path) {
        List<Point2D_I32> out = new ArrayList<>(path.size());
        for (Point2D_I32 p : path) {
            out.add(new Point2D_I32(p.x, p.y));
        }
        return out;
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

    private static double[][] integral(double[][] gray) {
        int height = gray.length;
        int width = gray[0].length;
        double[][] out = new double[height + 1][width + 1];
        for (int y = 1; y <= height; y++) {
            double rowSum = 0.0;
            for (int x = 1; x <= width; x++) {
                rowSum += gray[y - 1][x - 1];
                out[y][x] = out[y - 1][x] + rowSum;
            }
        }
        return out;
    }

    private static double sum(double[][] integral, int x0, int y0, int x1, int y1) {
        int ax = x0;
        int ay = y0;
        int bx = x1 + 1;
        int by = y1 + 1;
        return integral[by][bx] - integral[ay][bx] - integral[by][ax] + integral[ay][ax];
    }
}
