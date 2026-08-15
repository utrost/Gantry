package org.trostheide.gantry.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates multiple {@link SvgImportCli --metrics} sidecars into one compact
 * comparison report for image-art validation runs.
 */
public final class ImageArtValidationCli {

    private ImageArtValidationCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || contains(args, "--help") || contains(args, "-h")) {
            printUsage();
            return;
        }

        Path output = null;
        List<Path> metricsFiles = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("-o".equals(arg) || "--output".equals(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException(arg + " requires a report path");
                }
                output = Path.of(args[++i]);
            } else {
                metricsFiles.add(Path.of(arg));
            }
        }
        if (output == null) {
            throw new IllegalArgumentException("Missing -o/--output report path");
        }
        if (metricsFiles.isEmpty()) {
            throw new IllegalArgumentException("Provide at least one metrics JSON sidecar");
        }

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode report = buildReport(mapper, metricsFiles);
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        System.out.printf("Wrote image-art validation report for %d artifact(s) to %s%n",
                metricsFiles.size(), output);
    }

    static ObjectNode buildReport(ObjectMapper mapper, List<Path> metricsFiles) throws Exception {
        ObjectNode report = mapper.createObjectNode();
        ArrayNode artifacts = mapper.createArrayNode();

        String lowestTravel = null;
        double lowestTravelRatio = Double.POSITIVE_INFINITY;
        String fastest = null;
        double fastestSeconds = Double.POSITIVE_INFINITY;
        String fewestTiny = null;
        int fewestTinySegments = Integer.MAX_VALUE;
        int warningCount = 0;

        for (Path metricsFile : metricsFiles) {
            JsonNode metrics = mapper.readTree(metricsFile.toFile());
            String id = artifactId(metricsFile);
            ObjectNode artifact = mapper.createObjectNode();
            artifact.put("id", id);
            artifact.put("metricsFile", metricsFile.getFileName().toString());
            copyText(metrics, artifact, "commandFile");
            copyInt(metrics, artifact, "layers");
            copyInt(metrics, artifact, "commands");
            copyInt(metrics, artifact, "strokes");
            copyInt(metrics, artifact, "points");
            copyDouble(metrics, artifact, "drawDistanceMm");
            copyDouble(metrics, artifact, "travelDistanceMm");
            copyDouble(metrics, artifact, "travelRatio");
            copyInt(metrics, artifact, "tinySegments");
            if (metrics.hasNonNull("plotTime")) {
                ObjectNode plotTime = artifact.putObject("plotTime");
                plotTime.put("estimatedSeconds", metrics.at("/plotTime/estimatedSeconds").asDouble());
                plotTime.put("formatted", metrics.at("/plotTime/formatted").asText());
            }

            ArrayNode warningCodes = artifact.putArray("warningCodes");
            JsonNode warnings = metrics.get("warnings");
            if (warnings != null && warnings.isArray()) {
                for (JsonNode warning : warnings) {
                    if (warning.hasNonNull("code")) {
                        warningCodes.add(warning.get("code").asText());
                        warningCount++;
                    }
                }
            }
            artifacts.add(artifact);

            double travelRatio = metrics.path("travelRatio").asDouble(Double.POSITIVE_INFINITY);
            if (travelRatio < lowestTravelRatio) {
                lowestTravelRatio = travelRatio;
                lowestTravel = id;
            }
            double seconds = metrics.at("/plotTime/estimatedSeconds").asDouble(Double.POSITIVE_INFINITY);
            if (seconds < fastestSeconds) {
                fastestSeconds = seconds;
                fastest = id;
            }
            int tinySegments = metrics.path("tinySegments").asInt(Integer.MAX_VALUE);
            if (tinySegments < fewestTinySegments) {
                fewestTinySegments = tinySegments;
                fewestTiny = id;
            }
        }

        report.set("artifacts", artifacts);
        ObjectNode summary = report.putObject("summary");
        summary.put("artifactCount", artifacts.size());
        summary.put("warningCount", warningCount);
        if (lowestTravel != null) {
            summary.put("lowestTravelRatio", lowestTravel);
        }
        if (fastest != null) {
            summary.put("fastestByEstimatedTime", fastest);
        }
        if (fewestTiny != null) {
            summary.put("fewestTinySegments", fewestTiny);
        }
        return report;
    }

    private static String artifactId(Path metricsFile) {
        String name = metricsFile.getFileName().toString();
        if (name.endsWith(".metrics.json")) {
            return name.substring(0, name.length() - ".metrics.json".length());
        }
        if (name.endsWith(".json")) {
            return name.substring(0, name.length() - ".json".length());
        }
        return name;
    }

    private static void copyText(JsonNode source, ObjectNode target, String field) {
        if (source.hasNonNull(field)) {
            target.put(field, source.get(field).asText());
        }
    }

    private static void copyInt(JsonNode source, ObjectNode target, String field) {
        if (source.hasNonNull(field)) {
            target.put(field, source.get(field).asInt());
        }
    }

    private static void copyDouble(JsonNode source, ObjectNode target, String field) {
        if (source.hasNonNull(field)) {
            target.put(field, source.get(field).asDouble());
        }
    }

    private static boolean contains(String[] args, String token) {
        for (String arg : args) {
            if (token.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void printUsage() {
        System.out.println("""
                Gantry image-art validation — aggregate plot metrics into a comparison report.

                Usage:
                  ImageArtValidationCli -o report.json <mode.metrics.json>...

                Input files are JSON sidecars written by SvgImportCli --metrics, typically
                from chained VectorizeCli image-art runs. Artifact ids are inferred from
                file names such as sketch.metrics.json -> sketch.
                """);
    }
}
