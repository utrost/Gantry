package org.trostheide.svgtoolbox;

import org.w3c.dom.Document;

/**
 * Contract for SVG processing modules.
 */
public interface Processor {
    /**
     * Modifies the SVG document in-place.
     */
    void process(Document doc, Config config);
}