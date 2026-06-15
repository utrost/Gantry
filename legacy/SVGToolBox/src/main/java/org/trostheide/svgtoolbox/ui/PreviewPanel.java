package org.trostheide.svgtoolbox.ui;

import org.apache.batik.swing.JSVGCanvas;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.geom.NoninvertibleTransformException;
import java.io.File;

public class PreviewPanel extends JPanel {
    private JSVGCanvas svgCanvas;
    private Point lastMousePos;
    private JLabel hintLabel;
    private boolean fileLoaded = false;

    public PreviewPanel() {
        setLayout(new BorderLayout());

        // Drop hint overlay
        hintLabel = new JLabel("<html><center>Drag & drop an SVG file here<br>or click <b>Load SVG</b></center></html>");
        hintLabel.setHorizontalAlignment(SwingConstants.CENTER);
        hintLabel.setVerticalAlignment(SwingConstants.CENTER);
        hintLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.PLAIN, 16f));

        svgCanvas = new JSVGCanvas();
        // Disable default interactors to avoid conflict
        svgCanvas.setDocumentState(JSVGCanvas.ALWAYS_DYNAMIC);
        svgCanvas.setEnableImageZoomInteractor(false);
        svgCanvas.setEnablePanInteractor(false);
        svgCanvas.setEnableRotateInteractor(false);
        svgCanvas.setEnableResetTransformInteractor(true); // Keep ctrl+click reset? Or implement double click reset.

        // Custom Interactors
        setupInteractions();

        add(hintLabel, BorderLayout.CENTER);

        setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    java.util.List<File> files = (java.util.List<File>) support.getTransferable()
                            .getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        File file = files.get(0);
                        loadFile(file);
                        Container parent = getParent();
                        while (parent != null && !(parent instanceof MainWindow)) {
                            parent = parent.getParent();
                        }
                        if (parent instanceof MainWindow) {
                            ((MainWindow) parent).setInputFile(file);
                        }
                        return true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return false;
            }
        });
    }

    private void setupInteractions() {
        // Zoom via Scroll Wheel
        svgCanvas.addMouseWheelListener(e -> {
            if (svgCanvas.getSVGDocument() == null)
                return;

            double zoomFactor = (e.getWheelRotation() < 0) ? 1.1 : 0.9;
            AffineTransform at = svgCanvas.getRenderingTransform();
            if (at == null)
                at = new AffineTransform();

            Point p = e.getPoint();
            at.translate(p.x, p.y);
            at.scale(zoomFactor, zoomFactor);
            at.translate(-p.x, -p.y);

            svgCanvas.setRenderingTransform(at);
        });

        // Pan via Drag
        svgCanvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastMousePos = e.getPoint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                // Double click to reset
                if (e.getClickCount() == 2) {
                    svgCanvas.resetRenderingTransform();
                }
            }
        });

        svgCanvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (lastMousePos == null || svgCanvas.getSVGDocument() == null)
                    return;

                int dx = e.getX() - lastMousePos.x;
                int dy = e.getY() - lastMousePos.y;

                AffineTransform at = svgCanvas.getRenderingTransform();
                if (at == null)
                    at = new AffineTransform();

                at.preConcatenate(AffineTransform.getTranslateInstance(dx, dy));
                svgCanvas.setRenderingTransform(at);

                lastMousePos = e.getPoint();
            }
        });
    }

    public void loadFile(File file) {
        if (file.exists()) {
            if (!fileLoaded) {
                remove(hintLabel);
                add(svgCanvas, BorderLayout.CENTER);
                revalidate();
                repaint();
                fileLoaded = true;
            }
            svgCanvas.setURI(file.toURI().toString());
        }
    }

    public JSVGCanvas getCanvas() {
        return svgCanvas;
    }

    public Rectangle2D getViewportBounds() {
        AffineTransform at = svgCanvas.getRenderingTransform();
        if (at == null)
            at = new AffineTransform();

        try {
            AffineTransform inverse = at.createInverse();
            Rectangle visibleRect = svgCanvas.getVisibleRect(); // Component bounds

            // Map the corners of the component to SVG space
            // This handles zoom and pan
            // Actually, we can just transform the rect

            // Wait, getVisibleRect returns the component's visible area (0,0,w,h usually)
            // We want to know which part of the SVG is effectively "under" this rect.

            // 0,0 in screen space -> ? in SVG space
            java.awt.geom.Point2D.Double tl = new java.awt.geom.Point2D.Double(visibleRect.getX(), visibleRect.getY());
            java.awt.geom.Point2D.Double br = new java.awt.geom.Point2D.Double(visibleRect.getMaxX(),
                    visibleRect.getMaxY());

            java.awt.geom.Point2D svgTL = inverse.transform(tl, null);
            java.awt.geom.Point2D svgBR = inverse.transform(br, null);

            return new Rectangle2D.Double(
                    Math.min(svgTL.getX(), svgBR.getX()),
                    Math.min(svgTL.getY(), svgBR.getY()),
                    Math.abs(svgBR.getX() - svgTL.getX()),
                    Math.abs(svgBR.getY() - svgTL.getY()));

        } catch (NoninvertibleTransformException e) {
            return null;
        }
    }
}
