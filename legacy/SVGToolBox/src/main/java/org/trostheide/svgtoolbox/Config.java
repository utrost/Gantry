package org.trostheide.svgtoolbox;

import java.awt.Color;
import java.util.List;
import java.util.Map;

public record Config(
        String inputPath,
        String outputPath,
        float strokeWidth,
        List<Color> palette,
        boolean enableHatching,
        HatchStyle globalStyle,
        Map<String, HatchStyle> overrides,
        Map<String, Float> strokeWidthOverrides,
        List<String> hiddenLayers,
        List<Color> noHatchColors,
        double minHatchArea,
        double simplifyTolerance,
        String hatchPattern,
        double hatchAngle,
        double hatchGap,
        double rotationDegrees,
        boolean printStats,
        java.awt.geom.Rectangle2D cropBounds,
        boolean optimizePaths,
        boolean linesimplify,
        double linesimplifyTolerance,
        boolean linemerge,
        double linemergeTolerance,
        boolean linesort,
        boolean linesortTwoOpt,
        boolean reloop) {

    public static class Builder {
        private String inputPath;
        private String outputPath;
        private float strokeWidth = 0f;
        private List<Color> palette = List.of();
        private boolean enableHatching = false;
        private HatchStyle globalStyle = new HatchStyle(45.0, 5.0, "linear");
        private Map<String, HatchStyle> overrides = Map.of();
        private Map<String, Float> strokeWidthOverrides = Map.of();
        private List<String> hiddenLayers = List.of();
        private List<Color> noHatchColors = List.of();
        private double minHatchArea = 100.0;
        private double simplifyTolerance = 0.0;
        private String hatchPattern = "linear";
        private double hatchAngle = 45.0;
        private double hatchGap = 5.0;
        private double rotationDegrees = 0.0;
        private boolean printStats = false;
        private java.awt.geom.Rectangle2D cropBounds = null;
        private boolean optimizePaths = false;
        private boolean linesimplify = false;
        private double linesimplifyTolerance = 0.378;
        private boolean linemerge = false;
        private double linemergeTolerance = 1.89;
        private boolean linesort = false;
        private boolean linesortTwoOpt = false;
        private boolean reloop = false;

        public Builder inputPath(String inputPath) {
            this.inputPath = inputPath;
            return this;
        }

        public Builder outputPath(String outputPath) {
            this.outputPath = outputPath;
            return this;
        }

        public Builder strokeWidth(float strokeWidth) {
            this.strokeWidth = strokeWidth;
            return this;
        }

        public Builder palette(List<Color> palette) {
            this.palette = palette != null ? palette : List.of();
            return this;
        }

        public Builder enableHatching(boolean enableHatching) {
            this.enableHatching = enableHatching;
            return this;
        }

        public Builder globalStyle(HatchStyle globalStyle) {
            this.globalStyle = globalStyle != null ? globalStyle : new HatchStyle(45.0, 5.0, "linear");
            return this;
        }

        public Builder overrides(Map<String, HatchStyle> overrides) {
            this.overrides = overrides != null ? overrides : Map.of();
            return this;
        }

        public Builder strokeWidthOverrides(Map<String, Float> strokeWidthOverrides) {
            this.strokeWidthOverrides = strokeWidthOverrides != null ? strokeWidthOverrides : Map.of();
            return this;
        }

        public Builder hiddenLayers(List<String> hiddenLayers) {
            this.hiddenLayers = hiddenLayers != null ? hiddenLayers : List.of();
            return this;
        }

        public Builder noHatchColors(List<Color> noHatchColors) {
            this.noHatchColors = noHatchColors != null ? noHatchColors : List.of();
            return this;
        }

        public Builder minHatchArea(double minHatchArea) {
            this.minHatchArea = minHatchArea;
            return this;
        }

        public Builder simplifyTolerance(double simplifyTolerance) {
            this.simplifyTolerance = simplifyTolerance;
            return this;
        }

        public Builder hatchPattern(String hatchPattern) {
            this.hatchPattern = hatchPattern != null ? hatchPattern : "linear";
            return this;
        }

        public Builder hatchAngle(double hatchAngle) {
            this.hatchAngle = hatchAngle;
            return this;
        }

        public Builder hatchGap(double hatchGap) {
            this.hatchGap = hatchGap;
            return this;
        }

        public Builder rotationDegrees(double rotationDegrees) {
            this.rotationDegrees = rotationDegrees;
            return this;
        }

        public Builder printStats(boolean printStats) {
            this.printStats = printStats;
            return this;
        }

        public Builder cropBounds(java.awt.geom.Rectangle2D cropBounds) {
            this.cropBounds = cropBounds;
            return this;
        }

        public Builder optimizePaths(boolean optimizePaths) {
            this.optimizePaths = optimizePaths;
            return this;
        }

        public Builder linesimplify(boolean linesimplify) {
            this.linesimplify = linesimplify;
            return this;
        }

        public Builder linesimplifyTolerance(double linesimplifyTolerance) {
            this.linesimplifyTolerance = linesimplifyTolerance;
            return this;
        }

        public Builder linemerge(boolean linemerge) {
            this.linemerge = linemerge;
            return this;
        }

        public Builder linemergeTolerance(double linemergeTolerance) {
            this.linemergeTolerance = linemergeTolerance;
            return this;
        }

        public Builder linesort(boolean linesort) {
            this.linesort = linesort;
            return this;
        }

        public Builder linesortTwoOpt(boolean linesortTwoOpt) {
            this.linesortTwoOpt = linesortTwoOpt;
            return this;
        }

        public Builder reloop(boolean reloop) {
            this.reloop = reloop;
            return this;
        }

        public Config build() {
            return new Config(
                    inputPath, outputPath, strokeWidth, palette, enableHatching,
                    globalStyle, overrides, strokeWidthOverrides, hiddenLayers, noHatchColors, minHatchArea, simplifyTolerance,
                    hatchPattern, hatchAngle, hatchGap, rotationDegrees, printStats, cropBounds, optimizePaths,
                    linesimplify, linesimplifyTolerance, linemerge, linemergeTolerance, linesort, linesortTwoOpt,
                    reloop
            );
        }
    }
}