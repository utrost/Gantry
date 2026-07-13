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

    List<List<Point2D>> allPaths = new ArrayList<>();

    /**
     * Layer index (into the loaded {@code ProcessorOutput.layers()}) that produced each entry in
     * {@link #allPaths}, kept in lockstep. Lets the panel draw the selected layers in full colour
     * while ghosting the rest.
     */
    final List<Integer> pathLayer = new ArrayList<>();

    /** Command id of each entry in {@link #allPaths}, in lockstep — lets a clicked stroke map back to
     *  its {@code DrawCommand} for deletion (Tier A stroke editing). */
    final List<Integer> pathCommandId = new ArrayList<>();

    /** When true, draw dashed pen-up travel segments between consecutive strokes in the same layer. */
    boolean showTravelOverlay = false;
    /** Cached pen-down distance (mm) across all paths; 0 until paths are loaded. */
    double travelPenDownMm;
    /** Cached total travel distance (pen-down + pen-up mm); 0 until paths are loaded. */
    double travelTotalMm;

    /**
     * Indices of the layers currently selected for display. Selected layers draw in full colour;
     * unselected layers are ghosted (dimmed) for context. Defaults to "all layers" on load; the
     * controller ({@code PlotterPanel}) replaces it whenever the user (de)selects layers.
     */
    final Set<Integer> selectedLayers = new HashSet<>();

    /**
     * Display colour for each layer (indexed by layer position), derived from the layer's source
     * {@code #rrggbb} and brightened where needed so it stays readable against the dark canvas. A
     * layer with no known colour gets a distinct fallback hue so layers stay
     * visually separable. Kept in lockstep with the loaded output's layer list.
     */
    final List<Color> layerColors = new ArrayList<>();
    boolean colorByLayer = true;

    /** The canvas background; layer colours are floored against this so nothing vanishes into it. */
    static final Color CANVAS_BG = CanvasPalette.BACKGROUND;

    /** Default highlight colour used when per-layer colouring is off or a layer has no colour. */
    static final Color DEFAULT_PATH = CanvasPalette.DEFAULT_PATH;

    // Current head position (Physical coords). currentX/Y is the *displayed*
    // position, which is eased toward targetX/Y so motion looks smooth even
    // when position updates arrive in discrete steps.
    double currentX = 0;
    double currentY = 0;
    double targetX = 0;
    double targetY = 0;
    javax.swing.Timer cursorAnimTimer;

    // Current feed-rate override reported by the backend, in percent (100 = nominal).
    int speedPercent = 100;

    // Machine Bounds (Fixed by Settings - A3 Portrait default)
    double machineWidth = 297; // A3 Portrait width (short edge)
    double machineHeight = 420; // A3 Portrait height (long edge)

    // Raw Content Bounds (from JSON, before rotation)
    double rawMinX = 0, rawMaxX = 0, rawMinY = 0, rawMaxY = 0;

    // Alignment Offset (calculated based on alignment choice)
    double alignOffsetX = 0;
    double alignOffsetY = 0;

    // User Settings
    String canvasAlignment = "Top Right"; // Default: align to physical origin
    int dataRotation = 0; // 0, 90, 180, 270 degrees
    String orientation = "Portrait";

    // Driver Simulation Flags
    // Fully-composited transform (machine origin + portrait-swap + extra flags already
    // folded in by GantryConfig.toPlotSettings()) — the single source of truth shared with
    // jogging and G-code generation, so the preview/cursor and the actual hardware always agree.
    boolean effSwapXY = false;
    boolean effInvertX = false;
    boolean effInvertY = false;
    double paddingX = 0;
    double paddingY = 0;

    // Machine origin corner (determines how motor coords map to screen)
    String machineOrigin = "Top-Right";
    boolean flipY = false;

    // Overlay transform for interactive drag/resize (in raw content space)
    double overlayOffsetX = 0, overlayOffsetY = 0;
    double overlayScale = 1.0;
    int overlayRotation = 0;       // quarter turns: 0, 90, 180, 270
    boolean overlayMirror = false; // horizontal flip

    // When true, alignment offset is suppressed (baked coords already encode position)
    boolean suppressAlignment = false;

    // Cached paint-time values for screen-to-mm inversion
    double paintScale = 1.0;
    double paintTx = 0, paintTy = 0;

    // User viewport zoom/pan, applied *on top of* the fit-to-window transform and folded into
    // paintScale/paintTx/paintTy each paint. Because every hit-test already inverts those cached
    // values, the click→motor mapping stays correct at any zoom/pan with no parallel transform.
    double viewZoom = 1.0;
    double viewPanX = 0, viewPanY = 0;
    static final double VIEW_ZOOM_MIN = 0.2, VIEW_ZOOM_MAX = 50.0;

    // Pan gesture state (middle-drag anywhere, or left-drag on empty canvas).
    boolean panning = false;
    int panLastX, panLastY;

    /**
     * Exclusive left-click interaction modes on the canvas. {@code NONE} is the default place/resize
     * behaviour; the others repurpose the left click for hatching or stroke editing (Tier A).
     */
    public enum InteractionMode { NONE, HATCH, DELETE_STROKE, ADD_LINE, MOVE_STROKE }

    InteractionMode interactionMode = InteractionMode.NONE;
    RegionHatchListener regionHatchListener;
    StrokeEditListener strokeEditListener;
    Runnable hatchStyleAction;
    java.util.function.Consumer<InteractionMode> interactionModeListener;
    JCheckBoxMenuItem ctxHatchItem, ctxDeleteItem, ctxAddLineItem, ctxMoveItem;
    // Closed region currently under the cursor in hatch mode, highlighted as a pick preview (-1 none).
    int hoverRegionIndex = -1;
    // Flood-filled enclosure under the cursor (model space) when not over a closed path — computed on
    // a short debounce so the multi-stroke "fill this area" target also previews. Null when none.
    Path2D hoverEnclosedModel;
    javax.swing.Timer enclosedHoverTimer;
    int enclosedHoverX, enclosedHoverY;
    // Stroke under the cursor in delete mode (index into allPaths), highlighted in red (-1 none).
    int hoverStrokeIndex = -1;
    // First clicked point (model mm) while drawing a line in ADD_LINE mode, or null between lines.
    double[] lineStart;
    int lineHoverX, lineHoverY; // last cursor pixel, for the rubber-band preview
    // Live drag state for MOVE_STROKE: the stroke being dragged (index into allPaths), its original
    // points (model mm), the press point, and whether it actually moved.
    int dragStrokeIndex = -1;
    List<Point2D> dragStrokeOrig;
    double[] dragStrokePressModel;
    boolean dragStrokeMoved;
    // Pixel position of the last context-menu trigger, for "Hatch area here" / "Clear hatch here".
    int lastPopupX, lastPopupY;

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
        hoverEnclosedModel = null;
        if (enclosedHoverTimer != null) {
            enclosedHoverTimer.stop();
        }
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
    HatchTarget resolveHatchTarget(int px, int py) {
        int ri = interaction.findClosedRegionAt(px, py);
        if (ri >= 0) {
            return new HatchTarget(interaction.rawRegionPath(allPaths.get(ri)), pathLayer.get(ri));
        }
        double[] seed = interaction.screenToModel(px, py);
        if (seed != null) {
            Path2D enclosed = EnclosedRegion.fromSeed(interaction.strokesAsModel(), seed[0], seed[1]);
            if (enclosed != null) {
                return new HatchTarget(enclosed, interaction.nearestStrokeLayer(seed[0], seed[1]));
            }
        }
        return null;
    }

    record HatchTarget(Path2D region, int layerIndex) {
    }

    // Interactive drag state
    static final int HANDLE_NONE = -1;
    static final int HANDLE_MOVE = 0;
    static final int HANDLE_NW = 1, HANDLE_N = 2, HANDLE_NE = 3;
    static final int HANDLE_W = 4, HANDLE_E = 5;
    static final int HANDLE_SW = 6, HANDLE_S = 7, HANDLE_SE = 8;
    static final double HANDLE_SIZE_PX = 7;

    int dragHandle = HANDLE_NONE;
    double dragStartScreenX, dragStartScreenY;
    double dragStartOverlayOX, dragStartOverlayOY;
    double dragStartOverlayScale;

    // Listener for overlay changes (notifies the containing panel)
    Runnable overlayChangeListener;

    // Listener invoked when the user removes the drawing via the context menu, so the containing
    // panel can drop its own loaded-output state (otherwise a plot/export would still use it).
    Runnable removeDrawingListener;

    public void setOverlayChangeListener(Runnable listener) {
        this.overlayChangeListener = listener;
    }

    public void setRemoveDrawingListener(Runnable listener) {
        this.removeDrawingListener = listener;
    }

    /** Clears the loaded drawing and resets the overlay transform, leaving an empty bed. */
    public void clearDrawing() {
        allPaths.clear();
        travelPenDownMm = 0;
        travelTotalMm = 0;
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

    void fireOverlayChange() {
        if (overlayChangeListener != null) overlayChangeListener.run();
    }

    // Refill Stations (loaded from config)
    public record Station(String name, double x, double y) {
    }

    List<Station> stations = new ArrayList<>();

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

    StationEditListener stationEditListener;

    public void setStationEditListener(StationEditListener listener) {
        this.stationEditListener = listener;
    }

    // Station-drag state: index into `stations` of the marker being dragged, or -1.
    int draggingStation = -1;
    // Machine-mm location of the last popup trigger, used by "Add station here".
    double[] lastPopupMm;
    /** Pixel radius within which a click grabs a station marker. */
    static final double STATION_HIT_PX = 9;

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
    void zoomAtCursor(double factor, int mx, int my) {
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

    boolean isOriginRight() { return machineOrigin.contains("Right"); }
    boolean isOriginBottom() { return machineOrigin.contains("Bottom"); }
    boolean needsAxisSwap() { return isPortrait() && machineWidth > machineHeight; }

    public VisualizationPanel() {
        CanvasInteractionController.install(this);
    }

    /** Context-menu entries that only make sense with a loaded drawing (toggled per right-click). */
    List<JMenuItem> drawingMenuItems = new ArrayList<>();

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
            layerColors.add(CanvasPalette.displayColor(layers.get(li).color(), li));
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

        computeTravelStats();
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

    /** Shows or hides dashed pen-up travel lines between consecutive strokes (same layer). */
    public void setShowTravelOverlay(boolean show) {
        showTravelOverlay = show;
        repaint();
    }

    /** Whether the travel overlay is currently on. */
    public boolean isShowTravelOverlay() {
        return showTravelOverlay;
    }

    /** The colour a given layer index is drawn in (used e.g. for legend swatches). */
    public Color colorForLayer(int layerIndex) {
        if (!colorByLayer || layerIndex < 0 || layerIndex >= layerColors.size()) {
            return DEFAULT_PATH;
        }
        return layerColors.get(layerIndex);
    }

    /** A dimmed version of {@code c} for ghosting non-selected layers (blended toward the canvas). */
    static Color ghost(Color c) { return CanvasPalette.ghost(c); }

    /** Updates the feed-rate override (percent) shown in the HUD. */
    public void setSpeedPercent(int percent) {
        this.speedPercent = percent;
        repaint();
    }

    void animateCursorStep() {
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
    boolean effectiveSwap() { return effSwapXY; }
    boolean effectiveInvertX() { return effInvertX; }
    boolean effectiveInvertY() { return effInvertY; }

    double[] contentBoundsArray() {
        return new double[] { rawMinX, rawMaxX, rawMinY, rawMaxY };
    }

    /** Computes and caches pen-down and total travel distances (mm) across all loaded paths. */
    void computeTravelStats() {
        travelPenDownMm = 0;
        double penUpMm = 0;
        for (int i = 0; i < allPaths.size(); i++) {
            List<Point2D> path = allPaths.get(i);
            for (int j = 1; j < path.size(); j++) {
                Point2D a = path.get(j - 1), b = path.get(j);
                travelPenDownMm += Math.hypot(b.x() - a.x(), b.y() - a.y());
            }
            if (i + 1 < allPaths.size()
                    && pathLayer.get(i).equals(pathLayer.get(i + 1))
                    && !path.isEmpty() && !allPaths.get(i + 1).isEmpty()) {
                Point2D end = path.get(path.size() - 1);
                Point2D next = allPaths.get(i + 1).get(0);
                penUpMm += Math.hypot(next.x() - end.x(), next.y() - end.y());
            }
        }
        travelTotalMm = travelPenDownMm + penUpMm;
    }

    /**
     * Recalculate alignment offset using the shared {@link CoordinateTransform} utility.
     * MUST produce the same result as {@link org.trostheide.gantry.app.plot.PlotService}.
     */
    void recalculateTransform() {
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

        if (suppressAlignment || canvasAlignment == null) {
            alignOffsetX = 0;
            alignOffsetY = 0;
        } else {
            double[] offset = CoordinateTransform.calculateAlignmentOffset(
                    canvasAlignment, contentBoundsArray(),
                    machineWidth, machineHeight,
                    effectiveSwap(), effectiveInvertX(), effectiveInvertY(),
                    dataRotation, isOriginRight(), isOriginBottom(), flipY,
                    paddingX, paddingY);
            alignOffsetX = offset[0];
            alignOffsetY = offset[1];
        }
    }

    boolean isPortrait() {
        return "Portrait".equals(orientation);
    }

    double displayWidth() {
        return effectiveSwap() ? machineHeight : machineWidth;
    }

    double displayHeight() {
        return effectiveSwap() ? machineWidth : machineHeight;
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

    double[] physicalToScreen(double motorX, double motorY) {
        return CoordinateTransform.physicalToScreen(motorX, motorY,
                effectiveSwap(), isOriginRight(), isOriginBottom(),
                machineWidth, machineHeight);
    }

    /**
     * Full pipeline: Raw Point -> Screen Point
     * Overlay transform (scale + offset) is applied in raw content space before the rest of
     * the pipeline.
     */
    double[] transformPoint(Point2D rawPoint) {
        double[] motor = transformPointToMotor(rawPoint);
        return physicalToScreen(motor[0], motor[1]);
    }

    double[] transformPointToMotor(Point2D rawPoint) {
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
    void applyMotorShift(double shiftMmX, double shiftMmY) {
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

    void clampOverlayToBed() {
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
        CanvasRenderer.paint(this, (Graphics2D) g);
    }



    final CanvasInteractionGeometry interaction = new CanvasInteractionGeometry(this);

    // ----- Internal Types -----
    record Point2D(double x, double y) {
    }
}
