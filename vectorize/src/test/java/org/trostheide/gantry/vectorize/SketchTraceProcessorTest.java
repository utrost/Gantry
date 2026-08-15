package org.trostheide.gantry.vectorize;

import georegression.struct.point.Point2D_I32;
import org.apache.commons.cli.CommandLine;
import org.junit.jupiter.api.Test;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SketchTraceProcessorTest {

    @Test
    void faintPencilLineBecomesSingleStrokeGeometry() {
        BufferedImage image = sketchLine(96, 64, true);

        List<VectorGeometry> geometry = SketchTraceProcessor.process(image, SketchTraceProcessor.Options.defaults());

        assertFalse(geometry.isEmpty(), "faint pencil strokes on paper should be detected");
        PolylineGeometry longest = longestPolyline(geometry);
        assertTrue(longest.points.size() >= 20, "skeleton trace should preserve a long line path");
        assertTrue(horizontalSpan(longest) > verticalSpan(longest) * 4,
                "horizontal source stroke should remain a horizontal plot stroke");
    }

    @Test
    void verticalSketchLineKeepsOrientation() {
        BufferedImage image = sketchLine(64, 96, false);

        List<VectorGeometry> geometry = SketchTraceProcessor.process(image, SketchTraceProcessor.Options.defaults());

        PolylineGeometry longest = longestPolyline(geometry);
        assertTrue(verticalSpan(longest) > horizontalSpan(longest) * 4,
                "vertical source stroke should remain a vertical plot stroke");
    }

    @Test
    void cleanPaperIsSuppressed() {
        BufferedImage image = new BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(245, 245, 240));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();

        List<VectorGeometry> geometry = SketchTraceProcessor.process(image, SketchTraceProcessor.Options.defaults());

        assertEquals(0, geometry.size(), "blank paper should not create arbitrary plot strokes");
    }

    @Test
    void scannerBorderLineIsSuppressed() {
        BufferedImage image = new BufferedImage(90, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(245, 245, 240));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(new Color(90, 90, 90));
        g.fillRect(0, 0, image.getWidth(), 2);
        g.dispose();

        List<VectorGeometry> geometry = SketchTraceProcessor.process(image, SketchTraceProcessor.Options.defaults());

        assertEquals(0, geometry.size(), "scanner/photo borders should not dominate a sketch trace");
    }

    @Test
    void sameImageAndOptionsProduceDeterministicGeometry() {
        BufferedImage image = sketchLine(96, 64, true);
        SketchTraceProcessor.Options options = new SketchTraceProcessor.Options(15, 0.10, 8, true);

        String first = signature(SketchTraceProcessor.process(image, options));
        String second = signature(SketchTraceProcessor.process(image, options));

        assertEquals(first, second, "preview and final vectorization should be deterministic");
    }

    @Test
    void cliRegistersSketchTraceAndParsesControls() throws Exception {
        CliParser parser = new CliParser();
        CommandLine cmd = parser.parse(new String[] {
                "-i", "in.png", "-s", "sketch",
                "--sketch-window", "17",
                "--sketch-offset", "0.12",
                "--sketch-min-length", "11",
                "--sketch-skeleton", "false"
        });

        VectorizationStrategy strategy = parser.getStrategy(cmd.getOptionValue("s"));

        assertEquals("sketch", strategy.getName());
        assertEquals(VectorizationStrategy.WorkflowType.SKETCH_TRACE, strategy.getWorkflowType());
        assertEquals(17, parser.getSketchWindow(cmd));
        assertEquals(0.12, parser.getSketchOffset(cmd), 0.001);
        assertEquals(11, parser.getSketchMinLength(cmd));
        assertFalse(parser.getSketchSkeleton(cmd));
    }

    private static BufferedImage sketchLine(int width, int height, boolean horizontal) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(245, 245, 240));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(125, 125, 125));
        g.setStroke(new BasicStroke(5.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (horizontal) {
            g.drawLine(12, height / 2, width - 12, height / 2 + 2);
        } else {
            g.drawLine(width / 2, 12, width / 2 + 1, height - 12);
        }
        g.dispose();
        return image;
    }

    private static PolylineGeometry longestPolyline(List<VectorGeometry> geometry) {
        return geometry.stream()
                .filter(PolylineGeometry.class::isInstance)
                .map(PolylineGeometry.class::cast)
                .max((a, b) -> Integer.compare(a.points.size(), b.points.size()))
                .orElseThrow(() -> new AssertionError("expected at least one polyline"));
    }

    private static int horizontalSpan(PolylineGeometry polyline) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Point2D_I32 p : polyline.points) {
            min = Math.min(min, p.x);
            max = Math.max(max, p.x);
        }
        return max - min;
    }

    private static int verticalSpan(PolylineGeometry polyline) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Point2D_I32 p : polyline.points) {
            min = Math.min(min, p.y);
            max = Math.max(max, p.y);
        }
        return max - min;
    }

    private static String signature(List<VectorGeometry> geometry) {
        StringBuilder sb = new StringBuilder();
        for (VectorGeometry vector : geometry) {
            if (vector instanceof PolylineGeometry polyline) {
                sb.append('[');
                for (Point2D_I32 point : polyline.points) {
                    sb.append(point.x).append(',').append(point.y).append(';');
                }
                sb.append(']');
            } else if (vector instanceof PathGeometry path) {
                sb.append(path.pathData);
            }
        }
        return sb.toString();
    }
}
