package org.trostheide.gantry.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageArtValidationCliTest {

    @TempDir
    Path tmp;

    @Test
    void aggregatesMetricsSidecarsIntoComparableBakeoffReport() throws Exception {
        Path sketch = metrics("sketch.metrics.json", "sketch.json", 4, 0.25, 0, 12.0, "0:12", "[]");
        Path needles = metrics("needles.metrics.json", "needles.json", 180, 0.72, 8, 90.0, "1:30", """
                [{"code":"HIGH_TRAVEL_RATIO","message":"travel","value":0.72,"threshold":0.5},
                 {"code":"TINY_SEGMENTS","message":"tiny","value":8,"threshold":0.5}]
                """);
        Path report = tmp.resolve("image-art-validation.json");

        ImageArtValidationCli.main(new String[] {
                "-o", report.toString(), sketch.toString(), needles.toString()});

        JsonNode json = new ObjectMapper().readTree(Files.readString(report));
        assertEquals(2, json.at("/summary/artifactCount").asInt());
        assertEquals("sketch", json.at("/summary/lowestTravelRatio").asText());
        assertEquals("sketch", json.at("/summary/fastestByEstimatedTime").asText());
        assertEquals("sketch", json.at("/summary/fewestTinySegments").asText());
        assertEquals(2, json.at("/summary/warningCount").asInt());

        JsonNode first = json.get("artifacts").get(0);
        assertEquals("sketch", first.get("id").asText());
        assertEquals("sketch.json", first.get("commandFile").asText());
        assertEquals(4, first.get("commands").asInt());
        assertTrue(first.get("warningCodes").isArray(), "warning codes should be compact for review queues");
        assertEquals(0, first.get("warningCodes").size());

        JsonNode second = json.get("artifacts").get(1);
        assertEquals("needles", second.get("id").asText());
        assertEquals("HIGH_TRAVEL_RATIO", second.get("warningCodes").get(0).asText());
        assertEquals("TINY_SEGMENTS", second.get("warningCodes").get(1).asText());
    }

    private Path metrics(String fileName, String commandFile, int commands, double travelRatio,
                         int tinySegments, double estimatedSeconds, String formatted,
                         String warningsJson) throws Exception {
        Path path = tmp.resolve(fileName);
        Files.writeString(path, """
                {
                  "commandFile": "%s",
                  "layers": 1,
                  "commands": %d,
                  "strokes": %d,
                  "points": %d,
                  "drawDistanceMm": 100.0,
                  "travelDistanceMm": %.4f,
                  "travelRatio": %.4f,
                  "tinySegments": %d,
                  "bounds": {"x": 0, "y": 0, "width": 100, "height": 100},
                  "plotTime": {"feedRateDraw": 1200, "feedRateTravel": 2400, "penDownDelayMillis": 100, "estimatedSeconds": %.4f, "formatted": "%s"},
                  "warnings": %s
                }
                """.formatted(commandFile, commands, commands / 2, commands * 2,
                travelRatio * 100.0, travelRatio, tinySegments, estimatedSeconds, formatted, warningsJson));
        return path;
    }
}
