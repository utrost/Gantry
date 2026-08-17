package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.session.CompositionArtwork;
import org.trostheide.gantry.model.Bounds;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/** Inspector/actions panel for addressable artwork groups in a composed document. */
final class ArtworkGroupsPanel extends JPanel {
    record Actions(Consumer<CompositionArtwork> transform, Consumer<CompositionArtwork> reprocess,
                   Consumer<CompositionArtwork> rename) { }

    private final List<CompositionArtwork> artworks;
    private final Actions actions;
    private final DefaultListModel<CompositionArtwork> model = new DefaultListModel<>();
    private final JList<CompositionArtwork> list = new JList<>(model);
    private final JPanel details = new JPanel();
    private final JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final JButton transform = new JButton("Transform...");
    private final JButton reprocess = new JButton("Re-process...");
    private final JButton rename = new JButton("Rename...");

    ArtworkGroupsPanel(List<CompositionArtwork> artworks, Actions actions) {
        super(new BorderLayout(8, 8));
        this.artworks = artworks == null ? List.of() : List.copyOf(artworks);
        this.actions = actions;
        setBorder(new EmptyBorder(8, 8, 8, 8));
        JLabel title = new JLabel("Artwork groups");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        add(title, BorderLayout.NORTH);

        for (CompositionArtwork artwork : this.artworks) model.addElement(artwork);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((jList, artwork, index, selected, focus) -> {
            JLabel label = new JLabel(summary(artwork));
            label.setOpaque(true);
            label.setBorder(new EmptyBorder(5, 7, 5, 7));
            label.setBackground(selected ? jList.getSelectionBackground() : jList.getBackground());
            label.setForeground(selected ? jList.getSelectionForeground() : jList.getForeground());
            return label;
        });
        list.addListSelectionListener(e -> refreshDetails());
        if (!this.artworks.isEmpty()) list.setSelectedIndex(0);
        add(new JScrollPane(list), BorderLayout.CENTER);

        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setBorder(BorderFactory.createTitledBorder("Selected artwork"));
        actionsPanel.add(transform);
        actionsPanel.add(reprocess);
        actionsPanel.add(rename);
        details.add(actionsPanel);
        add(details, BorderLayout.SOUTH);

        transform.addActionListener(e -> { if (selected() != null) actions.transform().accept(selected()); });
        reprocess.addActionListener(e -> { if (selected() != null) actions.reprocess().accept(selected()); });
        rename.addActionListener(e -> { if (selected() != null) actions.rename().accept(selected()); });
        refreshDetails();
    }

    void selectArtwork(String artworkId) {
        for (int i = 0; i < artworks.size(); i++) {
            if (artworks.get(i).id().equals(artworkId)) {
                list.setSelectedIndex(i);
                return;
            }
        }
    }

    private CompositionArtwork selected() {
        return list.getSelectedValue();
    }

    private void refreshDetails() {
        details.removeAll();
        details.add(actionsPanel);
        CompositionArtwork artwork = selected();
        boolean hasSelection = artwork != null;
        transform.setEnabled(hasSelection);
        rename.setEnabled(hasSelection);
        reprocess.setEnabled(hasSelection && canReprocess(artwork));
        if (artwork != null) {
            details.add(label("Label: " + artwork.label()));
            details.add(label("Source: " + sourceLabel(artwork)));
            details.add(label("Layers: " + artwork.layerIndices().size()));
            details.add(label("Bounds: " + boundsLabel(artwork.bounds())));
            details.add(label("Transform: " + transformLabel(artwork.transform())));
            details.add(label(canReprocess(artwork) ? "Can re-process" : "No saved SVG source"));
        }
        revalidate();
        repaint();
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setBorder(new EmptyBorder(1, 4, 1, 4));
        return label;
    }

    private static String summary(CompositionArtwork artwork) {
        return artwork.label() + " — " + artwork.layerIndices().size() + " layer(s), " + sizeLabel(artwork.bounds());
    }

    private static String sourceLabel(CompositionArtwork artwork) {
        if (artwork.sourcePath() == null || artwork.sourcePath().isBlank()) return "No saved SVG source";
        return new File(artwork.sourcePath()).getName();
    }

    private static boolean canReprocess(CompositionArtwork artwork) {
        return artwork.sourcePath() != null && !artwork.sourcePath().isBlank() && artwork.importOptions() != null;
    }

    private static String boundsLabel(Bounds bounds) {
        if (bounds == null) return "unknown";
        return String.format("x %.1f, y %.1f, %s", bounds.minX(), bounds.minY(), sizeLabel(bounds));
    }

    private static String sizeLabel(Bounds bounds) {
        if (bounds == null) return "unknown size";
        return String.format("%.1f × %.1f mm", Math.max(0, bounds.maxX() - bounds.minX()),
                Math.max(0, bounds.maxY() - bounds.minY()));
    }

    private static String transformLabel(CompositionArtwork.Transform transform) {
        return String.format("x %.1f, y %.1f, scale %.3f%s", transform.x(), transform.y(), transform.scale(),
                transform.mirror() ? ", mirrored" : "");
    }
}
