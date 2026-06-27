package org.trostheide.gantry.vectorize;

import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.image.GrayU8;

import java.awt.image.BufferedImage;

/**
 * Computes optimal Canny edge detection thresholds from image histogram analysis.
 * Uses Otsu's method to find the optimal binarization threshold, then derives
 * Canny low/high thresholds from it. This produces much better results than
 * the simple sigma-based median method, especially for bright or high-contrast images.
 */
public class AutoCannyThresholds {

    private final double low;
    private final double high;

    public AutoCannyThresholds(double low, double high) {
        this.low = low;
        this.high = high;
    }

    public double getLow() {
        return low;
    }

    public double getHigh() {
        return high;
    }

    /**
     * Computes optimal Canny thresholds from the image using Otsu's method.
     *
     * The approach:
     * 1. Compute the grayscale histogram
     * 2. Find the Otsu threshold (maximises inter-class variance)
     * 3. Normalize to [0,1] and derive:
     *    low  = 0.5 × otsuNormalized
     *    high = 1.0 × otsuNormalized
     *
     * This typically produces thresholds in the 0.05–0.40 range, which
     * detects edges effectively across a wide variety of images.
     */
    public static AutoCannyThresholds compute(BufferedImage image) {
        GrayU8 gray = ConvertBufferedImage.convertFrom(image, (GrayU8) null);

        // Build histogram
        int[] histogram = new int[256];
        for (int y = 0; y < gray.height; y++) {
            for (int x = 0; x < gray.width; x++) {
                histogram[gray.get(x, y)]++;
            }
        }

        int totalPixels = gray.width * gray.height;

        // --- Otsu's method ---
        // Find the threshold that maximises inter-class variance
        double sumAll = 0;
        for (int i = 0; i < 256; i++) {
            sumAll += (double) i * histogram[i];
        }

        double sumBackground = 0;
        int weightBackground = 0;
        double maxVariance = 0;
        int otsuThreshold = 128; // fallback

        for (int t = 0; t < 256; t++) {
            weightBackground += histogram[t];
            if (weightBackground == 0) continue;

            int weightForeground = totalPixels - weightBackground;
            if (weightForeground == 0) break;

            sumBackground += (double) t * histogram[t];
            double meanBackground = sumBackground / weightBackground;
            double meanForeground = (sumAll - sumBackground) / weightForeground;

            double meanDiff = meanBackground - meanForeground;
            double variance = (double) weightBackground * weightForeground * meanDiff * meanDiff;

            if (variance > maxVariance) {
                maxVariance = variance;
                otsuThreshold = t;
            }
        }

        // Derive Canny thresholds from Otsu
        double otsuNormalized = otsuThreshold / 255.0;
        double low = Math.max(0.01, 0.5 * otsuNormalized);
        double high = Math.min(0.99, 1.0 * otsuNormalized);

        // Ensure minimum separation
        if (high - low < 0.02) {
            high = Math.min(0.99, low + 0.05);
        }

        System.out.printf("Auto Canny (Otsu): threshold=%d, low=%.3f, high=%.3f%n",
                otsuThreshold, low, high);

        return new AutoCannyThresholds(low, high);
    }
}
