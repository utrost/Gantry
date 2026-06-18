package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.model.CoordinateTransform;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Visualization Panel - Digital Twin of the Physical Plotter.
 * Supports all four origin corners (Top-Left, Top-Right, Bottom-Left, Bottom-Right)
 * and portrait/landscape orientation with automatic axis swap.
 *
 * All coordinate math delegates to {@link CoordinateTransform} (shared with
 * {@link org.trostheide.gantry.app.plot.PlotService}).
 */
public class VisualizationPanel extends JPanel {

    private List<List<Point2D>> allPaths = new ArrayList<>();

    // Current head position (Physical coords). currentX/Y is the *displayed*
    // position, which is eased toward targetX/Y so motion looks smooth even
    // when position updates arrive in discrete steps.
    private double currentX = 0;
    private double currentY = 0;
    private double targetX = 0;
    private double targetY = 0;
    private javax.swing.Timer cursorAnimTimer;

    // Machine Bounds (Fixed by Settings - A3 Portrait default)
    private double machineWidth = 297; // A3 Portrait width (short edge)
    private double machineHeight = 420; // A3 Portrait height (long edge)

    // Raw Content Bounds (from JSON, before rotation)
    private double rawMinX = 0, rawMaxX = 0, rawMinY = 0, rawMaxY = 0;

    // Alignment Offset (calculated based on alignment choice)
    private double alignOffsetX = 0;
    private double alignOffsetY = 0;

    // User Settings
    private String canvasAlignment = "Top Right"; // Default: align to physical origin
    private int dataRotation = 0; // 0, 90, 180, 270 degrees
    private String orientation = "Portrait";

    // Driver Simulation Flags
    // Fully-composited transform (machine origin + portrait-swap + extra flags already
    // folded in by GantryConfig.toPlotSettings()) — the single source of truth shared with
    // jogging and G-code generation, so the preview/cursor and the actual hardware always agree.
    private boolean effSwapXY = false;
    private boolean effInvertX = false;
    private boolean effInvertY = false;
    private double paddingX = 0;
    private double paddingY = 0;

    // Machine origin corner (determines how motor coords map to screen)
    private String machineOrigin = "Top-Right";
    private boolean flipY = false;

    // Overlay transform for interactive drag/resize (in raw content space)
    private double overlayOffsetX = 0, overlayOffsetY = 0;
    private double overlayScale = 1.0;
    private int overlayRotation = 0;       // quarter turns: 0, 90, 180, 270
    private boolean overlayMirror = false; // horizontal flip

    // When true, alignment offset is suppressed (baked coords already encode position)
    private boolean suppressAlignment = false;

    // Cached paint-time values for screen-to-mm inversion
    private double paintScale = 1.0;
    private double paintTx = 0, paintTy = 0;

    // Interactive drag state
    private static final int HANDLE_NONE = -1;
    private static final int HANDLE_MOVE = 0;
    private static final int HANDLE_NW = 1, HANDLE_N = 2, HANDLE_NE = 3;
    private static final int HANDLE_W = 4, HANDLE_E = 5;
    private static final int HANDLE_SW = 6, HANDLE_S = 7, HANDLE_SE = 8;
    private static final double HANDLE_SIZE_PX = 7;

    private int dragHandle = HANDLE_NONE;
    private double dragStartScreenX, dragStartScreenY;
    private double dragStartOverlayOX, dragStartOverlayOY;
    private double dragStartOverlayScale;

    // Listener for overlay changes (notifies the containing panel)
    private Runnable overlayChangeListener;

    public void setOverlayChangeListener(Runnable listener) {
        this.overlayChangeListener = listener;
    }

    private void fireOverlayChange() {
        if (overlayChangeListener != null) overlayChangeListener.run();
    }

    // Refill Stations (loaded from config)
    public record Station(String name, double x, double y) {
    }

    private List<Station> stations = new ArrayList<>();

    public void setStations(List<Station> newStations) {
        this.stations.clear();
        this.stations.addAll(newStations);
        repaint();
    }

    // ----- Overlay accessors -----

