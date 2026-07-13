package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.model.CoordinateTransform;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;

final class CanvasInteractionGeometry {
    private final VisualizationPanel panel;

    CanvasInteractionGeometry(VisualizationPanel panel) { this.panel = panel; }

// ----- Interactive Drag/Resize Helpers -----

double[] getContentScreenBoundsPixel() {
    if (panel.allPaths.isEmpty()) return new double[]{0, 0, 0, 0};
    VisualizationPanel.Point2D[] corners = {
        new VisualizationPanel.Point2D(panel.rawMinX, panel.rawMinY), new VisualizationPanel.Point2D(panel.rawMaxX, panel.rawMinY),
        new VisualizationPanel.Point2D(panel.rawMinX, panel.rawMaxY), new VisualizationPanel.Point2D(panel.rawMaxX, panel.rawMaxY)
    };
    double sMinX = Double.MAX_VALUE, sMinY = Double.MAX_VALUE;
    double sMaxX = -Double.MAX_VALUE, sMaxY = -Double.MAX_VALUE;
    for (VisualizationPanel.Point2D c : corners) {
        double[] sc = panel.transformPoint(c);
        // Convert from machine-space to screen pixels
        double px = sc[0] * panel.paintScale + panel.paintTx;
        double py = sc[1] * panel.paintScale + panel.paintTy;
        sMinX = Math.min(sMinX, px); sMaxX = Math.max(sMaxX, px);
        sMinY = Math.min(sMinY, py); sMaxY = Math.max(sMaxY, py);
    }
    return new double[]{sMinX, sMinY, sMaxX, sMaxY};
}

int hitTestHandle(int mouseX, int mouseY) {
    double[] bb = getContentScreenBoundsPixel();
    double x0 = bb[0], y0 = bb[1], x1 = bb[2], y1 = bb[3];
    double mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
    double ht = VisualizationPanel.HANDLE_SIZE_PX + 3;

    double[][] handles = {
        {x0, y0}, {mx, y0}, {x1, y0},
        {x0, my}, {x1, my},
        {x0, y1}, {mx, y1}, {x1, y1}
    };
    int[] handleIds = {VisualizationPanel.HANDLE_NW, VisualizationPanel.HANDLE_N, VisualizationPanel.HANDLE_NE, VisualizationPanel.HANDLE_W, VisualizationPanel.HANDLE_E, VisualizationPanel.HANDLE_SW, VisualizationPanel.HANDLE_S, VisualizationPanel.HANDLE_SE};

    for (int i = 0; i < handles.length; i++) {
        if (Math.abs(mouseX - handles[i][0]) <= ht && Math.abs(mouseY - handles[i][1]) <= ht) {
            return handleIds[i];
        }
    }

    if (mouseX >= x0 - 2 && mouseX <= x1 + 2 && mouseY >= y0 - 2 && mouseY <= y1 + 2) {
        return VisualizationPanel.HANDLE_MOVE;
    }

    return VisualizationPanel.HANDLE_NONE;
}

/**
 * Returns the index (into {@link #panel.stations}) of the station marker under the mouse, or
 * {@link #VisualizationPanel.HANDLE_NONE} if none is within {@link #STATION_HIT_PX}. Markers are drawn in
 * g2 content space, so we map each station's machine-mm position the same way paint does
 * ({@code panel.physicalToScreen} then the cached translate/scale) before comparing in pixels.
 */
int hitTestStation(int mouseX, int mouseY) {
    for (int i = 0; i < panel.stations.size(); i++) {
        VisualizationPanel.Station s = panel.stations.get(i);
        double[] sc = panel.physicalToScreen(s.x(), s.y());
        double px = sc[0] * panel.paintScale + panel.paintTx;
        double py = sc[1] * panel.paintScale + panel.paintTy;
        if (Math.hypot(mouseX - px, mouseY - py) <= VisualizationPanel.STATION_HIT_PX) {
            return i;
        }
    }
    return VisualizationPanel.HANDLE_NONE;
}

/**
 * Inverse of the station/cursor paint path: a mouse pixel back to a machine-mm position.
 * Undoes the cached translate/scale to reach canvas space, then uses the shared exact inverse of
 * {@link #panel.physicalToScreen} to recover bounded motor coordinates.
 */
double[] screenToPhysical(int mouseX, int mouseY) {
    double sx = (mouseX - panel.paintTx) / panel.paintScale;
    double sy = (mouseY - panel.paintTy) / panel.paintScale;
    return CoordinateTransform.screenToPhysical(sx, sy,
            panel.effectiveSwap(), panel.isOriginRight(), panel.isOriginBottom(),
            panel.machineWidth, panel.machineHeight);
}

Cursor cursorForHandle(int handle) {
    switch (handle) {
        case VisualizationPanel.HANDLE_NW: return Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
        case VisualizationPanel.HANDLE_NE: return Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
        case VisualizationPanel.HANDLE_SW: return Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
        case VisualizationPanel.HANDLE_SE: return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
        case VisualizationPanel.HANDLE_N:  return Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
        case VisualizationPanel.HANDLE_S:  return Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
        case VisualizationPanel.HANDLE_W:  return Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
        case VisualizationPanel.HANDLE_E:  return Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
        case VisualizationPanel.HANDLE_MOVE: return Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
        default: return Cursor.getDefaultCursor();
    }
}

double[] screenDeltaToMm(double dxPx, double dyPx) {
    // Screen pixels -> machine-space mm delta
    // The raw content space maps through the transform pipeline to screen.
    // For dragging, we need the inverse: screen delta -> raw content delta.
    // We use finite differences: transform a small offset and measure the screen effect.
    double cx = (panel.rawMinX + panel.rawMaxX) / 2.0;
    double cy = (panel.rawMinY + panel.rawMaxY) / 2.0;
    double[] base = panel.transformPoint(new VisualizationPanel.Point2D(cx, cy));
    double[] dxRef = panel.transformPoint(new VisualizationPanel.Point2D(cx + 1, cy));
    double[] dyRef = panel.transformPoint(new VisualizationPanel.Point2D(cx, cy + 1));

    double screenPerMmX_x = (dxRef[0] - base[0]) * panel.paintScale;
    double screenPerMmX_y = (dxRef[1] - base[1]) * panel.paintScale;
    double screenPerMmY_x = (dyRef[0] - base[0]) * panel.paintScale;
    double screenPerMmY_y = (dyRef[1] - base[1]) * panel.paintScale;

    // Solve 2x2 system: [screenPerMmX_x, screenPerMmY_x] [mmX]   [dxPx]
    //                    [screenPerMmX_y, screenPerMmY_y] [mmY] = [dyPx]
    double det = screenPerMmX_x * screenPerMmY_y - screenPerMmX_y * screenPerMmY_x;
    if (Math.abs(det) < 1e-10) return new double[]{0, 0};

    double mmX = (dxPx * screenPerMmY_y - dyPx * screenPerMmY_x) / det;
    double mmY = (screenPerMmX_x * dyPx - screenPerMmX_y * dxPx) / det;
    return new double[]{mmX, mmY};
}

void handleResize(int handle, double dxPx, double dyPx) {
    // Compute scale change from drag distance
    double[] bb = getContentScreenBoundsPixel();
    double bbW = bb[2] - bb[0];
    double bbH = bb[3] - bb[1];
    if (bbW < 1 || bbH < 1) return;

    double scaleFactorX = 1, scaleFactorY = 1;

    switch (handle) {
        case VisualizationPanel.HANDLE_SE: scaleFactorX = (bbW + dxPx) / bbW; scaleFactorY = (bbH + dyPx) / bbH; break;
        case VisualizationPanel.HANDLE_NW: scaleFactorX = (bbW - dxPx) / bbW; scaleFactorY = (bbH - dyPx) / bbH; break;
        case VisualizationPanel.HANDLE_NE: scaleFactorX = (bbW + dxPx) / bbW; scaleFactorY = (bbH - dyPx) / bbH; break;
        case VisualizationPanel.HANDLE_SW: scaleFactorX = (bbW - dxPx) / bbW; scaleFactorY = (bbH + dyPx) / bbH; break;
        case VisualizationPanel.HANDLE_E:  scaleFactorX = (bbW + dxPx) / bbW; scaleFactorY = scaleFactorX; break;
        case VisualizationPanel.HANDLE_W:  scaleFactorX = (bbW - dxPx) / bbW; scaleFactorY = scaleFactorX; break;
        case VisualizationPanel.HANDLE_S:  scaleFactorY = (bbH + dyPx) / bbH; scaleFactorX = scaleFactorY; break;
        case VisualizationPanel.HANDLE_N:  scaleFactorY = (bbH - dyPx) / bbH; scaleFactorX = scaleFactorY; break;
    }

    // Use uniform scale (average) to keep aspect ratio
    double scaleFactor = (scaleFactorX + scaleFactorY) / 2.0;
    scaleFactor = Math.max(0.05, Math.min(scaleFactor, 20.0));

    panel.overlayScale = panel.dragStartOverlayScale * scaleFactor;
}

// ----- Click-to-hatch region hit-test -----

/** Begins a viewport pan from the given press event. */
void startPan(MouseEvent e) {
    panel.panning = true;
    panel.panLastX = e.getX();
    panel.panLastY = e.getY();
    panel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
}

/**
 * Updates the hatch-mode pick preview: the closed path under the cursor (cheap) or, if none, a
 * debounced flood-fill enclosure (so multi-stroke "fill this area" targets — e.g. a freshly
 * bridged region — also highlight, and a leaky boundary visibly shows no highlight).
 */
void updateHoverRegion(int px, int py) {
    int ri = panel.allPaths.isEmpty() ? -1 : findClosedRegionAt(px, py);
    if (ri != panel.hoverRegionIndex) {
        panel.hoverRegionIndex = ri;
        panel.repaint();
    }
    if (ri >= 0) {
        if (panel.enclosedHoverTimer != null) {
            panel.enclosedHoverTimer.stop();
        }
        if (panel.hoverEnclosedModel != null) {
            panel.hoverEnclosedModel = null;
            panel.repaint();
        }
    } else {
        panel.enclosedHoverX = px;
        panel.enclosedHoverY = py;
        if (panel.enclosedHoverTimer == null) {
            panel.enclosedHoverTimer = new javax.swing.Timer(160, e -> computeEnclosedHover());
            panel.enclosedHoverTimer.setRepeats(false);
        }
        panel.enclosedHoverTimer.restart();
    }
}

void computeEnclosedHover() {
    Path2D enc = null;
    if (panel.interactionMode == VisualizationPanel.InteractionMode.HATCH && !panel.allPaths.isEmpty()) {
        double[] seed = screenToModel(panel.enclosedHoverX, panel.enclosedHoverY);
        if (seed != null) {
            enc = EnclosedRegion.fromSeed(strokesAsModel(), seed[0], seed[1]);
        }
    }
    panel.hoverEnclosedModel = enc;
    panel.repaint();
}

/** Maps a model-space path to content space (for drawing the enclosure highlight). */
Path2D modelPathToContent(Path2D modelPath) {
    Path2D content = new Path2D.Double();
    java.awt.geom.PathIterator it = modelPath.getPathIterator(null);
    double[] c = new double[6];
    while (!it.isDone()) {
        int t = it.currentSegment(c);
        if (t == java.awt.geom.PathIterator.SEG_MOVETO) {
            double[] p = panel.transformPoint(new VisualizationPanel.Point2D(c[0], c[1]));
            content.moveTo(p[0], p[1]);
        } else if (t == java.awt.geom.PathIterator.SEG_LINETO) {
            double[] p = panel.transformPoint(new VisualizationPanel.Point2D(c[0], c[1]));
            content.lineTo(p[0], p[1]);
        } else if (t == java.awt.geom.PathIterator.SEG_CLOSE) {
            content.closePath();
        }
        it.next();
    }
    return content;
}

/** Pixel-distance threshold for clicking/hovering a stroke in stroke-edit modes. */
static final double STROKE_HIT_PX = 7.0;

/** Updates the highlighted hover stroke (delete mode) and repaints on change. */
void updateHoverStroke(int px, int py) {
    int si = panel.allPaths.isEmpty() ? -1 : nearestStrokeIndex(px, py);
    if (si != panel.hoverStrokeIndex) {
        panel.hoverStrokeIndex = si;
        panel.repaint();
    }
}

/** Index of the stroke whose nearest segment is within {@link #panel.STROKE_HIT_PX} of the pixel, or -1. */
int nearestStrokeIndex(int px, int py) {
    int best = -1;
    double bestD = STROKE_HIT_PX;
    for (int i = 0; i < panel.allPaths.size(); i++) {
        List<VisualizationPanel.Point2D> path = panel.allPaths.get(i);
        if (path.isEmpty()) {
            continue;
        }
        double[] prev = pixelOf(path.get(0));
        double d = path.size() == 1 ? Math.hypot(px - prev[0], py - prev[1]) : Double.MAX_VALUE;
        for (int j = 1; j < path.size(); j++) {
            double[] cur = pixelOf(path.get(j));
            d = Math.min(d, CanvasGeometry.distanceToSegment(px, py, prev[0], prev[1], cur[0], cur[1]));
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
double[] pixelOf(VisualizationPanel.Point2D p) {
    double[] c = panel.transformPoint(p);
    return new double[]{c[0] * panel.paintScale + panel.paintTx, c[1] * panel.paintScale + panel.paintTy};
}


/** Pixel snap radius when placing an added line, so a bridge actually touches existing strokes. */
static final double SNAP_PX = 10.0;

/**
 * The model point a click at {@code (px, py)} should use when adding a line: snapped to the
 * nearest point on any existing stroke if within {@link #panel.SNAP_PX} (so a gap-bridging line
 * connects exactly and the area seals), otherwise the plain {@link #screenToModel}.
 */
double[] snapPoint(int px, int py) {
    double bestD = SNAP_PX;
    double bestX = 0, bestY = 0;
    boolean found = false;
    for (List<VisualizationPanel.Point2D> path : panel.allPaths) {
        if (path.isEmpty()) {
            continue;
        }
        double[] prev = pixelOf(path.get(0));
        for (int j = 1; j < path.size(); j++) {
            double[] cur = pixelOf(path.get(j));
            double[] cp = CanvasGeometry.closestOnSegment(px, py, prev[0], prev[1], cur[0], cur[1]);
            double d = Math.hypot(px - cp[0], py - cp[1]);
            if (d < bestD) {
                bestD = d;
                bestX = cp[0];
                bestY = cp[1];
                found = true;
            }
            prev = cur;
        }
    }
    return screenToModel((int) Math.round(found ? bestX : px), (int) Math.round(found ? bestY : py));
}

/**
 * Index (into {@link #panel.allPaths}) of the smallest closed region whose on-screen shape contains
 * the pixel {@code (mx, my)}, or {@code -1} if none. "Smallest" so clicking nested regions
 * picks the innermost. Geometry is tested in pixel space (each point pushed through the same
 * {@link #panel.transformPoint} + cached paint transform used to draw it), so it's correct at any
 * zoom/pan; {@link java.awt.geom.Path2D#contains} treats the path as implicitly closed.
 */
int findClosedRegionAt(int mx, int my) {
    int best = -1;
    double bestArea = Double.MAX_VALUE;
    for (int i = 0; i < panel.allPaths.size(); i++) {
        List<VisualizationPanel.Point2D> path = panel.allPaths.get(i);
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
boolean isClosedRegion(List<VisualizationPanel.Point2D> path) {
    VisualizationPanel.Point2D a = path.get(0);
    VisualizationPanel.Point2D b = path.get(path.size() - 1);
    double minX = a.x(), maxX = a.x(), minY = a.y(), maxY = a.y();
    for (VisualizationPanel.Point2D p : path) {
        minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
        minY = Math.min(minY, p.y()); maxY = Math.max(maxY, p.y());
    }
    double diag = Math.hypot(maxX - minX, maxY - minY);
    double tol = Math.max(0.5, 0.02 * diag); // 0.5mm floor, else 2% of the region's diagonal
    return Math.hypot(a.x() - b.x(), a.y() - b.y()) <= tol;
}

/** The region as a closed {@link Path2D} in screen pixels (for hit-testing). */
Path2D pixelPath(List<VisualizationPanel.Point2D> path) {
    Path2D p2d = new Path2D.Double();
    double[] p0 = panel.transformPoint(path.get(0));
    p2d.moveTo(p0[0] * panel.paintScale + panel.paintTx, p0[1] * panel.paintScale + panel.paintTy);
    for (int j = 1; j < path.size(); j++) {
        double[] pj = panel.transformPoint(path.get(j));
        p2d.lineTo(pj[0] * panel.paintScale + panel.paintTx, pj[1] * panel.paintScale + panel.paintTy);
    }
    p2d.closePath();
    return p2d;
}

/** All loaded strokes as model-space point arrays, for flood-fill enclosure detection. */
List<double[][]> strokesAsModel() {
    List<double[][]> out = new ArrayList<>();
    for (List<VisualizationPanel.Point2D> path : panel.allPaths) {
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
int nearestStrokeLayer(double mx, double my) {
    int best = -1;
    double bestD = Double.MAX_VALUE;
    for (int i = 0; i < panel.allPaths.size(); i++) {
        for (VisualizationPanel.Point2D p : panel.allPaths.get(i)) {
            double d = Math.hypot(p.x() - mx, p.y() - my);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
    }
    return best >= 0 ? panel.pathLayer.get(best) : 0;
}

/** Screen pixel of a model-space point, via the same forward transform paint uses. */
double[] modelToPixel(double mx, double my) {
    double[] c = panel.transformPoint(new VisualizationPanel.Point2D(mx, my));
    return new double[]{c[0] * panel.paintScale + panel.paintTx, c[1] * panel.paintScale + panel.paintTy};
}

/**
 * Inverse of {@link #modelToPixel}: a screen pixel back to a raw model (mm) point. The
 * model→pixel map is affine, so it's recovered exactly by sampling three reference points and
 * inverting the resulting 2×2 system (same technique as {@link #screenDeltaToMm}). Returns
 * {@code null} only if the transform is degenerate.
 */
double[] screenToModel(int px, int py) {
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
Path2D rawRegionPath(List<VisualizationPanel.Point2D> path) {
    Path2D p2d = new Path2D.Double();
    p2d.moveTo(path.get(0).x(), path.get(0).y());
    for (int j = 1; j < path.size(); j++) {
        p2d.lineTo(path.get(j).x(), path.get(j).y());
    }
    p2d.closePath();
    return p2d;
}

}
