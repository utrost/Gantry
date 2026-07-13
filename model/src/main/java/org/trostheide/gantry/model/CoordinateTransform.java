package org.trostheide.gantry.model;

/**
 * Single source of truth for coordinate transforms: rotation, axis swap/invert,
 * canvas alignment, and the interactive positioning overlay.
 */
public class CoordinateTransform {

    /**
     * Transform a logical input coordinate to physical machine coordinates.
     * Pipeline: rotate -> swap -> invertX -> invertY
     *
     * @param contentBounds {minX, maxX, minY, maxY} or null
     */
    public static double[] transformPoint(double x, double y,
            boolean swapXY, boolean invertX, boolean invertY,
            double maxX, double maxY,
            int dataRotation, double[] contentBounds) {

        // 1. Rotate around content center
        if (dataRotation != 0 && contentBounds != null) {
            double cx = (contentBounds[0] + contentBounds[1]) / 2.0;
            double cy = (contentBounds[2] + contentBounds[3]) / 2.0;
            double dx = x - cx, dy = y - cy;
            switch (dataRotation) {
                case 90:  { double t = dx; dx = -dy; dy = t; break; }
                case 180: { dx = -dx; dy = -dy; break; }
                case 270: { double t = dx; dx = dy; dy = -t; break; }
            }
            x = dx + cx;
            y = dy + cy;
        }

        // 2. Swap
        if (swapXY) {
            double t = x; x = y; y = t;
        }

        // 3. Invert X
        if (invertX) x = maxX - x;

        // 4. Invert Y
        if (invertY) y = maxY - y;

        return new double[] { x, y };
    }

    /**
     * Apply the interactive overlay transform in raw content space: mirror,
     * quarter-turn rotation, uniform scale and translation, all about the
     * content center (cx, cy). Shared by the live preview and the JSON baking
     * so they stay identical.
     *
     * @param rotation mirror-free quarter turn in degrees (0, 90, 180, 270)
     * @param mirror   horizontal flip (negate X about the center)
     */
    public static double[] applyOverlayRaw(double x, double y,
            double cx, double cy, double scale,
            double offsetX, double offsetY,
            int rotation, boolean mirror) {

        double dx = x - cx;
        double dy = y - cy;

        if (mirror) dx = -dx;

        switch (((rotation % 360) + 360) % 360) {
            case 90:  { double t = dx; dx = -dy; dy = t; break; }
            case 180: { dx = -dx; dy = -dy; break; }
            case 270: { double t = dx; dx = dy; dy = -t; break; }
        }

        dx *= scale;
        dy *= scale;

        return new double[] { dx + cx + offsetX, dy + cy + offsetY };
    }

    /**
     * Inverse of transformPoint: motor coordinates back to logical input coordinates.
     * Reverses: invertY -> invertX -> swap -> rotate(-θ)
     */
    public static double[] inverseTransformPoint(double x, double y,
            boolean swapXY, boolean invertX, boolean invertY,
            double maxX, double maxY,
            int dataRotation, double[] contentBounds) {

        // Reverse step 4: un-invert Y
        if (invertY) y = maxY - y;

        // Reverse step 3: un-invert X
        if (invertX) x = maxX - x;

        // Reverse step 2: un-swap
        if (swapXY) {
            double t = x; x = y; y = t;
        }

        // Reverse step 1: rotate by negative angle
        if (dataRotation != 0 && contentBounds != null) {
            double cx = (contentBounds[0] + contentBounds[1]) / 2.0;
            double cy = (contentBounds[2] + contentBounds[3]) / 2.0;
            double dx = x - cx, dy = y - cy;
            int reverseRot = (360 - dataRotation) % 360;
            switch (reverseRot) {
                case 90:  { double t = dx; dx = -dy; dy = t; break; }
                case 180: { dx = -dx; dy = -dy; break; }
                case 270: { double t = dx; dx = dy; dy = -t; break; }
            }
            x = dx + cx;
            y = dy + cy;
        }

        return new double[] { x, y };
    }

