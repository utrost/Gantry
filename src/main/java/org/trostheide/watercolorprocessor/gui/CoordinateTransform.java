package org.trostheide.watercolorprocessor.gui;

/**
 * Single source of truth for coordinate transforms in Java,
 * mirroring driver/transforms.py exactly.
 */
public class CoordinateTransform {

    /**
     * Transform a logical input coordinate to physical machine coordinates.
     * Pipeline: rotate -> swap -> invertX -> invertY
     * Mirrors transforms.py transform_point.
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
     * Mirrors transforms.py calculate_alignment_offset.
     *
     * @param canvasAlign  "top-left", "top-right", "bottom-left", "bottom-right", "center"
     * @param contentBounds {minX, maxX, minY, maxY}
     */
    public static double[] calculateAlignmentOffset(
            String canvasAlign, double[] contentBounds,
            double machineW, double machineH,
            boolean swapXY, boolean invertX, boolean invertY,
            int dataRotation, boolean originRight,
            double paddingX, double paddingY) {

        double minX = contentBounds[0], maxX = contentBounds[1];
        double minY = contentBounds[2], maxY = contentBounds[3];

        double[][] corners = {
            {minX, minY}, {maxX, minY}, {minX, maxY}, {maxX, maxY}
        };

        double tMinX = Double.MAX_VALUE, tMaxX = -Double.MAX_VALUE;
        double tMinY = Double.MAX_VALUE, tMaxY = -Double.MAX_VALUE;

        for (double[] c : corners) {
            double[] t = transformPoint(c[0], c[1],
                    swapXY, invertX, invertY, machineW, machineH,
                    dataRotation, contentBounds);
            tMinX = Math.min(tMinX, t[0]);
            tMaxX = Math.max(tMaxX, t[0]);
            tMinY = Math.min(tMinY, t[1]);
            tMaxY = Math.max(tMaxY, t[1]);
        }

        double contentLeftEdge, contentRightEdge, targetLeft, targetRight;
        if (originRight) {
            contentRightEdge = tMinX;
            contentLeftEdge = tMaxX;
            targetLeft = machineW - paddingX;
            targetRight = paddingX;
        } else {
            contentLeftEdge = tMinX;
            contentRightEdge = tMaxX;
            targetLeft = paddingX;
            targetRight = machineW - paddingX;
        }
        double targetTop = paddingY;
        double targetBottom = machineH - paddingY;

        double offsetX = 0, offsetY = 0;

        // Normalize to lowercase for matching
        String align = canvasAlign.toLowerCase().replace(" ", "-");
        switch (align) {
            case "top-left":
            case "top left":
                offsetX = targetLeft - contentLeftEdge;
                offsetY = targetTop - tMinY;
                break;
            case "top-right":
            case "top right":
                offsetX = targetRight - contentRightEdge;
                offsetY = targetTop - tMinY;
                break;
            case "bottom-left":
            case "bottom left":
                offsetX = targetLeft - contentLeftEdge;
                offsetY = targetBottom - tMaxY;
                break;
            case "bottom-right":
            case "bottom right":
                offsetX = targetRight - contentRightEdge;
                offsetY = targetBottom - tMaxY;
                break;
            case "center":
                double tWidth = tMaxX - tMinX;
                double tHeight = tMaxY - tMinY;
                offsetX = (machineW - tWidth) / 2.0 - tMinX;
                offsetY = (machineH - tHeight) / 2.0 - tMinY;
                break;
        }

        return new double[] { offsetX, offsetY };
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
}
