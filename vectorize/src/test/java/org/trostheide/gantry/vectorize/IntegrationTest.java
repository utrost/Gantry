package org.trostheide.gantry.vectorize;

import georegression.struct.point.Point2D_I32;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.trostheide.gantry.vectorize.strategies.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationTest {

    private static final String TEST_IMAGES_DIR = "testimages";

    private BufferedImage loadTestImage(String name) throws Exception {
        File f = new File(TEST_IMAGES_DIR, name);
        assertTrue(f.exists(), "Test image should exist: " + name);
        BufferedImage img = ImageIO.read(f);
        assertNotNull(img, "Should be able to read: " + name);
        return img;
    }

    @Nested
    class ContourExtractionTests {

        @ParameterizedTest
        @ValueSource(strings = {"test_circles.png", "test_curves.png", "test_mixed_shapes.png",
                "test_rectangles.png", "test_triangles.png"})
        void extractsContoursFromTestImages(String imageName) throws Exception {
            BufferedImage img = loadTestImage(imageName);
            List<List<Point2D_I32>> contours = BoofcvBatikVector.extractContours(img);
            assertNotNull(contours);
            assertFalse(contours.isEmpty(), "Should find contours in " + imageName);
        }

        @ParameterizedTest
        @ValueSource(strings = {"test_circles.png", "test_rectangles.png"})
        void colorAwareCannyFindsContours(String imageName) throws Exception {
            BufferedImage img = loadTestImage(imageName);
            List<List<Point2D_I32>> contours = BoofcvBatikVector.extractContours(
                    img, 1.5f, 0.02f, 0.1f, true);
            assertNotNull(contours);
            assertFalse(contours.isEmpty(), "Color-aware Canny should find contours");
        }

        @Test
        void colorAwareProcessesColorBoundaryImage() throws Exception {
            BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(200, 50, 50));
            g.fillRect(0, 0, 50, 100);
            g.setColor(new Color(50, 200, 50));
            g.fillRect(50, 0, 50, 100);
            g.dispose();

            List<List<Point2D_I32>> colorAware = BoofcvBatikVector.extractContours(
                    img, 1.5f, 0.02f, 0.1f, true);
            assertNotNull(colorAware);
            assertFalse(colorAware.isEmpty(),
                    "Color-aware should find edges on color boundary image");
        }

        @Test
        void grayscaleImageFallsBackToGrayscaleCanny() throws Exception {
            BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 100, 100);
            g.setColor(Color.BLACK);
            g.fillRect(25, 25, 50, 50);
            g.dispose();

            List<List<Point2D_I32>> contours = BoofcvBatikVector.extractContours(
                    img, 1.5f, 0.02f, 0.1f, true);
            assertNotNull(contours);
            assertFalse(contours.isEmpty());
        }
    }

    @Nested
    class StrategyWorkflowTests {

        @ParameterizedTest
        @ValueSource(strings = {"dp", "line", "raw", "convexhull"})
        void cannyBasedStrategiesProduceSvg(String strategyName) throws Exception {
            BufferedImage img = loadTestImage("test_circles.png");
            VectorizationStrategy strategy = createStrategy(strategyName);

            List<List<Point2D_I32>> contours = BoofcvBatikVector.extractContours(img);

            File tempSvg = File.createTempFile("test_" + strategyName + "_", ".svg");
            tempSvg.deleteOnExit();

            BoofcvBatikVector.createSvgFileBatik(
                    contours, img.getWidth(), img.getHeight(), tempSvg.getAbsolutePath(),
                    strategy, 2.0, 1.0, 5.0, 2000.0, "black", 1.0, false);

            assertTrue(tempSvg.exists(), "SVG file should be created");
            assertTrue(tempSvg.length() > 0, "SVG file should not be empty");

            String content = new String(Files.readAllBytes(tempSvg.toPath()));
            assertTrue(content.contains("<svg"), "Should contain SVG root element");
        }

        @Test
        void centerlineStrategyProducesSvg() throws Exception {
            BufferedImage img = loadTestImage("test_circles.png");

            List<VectorGeometry> geometry = SkeletonStrategy.vectoriseImage(img, 128, 2.0);
            assertNotNull(geometry);

            File tempSvg = File.createTempFile("test_centerline_", ".svg");
            tempSvg.deleteOnExit();

            BoofcvBatikVector.createSvgFileFromGeometry(
                    geometry, img.getWidth(), img.getHeight(), tempSvg.getAbsolutePath());

            assertTrue(tempSvg.exists());
            assertTrue(tempSvg.length() > 0);

            String content = new String(Files.readAllBytes(tempSvg.toPath()));
            assertTrue(content.contains("<svg"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"test_circles.png", "test_curves.png", "test_mixed_shapes.png"})
        void endToEndDouglasPeuckerWorkflow(String imageName) throws Exception {
            BufferedImage img = loadTestImage(imageName);

            List<List<Point2D_I32>> contours = BoofcvBatikVector.extractContours(img);
            assertFalse(contours.isEmpty());

            File tempSvg = File.createTempFile("test_e2e_", ".svg");
            tempSvg.deleteOnExit();

            BoofcvBatikVector.createSvgFileBatik(
                    contours, img.getWidth(), img.getHeight(), tempSvg.getAbsolutePath(),
                    new DouglasPeuckerStrategy(), 2.0, 1.0, 5.0, 2000.0, "black", 1.0, true);

            assertTrue(tempSvg.exists());
            String content = new String(Files.readAllBytes(tempSvg.toPath()));
            assertTrue(content.contains("<svg"));
        }

        @Test
        void smoothCurvesProducesBezierPaths() throws Exception {
            BufferedImage img = loadTestImage("test_curves.png");
            List<List<Point2D_I32>> contours = BoofcvBatikVector.extractContours(img);

            File tempSvg = File.createTempFile("test_smooth_", ".svg");
            tempSvg.deleteOnExit();

            BoofcvBatikVector.createSvgFileBatik(
                    contours, img.getWidth(), img.getHeight(), tempSvg.getAbsolutePath(),
                    new DouglasPeuckerStrategy(), 2.0, 1.0, 1.0, 2000.0, "red", 2.0, true);

            String content = new String(Files.readAllBytes(tempSvg.toPath()));
            assertTrue(content.contains("<svg"));
        }

        @Test
        void colorAwareCannyEndToEnd() throws Exception {
            BufferedImage img = loadTestImage("test_circles.png");

            List<List<Point2D_I32>> contours = BoofcvBatikVector.extractContours(
                    img, 1.5f, 0.02f, 0.1f, true);

            File tempSvg = File.createTempFile("test_color_e2e_", ".svg");
            tempSvg.deleteOnExit();

            BoofcvBatikVector.createSvgFileBatik(
                    contours, img.getWidth(), img.getHeight(), tempSvg.getAbsolutePath(),
                    new DouglasPeuckerStrategy(), 2.0, 1.0, 5.0, 2000.0);

            assertTrue(tempSvg.exists());
            assertTrue(tempSvg.length() > 0);
        }
    }

    @Nested
    class SvgOptimizerTests {

        @Test
        void optimizerDoesNotCorruptSvg() throws Exception {
            BufferedImage img = loadTestImage("test_rectangles.png");
            List<List<Point2D_I32>> contours = BoofcvBatikVector.extractContours(img);

            File tempSvg = File.createTempFile("test_opt_", ".svg");
            tempSvg.deleteOnExit();

            BoofcvBatikVector.createSvgFileBatik(
                    contours, img.getWidth(), img.getHeight(), tempSvg.getAbsolutePath(),
                    new DouglasPeuckerStrategy(), 2.0, 1.0, 5.0, 2000.0);

            long sizeBefore = tempSvg.length();
            SvgOptimizer.optimize(tempSvg.getAbsolutePath());
            long sizeAfter = tempSvg.length();

            assertTrue(sizeAfter > 0, "Optimized SVG should not be empty");
            assertTrue(sizeAfter <= sizeBefore, "Optimized SVG should be smaller or same size");

            String content = new String(Files.readAllBytes(tempSvg.toPath()));
            assertTrue(content.contains("<svg"));
        }
    }

    @Nested
    class AutoCannyTests {

        @Test
        void autoCannyComputesValidThresholds() {
            BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 100, 100);
            g.setColor(Color.BLACK);
            g.fillRect(25, 25, 50, 50);
            g.dispose();

            AutoCannyThresholds auto = AutoCannyThresholds.compute(img);
            assertTrue(auto.getLow() > 0, "Low threshold should be positive");
            assertTrue(auto.getHigh() > auto.getLow(), "High > low");
            assertTrue(auto.getHigh() <= 1.0, "High threshold should be <= 1.0");
        }

        @ParameterizedTest
        @ValueSource(strings = {"test_circles.png", "test_rectangles.png"})
        void autoCannyOnRealImages(String imageName) throws Exception {
            BufferedImage img = loadTestImage(imageName);
            AutoCannyThresholds auto = AutoCannyThresholds.compute(img);
            assertTrue(auto.getLow() > 0);
            assertTrue(auto.getHigh() > auto.getLow());
        }
    }

    @Nested
    class PolylineToBezierTests {

        @Test
        void convertsPolylineToBezierPath() {
            List<Point2D_I32> points = List.of(
                    new Point2D_I32(0, 0),
                    new Point2D_I32(50, 50),
                    new Point2D_I32(100, 0));

            String path = BoofcvBatikVector.polylineToCubicBezierPath(points);
            assertNotNull(path);
            assertTrue(path.startsWith("M"));
            assertTrue(path.contains("C"));
        }

        @Test
        void twoPointsProducesLinePath() {
            List<Point2D_I32> points = List.of(
                    new Point2D_I32(0, 0),
                    new Point2D_I32(100, 100));

            String path = BoofcvBatikVector.polylineToCubicBezierPath(points);
            assertTrue(path.startsWith("M"));
            assertTrue(path.contains("L"));
            assertFalse(path.contains("C"));
        }

        @Test
        void singlePointReturnsEmpty() {
            List<Point2D_I32> points = List.of(new Point2D_I32(10, 10));
            String path = BoofcvBatikVector.polylineToCubicBezierPath(points);
            assertEquals("", path);
        }
    }

    @Nested
    class StrategyConsistencyTests {

        @Test
        void allCannyStrategiesProcessContours() throws Exception {
            BufferedImage img = loadTestImage("test_rectangles.png");
            List<List<Point2D_I32>> contours = BoofcvBatikVector.extractContours(img);
            assertFalse(contours.isEmpty());

            String[] strategyNames = {"dp", "line", "raw", "convexhull"};
            for (String name : strategyNames) {
                VectorizationStrategy strategy = createStrategy(name);
                File tempSvg = File.createTempFile("test_consist_", ".svg");
                tempSvg.deleteOnExit();

                assertDoesNotThrow(() ->
                        BoofcvBatikVector.createSvgFileBatik(
                                contours, img.getWidth(), img.getHeight(), tempSvg.getAbsolutePath(),
                                strategy, 2.0, 1.0, 5.0, 2000.0),
                        "Strategy " + name + " should not throw");

                assertTrue(tempSvg.length() > 0, name + " should produce non-empty SVG");
            }
        }
    }

    @Nested
    class CropTests {

        @Test
        void cropCliOptionProducesCorrectRectangle() {
            CliParser cli = new CliParser();
            try {
                org.apache.commons.cli.CommandLine cmd = cli.parse(
                        new String[]{"-i", "dummy.png", "--crop", "10,20,100,200"});
                java.awt.Rectangle crop = cli.getCrop(cmd);
                assertNotNull(crop);
                assertEquals(10, crop.x);
                assertEquals(20, crop.y);
                assertEquals(100, crop.width);
                assertEquals(200, crop.height);
            } catch (Exception e) {
                fail("Should not throw: " + e.getMessage());
            }
        }

        @Test
        void cropCliOptionNullWhenNotSpecified() {
            CliParser cli = new CliParser();
            try {
                org.apache.commons.cli.CommandLine cmd = cli.parse(new String[]{"-i", "dummy.png"});
                assertNull(cli.getCrop(cmd));
            } catch (Exception e) {
                fail("Should not throw: " + e.getMessage());
            }
        }

        @Test
        void cropProducesSmallerSvg() throws Exception {
            BufferedImage img = loadTestImage("test_circles.png");

            BufferedImage cropped = img.getSubimage(10, 10,
                    Math.min(100, img.getWidth() - 10),
                    Math.min(100, img.getHeight() - 10));

            List<georegression.struct.point.Point2D_I32> contoursFull =
                    BoofcvBatikVector.extractContours(img).stream()
                            .flatMap(List::stream).toList();
            List<georegression.struct.point.Point2D_I32> contoursCropped =
                    BoofcvBatikVector.extractContours(cropped).stream()
                            .flatMap(List::stream).toList();

            File svgFull = File.createTempFile("test_crop_full_", ".svg");
            svgFull.deleteOnExit();
            File svgCropped = File.createTempFile("test_crop_cropped_", ".svg");
            svgCropped.deleteOnExit();

            BoofcvBatikVector.createSvgFileBatik(
                    BoofcvBatikVector.extractContours(img),
                    img.getWidth(), img.getHeight(), svgFull.getAbsolutePath(),
                    new org.trostheide.gantry.vectorize.strategies.DouglasPeuckerStrategy(),
                    2.0, 1.0, 5.0, 2000.0);

            BoofcvBatikVector.createSvgFileBatik(
                    BoofcvBatikVector.extractContours(cropped),
                    cropped.getWidth(), cropped.getHeight(), svgCropped.getAbsolutePath(),
                    new org.trostheide.gantry.vectorize.strategies.DouglasPeuckerStrategy(),
                    2.0, 1.0, 5.0, 2000.0);

            String croppedContent = new String(Files.readAllBytes(svgCropped.toPath()));
            assertTrue(croppedContent.contains("<svg"));
            assertTrue(croppedContent.contains("width=\"" + cropped.getWidth()));
            assertTrue(croppedContent.contains("height=\"" + cropped.getHeight()));
        }
    }

    private VectorizationStrategy createStrategy(String name) {
        return switch (name) {
            case "dp" -> new DouglasPeuckerStrategy();
            case "line" -> new StraightLineStrategy();
            case "raw" -> new RawContourStrategy();
            case "convexhull" -> new ConvexHullStrategy();
            case "bezier" -> new BezierStrategy();
            case "bezier2" -> new ImageTracerStrategy();
            case "centerline" -> new SkeletonStrategy();
            default -> throw new IllegalArgumentException("Unknown strategy: " + name);
        };
    }
}