    public double getOverlayOffsetX() { return overlayOffsetX; }
    public double getOverlayOffsetY() { return overlayOffsetY; }
    public double getOverlayScale() { return overlayScale; }
    public int getOverlayRotation() { return overlayRotation; }
    public boolean isOverlayMirror() { return overlayMirror; }
    public boolean hasOverlayTransform() {
        return overlayOffsetX != 0 || overlayOffsetY != 0 || overlayScale != 1.0
                || overlayRotation != 0 || overlayMirror;
    }

    /** Rotate the drawing 90° clockwise (interactive overlay), re-clamping to the bed. */
    public void rotateOverlay() {
        overlayRotation = (overlayRotation + 90) % 360;
        clampOverlayToBed();
        repaint();
        fireOverlayChange();
    }

    /** Toggle horizontal mirroring of the drawing (interactive overlay). */
    public void toggleMirror() {
        overlayMirror = !overlayMirror;
        clampOverlayToBed();
        repaint();
        fireOverlayChange();
    }

    public double[] getRawBounds() { return new double[] { rawMinX, rawMaxX, rawMinY, rawMaxY }; }
    public double getAlignOffsetX() { return alignOffsetX; }
    public double getAlignOffsetY() { return alignOffsetY; }
    public boolean getEffectiveSwap() { return effectiveSwap(); }
    public boolean getEffectiveInvertX() { return effectiveInvertX(); }
    public boolean getEffectiveInvertY() { return effectiveInvertY(); }
    public int getDataRotation() { return dataRotation; }
    public double getMachineWidth() { return machineWidth; }
    public double getMachineHeight() { return machineHeight; }

    public void setSuppressAlignment(boolean suppress) {
        this.suppressAlignment = suppress;
        recalculateTransform();
        repaint();
    }

    public void resetOverlay() {
        overlayOffsetX = 0;
        overlayOffsetY = 0;
        overlayScale = 1.0;
        overlayRotation = 0;
        overlayMirror = false;
        suppressAlignment = false;
        repaint();
        fireOverlayChange();
    }

    // ----- Setters -----

    public void setOrientation(String orientation) {
        this.orientation = orientation;
        repaint();
    }

    public void setDataRotation(int degrees) {
        this.dataRotation = degrees;
        recalculateTransform();
        repaint();
    }

    // Alias for compatibility with existing code
    public void setViewRotation(int degrees) {
        setDataRotation(degrees);
    }

    /**
     * Sets the fully-composited swap/invert transform, as computed by
     * {@code GantryConfig.toPlotSettings()} (machine origin + portrait-swap + extra flags
     * already folded in). Must match what jogging/G-code generation use, or the cursor and
     * content preview will move along the wrong axis relative to the real hardware.
     */
    public void setEffectiveAxes(boolean swapXY, boolean invertX, boolean invertY) {
        this.effSwapXY = swapXY;
        this.effInvertX = invertX;
        this.effInvertY = invertY;
        recalculateTransform();
        repaint();
    }

    public void setMachineSize(double width, double height) {
        this.machineWidth = width;
        this.machineHeight = height;
        recalculateTransform();
        repaint();
    }

    public void setCanvasAlignment(String alignment) {
        this.canvasAlignment = alignment;
        recalculateTransform();
        repaint();
    }

    public void setPadding(double x, double y) {
        this.paddingX = x;
        this.paddingY = y;
        recalculateTransform();
        repaint();
    }

    public void setMachineOrigin(String origin) {
        this.machineOrigin = origin;
        recalculateTransform();
        repaint();
    }

    public void setFlipY(boolean flip) {
        this.flipY = flip;
        recalculateTransform();
        repaint();
    }

    private boolean isOriginRight() { return machineOrigin.contains("Right"); }
    private boolean isOriginBottom() { return machineOrigin.contains("Bottom"); }
    private boolean needsAxisSwap() { return isPortrait() && machineWidth > machineHeight; }

