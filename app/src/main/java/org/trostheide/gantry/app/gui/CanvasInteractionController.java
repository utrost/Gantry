package org.trostheide.gantry.app.gui;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

final class CanvasInteractionController {
    private CanvasInteractionController() {}

    static void install(VisualizationPanel panel) {
        panel.setBackground(new Color(35, 35, 40));
        TitledBorder border = BorderFactory.createTitledBorder("Live View");
        border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD, 12f));
        panel.setBorder(border);

        JPopupMenu contextMenu = CanvasContextMenu.build(panel);

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (maybeShowPopup(e)) return;
                // Stations are grabbable even on an empty bed, and take precedence over the
                // drawing handles (they're small, on top, and a deliberate target).
                int st = panel.interaction.hitTestStation(e.getX(), e.getY());
                if (st != VisualizationPanel.HANDLE_NONE) {
                    panel.draggingStation = st;
                    return;
                }
                // Pan from ANYWHERE with middle-drag or Shift+left-drag — works over the drawing and
                // in hatch mode, so you can reposition while zoomed in even with no empty bed to grab
                // and no middle button.
                if (SwingUtilities.isMiddleMouseButton(e)
                        || (SwingUtilities.isLeftMouseButton(e) && e.isShiftDown())) {
                    panel.interaction.startPan(e);
                    return;
                }
                // Left-click behaviour depends on the interaction mode. A click that doesn't act
                // (e.g. hatch on empty space) falls through so the user can still pan.
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (panel.interactionMode == VisualizationPanel.InteractionMode.HATCH && panel.regionHatchListener != null) {
                        VisualizationPanel.HatchTarget t = panel.resolveHatchTarget(e.getX(), e.getY());
                        if (t != null) {
                            panel.regionHatchListener.onHatchRegion(t.region(), t.layerIndex());
                            return;
                        }
                    } else if (panel.interactionMode == VisualizationPanel.InteractionMode.DELETE_STROKE && panel.strokeEditListener != null) {
                        int si = panel.interaction.nearestStrokeIndex(e.getX(), e.getY());
                        if (si >= 0) {
                            panel.strokeEditListener.onDeleteStroke(panel.pathCommandId.get(si));
                            return;
                        }
                    } else if (panel.interactionMode == VisualizationPanel.InteractionMode.ADD_LINE && panel.strokeEditListener != null) {
                        double[] p = panel.interaction.snapPoint(e.getX(), e.getY());
                        if (p != null) {
                            if (panel.lineStart == null) {
                                panel.lineStart = p;
                                panel.lineHoverX = e.getX();
                                panel.lineHoverY = e.getY();
                            } else {
                                panel.strokeEditListener.onAddLine(panel.lineStart[0], panel.lineStart[1], p[0], p[1],
                                        panel.interaction.nearestStrokeLayer(panel.lineStart[0], panel.lineStart[1]));
                                panel.lineStart = null;
                            }
                            panel.repaint();
                            return;
                        }
                    } else if (panel.interactionMode == VisualizationPanel.InteractionMode.MOVE_STROKE && panel.strokeEditListener != null) {
                        int si = panel.interaction.nearestStrokeIndex(e.getX(), e.getY());
                        double[] m = si >= 0 ? panel.interaction.screenToModel(e.getX(), e.getY()) : null;
                        if (m != null) {
                            panel.dragStrokeIndex = si;
                            panel.dragStrokePressModel = m;
                            panel.dragStrokeOrig = new ArrayList<>(panel.allPaths.get(si));
                            panel.dragStrokeMoved = false;
                            return;
                        }
                    }
                }
                // Drawing move/resize handles only act in the default (NONE) mode; in an edit mode an
                // unconsumed left-press pans instead of grabbing the whole-drawing handle.
                int handle = (panel.interactionMode == VisualizationPanel.InteractionMode.NONE && !panel.allPaths.isEmpty())
                        ? panel.interaction.hitTestHandle(e.getX(), e.getY()) : VisualizationPanel.HANDLE_NONE;
                if (handle == VisualizationPanel.HANDLE_NONE && SwingUtilities.isLeftMouseButton(e)) {
                    panel.interaction.startPan(e);
                    return;
                }
                if (handle == VisualizationPanel.HANDLE_NONE) return;
                panel.dragHandle = handle;
                panel.dragStartScreenX = e.getX();
                panel.dragStartScreenY = e.getY();
                panel.dragStartOverlayOX = panel.overlayOffsetX;
                panel.dragStartOverlayOY = panel.overlayOffsetY;
                panel.dragStartOverlayScale = panel.overlayScale;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (panel.panning) {
                    panel.viewPanX += e.getX() - panel.panLastX;
                    panel.viewPanY += e.getY() - panel.panLastY;
                    panel.panLastX = e.getX();
                    panel.panLastY = e.getY();
                    panel.repaint();
                    return;
                }
                if (panel.draggingStation >= 0) {
                    double[] mm = panel.interaction.screenToPhysical(e.getX(), e.getY());
                    VisualizationPanel.Station s = panel.stations.get(panel.draggingStation);
                    panel.stations.set(panel.draggingStation, new VisualizationPanel.Station(s.name(), mm[0], mm[1]));
                    panel.repaint();
                    return;
                }
                if (panel.dragStrokeIndex >= 0) {
                    double[] m = panel.interaction.screenToModel(e.getX(), e.getY());
                    if (m != null && panel.dragStrokePressModel != null) {
                        double ddx = m[0] - panel.dragStrokePressModel[0];
                        double ddy = m[1] - panel.dragStrokePressModel[1];
                        List<VisualizationPanel.Point2D> moved = new ArrayList<>(panel.dragStrokeOrig.size());
                        for (VisualizationPanel.Point2D p : panel.dragStrokeOrig) {
                            moved.add(new VisualizationPanel.Point2D(p.x() + ddx, p.y() + ddy));
                        }
                        panel.allPaths.set(panel.dragStrokeIndex, moved);
                        panel.dragStrokeMoved = true;
                        panel.repaint();
                    }
                    return;
                }
                if (panel.dragHandle == VisualizationPanel.HANDLE_NONE) return;
                double dx = e.getX() - panel.dragStartScreenX;
                double dy = e.getY() - panel.dragStartScreenY;
                if (panel.dragHandle == VisualizationPanel.HANDLE_MOVE) {
                    double[] mmDelta = panel.interaction.screenDeltaToMm(dx, dy);
                    panel.overlayOffsetX = panel.dragStartOverlayOX + mmDelta[0];
                    panel.overlayOffsetY = panel.dragStartOverlayOY + mmDelta[1];
                } else {
                    panel.interaction.handleResize(panel.dragHandle, dx, dy);
                }
                panel.clampOverlayToBed();
                panel.repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (maybeShowPopup(e)) return;
                if (panel.panning) {
                    panel.panning = false;
                    panel.setCursor(Cursor.getPredefinedCursor(panel.interactionMode == VisualizationPanel.InteractionMode.NONE
                            ? Cursor.DEFAULT_CURSOR : Cursor.CROSSHAIR_CURSOR));
                    return;
                }
                if (panel.draggingStation >= 0) {
                    VisualizationPanel.Station s = panel.stations.get(panel.draggingStation);
                    panel.draggingStation = -1;
                    if (panel.stationEditListener != null) {
                        panel.stationEditListener.onStationMoved(s.name(), s.x(), s.y());
                    }
                    return;
                }
                if (panel.dragStrokeIndex >= 0) {
                    if (panel.dragStrokeMoved && panel.strokeEditListener != null) {
                        List<VisualizationPanel.Point2D> pts = panel.allPaths.get(panel.dragStrokeIndex);
                        double[][] coords = new double[pts.size()][2];
                        for (int i = 0; i < pts.size(); i++) {
                            coords[i][0] = pts.get(i).x();
                            coords[i][1] = pts.get(i).y();
                        }
                        panel.strokeEditListener.onMoveStroke(panel.pathCommandId.get(panel.dragStrokeIndex), coords);
                    }
                    panel.dragStrokeIndex = -1;
                    panel.dragStrokeOrig = null;
                    return;
                }
                if (panel.dragHandle != VisualizationPanel.HANDLE_NONE) {
                    panel.dragHandle = VisualizationPanel.HANDLE_NONE;
                    panel.fireOverlayChange();
                }
            }

            private boolean maybeShowPopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return false;
                panel.lastPopupX = e.getX();
                panel.lastPopupY = e.getY();
                panel.lastPopupMm = panel.interaction.screenToPhysical(e.getX(), e.getY());
                boolean hasDrawing = !panel.allPaths.isEmpty();
                for (Component item : panel.drawingMenuItems) {
                    item.setEnabled(hasDrawing);
                }
                panel.ctxHatchItem.setSelected(panel.interactionMode == VisualizationPanel.InteractionMode.HATCH);
                panel.ctxDeleteItem.setSelected(panel.interactionMode == VisualizationPanel.InteractionMode.DELETE_STROKE);
                panel.ctxAddLineItem.setSelected(panel.interactionMode == VisualizationPanel.InteractionMode.ADD_LINE);
                panel.ctxMoveItem.setSelected(panel.interactionMode == VisualizationPanel.InteractionMode.MOVE_STROKE);
                contextMenu.show(panel, e.getX(), e.getY());
                return true;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                // In an interaction mode the crosshair stays put (don't let the drag-handle hit-test
                // swap it), and a mode-specific preview tracks the cursor.
                if (panel.interactionMode != VisualizationPanel.InteractionMode.NONE) {
                    panel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    if (panel.interactionMode == VisualizationPanel.InteractionMode.HATCH) {
                        panel.interaction.updateHoverRegion(e.getX(), e.getY());
                    } else if (panel.interactionMode == VisualizationPanel.InteractionMode.DELETE_STROKE
                            || panel.interactionMode == VisualizationPanel.InteractionMode.MOVE_STROKE) {
                        panel.interaction.updateHoverStroke(e.getX(), e.getY());
                    } else if (panel.interactionMode == VisualizationPanel.InteractionMode.ADD_LINE && panel.lineStart != null) {
                        panel.lineHoverX = e.getX();
                        panel.lineHoverY = e.getY();
                        panel.repaint();
                    }
                    return;
                }
                if (panel.interaction.hitTestStation(e.getX(), e.getY()) != VisualizationPanel.HANDLE_NONE) {
                    panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    return;
                }
                if (panel.allPaths.isEmpty()) { panel.setCursor(Cursor.getDefaultCursor()); return; }
                int handle = panel.interaction.hitTestHandle(e.getX(), e.getY());
                panel.setCursor(panel.interaction.cursorForHandle(handle));
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                // Double-click on empty canvas (not a handle or station) resets the view to fit.
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)
                        && panel.interaction.hitTestStation(e.getX(), e.getY()) == VisualizationPanel.HANDLE_NONE
                        && (panel.allPaths.isEmpty() || panel.interaction.hitTestHandle(e.getX(), e.getY()) == VisualizationPanel.HANDLE_NONE)) {
                    panel.resetView();
                }
            }
        };
        panel.addMouseListener(mouseHandler);
        panel.addMouseMotionListener(mouseHandler);
        // Mouse wheel zooms toward the cursor (wheel-up / away = zoom in).
        panel.addMouseWheelListener(e ->
                panel.zoomAtCursor(e.getPreciseWheelRotation() < 0 ? 1.12 : 1 / 1.12, e.getX(), e.getY()));

    }
}
