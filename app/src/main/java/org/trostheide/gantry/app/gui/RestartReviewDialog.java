package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.*;
import org.trostheide.gantry.model.Point;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.List;
import java.util.Optional;

import javax.swing.*;

/** Read-only path selection. Approval prepares a job; it never starts motion. */
final class RestartReviewDialog {
    static Optional<PlotJobHistory.Job> show(Component parent, ReviewedRestart.Store store) {
        var snapshot = store.snapshot();
        JList<ReviewedRestart.Stroke> paths =
                new JList<>(snapshot.strokes().toArray(ReviewedRestart.Stroke[]::new));
        paths.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        paths.setName("restartStrokes");
        paths.getAccessibleContext().setAccessibleName("Restart start stroke and pass occurrence");
        JCheckBox reverse =
                new JCheckBox(
                        "Reverse: selected stroke → first; reverse each stroke",
                        snapshot.reverse());
        reverse.setName("restartReverse");
        RestartCanvas canvas = new RestartCanvas(store, paths);
        canvas.setPreferredSize(new Dimension(530, 370));
        JLabel progress =
                new JLabel(
                        "<html>Controller-confirmed at completed layer barriers: "
                                + snapshot.confirmed().size()
                                + " / "
                                + snapshot.strokes().size()
                                + "<br>Previous submission: "
                                + snapshot.started()
                                + " started, "
                                + snapshot.acknowledged()
                                + " acknowledged. Acknowledged is not physically finished.<br>"
                                + "State: "
                                + snapshot.state()
                                + ". After Stop or process loss, every stroke beyond the last"
                                + " barrier is uncertain.</html>");
        paths.setSelectedIndex(snapshot.selection());
        boolean[] persistenceFailed = {false};
        Runnable changed =
                () -> {
                    if (paths.getSelectedIndex() < 0) return;
                    try {
                        store.select(paths.getSelectedIndex(), reverse.isSelected());
                        persistenceFailed[0] = false;
                        canvas.repaint();
                    } catch (RuntimeException failure) {
                        persistenceFailed[0] = true;
                        JOptionPane.showMessageDialog(
                                parent,
                                failure.getMessage(),
                                "Restart proposal could not be saved",
                                JOptionPane.ERROR_MESSAGE);
                    }
                };
        paths.addListSelectionListener(
                e -> {
                    if (!e.getValueIsAdjusting()) changed.run();
                });
        reverse.addActionListener(e -> changed.run());
        JCheckBox mapping =
                new JCheckBox(
                        "I reviewed machine/profile mapping, paper registration and work origin");
        mapping.setName("restartMappingConfirmed");
        JCheckBox pens =
                new JCheckBox(
                        "I reviewed pen assignments, layer order, inclusion and all remaining pass"
                                + " occurrences");
        pens.setName("restartPensConfirmed");
        JCheckBox uncertain =
                new JCheckBox(
                        "I explicitly chose which uncertain strokes to replay or skip; no automatic"
                            + " physical resume");
        uncertain.setName("restartUncertaintyConfirmed");
        JPanel body = new JPanel(new BorderLayout(8, 8));
        body.add(progress, BorderLayout.NORTH);
        JSplitPane split =
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvas, new JScrollPane(paths));
        split.setResizeWeight(.7);
        split.setPreferredSize(new Dimension(850, 380));
        body.add(split, BorderLayout.CENTER);
        JPanel checks = new JPanel();
        checks.setLayout(new BoxLayout(checks, BoxLayout.Y_AXIS));
        checks.add(reverse);
        checks.add(
                WorkspaceStyle.note(
                        "Click a path or choose its exact layer/command/occurrence. Purple is the"
                            + " selected start; muted paths are skipped. Forward traverses selected"
                            + " → end. Every retained stroke begins with pen-up positioning."
                            + " Simulation verifies coordinates, not paper registration."));
        checks.add(mapping);
        checks.add(pens);
        checks.add(uncertain);
        body.add(checks, BorderLayout.SOUTH);
        JOptionPane pane =
                new JOptionPane(body, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = pane.createDialog(parent, "Review partial restart");
        dialog.setResizable(true);
        dialog.setVisible(true);
        dialog.dispose();
        if (!Integer.valueOf(JOptionPane.OK_OPTION).equals(pane.getValue()))
            return Optional.empty();
        if (persistenceFailed[0]
                || !mapping.isSelected()
                || !pens.isSelected()
                || !uncertain.isSelected()) {
            JOptionPane.showMessageDialog(
                    parent,
                    "All restart confirmations are required. Nothing was prepared or moved.",
                    "Restart not approved",
                    JOptionPane.WARNING_MESSAGE);
            return Optional.empty();
        }
        return Optional.of(ReviewedRestart.execution(store.snapshot()));
    }

