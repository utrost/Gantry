package org.trostheide.gantry.app.gui;

import javax.swing.*;
import java.util.List;

final class CanvasContextMenu {
    private CanvasContextMenu() {}

    static JPopupMenu build(VisualizationPanel panel) {
        JPopupMenu menu = new JPopupMenu();

        // Always available (Phase 17): drop a refill station at the clicked bed position. The
        // controller turns the mm coordinate into a real StationConfig and re-pushes the list.
        JMenuItem addStation = new JMenuItem("Add station here");
        addStation.addActionListener(e -> {
            if (panel.stationEditListener != null && panel.lastPopupMm != null) {
                panel.stationEditListener.onStationAdded(panel.lastPopupMm[0], panel.lastPopupMm[1]);
            }
        });
        menu.add(addStation);

        // Always available: snap the viewport back to fit-to-window (zoom 100%, no pan).
        JMenuItem resetView = new JMenuItem("Reset View (Zoom/Pan)");
        resetView.addActionListener(e -> panel.resetView());
        menu.add(resetView);
        menu.addSeparator();

        // Interaction-mode toggles, mutually exclusive (kept in sync with the Edit menu and each
        // other via setInteractionMode + the mode-change listener).
        panel.ctxHatchItem = modeItem(panel, "Hatch mode (click areas to fill)", VisualizationPanel.InteractionMode.HATCH);
        panel.ctxDeleteItem = modeItem(panel, "Delete-stroke mode (click a line)", VisualizationPanel.InteractionMode.DELETE_STROKE);
        panel.ctxAddLineItem = modeItem(panel, "Add-line mode (click two points)", VisualizationPanel.InteractionMode.ADD_LINE);
        panel.ctxMoveItem = modeItem(panel, "Move-stroke mode (drag a line)", VisualizationPanel.InteractionMode.MOVE_STROKE);
        menu.add(panel.ctxHatchItem);
        menu.add(panel.ctxDeleteItem);
        menu.add(panel.ctxAddLineItem);
        menu.add(panel.ctxMoveItem);

        // Hatch the region under the click — fill it, clear a previous fill, or pick the style —
        // without switching into hatch mode first. Disabled when no drawing is loaded.
        JMenuItem hatchHere = new JMenuItem("Hatch area here");
        hatchHere.addActionListener(e -> {
            if (panel.regionHatchListener != null) {
                VisualizationPanel.HatchTarget t = panel.resolveHatchTarget(panel.lastPopupX, panel.lastPopupY);
                if (t != null) {
                    panel.regionHatchListener.onHatchRegion(t.region(), t.layerIndex());
                }
            }
        });
        menu.add(hatchHere);
        JMenuItem clearHatchHere = new JMenuItem("Clear hatch in this area");
        clearHatchHere.addActionListener(e -> {
            if (panel.regionHatchListener != null) {
                VisualizationPanel.HatchTarget t = panel.resolveHatchTarget(panel.lastPopupX, panel.lastPopupY);
                if (t != null) {
                    panel.regionHatchListener.onClearHatchRegion(t.region());
                }
            }
        });
        menu.add(clearHatchHere);
        JMenuItem hatchStyle = new JMenuItem("Hatch style...");
        hatchStyle.addActionListener(e -> {
            if (panel.hatchStyleAction != null) {
                panel.hatchStyleAction.run();
            }
        });
        menu.add(hatchStyle);
        // One-shot delete/duplicate of the stroke nearest the click, without entering a mode.
        JMenuItem deleteHere = new JMenuItem("Delete nearest line");
        deleteHere.addActionListener(e -> {
            if (panel.strokeEditListener != null) {
                int si = panel.interaction.nearestStrokeIndex(panel.lastPopupX, panel.lastPopupY);
                if (si >= 0) {
                    panel.strokeEditListener.onDeleteStroke(panel.pathCommandId.get(si));
                }
            }
        });
        menu.add(deleteHere);
        JMenuItem duplicateHere = new JMenuItem("Duplicate nearest line");
        duplicateHere.addActionListener(e -> {
            if (panel.strokeEditListener != null) {
                int si = panel.interaction.nearestStrokeIndex(panel.lastPopupX, panel.lastPopupY);
                if (si >= 0) {
                    panel.strokeEditListener.onDuplicateStroke(panel.pathCommandId.get(si));
                }
            }
        });
        menu.add(duplicateHere);
        menu.addSeparator();

        // Drawing-only items: disabled when the bed is empty (toggled in maybeShowPopup).
        JMenuItem remove = new JMenuItem("Remove Drawing");
        remove.addActionListener(e -> {
            panel.clearDrawing();
            if (panel.removeDrawingListener != null) panel.removeDrawingListener.run();
        });
        menu.add(remove);

        JMenuItem reset = new JMenuItem("Reset Position");
        reset.addActionListener(e -> panel.resetOverlay());
        menu.add(reset);

        JMenuItem rotate = new JMenuItem("Rotate 90°");
        rotate.addActionListener(e -> panel.rotateOverlay());
        menu.add(rotate);

        JMenuItem mirror = new JMenuItem("Mirror");
        mirror.addActionListener(e -> panel.toggleMirror());
        menu.add(mirror);

        panel.drawingMenuItems = List.of(remove, reset, rotate, mirror, hatchHere, clearHatchHere,
                deleteHere, duplicateHere);
        return menu;

    }

    private static JCheckBoxMenuItem modeItem(VisualizationPanel panel, String label,
                                               VisualizationPanel.InteractionMode mode) {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(label);
        item.addActionListener(e -> panel.setInteractionMode(item.isSelected()
                ? mode : VisualizationPanel.InteractionMode.NONE));
        return item;
    }
}
