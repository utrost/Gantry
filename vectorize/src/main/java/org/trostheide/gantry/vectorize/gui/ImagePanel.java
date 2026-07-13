package org.trostheide.gantry.vectorize.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

public class ImagePanel extends JPanel {
    private BufferedImage image;
    private BufferedImage edgeOverlay;
    private boolean showEdgeOverlay = false;
    private double scale = 1.0;
    private double translateX = 0;
    private double translateY = 0;
    private Point lastMousePoint;
    private Consumer<Integer> zoomListener;
    private Runnable viewChangeListener;

    private Rectangle roiRect;
    private Point roiDragStart;
    private boolean roiMode = false;
    private Consumer<Rectangle> roiListener;

    private static final int CHECK_SIZE = 10;

    public ImagePanel() {
        setBackground(UIManager.getColor("Panel.background"));

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double zoomFactor = 1.1;
                double oldScale = scale;
                if (e.getWheelRotation() > 0) {
                    scale /= zoomFactor;
                } else {
                    scale *= zoomFactor;
                }
                scale = Math.max(0.01, Math.min(50.0, scale));

                double mx = e.getX();
                double my = e.getY();
                translateX = mx - (mx - translateX) * (scale / oldScale);
                translateY = my - (my - translateY) * (scale / oldScale);

                fireViewChanged();
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (roiMode) {
                    roiDragStart = e.getPoint();
                    roiRect = null;
                    repaint();
                    return;
                }
                lastMousePoint = e.getPoint();
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (roiMode) {
                    roiDragStart = null;
                    if (roiRect != null && roiRect.width > 2 && roiRect.height > 2) {
                        if (roiListener != null) roiListener.accept(roiRect);
                    } else {
                        roiRect = null;
                    }
                    repaint();
                    return;
                }
                setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (roiMode && roiDragStart != null) {
                    updateRoiRect(e.getPoint());
                    repaint();
                    return;
                }
                if (lastMousePoint != null) {
                    translateX += (e.getX() - lastMousePoint.x);
                    translateY += (e.getY() - lastMousePoint.y);
                    lastMousePoint = e.getPoint();
                    fireViewChanged();
                    repaint();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    if (roiRect != null) {
                        clearRoi();
                        return;
                    }
                    fitToWindow();
                }
            }
        };

        addMouseWheelListener(mouseHandler);
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    public void setRoiMode(boolean roiMode) {
        this.roiMode = roiMode;
        setCursor(roiMode ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR) : Cursor.getDefaultCursor());
    }

    public boolean isRoiMode() {
        return roiMode;
    }

    public Rectangle getRoi() {
        return roiRect;
    }

    public void setRoi(Rectangle roi) {
        roiRect = roi == null ? null : new Rectangle(roi);
        if (roiListener != null) roiListener.accept(roiRect);
        repaint();
    }

    public void clearRoi() {
        roiRect = null;
        if (roiListener != null) roiListener.accept(null);
        repaint();
    }

    public void setRoiListener(Consumer<Rectangle> listener) {
        this.roiListener = listener;
    }

    private void updateRoiRect(Point current) {
        if (image == null || roiDragStart == null) return;

        int startImgX = (int) ((roiDragStart.x - translateX) / scale);
        int startImgY = (int) ((roiDragStart.y - translateY) / scale);
        int endImgX = (int) ((current.x - translateX) / scale);
        int endImgY = (int) ((current.y - translateY) / scale);

        int rx = Math.max(0, Math.min(startImgX, endImgX));
        int ry = Math.max(0, Math.min(startImgY, endImgY));
        int rx2 = Math.min(image.getWidth(), Math.max(startImgX, endImgX));
        int ry2 = Math.min(image.getHeight(), Math.max(startImgY, endImgY));

        roiRect = new Rectangle(rx, ry, rx2 - rx, ry2 - ry);
    }

    public void setZoomListener(Consumer<Integer> listener) {
        this.zoomListener = listener;
    }

    public void setViewChangeListener(Runnable listener) {
        this.viewChangeListener = listener;
    }

    private void fireZoomChanged() {
        if (zoomListener != null) {
            zoomListener.accept(getZoomPercent());
        }
    }

    private void fireViewChanged() {
        fireZoomChanged();
        if (viewChangeListener != null) {
            viewChangeListener.run();
        }
    }

    public int getZoomPercent() {
        return (int) Math.round(scale * 100);
    }

    public void fitToWindow() {
        if (image == null || getWidth() <= 0 || getHeight() <= 0) return;
        double wScale = (double) getWidth() / image.getWidth();
        double hScale = (double) getHeight() / image.getHeight();
        this.scale = Math.min(wScale, hScale) * 0.9;
        this.translateX = (getWidth() - (image.getWidth() * scale)) / 2;
        this.translateY = (getHeight() - (image.getHeight() * scale)) / 2;
        fireViewChanged();
        repaint();
    }

    public void zoomIn() {
        double cx = getWidth() / 2.0;
        double cy = getHeight() / 2.0;
        double oldScale = scale;
        scale = Math.min(50.0, scale * 1.25);
        translateX = cx - (cx - translateX) * (scale / oldScale);
        translateY = cy - (cy - translateY) * (scale / oldScale);
        fireViewChanged();
        repaint();
    }

    public void zoomOut() {
        double cx = getWidth() / 2.0;
        double cy = getHeight() / 2.0;
        double oldScale = scale;
        scale = Math.max(0.01, scale / 1.25);
        translateX = cx - (cx - translateX) * (scale / oldScale);
        translateY = cy - (cy - translateY) * (scale / oldScale);
        fireViewChanged();
        repaint();
    }

    public double getViewScale() { return scale; }
    public double getViewTranslateX() { return translateX; }
    public double getViewTranslateY() { return translateY; }

    public void pan(double dx, double dy) {
        translateX += dx;
        translateY += dy;
        fireViewChanged();
        repaint();
    }

    public void setEdgeOverlay(BufferedImage overlay) {
        this.edgeOverlay = overlay;
        repaint();
    }

    public void setShowEdgeOverlay(boolean show) {
        this.showEdgeOverlay = show;
        repaint();
    }

    public void setImage(BufferedImage img) {
        this.image = img;
        this.scale = 1.0;
        this.translateX = 0;
        this.translateY = 0;
        this.roiRect = null;

        if (img != null && getWidth() > 0 && getHeight() > 0) {
            double wScale = (double) getWidth() / img.getWidth();
            double hScale = (double) getHeight() / img.getHeight();
            this.scale = Math.min(wScale, hScale) * 0.9;
            this.translateX = (getWidth() - (img.getWidth() * scale)) / 2;
            this.translateY = (getHeight() - (img.getHeight() * scale)) / 2;
        }

        fireViewChanged();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;

        if (image != null) {
            drawCheckerboard(g2);
        }

        if (image == null)
            return;

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        AffineTransform at = new AffineTransform();
        at.translate(translateX, translateY);
        at.scale(scale, scale);

        g2.drawImage(image, at, null);

        if (showEdgeOverlay && edgeOverlay != null) {
            Composite originalComposite = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
            g2.drawImage(edgeOverlay, at, null);
            g2.setComposite(originalComposite);
        }

        if (roiRect != null) {
            int rx = (int) (roiRect.x * scale + translateX);
            int ry = (int) (roiRect.y * scale + translateY);
            int rw = (int) (roiRect.width * scale);
            int rh = (int) (roiRect.height * scale);

            Composite oldComposite = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f));
            g2.setColor(new Color(64, 150, 255));
            g2.fillRect(rx, ry, rw, rh);
            g2.setComposite(oldComposite);

            Stroke oldStroke = g2.getStroke();
            g2.setColor(new Color(64, 150, 255));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, new float[]{6f, 4f}, 0f));
            g2.drawRect(rx, ry, rw, rh);
            g2.setStroke(oldStroke);
        }
    }

    private void drawCheckerboard(Graphics2D g2) {
        int imgW = (int) (image.getWidth() * scale);
        int imgH = (int) (image.getHeight() * scale);
        int startX = (int) translateX;
        int startY = (int) translateY;

        Rectangle clip = g2.getClipBounds();
        int x0 = Math.max(startX, clip != null ? clip.x : 0);
        int y0 = Math.max(startY, clip != null ? clip.y : 0);
        int x1 = Math.min(startX + imgW, clip != null ? clip.x + clip.width : getWidth());
        int y1 = Math.min(startY + imgH, clip != null ? clip.y + clip.height : getHeight());

        boolean isDark = VectorizerGUI.isDarkTheme();
        Color light = isDark ? new Color(60, 60, 60) : new Color(240, 240, 240);
        Color dark = isDark ? new Color(45, 45, 45) : new Color(210, 210, 210);

        for (int y = y0; y < y1; y += CHECK_SIZE) {
            for (int x = x0; x < x1; x += CHECK_SIZE) {
                boolean even = ((x - startX) / CHECK_SIZE + (y - startY) / CHECK_SIZE) % 2 == 0;
                g2.setColor(even ? light : dark);
                g2.fillRect(x, y, Math.min(CHECK_SIZE, x1 - x), Math.min(CHECK_SIZE, y1 - y));
            }
        }
    }
}
