package org.trostheide.gantry.vectorize.gui;

import org.apache.batik.swing.JSVGCanvas;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;

class ZoomPanController {

    ZoomPanController(ImagePanel imagePanel, JSVGCanvas svgCanvas) {
        imagePanel.setViewChangeListener(() -> syncTransform(imagePanel, svgCanvas));

        MouseAdapter previewMouse = new MouseAdapter() {
            private Point lastDragPoint;

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.getWheelRotation() < 0) {
                    imagePanel.zoomIn();
                } else {
                    imagePanel.zoomOut();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                lastDragPoint = e.getPoint();
                svgCanvas.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                lastDragPoint = null;
                svgCanvas.setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (lastDragPoint != null) {
                    double dx = e.getX() - lastDragPoint.x;
                    double dy = e.getY() - lastDragPoint.y;
                    imagePanel.pan(dx, dy);
                    lastDragPoint = e.getPoint();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    imagePanel.fitToWindow();
                }
            }
        };
        svgCanvas.addMouseWheelListener(previewMouse);
        svgCanvas.addMouseListener(previewMouse);
        svgCanvas.addMouseMotionListener(previewMouse);
    }

    private static void syncTransform(ImagePanel imagePanel, JSVGCanvas svgCanvas) {
        if (svgCanvas.getSVGDocument() == null) return;
        try {
            double s = imagePanel.getViewScale();
            double tx = imagePanel.getViewTranslateX();
            double ty = imagePanel.getViewTranslateY();

            double imgCenterX = (imagePanel.getWidth() / 2.0 - tx) / s;
            double imgCenterY = (imagePanel.getHeight() / 2.0 - ty) / s;

            double previewTx = svgCanvas.getWidth() / 2.0 - imgCenterX * s;
            double previewTy = svgCanvas.getHeight() / 2.0 - imgCenterY * s;

            AffineTransform at = new AffineTransform();
            at.translate(previewTx, previewTy);
            at.scale(s, s);
            svgCanvas.setRenderingTransform(at);
        } catch (Exception ignored) {
        }
    }
}
