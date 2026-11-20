package org.trostheide.gantry.vectorize;

import georegression.struct.point.Point2D_I32;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Raster-to-polyline image style that turns tonal image areas into modest horizontal squiggles
 * plus connected sketch contours, without a positional or portrait-specific foreground prior.
 * Suppression attenuates locally flat dark areas, including subject interiors;
 * zero disables it. This is a general stylization, not semantic background removal.
 * It is intentionally deterministic so the live preview and final
 * import agree for the same image/options.
 */
public final class TonalSquiggleProcessor {
    private TonalSquiggleProcessor() {
    }

    public record Options(
            double rowSpacing,
            double squiggleAmplitude,
            double detailStrength,
            double toneStrength,
            double backgroundSuppression,
            double density) {
        public Options(double rowSpacing, double squiggleAmplitude, double detailStrength,
                       double toneStrength, double backgroundSuppression) {
            this(rowSpacing, squiggleAmplitude, detailStrength, toneStrength, backgroundSuppression, 1.0);
        }

        public static Options defaults() {
            return new Options(8.0, 3.5, 0.60, 0.72, 0.85, 1.0);
        }

        public Options withDensity(double nextDensity) {
            return new Options(rowSpacing, squiggleAmplitude, detailStrength,
                    toneStrength, backgroundSuppression, nextDensity);
        }
    }

    public static List<VectorGeometry> process(BufferedImage image, Options options) {
        if (image == null) {
            return List.of();
        }
        Options safe = options == null ? Options.defaults() : options;
        int width = image.getWidth();
        int height = image.getHeight();
        if (width < 2 || height < 2) {
            return List.of();
        }

        double[][] gray = grayscale(image);
        double[][] local = boxBlur(gray, Math.max(2, Math.min(width, height) / 30));
        double[][] edges = edgeMagnitude(gray);
        List<VectorGeometry> geometry = new ArrayList<>();

        double density = Math.max(0.25, Math.min(3.0, safe.density()));
        int spacing = Math.max(3, (int) Math.round(safe.rowSpacing() / density));
        int xStep = Math.max(2, (int) Math.round(4.0 / density));
        double amp = Math.max(0.0, safe.squiggleAmplitude());
        double toneStrength = clamp01(safe.toneStrength());
        double detailStrength = clamp01(safe.detailStrength());
        double backgroundSuppression = clamp01(safe.backgroundSuppression());
        double drawThreshold = 0.12 + 0.08 * backgroundSuppression;

        for (int y = spacing; y < height - spacing; y += spacing) {
            List<Point2D_I32> current = new ArrayList<>();
            for (int x = spacing; x < width - spacing; x += xStep) {
                double darkness = 1.0 - gray[y][x];
                double localContrast = Math.abs(gray[y][x] - local[y][x]);
                double detail = Math.max(edges[y][x], localContrast * 2.0);
                double desire = toneStrength * darkness * 0.65 + detailStrength * detail * 1.35;
                // Suppress locally flat dark areas by their image content, never their position.
                // Zero disables this suppression, including at the image edges.
                double flatDark = clamp01((darkness - 0.65) / 0.20)
                        * (1.0 - clamp01(detail / 0.08));
                desire *= 1.0 - backgroundSuppression * flatDark;
                if (desire > drawThreshold) {
                    double wiggle = Math.sin(x * 0.075 + y * 0.035) * amp * (0.35 + darkness);
                    current.add(new Point2D_I32(x, clamp((int) Math.round(y + wiggle), 0, height - 1)));
                } else if (current.size() >= 2) {
                    geometry.add(new PolylineGeometry(current));
                    current = new ArrayList<>();
                } else {
                    current.clear();
                }
            }
            if (current.size() >= 2) {
                geometry.add(new PolylineGeometry(current));
            }
        }

        // Reuse the existing adaptive sketch tracer for structural contours. Short
        // horizontal edge dashes obscure features and add lifts without following them.
        if (detailStrength > 0) {
            int minimumContourLength = Math.max(6, (int) Math.round(12 - 10 * detailStrength));
            geometry.addAll(SketchTraceProcessor.process(image,
                    new SketchTraceProcessor.Options(21, 0.04, minimumContourLength, true)));
        }
        return geometry;
    }

    private static double[][] grayscale(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        double[][] gray = new double[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                gray[y][x] = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
            }
        }
        return gray;
    }

    private static double[][] edgeMagnitude(double[][] gray) {
        int height = gray.length;
        int width = gray[0].length;
        double[][] edges = new double[height][width];
        double max = 0.0;
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                double gx = (gray[y - 1][x + 1] + 2 * gray[y][x + 1] + gray[y + 1][x + 1])
                        - (gray[y - 1][x - 1] + 2 * gray[y][x - 1] + gray[y + 1][x - 1]);
                double gy = (gray[y + 1][x - 1] + 2 * gray[y + 1][x] + gray[y + 1][x + 1])
                        - (gray[y - 1][x - 1] + 2 * gray[y - 1][x] + gray[y - 1][x + 1]);
                double value = Math.hypot(gx, gy);
                edges[y][x] = value;
                max = Math.max(max, value);
            }
        }
        if (max > 0.0) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    edges[y][x] /= max;
                }
            }
        }
        return edges;
    }

    private static double[][] boxBlur(double[][] source, int radius) {
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

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
