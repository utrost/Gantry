package org.trostheide.watercolorprocessor.gui;

import java.util.Map;

/**
 * Top-level application configuration, persisted as config.json.
 */
public record AppConfig(GeneralSettings general, Map<String, StationConfig> stations) {
}
