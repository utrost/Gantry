package org.trostheide.svgtoolbox.processors;

import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.Processor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.awt.Color;

public class PaletteProcessor implements Processor {

    @Override
    public void process(Document doc, Config config) {
        if (config.palette() == null || config.palette().isEmpty()) return;

        NodeList elements = doc.getElementsByTagName("*");

        for (int i = 0; i < elements.getLength(); i++) {
            Element el = (Element) elements.item(i);

            if (el.hasAttribute("stroke")) {
                quantizeAttribute(el, "stroke", config);
            }
            if (el.hasAttribute("fill")) {
                quantizeAttribute(el, "fill", config);
            }
        }
        System.out.println("Quantized colors to palette size: " + config.palette().size());
    }

    private void quantizeAttribute(Element el, String attr, Config config) {
        String val = el.getAttribute(attr);
        if ("none".equalsIgnoreCase(val) || val.isEmpty()) return;

        try {
            Color original = Color.decode(val);
            Color nearest = findNearest(original, config);
            String hex = String.format("#%02x%02x%02x", nearest.getRed(), nearest.getGreen(), nearest.getBlue());
            el.setAttribute(attr, hex);
        } catch (NumberFormatException ignored) {
            // Handle named colors or complex paint servers if necessary
        }
    }

    private Color findNearest(Color target, Config config) {
        Color nearest = null;
        double minDistance = Double.MAX_VALUE;
        double[] targetLab = rgbToLab(target);

        for (Color p : config.palette()) {
            double[] pLab = rgbToLab(p);
            double dist = Math.pow(targetLab[0] - pLab[0], 2) +
                    Math.pow(targetLab[1] - pLab[1], 2) +
                    Math.pow(targetLab[2] - pLab[2], 2);
            if (dist < minDistance) {
                minDistance = dist;
                nearest = p;
            }
        }
        return nearest;
    }

    /**
     * Convert RGB to CIELAB color space for perceptually uniform distance calculation.
     * Uses D65 illuminant reference white.
     */
    private double[] rgbToLab(Color c) {
        // Step 1: RGB to linear sRGB
        double r = pivotSrgb(c.getRed() / 255.0);
        double g = pivotSrgb(c.getGreen() / 255.0);
        double b = pivotSrgb(c.getBlue() / 255.0);

        // Step 2: Linear sRGB to XYZ (D65)
        double x = r * 0.4124564 + g * 0.3575761 + b * 0.1804375;
        double y = r * 0.2126729 + g * 0.7151522 + b * 0.0721750;
        double z = r * 0.0193339 + g * 0.1191920 + b * 0.9503041;

        // Step 3: XYZ to Lab (D65 reference white)
        x /= 0.95047;
        y /= 1.00000;
        z /= 1.08883;

        x = pivotXyz(x);
        y = pivotXyz(y);
        z = pivotXyz(z);

        double L = 116.0 * y - 16.0;
        double a = 500.0 * (x - y);
        double bLab = 200.0 * (y - z);

        return new double[]{L, a, bLab};
    }

    private double pivotSrgb(double v) {
        return v > 0.04045 ? Math.pow((v + 0.055) / 1.055, 2.4) : v / 12.92;
    }

    private double pivotXyz(double v) {
        return v > 0.008856 ? Math.cbrt(v) : (7.787 * v) + (16.0 / 116.0);
    }
}