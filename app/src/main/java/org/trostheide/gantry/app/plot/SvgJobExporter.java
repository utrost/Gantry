package org.trostheide.gantry.app.plot;

import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.io.AtomicFiles;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a reviewed job as physical-size SVG, or one atomic ZIP with a file per layer.
 * Included empty layers retain their group/file and order; selection and rejecting
 * jobs without drawing paths are the caller's responsibility.
 */
public final class SvgJobExporter {
    private SvgJobExporter() { }

    public static void writeCombined(File destination, PlotService.ProjectedArtwork artwork,
                                     List<Double> widthsMm) throws IOException {
        writeCombined(destination, artwork, widthsMm, List.of());
    }

    public static void writeCombined(File destination, PlotService.ProjectedArtwork artwork,
                                     List<Double> widthsMm, List<String> penNames) throws IOException {
        String svg = render(artwork, widthsMm, penNames, -1);
        AtomicFiles.write(destination, temp -> Files.writeString(temp.toPath(), svg, StandardCharsets.UTF_8));
    }

    public static void writePackage(File destination, PlotService.ProjectedArtwork artwork,
                                    List<Double> widthsMm) throws IOException {
        writePackage(destination, artwork, widthsMm, List.of());
    }

    public static void writePackage(File destination, PlotService.ProjectedArtwork artwork,
                                    List<Double> widthsMm, List<String> penNames) throws IOException {
        AtomicFiles.write(destination, temp -> {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp.toPath()),
                    StandardCharsets.UTF_8)) {
                entry(zip, "combined.svg", render(artwork, widthsMm, penNames, -1));
                for (int i = 0; i < artwork.layers().size(); i++) {
                    String name = String.format(Locale.ROOT, "layers/%02d-%s.svg", i + 1,
                            safeName(artwork.layers().get(i).layer().id()));
                    entry(zip, name, render(artwork, widthsMm, penNames, i));
                }
                entry(zip, "README.txt", "SVG coordinates are physical-page millimetres, matching the reviewed workspace.\n"
                        + "combined.svg preserves the reviewed layer order; layers/ contains one SVG per layer.\n"
                        + "These files contain drawing paths only. They do not encode pen-change gates,\n"
                        + "refill motions, drying waits, or G-code/controller settings.\n");
            }
        });
    }

    public static String combined(PlotService.ProjectedArtwork artwork, List<Double> widthsMm) {
        return render(artwork, widthsMm, List.of(), -1);
    }

    private static String render(PlotService.ProjectedArtwork artwork, List<Double> widthsMm, List<String> penNames, int onlyLayer) {
        if (!Double.isFinite(artwork.widthMm()) || !Double.isFinite(artwork.heightMm())
                || artwork.widthMm() <= 0 || artwork.heightMm() <= 0) {
            throw new IllegalArgumentException("SVG bed dimensions must be positive and finite");
        }
        MachinePageProjection page = artwork.page();
        double[] matrix = new double[6];
        page.transform().getMatrix(matrix);
        String transform = java.util.Arrays.stream(matrix).mapToObj(SvgJobExporter::number)
                .collect(java.util.stream.Collectors.joining(" "));
        StringBuilder svg = new StringBuilder(1024);
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
                .append("xmlns:inkscape=\"http://www.inkscape.org/namespaces/inkscape\" ")
                .append("width=\"").append(number(page.widthMm())).append("mm\" height=\"")
                .append(number(page.heightMm())).append("mm\" viewBox=\"0 0 ")
                .append(number(page.widthMm())).append(' ').append(number(page.heightMm()))
                .append("\">\n");
        for (int i = 0; i < artwork.layers().size(); i++) {
            if (onlyLayer >= 0 && i != onlyLayer) continue;
            PlotService.ProjectedLayer layer = artwork.layers().get(i);
            String id = layer.layer().id() == null ? "Layer " + (i + 1) : layer.layer().id();
            String color = layer.layer().color();
            if (color == null || !color.matches("#[0-9a-fA-F]{6}")) color = "#000000";
            boolean knownWidth = widthsMm != null && i < widthsMm.size() && widthsMm.get(i) != null
                    && Double.isFinite(widthsMm.get(i)) && widthsMm.get(i) > 0;
            double width = knownWidth ? widthsMm.get(i) : 0.3;
            svg.append("  <g id=\"layer-").append(i + 1).append("\" inkscape:groupmode=\"layer\" ")
                    .append("inkscape:label=\"").append(xml(id)).append("\" data-gantry-order=\"")
                    .append(i + 1).append("\" ");
            if (i < penNames.size() && penNames.get(i) != null) {
                svg.append("data-gantry-pen-name=\"").append(xml(penNames.get(i))).append("\" ");
            }
            if (knownWidth) svg.append("data-gantry-pen-width-mm=\"").append(number(width)).append("\" ");
            else svg.append("data-gantry-pen-width-assumed=\"true\" ");
            svg.append("fill=\"none\" stroke=\"").append(color)
                    .append("\" stroke-width=\"").append(number(width))
                    .append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\" ")
                    .append("transform=\"matrix(").append(transform).append(")\">\n");
            for (List<Point> stroke : layer.strokes()) {
                if (stroke.isEmpty()) continue;
                svg.append("    <path d=\"M ").append(number(stroke.get(0).x())).append(' ')
                        .append(number(stroke.get(0).y()));
                if (stroke.size() == 1) {
                    svg.append(" L ").append(number(stroke.get(0).x())).append(' ')
                            .append(number(stroke.get(0).y()));
                } else {
                    for (int p = 1; p < stroke.size(); p++) {
                        svg.append(" L ").append(number(stroke.get(p).x())).append(' ')
                                .append(number(stroke.get(p).y()));
                    }
                }
                svg.append("\"/>\n");
            }
            svg.append("  </g>\n");
        }
        return svg.append("</svg>\n").toString();
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String safeName(String value) {
        String safe = value == null ? "layer" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (safe.isEmpty()) safe = "layer";
        return safe.substring(0, Math.min(50, safe.length()));
    }

    private static String xml(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        value.codePoints().forEach(code -> {
            if (!(code == 9 || code == 10 || code == 13 || code >= 0x20 && code <= 0xd7ff
                    || code >= 0xe000 && code <= 0xfffd || code >= 0x10000 && code <= 0x10ffff)) {
                throw new IllegalArgumentException(String.format(Locale.ROOT,
                        "SVG metadata contains an unsupported XML character U+%04X. Rename the layer or pen and retry.", code));
            }
            switch (code) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case 39 -> escaped.append("&apos;");
                case 9 -> escaped.append("&#9;");
                case 10 -> escaped.append("&#10;");
                case 13 -> escaped.append("&#13;");
                default -> escaped.appendCodePoint(code);
            }
        });
        return escaped.toString();
    }

    private static String number(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite SVG coordinate");
        if (value == 0) return "0";
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
