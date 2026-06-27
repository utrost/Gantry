package org.trostheide.gantry.vectorize;

import georegression.struct.point.Point2D_I32;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class PaintByNumbersProcessor {

    public static class PbnRegion {
        public final int id;
        public final int colorIndex;
        public int pixelCount;
        public int minX, minY, maxX, maxY;
        public List<Point2D_I32> boundary;
        public Point2D_I32 labelPosition;

        public PbnRegion(int id, int colorIndex) {
            this.id = id;
            this.colorIndex = colorIndex;
            this.pixelCount = 0;
            this.minX = Integer.MAX_VALUE;
            this.minY = Integer.MAX_VALUE;
            this.maxX = Integer.MIN_VALUE;
            this.maxY = Integer.MIN_VALUE;
        }

        public void addPixel(int x, int y) {
            pixelCount++;
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
        }
    }

    public static class PbnResult {
        public final List<PbnRegion> regions;
        public final Color[] palette;
        public final int width;
        public final int height;

        public PbnResult(List<PbnRegion> regions, Color[] palette, int width, int height) {
            this.regions = regions;
            this.palette = palette;
            this.width = width;
            this.height = height;
        }
    }

    public static PbnResult process(BufferedImage image, Color[] palette, int minArea, double tolerance) {
        if (palette == null || palette.length == 0) {
            palette = extractPalette(image, 6);
        }
        int w = image.getWidth();
        int h = image.getHeight();

        int[][] colorMap = quantizeToNearestColor(image, palette);

        int[][] regionMap = new int[h][w];
        List<PbnRegion> regions = labelConnectedComponents(colorMap, regionMap, w, h);

        mergeSmallRegions(colorMap, regionMap, regions, minArea, w, h);

        for (PbnRegion region : regions) {
            region.boundary = traceRegionContour(regionMap, region, w, h);
            if (tolerance > 0 && region.boundary.size() > 3) {
                region.boundary = douglasPeucker(region.boundary, tolerance);
            }
            region.labelPosition = computeLabelPosition(regionMap, region, w, h);
        }

        return new PbnResult(regions, palette, w, h);
    }

    public static void writeSvg(PbnResult result, String outputPath, int fontSize, boolean showNumbers, boolean showLegend) throws IOException {
        int w = result.width;
        int h = result.height;
        int legendHeight = showLegend ? 40 + ((result.palette.length + 9) / 10) * 30 : 0;
        int totalHeight = h + legendHeight;

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append(String.format("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %d %d\" width=\"%d\" height=\"%d\">\n",
                w, totalHeight, w, totalHeight));

        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>\n");

        sb.append("<g id=\"regions\">\n");
        for (int i = 0; i < result.regions.size(); i++) {
            PbnRegion region = result.regions.get(i);
            if (region.boundary == null || region.boundary.size() < 3) continue;

            StringBuilder points = new StringBuilder();
            for (Point2D_I32 p : region.boundary) {
                if (points.length() > 0) points.append(" ");
                points.append(p.x).append(",").append(p.y);
            }

            String fill = colorToHex(result.palette[region.colorIndex]);
            sb.append(String.format("  <polygon points=\"%s\" fill=\"%s\" fill-opacity=\"0.15\" stroke=\"#333\" stroke-width=\"0.5\"/>\n",
                    points, fill));
        }
        sb.append("</g>\n");

        if (showNumbers) {
            sb.append("<g id=\"labels\">\n");
            for (int i = 0; i < result.regions.size(); i++) {
                PbnRegion region = result.regions.get(i);
                if (region.labelPosition == null) continue;
                if (region.boundary == null || region.boundary.size() < 3) continue;

                int number = region.colorIndex + 1;
                sb.append(String.format("  <text x=\"%d\" y=\"%d\" text-anchor=\"middle\" dominant-baseline=\"central\" " +
                                "font-family=\"Arial,sans-serif\" font-size=\"%d\" fill=\"#333\">%d</text>\n",
                        region.labelPosition.x, region.labelPosition.y, fontSize, number));
            }
            sb.append("</g>\n");
        }

        if (showLegend) {
            int legendY = h + 10;
            sb.append("<g id=\"legend\">\n");
            int cols = 10;
            int swatchSize = 20;
            int colWidth = Math.max(60, w / cols);
            for (int i = 0; i < result.palette.length; i++) {
                int col = i % cols;
                int row = i / cols;
                int x = 10 + col * colWidth;
                int y = legendY + row * 30;
                String hex = colorToHex(result.palette[i]);
                sb.append(String.format("  <rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"%s\" stroke=\"#333\" stroke-width=\"0.5\"/>\n",
                        x, y, swatchSize, swatchSize, hex));
                sb.append(String.format("  <text x=\"%d\" y=\"%d\" font-family=\"Arial,sans-serif\" font-size=\"12\" " +
                                "dominant-baseline=\"central\" fill=\"#333\">%d</text>\n",
                        x + swatchSize + 4, y + swatchSize / 2, i + 1));
            }
            sb.append("</g>\n");
        }

        sb.append("</svg>\n");

        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write(sb.toString());
        }
    }

    // --- Color quantization using CIE LAB ΔE76 ---

    static int[][] quantizeToNearestColor(BufferedImage image, Color[] palette) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[][] colorMap = new int[h][w];

        double[][] paletteLab = new double[palette.length][];
        for (int i = 0; i < palette.length; i++) {
            paletteLab[i] = rgbToLab(palette[i].getRed(), palette[i].getGreen(), palette[i].getBlue());
        }

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                double[] lab = rgbToLab(r, g, b);

                int bestIdx = 0;
                double bestDist = Double.MAX_VALUE;
                for (int i = 0; i < paletteLab.length; i++) {
                    double dL = lab[0] - paletteLab[i][0];
                    double dA = lab[1] - paletteLab[i][1];
                    double dB = lab[2] - paletteLab[i][2];
                    double dist = dL * dL + dA * dA + dB * dB;
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestIdx = i;
                    }
                }
                colorMap[y][x] = bestIdx;
            }
        }
        return colorMap;
    }

    static double[] rgbToLab(int r, int g, int b) {
        double rr = r / 255.0, gg = g / 255.0, bb = b / 255.0;

        rr = rr > 0.04045 ? Math.pow((rr + 0.055) / 1.055, 2.4) : rr / 12.92;
        gg = gg > 0.04045 ? Math.pow((gg + 0.055) / 1.055, 2.4) : gg / 12.92;
        bb = bb > 0.04045 ? Math.pow((bb + 0.055) / 1.055, 2.4) : bb / 12.92;

        double x = (rr * 0.4124564 + gg * 0.3575761 + bb * 0.1804375) / 0.95047;
        double y = (rr * 0.2126729 + gg * 0.7151522 + bb * 0.0721750);
        double z = (rr * 0.0193339 + gg * 0.1191920 + bb * 0.9503041) / 1.08883;

        x = x > 0.008856 ? Math.cbrt(x) : (7.787 * x + 16.0 / 116.0);
        y = y > 0.008856 ? Math.cbrt(y) : (7.787 * y + 16.0 / 116.0);
        z = z > 0.008856 ? Math.cbrt(z) : (7.787 * z + 16.0 / 116.0);

        return new double[]{116.0 * y - 16.0, 500.0 * (x - y), 200.0 * (y - z)};
    }

    public static Color[] extractPalette(BufferedImage image, int numColors) {
        int w = image.getWidth();
        int h = image.getHeight();
        int totalPixels = w * h;
        int stride = Math.max(1, totalPixels / 10000);

        List<double[]> samples = new ArrayList<>();
        for (int i = 0; i < totalPixels; i += stride) {
            int x = i % w;
            int y = i / w;
            int rgb = image.getRGB(x, y);
            samples.add(rgbToLab((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF));
        }

        Random rng = new Random(42);
        double[][] centroids = new double[numColors][3];
        centroids[0] = samples.get(rng.nextInt(samples.size())).clone();

        for (int c = 1; c < numColors; c++) {
            double[] dists = new double[samples.size()];
            double totalDist = 0;
            for (int i = 0; i < samples.size(); i++) {
                double minDist = Double.MAX_VALUE;
                for (int j = 0; j < c; j++) {
                    double d = labDistSq(samples.get(i), centroids[j]);
                    if (d < minDist) minDist = d;
                }
                dists[i] = minDist;
                totalDist += minDist;
            }
            double threshold = rng.nextDouble() * totalDist;
            double cumulative = 0;
            int chosenIdx = samples.size() - 1;
            for (int i = 0; i < samples.size(); i++) {
                cumulative += dists[i];
                if (cumulative >= threshold) {
                    chosenIdx = i;
                    break;
                }
            }
            centroids[c] = samples.get(chosenIdx).clone();
        }

        int[] assignments = new int[samples.size()];
        for (int iter = 0; iter < 20; iter++) {
            boolean changed = false;
            for (int i = 0; i < samples.size(); i++) {
                double minDist = Double.MAX_VALUE;
                int bestC = 0;
                for (int c = 0; c < numColors; c++) {
                    double d = labDistSq(samples.get(i), centroids[c]);
                    if (d < minDist) {
                        minDist = d;
                        bestC = c;
                    }
                }
                if (assignments[i] != bestC) {
                    assignments[i] = bestC;
                    changed = true;
                }
            }
            if (!changed) break;

            double[][] sums = new double[numColors][3];
            int[] counts = new int[numColors];
            for (int i = 0; i < samples.size(); i++) {
                int c = assignments[i];
                sums[c][0] += samples.get(i)[0];
                sums[c][1] += samples.get(i)[1];
                sums[c][2] += samples.get(i)[2];
                counts[c]++;
            }
            for (int c = 0; c < numColors; c++) {
                if (counts[c] > 0) {
                    centroids[c][0] = sums[c][0] / counts[c];
                    centroids[c][1] = sums[c][1] / counts[c];
                    centroids[c][2] = sums[c][2] / counts[c];
                }
            }
        }

        Color[] palette = new Color[numColors];
        for (int c = 0; c < numColors; c++) {
            palette[c] = labToRgb(centroids[c]);
        }
        return palette;
    }

    static Color labToRgb(double[] lab) {
        double y = (lab[0] + 16.0) / 116.0;
        double x = lab[1] / 500.0 + y;
        double z = y - lab[2] / 200.0;

        x = x > 0.206893 ? x * x * x : (x - 16.0 / 116.0) / 7.787;
        y = y > 0.206893 ? y * y * y : (y - 16.0 / 116.0) / 7.787;
        z = z > 0.206893 ? z * z * z : (z - 16.0 / 116.0) / 7.787;

        x *= 0.95047;
        z *= 1.08883;

        double rr = x * 3.2404542 + y * -1.5371385 + z * -0.4985314;
        double gg = x * -0.9692660 + y * 1.8760108 + z * 0.0415560;
        double bb = x * 0.0556434 + y * -0.2040259 + z * 1.0572252;

        rr = rr > 0.0031308 ? 1.055 * Math.pow(rr, 1.0 / 2.4) - 0.055 : 12.92 * rr;
        gg = gg > 0.0031308 ? 1.055 * Math.pow(gg, 1.0 / 2.4) - 0.055 : 12.92 * gg;
        bb = bb > 0.0031308 ? 1.055 * Math.pow(bb, 1.0 / 2.4) - 0.055 : 12.92 * bb;

        return new Color(
                Math.max(0, Math.min(255, (int) Math.round(rr * 255))),
                Math.max(0, Math.min(255, (int) Math.round(gg * 255))),
                Math.max(0, Math.min(255, (int) Math.round(bb * 255))));
    }

    private static double labDistSq(double[] a, double[] b) {
        double dL = a[0] - b[0];
        double dA = a[1] - b[1];
        double dB = a[2] - b[2];
        return dL * dL + dA * dA + dB * dB;
    }

    // --- Connected component labeling (BFS flood fill, 8-connectivity) ---

    static List<PbnRegion> labelConnectedComponents(int[][] colorMap, int[][] regionMap, int w, int h) {
        for (int[] row : regionMap) Arrays.fill(row, -1);

        List<PbnRegion> regions = new ArrayList<>();
        int regionId = 0;

        int[] dx8 = {-1, -1, -1, 0, 0, 1, 1, 1};
        int[] dy8 = {-1, 0, 1, -1, 1, -1, 0, 1};

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (regionMap[y][x] >= 0) continue;

                int colorIdx = colorMap[y][x];
                PbnRegion region = new PbnRegion(regionId, colorIdx);

                Queue<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{x, y});
                regionMap[y][x] = regionId;

                while (!queue.isEmpty()) {
                    int[] p = queue.poll();
                    region.addPixel(p[0], p[1]);

                    for (int d = 0; d < 8; d++) {
                        int nx = p[0] + dx8[d];
                        int ny = p[1] + dy8[d];
                        if (nx >= 0 && nx < w && ny >= 0 && ny < h
                                && regionMap[ny][nx] < 0
                                && colorMap[ny][nx] == colorIdx) {
                            regionMap[ny][nx] = regionId;
                            queue.add(new int[]{nx, ny});
                        }
                    }
                }

                regions.add(region);
                regionId++;
            }
        }
        return regions;
    }

    // --- Merge small regions ---

    static void mergeSmallRegions(int[][] colorMap, int[][] regionMap, List<PbnRegion> regions, int minArea, int w, int h) {
        int[] dx4 = {-1, 0, 1, 0};
        int[] dy4 = {0, -1, 0, 1};

        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < regions.size(); i++) {
                PbnRegion region = regions.get(i);
                if (region == null || region.pixelCount == 0 || region.pixelCount >= minArea) continue;

                Map<Integer, Integer> neighborBorder = new HashMap<>();
                for (int y = region.minY; y <= region.maxY && y < h; y++) {
                    for (int x = region.minX; x <= region.maxX && x < w; x++) {
                        if (regionMap[y][x] != region.id) continue;
                        for (int d = 0; d < 4; d++) {
                            int nx = x + dx4[d];
                            int ny = y + dy4[d];
                            if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                                int neighborId = regionMap[ny][nx];
                                if (neighborId != region.id && regions.get(neighborId) != null) {
                                    neighborBorder.merge(neighborId, 1, Integer::sum);
                                }
                            }
                        }
                    }
                }

                if (neighborBorder.isEmpty()) continue;

                int bestNeighbor = neighborBorder.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .get().getKey();

                PbnRegion target = regions.get(bestNeighbor);
                for (int y = region.minY; y <= region.maxY && y < h; y++) {
                    for (int x = region.minX; x <= region.maxX && x < w; x++) {
                        if (regionMap[y][x] == region.id) {
                            regionMap[y][x] = bestNeighbor;
                            colorMap[y][x] = target.colorIndex;
                            target.addPixel(x, y);
                        }
                    }
                }

                region.pixelCount = 0;
                changed = true;
            }
        }

        List<PbnRegion> filtered = new ArrayList<>();
        Map<Integer, Integer> idRemap = new HashMap<>();
        for (PbnRegion r : regions) {
            if (r != null && r.pixelCount > 0) {
                idRemap.put(r.id, filtered.size());
                PbnRegion remapped = new PbnRegion(filtered.size(), r.colorIndex);
                remapped.pixelCount = r.pixelCount;
                remapped.minX = r.minX;
                remapped.minY = r.minY;
                remapped.maxX = r.maxX;
                remapped.maxY = r.maxY;
                filtered.add(remapped);
            }
        }

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Integer newId = idRemap.get(regionMap[y][x]);
                if (newId != null) {
                    regionMap[y][x] = newId;
                }
            }
        }

        regions.clear();
        regions.addAll(filtered);
    }

    // --- Contour tracing (Moore neighborhood) ---

    static List<Point2D_I32> traceRegionContour(int[][] regionMap, PbnRegion region, int w, int h) {
        int[] dx = {1, 1, 0, -1, -1, -1, 0, 1};
        int[] dy = {0, 1, 1, 1, 0, -1, -1, -1};

        int startX = -1, startY = -1;
        outer:
        for (int y = region.minY; y <= region.maxY && y < h; y++) {
            for (int x = region.minX; x <= region.maxX && x < w; x++) {
                if (regionMap[y][x] == region.id) {
                    boolean isBorder = false;
                    for (int d = 0; d < 8; d++) {
                        int nx = x + dx[d];
                        int ny = y + dy[d];
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h || regionMap[ny][nx] != region.id) {
                            isBorder = true;
                            break;
                        }
                    }
                    if (isBorder) {
                        startX = x;
                        startY = y;
                        break outer;
                    }
                }
            }
        }

        if (startX < 0) return Collections.emptyList();

        List<Point2D_I32> contour = new ArrayList<>();
        int cx = startX, cy = startY;
        int dir = 7;
        int maxSteps = region.pixelCount * 4 + 4;

        do {
            contour.add(new Point2D_I32(cx, cy));
            if (contour.size() > maxSteps) break;

            int startDir = (dir + 6) % 8;
            boolean found = false;
            for (int i = 0; i < 8; i++) {
                int d = (startDir + i) % 8;
                int nx = cx + dx[d];
                int ny = cy + dy[d];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h && regionMap[ny][nx] == region.id) {
                    dir = d;
                    cx = nx;
                    cy = ny;
                    found = true;
                    break;
                }
            }
            if (!found) break;
        } while (cx != startX || cy != startY);

        return contour;
    }

    // --- Douglas-Peucker simplification ---

    static List<Point2D_I32> douglasPeucker(List<Point2D_I32> points, double epsilon) {
        if (points.size() <= 2) return new ArrayList<>(points);

        double maxDist = 0;
        int index = 0;
        Point2D_I32 first = points.get(0);
        Point2D_I32 last = points.get(points.size() - 1);

        for (int i = 1; i < points.size() - 1; i++) {
            double dist = perpendicularDistance(points.get(i), first, last);
            if (dist > maxDist) {
                maxDist = dist;
                index = i;
            }
        }

        if (maxDist > epsilon) {
            List<Point2D_I32> left = douglasPeucker(points.subList(0, index + 1), epsilon);
            List<Point2D_I32> right = douglasPeucker(points.subList(index, points.size()), epsilon);

            List<Point2D_I32> result = new ArrayList<>(left);
            result.addAll(right.subList(1, right.size()));
            return result;
        } else {
            List<Point2D_I32> result = new ArrayList<>();
            result.add(first);
            result.add(last);
            return result;
        }
    }

    private static double perpendicularDistance(Point2D_I32 p, Point2D_I32 lineStart, Point2D_I32 lineEnd) {
        double dx = lineEnd.x - lineStart.x;
        double dy = lineEnd.y - lineStart.y;
        if (dx == 0 && dy == 0) {
            return Math.sqrt((p.x - lineStart.x) * (p.x - lineStart.x) + (p.y - lineStart.y) * (p.y - lineStart.y));
        }
        double num = Math.abs(dy * p.x - dx * p.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x);
        double den = Math.sqrt(dx * dx + dy * dy);
        return num / den;
    }

    // --- Label position: pole of inaccessibility ---

    static Point2D_I32 computeLabelPosition(int[][] regionMap, PbnRegion region, int w, int h) {
        int bestX = (region.minX + region.maxX) / 2;
        int bestY = (region.minY + region.maxY) / 2;
        double bestDist = -1;

        int regW = region.maxX - region.minX + 1;
        int regH = region.maxY - region.minY + 1;

        for (int pass = 0; pass < 3; pass++) {
            int step;
            int searchMinX, searchMinY, searchMaxX, searchMaxY;
            if (pass == 0) {
                step = Math.max(1, Math.max(regW, regH) / 8);
                searchMinX = region.minX;
                searchMinY = region.minY;
                searchMaxX = region.maxX;
                searchMaxY = region.maxY;
            } else {
                int prevStep = Math.max(1, Math.max(regW, regH) / (8 * (int) Math.pow(4, pass - 1)));
                step = Math.max(1, prevStep / 4);
                searchMinX = Math.max(region.minX, bestX - prevStep * 2);
                searchMinY = Math.max(region.minY, bestY - prevStep * 2);
                searchMaxX = Math.min(region.maxX, bestX + prevStep * 2);
                searchMaxY = Math.min(region.maxY, bestY + prevStep * 2);
            }

            for (int y = searchMinY; y <= searchMaxY; y += step) {
                for (int x = searchMinX; x <= searchMaxX; x += step) {
                    if (y < 0 || y >= h || x < 0 || x >= w) continue;
                    if (regionMap[y][x] != region.id) continue;

                    double dist = distanceToBorder(regionMap, region.id, x, y, w, h);
                    if (dist > bestDist) {
                        bestDist = dist;
                        bestX = x;
                        bestY = y;
                    }
                }
            }
        }

        if (bestDist <= 0 && regionMap[bestY < h ? bestY : h - 1][bestX < w ? bestX : w - 1] != region.id) {
            for (int y = region.minY; y <= region.maxY && y < h; y++) {
                for (int x = region.minX; x <= region.maxX && x < w; x++) {
                    if (regionMap[y][x] == region.id) {
                        return new Point2D_I32(x, y);
                    }
                }
            }
        }

        return new Point2D_I32(bestX, bestY);
    }

    private static double distanceToBorder(int[][] regionMap, int regionId, int px, int py, int w, int h) {
        double minDist = Double.MAX_VALUE;
        int searchRadius = 50;

        for (int dy = -searchRadius; dy <= searchRadius; dy++) {
            for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                int nx = px + dx;
                int ny = py + dy;
                if (nx < 0 || nx >= w || ny < 0 || ny >= h || regionMap[ny][nx] != regionId) {
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist < minDist) {
                        minDist = dist;
                    }
                }
            }
        }
        return minDist;
    }

    private static String colorToHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }
}
