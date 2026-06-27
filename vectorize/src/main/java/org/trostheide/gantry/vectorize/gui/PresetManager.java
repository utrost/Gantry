package org.trostheide.gantry.vectorize.gui;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

class PresetManager {

    static Map<String, Object> getPresetValues(String presetName) {
        Map<String, Object> v = new HashMap<>();
        switch (presetName) {
            case "Line Art" -> {
                v.put("strategy", "dp");
                v.put("tolerance", 15);
                v.put("detail", 10);
                v.put("blur", 10);
                v.put("speckle", 10);
                v.put("cannyLow", 10);
                v.put("cannyHigh", 80);
                v.put("smoothCurves", true);
                v.put("strokeColor", "black");
                v.put("strokeWidth", 10);
            }
            case "Photo - Detailed" -> {
                v.put("strategy", "bezier2");
                v.put("colors", 6);
                v.put("speckle", 4);
            }
            case "Photo - Simplified" -> {
                v.put("strategy", "bezier2");
                v.put("colors", 4);
                v.put("speckle", 12);
            }
            case "Logo" -> {
                v.put("strategy", "bezier");
                v.put("colors", 3);
                v.put("detail", 5);
                v.put("strokeColor", "black");
                v.put("strokeWidth", 15);
            }
            case "Sketch" -> {
                v.put("strategy", "dp");
                v.put("tolerance", 30);
                v.put("detail", 5);
                v.put("blur", 20);
                v.put("speckle", 15);
                v.put("cannyLow", 30);
                v.put("cannyHigh", 150);
                v.put("smoothCurves", true);
                v.put("strokeColor", "black");
                v.put("strokeWidth", 15);
            }
            case "Paint by Numbers" -> {
                Color[] defaultPalette = new Color[6];
                for (int i = 0; i < 6; i++) {
                    defaultPalette[i] = Color.getHSBColor(i / 6f, 0.8f, 0.9f);
                }
                v.put("strategy", "pbn");
                v.put("pbnPalette", defaultPalette);
                v.put("pbnMinArea", 100);
                v.put("pbnFontSize", 14);
                v.put("tolerance", 20);
                v.put("pbnShowNumbers", true);
                v.put("pbnShowLegend", true);
            }
        }
        return v.isEmpty() ? null : v;
    }
}
