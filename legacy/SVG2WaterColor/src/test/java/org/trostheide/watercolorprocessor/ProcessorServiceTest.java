package org.trostheide.watercolorprocessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.trostheide.watercolorprocessor.dto.Bounds;
import org.trostheide.watercolorprocessor.dto.Point;
import org.trostheide.watercolorprocessor.dto.ProcessorOutput;
import org.trostheide.watercolorprocessor.dto.command.Command;
import org.trostheide.watercolorprocessor.dto.command.RefillCommand;

import java.awt.geom.AffineTransform;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProcessorServiceTest {

    private ProcessorService service;

    @BeforeEach
    void setUp() {
        service = new ProcessorService();
    }

    // --- Helper method tests ---

    @Nested
    class DistanceTests {
        @Test
        void distanceBetweenSamePointIsZero() {
            Point p = new Point(5.0, 5.0);
            assertEquals(0.0, service.distance(p, p), 1e-9);
        }

        @Test
        void distanceHorizontal() {
            Point a = new Point(0, 0);
            Point b = new Point(3, 0);
            assertEquals(3.0, service.distance(a, b), 1e-9);
        }

        @Test
        void distanceVertical() {
            Point a = new Point(0, 0);
            Point b = new Point(0, 4);
            assertEquals(4.0, service.distance(a, b), 1e-9);
        }

        @Test
        void distanceDiagonal345() {
            Point a = new Point(0, 0);
            Point b = new Point(3, 4);
            assertEquals(5.0, service.distance(a, b), 1e-9);
        }

        @Test
        void distanceIsSymmetric() {
            Point a = new Point(1.5, 2.7);
            Point b = new Point(8.3, 11.1);
            assertEquals(service.distance(a, b), service.distance(b, a), 1e-9);
        }

        @Test
        void distanceWithNegativeCoordinates() {
            Point a = new Point(-5, -5);
            Point b = new Point(-2, -1);
            assertEquals(5.0, service.distance(a, b), 1e-9);
        }
    }

    @Nested
    class InterpolateTests {
        @Test
        void interpolateAtZeroReturnsStart() {
            Point start = new Point(0, 0);
            Point end = new Point(10, 0);
            Point result = service.interpolate(start, end, 0, 10);
            assertEquals(0.0, result.x(), 1e-9);
            assertEquals(0.0, result.y(), 1e-9);
        }

        @Test
        void interpolateAtFullDistReturnsEnd() {
            Point start = new Point(0, 0);
            Point end = new Point(10, 0);
            Point result = service.interpolate(start, end, 10, 10);
            assertEquals(10.0, result.x(), 1e-9);
            assertEquals(0.0, result.y(), 1e-9);
        }

        @Test
        void interpolateAtMidpoint() {
            Point start = new Point(0, 0);
            Point end = new Point(10, 10);
            Point result = service.interpolate(start, end, 5 * Math.sqrt(2), 10 * Math.sqrt(2));
            assertEquals(5.0, result.x(), 1e-9);
            assertEquals(5.0, result.y(), 1e-9);
        }

        @Test
        void interpolateWithZeroTotalDistReturnsStart() {
            Point start = new Point(3, 4);
            Point end = new Point(3, 4);
            Point result = service.interpolate(start, end, 0, 0);
            assertEquals(3.0, result.x(), 1e-9);
            assertEquals(4.0, result.y(), 1e-9);
        }

        @Test
        void interpolateQuarterWay() {
            Point start = new Point(0, 0);
            Point end = new Point(100, 0);
            Point result = service.interpolate(start, end, 25, 100);
            assertEquals(25.0, result.x(), 1e-9);
            assertEquals(0.0, result.y(), 1e-9);
        }
    }

    @Nested
    class BoundsBuilderTests {
        @Test
        void emptyBoundsBuilderReturnsEmptyBounds() {
            var builder = new ProcessorService.BoundsBuilder();
            Bounds bounds = builder.build();
            assertEquals(Double.MAX_VALUE, bounds.minX());
        }

        @Test
        void singlePointBounds() {
            var builder = new ProcessorService.BoundsBuilder();
            builder.add(5.0, 10.0);
            Bounds bounds = builder.build();
            assertEquals(5.0, bounds.minX(), 1e-9);
            assertEquals(10.0, bounds.minY(), 1e-9);
            assertEquals(5.0, bounds.maxX(), 1e-9);
            assertEquals(10.0, bounds.maxY(), 1e-9);
        }

        @Test
        void multiplePointsBounds() {
            var builder = new ProcessorService.BoundsBuilder();
            builder.add(1.0, 2.0);
            builder.add(5.0, 8.0);
            builder.add(3.0, 4.0);
            Bounds bounds = builder.build();
            assertEquals(1.0, bounds.minX(), 1e-9);
            assertEquals(2.0, bounds.minY(), 1e-9);
            assertEquals(5.0, bounds.maxX(), 1e-9);
            assertEquals(8.0, bounds.maxY(), 1e-9);
        }

        @Test
        void negativeCoordinateBounds() {
            var builder = new ProcessorService.BoundsBuilder();
            builder.add(-10.0, -20.0);
            builder.add(5.0, 8.0);
            Bounds bounds = builder.build();
            assertEquals(-10.0, bounds.minX(), 1e-9);
            assertEquals(-20.0, bounds.minY(), 1e-9);
            assertEquals(5.0, bounds.maxX(), 1e-9);
            assertEquals(8.0, bounds.maxY(), 1e-9);
        }

        @Test
        void resetClearsBounds() {
            var builder = new ProcessorService.BoundsBuilder();
            builder.add(1.0, 2.0);
            builder.reset();
            Bounds bounds = builder.build();
            // After reset, should return empty bounds
            assertEquals(Double.MAX_VALUE, bounds.minX());
        }
    }

    @Nested
    class PaperFormatTests {
        @Test
        void fromStringA4() {
            var format = ProcessorService.PaperFormat.fromString("A4");
            assertNotNull(format);
            assertEquals(210, format.width());
            assertEquals(297, format.height());
        }

        @Test
        void fromStringA3() {
            var format = ProcessorService.PaperFormat.fromString("A3");
            assertNotNull(format);
            assertEquals(297, format.width());
            assertEquals(420, format.height());
        }

        @Test
        void fromStringCaseInsensitive() {
            assertNotNull(ProcessorService.PaperFormat.fromString("a4"));
            assertNotNull(ProcessorService.PaperFormat.fromString("xl"));
        }

        @Test
        void fromStringNull() {
            assertNull(ProcessorService.PaperFormat.fromString(null));
        }

        @Test
        void fromStringUnknown() {
            assertNull(ProcessorService.PaperFormat.fromString("B5"));
        }
    }

    @Nested
    class FitToPageTransformTests {
        @Test
        void identityWhenBoundsAreEmpty() {
            Bounds empty = Bounds.empty();
            AffineTransform tx = service.calculateFitToPageTransform(
                    empty, ProcessorService.PaperFormat.A4, 10.0);
            assertTrue(tx.isIdentity());
        }

        @Test
        void fitToPageScalesContent() {
            // Content is 100x100, target A4 (210x297) with 10mm padding
            // Available: 190x277, so scale = min(1.9, 2.77) = 1.9
            Bounds content = new Bounds(0, 0, 100, 100);
            AffineTransform tx = service.calculateFitToPageTransform(
                    content, ProcessorService.PaperFormat.A4, 10.0);
            assertFalse(tx.isIdentity());

            // Transform (0,0) should move to padding + centering offset
            double[] src = {0, 0};
            double[] dst = new double[2];
            tx.transform(src, 0, dst, 0, 1);
            assertTrue(dst[0] >= 10.0, "X should be at least padding");
            assertTrue(dst[1] >= 10.0, "Y should be at least padding");
        }

        @Test
        void fitToPageRespectsAspectRatio() {
            // Wide content (200x50) should be limited by width
            Bounds content = new Bounds(0, 0, 200, 50);
            AffineTransform tx = service.calculateFitToPageTransform(
                    content, ProcessorService.PaperFormat.A4, 10.0);

            double[] topLeft = {0, 0};
            double[] bottomRight = {200, 50};
            double[] dstTL = new double[2];
            double[] dstBR = new double[2];
            tx.transform(topLeft, 0, dstTL, 0, 1);
            tx.transform(bottomRight, 0, dstBR, 0, 1);

            double transformedWidth = dstBR[0] - dstTL[0];
            double transformedHeight = dstBR[1] - dstTL[1];

            // Aspect ratio should be preserved: 4:1
            assertEquals(4.0, transformedWidth / transformedHeight, 0.01);
        }

        @Test
        void fitToPageWithExcessivePaddingReturnsIdentity() {
            Bounds content = new Bounds(0, 0, 100, 100);
            AffineTransform tx = service.calculateFitToPageTransform(
                    content, ProcessorService.PaperFormat.A5, 200.0);
            assertTrue(tx.isIdentity());
        }
    }

    // --- End-to-end processing tests ---

    @Nested
    class EndToEndTests {
        @TempDir
        Path tempDir;

        private File getTestResource(String name) {
            URL url = getClass().getClassLoader().getResource(name);
            assertNotNull(url, "Test resource not found: " + name);
            return new File(url.getFile());
        }

        @Test
        void processSimpleSvgProducesValidOutput() throws Exception {
            File input = getTestResource("test_simple.svg");
            File output = tempDir.resolve("output.json").toFile();

            service.process(input, output, 500, "default_station", 0.5, null, 10.0, false);

            assertTrue(output.exists(), "Output file should be created");
            assertTrue(output.length() > 0, "Output should not be empty");

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            ProcessorOutput result = mapper.readValue(output, ProcessorOutput.class);

            assertNotNull(result.metadata());
            assertEquals("mm", result.metadata().units());
            assertEquals("test_simple.svg", result.metadata().source());
            assertFalse(result.layers().isEmpty(), "Should have at least one layer");
            assertTrue(result.metadata().totalCommands() > 0, "Should have commands");
        }

        @Test
        void processLayeredSvgProducesMultipleLayers() throws Exception {
            File input = getTestResource("test_layers.svg");
            File output = tempDir.resolve("output_layers.json").toFile();

            service.process(input, output, 500, "default_station", 0.5, null, 10.0, false);

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            ProcessorOutput result = mapper.readValue(output, ProcessorOutput.class);

            assertEquals(2, result.layers().size(), "Should have two layers");
            // Layers should have generic IDs
            assertEquals("Layer1", result.layers().get(0).stationId());
            assertEquals("Layer2", result.layers().get(1).stationId());
        }

        @Test
        void processWithFitToA4ScalesOutput() throws Exception {
            File input = getTestResource("test_simple.svg");
            File output = tempDir.resolve("output_a4.json").toFile();

            service.process(input, output, 500, "default_station", 0.5, "A4", 10.0, false);

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            ProcessorOutput result = mapper.readValue(output, ProcessorOutput.class);

            Bounds bounds = result.metadata().bounds();
            // With A4 fit and 10mm padding, all coords should be within A4 bounds
            assertTrue(bounds.minX() >= 0, "minX should be >= 0, was " + bounds.minX());
            assertTrue(bounds.minY() >= 0, "minY should be >= 0, was " + bounds.minY());
            assertTrue(bounds.maxX() <= 210, "maxX should be <= 210, was " + bounds.maxX());
            assertTrue(bounds.maxY() <= 297, "maxY should be <= 297, was " + bounds.maxY());
        }

        @Test
        void processWithMirrorFlipsX() throws Exception {
            File input = getTestResource("test_simple.svg");
            File outputNormal = tempDir.resolve("normal.json").toFile();
            File outputMirror = tempDir.resolve("mirror.json").toFile();

            service.process(input, outputNormal, 500, "default_station", 0.5, "A4", 10.0, false);

            // Reset service for second run (commandCounter reset is now built-in)
            service = new ProcessorService();
            service.process(input, outputMirror, 500, "default_station", 0.5, "A4", 10.0, true);

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            ProcessorOutput normal = mapper.readValue(outputNormal, ProcessorOutput.class);
            ProcessorOutput mirror = mapper.readValue(outputMirror, ProcessorOutput.class);

            assertEquals(normal.layers().size(), mirror.layers().size());
            assertEquals(normal.metadata().totalCommands(), mirror.metadata().totalCommands());
        }

        @Test
        void eachLayerStartsWithRefill() throws Exception {
            File input = getTestResource("test_layers.svg");
            File output = tempDir.resolve("output_refill.json").toFile();

            service.process(input, output, 500, "default_station", 0.5, null, 10.0, false);

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            ProcessorOutput result = mapper.readValue(output, ProcessorOutput.class);

            for (var layer : result.layers()) {
                assertFalse(layer.commands().isEmpty());
                Command first = layer.commands().get(0);
                assertInstanceOf(RefillCommand.class, first,
                        "Each layer should start with a REFILL command");
            }
        }

        @Test
        void refillInsertedWhenExceedingMaxDistance() throws Exception {
            File input = getTestResource("test_simple.svg");
            File output = tempDir.resolve("output_refill_split.json").toFile();

            // Very short max distance forces frequent refills
            service.process(input, output, 5.0, "default_station", 0.5, null, 10.0, false);

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            ProcessorOutput result = mapper.readValue(output, ProcessorOutput.class);

            long refillCount = result.layers().get(0).commands().stream()
                    .filter(c -> c instanceof RefillCommand)
                    .count();

            assertTrue(refillCount > 1,
                    "With max distance of 5mm, there should be multiple refills. Got: " + refillCount);
        }

        @Test
        void commandIdsAreSequential() throws Exception {
            File input = getTestResource("test_simple.svg");
            File output = tempDir.resolve("output_ids.json").toFile();

            service.process(input, output, 500, "default_station", 0.5, null, 10.0, false);

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            ProcessorOutput result = mapper.readValue(output, ProcessorOutput.class);

            int lastId = 0;
            for (var layer : result.layers()) {
                for (Command cmd : layer.commands()) {
                    assertTrue(cmd.getId() > lastId,
                            "Command ID should be sequential. Expected > " + lastId + " but got " + cmd.getId());
                    lastId = cmd.getId();
                }
            }
        }

        @Test
        void commandCounterResetsOnNewProcess() throws Exception {
            File input = getTestResource("test_simple.svg");
            File output1 = tempDir.resolve("output1.json").toFile();
            File output2 = tempDir.resolve("output2.json").toFile();

            service.process(input, output1, 500, "default_station", 0.5, null, 10.0, false);
            service.process(input, output2, 500, "default_station", 0.5, null, 10.0, false);

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            ProcessorOutput result1 = mapper.readValue(output1, ProcessorOutput.class);
            ProcessorOutput result2 = mapper.readValue(output2, ProcessorOutput.class);

            // Both runs should start IDs from 1
            int firstId1 = result1.layers().get(0).commands().get(0).getId();
            int firstId2 = result2.layers().get(0).commands().get(0).getId();
            assertEquals(firstId1, firstId2,
                    "Command IDs should reset between process() calls");
        }

        @Test
        void processTransformedSvg() throws Exception {
            File input = getTestResource("test_transform.svg");
            File output = tempDir.resolve("output_transform.json").toFile();

            service.process(input, output, 500, "default_station", 0.5, null, 10.0, false);

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            ProcessorOutput result = mapper.readValue(output, ProcessorOutput.class);

            // The rect at (10,10) with transform translate(50,50) should produce
            // points around (60,60)
            Bounds bounds = result.metadata().bounds();
            assertTrue(bounds.minX() >= 55, "Translated rect minX should be ~60, was " + bounds.minX());
            assertTrue(bounds.minY() >= 55, "Translated rect minY should be ~60, was " + bounds.minY());
        }

        @Test
        void processNestedTransformAccumulatesParent() throws Exception {
            File input = getTestResource("test_nested_transform.svg");
            File output = tempDir.resolve("output_nested.json").toFile();

            service.process(input, output, 500, "default_station", 0.5, null, 10.0, false);

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            ProcessorOutput result = mapper.readValue(output, ProcessorOutput.class);

            // The rect at (0,0) with own translate(10,10) inside <g translate(100,100)>
            // should produce points around (110,110)
            Bounds bounds = result.metadata().bounds();
            assertTrue(bounds.minX() >= 105, "Nested transform minX should be ~110, was " + bounds.minX());
            assertTrue(bounds.minY() >= 105, "Nested transform minY should be ~110, was " + bounds.minY());
            assertTrue(bounds.maxX() <= 125, "Nested transform maxX should be ~120, was " + bounds.maxX());
            assertTrue(bounds.maxY() <= 125, "Nested transform maxY should be ~120, was " + bounds.maxY());
        }
    }

    // --- JSON serialization round-trip ---

    @Nested
    class SerializationTests {
        @Test
        void processOutputRoundTrips(@TempDir Path tempDir) throws Exception {
            File input = new File(getClass().getClassLoader().getResource("test_simple.svg").getFile());
            File output = tempDir.resolve("roundtrip.json").toFile();

            service.process(input, output, 200, "test_station", 0.5, null, 10.0, false);

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            ProcessorOutput parsed = mapper.readValue(output, ProcessorOutput.class);

            // Re-serialize and parse again
            File output2 = tempDir.resolve("roundtrip2.json").toFile();
            mapper.writeValue(output2, parsed);
            ProcessorOutput parsed2 = mapper.readValue(output2, ProcessorOutput.class);

            assertEquals(parsed.metadata().totalCommands(), parsed2.metadata().totalCommands());
            assertEquals(parsed.layers().size(), parsed2.layers().size());
            assertEquals(parsed.metadata().units(), parsed2.metadata().units());
        }
    }
}