    public VisualizationPanel() {
        setBackground(new Color(35, 35, 40));
        TitledBorder border = BorderFactory.createTitledBorder("Live View");
        border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD, 12f));
        setBorder(border);

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (allPaths.isEmpty()) return;
                int handle = hitTestHandle(e.getX(), e.getY());
                if (handle == HANDLE_NONE) return;
                dragHandle = handle;
                dragStartScreenX = e.getX();
                dragStartScreenY = e.getY();
                dragStartOverlayOX = overlayOffsetX;
                dragStartOverlayOY = overlayOffsetY;
                dragStartOverlayScale = overlayScale;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragHandle == HANDLE_NONE) return;
                double dx = e.getX() - dragStartScreenX;
                double dy = e.getY() - dragStartScreenY;
                if (dragHandle == HANDLE_MOVE) {
                    double[] mmDelta = screenDeltaToMm(dx, dy);
                    overlayOffsetX = dragStartOverlayOX + mmDelta[0];
                    overlayOffsetY = dragStartOverlayOY + mmDelta[1];
                } else {
                    handleResize(dragHandle, dx, dy);
                }
                clampOverlayToBed();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragHandle != HANDLE_NONE) {
                    dragHandle = HANDLE_NONE;
                    fireOverlayChange();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (allPaths.isEmpty()) { setCursor(Cursor.getDefaultCursor()); return; }
                int handle = hitTestHandle(e.getX(), e.getY());
                setCursor(cursorForHandle(handle));
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    // ----- Data Loading -----

    /** Loads every DRAW polyline from {@code output}, resetting the cursor and overlay transform. */
    public void loadFromOutput(ProcessorOutput output) {
        allPaths.clear();
        currentX = 0;
        currentY = 0;
        targetX = 0;
        targetY = 0;
        if (cursorAnimTimer != null) {
            cursorAnimTimer.stop();
        }
        overlayOffsetX = 0;
        overlayOffsetY = 0;
        overlayScale = 1.0;
        overlayRotation = 0;
        overlayMirror = false;
        suppressAlignment = false;

        for (Layer layer : output.layers()) {
            for (Command cmd : layer.commands()) {
                if (cmd instanceof DrawCommand draw) {
                    List<Point2D> stroke = new ArrayList<>();
                    for (org.trostheide.gantry.model.Point p : draw.points) {
                        stroke.add(new Point2D(p.x(), p.y()));
                    }
                    if (!stroke.isEmpty()) {
                        allPaths.add(stroke);
                    }
                }
            }
        }

        recalculateTransform();
        repaint();
    }

    /**
     * Report a new head position. The displayed cursor eases toward this
     * target on a ~60 FPS timer so motion stays smooth between the discrete
     * position updates.
     */
    public void updatePosition(double x, double y) {
        this.targetX = x;
        this.targetY = y;
        if (cursorAnimTimer == null) {
            cursorAnimTimer = new javax.swing.Timer(16, e -> animateCursorStep());
        }
        if (!cursorAnimTimer.isRunning()) {
            cursorAnimTimer.start();
        }
    }

    private void animateCursorStep() {
        double dx = targetX - currentX;
        double dy = targetY - currentY;
        double dist = Math.hypot(dx, dy);
        if (dist < 0.05) {
            // Settled: snap and stop animating to avoid idle repaints.
            currentX = targetX;
            currentY = targetY;
            if (cursorAnimTimer != null) {
                cursorAnimTimer.stop();
            }
            repaint();
            return;
        }
        // Exponential ease for smoothness, with a minimum step so long jumps
        // don't crawl. ~0.5 mm/frame floor ~= 30 mm/s at 60 FPS.
        double step = Math.max(dist * 0.35, 0.5);
        if (step >= dist) {
            currentX = targetX;
            currentY = targetY;
        } else {
            currentX += dx / dist * step;
            currentY += dy / dist * step;
        }
        repaint();
    }

    // ----- Transformation Helpers -----

    // The composited transform is supplied wholesale via setEffectiveAxes() (it already
    // accounts for portrait axis swap, machine origin and the extra flags), so these just
    // expose it under the names the rest of this class already uses.
    private boolean effectiveSwap() { return effSwapXY; }
    private boolean effectiveInvertX() { return effInvertX; }
    private boolean effectiveInvertY() { return effInvertY; }

    private double[] contentBoundsArray() {
        return new double[] { rawMinX, rawMaxX, rawMinY, rawMaxY };
    }

    /**
     * Recalculate alignment offset using the shared {@link CoordinateTransform} utility.
     * MUST produce the same result as {@link org.trostheide.gantry.app.plot.PlotService}.
     */
    private void recalculateTransform() {
        if (allPaths.isEmpty()) {
            rawMinX = rawMaxX = rawMinY = rawMaxY = 0;
            alignOffsetX = alignOffsetY = 0;
            return;
        }

        // Calculate raw content bounds
        this.rawMinX = Double.MAX_VALUE;
        this.rawMaxX = -Double.MAX_VALUE;
        this.rawMinY = Double.MAX_VALUE;
        this.rawMaxY = -Double.MAX_VALUE;

        for (List<Point2D> path : allPaths) {
            for (Point2D p : path) {
                this.rawMinX = Math.min(this.rawMinX, p.x());
                this.rawMaxX = Math.max(this.rawMaxX, p.x());
                this.rawMinY = Math.min(this.rawMinY, p.y());
                this.rawMaxY = Math.max(this.rawMaxY, p.y());
            }
        }

        // When axes are swapped for portrait, translate the alignment label
        String effectiveAlign = canvasAlignment;
        if (needsAxisSwap()) {
            effectiveAlign = translateAlignmentForPortrait(canvasAlignment);
        }

        if (suppressAlignment || effectiveAlign == null) {
            alignOffsetX = 0;
            alignOffsetY = 0;
        } else {
            double[] offset = CoordinateTransform.calculateAlignmentOffset(
                    effectiveAlign, contentBoundsArray(),
                    machineWidth, machineHeight,
                    effectiveSwap(), effectiveInvertX(), effectiveInvertY(),
                    dataRotation, isOriginRight(),
                    paddingX, paddingY);
            alignOffsetX = offset[0];
            alignOffsetY = offset[1];
        }
    }

    /**
     * In portrait mode, the alignment corners sharing exactly one component with the origin
     * corner swap with each other (the origin corner and its diagonal are fixed).
     */
    private String translateAlignmentForPortrait(String label) {
        return org.trostheide.gantry.app.plot.GantryConfig.translateAlignmentForPortrait(label, isOriginRight(), isOriginBottom());
    }

    private boolean isPortrait() {
        return "Portrait".equals(orientation);
    }

    private double displayWidth() {
        return isPortrait() ? Math.min(machineWidth, machineHeight) : Math.max(machineWidth, machineHeight);
    }

    private double displayHeight() {
        return isPortrait() ? Math.max(machineWidth, machineHeight) : Math.min(machineWidth, machineHeight);
    }

    private double[] physicalToScreen(double motorX, double motorY) {
        // Motor space already has swap/invert baked in relative to content; mapping it to
        // screen space (top-left origin, X right, Y down) applies the same swap/invert
        // formula again (not its inverse) against the bed bounds, swapped when the axes
        // are swapped, so it matches transformPointToMotor()'s composited flags exactly.
        double maxX = effectiveSwap() ? machineHeight : machineWidth;
        double maxY = effectiveSwap() ? machineWidth : machineHeight;
        return CoordinateTransform.transformPoint(motorX, motorY,
                effectiveSwap(), effectiveInvertX(), effectiveInvertY(),
                maxX, maxY, 0, null);
    }

    /**
     * Full pipeline: Raw Point -> Screen Point
     * Overlay transform (scale + offset) is applied in raw content space before the rest of
     * the pipeline.
     */
    private double[] transformPoint(Point2D rawPoint) {
        double[] motor = transformPointToMotor(rawPoint);
        return physicalToScreen(motor[0], motor[1]);
    }

    private double[] transformPointToMotor(Point2D rawPoint) {
        double cx = (rawMinX + rawMaxX) / 2.0;
        double cy = (rawMinY + rawMaxY) / 2.0;
        double[] o = CoordinateTransform.applyOverlayRaw(rawPoint.x(), rawPoint.y(),
                cx, cy, overlayScale, overlayOffsetX, overlayOffsetY,
                overlayRotation, overlayMirror);
        double x = o[0];
        double y = o[1];

        double[] motor = CoordinateTransform.transformPoint(
                x, y,
                effectiveSwap(), effectiveInvertX(), effectiveInvertY(),
                machineWidth, machineHeight,
                dataRotation, contentBoundsArray());
        motor[0] += alignOffsetX;
        motor[1] += alignOffsetY;
        return motor;
    }

    private void clampOverlayToBed() {
        if (allPaths.isEmpty()) return;
        Point2D[] corners = {
            new Point2D(rawMinX, rawMinY), new Point2D(rawMaxX, rawMinY),
            new Point2D(rawMinX, rawMaxY), new Point2D(rawMaxX, rawMaxY)
        };
        double mMinX = Double.MAX_VALUE, mMinY = Double.MAX_VALUE;
        double mMaxX = -Double.MAX_VALUE, mMaxY = -Double.MAX_VALUE;
        for (Point2D c : corners) {
            double[] m = transformPointToMotor(c);
            mMinX = Math.min(mMinX, m[0]); mMaxX = Math.max(mMaxX, m[0]);
            mMinY = Math.min(mMinY, m[1]); mMaxY = Math.max(mMaxY, m[1]);
        }
        double bedW = needsAxisSwap() ? Math.max(machineWidth, machineHeight) : machineWidth;
        double bedH = needsAxisSwap() ? Math.min(machineWidth, machineHeight) : machineHeight;
        double shiftMmX = 0, shiftMmY = 0;
        if (mMinX < 0) shiftMmX = -mMinX;
        else if (mMaxX > bedW) shiftMmX = bedW - mMaxX;
        if (mMinY < 0) shiftMmY = -mMinY;
        else if (mMaxY > bedH) shiftMmY = bedH - mMaxY;
        if (shiftMmX != 0 || shiftMmY != 0) {
            // Convert motor-space correction back to raw content space
            // Use the same finite-difference approach but in reverse (motor->raw)
            double cx = (rawMinX + rawMaxX) / 2.0;
            double cy = (rawMinY + rawMaxY) / 2.0;
            double[] baseMotor = transformPointToMotor(new Point2D(cx, cy));
            // Temporarily adjust offset by +1 in each axis to measure the mapping
            double savedOX = overlayOffsetX, savedOY = overlayOffsetY;
            overlayOffsetX += 1;
            double[] dxMotor = transformPointToMotor(new Point2D(cx, cy));
            overlayOffsetX = savedOX;
            overlayOffsetY += 1;
            double[] dyMotor = transformPointToMotor(new Point2D(cx, cy));
            overlayOffsetY = savedOY;
            double a = dxMotor[0] - baseMotor[0], b = dyMotor[0] - baseMotor[0];
            double c2 = dxMotor[1] - baseMotor[1], d = dyMotor[1] - baseMotor[1];
            double det = a * d - b * c2;
            if (Math.abs(det) > 1e-10) {
                double rawDX = (shiftMmX * d - shiftMmY * b) / det;
                double rawDY = (a * shiftMmY - c2 * shiftMmX) / det;
                overlayOffsetX += rawDX;
                overlayOffsetY += rawDY;
            }
        }
    }

    // ----- Painting -----

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        double dw = displayWidth();
        double dh = displayHeight();

        // Calculate scale to fit displayed bed in panel
        double scaleX = (w - 40) / dw;
        double scaleY = (h - 40) / dh;
        double scale = Math.min(scaleX, scaleY);
        if (scale <= 0)
            scale = 1.0;

        // Center the displayed bed in the panel
        double tx = 20 + (w - 40 - dw * scale) / 2.0;
        double ty = 20 + (h - 40 - dh * scale) / 2.0;

        this.paintScale = scale;
        this.paintTx = tx;
        this.paintTy = ty;

        AffineTransform old = g2.getTransform();
        g2.translate(tx, ty);
        g2.scale(scale, scale);

        // --- Draw Machine Bed ---
        g2.setColor(new Color(50, 52, 58));
        g2.fill(new java.awt.geom.Rectangle2D.Double(0, 0, dw, dh));
        g2.setColor(new Color(80, 82, 90));
        g2.setStroke(new BasicStroke((float) (1.5 / scale)));
        g2.draw(new java.awt.geom.Rectangle2D.Double(0, 0, dw, dh));

        // --- Draw Origin Marker ---
        double[] originScreen = physicalToScreen(0, 0);
        g2.setColor(Color.ORANGE);
        double markerR = 5 / scale;
        g2.fill(new java.awt.geom.Ellipse2D.Double(originScreen[0] - markerR, originScreen[1] - markerR,
                markerR * 2, markerR * 2));
        g2.setColor(new Color(200, 200, 200));
        g2.setFont(g2.getFont().deriveFont((float) (12 / scale)));
        g2.drawString("0,0 (Origin)", (float) (originScreen[0] - 50 / scale), (float) (originScreen[1] + 15 / scale));

        // --- Draw Axes (from Physical Origin) ---
        g2.setStroke(new BasicStroke((float) (2.0 / scale)));
        double axisLen = Math.min(machineWidth, machineHeight) * 0.15;

        double[] xAxisEnd = physicalToScreen(axisLen, 0);
        g2.setColor(Color.RED);
        g2.draw(new java.awt.geom.Line2D.Double(originScreen[0], originScreen[1], xAxisEnd[0], xAxisEnd[1]));
        g2.drawString("+X", (float) (xAxisEnd[0] - 10 / scale), (float) (xAxisEnd[1] + 15 / scale));

        double[] yAxisEnd = physicalToScreen(0, axisLen);
        g2.setColor(Color.GREEN);
        g2.draw(new java.awt.geom.Line2D.Double(originScreen[0], originScreen[1], yAxisEnd[0], yAxisEnd[1]));
        g2.drawString("+Y", (float) (yAxisEnd[0] + 5 / scale), (float) (yAxisEnd[1] + 5 / scale));

        // --- Draw Refill Stations ---
        for (Station station : stations) {
            // Station coords are in raw input space, transform to screen
            double[] sScreen = physicalToScreen(station.x(), station.y());
            g2.setColor(new Color(80, 180, 255)); // Blue marker
            double stationR = 4 / scale;
            g2.fill(new java.awt.geom.Ellipse2D.Double(sScreen[0] - stationR, sScreen[1] - stationR,
                    stationR * 2, stationR * 2));
            // Draw station label
            g2.setColor(new Color(190, 190, 190));
            g2.setFont(g2.getFont().deriveFont((float) (9 / scale)));
            g2.drawString(station.name(), (float) (sScreen[0] + stationR + 2 / scale),
                    (float) (sScreen[1] + 4 / scale));
        }

        // --- Draw Paths ---
        g2.setColor(new Color(130, 160, 255));
        g2.setStroke(new BasicStroke((float) (1.0 / scale)));

        for (List<Point2D> path : allPaths) {
            if (path.isEmpty())
                continue;
            Path2D p2d = new Path2D.Double();

            double[] p0 = transformPoint(path.get(0));
            p2d.moveTo(p0[0], p0[1]);

            for (int i = 1; i < path.size(); i++) {
                double[] pi = transformPoint(path.get(i));
                p2d.lineTo(pi[0], pi[1]);
            }
            g2.draw(p2d);
        }

        // --- Draw Interactive Bounding Box ---
        if (!allPaths.isEmpty()) {
            // Transform raw content corners through the full pipeline
            Point2D[] corners = {
                new Point2D(rawMinX, rawMinY), new Point2D(rawMaxX, rawMinY),
                new Point2D(rawMinX, rawMaxY), new Point2D(rawMaxX, rawMaxY)
            };
            double sMinX = Double.MAX_VALUE, sMinY = Double.MAX_VALUE;
            double sMaxX = -Double.MAX_VALUE, sMaxY = -Double.MAX_VALUE;
            for (Point2D c : corners) {
                double[] sc = transformPoint(c);
                sMinX = Math.min(sMinX, sc[0]); sMaxX = Math.max(sMaxX, sc[0]);
                sMinY = Math.min(sMinY, sc[1]); sMaxY = Math.max(sMaxY, sc[1]);
            }

            g2.setColor(new Color(255, 200, 50, 120));
            float[] dash = {(float)(6 / scale), (float)(4 / scale)};
            g2.setStroke(new BasicStroke((float)(1.5 / scale), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, dash, 0));
            g2.draw(new Rectangle2D.Double(sMinX, sMinY, sMaxX - sMinX, sMaxY - sMinY));

            double hs = HANDLE_SIZE_PX / scale;
            double midX = (sMinX + sMaxX) / 2, midY = (sMinY + sMaxY) / 2;
            double[][] handlePos = {
                {sMinX, sMinY}, {midX, sMinY}, {sMaxX, sMinY},
                {sMinX, midY}, {sMaxX, midY},
                {sMinX, sMaxY}, {midX, sMaxY}, {sMaxX, sMaxY}
            };
            g2.setColor(new Color(255, 200, 50));
            g2.setStroke(new BasicStroke((float)(1.0 / scale)));
            for (double[] hp : handlePos) {
                g2.fill(new Rectangle2D.Double(hp[0] - hs/2, hp[1] - hs/2, hs, hs));
            }
        }

        // --- Draw Cursor (Head Position) ---
        // currentX, currentY are Physical coordinates from the backend
        double[] headScreen = physicalToScreen(currentX, currentY);
        g2.setColor(Color.RED);
        double r = 4.0 / scale;
        g2.fill(new java.awt.geom.Ellipse2D.Double(headScreen[0] - r, headScreen[1] - r, r * 2, r * 2));

        // Crosshair
        g2.setStroke(new BasicStroke((float) (0.5 / scale)));
        double crossSize = Math.max(machineWidth, machineHeight);
        g2.draw(new java.awt.geom.Line2D.Double(headScreen[0] - crossSize, headScreen[1],
                headScreen[0] + crossSize, headScreen[1]));
        g2.draw(new java.awt.geom.Line2D.Double(headScreen[0], headScreen[1] - crossSize,
                headScreen[0], headScreen[1] + crossSize));

        g2.setTransform(old);

        // --- HUD ---
        g2.setColor(new Color(180, 180, 180));
        g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        g2.drawString(String.format(
                "Pos: %.1f, %.1f | Align: %s | Rot: %d | Origin: %s | %s",
                currentX, currentY, canvasAlignment, dataRotation,
                machineOrigin, orientation), 10, h - 10);
        if (hasOverlayTransform()) {
            g2.drawString(String.format(
                    "Drag: dX=%.1f dY=%.1f Scale=%.0f%% | Bed: %.0fx%.0f",
                    overlayOffsetX, overlayOffsetY, overlayScale * 100,
                    machineWidth, machineHeight), 10, h - 24);
        } else {
            g2.drawString(String.format(
                    "Bed: %.0fx%.0f | Offset: %.1f, %.1f | Swap: %s InvX: %s InvY: %s",
                    machineWidth, machineHeight, alignOffsetX, alignOffsetY,
                    effectiveSwap() ? "Y" : "N",
                    effectiveInvertX() ? "Y" : "N",
                    effectiveInvertY() ? "Y" : "N"), 10, h - 24);
        }
    }

    // ----- Interactive Drag/Resize Helpers -----

    private double[] getContentScreenBoundsPixel() {
        if (allPaths.isEmpty()) return new double[]{0, 0, 0, 0};
        Point2D[] corners = {
            new Point2D(rawMinX, rawMinY), new Point2D(rawMaxX, rawMinY),
            new Point2D(rawMinX, rawMaxY), new Point2D(rawMaxX, rawMaxY)
        };
        double sMinX = Double.MAX_VALUE, sMinY = Double.MAX_VALUE;
        double sMaxX = -Double.MAX_VALUE, sMaxY = -Double.MAX_VALUE;
        for (Point2D c : corners) {
            double[] sc = transformPoint(c);
            // Convert from machine-space to screen pixels
            double px = sc[0] * paintScale + paintTx;
            double py = sc[1] * paintScale + paintTy;
            sMinX = Math.min(sMinX, px); sMaxX = Math.max(sMaxX, px);
            sMinY = Math.min(sMinY, py); sMaxY = Math.max(sMaxY, py);
        }
        return new double[]{sMinX, sMinY, sMaxX, sMaxY};
    }

    private int hitTestHandle(int mouseX, int mouseY) {
        double[] bb = getContentScreenBoundsPixel();
        double x0 = bb[0], y0 = bb[1], x1 = bb[2], y1 = bb[3];
        double mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
        double ht = HANDLE_SIZE_PX + 3;

        double[][] handles = {
            {x0, y0}, {mx, y0}, {x1, y0},
            {x0, my}, {x1, my},
            {x0, y1}, {mx, y1}, {x1, y1}
        };
        int[] handleIds = {HANDLE_NW, HANDLE_N, HANDLE_NE, HANDLE_W, HANDLE_E, HANDLE_SW, HANDLE_S, HANDLE_SE};

        for (int i = 0; i < handles.length; i++) {
            if (Math.abs(mouseX - handles[i][0]) <= ht && Math.abs(mouseY - handles[i][1]) <= ht) {
                return handleIds[i];
            }
        }

        if (mouseX >= x0 - 2 && mouseX <= x1 + 2 && mouseY >= y0 - 2 && mouseY <= y1 + 2) {
            return HANDLE_MOVE;
        }

        return HANDLE_NONE;
    }

    private Cursor cursorForHandle(int handle) {
        switch (handle) {
            case HANDLE_NW: return Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
            case HANDLE_NE: return Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
            case HANDLE_SW: return Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
            case HANDLE_SE: return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
            case HANDLE_N:  return Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
            case HANDLE_S:  return Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
            case HANDLE_W:  return Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
            case HANDLE_E:  return Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
            case HANDLE_MOVE: return Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
            default: return Cursor.getDefaultCursor();
        }
    }

    private double[] screenDeltaToMm(double dxPx, double dyPx) {
        // Screen pixels -> machine-space mm delta
        // The raw content space maps through the transform pipeline to screen.
        // For dragging, we need the inverse: screen delta -> raw content delta.
        // We use finite differences: transform a small offset and measure the screen effect.
        double cx = (rawMinX + rawMaxX) / 2.0;
        double cy = (rawMinY + rawMaxY) / 2.0;
        double[] base = transformPoint(new Point2D(cx, cy));
        double[] dxRef = transformPoint(new Point2D(cx + 1, cy));
        double[] dyRef = transformPoint(new Point2D(cx, cy + 1));

        double screenPerMmX_x = (dxRef[0] - base[0]) * paintScale;
        double screenPerMmX_y = (dxRef[1] - base[1]) * paintScale;
        double screenPerMmY_x = (dyRef[0] - base[0]) * paintScale;
        double screenPerMmY_y = (dyRef[1] - base[1]) * paintScale;

        // Solve 2x2 system: [screenPerMmX_x, screenPerMmY_x] [mmX]   [dxPx]
        //                    [screenPerMmX_y, screenPerMmY_y] [mmY] = [dyPx]
        double det = screenPerMmX_x * screenPerMmY_y - screenPerMmX_y * screenPerMmY_x;
        if (Math.abs(det) < 1e-10) return new double[]{0, 0};

        double mmX = (dxPx * screenPerMmY_y - dyPx * screenPerMmY_x) / det;
        double mmY = (screenPerMmX_x * dyPx - screenPerMmX_y * dxPx) / det;
        return new double[]{mmX, mmY};
    }

    private void handleResize(int handle, double dxPx, double dyPx) {
        // Compute scale change from drag distance
        double[] bb = getContentScreenBoundsPixel();
        double bbW = bb[2] - bb[0];
        double bbH = bb[3] - bb[1];
        if (bbW < 1 || bbH < 1) return;

        double scaleFactorX = 1, scaleFactorY = 1;

        switch (handle) {
            case HANDLE_SE: scaleFactorX = (bbW + dxPx) / bbW; scaleFactorY = (bbH + dyPx) / bbH; break;
            case HANDLE_NW: scaleFactorX = (bbW - dxPx) / bbW; scaleFactorY = (bbH - dyPx) / bbH; break;
            case HANDLE_NE: scaleFactorX = (bbW + dxPx) / bbW; scaleFactorY = (bbH - dyPx) / bbH; break;
            case HANDLE_SW: scaleFactorX = (bbW - dxPx) / bbW; scaleFactorY = (bbH + dyPx) / bbH; break;
            case HANDLE_E:  scaleFactorX = (bbW + dxPx) / bbW; scaleFactorY = scaleFactorX; break;
            case HANDLE_W:  scaleFactorX = (bbW - dxPx) / bbW; scaleFactorY = scaleFactorX; break;
            case HANDLE_S:  scaleFactorY = (bbH + dyPx) / bbH; scaleFactorX = scaleFactorY; break;
            case HANDLE_N:  scaleFactorY = (bbH - dyPx) / bbH; scaleFactorX = scaleFactorY; break;
        }

        // Use uniform scale (average) to keep aspect ratio
        double scaleFactor = (scaleFactorX + scaleFactorY) / 2.0;
        scaleFactor = Math.max(0.05, Math.min(scaleFactor, 20.0));

        overlayScale = dragStartOverlayScale * scaleFactor;
    }

    // ----- Internal Types -----
    private record Point2D(double x, double y) {
    }
}
