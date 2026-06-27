package org.trostheide.gantry.vectorize.gui;

import org.apache.batik.swing.JSVGCanvas;
import org.trostheide.gantry.vectorize.ExportUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import javax.imageio.ImageIO;

public class MainFrame extends JFrame {
    ImagePanel originalPanel;
    private JSVGCanvas previewPanel;
    ControlsPanel controlsPanel;

    private BufferedImage originalImage;
    private File currentPreviewFile;
    private File currentImageFile;

    private Timer debounceTimer;
    private static final int DEBOUNCE_DELAY_MS = 400;

    private JLabel statusLabel;
    private JProgressBar progressBar;
    private JButton cancelBtn;
    private VectorizationWorker currentWorker;
    JButton undoBtn;
    JButton redoBtn;

    private StatusBarFactory statusBar;
    JToggleButton edgeToggle;
    JToggleButton roiToggle;
    private JButton clearRoiBtn;
    private long vectorizationStartTime;

    public MainFrame() {
        super("Vectorize");
        setIconImage(createAppIcon());

        setJMenuBar(MenuBarFactory.create(this));

        ToolbarFactory.Result toolbar = ToolbarFactory.create(this);
        progressBar = toolbar.progressBar();
        statusLabel = toolbar.statusLabel();
        cancelBtn = toolbar.cancelBtn();
        undoBtn = toolbar.undoBtn();
        redoBtn = toolbar.redoBtn();
        edgeToggle = toolbar.edgeToggle();
        roiToggle = toolbar.roiToggle();
        clearRoiBtn = toolbar.clearRoiBtn();
        add(toolbar.toolBar(), BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout());

        originalPanel = new ImagePanel();
        JPanel leftPanel = createTitledPanel("Source Image", originalPanel);

        previewPanel = new JSVGCanvas();
        previewPanel.setDocumentState(JSVGCanvas.ALWAYS_DYNAMIC);
        JPanel rightPanel = createTitledPanel("Vector Preview", previewPanel);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerSize(6);
        splitPane.setContinuousLayout(true);
        centerPanel.add(splitPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        controlsPanel = new ControlsPanel(this::vectorizationParamsChanged);
        JScrollPane controlsScroll = new JScrollPane(controlsPanel);
        controlsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        controlsScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        controlsScroll.setPreferredSize(new Dimension(300, 0));
        controlsScroll.setMinimumSize(new Dimension(280, 0));
        controlsScroll.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0,
                UIManager.getColor("Separator.foreground")));
        add(controlsScroll, BorderLayout.EAST);

        statusBar = new StatusBarFactory();
        add(statusBar.getPanel(), BorderLayout.SOUTH);

        debounceTimer = new Timer(DEBOUNCE_DELAY_MS, e -> runVectorization());
        debounceTimer.setRepeats(false);