    /**
     * Calculate alignment offset to position content on the machine canvas.
     * Alignment names always refer to visible canvas corners. The content is first
     * projected through the effective axis mapping, origin corner and optional final
     * Flip Y; the resulting screen displacement is then converted back to the motor-space
     * offset consumed by the plot pipeline.
     *
     * @param canvasAlign  "top-left", "top-right", "bottom-left", "bottom-right", "center"
     * @param contentBounds {minX, maxX, minY, maxY}
     */
    public static double[] calculateAlignmentOffset(
            String canvasAlign, double[] contentBounds,
            double machineW, double machineH,
            boolean swapXY, boolean invertX, boolean invertY,
            int dataRotation, boolean originRight, boolean originBottom,
            boolean flipY,
            double paddingX, double paddingY) {

        double minX = contentBounds[0], maxX = contentBounds[1];
        double minY = contentBounds[2], maxY = contentBounds[3];

        double[][] corners = {
            {minX, minY}, {maxX, minY}, {minX, maxY}, {maxX, maxY}
        };

        double sMinX = Double.MAX_VALUE, sMaxX = -Double.MAX_VALUE;
        double sMinY = Double.MAX_VALUE, sMaxY = -Double.MAX_VALUE;

        for (double[] c : corners) {
            double[] t = transformPoint(c[0], c[1],
                    swapXY, invertX, invertY, machineW, machineH,
                    dataRotation, contentBounds);
            if (flipY) {
                t[1] = machineH - t[1];
            }
            double[] s = physicalToScreen(t[0], t[1], swapXY,
                    originRight, originBottom, machineW, machineH);
            sMinX = Math.min(sMinX, s[0]);
            sMaxX = Math.max(sMaxX, s[0]);
            sMinY = Math.min(sMinY, s[1]);
            sMaxY = Math.max(sMaxY, s[1]);
        }

        double displayW = swapXY ? machineH : machineW;
        double displayH = swapXY ? machineW : machineH;
        double targetLeft = paddingX;
        double targetRight = displayW - paddingX;
        double targetTop = paddingY;
        double targetBottom = displayH - paddingY;

        double screenOffsetX = 0, screenOffsetY = 0;

        // Normalize to lowercase for matching
        String align = canvasAlign.toLowerCase().replace(" ", "-");
        switch (align) {
            case "top-left":
            case "top left":
                screenOffsetX = targetLeft - sMinX;
                screenOffsetY = targetTop - sMinY;
                break;
            case "top-right":
            case "top right":
                screenOffsetX = targetRight - sMaxX;
                screenOffsetY = targetTop - sMinY;
                break;
            case "bottom-left":
            case "bottom left":
                screenOffsetX = targetLeft - sMinX;
                screenOffsetY = targetBottom - sMaxY;
                break;
            case "bottom-right":
            case "bottom right":
                screenOffsetX = targetRight - sMaxX;
                screenOffsetY = targetBottom - sMaxY;
                break;
            case "center":
                screenOffsetX = displayW / 2.0 - (sMinX + sMaxX) / 2.0;
                screenOffsetY = displayH / 2.0 - (sMinY + sMaxY) / 2.0;
                break;
        }

        // Convert the desired screen-space displacement back to the motor-space offset
        // that PlotService adds before its optional final Flip Y operation.
        double motorOffsetX;
        double motorOffsetY;
        if (swapXY) {
            motorOffsetY = originRight ? -screenOffsetX : screenOffsetX;
            motorOffsetX = originBottom ? -screenOffsetY : screenOffsetY;
        } else {
            motorOffsetX = originRight ? -screenOffsetX : screenOffsetX;
            motorOffsetY = originBottom ? -screenOffsetY : screenOffsetY;
        }
        if (flipY) {
            motorOffsetY = -motorOffsetY;
        }
        return new double[] { motorOffsetX, motorOffsetY };
    }

    /**
     * Map motor coordinates to screen coordinates given the origin corner.
     * Screen (0,0) is always top-left, X right, Y down.
     * Motor (0,0) is at the machine's origin corner.
     *
     * @param axisSwap  true when portrait display requires swapping motor X/Y to screen Y/X
     * @param machineW  raw machine width (motor X range)
     * @param machineH  raw machine height (motor Y range)
     */
    public static double[] physicalToScreen(double motorX, double motorY,
            boolean axisSwap, boolean originRight, boolean originBottom,
            double machineW, double machineH) {

        if (axisSwap) {
            double screenX = originRight ? (machineH - motorY) : motorY;
            double screenY = originBottom ? (machineW - motorX) : motorX;
            return new double[] { screenX, screenY };
        }
        double screenX = originRight ? (machineW - motorX) : motorX;
        double screenY = originBottom ? (machineH - motorY) : motorY;
        return new double[] { screenX, screenY };
    }

    /** Exact inverse of {@link #physicalToScreen}. */
    public static double[] screenToPhysical(double screenX, double screenY,
            boolean axisSwap, boolean originRight, boolean originBottom,
            double machineW, double machineH) {

        if (axisSwap) {
            double motorY = originRight ? (machineH - screenX) : screenX;
            double motorX = originBottom ? (machineW - screenY) : screenY;
            return new double[] { motorX, motorY };
        }
        double motorX = originRight ? (machineW - screenX) : screenX;
        double motorY = originBottom ? (machineH - screenY) : screenY;
        return new double[] { motorX, motorY };
    }
}
