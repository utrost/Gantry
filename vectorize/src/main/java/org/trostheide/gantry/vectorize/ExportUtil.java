package org.trostheide.gantry.vectorize;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;

/**
 * Utility class for exporting SVG files to other formats (PNG, PDF).
 * Uses Apache Batik transcoders which are already a project dependency.
 */
public class ExportUtil {

    /**
     * Exports an SVG file to PNG format.
     *
     * @param svgPath    Path to the source SVG file
     * @param pngPath    Path for the output PNG file
     * @param widthHint  Desired width in pixels (0 = use SVG's native size)
     */
    public static void exportToPng(String svgPath, String pngPath, float widthHint) throws Exception {
        PNGTranscoder transcoder = new PNGTranscoder();

        if (widthHint > 0) {
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, widthHint);
        }

        try {
            atomicExport(pngPath, temporary -> {
                try (InputStream in = Files.newInputStream(Path.of(svgPath));
                     OutputStream out = Files.newOutputStream(temporary)) {
                    transcoder.transcode(new TranscoderInput(in), new TranscoderOutput(out));
                }
            });
        } catch (Exception failure) {
            throw new IOException("Could not export PNG to " + pngPath + ": " + failure, failure);
        }

        System.out.println("Exported PNG: " + pngPath);
    }

    /**
     * Exports an SVG file to PDF format using Batik's FOP transcoder.
     * Falls back to a helpful error message if FOP is not available.
     *
     * @param svgPath Path to the source SVG file
     * @param pdfPath Path for the output PDF file
     */
    public static void exportToPdf(String svgPath, String pdfPath) throws Exception {
        requireFormatSupport("pdf");
        Class<?> pdfTranscoderClass = Class.forName("org.apache.fop.svg.PDFTranscoder");
        org.apache.batik.transcoder.Transcoder transcoder =
                (org.apache.batik.transcoder.Transcoder) pdfTranscoderClass.getDeclaredConstructor().newInstance();
        atomicExport(pdfPath, temporary -> {
            try (InputStream in = Files.newInputStream(Path.of(svgPath));
                 OutputStream out = Files.newOutputStream(temporary)) {
                transcoder.transcode(new TranscoderInput(in), new TranscoderOutput(out));
            }
        });
        System.out.println("Exported PDF: " + pdfPath);
    }

    /** Fail before generating artifacts when an optional requested exporter is unavailable. */
    public static void requireFormatSupport(String format) throws IOException {
        if ("pdf".equals(format)) {
            try { Class.forName("org.apache.fop.svg.PDFTranscoder"); }
            catch (ClassNotFoundException | LinkageError missing) {
                throw new IOException("PDF export is unavailable: the packaged CLI does not bundle Apache FOP. "
                        + "Use --format svg or --format png, or supply Apache FOP on a custom classpath.", missing);
            }
        }
    }

    @FunctionalInterface private interface ExportWriter { void write(Path temporary) throws Exception; }

    // This module intentionally has no model-module dependency.
    private static void atomicExport(String destination, ExportWriter writer) throws Exception {
        Path target = Path.of(destination).toAbsolutePath();
        Path temporary = Files.createTempFile(target.getParent(), ".gantry-export-", ".tmp");
        try {
            writer.write(temporary);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    /**
     * Determines the output path for a given format, replacing the .svg extension.
     */
    public static String getOutputPath(String svgPath, String format) {
        if (svgPath.toLowerCase().endsWith(".svg")) {
            return svgPath.substring(0, svgPath.length() - 4) + "." + format;
        }
        return svgPath + "." + format;
    }
}
