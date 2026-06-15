package org.trostheide.gantry.svgtoolbox;

import org.w3c.dom.Document;

/**
 * Contract for SVG-to-SVG processing modules.
 */
public interface Processor {
    /**
     * Modifies the SVG document in-place.
     */
    void process(Document doc, Config config);
}
