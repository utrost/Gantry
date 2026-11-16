package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.InkCoverageAnalyzer;
import org.trostheide.gantry.app.plot.PlotSettings;
import org.trostheide.gantry.model.ProcessorOutput;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Optional preflight view of geometric pen-footprint overlap. Never issues machine commands. */
final class InkCoverageDialog {
    private InkCoverageDialog() { }

    static void show(Window owner, ProcessorOutput output, PlotSettings settings,
                     List<InkCoverageAnalyzer.PenSpec> pens) {
        JDialog dialog = new JDialog(owner, "Ink overlap", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel summary = new JLabel("Analyzing prepared job...");
        JLabel image = new JLabel("Calculating pen footprints...", SwingConstants.CENTER);
        image.setOpaque(true);
        image.setBackground(Color.WHITE);
        JScrollPane scroll = new JScrollPane(image);
        JComboBox<String> view = new JComboBox<>(new String[] {"All pens"});
        JSpinner threshold = new JSpinner(new SpinnerNumberModel(2, 2, 20, 1));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        controls.add(new JLabel("View")); controls.add(view);
        controls.add(new JLabel("Warn at overlap count")); controls.add(threshold);
        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.add(summary, BorderLayout.NORTH);
        top.add(controls, BorderLayout.SOUTH);
        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        JLabel caveat = new JLabel("<html>White: no coverage · Gold: below threshold · Red: at/above threshold.<br>Geometric pen-footprint overlap only. Actual ink spread and drying depend on pen, paper, pressure and speed.</html>");
        JButton close = new JButton("Close");
        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.add(caveat, BorderLayout.CENTER);
        bottom.add(close, BorderLayout.EAST);
        root.add(bottom, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.setSize(820, 660);
        dialog.setLocationRelativeTo(owner);

        SwingWorker<InkCoverageAnalyzer.Result, Void> worker = new SwingWorker<>() {
            @Override protected InkCoverageAnalyzer.Result doInBackground() {
                return InkCoverageAnalyzer.analyze(output, settings, pens);
            }
            @Override protected void done() {
                if (isCancelled() || !dialog.isDisplayable()) return;
                try {
                    InkCoverageAnalyzer.Result result = get();
                    for (String name : result.penNames()) view.addItem(name);
                    Runnable render = () -> render(result, view.getSelectedIndex() - 1,
                            (int) threshold.getValue(), summary, image);
                    view.addActionListener(e -> render.run());
                    threshold.addChangeListener(e -> render.run());
                    render.run();
                } catch (CancellationException ignored) {
                    // Closing the dialog cancels its analysis.
                } catch (Exception ex) {
                    summary.setText("Could not analyze this job: " + ex.getMessage());
                    image.setText("Analysis unavailable");
                }
            }
        };
        close.addActionListener(e -> dialog.dispose());
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent event) { worker.cancel(true); }
        });
        worker.execute();
        dialog.setVisible(true);
    }

    private static void render(InkCoverageAnalyzer.Result result, int penIndex, int threshold,
                               JLabel summary, JLabel image) {
        byte[] counts = result.counts(penIndex);
        BufferedImage bitmap = new BufferedImage(result.columns(), result.rows(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < result.rows(); y++) {
            for (int x = 0; x < result.columns(); x++) {
                int count = Byte.toUnsignedInt(counts[y * result.columns() + x]);
                int rgb = count == 0 ? 0xffffff : count >= threshold ? 0xd73524 : 0xf3ba4a;
                bitmap.setRGB(x, y, rgb);
            }
        }
        int targetWidth = Math.min(720, result.columns());
        int targetHeight = Math.max(1, (int) Math.round((double) result.rows() * targetWidth / result.columns()));
        image.setText(null);
        image.setIcon(new ImageIcon(bitmap.getScaledInstance(targetWidth, targetHeight, Image.SCALE_FAST)));
        String name = penIndex < 0 ? "all pens" : result.penNames().get(penIndex);
        String warning = result.clipped() ? " Some geometry is clipped to the bed." : "";
        summary.setText(String.format(java.util.Locale.ROOT,
                "%s: max %d overlaps; %d cells at or above %d (%.2f mm grid).%s",
                name, result.maximum(penIndex), result.cellsAtLeast(penIndex, threshold), threshold,
                result.cellMm(), warning));
    }
}
