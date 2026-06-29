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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * Layer index (into the loaded {@code ProcessorOutput.layers()}) that produced each entry in
     * {@link #allPaths}, kept in lockstep. Lets the panel draw the selected layers in full colour
     * while ghosting the rest.
     */
    private final List<Integer> pathLayer = new ArrayList<>();

    /** Command id of each entry in {@link #allPaths}, in lockstep — lets a clicked stroke map back to
     *  its {@code DrawCommand} for deletion (Tier A stroke editing). */
    private final List<Integer> pathCommandId = new ArrayList<>();

    /**
     * Indices of the layers currently selected for display. Selected layers draw in full colour;
     * unselected layers are ghosted (dimmed) for context. Defaults to "all layers" on load; the
     * controller ({@code PlotterPanel}) replaces it whenever the user (de)selects layers.
     */
    private final Set<Integer> selectedLayers = new HashSet<>();

    /**
     * Display colour for each layer (indexed by layer position), derived from the layer's source
     * {@code #rrggbb} and brightened where needed so it stays readable against the dark canvas. A
     * layer with no known colour gets a distinct hue from {@link #FALLBACK_PALETTE} so layers stay
     * visually separable. Kept in lockstep with the loaded output's layer list.
     */
    private final List<Color> layerColors = new ArrayList<>();
    private boolean colorByLayer = true;

    /** The canvas background; layer colours are floored against this so nothing vanishes into it. */
    private static final Color CANVAS_BG = new Color(35, 35, 40);

    /** Default highlight colour used when per-layer colouring is off or a layer has no colour. */
    private static final Color DEFAULT_PATH = new Color(130, 160, 255);

    /** Distinct hues for layers whose source colour is unknown (or would be unreadable). */
    private static final Color[] FALLBACK_PALETTE = {
            new Color(130, 160, 255), new Color(255, 140, 120), new Color(120, 220, 150),
            new Color(230, 200, 110), new Color(210, 130, 230), new Color(120, 220, 220),
            new Color(240, 150, 190), new Color(170, 200, 120)
    };

    // Current head position (Physical coords). currentX/Y is the *displayed*
    // position, which is eased toward targetX/Y so motion looks smooth even
    // when position updates arrive in discrete steps.
    private double currentX = 0;
    private double currentY = 0;
    private double targetX = 0;
    private double targetY = 0;
    private javax.swing.Timer cursorAnimTimer;

    // Current feed-rate override reported by the backend, in percent (100 = nominal).
    private int speedPercent = 100;

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

    // User viewport zoom/pan, applied *on top of* the fit-to-window transform and folded into
    // paintScale/paintTx/paintTy each paint. Because every hit-test already inverts those cached
    // values, the click→motor mapping stays correct at any zoom/pan with no parallel transform.
    private double viewZoom = 1.0;
    private double viewPanX = 0, viewPanY = 0;
    private static final double VIEW_ZOOM_MIN = 0.2, VIEW_ZOOM_MAX = 50.0;

    // Pan gesture state (middle-drag anywhere, or left-drag on empty canvas).
    private boolean panning = false;
    private int panLastX, panLastY;

    /**
     * Exclusive left-click interaction modes on the canvas. {@code NONE} is the default place/resize
     * behaviour; the others repurpose the left click for hatching or stroke editing (Tier A).
     */
    public enum InteractionMode { NONE, HATCH, DELETE_STROKE, ADD_LINE, MOVE_STROKE }

    private InteractionMode interactionMode = InteractionMode.NONE;
    private RegionHatchListener regionHatchListener;
    private StrokeEditListener strokeEditListener;
    private Runnable hatchStyleAction;
    private java.util.function.Consumer<InteractionMode> interactionModeListener;
    private JCheckBoxMenuItem ctxHatchItem, ctxDeleteItem, ctxAddLineItem, ctxMoveItem;
    // Closed region currently under the cursor in hatch mode, highlighted as a pick preview (-1 none).
    private int hoverRegionIndex = -1;
    // Stroke under the cursor in delete mode (index into allPaths), highlighted in red (-1 none).
    private int hoverStrokeIndex = -1;
    // First clicked point (model mm) while drawing a line in ADD_LINE mode, or null between lines.
    private double[] lineStart;
    private int lineHoverX, lineHoverY; // last cursor pixel, for the rubber-band preview
    // Live drag state for MOVE_STROKE: the stroke being dragged (index into allPaths), its original
    // points (model mm), the press point, and whether it actually moved.
    private int dragStrokeIndex = -1;
    private List<Point2D> dragStrokeOrig;
    private double[] dragStrokePressModel;
    private boolean dragStrokeMoved;
    // Pixel position of the last context-menu trigger, for "Hatch area here" / "Clear hatch here".
    private int lastPopupX, lastPopupY;

    /** Notified when the user picks a region to hatch or clear (click in hatch mode, or context menu). */
    public interface RegionHatchListener {
        /**
         * @param region     the picked region as a closed path in raw model (mm) space
         * @param layerIndex the index (into the loaded output's layers) the region belongs to, so
         *                   the fill can be added to the same pen/layer
         */
        void onHatchRegion(Path2D region, int layerIndex);

        /** Removes any previously-added hatch fill whose strokes fall inside {@code region}. */
        void onClearHatchRegion(Path2D region);
    }

    /** Notified for stroke add/delete edits (Tier A). */
    public interface StrokeEditListener {
        /** Delete the command with this id from the model. */
        void onDeleteStroke(int commandId);
        /** Add a straight 2-point stroke (model mm) into {@code layerIndex} (the nearest pen/layer). */
        void onAddLine(double x1, double y1, double x2, double y2, int layerIndex);
        /** Replace the command's points after a drag (model mm), as {@code [n][2]}. */
        void onMoveStroke(int commandId, double[][] points);
        /** Duplicate the command (a nudged copy in the same layer). */
        void onDuplicateStroke(int commandId);
    }

    public void setRegionHatchListener(RegionHatchListener listener) {
        this.regionHatchListener = listener;
    }

    public void setStrokeEditListener(StrokeEditListener listener) {
        this.strokeEditListener = listener;
    }

    /** Action invoked by the canvas "Hatch style…" menu item (opens the style picker). */
    public void setHatchStyleAction(Runnable action) {
        this.hatchStyleAction = action;
    }

    /** Notified when the interaction mode changes, so the menu checkboxes can stay in sync. */
    public void setInteractionModeChangeListener(java.util.function.Consumer<InteractionMode> listener) {
        this.interactionModeListener = listener;
    }

    /** Sets the exclusive interaction mode, resetting transient pick state and the cursor. */
    public void setInteractionMode(InteractionMode mode) {
        if (mode == null) {
            mode = InteractionMode.NONE;
        }
        boolean changed = mode != interactionMode;
        interactionMode = mode;
        hoverRegionIndex = -1;
        hoverStrokeIndex = -1;
        lineStart = null;
        dragStrokeIndex = -1;
        dragStrokeOrig = null;
        setCursor(Cursor.getPredefinedCursor(
                mode == InteractionMode.NONE ? Cursor.DEFAULT_CURSOR : Cursor.CROSSHAIR_CURSOR));
        if (changed && interactionModeListener != null) {
            interactionModeListener.accept(mode);
        }
        repaint();
    }

    public InteractionMode getInteractionMode() {
        return interactionMode;
    }

    /** Back-compat shim: hatch mode is now one of the interaction modes. */
    public void setHatchRegionMode(boolean on) {
        setInteractionMode(on ? InteractionMode.HATCH : InteractionMode.NONE);
    }

    public boolean isHatchRegionMode() {
        return interactionMode == InteractionMode.HATCH;
    }

    /**
     * Resolves the hatch target under a pixel: the smallest closed path containing it, or — if none —
     * an area flood-filled from the point and bounded by all strokes. Returns {@code null} if nothing
     * encloses the point. Shared by the left-click handler and the context menu.
     */
    private HatchTarget resolveHatchTarget(int px, int py) {
        int ri = findClosedRegionAt(px, py);
        if (ri >= 0) {
            return new HatchTarget(rawRegionPath(allPaths.get(ri)), pathLayer.get(ri));
        }
        double[] seed = screenToModel(px, py);
        if (seed != null) {
            Path2D enclosed = EnclosedRegion.fromSeed(strokesAsModel(), seed[0], seed[1]);
            if (enclosed != null) {
                return new HatchTarget(enclosed, nearestStrokeLayer(seed[0], seed[1]));
            }
        }
        return null;
    }

    private record HatchTarget(Path2D region, int layerIndex) {
    }

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

    // Listener invoked when the user removes the drawing via the context menu, so the containing
    // panel can drop its own loaded-output state (otherwise a plot/export would still use it).
    private Runnable removeDrawingListener;

    public void setOverlayChangeListener(Runnable listener) {
        this.overlayChangeListener = listener;
    }

    public void setRemoveDrawingListener(Runnable listener) {
        this.removeDrawingListener = listener;
    }

    /** Clears the loaded drawing and resets the overlay transform, leaving an empty bed. */
    public void clearDrawing() {
        allPaths.clear();
        overlayOffsetX = 0;
        overlayOffsetY = 0;
        overlayScale = 1.0;
        overlayRotation = 0;
        overlayMirror = false;
        suppressAlignment = false;
        viewZoom = 1.0;
        viewPanX = 0;
        viewPanY = 0;
        recalculateTransform();
        repaint();
        fireOverlayChange();
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

    /**
     * Notified when the user edits a refill station directly on the canvas (Phase 17 Half A):
     * drags a marker to a new position, or adds one via the right-click "Add station here" menu.
     * The controller ({@code PlotterPanel}) writes the change back into the authoritative
     * {@code config.stations} map and re-pushes it, so the canvas and the {@code SettingsPanel}
     * station table never hold diverging copies of a station's coordinates.
     */
    public interface StationEditListener {
        /** A station marker was dragged to {@code (x, y)} in machine mm. */
        void onStationMoved(String name, double x, double y);
        /** The user asked to add a new station at {@code (x, y)} in machine mm. */
        void onStationAdded(double x, double y);
    }

    private StationEditListener stationEditListener;

    public void setStationEditListener(StationEditListener listener) {
        this.stationEditListener = listener;
    }

    // Station-drag state: index into `stations` of the marker being dragged, or -1.
    private int draggingStation = -1;
    // Machine-mm location of the last popup trigger, used by "Add station here".
    private double[] lastPopupMm;
    /** Pixel radius within which a click grabs a station marker. */
    private static final double STATION_HIT_PX = 9;

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

    // ----- Viewport zoom/pan -----

    /** Resets the viewport to fit-to-window (zoom 100%, no pan). Leaves the drawing untouched. */
    public void resetView() {
        viewZoom = 1.0;
        viewPanX = 0;
        viewPanY = 0;
        repaint();
    }

    /**
     * Multiplies the viewport zoom by {@code factor} while keeping the content under screen pixel
     * {@code (mx, my)} fixed (zoom-to-cursor). The pan is re-solved via {@link #zoomToCursorPan}
     * so the cached paint transform — and therefore every hit-test that inverts it — stays exact.
     */
    private void zoomAtCursor(double factor, int mx, int my) {
        double newZoom = Math.max(VIEW_ZOOM_MIN, Math.min(VIEW_ZOOM_MAX, viewZoom * factor));
        if (newZoom == viewZoom) {
            return;
        }
        viewPanX = zoomToCursorPan(viewPanX, viewZoom, newZoom, mx);
        viewPanY = zoomToCursorPan(viewPanY, viewZoom, newZoom, my);
        viewZoom = newZoom;
        repaint();
    }

    /**
     * The new pan offset (one axis) that keeps screen pixel {@code m} pointing at the same content
     * after the zoom changes from {@code oldZoom} to {@code newZoom}. Derived from holding
     * {@code finalPixel = zoom*fitPixel + pan} fixed at {@code m}; the fit terms cancel, leaving a
     * relation in pan/zoom/cursor alone (package-private for testing).
     */
    static double zoomToCursorPan(double pan, double oldZoom, double newZoom, double m) {
        double f = newZoom / oldZoom;
        return f * pan + (1 - f) * m;
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

        JPopupMenu contextMenu = buildContextMenu();

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (maybeShowPopup(e)) return;
                // Stations are grabbable even on an empty bed, and take precedence over the
                // drawing handles (they're small, on top, and a deliberate target).
                int st = hitTestStation(e.getX(), e.getY());
                if (st != HANDLE_NONE) {
                    draggingStation = st;
                    return;
                }
                // Pan from ANYWHERE with middle-drag or Shift+left-drag — works over the drawing and
                // in hatch mode, so you can reposition while zoomed in even with no empty bed to grab
                // and no middle button.
                if (SwingUtilities.isMiddleMouseButton(e)
                        || (SwingUtilities.isLeftMouseButton(e) && e.isShiftDown())) {
                    startPan(e);
                    return;
                }
                // Left-click behaviour depends on the interaction mode. A click that doesn't act
                // (e.g. hatch on empty space) falls through so the user can still pan.
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (interactionMode == InteractionMode.HATCH && regionHatchListener != null) {
                        HatchTarget t = resolveHatchTarget(e.getX(), e.getY());
                        if (t != null) {
                            regionHatchListener.onHatchRegion(t.region(), t.layerIndex());
                            return;
                        }
                    } else if (interactionMode == InteractionMode.DELETE_STROKE && strokeEditListener != null) {
                        int si = nearestStrokeIndex(e.getX(), e.getY());
                        if (si >= 0) {
                            strokeEditListener.onDeleteStroke(pathCommandId.get(si));
                            return;
                        }
                    } else if (interactionMode == InteractionMode.ADD_LINE && strokeEditListener != null) {
                        double[] p = screenToModel(e.getX(), e.getY());
                        if (p != null) {
                            if (lineStart == null) {
                                lineStart = p;
                                lineHoverX = e.getX();
                                lineHoverY = e.getY();
                            } else {
                                strokeEditListener.onAddLine(lineStart[0], lineStart[1], p[0], p[1],
                                        nearestStrokeLayer(lineStart[0], lineStart[1]));
                                lineStart = null;
                            }
                            repaint();
                            return;
                        }
                    } else if (interactionMode == InteractionMode.MOVE_STROKE && strokeEditListener != null) {
                        int si = nearestStrokeIndex(e.getX(), e.getY());
                        double[] m = si >= 0 ? screenToModel(e.getX(), e.getY()) : null;
                        if (m != null) {
                            dragStrokeIndex = si;
                            dragStrokePressModel = m;
                            dragStrokeOrig = new ArrayList<>(allPaths.get(si));
                            dragStrokeMoved = false;
                            return;
                        }
                    }
                }
                // Drawing move/resize handles only act in the default (NONE) mode; in an edit mode an
                // unconsumed left-press pans instead of grabbing the whole-drawing handle.
                int handle = (interactionMode == InteractionMode.NONE && !allPaths.isEmpty())
                        ? hitTestHandle(e.getX(), e.getY()) : HANDLE_NONE;
                if (handle == HANDLE_NONE && SwingUtilities.isLeftMouseButton(e)) {
                    startPan(e);
                    return;
                }
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
                if (panning) {
                    viewPanX += e.getX() - panLastX;
                    viewPanY += e.getY() - panLastY;
                    panLastX = e.getX();
                    panLastY = e.getY();
                    repaint();
                    return;
                }
                if (draggingStation >= 0) {
                    double[] mm = screenToPhysical(e.getX(), e.getY());
                    Station s = stations.get(draggingStation);
                    stations.set(draggingStation, new Station(s.name(), mm[0], mm[1]));
                    repaint();
                    return;
                }
                if (dragStrokeIndex >= 0) {
                    double[] m = screenToModel(e.getX(), e.getY());
                    if (m != null && dragStrokePressModel != null) {
                        double ddx = m[0] - dragStrokePressModel[0];
                        double ddy = m[1] - dragStrokePressModel[1];
                        List<Point2D> moved = new ArrayList<>(dragStrokeOrig.size());
                        for (Point2D p : dragStrokeOrig) {
                            moved.add(new Point2D(p.x() + ddx, p.y() + ddy));
                        }
                        allPaths.set(dragStrokeIndex, moved);
                        dragStrokeMoved = true;
                        repaint();
                    }
                    return;
                }
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
                if (maybeShowPopup(e)) return;
                if (panning) {
                    panning = false;
                    setCursor(Cursor.getPredefinedCursor(interactionMode == InteractionMode.NONE
                            ? Cursor.DEFAULT_CURSOR : Cursor.CROSSHAIR_CURSOR));
                    return;
                }
                if (draggingStation >= 0) {
                    Station s = stations.get(draggingStation);
                    draggingStation = -1;
                    if (stationEditListener != null) {
                        stationEditListener.onStationMoved(s.name(), s.x(), s.y());
                    }
                    return;
                }
                if (dragStrokeIndex >= 0) {
                    if (dragStrokeMoved && strokeEditListener != null) {
                        List<Point2D> pts = allPaths.get(dragStrokeIndex);
                        double[][] coords = new double[pts.size()][2];
                        for (int i = 0; i < pts.size(); i++) {
                            coords[i][0] = pts.get(i).x();
                            coords[i][1] = pts.get(i).y();
                        }
                        strokeEditListener.onMoveStroke(pathCommandId.get(dragStrokeIndex), coords);
                    }
                    dragStrokeIndex = -1;
                    dragStrokeOrig = null;
                    return;
                }
                if (dragHandle != HANDLE_NONE) {
                    dragHandle = HANDLE_NONE;
                    fireOverlayChange();
                }
            }

            private boolean maybeShowPopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return false;
                lastPopupX = e.getX();
                lastPopupY = e.getY();
                lastPopupMm = screenToPhysical(e.getX(), e.getY());
                boolean hasDrawing = !allPaths.isEmpty();
                for (Component item : drawingMenuItems) {
                    item.setEnabled(hasDrawing);
                }
                ctxHatchItem.setSelected(interactionMode == InteractionMode.HATCH);
                ctxDeleteItem.setSelected(interactionMode == InteractionMode.DELETE_STROKE);
                ctxAddLineItem.setSelected(interactionMode == InteractionMode.ADD_LINE);
                ctxMoveItem.setSelected(interactionMode == InteractionMode.MOVE_STROKE);
                contextMenu.show(VisualizationPanel.this, e.getX(), e.getY());
                return true;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                // In an interaction mode the crosshair stays put (don't let the drag-handle hit-test
                // swap it), and a mode-specific preview tracks the cursor.
                if (interactionMode != InteractionMode.NONE) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    if (interactionMode == InteractionMode.HATCH) {
                        updateHoverRegion(e.getX(), e.getY());
                    } else if (interactionMode == InteractionMode.DELETE_STROKE
                            || interactionMode == InteractionMode.MOVE_STROKE) {
                        updateHoverStroke(e.getX(), e.getY());
                    } else if (interactionMode == InteractionMode.ADD_LINE && lineStart != null) {
                        lineHoverX = e.getX();
                        lineHoverY = e.getY();
                        repaint();
                    }
                    return;
                }
                if (hitTestStation(e.getX(), e.getY()) != HANDLE_NONE) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    return;
                }
                if (allPaths.isEmpty()) { setCursor(Cursor.getDefaultCursor()); return; }
                int handle = hitTestHandle(e.getX(), e.getY());
                setCursor(cursorForHandle(handle));
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                // Double-click on empty canvas (not a handle or station) resets the view to fit.
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)
                        && hitTestStation(e.getX(), e.getY()) == HANDLE_NONE
                        && (allPaths.isEmpty() || hitTestHandle(e.getX(), e.getY()) == HANDLE_NONE)) {
                    resetView();
                }
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        // Mouse wheel zooms toward the cursor (wheel-up / away = zoom in).
        addMouseWheelListener(e ->
                zoomAtCursor(e.getPreciseWheelRotation() < 0 ? 1.12 : 1 / 1.12, e.getX(), e.getY()));
    }

    /**
     * Right-click menu for the Live View: removing the drawing plus the overlay-placement
     * actions (reset/rotate/mirror), kept here so they're reachable directly on the canvas.
     */
    private JPopupMenu buildContextMenu() {
        JPopupMenu menu = new JPopupMenu();

        // Always available (Phase 17): drop a refill station at the clicked bed position. The
        // controller turns the mm coordinate into a real StationConfig and re-pushes the list.
        JMenuItem addStation = new JMenuItem("Add station here");
        addStation.addActionListener(e -> {
            if (stationEditListener != null && lastPopupMm != null) {
                stationEditListener.onStationAdded(lastPopupMm[0], lastPopupMm[1]);
            }
        });
        menu.add(addStation);

        // Always available: snap the viewport back to fit-to-window (zoom 100%, no pan).
        JMenuItem resetView = new JMenuItem("Reset View (Zoom/Pan)");
        resetView.addActionListener(e -> resetView());
        menu.add(resetView);
        menu.addSeparator();

        // Interaction-mode toggles, mutually exclusive (kept in sync with the Edit menu and each
        // other via setInteractionMode + the mode-change listener).
        ctxHatchItem = modeItem("Hatch mode (click areas to fill)", InteractionMode.HATCH);
        ctxDeleteItem = modeItem("Delete-stroke mode (click a line)", InteractionMode.DELETE_STROKE);
        ctxAddLineItem = modeItem("Add-line mode (click two points)", InteractionMode.ADD_LINE);
        ctxMoveItem = modeItem("Move-stroke mode (drag a line)", InteractionMode.MOVE_STROKE);
        menu.add(ctxHatchItem);
        menu.add(ctxDeleteItem);
        menu.add(ctxAddLineItem);
        menu.add(ctxMoveItem);

        // Hatch the region under the click — fill it, clear a previous fill, or pick the style —
        // without switching into hatch mode first. Disabled when no drawing is loaded.
        JMenuItem hatchHere = new JMenuItem("Hatch area here");
        hatchHere.addActionListener(e -> {
            if (regionHatchListener != null) {
                HatchTarget t = resolveHatchTarget(lastPopupX, lastPopupY);
                if (t != null) {
                    regionHatchListener.onHatchRegion(t.region(), t.layerIndex());
                }
            }
        });
        menu.add(hatchHere);
        JMenuItem clearHatchHere = new JMenuItem("Clear hatch in this area");
        clearHatchHere.addActionListener(e -> {
            if (regionHatchListener != null) {
                HatchTarget t = resolveHatchTarget(lastPopupX, lastPopupY);
                if (t != null) {
                    regionHatchListener.onClearHatchRegion(t.region());
                }
            }
        });
        menu.add(clearHatchHere);
        JMenuItem hatchStyle = new JMenuItem("Hatch style...");
        hatchStyle.addActionListener(e -> {
            if (hatchStyleAction != null) {
                hatchStyleAction.run();
            }
        });
        menu.add(hatchStyle);
        // One-shot delete/duplicate of the stroke nearest the click, without entering a mode.
        JMenuItem deleteHere = new JMenuItem("Delete nearest line");
        deleteHere.addActionListener(e -> {
            if (strokeEditListener != null) {
                int si = nearestStrokeIndex(lastPopupX, lastPopupY);
                if (si >= 0) {
                    strokeEditListener.onDeleteStroke(pathCommandId.get(si));
                }
            }
        });
        menu.add(deleteHere);
        JMenuItem duplicateHere = new JMenuItem("Duplicate nearest line");
        duplicateHere.addActionListener(e -> {
            if (strokeEditListener != null) {
                int si = nearestStrokeIndex(lastPopupX, lastPopupY);
                if (si >= 0) {
                    strokeEditListener.onDuplicateStroke(pathCommandId.get(si));
                }
            }
        });
        menu.add(duplicateHere);
        menu.addSeparator();

        // Drawing-only items: disabled when the bed is empty (toggled in maybeShowPopup).
        JMenuItem remove = new JMenuItem("Remove Drawing");
        remove.addActionListener(e -> {
            clearDrawing();
            if (removeDrawingListener != null) removeDrawingListener.run();
        });
        menu.add(remove);

        JMenuItem reset = new JMenuItem("Reset Position");
        reset.addActionListener(e -> resetOverlay());
        menu.add(reset);

        JMenuItem rotate = new JMenuItem("Rotate 90°");
        rotate.addActionListener(e -> rotateOverlay());
        menu.add(rotate);

        JMenuItem mirror = new JMenuItem("Mirror");
        mirror.addActionListener(e -> toggleMirror());
        menu.add(mirror);

        drawingMenuItems = List.of(remove, reset, rotate, mirror, hatchHere, clearHatchHere,
                deleteHere, duplicateHere);
        return menu;
    }

    /** A mutually-exclusive interaction-mode toggle for the context menu. */
    private JCheckBoxMenuItem modeItem(String label, InteractionMode mode) {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(label);
        item.addActionListener(e ->
                setInteractionMode(item.isSelected() ? mode : InteractionMode.NONE));
        return item;
    }

    /** Context-menu entries that only make sense with a loaded drawing (toggled per right-click). */
    private List<JMenuItem> drawingMenuItems = new ArrayList<>();

    // ----- Data Loading -----

    /** Loads every DRAW polyline from {@code output}, resetting the cursor and overlay transform. */
    public void loadFromOutput(ProcessorOutput output) {
        overlayOffsetX = 0;
        overlayOffsetY = 0;
        overlayScale = 1.0;
        overlayRotation = 0;
        overlayMirror = false;
        suppressAlignment = false;
        // A fresh drawing starts at fit-to-window so the user isn't left looking at an off-screen
        // region left over from a previous drawing's zoom/pan.
        viewZoom = 1.0;
        viewPanX = 0;
        viewPanY = 0;
        loadPathsPreservingOverlay(output);
    }

    /**
     * Reloads geometry from {@code output} (e.g. after an in-place optimize/reorder pass)
     * without disturbing the user's current pan/scale/rotation/mirror placement.
     */
    public void loadPathsPreservingOverlay(ProcessorOutput output) {
        allPaths.clear();
        pathLayer.clear();
        pathCommandId.clear();
        currentX = 0;
        currentY = 0;
        targetX = 0;
        targetY = 0;
        if (cursorAnimTimer != null) {
            cursorAnimTimer.stop();
        }

        layerColors.clear();
        List<Layer> layers = output.layers();
        for (int li = 0; li < layers.size(); li++) {
            layerColors.add(displayColorFor(layers.get(li).color(), li));
            for (Command cmd : layers.get(li).commands()) {
                if (cmd instanceof DrawCommand draw) {
                    List<Point2D> stroke = new ArrayList<>();
                    for (org.trostheide.gantry.model.Point p : draw.points) {
                        stroke.add(new Point2D(p.x(), p.y()));
                    }
                    if (!stroke.isEmpty()) {
                        allPaths.add(stroke);
                        pathLayer.add(li);
                        pathCommandId.add(draw.id);
                    }
                }
            }
        }
        // Default to showing every layer; the controller pushes the real selection right after.
        selectedLayers.clear();
        for (int li = 0; li < layers.size(); li++) {
            selectedLayers.add(li);
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

    /**
     * Highlights a single layer in the preview, ghosting the rest. {@code layerIndex} is an index
     * into the loaded output's layer list; {@code -1} restores the "all layers" view. The drawing's
     * position on the bed is unchanged — only how each layer is shaded — so the operator can see
     * exactly where the selected layer (i.e. the next pen) will draw relative to the whole piece.
     */
    /**
     * Sets which layers are shown in full colour. Layers not in {@code selected} are ghosted (drawn
     * dimmed) for context. Passing every layer index shows the whole drawing normally; passing a
     * subset highlights just those layers (e.g. the pens about to be used).
     */
    public void setSelectedLayers(Collection<Integer> selected) {
        selectedLayers.clear();
        if (selected != null) {
            selectedLayers.addAll(selected);
        }
        repaint();
    }

    /** Toggles per-layer colouring of the preview. When off, every layer draws in one colour. */
    public void setColorByLayer(boolean enabled) {
        this.colorByLayer = enabled;
        repaint();
    }

    /** The colour a given layer index is drawn in (used e.g. for legend swatches). */
    public Color colorForLayer(int layerIndex) {
        if (!colorByLayer || layerIndex < 0 || layerIndex >= layerColors.size()) {
            return DEFAULT_PATH;
        }
        return layerColors.get(layerIndex);
    }

    /**
     * Resolves a layer's display colour: its source {@code #rrggbb} if parseable and bright enough,
     * otherwise a distinct hue from {@link #FALLBACK_PALETTE}. A parseable-but-dark colour (e.g. pure
     * black, common for line art) is brightened toward readability rather than left to vanish into
     * the dark canvas.
     */
    private Color displayColorFor(String hex, int index) {
        Color parsed = parseHex(hex);
        if (parsed == null) {
            return FALLBACK_PALETTE[index % FALLBACK_PALETTE.length];
        }
        return ensureReadable(parsed, index);
    }

    private static Color parseHex(String hex) {
        if (hex == null) {
            return null;
        }
        String s = hex.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() == 3) {
            // Expand shorthand #abc -> #aabbcc.
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                sb.append(s.charAt(i)).append(s.charAt(i));
            }
            s = sb.toString();
        }
        if (s.length() != 6) {
            return null;
        }
        try {
            return new Color(Integer.parseInt(s.substring(0, 2), 16),
                    Integer.parseInt(s.substring(2, 4), 16),
                    Integer.parseInt(s.substring(4, 6), 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Keeps a layer colour distinguishable from the dark background: if perceived brightness is below
     * a floor, the colour is lightened toward white. A near-greyscale dark colour (e.g. black) would
     * brighten to grey and blur together with other dark layers, so those fall back to a palette hue.
     */
    private Color ensureReadable(Color c, int index) {
        double brightness = (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue());
        if (brightness >= 70) {
            return c;
        }
        int chroma = Math.max(c.getRed(), Math.max(c.getGreen(), c.getBlue()))
                - Math.min(c.getRed(), Math.min(c.getGreen(), c.getBlue()));
        if (chroma < 24) {
            // Effectively greyscale and dark: a distinct hue reads far better than grey-on-grey.
            return FALLBACK_PALETTE[index % FALLBACK_PALETTE.length];
        }
        // Coloured but dark: lift toward white while keeping the hue.
        double lift = 0.55;
        return new Color(
                (int) Math.round(c.getRed() + (255 - c.getRed()) * lift),
                (int) Math.round(c.getGreen() + (255 - c.getGreen()) * lift),
                (int) Math.round(c.getBlue() + (255 - c.getBlue()) * lift));
    }

    /** A dimmed version of {@code c} for ghosting non-selected layers (blended toward the canvas). */
    private static Color ghost(Color c) {
        double keep = 0.30;
        return new Color(
                (int) Math.round(c.getRed() * keep + CANVAS_BG.getRed() * (1 - keep)),
                (int) Math.round(c.getGreen() * keep + CANVAS_BG.getGreen() * (1 - keep)),
                (int) Math.round(c.getBlue() * keep + CANVAS_BG.getBlue() * (1 - keep)));
    }

    /** Updates the feed-rate override (percent) shown in the HUD. */
    public void setSpeedPercent(int percent) {
        this.speedPercent = percent;
        repaint();
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

    /**
     * Clamps a motor position to the bed for jog soft limits, using the same composited
     * swap/invert/origin geometry this panel draws the cursor with — so the soft stops always
     * align with the physical bed edges regardless of inverted/swapped axes or origin corner.
     */
    public double[] clampMotorToBed(double motorX, double motorY) {
        return SoftLimits.clampMotorToBed(motorX, motorY,
                effectiveInvertX(), effectiveInvertY(), effectiveSwap(),
                displayWidth(), displayHeight(), isOriginRight(), isOriginBottom());
    }

    private double[] physicalToScreen(double motorX, double motorY) {
        // This is the exact inverse of the jog logic in PlotterPanel.jog(): jogging maps a
        // desired on-screen direction (right/up) to a motor delta using the composited
        // swap/invert flags; rendering a motor position back to screen must invert that, so
        // the cursor, origin marker, axes and stations always track the same direction the
        // pen physically moves. Undo invert, then swap, to recover the screen-space offset
        // (dx = rightward, dy = upward) of this motor position from the origin corner.
        double dx = motorX;
        double dy = motorY;
        if (effectiveInvertY()) dy = -dy;
        if (effectiveInvertX()) dx = -dx;
        if (effectiveSwap()) {
            double t = dx; dx = dy; dy = t;
        }
        // Place relative to the machine-origin corner of the displayed bed. Screen Y grows
        // downward, so the upward component dy is subtracted.
        double originScreenX = isOriginRight() ? displayWidth() : 0;
        double originScreenY = isOriginBottom() ? displayHeight() : 0;
        return new double[] { originScreenX + dx, originScreenY - dy };
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
        // Apply flipY in exactly the same place PlotService.transformAndClamp() does (after
        // transform + alignment offset), so the previewed content lands in the same machine
        // frame that actually gets plotted — and therefore in the same frame the live head
        // position is reported in, keeping the cursor and the drawing in sync.
        if (flipY) {
            motor[1] = machineHeight - motor[1];
        }
        return motor;
    }

    /**
     * Returns the {x, y} motor-space (mm-from-origin) position of the content bounding box's
     * corner nearest the machine origin, including the current overlay transform. This is the
     * value surfaced to the numeric position fields.
     */
    public double[] getContentMotorMin() {
        if (allPaths.isEmpty()) {
            return new double[] { 0, 0 };
        }
        Point2D[] corners = {
            new Point2D(rawMinX, rawMinY), new Point2D(rawMaxX, rawMinY),
            new Point2D(rawMinX, rawMaxY), new Point2D(rawMaxX, rawMaxY)
        };
        double mMinX = Double.MAX_VALUE, mMinY = Double.MAX_VALUE;
        for (Point2D c : corners) {
            double[] m = transformPointToMotor(c);
            mMinX = Math.min(mMinX, m[0]);
            mMinY = Math.min(mMinY, m[1]);
        }
        return new double[] { mMinX, mMinY };
    }

    /**
     * Positions the content so its origin-nearest bounding-box corner lands at the given
     * motor coordinates (mm from origin), then re-clamps to the bed.
     */
    public void setContentMotorMin(double targetX, double targetY) {
        if (allPaths.isEmpty()) {
            return;
        }
        double[] cur = getContentMotorMin();
        applyMotorShift(targetX - cur[0], targetY - cur[1]);
        clampOverlayToBed();
        repaint();
        fireOverlayChange();
    }

    /**
     * Adjusts the raw-space overlay offset to translate the content by the given motor-space
     * delta (mm). Uses a finite-difference Jacobian because the raw→motor mapping involves
     * swap/invert/rotate, so a motor delta is not simply a raw delta.
     */
    private void applyMotorShift(double shiftMmX, double shiftMmY) {
        if (shiftMmX == 0 && shiftMmY == 0) {
            return;
        }
        double cx = (rawMinX + rawMaxX) / 2.0;
        double cy = (rawMinY + rawMaxY) / 2.0;
        double[] baseMotor = transformPointToMotor(new Point2D(cx, cy));
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
            overlayOffsetX += (shiftMmX * d - shiftMmY * b) / det;
            overlayOffsetY += (a * shiftMmY - c2 * shiftMmX) / det;
        }
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
        applyMotorShift(shiftMmX, shiftMmY);
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
        double fitScale = Math.min(scaleX, scaleY);
        if (fitScale <= 0)
            fitScale = 1.0;

        // Center the displayed bed in the panel
        double fitTx = 20 + (w - 40 - dw * fitScale) / 2.0;
        double fitTy = 20 + (h - 40 - dh * fitScale) / 2.0;

        // Fold the user viewport zoom/pan over the fit transform: a final pixel is
        // viewZoom * fitPixel + viewPan, i.e. content*(fitScale*viewZoom) + (viewZoom*fitTx+pan).
        double scale = fitScale * viewZoom;
        double tx = viewZoom * fitTx + viewPanX;
        double ty = viewZoom * fitTy + viewPanY;

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
        // Each layer is drawn in its own colour (so layers/pens are visually separable). Unselected
        // layers are ghosted (dimmed toward the background), so the operator can focus on a chosen
        // subset of layers without losing the surrounding context.
        g2.setStroke(new BasicStroke((float) (1.0 / scale)));

        // Draw ghosts first so the selected layers paint on top of them.
        for (int pass = 0; pass < 2; pass++) {
            boolean drawingSelected = (pass == 1);
            for (int i = 0; i < allPaths.size(); i++) {
                List<Point2D> path = allPaths.get(i);
                if (path.isEmpty()) {
                    continue;
                }
                int li = pathLayer.get(i);
                boolean selected = selectedLayers.contains(li);
                if (selected != drawingSelected) {
                    continue;
                }
                Color base = colorByLayer ? colorForLayer(li) : DEFAULT_PATH;
                g2.setColor(selected ? base : ghost(base));
                Path2D p2d = new Path2D.Double();
                double[] p0 = transformPoint(path.get(0));
                p2d.moveTo(p0[0], p0[1]);
                for (int j = 1; j < path.size(); j++) {
                    double[] pj = transformPoint(path.get(j));
                    p2d.lineTo(pj[0], pj[1]);
                }
                g2.draw(p2d);
            }
        }

        // --- Hatch-mode hover highlight: tint the closed region the next click would fill ---
        if (interactionMode == InteractionMode.HATCH
                && hoverRegionIndex >= 0 && hoverRegionIndex < allPaths.size()) {
            List<Point2D> hp = allPaths.get(hoverRegionIndex);
            if (hp.size() >= 3) {
                Path2D hi = new Path2D.Double();
                double[] h0 = transformPoint(hp.get(0));
                hi.moveTo(h0[0], h0[1]);
                for (int j = 1; j < hp.size(); j++) {
                    double[] hj = transformPoint(hp.get(j));
                    hi.lineTo(hj[0], hj[1]);
                }
                hi.closePath();
                g2.setColor(new Color(255, 210, 80, 70));
                g2.fill(hi);
                g2.setColor(new Color(255, 200, 50));
                g2.setStroke(new BasicStroke((float) (1.5 / scale)));
                g2.draw(hi);
            }
        }

        // --- Stroke-edit hover highlight: outline the stroke the next click would act on
        //     (red = delete, cyan = move) ---
        if ((interactionMode == InteractionMode.DELETE_STROKE || interactionMode == InteractionMode.MOVE_STROKE)
                && hoverStrokeIndex >= 0 && hoverStrokeIndex < allPaths.size()) {
            List<Point2D> hp = allPaths.get(hoverStrokeIndex);
            if (!hp.isEmpty()) {
                Path2D hi = new Path2D.Double();
                double[] h0 = transformPoint(hp.get(0));
                hi.moveTo(h0[0], h0[1]);
                for (int j = 1; j < hp.size(); j++) {
                    double[] hj = transformPoint(hp.get(j));
                    hi.lineTo(hj[0], hj[1]);
                }
                g2.setColor(interactionMode == InteractionMode.DELETE_STROKE
                        ? new Color(255, 80, 80) : new Color(90, 210, 230));
                g2.setStroke(new BasicStroke((float) (3.0 / scale)));
                g2.draw(hi);
            }
        }

        // --- Add-line preview: marker at the first point + rubber-band to the cursor ---
        if (interactionMode == InteractionMode.ADD_LINE && lineStart != null) {
            double[] a = transformPoint(new Point2D(lineStart[0], lineStart[1]));
            double[] b = screenToModel(lineHoverX, lineHoverY);
            g2.setColor(new Color(120, 220, 150));
            double rr = 3 / scale;
            g2.fill(new java.awt.geom.Ellipse2D.Double(a[0] - rr, a[1] - rr, rr * 2, rr * 2));
            if (b != null) {
                double[] bp = transformPoint(new Point2D(b[0], b[1]));
                g2.setStroke(new BasicStroke((float) (1.5 / scale)));
                g2.draw(new java.awt.geom.Line2D.Double(a[0], a[1], bp[0], bp[1]));
            }
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
                "Pos: %.1f, %.1f | Speed: %d%% | View: %.0f%% | Align: %s | Rot: %d | Origin: %s | %s",
                currentX, currentY, speedPercent, viewZoom * 100, canvasAlignment, dataRotation,
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

    /**
     * Returns the index (into {@link #stations}) of the station marker under the mouse, or
     * {@link #HANDLE_NONE} if none is within {@link #STATION_HIT_PX}. Markers are drawn in
     * g2 content space, so we map each station's machine-mm position the same way paint does
     * ({@code physicalToScreen} then the cached translate/scale) before comparing in pixels.
     */
    private int hitTestStation(int mouseX, int mouseY) {
        for (int i = 0; i < stations.size(); i++) {
            Station s = stations.get(i);
            double[] sc = physicalToScreen(s.x(), s.y());
            double px = sc[0] * paintScale + paintTx;
            double py = sc[1] * paintScale + paintTy;
            if (Math.hypot(mouseX - px, mouseY - py) <= STATION_HIT_PX) {
                return i;
            }
        }
        return HANDLE_NONE;
    }

    /**
     * Inverse of the station/cursor paint path: a mouse pixel back to a machine-mm position.
     * Undoes the cached translate/scale to reach g2 content space, then inverts
     * {@link #physicalToScreen} (reverse order: un-swap, then un-invert) to recover motor coords.
     */
    private double[] screenToPhysical(int mouseX, int mouseY) {
        double sx = (mouseX - paintTx) / paintScale;
        double sy = (mouseY - paintTy) / paintScale;
        double originScreenX = isOriginRight() ? displayWidth() : 0;
        double originScreenY = isOriginBottom() ? displayHeight() : 0;
        double dx = sx - originScreenX;
        double dy = originScreenY - sy;
        if (effectiveSwap()) {
            double t = dx; dx = dy; dy = t;
        }
        if (effectiveInvertX()) dx = -dx;
        if (effectiveInvertY()) dy = -dy;
        return new double[] { dx, dy };
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

    // ----- Click-to-hatch region hit-test -----

    /** Begins a viewport pan from the given press event. */
    private void startPan(MouseEvent e) {
        panning = true;
        panLastX = e.getX();
        panLastY = e.getY();
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
    }

    /** Updates the highlighted hover region (closed paths only, cheap) and repaints on change. */
    private void updateHoverRegion(int px, int py) {
        int ri = allPaths.isEmpty() ? -1 : findClosedRegionAt(px, py);
        if (ri != hoverRegionIndex) {
            hoverRegionIndex = ri;
            repaint();
        }
    }

    /** Pixel-distance threshold for clicking/hovering a stroke in stroke-edit modes. */
    private static final double STROKE_HIT_PX = 7.0;

    /** Updates the highlighted hover stroke (delete mode) and repaints on change. */
    private void updateHoverStroke(int px, int py) {
        int si = allPaths.isEmpty() ? -1 : nearestStrokeIndex(px, py);
        if (si != hoverStrokeIndex) {
            hoverStrokeIndex = si;
            repaint();
        }
    }

    /** Index of the stroke whose nearest segment is within {@link #STROKE_HIT_PX} of the pixel, or -1. */
    private int nearestStrokeIndex(int px, int py) {
        int best = -1;
        double bestD = STROKE_HIT_PX;
        for (int i = 0; i < allPaths.size(); i++) {
            List<Point2D> path = allPaths.get(i);
            if (path.isEmpty()) {
                continue;
            }
            double[] prev = pixelOf(path.get(0));
            double d = path.size() == 1 ? Math.hypot(px - prev[0], py - prev[1]) : Double.MAX_VALUE;
            for (int j = 1; j < path.size(); j++) {
                double[] cur = pixelOf(path.get(j));
                d = Math.min(d, distToSegment(px, py, prev[0], prev[1], cur[0], cur[1]));
                prev = cur;
            }
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** Screen pixel of a model-space stroke point (same transform paint uses). */
    private double[] pixelOf(Point2D p) {
        double[] c = transformPoint(p);
        return new double[]{c[0] * paintScale + paintTx, c[1] * paintScale + paintTy};
    }

    private static double distToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double len2 = dx * dx + dy * dy;
        if (len2 < 1e-9) {
            return Math.hypot(px - x1, py - y1);
        }
        double t = ((px - x1) * dx + (py - y1) * dy) / len2;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    /**
     * Index (into {@link #allPaths}) of the smallest closed region whose on-screen shape contains
     * the pixel {@code (mx, my)}, or {@code -1} if none. "Smallest" so clicking nested regions
     * picks the innermost. Geometry is tested in pixel space (each point pushed through the same
     * {@link #transformPoint} + cached paint transform used to draw it), so it's correct at any
     * zoom/pan; {@link java.awt.geom.Path2D#contains} treats the path as implicitly closed.
     */
    private int findClosedRegionAt(int mx, int my) {
        int best = -1;
        double bestArea = Double.MAX_VALUE;
        for (int i = 0; i < allPaths.size(); i++) {
            List<Point2D> path = allPaths.get(i);
            if (path.size() < 3 || !isClosedRegion(path)) {
                continue;
            }
            Path2D px = pixelPath(path);
            if (px.contains(mx, my)) {
                Rectangle2D b = px.getBounds2D();
                double area = b.getWidth() * b.getHeight();
                if (area < bestArea) {
                    bestArea = area;
                    best = i;
                }
            }
        }
        return best;
    }

    /**
     * Whether a polyline reads as an enclosed region: its endpoints meet within a small fraction of
     * its own size. Filters out clearly-open contours (a single un-closed stroke) that
     * {@code contains} would otherwise treat as a fillable area.
     */
    private static boolean isClosedRegion(List<Point2D> path) {
        Point2D a = path.get(0);
        Point2D b = path.get(path.size() - 1);
        double minX = a.x(), maxX = a.x(), minY = a.y(), maxY = a.y();
        for (Point2D p : path) {
            minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
            minY = Math.min(minY, p.y()); maxY = Math.max(maxY, p.y());
        }
        double diag = Math.hypot(maxX - minX, maxY - minY);
        double tol = Math.max(0.5, 0.02 * diag); // 0.5mm floor, else 2% of the region's diagonal
        return Math.hypot(a.x() - b.x(), a.y() - b.y()) <= tol;
    }

    /** The region as a closed {@link Path2D} in screen pixels (for hit-testing). */
    private Path2D pixelPath(List<Point2D> path) {
        Path2D p2d = new Path2D.Double();
        double[] p0 = transformPoint(path.get(0));
        p2d.moveTo(p0[0] * paintScale + paintTx, p0[1] * paintScale + paintTy);
        for (int j = 1; j < path.size(); j++) {
            double[] pj = transformPoint(path.get(j));
            p2d.lineTo(pj[0] * paintScale + paintTx, pj[1] * paintScale + paintTy);
        }
        p2d.closePath();
        return p2d;
    }

    /** All loaded strokes as model-space point arrays, for flood-fill enclosure detection. */
    private List<double[][]> strokesAsModel() {
        List<double[][]> out = new ArrayList<>();
        for (List<Point2D> path : allPaths) {
            if (path.size() < 2) {
                continue;
            }
            double[][] s = new double[path.size()][2];
            for (int i = 0; i < path.size(); i++) {
                s[i][0] = path.get(i).x();
                s[i][1] = path.get(i).y();
            }
            out.add(s);
        }
        return out;
    }

    /** Layer of the stroke whose nearest vertex is closest to {@code (mx, my)} (model mm), or 0. */
    private int nearestStrokeLayer(double mx, double my) {
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < allPaths.size(); i++) {
            for (Point2D p : allPaths.get(i)) {
                double d = Math.hypot(p.x() - mx, p.y() - my);
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
        }
        return best >= 0 ? pathLayer.get(best) : 0;
    }

    /** Screen pixel of a model-space point, via the same forward transform paint uses. */
    private double[] modelToPixel(double mx, double my) {
        double[] c = transformPoint(new Point2D(mx, my));
        return new double[]{c[0] * paintScale + paintTx, c[1] * paintScale + paintTy};
    }

    /**
     * Inverse of {@link #modelToPixel}: a screen pixel back to a raw model (mm) point. The
     * model→pixel map is affine, so it's recovered exactly by sampling three reference points and
     * inverting the resulting 2×2 system (same technique as {@link #screenDeltaToMm}). Returns
     * {@code null} only if the transform is degenerate.
     */
    private double[] screenToModel(int px, int py) {
        double[] o = modelToPixel(0, 0);
        double[] ux = modelToPixel(1, 0);
        double[] uy = modelToPixel(0, 1);
        double a = ux[0] - o[0], b = uy[0] - o[0];
        double c = ux[1] - o[1], d = uy[1] - o[1];
        double det = a * d - b * c;
        if (Math.abs(det) < 1e-12) {
            return null;
        }
        double rx = px - o[0], ry = py - o[1];
        double mx = (rx * d - b * ry) / det;
        double my = (a * ry - rx * c) / det;
        return new double[]{mx, my};
    }

    /** The region as a closed {@link Path2D} in raw model (mm) space (for hatch generation). */
    private static Path2D rawRegionPath(List<Point2D> path) {
        Path2D p2d = new Path2D.Double();
        p2d.moveTo(path.get(0).x(), path.get(0).y());
        for (int j = 1; j < path.size(); j++) {
            p2d.lineTo(path.get(j).x(), path.get(j).y());
        }
        p2d.closePath();
        return p2d;
    }

    // ----- Internal Types -----
    private record Point2D(double x, double y) {
    }
}
