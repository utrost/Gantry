package org.trostheide.gantry.app.gui;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

/** Curated links to Uwe's plotter-adjacent tools, kept close to Gantry's Help menu. */
final class PlotterResourceLinks {

    private PlotterResourceLinks() {
    }

    static List<PlotterResourceLink> defaults() {
        return List.of(
                new PlotterResourceLink(
                        "VHS",
                        "Open VHS — handwriting SVGs",
                        "Generate handwriting as single-stroke SVGs to plot in Gantry.",
                        URI.create("https://simiono.com/vhs/"),
                        URI.create("https://github.com/utrost/VHS")),
                new PlotterResourceLink(
                        "PPCT",
                        "Open PPCT — pen calibration target",
                        "Generate calibration SVGs to verify pen, ink, paper, and plotter settings.",
                        URI.create("https://simiono.com/ppct/"),
                        URI.create("https://github.com/utrost/PPCT")),
                new PlotterResourceLink(
                        "Generative Art",
                        "Open Generative Art — SVG art generators",
                        "Create plotter-ready generative SVG art to import into Gantry.",
                        URI.create("https://simiono.com/genart/"),
                        URI.create("https://github.com/utrost/GenerativeArt"))
        );
    }

    static JMenu buildMenu(Consumer<URI> opener) {
        JMenu resources = new JMenu("Plotter Resources");
        resources.setToolTipText("Open related web tools that create SVGs, calibration targets, or examples to verify with Gantry.");
        for (PlotterResourceLink link : defaults()) {
            resources.add(tip(menuItem(link.menuLabel(), opener, link.liveUrl()),
                    link.plotterUse() + " Opens " + link.liveUrl()));
        }
        resources.addSeparator();
        for (PlotterResourceLink link : defaults()) {
            resources.add(tip(menuItem("Source: " + link.name(), opener, link.sourceUrl()),
                    "Open the GitHub repository: " + link.sourceUrl()));
        }
        resources.addSeparator();
        resources.add(tip(menuItem("Open all project repositories", opener,
                URI.create("https://github.com/utrost?tab=repositories&q=&type=source")),
                "Open Uwe's GitHub repositories so related plotting tools are easy to find."));
        return resources;
    }

    private static JMenuItem menuItem(String label, Consumer<URI> opener, URI uri) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(e -> opener.accept(uri));
        return item;
    }

    private static JMenuItem tip(JMenuItem item, String tooltip) {
        item.setToolTipText(tooltip);
        return item;
    }
}
