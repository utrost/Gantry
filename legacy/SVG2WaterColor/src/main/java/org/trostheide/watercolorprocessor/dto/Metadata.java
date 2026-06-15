package org.trostheide.watercolorprocessor.dto;

import java.time.Instant;

/**
 * Header information for the command file.
 */
public record Metadata(
        String source,
        Instant generatedAt,
        String stationId,
        String units,
        int totalCommands,
        Bounds bounds
) {}