    private static final class RestartCanvas extends JPanel {
        private final ReviewedRestart.Store store;
        private final JList<?> list;
        private final MachinePageProjection projection;

        RestartCanvas(ReviewedRestart.Store store, JList<?> list) {
            this.store = store;
            this.list = list;
            projection = MachinePageProjection.from(store.snapshot().source().reviewSettings());
            setName("restartCanvas");
            setBackground(new Color(28, 34, 45));
            addMouseListener(
                    new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            var snapshot = store.snapshot();
                            double closest = 12;
                            int selected = -1;
                            for (int i = 0; i < snapshot.strokes().size(); i++) {
                                List<Point> points = snapshot.strokes().get(i).points();
                                for (int j = 1; j < points.size(); j++) {
                                    double[] a = screen(points.get(j - 1)),
                                            b = screen(points.get(j));
                                    double distance =
                                            Line2D.ptSegDist(
                                                    a[0], a[1], b[0], b[1], e.getX(), e.getY());
                                    if (distance < closest) {
                                        closest = distance;
                                        selected = i;
                                    }
                                }
                            }
                            if (selected >= 0) {
                                list.setSelectedIndex(selected);
                                list.ensureIndexIsVisible(selected);
                            }
                        }
                    });
        }

        private double[] screen(Point point) {
            double scale =
                    Math.max(
                            .001,
                            Math.min(
                                    (getWidth() - 32) / projection.widthMm(),
                                    (getHeight() - 32) / projection.heightMm()));
            double[] p = projection.point(point.x(), point.y());
            return new double[] {16 + p[0] * scale, 16 + p[1] * scale};
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                var snapshot = store.snapshot();
                var includedStrokes = new java.util.HashSet<>(snapshot.order());
                for (int i = 0; i < snapshot.strokes().size(); i++) {
                    boolean included = includedStrokes.contains(i);
                    g.setColor(
                            i == snapshot.selection()
                                    ? new Color(220, 125, 245)
                                    : included ? new Color(100, 205, 235) : new Color(85, 92, 104));
                    g.setStroke(
                            new BasicStroke(i == snapshot.selection() ? 3 : included ? 1.8f : 1));
                    var points = snapshot.strokes().get(i).points();
                    Path2D path = new Path2D.Double();
                    for (int j = 0; j < points.size(); j++) {
                        double[] p = screen(points.get(j));
                        if (j == 0) path.moveTo(p[0], p[1]);
                        else path.lineTo(p[0], p[1]);
                    }
                    g.draw(path);
                }
                var selected = snapshot.strokes().get(snapshot.selection()).points();
                Point start = selected.get(snapshot.reverse() ? selected.size() - 1 : 0);
                double[] p = screen(start);
                g.setColor(Color.ORANGE);
                g.fill(new Ellipse2D.Double(p[0] - 4, p[1] - 4, 8, 8));
                g.drawString(
                        "Start " + (snapshot.reverse() ? "← reverse" : "→ forward"),
                        (float) p[0] + 7,
                        (float) p[1] - 6);
            } finally {
                g.dispose();
            }
        }
    }
}
