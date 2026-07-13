package org.trostheide.gantry.app.gui;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

final class CanvasRenderer {
    private CanvasRenderer() {}

    static void paint(VisualizationPanel panel, Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = panel.getWidth();
        int h = panel.getHeight();

        double dw = panel.displayWidth();
        double dh = panel.displayHeight();

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
        // panel.viewZoom * fitPixel + viewPan, i.e. content*(fitScale*panel.viewZoom) + (panel.viewZoom*fitTx+pan).
        double scale = fitScale * panel.viewZoom;
        double tx = panel.viewZoom * fitTx + panel.viewPanX;
        double ty = panel.viewZoom * fitTy + panel.viewPanY;

        panel.paintScale = scale;
        panel.paintTx = tx;
        panel.paintTy = ty;

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
        double[] originScreen = panel.physicalToScreen(0, 0);
        g2.setColor(Color.ORANGE);
        double markerR = 5 / scale;
        g2.fill(new java.awt.geom.Ellipse2D.Double(originScreen[0] - markerR, originScreen[1] - markerR,
                markerR * 2, markerR * 2));
        g2.setColor(new Color(200, 200, 200));
        g2.setFont(g2.getFont().deriveFont((float) (12 / scale)));
        g2.drawString("0,0 (Origin)", (float) (originScreen[0] - 50 / scale), (float) (originScreen[1] + 15 / scale));

        // --- Draw Axes (from Physical Origin) ---
        g2.setStroke(new BasicStroke((float) (2.0 / scale)));
        double axisLen = Math.min(panel.machineWidth, panel.machineHeight) * 0.15;

        double[] xAxisEnd = panel.physicalToScreen(axisLen, 0);
        g2.setColor(Color.RED);
        g2.draw(new java.awt.geom.Line2D.Double(originScreen[0], originScreen[1], xAxisEnd[0], xAxisEnd[1]));
        g2.drawString("+X", (float) (xAxisEnd[0] - 10 / scale), (float) (xAxisEnd[1] + 15 / scale));

        double[] yAxisEnd = panel.physicalToScreen(0, axisLen);
        g2.setColor(Color.GREEN);
        g2.draw(new java.awt.geom.Line2D.Double(originScreen[0], originScreen[1], yAxisEnd[0], yAxisEnd[1]));
        g2.drawString("+Y", (float) (yAxisEnd[0] + 5 / scale), (float) (yAxisEnd[1] + 5 / scale));

        // --- Draw Refill Stations ---
        for (VisualizationPanel.Station station : panel.stations) {
            // VisualizationPanel.Station coords are in raw input space, transform to screen
            double[] sScreen = panel.physicalToScreen(station.x(), station.y());
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
            for (int i = 0; i < panel.allPaths.size(); i++) {
                List<VisualizationPanel.Point2D> path = panel.allPaths.get(i);
                if (path.isEmpty()) {
                    continue;
                }
                int li = panel.pathLayer.get(i);
                boolean selected = panel.selectedLayers.contains(li);
                if (selected != drawingSelected) {
                    continue;
                }
                Color base = panel.colorByLayer ? panel.colorForLayer(li) : panel.DEFAULT_PATH;
                g2.setColor(selected ? base : panel.ghost(base));
                Path2D p2d = new Path2D.Double();
                double[] p0 = panel.transformPoint(path.get(0));
                p2d.moveTo(p0[0], p0[1]);
                for (int j = 1; j < path.size(); j++) {
                    double[] pj = panel.transformPoint(path.get(j));
                    p2d.lineTo(pj[0], pj[1]);
                }
                g2.draw(p2d);
            }
        }

        // --- Travel overlay: dashed pen-up segments coloured by distance ---
        if (panel.showTravelOverlay) {
            float[] dash = {8.0f / (float) scale, 5.0f / (float) scale};
            for (int i = 0; i < panel.allPaths.size() - 1; i++) {
                if (!panel.pathLayer.get(i).equals(panel.pathLayer.get(i + 1))) {
                    continue;
                }
                List<VisualizationPanel.Point2D> cur = panel.allPaths.get(i);
                List<VisualizationPanel.Point2D> nxt = panel.allPaths.get(i + 1);
                if (cur.isEmpty() || nxt.isEmpty()) {
                    continue;
                }
                VisualizationPanel.Point2D endPt = cur.get(cur.size() - 1);
                VisualizationPanel.Point2D startPt = nxt.get(0);
                double distMm = Math.hypot(endPt.x() - startPt.x(), endPt.y() - startPt.y());
                Color travelColor;
                if (distMm < 20) {
                    travelColor = new Color(80, 200, 80, 160);
                } else if (distMm < 80) {
                    travelColor = new Color(220, 170, 50, 160);
                } else {
                    travelColor = new Color(220, 60, 60, 180);
                }
                g2.setColor(travelColor);
                g2.setStroke(new BasicStroke((float) (1.0 / scale), BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f));
                double[] from = panel.transformPoint(endPt);
                double[] to = panel.transformPoint(startPt);
                g2.draw(new java.awt.geom.Line2D.Double(from[0], from[1], to[0], to[1]));
            }
        }

        // --- Hatch-mode hover highlight: tint the closed region the next click would fill ---
        if (panel.interactionMode == VisualizationPanel.InteractionMode.HATCH
                && panel.hoverRegionIndex >= 0 && panel.hoverRegionIndex < panel.allPaths.size()) {
            List<VisualizationPanel.Point2D> hp = panel.allPaths.get(panel.hoverRegionIndex);
            if (hp.size() >= 3) {
                Path2D hi = new Path2D.Double();
                double[] h0 = panel.transformPoint(hp.get(0));
                hi.moveTo(h0[0], h0[1]);
                for (int j = 1; j < hp.size(); j++) {
                    double[] hj = panel.transformPoint(hp.get(j));
                    hi.lineTo(hj[0], hj[1]);
                }
                hi.closePath();
                g2.setColor(new Color(255, 210, 80, 70));
                g2.fill(hi);
                g2.setColor(new Color(255, 200, 50));
                g2.setStroke(new BasicStroke((float) (1.5 / scale)));
                g2.draw(hi);
            }
        } else if (panel.interactionMode == VisualizationPanel.InteractionMode.HATCH && panel.hoverEnclosedModel != null) {
            // Multi-stroke enclosure preview (debounced flood fill) — same tint as a closed region.
            Path2D hi = panel.interaction.modelPathToContent(panel.hoverEnclosedModel);
            g2.setColor(new Color(255, 210, 80, 70));
            g2.fill(hi);
            g2.setColor(new Color(255, 200, 50));
            g2.setStroke(new BasicStroke((float) (1.5 / scale)));
            g2.draw(hi);
        }

        // --- Stroke-edit hover highlight: outline the stroke the next click would act on
        //     (red = delete, cyan = move) ---
        if ((panel.interactionMode == VisualizationPanel.InteractionMode.DELETE_STROKE || panel.interactionMode == VisualizationPanel.InteractionMode.MOVE_STROKE)
                && panel.hoverStrokeIndex >= 0 && panel.hoverStrokeIndex < panel.allPaths.size()) {
            List<VisualizationPanel.Point2D> hp = panel.allPaths.get(panel.hoverStrokeIndex);
            if (!hp.isEmpty()) {
                Path2D hi = new Path2D.Double();
                double[] h0 = panel.transformPoint(hp.get(0));
                hi.moveTo(h0[0], h0[1]);
                for (int j = 1; j < hp.size(); j++) {
                    double[] hj = panel.transformPoint(hp.get(j));
                    hi.lineTo(hj[0], hj[1]);
                }
                g2.setColor(panel.interactionMode == VisualizationPanel.InteractionMode.DELETE_STROKE
                        ? new Color(255, 80, 80) : new Color(90, 210, 230));
                g2.setStroke(new BasicStroke((float) (3.0 / scale)));
                g2.draw(hi);
            }
        }

        // --- Add-line preview: marker at the first point + rubber-band to the cursor ---
        if (panel.interactionMode == VisualizationPanel.InteractionMode.ADD_LINE && panel.lineStart != null) {
            double[] a = panel.transformPoint(new VisualizationPanel.Point2D(panel.lineStart[0], panel.lineStart[1]));
            double[] b = panel.interaction.snapPoint(panel.lineHoverX, panel.lineHoverY); // snaps the preview end onto nearby strokes
            g2.setColor(new Color(120, 220, 150));
            double rr = 3 / scale;
            g2.fill(new java.awt.geom.Ellipse2D.Double(a[0] - rr, a[1] - rr, rr * 2, rr * 2));
            if (b != null) {
                double[] bp = panel.transformPoint(new VisualizationPanel.Point2D(b[0], b[1]));
                g2.setStroke(new BasicStroke((float) (1.5 / scale)));
                g2.draw(new java.awt.geom.Line2D.Double(a[0], a[1], bp[0], bp[1]));
            }
        }

        // --- Draw Interactive Bounding Box ---
        if (!panel.allPaths.isEmpty()) {
            // Transform raw content corners through the full pipeline
            VisualizationPanel.Point2D[] corners = {
                new VisualizationPanel.Point2D(panel.rawMinX, panel.rawMinY), new VisualizationPanel.Point2D(panel.rawMaxX, panel.rawMinY),
                new VisualizationPanel.Point2D(panel.rawMinX, panel.rawMaxY), new VisualizationPanel.Point2D(panel.rawMaxX, panel.rawMaxY)
            };
            double sMinX = Double.MAX_VALUE, sMinY = Double.MAX_VALUE;
            double sMaxX = -Double.MAX_VALUE, sMaxY = -Double.MAX_VALUE;
            for (VisualizationPanel.Point2D c : corners) {
                double[] sc = panel.transformPoint(c);
                sMinX = Math.min(sMinX, sc[0]); sMaxX = Math.max(sMaxX, sc[0]);
                sMinY = Math.min(sMinY, sc[1]); sMaxY = Math.max(sMaxY, sc[1]);
            }

            g2.setColor(new Color(255, 200, 50, 120));
            float[] dash = {(float)(6 / scale), (float)(4 / scale)};
            g2.setStroke(new BasicStroke((float)(1.5 / scale), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, dash, 0));
            g2.draw(new Rectangle2D.Double(sMinX, sMinY, sMaxX - sMinX, sMaxY - sMinY));

            double hs = panel.HANDLE_SIZE_PX / scale;
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
        // panel.currentX, panel.currentY are Physical coordinates from the backend
        double[] headScreen = panel.physicalToScreen(panel.currentX, panel.currentY);
        g2.setColor(Color.RED);
        double r = 4.0 / scale;
        g2.fill(new java.awt.geom.Ellipse2D.Double(headScreen[0] - r, headScreen[1] - r, r * 2, r * 2));

        // Crosshair
        g2.setStroke(new BasicStroke((float) (0.5 / scale)));
        double crossSize = Math.max(panel.machineWidth, panel.machineHeight);
        g2.draw(new java.awt.geom.Line2D.Double(headScreen[0] - crossSize, headScreen[1],
                headScreen[0] + crossSize, headScreen[1]));
        g2.draw(new java.awt.geom.Line2D.Double(headScreen[0], headScreen[1] - crossSize,
                headScreen[0], headScreen[1] + crossSize));

        g2.setTransform(old);

        // --- HUD ---
        g2.setColor(new Color(180, 180, 180));
        g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        String travelHud = (panel.travelTotalMm > 0)
                ? String.format(" | Travel: %.0f%%", 100.0 * panel.travelPenDownMm / panel.travelTotalMm)
                : "";
        g2.drawString(String.format(
                "Pos: %.1f, %.1f | Speed: %d%% | View: %.0f%% | Align: %s | Rot: %d | Origin: %s | %s%s",
                panel.currentX, panel.currentY, panel.speedPercent, panel.viewZoom * 100, panel.canvasAlignment, panel.dataRotation,
                panel.machineOrigin, panel.orientation, travelHud), 10, h - 10);
        if (panel.hasOverlayTransform()) {
            g2.drawString(String.format(
                    "Drag: dX=%.1f dY=%.1f Scale=%.0f%% | Bed: %.0fx%.0f",
                    panel.overlayOffsetX, panel.overlayOffsetY, panel.overlayScale * 100,
                    panel.machineWidth, panel.machineHeight), 10, h - 24);
        } else {
            g2.drawString(String.format(
                    "Bed: %.0fx%.0f | Offset: %.1f, %.1f | Swap: %s InvX: %s InvY: %s",
                    panel.machineWidth, panel.machineHeight, panel.alignOffsetX, panel.alignOffsetY,
                    panel.effectiveSwap() ? "Y" : "N",
                    panel.effectiveInvertX() ? "Y" : "N",
                    panel.effectiveInvertY() ? "Y" : "N"), 10, h - 24);
        }

    }
}
