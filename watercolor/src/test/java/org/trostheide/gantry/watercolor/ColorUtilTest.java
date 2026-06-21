package org.trostheide.gantry.watercolor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorUtilTest {

    @Test
    void parsesSixAndThreeDigitHexWithOrWithoutHash() {
        assertArrayEquals(new int[] {255, 0, 0}, ColorUtil.parseHex("#ff0000"));
        assertArrayEquals(new int[] {255, 0, 0}, ColorUtil.parseHex("ff0000"));
        assertArrayEquals(new int[] {255, 255, 255}, ColorUtil.parseHex("#fff"));
        assertNull(ColorUtil.parseHex("nonsense"));
        assertNull(ColorUtil.parseHex(null));
    }

    @Test
    void distanceIsZeroForEqualColoursAndOrdersByPerceptualCloseness() {
        assertTrue(ColorUtil.distance("#ff0000", "#ff0000") == 0.0);
        // A near-red is closer to red than a green is.
        assertTrue(ColorUtil.distance("#ff0000", "#fe0101") < ColorUtil.distance("#ff0000", "#00ff00"));
    }
}
