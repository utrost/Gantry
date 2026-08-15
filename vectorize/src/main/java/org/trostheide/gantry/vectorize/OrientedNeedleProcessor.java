package org.trostheide.gantry.vectorize;

import georegression.struct.point.Point2D_I32;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Raster-to-polyline image style that samples a regular grid and draws short
 * dashes oriented along the local tonal gradient. This is useful for portraits
 * and textured sources where direction cues matter more than filled regions.
 */
public final class OrientedNeedleProcessor {
    private OrientedNeedleProcessor() {
    }

    public record Options(
            double spacing,
            double length,
            double toneThreshold,
            double gradientThreshold,
            double toneStrength) {
        public static Options defaults() {
            return new Options(6.0, 10.0, 0.12, 0.04, 1.0);
        }
    }

    public static List<VectorGeometry> process(BufferedImage image, Options options) {
        Options safe = sanitize(options);
        int spacing = Math.max(2, (int) Math.round(safe.spacing()));
        double baseLength = Math.max(1.0, safe.length());
        double toneThreshold = clamp01(safe.toneThreshold());
        double gradientThreshold = Math.max(0.0, safe.gradientThreshold());
        double toneStrength = Math.max(0.0, safe.toneStrength());

        int width = image.getWidth();
        int height = image.getHeight();
        double[][] brightness = brightness(image);
        List<VectorGeometry> out = new ArrayList<>();

        for (int y = spacing / 2; y < height; y += spacing) {
            for (int x = spacing / 2; x < width; x += spacing) {
                int radius = Math.max(1, spacing / 2);
                double gx = sample(brightness, x + radius, y) - sample(brightness, x - radius, y);
                double gy = sample(brightness, x, y + radius) - sample(brightness, x, y - radius);
                double gradient = Math.hypot(gx, gy) / 2.0;
                double localDarkness = localMaxDarkness(brightness, x, y, radius);

                if (gradient < gradientThreshold || localDarkness < toneThreshold) {
                    continue;
                }

                double strength = clamp01((gradient / Math.max(gradientThreshold, 0.001)) * 0.5
                        + localDarkness * toneStrength * 0.5);
                double len = Math.max(2.0, baseLength * strength);
                double norm = Math.hypot(gx, gy);
                if (norm == 0.0) {
                    continue;
                }

                double ux = gx / norm;
                double uy = gy / norm;
                int x1 = clampInt((int) Math.round(x - ux * len / 2.0), 0, width - 1);
                int y1 = clampInt((int) Math.round(y - uy * len / 2.0), 0, height - 1);
                int x2 = clampInt((int) Math.round(x + ux * len / 2.0), 0, width - 1);
                int y2 = clampInt((int) Math.round(y + uy * len / 2.0), 0, height - 1);
                if (x1 == x2 && y1 == y2) {
                    continue;
                }
                out.add(new PolylineGeometry(List.of(new Point2D_I32(x1, y1), new Point2D_I32(x2, y2))));
            }
        }
        return out;
    }

    private static Options sanitize(Options options) {
        Options source = options == null ? Options.defaults() : options;
        return new Options(
                Math.max(2.0, source.spacing()),
                Math.max(1.0, source.length()),
                clamp01(source.toneThreshold()),
                Math.max(0.0, source.gradientThreshold()),
                Math.max(0.0, source.toneStrength()));
    }

    private static double[][] brightness(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        double[][] values = new double[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color c = new Color(image.getRGB(x, y), true);
                double alpha = c.getAlpha() / 255.0;
                double luma = (0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue()) / 255.0;
                values[y][x] = 1.0 - alpha + alpha * luma;
            }
        }
        return values;
    }

    private static double localMaxDarkness(double[][] brightness, int cx, int cy, int radius) {
        double max = 0.0;
        int height = brightness.length;
        int width = brightness[0].length;
        for (int y = Math.max(0, cy - radius); y <= Math.min(height - 1, cy + radius); y++) {
            for (int x = Math.max(0, cx - radius); x <= Math.min(width - 1, cx + radius); x++) {
                max = Math.max(max, 1.0 - brightness[y][x]);
            }
        }
        return max;
    }

    private static double sample(double[][] brightness, int x, int y) {
        int yy = clampInt(y, 0, brightness.length - 1);
        int xx = clampInt(x, 0, brightness[0].length - 1);
        return brightness[yy][xx];
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
