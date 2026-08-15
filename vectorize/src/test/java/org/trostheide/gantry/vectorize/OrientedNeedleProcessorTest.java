package org.trostheide.gantry.vectorize;

import georegression.struct.point.Point2D_I32;
import org.apache.commons.cli.CommandLine;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrientedNeedleProcessorTest {

    @Test
    void verticalToneBoundaryProducesMostlyHorizontalNeedles() {
        BufferedImage image = whiteImage(80, 60);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 40, 60);
        g.dispose();

        List<VectorGeometry> geometry = OrientedNeedleProcessor.process(image, OrientedNeedleProcessor.Options.defaults());

        assertFalse(geometry.isEmpty(), "a hard tonal boundary should produce needle strokes");
        assertTrue(geometry.stream().allMatch(PolylineGeometry.class::isInstance), "needles should be plotter-friendly polylines");

        long horizontal = geometry.stream()
                .map(PolylineGeometry.class::cast)
                .filter(OrientedNeedleProcessorTest::isMostlyHorizontal)
                .count();

        assertTrue(horizontal >= geometry.size() * 0.70,
                "a vertical brightness boundary has an X gradient, so most needles should run horizontally");
    }

    @Test
    void horizontalToneBoundaryProducesMostlyVerticalNeedles() {
        BufferedImage image = whiteImage(80, 60);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 80, 30);
        g.dispose();

        List<VectorGeometry> geometry = OrientedNeedleProcessor.process(image, OrientedNeedleProcessor.Options.defaults());

        long vertical = geometry.stream()
                .map(PolylineGeometry.class::cast)
                .filter(OrientedNeedleProcessorTest::isMostlyVertical)
                .count();

        assertFalse(geometry.isEmpty(), "a hard horizontal boundary should produce needle strokes");
        assertTrue(vertical >= geometry.size() * 0.70,
                "a horizontal brightness boundary has a Y gradient, so most needles should run vertically");
    }

    @Test
    void whiteBackgroundIsSuppressed() {
        BufferedImage image = whiteImage(90, 70);

        List<VectorGeometry> geometry = OrientedNeedleProcessor.process(image, OrientedNeedleProcessor.Options.defaults());

        assertEquals(0, geometry.size(), "plain white image should not create needless plotting strokes");
    }

    @Test
    void sameImageAndOptionsProduceDeterministicGeometry() {
        BufferedImage image = whiteImage(90, 70);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillOval(25, 15, 38, 34);
        g.dispose();

        OrientedNeedleProcessor.Options options = new OrientedNeedleProcessor.Options(7.0, 11.0, 0.18, 0.05, 1.2);

        String first = signature(OrientedNeedleProcessor.process(image, options));
        String second = signature(OrientedNeedleProcessor.process(image, options));

        assertEquals(first, second, "preview and final vectorization need stable output for the same options");
    }

    @Test
    void cliRegistersNeedlesAsWholeImageStrategyAndParsesControls() throws Exception {
        CliParser parser = new CliParser();
        CommandLine cmd = parser.parse(new String[] {
                "-i", "in.png", "-s", "needles",
                "--needle-spacing", "8.5",
                "--needle-length", "13.0",
                "--needle-threshold", "0.22",
                "--needle-gradient", "0.08",
                "--needle-tone", "1.4"
        });

        VectorizationStrategy strategy = parser.getStrategy(cmd.getOptionValue("s"));

        assertEquals("needles", strategy.getName());
        assertEquals(VectorizationStrategy.WorkflowType.ORIENTED_NEEDLES, strategy.getWorkflowType());
        assertEquals(8.5, parser.getNeedleSpacing(cmd), 0.001);
        assertEquals(13.0, parser.getNeedleLength(cmd), 0.001);
        assertEquals(0.22, parser.getNeedleThreshold(cmd), 0.001);
        assertEquals(0.08, parser.getNeedleGradientThreshold(cmd), 0.001);
        assertEquals(1.4, parser.getNeedleTone(cmd), 0.001);
    }

    private static boolean isMostlyHorizontal(PolylineGeometry polyline) {
        assertEquals(2, polyline.points.size(), "a needle is a two-point dash");
        Point2D_I32 a = polyline.points.get(0);
        Point2D_I32 b = polyline.points.get(1);
        return Math.abs(b.x - a.x) > Math.abs(b.y - a.y) * 2;
    }

    private static boolean isMostlyVertical(PolylineGeometry polyline) {
        assertEquals(2, polyline.points.size(), "a needle is a two-point dash");
        Point2D_I32 a = polyline.points.get(0);
        Point2D_I32 b = polyline.points.get(1);
        return Math.abs(b.y - a.y) > Math.abs(b.x - a.x) * 2;
    }

    private static BufferedImage whiteImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
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
