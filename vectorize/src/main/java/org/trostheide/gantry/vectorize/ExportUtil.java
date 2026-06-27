package org.trostheide.gantry.vectorize;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

import java.io.*;
import java.nio.file.Files;

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

        try (InputStream in = Files.newInputStream(new File(svgPath).toPath());
             OutputStream out = new FileOutputStream(pngPath)) {
            TranscoderInput input = new TranscoderInput(in);
            TranscoderOutput output = new TranscoderOutput(out);
            transcoder.transcode(input, output);
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
        // Try to use Batik's PDF transcoder (requires batik-transcoder + FOP)
        try {
            Class<?> pdfTranscoderClass = Class.forName("org.apache.fop.svg.PDFTranscoder");
            org.apache.batik.transcoder.Transcoder transcoder =
                    (org.apache.batik.transcoder.Transcoder) pdfTranscoderClass.getDeclaredConstructor().newInstance();

            try (InputStream in = Files.newInputStream(new File(svgPath).toPath());
                 OutputStream out = new FileOutputStream(pdfPath)) {
                TranscoderInput input = new TranscoderInput(in);
                TranscoderOutput output = new TranscoderOutput(out);
                transcoder.transcode(input, output);
            }

            System.out.println("Exported PDF: " + pdfPath);
        } catch (ClassNotFoundException e) {
            System.err.println("PDF export requires Apache FOP on the classpath. "
                    + "Add 'org.apache.xmlgraphics:fop' dependency to pom.xml for PDF support.");
            System.err.println("As an alternative, use --format png and convert to PDF externally.");
        }
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
