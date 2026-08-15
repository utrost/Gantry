package org.trostheide.gantry.vectorize;

import georegression.struct.point.Point2D_I32;
import org.apache.commons.cli.CommandLine;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TonalIsolineProcessorTest {

    @Test
    void radialGradientProducesClosedContourLoops() {
        BufferedImage image = radialGradient(96, 96);

        List<VectorGeometry> geometry = TonalIsolineProcessor.process(image, TonalIsolineProcessor.Options.defaults());

        assertFalse(geometry.isEmpty(), "a smooth tonal hill should produce contour loops");
        assertTrue(geometry.stream().allMatch(PolylineGeometry.class::isInstance), "isolines should be plotter-friendly polylines");
        long closedLoops = geometry.stream()
                .map(PolylineGeometry.class::cast)
                .filter(polyline -> distance(polyline.points.get(0), polyline.points.get(polyline.points.size() - 1)) <= 2.0)
                .count();
        assertTrue(closedLoops >= 3, "radial gradient should produce several closed topographic rings");
    }

    @Test
    void levelCountControlsNumberOfContours() {
        BufferedImage image = radialGradient(96, 96);

        int sparse = TonalIsolineProcessor.process(image,
                new TonalIsolineProcessor.Options(3, 4.0, 8, 0.08)).size();
        int dense = TonalIsolineProcessor.process(image,
                new TonalIsolineProcessor.Options(8, 4.0, 8, 0.08)).size();

        assertTrue(dense > sparse, "more tonal levels should produce more isoline loops");
    }

    @Test
    void flatImageIsSuppressed() {
        BufferedImage image = new BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(200, 200, 200));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();

        List<VectorGeometry> geometry = TonalIsolineProcessor.process(image, TonalIsolineProcessor.Options.defaults());

        assertEquals(0, geometry.size(), "flat tone should not create arbitrary contour noise");
    }

    @Test
    void sameImageAndOptionsProduceDeterministicGeometry() {
        BufferedImage image = radialGradient(90, 70);
        TonalIsolineProcessor.Options options = new TonalIsolineProcessor.Options(6, 3.0, 6, 0.05);

        String first = signature(TonalIsolineProcessor.process(image, options));
        String second = signature(TonalIsolineProcessor.process(image, options));

        assertEquals(first, second, "preview and final vectorization need stable output for the same options");
    }

    @Test
    void cliRegistersIsolinesAsWholeImageStrategyAndParsesControls() throws Exception {
        CliParser parser = new CliParser();
        CommandLine cmd = parser.parse(new String[] {
                "-i", "in.png", "-s", "isolines",
                "--isoline-levels", "7",
                "--isoline-smoothing", "5.0",
                "--isoline-min-length", "12",
                "--isoline-threshold", "0.09"
        });

        VectorizationStrategy strategy = parser.getStrategy(cmd.getOptionValue("s"));

        assertEquals("isolines", strategy.getName());
        assertEquals(VectorizationStrategy.WorkflowType.TONAL_ISOLINES, strategy.getWorkflowType());
        assertEquals(7, parser.getIsolineLevels(cmd));
        assertEquals(5.0, parser.getIsolineSmoothing(cmd), 0.001);
        assertEquals(12, parser.getIsolineMinLength(cmd));
        assertEquals(0.09, parser.getIsolineThreshold(cmd), 0.001);
    }

    private static BufferedImage radialGradient(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        double cx = (width - 1) / 2.0;
        double cy = (height - 1) / 2.0;
        double maxDistance = Math.hypot(cx, cy);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double d = Math.hypot(x - cx, y - cy) / maxDistance;
                int v = (int) Math.round(255 * Math.min(1.0, d));
                image.setRGB(x, y, new Color(v, v, v).getRGB());
            }
        }
        return image;
    }

    private static double distance(Point2D_I32 a, Point2D_I32 b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    private static String signature(List<VectorGeometry> geometry) {
        StringBuilder sb = new StringBuilder();
        for (VectorGeometry vector : geometry) {
            PolylineGeometry polyline = (PolylineGeometry) vector;
            sb.append('[');
            for (Point2D_I32 point : polyline.points) {
                sb.append(point.x).append(',').append(point.y).append(';');
            }
            sb.append(']');
        }
        return sb.toString();
    }
}
