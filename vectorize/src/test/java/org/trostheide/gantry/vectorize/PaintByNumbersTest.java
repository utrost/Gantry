package org.trostheide.gantry.vectorize;

import georegression.struct.point.Point2D_I32;
import org.junit.jupiter.api.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaintByNumbersTest {

    @Nested
    class QuantizationTests {

        @Test
        void quantizesToNearestColor() {
            BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            img.setRGB(0, 0, Color.RED.getRGB());
            img.setRGB(1, 0, Color.BLUE.getRGB());
            img.setRGB(0, 1, Color.RED.getRGB());
            img.setRGB(1, 1, Color.BLUE.getRGB());

            Color[] palette = {Color.RED, Color.BLUE};
            int[][] colorMap = PaintByNumbersProcessor.quantizeToNearestColor(img, palette);

            assertEquals(0, colorMap[0][0]);
            assertEquals(1, colorMap[0][1]);
            assertEquals(0, colorMap[1][0]);
            assertEquals(1, colorMap[1][1]);
        }

        @Test
        void quantizesNearColorToClosestPalette() {
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            img.setRGB(0, 0, new Color(200, 10, 10).getRGB());

            Color[] palette = {Color.RED, Color.BLUE, Color.GREEN};
            int[][] colorMap = PaintByNumbersProcessor.quantizeToNearestColor(img, palette);

            assertEquals(0, colorMap[0][0], "Near-red should map to red (index 0)");
        }
    }

    @Nested
    class ConnectedComponentTests {

        @Test
        void findsTwoDistinctRegions() {
            int[][] colorMap = {
                    {0, 0, 1, 1},
                    {0, 0, 1, 1},
                    {0, 0, 1, 1},
                    {0, 0, 1, 1}
            };
            int[][] regionMap = new int[4][4];
            List<PaintByNumbersProcessor.PbnRegion> regions =
                    PaintByNumbersProcessor.labelConnectedComponents(colorMap, regionMap, 4, 4);

            assertEquals(2, regions.size());
            assertEquals(8, regions.get(0).pixelCount);
            assertEquals(8, regions.get(1).pixelCount);
        }

        @Test
        void diagonallyConnectedPixelsAreSameRegion() {
            int[][] colorMap = {
                    {0, 1},
                    {1, 0}
            };
            int[][] regionMap = new int[2][2];
            List<PaintByNumbersProcessor.PbnRegion> regions =
                    PaintByNumbersProcessor.labelConnectedComponents(colorMap, regionMap, 2, 2);

            // With 8-connectivity, diagonally touching same-color pixels form one region
            // Color 0 pixels: (0,0) and (1,1) are diagonally connected
            assertTrue(regions.size() <= 4);
            // Verify the two color-0 pixels share a region
            assertEquals(regionMap[0][0], regionMap[1][1]);
        }
    }

    @Nested
    class RegionMergeTests {

        @Test
        void mergesSmallRegionIntoNeighbor() {
            // 4x4 grid: mostly color 0, with a tiny 1-pixel color-1 region
            int[][] colorMap = {
                    {0, 0, 0, 0},
                    {0, 1, 0, 0},
                    {0, 0, 0, 0},
                    {0, 0, 0, 0}
            };
            int[][] regionMap = new int[4][4];
            List<PaintByNumbersProcessor.PbnRegion> regions =
                    PaintByNumbersProcessor.labelConnectedComponents(colorMap, regionMap, 4, 4);

            int initialCount = regions.size();

            PaintByNumbersProcessor.mergeSmallRegions(colorMap, regionMap, regions, 5, 4, 4);

            assertTrue(regions.size() < initialCount, "Small region should be merged");
            assertEquals(1, regions.size(), "Should have one region after merge");
            assertEquals(16, regions.get(0).pixelCount);
        }
    }

    @Nested
    class ContourTracingTests {

        @Test
        void tracesRectangularRegion() {
            int[][] regionMap = new int[6][6];
            PaintByNumbersProcessor.PbnRegion region = new PaintByNumbersProcessor.PbnRegion(0, 0);
            for (int y = 1; y <= 4; y++) {
                for (int x = 1; x <= 4; x++) {
                    regionMap[y][x] = 0;
                    region.addPixel(x, y);
                }
            }
            // Fill rest with -1 to mark as different region
            for (int y = 0; y < 6; y++) {
                for (int x = 0; x < 6; x++) {
                    if (y < 1 || y > 4 || x < 1 || x > 4) {
                        regionMap[y][x] = -1;
                    }
                }
            }

            List<Point2D_I32> boundary = PaintByNumbersProcessor.traceRegionContour(regionMap, region, 6, 6);
            assertNotNull(boundary);
            assertFalse(boundary.isEmpty(), "Should trace boundary of rectangular region");
        }
    }

    @Nested
    class LabelPositionTests {

        @Test
        void labelIsInsideRegion() {
            int[][] regionMap = new int[10][10];
            for (int[] row : regionMap) Arrays.fill(row, -1);
            PaintByNumbersProcessor.PbnRegion region = new PaintByNumbersProcessor.PbnRegion(0, 0);
            for (int y = 2; y <= 7; y++) {
                for (int x = 2; x <= 7; x++) {
                    regionMap[y][x] = 0;
                    region.addPixel(x, y);
                }
            }

            Point2D_I32 pos = PaintByNumbersProcessor.computeLabelPosition(regionMap, region, 10, 10);
            assertNotNull(pos);
            assertEquals(0, regionMap[pos.y][pos.x], "Label should be inside region");
        }
    }

    @Nested
    class EndToEndTests {

        @Test
        void fullPipelineProducesValidSvg() throws Exception {
            BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.RED);
            g.fillRect(0, 0, 50, 100);
            g.setColor(Color.BLUE);
            g.fillRect(50, 0, 50, 100);
            g.dispose();

            Color[] palette = {Color.RED, Color.BLUE};
            PaintByNumbersProcessor.PbnResult result = PaintByNumbersProcessor.process(img, palette, 10, 2.0);

            assertNotNull(result);
            assertFalse(result.regions.isEmpty());
            assertEquals(100, result.width);
            assertEquals(100, result.height);

            File tempSvg = File.createTempFile("test_pbn_", ".svg");
            tempSvg.deleteOnExit();

            PaintByNumbersProcessor.writeSvg(result, tempSvg.getAbsolutePath(), 14, true, true);

            String content = new String(Files.readAllBytes(tempSvg.toPath()));
            assertTrue(content.contains("<svg"), "Should contain SVG root");
            assertTrue(content.contains("<polygon"), "Should contain polygon elements");
            assertTrue(content.contains("<text"), "Should contain text labels");
            assertTrue(content.contains("id=\"legend\""), "Should contain legend");
        }

        @Test
        void noLegendNoNumbersMode() throws Exception {
            BufferedImage img = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 50, 50);
            g.dispose();

            Color[] palette = {Color.WHITE, Color.BLACK};
            PaintByNumbersProcessor.PbnResult result = PaintByNumbersProcessor.process(img, palette, 10, 2.0);

            File tempSvg = File.createTempFile("test_pbn_minimal_", ".svg");
            tempSvg.deleteOnExit();

            PaintByNumbersProcessor.writeSvg(result, tempSvg.getAbsolutePath(), 14, false, false);

            String content = new String(Files.readAllBytes(tempSvg.toPath()));
            assertTrue(content.contains("<svg"));
            assertFalse(content.contains("id=\"labels\""), "Should not contain labels when showNumbers=false");
            assertFalse(content.contains("id=\"legend\""), "Should not contain legend when showLegend=false");
        }
    }

    @Nested
    class StrategyRegistrationTests {

        @Test
        void pbnStrategyRegisteredInCliParser() {
            CliParser cli = new CliParser();
            VectorizationStrategy strategy = cli.getStrategy("pbn");
            assertNotNull(strategy);
            assertEquals("pbn", strategy.getName());
        }

        @Test
        void pbnStrategyThrowsOnProcessContour() {
            var strategy = new org.trostheide.gantry.vectorize.strategies.PaintByNumbersStrategy();
            assertThrows(UnsupportedOperationException.class, () ->
                    strategy.processContour(List.of(), 1.0));
        }
    }

    @Nested
    class AutoPaletteTests {

        @Test
        void extractsColorsFromSolidQuadrants() {
            BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.RED);
            g.fillRect(0, 0, 50, 50);
            g.setColor(Color.GREEN);
            g.fillRect(50, 0, 50, 50);
            g.setColor(Color.BLUE);
            g.fillRect(0, 50, 100, 50);
            g.dispose();

            Color[] palette = PaintByNumbersProcessor.extractPalette(img, 3);
            assertNotNull(palette);
            assertEquals(3, palette.length);

            boolean hasReddish = false, hasGreenish = false, hasBluish = false;
            for (Color c : palette) {
                double[] lab = PaintByNumbersProcessor.rgbToLab(c.getRed(), c.getGreen(), c.getBlue());
                double[] redLab = PaintByNumbersProcessor.rgbToLab(255, 0, 0);
                double[] greenLab = PaintByNumbersProcessor.rgbToLab(0, 255, 0);
                double[] blueLab = PaintByNumbersProcessor.rgbToLab(0, 0, 255);
                double dRed = Math.sqrt(sq(lab, redLab));
                double dGreen = Math.sqrt(sq(lab, greenLab));
                double dBlue = Math.sqrt(sq(lab, blueLab));
                if (dRed < 20) hasReddish = true;
                if (dGreen < 20) hasGreenish = true;
                if (dBlue < 20) hasBluish = true;
            }
            assertTrue(hasReddish, "Should find a color close to red");
            assertTrue(hasGreenish, "Should find a color close to green");
            assertTrue(hasBluish, "Should find a color close to blue");
        }

        @Test
        void processAutoExtractsWhenPaletteNull() {
            BufferedImage img = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 50, 50);
            g.dispose();

            PaintByNumbersProcessor.PbnResult result = PaintByNumbersProcessor.process(img, null, 10, 2.0);
            assertNotNull(result);
            assertNotNull(result.palette);
            assertEquals(6, result.palette.length);
        }

        private double sq(double[] a, double[] b) {
            return (a[0]-b[0])*(a[0]-b[0]) + (a[1]-b[1])*(a[1]-b[1]) + (a[2]-b[2])*(a[2]-b[2]);
        }
    }

    @Nested
    class LabRoundTripTests {

        @Test
        void labToRgbRoundTripsForPrimaryColors() {
            for (Color c : new Color[]{Color.RED, Color.GREEN, Color.BLUE, Color.WHITE, Color.BLACK}) {
                double[] lab = PaintByNumbersProcessor.rgbToLab(c.getRed(), c.getGreen(), c.getBlue());
                Color result = PaintByNumbersProcessor.labToRgb(lab);
                assertEquals(c.getRed(), result.getRed(), 2, "Red channel round-trip for " + c);
                assertEquals(c.getGreen(), result.getGreen(), 2, "Green channel round-trip for " + c);
                assertEquals(c.getBlue(), result.getBlue(), 2, "Blue channel round-trip for " + c);
            }
        }
    }

    @Nested
    class ColorConversionTests {

        @Test
        void rgbToLabBlack() {
            double[] lab = PaintByNumbersProcessor.rgbToLab(0, 0, 0);
            assertEquals(0.0, lab[0], 1.0, "Black should have L near 0");
        }

        @Test
        void rgbToLabWhite() {
            double[] lab = PaintByNumbersProcessor.rgbToLab(255, 255, 255);
            assertEquals(100.0, lab[0], 1.0, "White should have L near 100");
        }
    }
}