        new DropTarget(this, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) dtde.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        loadImageFile(files.get(0));
                    }
                    dtde.dropComplete(true);
                } catch (Exception ex) {
                    dtde.dropComplete(false);
                }
            }
        }, true);

        setupKeyboardShortcuts();
        new ZoomPanController(originalPanel, previewPanel);

        originalPanel.setRoiListener(roi -> {
            clearRoiBtn.setEnabled(roi != null);
            runVectorization();
        });
    }

    private BufferedImage createAppIcon() {
        BufferedImage icon = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(64, 150, 255));
        g.fillRoundRect(0, 0, 32, 32, 8, 8);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(6, 24, 12, 8);
        g.drawLine(12, 8, 20, 20);
        g.drawLine(20, 20, 26, 6);
        g.dispose();
        return icon;
    }

    private JPanel createTitledPanel(String title, JComponent content) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        label.setBorder(new EmptyBorder(4, 8, 4, 8));
        panel.add(label, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private void setupKeyboardShortcuts() {
        JRootPane rootPane = getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "updatePreview");
        actionMap.put("updatePreview", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runVectorization();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        actionMap.put("cancel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cancelVectorization();
            }
        });

        for (int i = 0; i < 5; i++) {
            final int idx = i + 1;
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, KeyEvent.ALT_DOWN_MASK), "preset" + (i + 1));
            actionMap.put("preset" + (i + 1), new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    controlsPanel.selectPreset(idx);
                }
            });
        }
    }

    void updateUndoRedoButtons() {
        undoBtn.setEnabled(controlsPanel.canUndo());
        redoBtn.setEnabled(controlsPanel.canRedo());
    }

    private void vectorizationParamsChanged() {
        debounceTimer.restart();
    }

    void openImage(ActionEvent e) {
        FileDialog fd = new FileDialog(this, "Open Image", FileDialog.LOAD);
        fd.setFilenameFilter((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                    || lower.endsWith(".bmp") || lower.endsWith(".gif") || lower.endsWith(".svg");
        });
        fd.setVisible(true);
        if (fd.getFile() != null) {
            loadImageFile(new File(fd.getDirectory(), fd.getFile()));
        }
    }

    private void loadImageFile(File f) {
        if (f.getName().toLowerCase().endsWith(".svg")) {
            currentPreviewFile = f;
            currentImageFile = f;
            originalImage = null;
            originalPanel.setImage(null);
            try {
                previewPanel.setURI(f.toURI().toString());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error loading SVG: " + ex.getMessage());
            }
            setTitle("Vectorize - " + f.getName());
            statusBar.setImageInfo("SVG: " + f.getName());
            return;
        }

        try {
            originalImage = ImageIO.read(f);
            if (originalImage != null) {
                currentImageFile = f;
                originalPanel.setImage(originalImage);
                originalPanel.setZoomListener(statusBar::updateZoomLabel);
                controlsPanel.setImageSize(originalImage.getWidth(), originalImage.getHeight());
                controlsPanel.setSourceImage(originalImage);
                previewPanel.setURI(null);
                setTitle("Vectorize - " + f.getName());
                statusBar.setImageInfo(String.format("%s  |  %d x %d px",
                        f.getName(), originalImage.getWidth(), originalImage.getHeight()));
                runVectorization();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error loading image: " + ex.getMessage());
        }
    }

    void saveSvg(ActionEvent e) {
        if (currentPreviewFile == null || !currentPreviewFile.exists()) {
            JOptionPane.showMessageDialog(this, "No vector data to save. Open an image first.");
            return;
        }

        FileDialog fd = new FileDialog(this, "Save SVG", FileDialog.SAVE);
        fd.setFile("vectorized.svg");
        fd.setVisible(true);
        if (fd.getFile() != null) {
            File dest = new File(fd.getDirectory(), fd.getFile());
            if (!dest.getName().toLowerCase().endsWith(".svg")) {
                dest = new File(dest.getAbsolutePath() + ".svg");
            }
            try {
                Files.copy(currentPreviewFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                statusLabel.setText("Saved: " + dest.getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error saving file: " + ex.getMessage());
            }
        }
    }

    void savePng(ActionEvent e) {
        if (currentPreviewFile == null || !currentPreviewFile.exists()) {
            JOptionPane.showMessageDialog(this, "No vector data to save. Open an image first.");
            return;
        }

        FileDialog fd = new FileDialog(this, "Save PNG", FileDialog.SAVE);
        fd.setFile("vectorized.png");
        fd.setVisible(true);
        if (fd.getFile() != null) {
            File dest = new File(fd.getDirectory(), fd.getFile());
            if (!dest.getName().toLowerCase().endsWith(".png")) {
                dest = new File(dest.getAbsolutePath() + ".png");
            }
            try {
                ExportUtil.exportToPng(currentPreviewFile.getAbsolutePath(), dest.getAbsolutePath(), 0);
                statusLabel.setText("Exported: " + dest.getName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error exporting PNG: " + ex.getMessage());
            }
        }
    }

    void cancelVectorization() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
            setBusy(false, "Cancelled");
        }
    }

    private void setBusy(boolean busy, String message) {
        progressBar.setVisible(busy);
        cancelBtn.setEnabled(busy);
        statusLabel.setText(message);
    }

    void runVectorization() {
        if (originalImage == null)
            return;

        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
        }

        controlsPanel.pushUndoState();
        updateUndoRedoButtons();

        vectorizationStartTime = System.currentTimeMillis();
        setBusy(true, "Vectorizing...");

        BufferedImage workImage = originalImage;
        Rectangle roi = originalPanel.getRoi();
        if (roi != null) {
            workImage = originalImage.getSubimage(roi.x, roi.y, roi.width, roi.height);
        }

        currentWorker = new VectorizationWorker(workImage, controlsPanel,
                new VectorizationWorker.Callback() {
                    @Override
                    public void onVectorizationComplete(File svgFile, BufferedImage edgeImage) {
                        long elapsed = System.currentTimeMillis() - vectorizationStartTime;
                        currentPreviewFile = svgFile;
                        try {
                            previewPanel.setURI(svgFile.toURI().toString());
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                        if (edgeImage != null) {
                            originalPanel.setEdgeOverlay(edgeImage);
                        }
                        statusBar.updateStats(svgFile, elapsed);
                        setBusy(false, "Ready");
                    }

                    @Override
                    public void onError(Exception e) {
                        if (e instanceof java.util.concurrent.CancellationException) {
                            setBusy(false, "Cancelled");
                        } else {
                            JOptionPane.showMessageDialog(MainFrame.this, "Error: " + e.getMessage());
                            e.printStackTrace();
                            setBusy(false, "Error");
                        }
                    }

                    @Override
                    public void onProgress(String stage) {
                        SwingUtilities.invokeLater(() -> statusLabel.setText(stage));
                    }

                    @Override
                    public void onAutoCannyComputed(float low, float high) {
                        SwingUtilities.invokeLater(() -> controlsPanel.updateCannyDisplay(low, high));
                    }
                });

        currentWorker.execute();
    }
}
