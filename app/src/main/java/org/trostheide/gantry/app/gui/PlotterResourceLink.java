package org.trostheide.gantry.app.gui;

import java.net.URI;

/** One plotter-adjacent web tool that can hand SVGs or calibration targets to Gantry. */
record PlotterResourceLink(String name, String menuLabel, String plotterUse, URI liveUrl, URI sourceUrl) {
}
