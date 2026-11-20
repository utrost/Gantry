package org.trostheide.gantry.vectorize;

import georegression.struct.point.Point2D_I32;
import org.apache.commons.cli.CommandLine;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TonalSquiggleProcessorTest {

    @Test
    void darkSubjectProducesSquigglePolylinesWhileWhiteBackgroundIsSuppressed() {
        BufferedImage image = whiteImage(80, 60);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(28, 12, 24, 36);
        g.dispose();

        List<VectorGeometry> geometry = TonalSquiggleProcessor.process(image, TonalSquiggleProcessor.Options.defaults());

        assertFalse(geometry.isEmpty(), "dark subject should produce drawable squiggle paths");
        assertTrue(geometry.stream().allMatch(PolylineGeometry.class::isInstance), "squiggle output should be plotter-friendly polylines");

        long subjectPaths = geometry.stream()
                .map(PolylineGeometry.class::cast)
                .filter(polyline -> polyline.points.stream().anyMatch(p -> p.x >= 28 && p.x <= 52 && p.y >= 12 && p.y <= 48))
                .count();
        long backgroundPaths = geometry.stream()
                .map(PolylineGeometry.class::cast)
                .filter(polyline -> polyline.points.stream().noneMatch(p -> p.x >= 24 && p.x <= 56 && p.y >= 8 && p.y <= 52))
                .count();

        assertTrue(subjectPaths > 0, "subject area should contain tonal squiggle paths");
        assertEquals(0, backgroundPaths, "plain white background should stay empty");
    }

    @Test
    void flatDarkAreasAreSuppressedWithoutRemovingSubjectBoundary() {
        BufferedImage image = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 100, 80);
        g.setColor(Color.WHITE);
        g.fillOval(34, 18, 32, 42);
        g.dispose();

        List<VectorGeometry> geometry = TonalSquiggleProcessor.process(image, TonalSquiggleProcessor.Options.defaults());

        long centralPaths = geometry.stream()
                .map(PolylineGeometry.class::cast)
                .filter(polyline -> polyline.points.stream().anyMatch(p -> p.x >= 28 && p.x <= 72 && p.y >= 12 && p.y <= 66))
                .count();
        long farBackgroundPaths = geometry.stream()
                .map(PolylineGeometry.class::cast)
                .filter(polyline -> polyline.points.stream().noneMatch(p -> p.x >= 20 && p.x <= 80 && p.y >= 8 && p.y <= 70))
                .count();

        assertTrue(centralPaths > 0, "portrait subject boundary should still be drawn");
        assertTrue(farBackgroundPaths < centralPaths,
                "flat dark background should be suppressed instead of becoming the dominant drawing");
    }

    @Test
    void shiftedSubjectRetainsTheSameGeometryWithoutACentrePrior() {
        for (double suppression : new double[] {0, .85}) {
            String reference = null;
            for (int shift : new int[] {0, 48, 96}) {
                BufferedImage image = whiteImage(192, 144);
                Graphics2D g = image.createGraphics();
                g.setColor(Color.BLACK);
                g.fillRect(24 + shift, 24 + shift / 2, 24, 32);
                g.dispose();
                // Translation aligns with all sampling grids; zero amplitude removes sine phase.
                var options = new TonalSquiggleProcessor.Options(8, 0, .6, .72, suppression, 1);
                var geometry = TonalSquiggleProcessor.process(image, options);
                assertFalse(geometry.isEmpty(), "off-centre subject must survive suppression=" + suppression);
                StringBuilder normalized = new StringBuilder();
                for (var vector : geometry) {
                    normalized.append('[');
                    for (var point : ((PolylineGeometry) vector).points) {
                        normalized.append(point.x - shift).append(',')
                                .append(point.y - shift / 2).append(';');
                    }
                    normalized.append(']');
                }
                if (reference == null) reference = normalized.toString();
                else assertEquals(reference, normalized.toString(),
                        "moving a subject must not change its coverage or contours");
            }
        }
    }

    @Test
    void zeroSuppressionPreservesFlatDarkToneAcrossTheWholeImage() {
        BufferedImage image = new BufferedImage(160, 112, BufferedImage.TYPE_INT_RGB);
        var options = new TonalSquiggleProcessor.Options(8, 0, .6, .72, 0, 1);
        var geometry = TonalSquiggleProcessor.process(image, options);
        assertFalse(geometry.isEmpty());
        assertTrue(geometry.stream().map(PolylineGeometry.class::cast)
                .allMatch(line -> line.points.get(0).x < 20
                        && line.points.get(line.points.size() - 1).x > 140),
                "zero suppression must shade peripheral dark areas as well as the centre");
        assertTrue(TonalSquiggleProcessor.process(image,
                new TonalSquiggleProcessor.Options(8, 0, .6, .72, 1, 1)).isEmpty(),
                "maximum suppression intentionally omits uniformly dark areas everywhere");
    }

    @Test
    void detailPreservesConnectedVerticalStructureRatherThanHorizontalEdgeDashes() {
        BufferedImage image = whiteImage(80, 80);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(35, 8, 3, 56);
        g.dispose();
        var geometry = TonalSquiggleProcessor.process(image,
                new TonalSquiggleProcessor.Options(8, 0, .6, 0, .85, 1));
        assertTrue(geometry.stream().map(PolylineGeometry.class::cast).anyMatch(line -> {
            int minX = line.points.stream().mapToInt(p -> p.x).min().orElseThrow();
            int maxX = line.points.stream().mapToInt(p -> p.x).max().orElseThrow();
            int minY = line.points.stream().mapToInt(p -> p.y).min().orElseThrow();
            int maxY = line.points.stream().mapToInt(p -> p.y).max().orElseThrow();
            return maxY - minY > 40 && maxX - minX <= 4;
        }), "a long vertical feature should remain a connected contour");
    }

    @Test
    void sameImageAndOptionsProduceDeterministicGeometry() {
        BufferedImage image = whiteImage(70, 50);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillOval(20, 10, 30, 28);
        g.dispose();

        TonalSquiggleProcessor.Options options = new TonalSquiggleProcessor.Options(8.0, 4.0, 0.65, 0.75, 0.85);

        String first = signature(TonalSquiggleProcessor.process(image, options));
        String second = signature(TonalSquiggleProcessor.process(image, options));

        assertEquals(first, second, "preview and final vectorization need stable output for the same options");
    }

    @Test
    void cliRegistersSquiggleAsWholeImageStrategy() throws Exception {
        CliParser parser = new CliParser();
        CommandLine cmd = parser.parse(new String[] {"-i", "in.png", "-s", "squiggle"});

        VectorizationStrategy strategy = parser.getStrategy(cmd.getOptionValue("s"));

        assertEquals("squiggle", strategy.getName());
        assertEquals(VectorizationStrategy.WorkflowType.TONAL_SQUIGGLE, strategy.getWorkflowType());
    }

    @Test
    void cliParsesExplicitSquiggleControls() throws Exception {
        CliParser parser = new CliParser();
        CommandLine cmd = parser.parse(new String[] {
                "-i", "in.png", "-s", "squiggle",
                "--squiggle-density", "1.8",
                "--squiggle-amplitude", "4.5",
                "--squiggle-tone", "0.6",
                "--squiggle-background", "0.9"
        });

        assertEquals(1.8, parser.getSquiggleDensity(cmd), 0.001);
        assertEquals(4.5, parser.getSquiggleAmplitude(cmd), 0.001);
        assertEquals(0.6, parser.getSquiggleTone(cmd), 0.001);
        assertEquals(0.9, parser.getSquiggleBackgroundSuppression(cmd), 0.001);
    }

    @Test
    void densityControlChangesPathCountPredictably() {
        BufferedImage image = whiteImage(90, 70);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillOval(24, 12, 42, 46);
        g.dispose();

        int sparse = TonalSquiggleProcessor.process(image,
                TonalSquiggleProcessor.Options.defaults().withDensity(0.7)).size();
        int dense = TonalSquiggleProcessor.process(image,
                TonalSquiggleProcessor.Options.defaults().withDensity(1.6)).size();

        assertTrue(dense > sparse, "higher density should produce more plot paths");
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
