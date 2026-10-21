package org.trostheide.gantry.app.plot;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.util.Set;

/** Portable, versioned machine profiles. UI preferences and execution state stay local. */
public final class MachineSettingsIO {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    private static final Set<String> FIELDS = Set.of("gcode", "machineOrigin", "orientation",
            "invertX", "invertY", "swapXY", "flipY", "canvasAlignment", "dataRotation",
            "paddingX", "paddingY", "stations", "softLimits", "hasLimitSwitches");
    private MachineSettingsIO() { }

    public static void exportSettings(GantryConfig config, File file) throws IOException {
        validate(config);
        ObjectNode root = JSON.createObjectNode();
        root.put("format", "gantry-machine-settings");root.put("version", 1);
        root.set("settings", machineFields(JSON.valueToTree(config)));
        org.trostheide.gantry.model.io.AtomicFiles.write(file,
                temp -> JSON.writerWithDefaultPrettyPrinter().writeValue(temp, root));
    }

    /** Also accepts existing Gantry config.json files; never falls back silently to defaults. */
    public static GantryConfig importSettings(File file, GantryConfig current) throws IOException {
        JsonNode root = JSON.readTree(file);
        require(root != null && root.isObject(), "Expected a machine-settings JSON object.");
        JsonNode settings = root;
        if (root.has("format")) {
            require("gantry-machine-settings".equals(root.path("format").asText()), "Not a Gantry machine-settings file.");
            require(root.path("version").isIntegralNumber() && root.path("version").canConvertToInt() && root.path("version").intValue() == 1,
                    "Unsupported machine-settings version.");
            settings = root.get("settings");
        }
        require(settings != null && settings.isObject() && settings.path("gcode").isObject(),
                "Machine settings must contain a gcode configuration.");
        require(settings.path("gcode").has("machineWidth") && settings.path("gcode").has("machineHeight"),
                "Machine settings must specify bed width and height.");
        // Missing machine fields use Gantry defaults, never values from a different local machine.
        GantryConfig imported = JSON.treeToValue(machineFields(settings), GantryConfig.class);
        validate(imported);
        ObjectNode merged = JSON.valueToTree(current);
        machineFields(JSON.valueToTree(imported)).fields().forEachRemaining(e -> merged.set(e.getKey(), e.getValue()));
        return JSON.treeToValue(merged, GantryConfig.class);
    }

    private static ObjectNode machineFields(JsonNode source) {
        ObjectNode result = JSON.createObjectNode();
        source.fields().forEachRemaining(e -> { if (FIELDS.contains(e.getKey())) result.set(e.getKey(), e.getValue()); });
        return result;
    }

    private static void validate(GantryConfig c) throws IOException {
        require(c.gcode != null, "Missing G-code settings.");
        var g = c.gcode;
        require(g.serialPort != null && !g.serialPort.isBlank(), "Serial port must not be empty.");
        require(g.baudRate > 0, "Baud rate must be positive.");
        require(positive(g.machineWidth) && positive(g.machineHeight), "Machine dimensions must be finite and positive.");
        require(g.feedRateDraw > 0 && g.feedRateTravel > 0, "Drawing and travel speeds must be positive.");
        require(Set.of("servo", "zaxis", "m3m5").contains(g.penMode == null ? "" : g.penMode), "Unknown pen mode.");
        require(g.servoPin >= 0 && g.penServoUp >= 0 && g.penServoUp <= 180 && g.penServoDown >= 0 && g.penServoDown <= 180,
                "Invalid servo pin or pen angles (0–180).");
        require(Double.isFinite(g.zUp) && Double.isFinite(g.zDown), "Pen heights must be finite.");
        require(positive(g.positionPollIntervalSeconds) && g.responseTimeoutSeconds > 0
                && g.bootDelayMillis >= 0 && g.penDownDelayMillis >= 0, "Invalid controller timing values.");
        require(Set.of("Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right").contains(c.machineOrigin == null ? "" : c.machineOrigin), "Unknown machine origin.");
        require("Landscape".equals(c.orientation) || "Portrait".equals(c.orientation), "Unknown orientation.");
        require(c.canvasAlignment == null || Set.of("None", "Top Left", "Top Right", "Bottom Left", "Bottom Right", "Center").contains(c.canvasAlignment), "Unknown artwork alignment.");
        require(c.dataRotation == 0 || c.dataRotation == 90 || c.dataRotation == 180 || c.dataRotation == 270, "Rotation must be 0, 90, 180 or 270 degrees.");
        require(Double.isFinite(c.paddingX) && Double.isFinite(c.paddingY) && c.paddingX >= 0 && c.paddingY >= 0, "Padding must be finite and nonnegative.");
        require(c.stations != null, "Station settings must be an object.");
        for (var entry : c.stations.entrySet()) {
            var station = entry.getValue();
            require(!entry.getKey().isBlank() && station != null, "Invalid station entry.");
            require(Double.isFinite(station.x()) && Double.isFinite(station.y()) && positive(station.swirlRadius()), "Station coordinates and radius must be finite.");
            require(Set.of("simple_dip", "dip_swirl", "rinse").contains(station.behavior() == null ? "" : station.behavior()), "Unknown station behavior.");
        }
    }
    private static boolean positive(double value) { return Double.isFinite(value) && value > 0; }
    private static void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }
}
