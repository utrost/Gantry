package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotterResourceLinksTest {

    @Test
    void catalogContainsPlotterAdjacentWebToolsWithLiveLinks() {
        List<PlotterResourceLink> links = PlotterResourceLinks.defaults();

        assertEquals(List.of("VHS", "PPCT", "Generative Art"), links.stream().map(PlotterResourceLink::name).toList());
        assertEquals(URI.create("https://simiono.com/vhs/"), links.get(0).liveUrl());
        assertEquals(URI.create("https://simiono.com/ppct/"), links.get(1).liveUrl());
        assertEquals(URI.create("https://simiono.com/genart/"), links.get(2).liveUrl());
        assertEquals(URI.create("https://github.com/utrost/VHS"), links.get(0).sourceUrl());
        assertEquals(URI.create("https://github.com/utrost/PPCT"), links.get(1).sourceUrl());
        assertEquals(URI.create("https://github.com/utrost/GenerativeArt"), links.get(2).sourceUrl());
        assertTrue(links.get(0).plotterUse().contains("handwriting"));
        assertTrue(links.get(1).plotterUse().contains("calibration"));
        assertTrue(links.get(2).plotterUse().contains("SVG art"));
    }

    @Test
    void helpMenuIncludesPlotterResourcesBetweenGuideAndDiagnostics() {
        List<URI> opened = new ArrayList<>();
        JMenu help = new JMenu("Help");

        PlotterPanel.addHelpMenuItems(help, () -> { }, () -> { }, opened::add, () -> { }, () -> { });

        assertEquals("Guided First Plot...", help.getItem(0).getText());
        assertEquals("User Guide...", help.getItem(1).getText());
        JMenu resources = (JMenu) help.getItem(2);
        assertEquals("Plotter Resources", resources.getText());
        findItem(resources, "Open PPCT — pen calibration target").orElseThrow().doClick();
        assertEquals(List.of(URI.create("https://simiono.com/ppct/")), opened);
        assertTrue(help.getMenuComponent(3) instanceof javax.swing.JSeparator);
        assertEquals("Copy Diagnostics", help.getItem(4).getText());
        assertEquals("About Gantry...", help.getItem(5).getText());
    }

    @Test
    void plotterResourcesMenuOffersLiveAndRepositoryEntries() {
        JMenu resources = PlotterResourceLinks.buildMenu(uri -> { });

        assertEquals("Plotter Resources", resources.getText());
        assertTrue(findItem(resources, "Open VHS — handwriting SVGs").isPresent());
        assertTrue(findItem(resources, "Open PPCT — pen calibration target").isPresent());
        assertTrue(findItem(resources, "Open Generative Art — SVG art generators").isPresent());
        assertTrue(findItem(resources, "Source: VHS").isPresent());
        assertTrue(findItem(resources, "Source: PPCT").isPresent());
        assertTrue(findItem(resources, "Source: Generative Art").isPresent());
        assertTrue(findItem(resources, "Open all project repositories").isPresent());
    }

    private static Optional<JMenuItem> findItem(JMenu menu, String text) {
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            if (item != null && text.equals(item.getText())) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }
}
