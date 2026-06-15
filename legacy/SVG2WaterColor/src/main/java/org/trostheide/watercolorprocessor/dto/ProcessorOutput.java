package org.trostheide.watercolorprocessor.dto;

import java.util.List;

/**
 * Root object for the JSON output file.
 * Contains metadata and a list of layers (multi-color jobs).
 */
public record ProcessorOutput(Metadata metadata, List<Layer> layers) {}