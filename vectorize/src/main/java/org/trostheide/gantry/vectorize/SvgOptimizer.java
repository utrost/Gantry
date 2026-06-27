package org.trostheide.gantry.vectorize;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Post-processes SVG files to reduce file size without visual degradation.
 *
 * Optimizations applied:
 * 1. Round coordinates to 1 decimal place
 * 2. Remove trailing zeros after decimal point
 * 3. Remove unnecessary whitespace in path data
 */
public class SvgOptimizer {

    // Pattern to match decimal numbers with excessive precision
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("(\\d+\\.\\d{2,})");

    /**
     * Optimizes an SVG file in place.
     */
    public static void optimize(String svgPath) throws IOException {
        Path path = Path.of(svgPath);
        String content = Files.readString(path);
        String optimized = optimizeContent(content);
        Files.writeString(path, optimized);

        long originalSize = content.length();
        long optimizedSize = optimized.length();
        double savings = (1.0 - (double) optimizedSize / originalSize) * 100;
        System.out.printf("SVG optimized: %d → %d bytes (%.1f%% reduction)%n",
                originalSize, optimizedSize, savings);
    }

    /**
     * Optimizes SVG content string.
     */
    public static String optimizeContent(String svgContent) {
        // 1. Round decimal numbers to 1 decimal place
        Matcher matcher = DECIMAL_PATTERN.matcher(svgContent);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            double value = Double.parseDouble(matcher.group(1));
            String rounded = formatNumber(value);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(rounded));
        }
        matcher.appendTail(sb);
        String result = sb.toString();

        // 2. Clean up redundant spaces in point lists (e.g., "10,20  30,40" → "10,20 30,40")
        result = result.replaceAll(" {2,}", " ");

        // 3. Remove spaces around commas in coordinates
        result = result.replaceAll(" *, *", ",");

        return result;
    }

    /**
     * Formats a number, removing unnecessary trailing zeros.
     * e.g., 10.0 → "10", 10.5 → "10.5", 10.50 → "10.5"
     */
    private static String formatNumber(double value) {
        double rounded = Math.round(value * 10.0) / 10.0;
        if (rounded == (long) rounded) {
            return String.valueOf((long) rounded);
        }
        return String.format("%.1f", rounded);
    }
}
